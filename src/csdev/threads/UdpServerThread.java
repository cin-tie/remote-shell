package csdev.threads;

import csdev.Protocol;
import csdev.messages.*;
import csdev.server.ServerMain;
import csdev.threads.session.UdpClientSession;
import csdev.utils.Logger;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>UDP server thread for handling client connections
 * @author cin-tie
 * @version 1.0
 */
public class UdpServerThread extends Thread {

    private DatagramSocket socket;
    private boolean running = true;
    private byte[] buffer = new byte[4096];

    private ConcurrentHashMap<String, UdpClientSession> sessions = new ConcurrentHashMap<>();
    private ThreadPoolExecutor executor;

    public  UdpServerThread() throws  IOException {
        this.socket = new DatagramSocket(Protocol.PORT);
        this.running = true;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        this.setDaemon(true);
    }

    public void run() {
        Logger.logServer("UDP Server started on port " + Protocol.PORT);

        while (running && !ServerMain.getStopFlag()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                executor.execute(() -> processPacket(packet));
            } catch (IOException e){
                if (running && !ServerMain.getStopFlag()) {
                    Logger.logError("UDP Server error: " + e.getMessage());
                }
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        sessions.values().forEach(UdpClientSession::disconnect);
        sessions.clear();

        socket.close();
        Logger.logServer("UDP Server stopped");
    }

    private void processPacket(DatagramPacket packet) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(),  0, packet.getLength());
            ObjectInputStream ois = new ObjectInputStream(bais);
            Message msg = (Message) ois.readObject();

            String clientKey = getClientKey(packet.getAddress(), packet.getPort());
            UdpClientSession session = sessions.get(clientKey);

            if(session == null && msg.getId() != Protocol.CMD_CONNECT){
                Logger.logWarning("UDP packet from unknown client: " + clientKey);
                return;
            }

            processMessage(msg, packet.getAddress(), packet.getPort(), session);
        } catch (Exception e){
            Logger.logError("Error processing UDP packet: " + e.getMessage());
        }
    }

    private void processMessage(Message msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        switch (msg.getId()) {
            case Protocol.CMD_CONNECT:
                handleConnect((MessageConnect) msg, address, port);
                break;

            case Protocol.CMD_DISCONNECT:
                handleDisconnect(address, port);
                break;

            case Protocol.CMD_EXECUTE:
                handleExecute((MessageExecute) msg, address, port, session);
                break;

            case Protocol.CMD_UPLOAD:
                handleUpload((MessageUpload) msg, address, port, session);
                break;

            case Protocol.CMD_DOWNLOAD:
                handleDownload((MessageDownload) msg, address, port, session);
                break;

            case Protocol.CMD_CHDIR:
                handleChdir((MessageChdir) msg, address, port, session);
                break;

            case Protocol.CMD_GETDIR:
                handleGetdir((MessageGetdir) msg, address, port, session);
                break;

            default:
                Logger.logError("Unknown message type: " + msg.getId());
                break;
        }
    }

    private void handleConnect(MessageConnect msg, InetAddress address, int port) throws IOException {
        String clientKey = getClientKey(address, port);
        Logger.logInfo("UDP connecting attempt from: " + msg.username + "(" + msg.usernameFull + ") at " + clientKey);

        if(ServerMain.isPasswordRequired()){
            if(msg.password == null || !msg.password.equals(ServerMain.getServerPassword())){
                MessageConnectResult result = new MessageConnectResult("WrongPassword");
                sendMessage(address, port, result);
                Logger.logWarning("UDP connection rejected - invalid password for user: " + msg.username);
                return;
            }
        }

        if(ServerMain.getUser(msg.username) != null){
            MessageConnectResult result = new MessageConnectResult("User already connected: " + msg.username);
            sendMessage(address, port, result);
            Logger.logWarning("UDP connection rejected - user already connected: " + msg.username);
            return;
        }

        UdpClientSession session = new UdpClientSession(address, port, this);
        session.registerUser(msg.username, msg.usernameFull);
        sessions.put(clientKey, session);

        String serverOS = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String serverVersion = "Remote Shell server 1.1";
        MessageConnectResult result = new MessageConnectResult(serverOS, session.getCurrentDirectory(), serverVersion);
        sendMessage(address, port, result);
        Logger.logInfo("User connected successfully via UDP: " + msg.username + " from " + clientKey);
    }

    private void handleDisconnect(InetAddress address, int port) throws IOException {
        String clientKey = getClientKey(address, port);
        UdpClientSession session = sessions.get(clientKey);
        if(session != null){
            session.disconnect();
            Logger.logInfo("UDP Client disconnected: " + session.getUsername() + " from " + clientKey);
        }
    }

    private void handleExecute(MessageExecute msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if(session == null)
            return;

        Logger.logInfo("Executing UDP command for " + session.getUsername() + ": " + msg.command);

        try {
            String command = msg.command;
            String workingDir = (msg.workingDir == null || msg.workingDir.isEmpty()) ? session.getCurrentDirectory() : msg.workingDir;
            long timeout = msg.timeMillis > 0 ? msg.timeMillis : 30000;

            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(new File(workingDir));

            long startTime = System.currentTimeMillis();
            Process process = pb.start();

            boolean finished = false;
            try {
                finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                process.destroy();
                Thread.currentThread().interrupt();
                throw new IOException("Command execution interrupted");
            }

            long executionTime = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                MessageExecuteResult result = new MessageExecuteResult("Command timed out after " + timeout + "ms");
                session.sendMessage(result);
                Logger.logWarning("UDP Command timeout for " + session.getUsername() + ": " + command);
                return;
            }

            String output = session.readStream(process.getInputStream());
            String error = session.readStream(process.getErrorStream());
            int exitCode = process.exitValue();

            MessageExecuteResult result = new MessageExecuteResult(output, error, exitCode, executionTime, workingDir);
            session.sendMessage(result);

            Logger.logInfo("UDP Command completed for " + session.getUsername() + " [exitCode=" + exitCode + ", time=" + executionTime + "ms]");

        } catch (Exception e) {
            Logger.logError("UDP Command execution failed for " + session.getUsername() + ": " + e.getMessage());
            MessageExecuteResult result = new MessageExecuteResult("Command execution failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleUpload(MessageUpload msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        Logger.logInfo("Uploading file via UDP from " + session.getUsername() + ": " + msg.fileName);

        try {
            File targetDir = new File(msg.filePath.isEmpty() ? session.getCurrentDirectory() : msg.filePath);
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                MessageUploadResult result = new MessageUploadResult("Invalid target directory: " + msg.filePath);
                session.sendMessage(result);
                return;
            }

            File targetFile = new File(targetDir, msg.fileName);
            boolean fileExists = targetFile.exists();

            if (fileExists && !msg.overwrite) {
                MessageUploadResult result = new MessageUploadResult("File already exists and overwrite is disabled: " + targetFile.getAbsolutePath());
                session.sendMessage(result);
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(msg.fileData);
            }

            MessageUploadResult result = new MessageUploadResult(targetFile.getAbsolutePath(), msg.fileSize, fileExists);
            session.sendMessage(result);

            Logger.logInfo("UDP File uploaded successfully: " + targetFile.getAbsolutePath() + " [size=" + msg.fileSize + " bytes, overwrite=" + fileExists + "]");
        } catch (Exception e) {
            Logger.logError("UDP File upload failed for " + session.getUsername() + ": " + e.getMessage());
            MessageUploadResult result = new MessageUploadResult("File upload failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleDownload(MessageDownload msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        Logger.logInfo("UDP File download request from " + session.getUsername() + ": " + msg.filePath);

        try {
            File file = new File(msg.filePath.isEmpty() ? session.getCurrentDirectory() : msg.filePath);
            if (!file.exists() || !file.isFile()) {
                MessageDownloadResult result = new MessageDownloadResult("File not found: " + msg.filePath);
                session.sendMessage(result);
                return;
            }

            if (!file.canRead()) {
                MessageDownloadResult result = new MessageDownloadResult("Cannot read file: " + msg.filePath);
                session.sendMessage(result);
                return;
            }

            long fileSize = file.length();
            long offset = msg.offset;
            long length = msg.length > 0 ? Math.min(fileSize - offset, msg.length) : fileSize - offset;

            if (length > 60000) {
                MessageDownloadResult result = new MessageDownloadResult("File too large for UDP download. Use TCP for large files.");
                session.sendMessage(result);
                return;
            }

            byte[] fileData = new byte[(int) length];
            try (FileInputStream fis = new FileInputStream(file)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                bis.skip(offset);
                int bytesRead = bis.read(fileData);
                if (bytesRead != length) {
                    throw new IOException("Failed to read complete file data");
                }
            }

            boolean isPartial = (offset > 0 || length < fileSize);
            MessageDownloadResult result = new MessageDownloadResult(file.getName(), fileSize, fileData, isPartial);
            session.sendMessage(result);
            Logger.logInfo("UDP File downloaded successfully: " + file.getAbsolutePath() + " [size=" + fileSize + " bytes, sent=" + length + " bytes, partial=" + isPartial + "]");
        } catch (Exception e) {
            Logger.logError("UDP File download failed for " + session.getUsername() + ": " + e.getMessage());
            MessageDownloadResult result = new MessageDownloadResult("File download failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleChdir(MessageChdir msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        Logger.logInfo("UDP Directory change request from " + session.getUsername() + ": " + msg.newDirectory);

        try {
            File newDir = new File(msg.newDirectory);
            if (!newDir.exists() || !newDir.isDirectory()) {
                MessageChdirResult result = new MessageChdirResult("Directory does not exist: " + msg.newDirectory);
                session.sendMessage(result);
                return;
            }

            String oldDirectory = session.getCurrentDirectory();
            session.setCurrentDirectory(newDir.getAbsolutePath());

            MessageChdirResult result = new MessageChdirResult(session.getCurrentDirectory(), oldDirectory);
            session.sendMessage(result);
            Logger.logInfo("UDP Directory changed for " + session.getUsername() + " successfully: " + oldDirectory + " -> " + session.getCurrentDirectory());
        } catch (Exception e) {
            Logger.logError("UDP Directory change failed for " + session.getUsername() + ": " + e.getMessage());
            MessageChdirResult result = new MessageChdirResult("Directory change failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleGetdir(MessageGetdir msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        Logger.logDebug("UDP Current directory request from " + session.getUsername());

        try {
            MessageGetdirResult result = new MessageGetdirResult(session.getCurrentDirectory(), 0);
            session.sendMessage(result);
        } catch (Exception e) {
            Logger.logError("UDP Get directory failed for " + session.getUsername() + ": " + e.getMessage());
            MessageGetdirResult result = new MessageGetdirResult("Get directory failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    public void sendMessage(InetAddress address, int port, Message msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream  oos = new ObjectOutputStream(baos);

        oos.writeObject(msg);
        oos.flush();

        byte[] data = baos.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    private String getClientKey(InetAddress address, int port) {
        return address.getHostAddress() +  ":" + port;
    }

    public void stopServer() {
        running = false;
        socket.close();
    }

    public void removeSession(String clientKey) {
        sessions.remove(clientKey);
    }
}