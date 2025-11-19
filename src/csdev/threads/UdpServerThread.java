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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>UDP server thread for handling client connections
 * (modified to support fragment upload/download with ACKs)
 * @author cin-tie
 * @version 1.1
 */
public class UdpServerThread extends Thread {

    private DatagramSocket socket;
    private boolean running = true;
    private byte[] buffer = new byte[65536];

    private static final int MAX_FRAGMENT_SIZE = 4000;
    private static final int FRAGMENT_TIMEOUT = 5000;
    private static final int MAX_RETRIES = 5;

    private ConcurrentHashMap<String, UdpClientSession> sessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FileTransferSession> fileSessions = new ConcurrentHashMap<>();
    private ThreadPoolExecutor executor;

    private static class FileTransferSession {
        public String fileId;
        public String clientKey;
        public int totalFragments;
        public int receivedFragments;
        public byte[][] fragments;
        public long lastActivity;
        public boolean isDownload;

        public String fileName;
        public String targetDir;
        public boolean overwrite;
        public long fileSize;

        public boolean[] acked;

        public FileTransferSession(String fileId, String clientKey, int totalFragments, boolean isDownload) {
            this.fileId = fileId;
            this.clientKey = clientKey;
            this.totalFragments = totalFragments;
            this.receivedFragments = 0;
            this.fragments = new byte[totalFragments][];
            this.lastActivity = System.currentTimeMillis();
            this.isDownload = isDownload;
            if (isDownload) {
                this.acked = new boolean[totalFragments];
            }
        }
    }

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

                DatagramPacket pCopy = new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort());
                executor.execute(() -> processPacket(pCopy));
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
                logWarning("UDP packet from unknown client: " + clientKey + " msgId=" + msg.getId());
                return;
            }

            processMessage(msg, packet.getAddress(), packet.getPort(), session);
        } catch (Exception e){
            logError("Error processing UDP packet: " + e.getMessage());
        }
    }

    private void processMessage(Message msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (msg instanceof MessageFragment) {
            handleFragment((MessageFragment) msg, address, port, session);
            return;
        } else if (msg instanceof MessageFragmentResult) {
            handleFragmentAck((MessageFragmentResult) msg, address, port);
            return;
        }

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
                logError("Unknown message type: " + msg.getId());
                break;
        }
    }

    private void handleConnect(MessageConnect msg, InetAddress address, int port) throws IOException {
        String clientKey = getClientKey(address, port);
        logInfo("UDP connecting attempt from: " + msg.username + "(" + msg.usernameFull + ") at " + clientKey);

        if(ServerMain.isPasswordRequired()){
            if(msg.password == null || !msg.password.equals(ServerMain.getServerPassword())){
                MessageConnectResult result = new MessageConnectResult("WrongPassword");
                sendMessage(address, port, result);
                logWarning("UDP connection rejected - invalid password for user: " + msg.username);
                return;
            }
        }

        if(ServerMain.getUser(msg.username) != null){
            MessageConnectResult result = new MessageConnectResult("User already connected: " + msg.username);
            sendMessage(address, port, result);
            logWarning("UDP connection rejected - user already connected: " + msg.username);
            return;
        }

        UdpClientSession session = new UdpClientSession(address, port, this);
        session.registerUser(msg.username, msg.usernameFull);
        sessions.put(clientKey, session);

        String serverOS = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String serverVersion = "Remote Shell server 1.1";
        MessageConnectResult result = new MessageConnectResult(serverOS, session.getCurrentDirectory(), serverVersion);
        sendMessage(address, port, result);
        logInfo("User connected successfully via UDP: " + msg.username + " from " + clientKey);
    }

    private void handleDisconnect(InetAddress address, int port) throws IOException {
        String clientKey = getClientKey(address, port);
        UdpClientSession session = sessions.get(clientKey);
        if(session != null){
            session.disconnect();
            sessions.remove(clientKey);
            logInfo("UDP Client disconnected: " + session.getUsername() + " from " + clientKey);
        }
    }

    private void handleExecute(MessageExecute msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if(session == null)
            return;

        logInfo("Executing UDP command for " + session.getUsername() + ": " + msg.command);

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
                logWarning("UDP Command timeout for " + session.getUsername() + ": " + command);
                return;
            }

            String output = session.readStream(process.getInputStream());
            String error = session.readStream(process.getErrorStream());
            int exitCode = process.exitValue();

            MessageExecuteResult result = new MessageExecuteResult(output, error, exitCode, executionTime, workingDir);
            session.sendMessage(result);

            logInfo("UDP Command completed for " + session.getUsername() + " [exitCode=" + exitCode + ", time=" + executionTime + "ms]");

        } catch (Exception e) {
            logError("UDP Command execution failed for " + session.getUsername() + ": " + e.getMessage());
            MessageExecuteResult result = new MessageExecuteResult("Command execution failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleUpload(MessageUpload msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        logInfo("Uploading file via UDP from " + session.getUsername() + ": " + msg.fileName);

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

            logInfo("UDP File uploaded successfully: " + targetFile.getAbsolutePath() + " [size=" + msg.fileSize + " bytes, overwrite=" + fileExists + "]");
        } catch (Exception e) {
            logError("UDP File upload failed for " + session.getUsername() + ": " + e.getMessage());
            MessageUploadResult result = new MessageUploadResult("File upload failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleDownload(MessageDownload msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        logInfo("UDP File download request from " + session.getUsername() + ": " + msg.filePath);

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

            if (fileSize <= MAX_FRAGMENT_SIZE) {
                sendSmallFile(file, session, msg.filePath);
                return;
            }

            sendLargeFileFragmented(file, session, address, port, msg.filePath);

        } catch (Exception e) {
            logError("UDP File download failed for " + session.getUsername() + ": " + e.getMessage());
            MessageDownloadResult result = new MessageDownloadResult("File download failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void sendSmallFile(File file, UdpClientSession session, String filePath) throws IOException {
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
        session.sendMessage(result);
        logInfo("UDP Small file downloaded: " + filePath + " [size=" + fileSize + " bytes]");
    }

    private void sendLargeFileFragmented(File file, UdpClientSession session, InetAddress address, int port, String filePath) {
        new Thread(() -> {
            try {
                long fileSize = file.length();
                byte[] all = new byte[(int) fileSize];
                try (FileInputStream fis = new FileInputStream(file)) {
                    BufferedInputStream bis = new BufferedInputStream(fis);
                    int read = bis.read(all);
                    if (read != fileSize) {
                        logError("Failed to read full file for fragmentation: " + filePath);
                        session.sendMessage(new MessageDownloadResult("Failed to read file for fragmentation: " + filePath));
                        return;
                    }
                }

                int total = (all.length + MAX_FRAGMENT_SIZE - 1) / MAX_FRAGMENT_SIZE;
                String fileId = file.getName() + "_" + System.currentTimeMillis();
                logInfo("Starting fragmented download to " + session.getUsername() + ": file=" + filePath + " size=" + all.length + " fragments=" + total + " fileId=" + fileId);

                FileTransferSession fts = new FileTransferSession(fileId, getClientKey(address, port), total, true);
                fileSessions.put(getSessionKey(fileId, getClientKey(address, port)), fts);

                for (int idx = 0; idx < total; idx++) {
                    int start = idx * MAX_FRAGMENT_SIZE;
                    int end = Math.min(start + MAX_FRAGMENT_SIZE, all.length);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(all, start, chunk, 0, chunk.length);

                    byte fragType;
                    if (idx == 0) fragType = MessageFragment.FRAGMENT_START;
                    else if (idx == total - 1) fragType = MessageFragment.FRAGMENT_END;
                    else fragType = MessageFragment.FRAGMENT_MIDDLE;

                    MessageFragment frag = new MessageFragment(fragType, total, idx, fileId, file.getName(), chunk, chunk.length);

                    boolean ackReceived = false;
                    int tries = 0;
                    while (!ackReceived && tries < MAX_RETRIES) {
                        tries++;
                        sendMessage(address, port, frag);
                        logDebug("Sent fragment to " + session.getUsername() + " idx=" + idx + " try=" + tries);
                        long waitStart = System.currentTimeMillis();
                        synchronized (fts) {
                            long waited = 0;
                            while (!fts.acked[idx] && waited < FRAGMENT_TIMEOUT) {
                                long toWait = FRAGMENT_TIMEOUT - waited;
                                try {
                                    fts.wait(toWait);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                waited = System.currentTimeMillis() - waitStart;
                            }
                            ackReceived = fts.acked[idx];
                        }

                        if (!ackReceived) {
                            logWarning("No ACK from " + session.getUsername() + " for fragment " + idx + " (try " + tries + ")");
                        } else {
                            logDebug("ACK received for fragment " + idx + " from " + session.getUsername());
                        }
                    }

                    if (!ackReceived) {
                        logError("Failed to receive ACK for fragment " + idx + " after " + MAX_RETRIES + " tries. Aborting transfer.");
                        fileSessions.remove(getSessionKey(fileId, getClientKey(address, port)));
                        session.sendMessage(new MessageDownloadResult("Failed to send file: transfer aborted (missing ACKs)"));
                        return;
                    }
                }

                logInfo("Fragmented download finished for " + session.getUsername() + " fileId=" + fileId);
                MessageDownloadResult finalMsg = new MessageDownloadResult(file.getName(), file.length(), null, false, true);
                session.sendMessage(finalMsg);
                fileSessions.remove(getSessionKey(fileId, getClientKey(address, port)));
            } catch (Exception e) {
                logError("Error in fragmented download thread: " + e.getMessage());
                try {
                    session.sendMessage(new MessageDownloadResult("File download failed: " + e.getMessage()));
                } catch (IOException ioException) {
                    logError("Failed to notify client about download failure: " + ioException.getMessage());
                }
            }
        }).start();

    }

    private void handleChdir(MessageChdir msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        logInfo("UDP Directory change request from " + session.getUsername() + ": " + msg.newDirectory);

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
            logInfo("UDP Directory changed for " + session.getUsername() + " successfully: " + oldDirectory + " -> " + session.getCurrentDirectory());
        } catch (Exception e) {
            logError("UDP Directory change failed for " + session.getUsername() + ": " + e.getMessage());
            MessageChdirResult result = new MessageChdirResult("Directory change failed: " + e.getMessage());
            session.sendMessage(result);
        }
    }

    private void handleGetdir(MessageGetdir msg, InetAddress address, int port, UdpClientSession session) throws IOException {
        if (session == null) return;

        logDebug("UDP Current directory request from " + session.getUsername());

        try {
            MessageGetdirResult result = new MessageGetdirResult(session.getCurrentDirectory(), 0);
            session.sendMessage(result);
        } catch (Exception e) {
            logError("UDP Get directory failed for " + session.getUsername() + ": " + e.getMessage());
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

    private String getSessionKey(String fileId, String clientKey) {
        return clientKey + ":" + fileId;
    }

    public void stopServer() {
        running = false;
        socket.close();
    }

    public int getNumUsers(){
        return sessions.keySet().size();
    }

    public String[] getUsers(){
        return sessions.keySet().toArray(new String[0]);
    }

    public void removeSession(String clientKey) {
        sessions.remove(clientKey);
    }

    private void handleFragment(MessageFragment msg, InetAddress address, int port, UdpClientSession session) {
        String clientKey = getClientKey(address, port);
        String key = getSessionKey(msg.fileId, clientKey);

        try {
            FileTransferSession fts = fileSessions.get(key);

            if (fts == null && msg.fragmentType == MessageFragment.FRAGMENT_START) {
                byte[] payload = msg.data;
                if (payload == null || payload.length < 4) {
                    logWarning("Invalid fragment start without header from " + clientKey);
                    MessageFragmentResult nack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, false);
                    sendMessage(address, port, nack);
                    return;
                }
                int headerLen = ((payload[0] & 0xFF) << 24) | ((payload[1] & 0xFF) << 16) | ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
                if (headerLen < 0 || headerLen > payload.length - 4) {
                    logWarning("Invalid header length in fragment start from " + clientKey);
                    MessageFragmentResult nack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, false);
                    sendMessage(address, port, nack);
                    return;
                }
                byte[] headerBytes = new byte[headerLen];
                System.arraycopy(payload, 4, headerBytes, 0, headerLen);
                String headerJson = new String(headerBytes, "UTF-8");
                String fn = extractJsonString(headerJson, "fileName");
                String td = extractJsonString(headerJson, "targetDir");
                String ovStr = extractJsonString(headerJson, "overwrite");
                String fsStr = extractJsonString(headerJson, "fileSize");
                boolean overwrite = "true".equalsIgnoreCase(ovStr) || "1".equals(ovStr);
                long fileSize = 0;
                try {
                    fileSize = Long.parseLong(fsStr);
                } catch (Exception ignore) {}

                fts = new FileTransferSession(msg.fileId, clientKey, msg.totalFragments, false);
                fts.fileName = fn;
                fts.targetDir = td;
                fts.overwrite = overwrite;
                fts.fileSize = fileSize;
                fileSessions.put(key, fts);
                logInfo("Created upload session for " + clientKey + " fileId=" + msg.fileId + " fileName=" + fn + " totalFragments=" + msg.totalFragments);
                int remainder = payload.length - 4 - headerLen;
                if (remainder > 0) {
                    byte[] chunk = new byte[remainder];
                    System.arraycopy(payload, 4 + headerLen, chunk, 0, remainder);
                    fts.fragments[msg.fragmentIndex] = chunk;
                    fts.receivedFragments++;
                }
            } else if (fts == null) {
                logWarning("Received non-start fragment for unknown upload session: fileId=" + msg.fileId + " from " + clientKey);
                MessageFragmentResult nack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, false);
                sendMessage(address, port, nack);
                return;
            } else {
                if (fts.fragments[msg.fragmentIndex] == null) {
                    fts.fragments[msg.fragmentIndex] = msg.data;
                    fts.receivedFragments++;
                } else {
                    logDebug("Duplicate fragment " + msg.fragmentIndex + " for " + msg.fileId + " from " + clientKey);
                }
            }

            MessageFragmentResult ack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, true);
            sendMessage(address, port, ack);

            if (fts.receivedFragments == fts.totalFragments) {
                logInfo("All fragments received for upload fileId=" + msg.fileId + " from " + clientKey + " assembling...");
                assembleAndSaveUpload(fts, address, port, session);
                fileSessions.remove(key);
            }
        } catch (Exception e) {
            logError("Error handling fragment from " + clientKey + ": " + e.getMessage());
            try {
                MessageFragmentResult nack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, false);
                sendMessage(address, port, nack);
            } catch (IOException ignored) {}
        }
    }

    private void handleFragmentAck(MessageFragmentResult ack, InetAddress address, int port) {
        String clientKey = getClientKey(address, port);
        String key = getSessionKey(ack.fileId, clientKey);
        FileTransferSession fts = fileSessions.get(key);
        if (fts == null) {
            logWarning("Received fragment ACK for unknown transfer: fileId=" + ack.fileId + " from " + clientKey);
            return;
        }
        if (!fts.isDownload) {
            logWarning("Received fragment ACK for upload-session (?) fileId=" + ack.fileId + " from " + clientKey);
            return;
        }
        if (ack.fragmentIndex < 0 || ack.fragmentIndex >= fts.totalFragments) {
            logWarning("Received fragment ACK index out of range: " + ack.fragmentIndex);
            return;
        }
        if (ack.received) {
            synchronized (fts) {
                fts.acked[ack.fragmentIndex] = true;
                fts.lastActivity = System.currentTimeMillis();
                fts.notifyAll();
            }
            logDebug("ACK registered for fragment " + ack.fragmentIndex + " fileId=" + ack.fileId + " from " + clientKey);
        } else {
            logWarning("Negative ACK for fragment " + ack.fragmentIndex + " fileId=" + ack.fileId + " from " + clientKey);
        }
    }

    private void assembleAndSaveUpload(FileTransferSession fts, InetAddress address, int port, UdpClientSession session) {
        try {
            int totalSize = 0;
            for (int i = 0; i < fts.totalFragments; i++) {
                byte[] b = fts.fragments[i];
                if (b != null) totalSize += b.length;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
            for (int i = 0; i < fts.totalFragments; i++) {
                byte[] b = fts.fragments[i];
                if (b != null) baos.write(b);
            }

            byte[] fileData = baos.toByteArray();

            String targetDir = (fts.targetDir == null || fts.targetDir.isEmpty()) ? (session == null ? "." : session.getCurrentDirectory()) : fts.targetDir;
            File td = new File(targetDir);
            if (!td.exists() || !td.isDirectory()) {
                MessageUploadResult res = new MessageUploadResult("Invalid target directory: " + targetDir);
                if (session != null) session.sendMessage(res);
                return;
            }

            File outFile = new File(td, fts.fileName);
            boolean existed = outFile.exists();
            if (existed && !fts.overwrite) {
                MessageUploadResult res = new MessageUploadResult("File already exists and overwrite is disabled: " + outFile.getAbsolutePath());
                if (session != null) session.sendMessage(res);
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(fileData);
            }

            MessageUploadResult res = new MessageUploadResult(outFile.getAbsolutePath(), fileData.length, existed);
            if (session != null) session.sendMessage(res);
            logInfo("Saved uploaded file: " + outFile.getAbsolutePath() + " size=" + fileData.length);
        } catch (Exception e) {
            logError("Failed to assemble/save uploaded file: " + e.getMessage());
            try {
                if (session != null) session.sendMessage(new MessageUploadResult("File upload failed: " + e.getMessage()));
            } catch (IOException ignored) {}
        }
    }

    private String extractJsonString(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int pos = json.indexOf(pattern);
            if (pos < 0) return "";
            int start = pos + pattern.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
                if (json.charAt(start) == '\"') break;
                start++;
            }
            if (json.charAt(start) == '\"') {
                start++;
                int end = json.indexOf("\"", start);
                if (end < 0) return json.substring(start);
                return json.substring(start, end);
            } else {
                int end = start;
                while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-' || json.charAt(end) == 't' || json.charAt(end) == 'f' || json.charAt(end) == 'r' || json.charAt(end) == 'u' || json.charAt(end) == 'e' || json.charAt(end) == 'a' || json.charAt(end) == 'l' || json.charAt(end) == 's' || json.charAt(end) == 'n' )) {
                    end++;
                }
                return json.substring(start, end).replaceAll("[\",}]", "").trim();
            }
        } catch (Exception e) {
            return "";
        }
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
        if(Logger.getDebugEnabled()) {
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
