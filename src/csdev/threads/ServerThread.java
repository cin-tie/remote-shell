package csdev.threads;

import csdev.Protocol;
import csdev.messages.*;
import csdev.server.ServerMain;
import csdev.utils.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * <p>Server thread for handling client connections
 * @author cin-tie
 * @version 1.2
 */
public class ServerThread extends Thread {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private InetAddress ip;

    private String username = null;
    private String usernameFull;
    private volatile boolean gracefulShutdown = false;
    private String currentDirectory;

    private Object syncCommands = new Object();
    private Vector<String> commandQueue = null;

    private boolean disconnected = false;

    public void queueCommand(String command) {
        synchronized (syncCommands) {
            if(commandQueue == null){
                commandQueue = new Vector<String>();
            }
            commandQueue.add(command);
        }
    }

    public String[] getQueuedCommands() {
        synchronized (syncCommands) {
            String[] commands = new String[commandQueue.size()];
            if(commandQueue != null){
                commands = commandQueue.toArray(commands);
                commandQueue.clear();
            }
            return commands;
        }
    }

    public ServerThread(Socket s) throws IOException {
        socket = s;
        s.setSoTimeout(1000);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        ip = s.getInetAddress();
        this.currentDirectory = System.getProperty("user.dir");
        this.setDaemon(true);
        logDebug("Server thread created for: " + ip.getHostName());
    }

    public void run() {
        logDebug("Client session started: " + ip.getHostAddress());

        try {
            while (!disconnected && !gracefulShutdown) {
                Message msg = null;
                try {
                    msg = (Message) in.readObject();
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!gracefulShutdown) {
                        logError("IO error reading from client: " + e.getMessage());
                    }
                    break;
                } catch (ClassNotFoundException e) {
                    logError("Invalid message received: " + e.getMessage());
                    continue;
                }

                if (msg != null) {
                    logDebug("Received message type: " + msg.getId() + " from " + username);
                    processMessage(msg);
                }

                if(Thread.interrupted()){
                    logDebug("Thread interrupted, ending session");
                    break;
                }
            }
        } catch (Exception e){
            if (!gracefulShutdown) {
                logError("Unexpected error in client session: " + e.getMessage());
            }
        } finally {
            disconnect();
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
                return;

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
                logWarning("Unknown message id: " + msg.getId());
                break;
        }
    }

    boolean connect(MessageConnect msg) throws IOException {
        logInfo("Connecting attempt from: " + msg.username + "(" + msg.usernameFull + ")");
        ServerThread old = register(msg.username, msg.usernameFull);
        if(old == null){
            String serverOS = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String serverVersion = "RemoteSheell server 1.0";
            MessageConnectResult result = new MessageConnectResult(serverOS, currentDirectory, serverVersion);
            out.writeObject(result);
            logInfo("User connected successfully: " + msg.username);
            return true;
        } else{
            MessageConnectResult result = new MessageConnectResult("User" + old.usernameFull + " already connected as " + username);
            out.writeObject(result);
            logWarning("Connection rejected - user already connected: " + msg.username);
            return false;
        }
    }

    void executeCommand(MessageExecute msg) throws IOException {
        logInfo("Executing command for " + username + ": " + msg.command);

        try {
            String command = msg.command;
            String workingDir = (msg.workingDir == null || msg.workingDir.isEmpty()) ? currentDirectory : msg.workingDir;
            long timeout = msg.timeMillis > 0 ? msg.timeMillis : 30000;

            ProcessBuilder pb = new ProcessBuilder();
            if(System.getProperty("os.name").toLowerCase().contains("windows")){
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(new File(workingDir));

            long startTime = System.currentTimeMillis();;
            Process process = pb.start();

            boolean finished = false;
            try{
                finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            } catch(InterruptedException e){
                process.destroy();
                Thread.currentThread().interrupt();
                throw new IOException("Command execution interrupted");
            }

            long executionTime = System.currentTimeMillis() - startTime;

            if(!finished){
                process.destroyForcibly();
                MessageExecuteResult result = new MessageExecuteResult("Command timed out after " + timeout + "ms");
                out.writeObject(result);
                logWarning("Command timeout for " + username + ": " + command);
                return;
            }

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());
            int exitCode =  process.exitValue();

            MessageExecuteResult result = new MessageExecuteResult(output, error, exitCode, executionTime, workingDir);
            out.writeObject(result);

            logInfo("Command completed for " + username + " [exitCode=" + exitCode + ", time=" + executionTime + "ms]");

        } catch(Exception e){
            logError("Command execution failed for " + username + ": " + e.getMessage());
            MessageExecuteResult result = new MessageExecuteResult("Command execution failed: " + e.getMessage());
            out.writeObject(result);
        }
    }

    void uploadFile(MessageUpload msg) throws IOException {
        logInfo("Uploading file from " + username + ": " + msg.fileName);

        try {
            File targetDir = new File(msg.filePath.isEmpty() ? currentDirectory : msg.filePath);
            if(!targetDir.exists() || !targetDir.isDirectory()){
                MessageUploadResult result = new MessageUploadResult("Invalid target directory: " + msg.filePath);
                out.writeObject(result);
                return;
            }

            File targetFile = new File(targetDir, msg.fileName);
            boolean fileExists = targetFile.exists();

            if(fileExists && !msg.overwrite){
                MessageUploadResult result = new MessageUploadResult("File already exists and overwrite is disabled: " + targetFile.getAbsolutePath());
                out.writeObject(result);
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(msg.fileData);
            }

            MessageUploadResult result = new MessageUploadResult(targetFile.getAbsolutePath(), msg.fileSize, fileExists);
            out.writeObject(result);

            logInfo("File uploaded successfully: " + targetFile.getAbsolutePath() + " [size=" + msg.fileSize + " bytes, overwrite=" + fileExists + "]");
        } catch(Exception e){
            logError("File upload failed for " + username + ": " + e.getMessage());
            MessageUploadResult result = new MessageUploadResult("File upload failed: " + e.getMessage());
            out.writeObject(result);
        }
    }

    void downloadFile(MessageDownload msg) throws IOException {
        logInfo("File download request from " + username + ": " + msg.filePath);

        try {
            File file = new File(msg.filePath.isEmpty() ? currentDirectory : msg.filePath);
            if(!file.exists() || !file.isFile()){
                MessageDownloadResult result = new MessageDownloadResult("File not found: " + msg.filePath);
                out.writeObject(result);
                return;
            }

            if(!file.canRead()){
                MessageDownloadResult result = new MessageDownloadResult("Cannot read file: " + msg.filePath);
                out.writeObject(result);
                return;
            }

            long fileSize = file.length();
            long offset = msg.offset;
            long length = msg.length > 0 ? Math.min(fileSize - offset, msg.length) : fileSize - offset;

            byte[] fileData = new byte[(int) length];
            try (FileInputStream fis = new FileInputStream(file)) {
                BufferedInputStream bis = new BufferedInputStream(fis);

                bis.skip(offset);
                int bytesRead = bis.read(fileData);
                if(bytesRead != length){
                    throw new IOException("ailed to read complete file data");
                }
            }

            boolean isPartial = (offset > 0 || length < fileSize);
            MessageDownloadResult result = new MessageDownloadResult(file.getName(), fileSize, fileData, isPartial);
            out.writeObject(result);
            logInfo("File downloaded successfully: " + file.getAbsolutePath() + " [size=" + fileSize + " bytes, sent=" + length + " bytes, partial=" + isPartial + "]");
        } catch(Exception e){
            logError("File download failed for " + username + ": " + e.getMessage());
            MessageDownloadResult result = new MessageDownloadResult(
                    "File download failed: " + e.getMessage());
            out.writeObject(result);
        }
    }

    void changeDirectory(MessageChdir msg) throws IOException {
        logInfo("Directory change request from " + username + ": " + msg.newDirectory);

        try {
            File newDir = new File(msg.newDirectory);
            if(!newDir.exists() || !newDir.isDirectory()){
                MessageChdirResult result = new MessageChdirResult("Directory does not exist: " + msg.newDirectory);
                out.writeObject(result);
                return;
            }

            String oldDirectory = currentDirectory;
            currentDirectory = newDir.getAbsolutePath();

            MessageChdirResult result = new MessageChdirResult(currentDirectory, oldDirectory);
            out.writeObject(result);
            logInfo("Directory changed for " + username + " successfully: " + oldDirectory + " -> " + currentDirectory);
        } catch(Exception e){
            logError("Directory change failed for " + username + ": " + e.getMessage());
            MessageChdirResult result = new MessageChdirResult(
                    "Directory change failed: " + e.getMessage());
            out.writeObject(result);
        }
    }

    void getCurrentDirectory(MessageGetdir msg) throws IOException {
        logDebug("Current directory request from " + username);

        try {
            MessageGetdirResult result = new MessageGetdirResult(currentDirectory, 0);
            out.writeObject(result);
        } catch (Exception e) {
            logError("Get directory failed for " + username + ": " + e.getMessage());
            MessageGetdirResult result = new MessageGetdirResult(
                    "Get directory failed: " + e.getMessage());
            out.writeObject(result);
        }
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public void gracefulDisconnect(){
        gracefulShutdown = true;
        try {
            if (out != null && !disconnected) {
                MessageDisconnect disconnectMsg = new MessageDisconnect("Server is shutting down");
                out.writeObject(disconnectMsg);
                out.flush();
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

    public void disconnect() {
        if(!disconnected){
            try {
                if (gracefulShutdown) {
                    logInfo("Client gracefully disconnected: " + ip.getHostName() + " (" + username + ")");
                } else {
                    logInfo("Client disconnected: " + ip.getHostName() + " (" + username + ")");
                }
                unregister();
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                if (!gracefulShutdown) {
                    logError("Error while disconnecting: " + e.getMessage());
                }
            } finally {
                disconnected = true;
                this.interrupt();
            }
        }
    }

    private void unregister(){
        if(username != null){
            ServerMain.setUser(username, null);
            username = null;
        }
    }

    private ServerThread register(String username, String usernameFull){
        ServerThread old = ServerMain.registerUser(username, this);
        if(old == null){
            if(this.username == null){
                this.username = username;
                this.usernameFull = usernameFull;
                logInfo("User '" + usernameFull + "' registered as '" + username + "' from " + ip.getHostAddress());
            }
        }
        return old;
    }

    private void logInfo(String message) {
        System.out.println();
        Logger.logInfo(message);
        restorePrompt();
    }

    private void logWarning(String message) {
        System.out.println();
        Logger.logWarning(message);
        restorePrompt();
    }

    private void logError(String message) {
        System.out.println();
        Logger.logError(message);
        restorePrompt();
    }
    private void logDebug(String message) {
        System.out.println();
        Logger.logDebug(message);
        restorePrompt();
    }

    private void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}
