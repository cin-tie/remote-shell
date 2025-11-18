package csdev.client;

import csdev.Protocol;
import csdev.messages.*;
import csdev.utils.Logger;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Main class of client application using UDP protocol
 * <p>Remote shell client for MacOS/Linux/Unix servers
 * <br>Use arguments: userNic userFullName host [password]
 * @author cin-tie (modified)
 * @version 1.1
 *
 */
public class UdpClientMain {

    public static final int CLIENT_MAX_FRAGMENT_SIZE = 4000;
    private static final int CLIENT_FRAGMENT_ACK_TIMEOUT = 5000; // ms
    private static final int CLIENT_MAX_RETRIES = 5;

    public static void main(String[] args) {
        Logger.logClient("Starting Remote Shell UDP Client...");

        if(args.length < 3 || args.length > 4) {
            Logger.logError("Invalid number of arguments\nUse: nic name host [password]");
            Logger.logError("Examples:");
            Logger.logError("       john \"John Doe\" localhost");
            Logger.logError("       john \"John Doe\" localhost mypassword");
            waitKeyToStop();
            return;
        }

        String password = args.length == 4 ? args[3] : "";
        String host = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(host);
            Logger.logClient("UDP Client initialized");
            session(socket, serverAddress, args[0], args[1], password);
        } catch (Exception e) {
            Logger.logError("UDP Connection failed: " + e.getMessage());
        } finally {
            Logger.logClient("UDP Client shutdown");
        }
    }

    private static ConcurrentHashMap<String, FileAssemblySession> assemblySessions = new ConcurrentHashMap<>();

    private static class FileAssemblySession {
        public String fileId;
        public int totalFragments;
        public int receivedFragments;
        public byte[][] fragments;
        public long lastActivity;
        public String fileName;

        public FileAssemblySession(String fileId, int totalFragments, String fileName) {
            this.fileId = fileId;
            this.totalFragments = totalFragments;
            this.receivedFragments = 0;
            this.fragments = new byte[totalFragments][];
            this.lastActivity = System.currentTimeMillis();
            this.fileName = fileName;
        }
    }

    static void waitKeyToStop(){
        Logger.logInfo("Press enter to stop...");
        try {
            System.in.read();
        } catch (Exception e) {
        }
    }

    static class UdpSession{
        boolean connected = false;
        String username = null;
        String usernameFull = null;
        String password = "";
        String currentDirectory = "";
        String serverOS = "";
        InetAddress serverAddress;
        int serverPort = Protocol.PORT;

        public UdpSession(String username, String usernameFull, String password, InetAddress serverAddress)
        {
            this.username = username;
            this.usernameFull = usernameFull;
            this.password = password;
            this.serverAddress = serverAddress;
        }
    }

    static void session(DatagramSocket socket, InetAddress serverAddress, String username, String usernameFull, String password){
        try (Scanner in = new Scanner(System.in)) {
            UdpSession s = new UdpSession(username, usernameFull, password, serverAddress);
            if(openSession(s, socket, in)){
                try {
                    displayWelcome(s);
                    while (s.connected) {
                        Message msg = getCommand(s, in);
                        if(msg == null) {
                            break;
                        }
                        if (!processCommand(s, msg, socket, in)) {
                            break;
                        }
                    }
                } finally {
                    closeSession(s, socket);
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("Server is shutting down") &&
                    !e.getMessage().contains("Connection reset") &&
                    !e.getMessage().contains("Обрыв канала")) {
                Logger.logError("UDP Session Error: " + e.getMessage());
            } else {
                Logger.logInfo("Disconnected from server: " + e.getMessage());
            }
        }
    }

    static boolean openSession(UdpSession s, DatagramSocket socket, Scanner in) throws IOException, ClassNotFoundException {
        Logger.logDebug("Sending UDP connection request...");
        MessageConnect messageConnect = new MessageConnect(s.username, s.usernameFull, s.password);
        sendMessage(socket, s.serverAddress, s.serverPort, messageConnect);
        MessageConnectResult msg = (MessageConnectResult) recieveMessage(socket, 30000);

        if(msg != null && !msg.Error()){
            s.connected = true;
            s.serverOS = msg.serverOS;
            s.currentDirectory = msg.currentDir;
            Logger.logInfo("Connected via UDP to server: " + msg.serverOS);
            Logger.logInfo("Current directory: " + msg.currentDir);
            Logger.logInfo("Server version: " + msg.serverVersion);
            return true;
        }

        if (msg == null) {
            Logger.logError("No response from server (timeout).");
        } else {
            Logger.logError("Unable to connect via UDP: " + msg.getErrorMessage());
        }
        Logger.logInfo("Press Enter to continue...");
        if (in.hasNextLine()) {
            in.nextLine();
        }
        return false;
    }

    static void closeSession(UdpSession s, DatagramSocket socket) throws IOException {
        if(s.connected) {
            s.connected = false;
            MessageDisconnect messageDisconnect = new MessageDisconnect("Client shutdown");
            sendMessage(socket, s.serverAddress, Protocol.PORT, messageDisconnect);
            Logger.logInfo("Disconnected from TCP server");
        }
    }

    private static void sendMessage(DatagramSocket socket, InetAddress address, int port, Message msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(msg);
        oos.flush();
        byte[] data = baos.toByteArray();
        DatagramPacket dp = new DatagramPacket(data, data.length, address, port);
        socket.send(dp);
    }

    private static Message recieveMessage(DatagramSocket socket, int timeout) throws IOException, ClassNotFoundException {
        socket.setSoTimeout(timeout);

        byte[] data = new byte[65536];
        DatagramPacket dp = new DatagramPacket(data, data.length);

        try{
            socket.receive(dp);
            ByteArrayInputStream bais = new ByteArrayInputStream(dp.getData(), 0, dp.getLength());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (Message) ois.readObject();
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    static void displayWelcome(UdpSession s){
        System.out.println("\n" + "=".repeat(60));
        System.out.println("    REMOTE SHELL CLIENT");
        System.out.println("=".repeat(60));
        System.out.println("User: " + s.usernameFull + " (" + s.username + ")");
        System.out.println("Server: " + s.serverOS);
        System.out.println("Protocol: UDP");
        System.out.println("Current directory: " + s.currentDirectory);
        displayHelp();
    }

    static void displayHelp(){
        System.out.println("-".repeat(60));
        System.out.println("Available commands:");
        System.out.println("  (h)elp     - Watch all available commands");
        System.out.println("  (e)xecute  - Execute shell command");
        System.out.println("  (u)pload   - Upload file to server");
        System.out.println("  (d)ownload - Download file from server");
        System.out.println("  (c)d       - Change directory");
        System.out.println("  (p)wd      - Print working directory");
        System.out.println("  (q)uit     - Exit client");
        System.out.println("=".repeat(60) + "\n");
    }

    static Message getCommand(UdpSession ses, Scanner in) {
        while (true) {
            printPrompt(ses);
            if (!in.hasNextLine())
                break;
            String str = in.nextLine().trim();

            if (str.isEmpty()) {
                continue;
            }

            byte cmd = translateCmd(str);
            switch (cmd) {
                case -1:
                    return null;
                case -2:
                    displayHelp();
                    break;
                case Protocol.CMD_EXECUTE:
                    return inputExecute(in);
                case Protocol.CMD_UPLOAD:
                    return inputUpload(in);
                case Protocol.CMD_DOWNLOAD:
                    return inputDownload(in);
                case Protocol.CMD_CHDIR:
                    return inputChdir(in);
                case Protocol.CMD_GETDIR:
                    return new MessageGetdir();
                case 0:
                    Logger.logWarning("Unknown command: " + str);
                    System.out.println("Unknown command. Type 'help' for available commands.");
                    continue;
                default:
                    Logger.logWarning("Unhandled command: " + str);
            }
        }
        return null;
    }

    static MessageExecute inputExecute(Scanner in) {
        System.out.print("Enter command to execute: ");
        String command = in.nextLine().trim();
        if (command.isEmpty()) {
            return null;
        }
        System.out.print("Working directory [current]: ");
        String workingDir = in.nextLine().trim();
        System.out.print("Timeout in ms [30000]: ");
        String timeoutStr = in.nextLine().trim();

        long timeout = 30000;
        try {
            if (!timeoutStr.isEmpty()) {
                timeout = Long.parseLong(timeoutStr);
            }
        } catch (NumberFormatException e) {
            Logger.logWarning("Invalid timeout, using default: " + timeout);
        }

        return new MessageExecute(command, workingDir.isEmpty() ? null : workingDir, timeout);
    }


    static MessageUpload inputUpload(Scanner in) {
        System.out.print("Enter local file path: ");
        String localPath = in.nextLine().trim();
        if (localPath.isEmpty()) {
            return null;
        }

        System.out.print("Enter target directory on server [current]: ");
        String targetDir = in.nextLine().trim();

        System.out.print("Overwrite if exists? (y/n) [n]: ");
        String overwriteStr = in.nextLine().trim();
        boolean overwrite = overwriteStr.equalsIgnoreCase("y");

        try {
            File file = new File(localPath);
            if (!file.exists() || !file.isFile()) {
                Logger.logError("File not found: " + localPath);
                return null;
            }

            byte[] fileData = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                bis.read(fileData);
            }

            // Attach file path info in filePath field, same as before
            return new MessageUpload(file.getName(), targetDir, fileData, overwrite);

        } catch (IOException e) {
            Logger.logError("Error reading file: " + e.getMessage());
            return null;
        }
    }

    static MessageDownload inputDownload(Scanner in) {
        System.out.print("Enter remote file path: ");
        String remotePath = in.nextLine().trim();
        if (remotePath.isEmpty()) {
            return null;
        }

        System.out.print("Download offset [0]: ");
        String offsetStr = in.nextLine().trim();
        long offset = 0;
        try {
            if (!offsetStr.isEmpty()) {
                offset = Long.parseLong(offsetStr);
            }
        } catch (NumberFormatException e) {
            Logger.logWarning("Invalid offset, using default: 0");
        }

        System.out.print("Download length [full file]: ");
        String lengthStr = in.nextLine().trim();
        long length = -1;
        try {
            if (!lengthStr.isEmpty()) {
                length = Long.parseLong(lengthStr);
            }
        } catch (NumberFormatException e) {
            Logger.logWarning("Invalid length, downloading full file");
        }

        return new MessageDownload(remotePath, offset, length);
    }

    static MessageChdir inputChdir(Scanner in) {
        System.out.print("Enter new directory: ");
        String newDir = in.nextLine().trim();
        if (newDir.isEmpty()) {
            return null;
        }

        return new MessageChdir(newDir);
    }

    static boolean processCommand(UdpSession s, Message msg, DatagramSocket socket, Scanner in)
            throws IOException, ClassNotFoundException {

        if (msg != null) {
            Logger.logDebug("Sending command type: " + msg.getId());

            // Для команд загрузки файлов используем фрагментированную отправку
            if (msg.getId() == Protocol.CMD_UPLOAD && ((MessageUpload) msg).fileData.length > CLIENT_MAX_FRAGMENT_SIZE) {
                handleFragmentedUpload((MessageUpload) msg, socket, s.serverAddress, Protocol.PORT, in);
                return true;
            }

            sendMessage(socket, s.serverAddress, Protocol.PORT, msg);

            while (true) {
                Message incoming = recieveMessage(socket, 30000);

                if (incoming == null) {
                    Logger.logWarning("Timeout waiting for server response.");
                    return false;
                }

                if (incoming instanceof MessageFragment) {
                    handleFileFragment((MessageFragment) incoming, socket, s.serverAddress, Protocol.PORT, in);
                    continue;
                } else if (incoming instanceof MessageFragmentResult) {
                    handleFragmentAck((MessageFragmentResult) incoming, socket, s.serverAddress, Protocol.PORT);
                    continue;
                }

                if (incoming instanceof MessageResult) {
                    MessageResult res = (MessageResult) incoming;

                    if (res.Error()) {
                        Logger.logError("Server error: " + res.getErrorMessage());
                        System.out.println("Error: " + res.getErrorMessage());
                    } else {
                        switch (res.getId()) {
                            case Protocol.CMD_EXECUTE:
                                printExecuteResult((MessageExecuteResult) res);
                                break;
                            case Protocol.CMD_UPLOAD:
                                printUploadResult((MessageUploadResult) res);
                                break;
                            case Protocol.CMD_DOWNLOAD:
                                printDownloadResult((MessageDownloadResult) res, in);
                                break;
                            case Protocol.CMD_CHDIR:
                                printChdirResult(s, (MessageChdirResult) res);
                                break;
                            case Protocol.CMD_GETDIR:
                                printGetdirResult(s, (MessageGetdirResult) res);
                                break;
                            default:
                                Logger.logWarning("Unknown result type: " + res.getId());
                        }
                    }
                    return true;
                }

                Logger.logWarning("Unknown UDP message type received: " + incoming.getClass().getSimpleName());
            }
        }
        return false;
    }

    /**
     * Handle sending a large file by fragmentation (client side upload).
     */
    private static void handleFragmentedUpload(MessageUpload up, DatagramSocket socket, InetAddress address, int port, Scanner in)
            throws IOException, ClassNotFoundException {

        byte[] fileData = up.fileData;
        int totalFragments = (fileData.length + CLIENT_MAX_FRAGMENT_SIZE - 1) / CLIENT_MAX_FRAGMENT_SIZE;
        String fileId = up.fileName + "_" + System.currentTimeMillis();

        Logger.logInfo("Starting fragmented upload: " + up.fileName + " size=" + fileData.length +
                " fragments=" + totalFragments + " fileId=" + fileId);

        // Отправляем начальный фрагмент с метаданными
        for (int fragmentIndex = 0; fragmentIndex < totalFragments; fragmentIndex++) {
            int start = fragmentIndex * CLIENT_MAX_FRAGMENT_SIZE;
            int end = Math.min(start + CLIENT_MAX_FRAGMENT_SIZE, fileData.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileData, start, chunk, 0, chunk.length);

            byte fragmentType;
            if (fragmentIndex == 0) {
                fragmentType = MessageFragment.FRAGMENT_START;
            } else if (fragmentIndex == totalFragments - 1) {
                fragmentType = MessageFragment.FRAGMENT_END;
            } else {
                fragmentType = MessageFragment.FRAGMENT_MIDDLE;
            }

            // Для первого фрагмента добавляем метаданные
            byte[] payload;
            int payloadSize;

            if (fragmentType == MessageFragment.FRAGMENT_START) {
                // Создаем JSON с метаданными
                String metadataJson = String.format(
                        "{\"fileName\":\"%s\",\"targetDir\":\"%s\",\"overwrite\":%b,\"fileSize\":%d}",
                        escapeJson(up.fileName),
                        escapeJson(up.filePath == null ? "" : up.filePath),
                        up.overwrite,
                        fileData.length
                );
                byte[] metadataBytes = metadataJson.getBytes("UTF-8");

                // Формируем payload: [4 байта длина метаданных][метаданные][данные файла]
                payloadSize = 4 + metadataBytes.length + chunk.length;
                payload = new byte[payloadSize];

                // Записываем длину метаданных (big-endian)
                payload[0] = (byte) ((metadataBytes.length >> 24) & 0xFF);
                payload[1] = (byte) ((metadataBytes.length >> 16) & 0xFF);
                payload[2] = (byte) ((metadataBytes.length >> 8) & 0xFF);
                payload[3] = (byte) (metadataBytes.length & 0xFF);

                // Копируем метаданные
                System.arraycopy(metadataBytes, 0, payload, 4, metadataBytes.length);
                // Копируем данные файла
                System.arraycopy(chunk, 0, payload, 4 + metadataBytes.length, chunk.length);
            } else {
                payload = chunk;
                payloadSize = chunk.length;
            }

            MessageFragment fragment = new MessageFragment(
                    fragmentType, totalFragments, fragmentIndex, fileId, up.fileName, payload, payloadSize
            );

            // Отправляем фрагмент с повторными попытками
            boolean ackReceived = false;
            int attempts = 0;

            while (!ackReceived && attempts < CLIENT_MAX_RETRIES) {
                attempts++;
                sendMessage(socket, address, port, fragment);
                Logger.logDebug("Sent upload fragment " + fragmentIndex + "/" + (totalFragments - 1) + " attempt=" + attempts);

                // Ждем подтверждения
                Message response = recieveMessage(socket, CLIENT_FRAGMENT_ACK_TIMEOUT);

                if (response instanceof MessageFragmentResult) {
                    MessageFragmentResult ack = (MessageFragmentResult) response;
                    if (ack.fileId != null && ack.fileId.equals(fileId) &&
                            ack.fragmentIndex == fragmentIndex && ack.received) {
                        ackReceived = true;
                        Logger.logDebug("Received ACK for upload fragment " + fragmentIndex);
                        break;
                    }
                } else if (response instanceof MessageResult) {
                    MessageResult errorResult = (MessageResult) response;
                    if (errorResult.Error()) {
                        Logger.logError("Server error during upload: " + errorResult.getErrorMessage());
                        return;
                    }
                }

                if (!ackReceived && attempts < CLIENT_MAX_RETRIES) {
                    Logger.logWarning("No ACK for fragment " + fragmentIndex + ", retrying...");
                }
            }

            if (!ackReceived) {
                Logger.logError("Failed to send fragment " + fragmentIndex + " after " + CLIENT_MAX_RETRIES + " attempts");
                return;
            }
        }

        // Ждем финальный результат от сервера
        Logger.logInfo("All fragments uploaded, waiting for final result...");
        Message finalResult = recieveMessage(socket, 30000);

        if (finalResult instanceof MessageUploadResult) {
            printUploadResult((MessageUploadResult) finalResult);
        } else if (finalResult != null) {
            Logger.logError("Unexpected final message type: " + finalResult.getClass().getSimpleName());
        } else {
            Logger.logError("Timeout waiting for final upload result");
        }
    }

    /**
     * Обработка ACK для исходящих фрагментов (в текущей реализации не требуется,
     * так как мы обрабатываем ACK в основном цикле отправки)
     */
    private static void handleFragmentAck(MessageFragmentResult ack, DatagramSocket socket,
                                          InetAddress address, int port) throws IOException {
        // ACK уже обрабатываются в основном цикле отправки фрагментов
        Logger.logDebug("Received fragment ACK: fileId=" + ack.fileId + " index=" + ack.fragmentIndex);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    static TreeMap<String, Byte> commands = new TreeMap<String, Byte>();

    static {
        commands.put("q", (byte) -1);
        commands.put("quit", (byte) -1);
        commands.put("exit", (byte) -1);
        commands.put("e", Protocol.CMD_EXECUTE);
        commands.put("execute", Protocol.CMD_EXECUTE);
        commands.put("u", Protocol.CMD_UPLOAD);
        commands.put("upload", Protocol.CMD_UPLOAD);
        commands.put("d", Protocol.CMD_DOWNLOAD);
        commands.put("download", Protocol.CMD_DOWNLOAD);
        commands.put("c", Protocol.CMD_CHDIR);
        commands.put("cd", Protocol.CMD_CHDIR);
        commands.put("p", Protocol.CMD_GETDIR);
        commands.put("pwd", Protocol.CMD_GETDIR);
        commands.put("h", (byte) -2);
        commands.put("help", (byte) -2);
    }

    static byte translateCmd(String str) {
        str = str.trim().toLowerCase();
        Byte r = commands.get(str);
        return (r == null ? 0 : r.byteValue());
    }

    static void printPrompt(UdpSession s) {
        System.out.print(s.username + "@" + s.currentDirectory + "> ");
        System.out.flush();
    }

    static void printExecuteResult(MessageExecuteResult m) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("COMMAND EXECUTION RESULT");
        System.out.println("=".repeat(60));
        System.out.println("Exit code: " + m.exitCode);
        System.out.println("Execution time: " + m.executionTime + "ms");
        System.out.println("Working directory: " + m.workingDir);

        if (m.output != null && !m.output.isEmpty()) {
            System.out.println("\n--- STDOUT ---");
            System.out.print(m.output);
        }

        if (m.error != null && !m.error.isEmpty()) {
            System.out.println("--- STDERR ---");
            System.out.print(m.error);
        }

        System.out.println("=".repeat(60));
    }

    static void printUploadResult(MessageUploadResult msg) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FILE UPLOAD RESULT");
        System.out.println("=".repeat(60));
        System.out.println("File path: " + msg.filePath);
        System.out.println("File size: " + msg.fileSize + " bytes");
        System.out.println("File existed: " + msg.fileExists);
        System.out.println("Status: " + (msg.Error() ? "FAILED" : "SUCCESS"));
        System.out.println("=".repeat(60));
    }

    static void printDownloadResult(MessageDownloadResult msg, Scanner in){
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FILE DOWNLOAD RESULT");
        System.out.println("=".repeat(60));
        System.out.println("File name: " + msg.fileName);
        System.out.println("Total size: " + msg.fileSize + " bytes");
        System.out.println("Downloaded: " + (msg.isFragmented? msg.fileSize : msg.dataSize) + " bytes");
        System.out.println("Partial: " + msg.isPartial);

        if (msg.fileData != null && msg.dataSize > 0) {
            System.out.print("\nSave file to local disk? (y/n) [y]: ");
            String saveChoice = in.nextLine().trim().toLowerCase();

            if (saveChoice.isEmpty() || saveChoice.equals("y") || saveChoice.equals("yes")) {
                System.out.print("Enter local file path (absolute path): ");
                String localPath = in.nextLine().trim();

                if (localPath.isEmpty()) {
                    localPath = System.getProperty("user.dir") + File.separator + msg.fileName;
                } else {
                    File path = new File(localPath);
                    if (path.isDirectory() || localPath.endsWith(File.separator)) {
                        localPath = localPath + File.separator + msg.fileName;
                    }
                }

                try {
                    saveFileToDisk(msg.fileData, localPath, msg.dataSize, msg.fileName);
                } catch (IOException e) {
                    Logger.logError("Failed to save file: " + e.getMessage());
                    System.out.println("Error saving file: " + e.getMessage());
                }
            }

            if (msg.dataSize <= 5000) {
                System.out.println("\nFile content preview (first 500 bytes):");
                String preview = new String(msg.fileData, 0, (int)Math.min(500, msg.dataSize));
                System.out.println(preview);
                if (msg.dataSize > 500) {
                    System.out.println("... [truncated]");
                }
            } else {
                System.out.println("\nFile is too large for preview (" + msg.dataSize + " bytes)");
            }
        } else {
            if(!msg.isFragmented) {
                System.out.println("\nNo file data received or file is empty");
            }
        }

        System.out.println("=".repeat(60));
    }

    static void saveFileToDisk(byte[] fileData, String filePath, long dataSize, String fileName) throws IOException {
        File file = new File(filePath);

        // 🟩 Если пользователь указал директорию — автоматически добавляем имя файла
        if (file.isDirectory()) {
            System.out.println("Target is a directory. Appending filename automatically.");
            file = new File(file, fileName);
        }

        // 🟩 Если путь заканчивается "/" — значит указана директория
        if (filePath.endsWith(File.separator)) {
            file = new File(filePath + fileName);
        }

        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 🟩 Если файл существует — спрашиваем на перезапись
        if (file.exists()) {
            System.out.print("File already exists. Overwrite? (y/n) [n]: ");
            Scanner in = new Scanner(System.in);
            String overwrite = in.nextLine().trim().toLowerCase();
            if (!overwrite.equals("y") && !overwrite.equals("yes")) {
                System.out.println("File save cancelled.");
                return;
            }
        }

        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(fileData, 0, (int) dataSize);
            bos.flush();
        }

        System.out.println("File saved successfully: " + file.getAbsolutePath());
        System.out.println("File size: " + file.length() + " bytes");
    }


    static void printChdirResult(UdpSession s, MessageChdirResult msg){
        s.currentDirectory = msg.newDirectory;
        System.out.println("Directory changed: " + msg.oldDirectory + " -> " + msg.newDirectory);
    }

    static void printGetdirResult(UdpSession s, MessageGetdirResult msg){
        s.currentDirectory = msg.currentDirectory;
        System.out.println("Current directory: " + msg.currentDirectory);
    }

    /**
     * Handle incoming file fragment (from server -> client) and assemble.
     */
    private static void handleFileFragment(MessageFragment msg, DatagramSocket socket, InetAddress address, int port, Scanner in) throws IOException {
        FileAssemblySession session = assemblySessions.get(msg.fileId);


        if(session == null && msg.fragmentType == MessageFragment.FRAGMENT_START) {
            session = new FileAssemblySession(msg.fileId, msg.totalFragments, msg.fileName);
            assemblySessions.put(msg.fileId, session);
            Logger.logInfo("Starting file assembly: " + msg.fileId + " [fragments=" + msg.totalFragments + "]");
        }

        if(session != null) {
            session.fragments[msg.fragmentIndex] = msg.data;
            session.receivedFragments++;
            session.lastActivity = System.currentTimeMillis();

            MessageFragmentResult ack = new MessageFragmentResult(msg.fileId, msg.fragmentIndex, true);
            sendMessage(socket, address, port, ack);

            if (session.receivedFragments == session.totalFragments) {
                assembleAndSaveFile(session, in);
                assemblySessions.remove(msg.fileId);
            }
        } else {
            Logger.logWarning("Received fragment for unknown session (fileId=" + msg.fileId + ")");
        }
    }

    private static void assembleAndSaveFile(FileAssemblySession session, Scanner in) throws IOException {
        int totalSize = 0;
        for (byte[] fragment : session.fragments) {
            if (fragment != null) {
                totalSize += fragment.length;
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
        for (byte[] fragment : session.fragments) {
            if (fragment != null) {
                baos.write(fragment);
            }
        }

        byte[] fileData = baos.toByteArray();

        System.out.println("\nFile download completed: " + session.totalFragments + " fragments assembled");
        System.out.print("Save file to local disk? (y/n) [y]: ");
        String saveChoice = in.nextLine().trim().toLowerCase();

        if (saveChoice.isEmpty() || saveChoice.equals("y") || saveChoice.equals("yes")) {
            System.out.print("Enter local file path: ");
            String localPath = in.nextLine().trim();

            String defaultName = session.fileName != null ? session.fileName :
                    ("downloaded_" + System.currentTimeMillis());

            if (localPath.isEmpty()) {
                localPath = defaultName;
            }

            saveFileToDisk(fileData, localPath, totalSize, defaultName);
        }
    }
}
