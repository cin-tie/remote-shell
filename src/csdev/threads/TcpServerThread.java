package csdev.threads;

import csdev.Protocol;
import csdev.client.TcpClientMain;
import csdev.messages.*;
import csdev.server.ServerMain;
import csdev.threads.session.ClientSession;
import csdev.utils.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * <p>Tcp server thread for handling client connections
 * @author cin-tie
 * @version 1.2
 */
public class TcpServerThread extends Thread {

    private TcpClientSession clientSession;

    public TcpServerThread(Socket s) throws IOException {
        this.clientSession = new TcpClientSession(s);
        this.setDaemon(true);
        logDebug("TCP Server thread created for: " + clientSession.getClientInfo());
    }

    public void run() {
        logDebug("TCP Client session started: " + clientSession.getClientInfo());

        try {
            clientSession.processMessages();
        } catch (Exception e) {
            if (!clientSession.isGracefulShutdown()) {
                logError("Unexpected error in TCP client session: " + e.getMessage());
            }
        } finally {
            clientSession.disconnect();
        }
    }

    public void gracefulDisconnect() {
        clientSession.gracefulDisconnect();
    }

    public String getUsername() {
        return clientSession.getUsername();
    }

    private void logDebug(String message) {
        System.out.println();
        Logger.logDebug(message);
        restorePrompt();
    }

    private void logError(String message) {
        System.out.println();
        Logger.logError(message);
        restorePrompt();
    }

    private void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}

/**
 * <p>TCP client session implementation
 */
class TcpClientSession extends ClientSession {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private InetAddress address;

    private Object syncCommands = new Object();
    private Vector<String> commandQueue = null;

    public TcpClientSession(Socket s) throws IOException {
        super();
        this.socket = s;
        s.setSoTimeout(1000);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        address = s.getInetAddress();
    }

    public void processMessages() throws IOException {
        while (!disconnected && !gracefulShutdown) {
            Message msg = null;
            try {
                msg = (Message) in.readObject();
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                if(!gracefulShutdown) {
                    logError("IOError reading from client: " + e.getMessage());
                }
                break;
            } catch (ClassNotFoundException e) {
                logError("Invalid message received: " + e.getMessage());
                continue;
            }

            if(msg != null) {
                logDebug("Received TCP message type: " + msg.getId() + " from " + username);
                processMessage(msg);
            }
            if(Thread.interrupted()) {
                logDebug("Thread interrupted, ending session");
                break;
            }
        }
    }

    private void processMessage(Message msg) throws IOException {
        switch (msg.getId()) {
            case Protocol.CMD_CONNECT:
                if(!connect((MessageConnect) msg))
                    return;
                break;

            case Protocol.CMD_DISCONNECT:
                logInfo("Client requested disconnect: " + username);
                disconnect();
                break;

            case Protocol.CMD_EXECUTE:
                executeCommand((MessageExecute) msg);
                break;

            case Protocol.CMD_UPLOAD:
                uploadFile((MessageUpload) msg);
                break;

            case Protocol.CMD_DOWNLOAD:
                downloadFile((MessageDownload) msg);
                break;

            case Protocol.CMD_CHDIR:
                changeDirectory((MessageChdir) msg);
                break;

            case Protocol.CMD_GETDIR:
                getCurrentDirectory((MessageGetdir) msg);
                break;

            default:
                logError("Unknown message type: " + msg.getId());
                break;
        }
    }

    boolean connect(MessageConnect msg) throws IOException {
        logInfo("TCP connecting attempt from: " + msg.username + "(" + msg.usernameFull + ")");

        if(ServerMain.isPasswordRequired()){
            if(msg.password == null || !msg.password.equals(ServerMain.getServerPassword())){
                MessageConnectResult result = new MessageConnectResult("Wrong password");
                sendMessage(result);
                logWarning("TCP Connection rejected - invalid password for user: " + msg.username);
                return false;
            }
        }

        super.register(msg.username,  msg.password);

        String serverOS = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String serverVersion = "Remote Shell server 1.1";
        MessageConnectResult result = new MessageConnectResult(serverOS, currentDirectory, serverVersion);
        sendMessage(result);
        logInfo("User connected successfully via TCP: " + msg.username);
        return true;
    }

    void executeCommand(MessageExecute msg) throws IOException {
        logInfo("Executing command for " + username + ": " + msg.command);

        try{
            String command = msg.command;
            String workingDirectory = (msg.workingDir == null || msg.workingDir.isEmpty()) ? currentDirectory : msg.workingDir;
            long timeout = msg.timeMillis > 0 ? msg.timeMillis : 30000;

            ProcessBuilder pb = new  ProcessBuilder();
            pb.command("sh", "-c", command);
            pb.directory(new File(workingDirectory));

            long startTime = System.currentTimeMillis();
            Process p = pb.start();

            boolean finished = false;
            try{
                finished = p.waitFor(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                p.destroy();
                Thread.currentThread().interrupt();
                throw new IOException("Command execution interrupted");
            }

            long timeMillis = System.currentTimeMillis() -  startTime;

            if(!finished) {
                p.destroyForcibly();
                MessageExecuteResult result = new MessageExecuteResult("Command timed out after " + timeMillis + " ms");
                sendMessage(result);
                logWarning("Command timed out for: " + username + " after " + timeMillis + " ms");
                return;
            }

            String output = readStream(p.getInputStream());
            String error = readStream(p.getErrorStream());
            int exitCode = p.exitValue();

            MessageExecuteResult result = new MessageExecuteResult(output, error, exitCode, timeMillis, workingDirectory);
            sendMessage(result);

            logInfo("Command completed for " + username + " [exitCode=" + exitCode + ", time=" + timeMillis + "ms]");
        } catch (Exception e){
            logError("Command execution failed for " + username + ": " + e.getMessage());
            MessageExecuteResult result = new MessageExecuteResult("Command execution failed: " + e.getMessage());
            sendMessage(result);
        }
    }

    void uploadFile(MessageUpload msg) throws IOException {
        logInfo("Uploading file from " + username + ": " + msg.fileName);

        try {
            File targetDirectory = new File(currentDirectory);
            if(!targetDirectory.exists() || !targetDirectory.isDirectory()) {
                MessageUploadResult result = new MessageUploadResult("Invalid target directory: " + msg.filePath);
                sendMessage(result);
                return;
            }

            File targetFile = new File(targetDirectory, msg.fileName);
            boolean fileExists = targetFile.exists();

            if(fileExists && !msg.overwrite) {
                MessageUploadResult result = new MessageUploadResult("File already exists and overwrite is disabled: " + msg.filePath);
                sendMessage(result);
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(msg.fileData);
            }

            MessageUploadResult result = new MessageUploadResult(targetFile.getAbsolutePath(), msg.fileSize, fileExists);
            sendMessage(result);

            logInfo("File uploaded successfully: " + targetFile.getAbsolutePath() + " [size=" + msg.fileSize + " bytes, overwrite=" + fileExists + "]");
        } catch (Exception e) {
            logError("File upload failed for " + username + ": " + e.getMessage());
            MessageUploadResult result = new MessageUploadResult("File upload failed: " + e.getMessage());
            sendMessage(result);
        }
    }

    void downloadFile(MessageDownload msg) throws IOException {
        logInfo("File download request from " + username + ": " + msg.filePath);

        try {
            File file = new File(msg.filePath.isEmpty() ? currentDirectory : msg.filePath);
            if (!file.exists() || !file.isFile()) {
                MessageDownloadResult result = new MessageDownloadResult("File not found: " + msg.filePath);
                sendMessage(result);
                return;
            }

            if(!file.canRead()) {
                MessageDownloadResult result = new MessageDownloadResult("Cannot read file: " + msg.filePath);
                sendMessage(result);
                return;
            }

            long size = file.length();
            long offset = msg.offset;
            long length = msg.length > 0 ? Math.min(size - offset, msg.length) : size - offset;

            byte[] fileData = new byte[(int) length];
            try (FileInputStream fis = new FileInputStream(file)) {
                BufferedInputStream bis = new BufferedInputStream(fis);

                bis.skip(offset);
                int read = bis.read(fileData);
                if(read != length) {
                    throw new IOException("Failed to read complete file: " + msg.filePath);
                }
            }

            boolean isPartial = (offset > 0 || length < size);
            MessageDownloadResult result = new MessageDownloadResult(file.getName(), size, fileData, isPartial);
            sendMessage(result);
            logInfo("File downloaded successfully: " + file.getAbsolutePath() + " [size=" + size + " bytes, sent=" + length + " bytes, partial=" + isPartial + "]");
        } catch (Exception e) {
            logError("File download failed for " + username + ": " + e.getMessage());
            MessageDownloadResult result = new MessageDownloadResult("File download failed: " + e.getMessage());
            sendMessage(result);
        }
    }

    void changeDirectory(MessageChdir msg) throws IOException {
        logInfo("Directory change request from " + username + ": " + msg.newDirectory);

        try {
            File newDir = new File(msg.newDirectory);
            if (!newDir.exists() || !newDir.isDirectory()) {
                MessageChdirResult result = new MessageChdirResult("Directory does not exist: " + msg.newDirectory);
                sendMessage(result);
                return;
            }

            String oldDirectory = currentDirectory;
            currentDirectory = newDir.getAbsolutePath();

            MessageChdirResult result = new MessageChdirResult(currentDirectory, oldDirectory);
            sendMessage(result);
            logInfo("Directory changed for " + username + " successfully: " + oldDirectory + " -> " + currentDirectory);
        } catch (Exception e) {
            logError("Directory change failed for " + username + ": " + e.getMessage());
            MessageChdirResult result = new MessageChdirResult("Directory change failed: " + e.getMessage());
            sendMessage(result);
        }
    }

    void getCurrentDirectory(MessageGetdir msg) throws IOException {
        logDebug("Current directory request from " + username);

        try {
            MessageGetdirResult result = new MessageGetdirResult(currentDirectory, 0);
            sendMessage(result);
        } catch (Exception e) {
            logError("Get directory failed for " + username + ": " + e.getMessage());
            MessageGetdirResult result = new MessageGetdirResult("Get directory failed: " + e.getMessage());
            sendMessage(result);
        }
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public void sendMessage(Message msg) throws IOException {
        if(out != null && !disconnected) {
            out.writeObject(msg);
            out.flush();
        }
    }

    @Override
    public void gracefulDisconnect() {
        gracefulShutdown = true;
        try {
            if (out != null && !disconnected) {
                MessageDisconnect disconnectMsg = new MessageDisconnect("Server is shutting down");
                sendMessage(disconnectMsg);
            }
        } catch (IOException e) {
            logDebug("Could not send graceful disconnect message: " + e.getMessage());
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        disconnect();
    }

    @Override
    public void disconnect() {
        if(!disconnected) {
            try {
                if(gracefulShutdown) {
                    logInfo("TCP Client gracefully disconnected: " + getClientInfo());
                }
                else{
                    logInfo("TCP Client disconnected: " + getClientInfo());
                }
                unregister();
                if(out != null)
                    out.close();
                if(in != null)
                    in.close();
                if(socket != null)
                    socket.close();
            } catch (IOException e){
                if(!gracefulShutdown) {
                    logError("Error while disconnecting TCP client: " + e.getMessage());
                }
            }
            finally {
                disconnected = true;
            }
        }
    }

    public String getClientInfo() {
        return address != null ? address.getHostAddress() + " (" + username + ")" : "Unknown";
    }

    public boolean isGracefulShutdown() {
        return gracefulShutdown;
    }
}