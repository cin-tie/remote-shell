package csdev.threads;

import csdev.Protocol;
import csdev.messages.*;
import csdev.server.ServerMain;
import csdev.threads.session.RmiClientSession;
import csdev.utils.Logger;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RmiServerThread extends Thread implements RemoteShellService{

    private Registry registry;
    private boolean running = true;
    private ConcurrentHashMap<String, RmiClientSession> sessions = new ConcurrentHashMap<>();

    public RmiServerThread() throws RemoteException {
        super();
        this.setDaemon(true);
        this.setName("RmiServerThread");
    }

    @Override
    public void run() {
        try {
            RemoteShellService stub = (RemoteShellService) UnicastRemoteObject.exportObject(this, 0);
            registry = LocateRegistry.createRegistry(Protocol.RMI_PORT);
            registry.rebind("RmiServerThread", stub);

            Logger.logServer("RMI Server started on port: " + Protocol.RMI_PORT);

            while (running && !ServerMain.getStopFlag()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e){
            Logger.logError("RMI Server error: " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    @Override
    public MessageConnectResult connect(MessageConnect msg, String sessionId){
        logInfo("RMI connecting attempt from: " + msg.username + "(" + msg.usernameFull + ")");

        if(ServerMain.isPasswordRequired()){
            if(msg.password == null || !ServerMain.getServerPassword().equals(msg.password)){
                MessageConnectResult result = new MessageConnectResult("Wrong password");
                logWarning("RMI connection rejected - invalid password for user: " + msg.username);
                return result;
            }
        }

        if(sessions.containsKey(sessionId)){
            MessageConnectResult result = new MessageConnectResult("User already connected: " + msg.username);
            logWarning("UDP connection rejected - user already connected: " + msg.username);
            return result;
        }

        RmiClientSession session = new RmiClientSession();
        session.registerUser(msg.username, msg.usernameFull);
        sessions.put(sessionId, session);

        String serverOS = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String serverVersion = "Remote Shell server 1.0";
        MessageConnectResult result = new MessageConnectResult(serverOS, session.getCurrentDirectory(), serverVersion);
        logInfo("User connected successfully via RMI: " + msg.username);
        return result;
    }

    @Override
    public void disconnect(MessageDisconnect msg, String sessionId) throws RemoteException {
        String username = sessions.get(sessionId).getUsername();
        sessions.remove(sessionId);
        logInfo("RMI Client disconnected: " + username);
    }

    @Override
    public MessageResult processCommand(Message msg, String sessionId) throws RemoteException {
        if(msg == null || sessionId == null){
            return new MessageExecuteResult("Invalid message: null");
        }
        RmiClientSession session = sessions.get(sessionId);
        if(session == null){
            return new MessageExecuteResult("Invalid message: session not found");
        }

        try {
            switch (msg.getId()){
                case Protocol.CMD_EXECUTE:
                    return processExecuteCommand((MessageExecute) msg, session);
                case Protocol.CMD_UPLOAD:
                    return processUploadCommand((MessageUpload) msg, session);
                case Protocol.CMD_DOWNLOAD:
                    return processDownloadCommand((MessageDownload) msg, session);
                case Protocol.CMD_CHDIR:
                    return processChdirCommand((MessageChdir) msg, session);
                case Protocol.CMD_GETDIR:
                    return processGetdirCommand((MessageGetdir) msg, session);
                default:
                    return new MessageExecuteResult("Unknown command type: " + msg.getId());
            }
        } catch (Exception e){
            logError("RMI Command processing error: " + e.getMessage());
            return new MessageExecuteResult("Command processing failed: " + e.getMessage());
        }
    }

    private MessageResult processExecuteCommand(MessageExecute msg, RmiClientSession session) throws RemoteException {
        logInfo("Executing RMI command for " + session.getUsername() + ": " + msg.command);

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
                logWarning("RMI Command timeout for " + session.getUsername() + ": " + command);
                return result;
            }

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());
            int exitCode = process.exitValue();

            MessageExecuteResult result = new MessageExecuteResult(output, error, exitCode, executionTime, workingDir);

            logInfo("RMI Command completed for " + session.getUsername() + " [exitCode=" + exitCode + ", time=" + executionTime + "ms]");
            return result;

        } catch (Exception e) {
            logError("RMI Command execution failed for " + session.getUsername() + ": " + e.getMessage());
            MessageExecuteResult result = new MessageExecuteResult("Command execution failed: " + e.getMessage());
            return result;
        }
    }

    private MessageUploadResult processUploadCommand(MessageUpload msg, RmiClientSession session) throws RemoteException{
        logInfo("Uploading file via RMI from " + session.getUsername() + ": " + msg.fileName);

        try {
            File targetDir = new File(msg.filePath.isEmpty() ? session.getCurrentDirectory() : msg.filePath);
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                MessageUploadResult result = new MessageUploadResult("Invalid target directory: " + msg.filePath);
                return result;
            }

            File targetFile = new File(targetDir, msg.fileName);
            boolean fileExists = targetFile.exists();

            if (fileExists && !msg.overwrite) {
                MessageUploadResult result = new MessageUploadResult("File already exists and overwrite is disabled: " + targetFile.getAbsolutePath());
                return result;
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(msg.fileData);
            }

            MessageUploadResult result = new MessageUploadResult(targetFile.getAbsolutePath(), msg.fileSize, fileExists);

            logInfo("RMI File uploaded successfully: " + targetFile.getAbsolutePath() + " [size=" + msg.fileSize + " bytes, overwrite=" + fileExists + "]");
            return result;
        } catch (Exception e) {
            logError("RMI File upload failed for " + session.getUsername() + ": " + e.getMessage());
            MessageUploadResult result = new MessageUploadResult("File upload failed: " + e.getMessage());
            return result;
        }
    }

    private MessageDownloadResult processDownloadCommand(MessageDownload msg, RmiClientSession session) throws RemoteException {
        logInfo("RMI File download request from " + session.getUsername() + ": " + msg.filePath);

        try {
            File file = new File(msg.filePath.isEmpty() ? session.getCurrentDirectory() : msg.filePath);
            if (!file.exists() || !file.isFile()) {
                MessageDownloadResult result = new MessageDownloadResult("File not found: " + msg.filePath);
                return result;
            }

            if (!file.canRead()) {
                MessageDownloadResult result = new MessageDownloadResult("Cannot read file: " + msg.filePath);
                return result;
            }

            long fileSize = file.length();
            byte[] fileData = new byte[(int) fileSize];
            try (FileInputStream fis = new FileInputStream(file)) {
                BufferedInputStream bis = new BufferedInputStream(fis);
                int bytesRead = bis.read(fileData);
                if (bytesRead != fileSize) {
                    throw new IOException("Failed to read complete file data");
                }
            }

            MessageDownloadResult result = new MessageDownloadResult(file.getName(), fileSize, fileData, false, false);
            logInfo("RMI Small file downloaded: " + msg.filePath + " [size=" + fileSize + " bytes]");
            return result;

        } catch (Exception e) {
            logError("RMI File download failed for " + session.getUsername() + ": " + e.getMessage());
            MessageDownloadResult result = new MessageDownloadResult("File download failed: " + e.getMessage());
            return result;
        }
    }

    private MessageChdirResult processChdirCommand(MessageChdir msg, RmiClientSession session) throws RemoteException {
        logInfo("RMI Directory change request from " + session.getUsername() + ": " + msg.newDirectory);

        try {
            File newDir = new File(msg.newDirectory);
            if (!newDir.exists() || !newDir.isDirectory()) {
                MessageChdirResult result = new MessageChdirResult("Directory does not exist: " + msg.newDirectory);
                return result;
            }

            String oldDirectory = session.getCurrentDirectory();
            session.setCurrentDirectory(newDir.getAbsolutePath());

            MessageChdirResult result = new MessageChdirResult(session.getCurrentDirectory(), oldDirectory);
            logInfo("RMI Directory changed for " + session.getUsername() + " successfully: " + oldDirectory + " -> " + session.getCurrentDirectory());
            return result;
        } catch (Exception e) {
            logError("RMI Directory change failed for " + session.getUsername() + ": " + e.getMessage());
            MessageChdirResult result = new MessageChdirResult("Directory change failed: " + e.getMessage());
            return result;
        }
    }

    private MessageGetdirResult processGetdirCommand(MessageGetdir msg, RmiClientSession session) throws RemoteException {
        logDebug("RMI Current directory request from " + session.getUsername());

        try {
            MessageGetdirResult result = new MessageGetdirResult(session.getCurrentDirectory(), 0);
            return result;
        } catch (Exception e) {
            logError("UDP Get directory failed for " + session.getUsername() + ": " + e.getMessage());
            MessageGetdirResult result = new MessageGetdirResult("Get directory failed: " + e.getMessage());
            return result;
        }
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public void stopServer(){
        running = false;
        try{
            if(registry != null){
                registry.unbind("RmiServerThread");
            }
            UnicastRemoteObject.unexportObject(this, true);
        } catch(Exception e){
            Logger.logError("Error stopping RMI Server: " + e.getMessage());
        }
        Logger.logServer("RMI Server stopped");
    }

    protected void logInfo(String message) {
        System.out.print(" ");
        Logger.logInfo(message);
        restorePrompt();
    }

    protected void logWarning(String message) {
        System.out.print(" ");
        Logger.logWarning(message);
        restorePrompt();
    }

    private void logDebug(String message) {
        if (Logger.getDebugEnabled()) {
            System.out.print(" ");
            Logger.logDebug(message);
            restorePrompt();
        }
    }

    private void logError(String message) {
        System.out.print(" ");
        Logger.logError(message);
        restorePrompt();
    }

    private void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}
