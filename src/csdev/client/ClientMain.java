package csdev.client;

import csdev.Protocol;
import csdev.messages.*;
import csdev.utils.Logger;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * <p>Main class of client application
 * <p>Remote shell client for MacOS/Linux/Unix servers
 * <br>Use arguments: userNic userFullName [host]
 * @author cin-tie
 * @version 1.0
 */
public class ClientMain {

    public static void main(String[] args) {
        Logger.logClient("Starting Remote Shell Client...");

        if(args.length < 2 || args.length > 3) {
            Logger.logError("Invalid number of arguments\nUse: nic name [host]");
            waitKeyToStop();
            return;
        }

        String host = args.length == 2 ? "localhost" : args[2];
        Logger.logInfo("Connecting to " + host + " as " + args[0] + " (" + args[1] + ")");

        try (Socket sock = new Socket(host, Protocol.PORT)) {
            Logger.logClient("Client initialized");
            session(sock, args[0], args[1]);
        } catch (Exception e) {
            Logger.logError("Connection failed: " + e.getMessage());
        } finally {
            Logger.logClient("Client shutdown");
        }
    }

    static void waitKeyToStop(){
        Logger.logInfo("Press enter to stop...");
        try {
            System.in.read();
        } catch (Exception e) {
        }
    }

    static class Session {
        boolean connected = false;
        String username = null;
        String usernameFull = null;
        String currentDirectory = "";
        String serverOS = "";

        Session(String username, String usernameFull){
            this.username = username;
            this.usernameFull = usernameFull;
        }
    }

    static void session(Socket socket, String username, String usernameFull){
        try(Scanner in = new Scanner(System.in);
            ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream())){

            Session s = new Session(username, usernameFull);
            if(open)
        } catch (Exception e){
            Logger.logError("Session Error: " + e.getMessage());
        }
    }

    static boolean openSession(Session s, ObjectInputStream is, ObjectOutputStream os, Scanner in) throws IOException, ClassNotFoundException {
        Logger.logDebug("Sending connection request...");
        os.writeObject(new MessageConnect(s.username, s.usernameFull));
        MessageConnectResult msg = (MessageConnectResult) is.readObject();

        if(!msg.Error()){
            s.connected = true;
            s.serverOS = msg.serverOS;
            s.currentDirectory = msg.currentDir;
            Logger.logInfo("Connected to server: " + msg.serverOS);
            Logger.logInfo("Current directory: " + msg.currentDir);
            Logger.logInfo("Server version: " + msg.serverVersion);
            return true;
        }

        Logger.logError("Unable to connect: " + msg.getErrorMessage());
        Logger.logInfo("Press Enter to continue...");
        if (in.hasNextLine()) {
            in.nextLine();
        }
        return false;
    }

    static void closeSession(Session s, ObjectOutputStream os) throws IOException {
        if(s.connected){
            s.connected = false;
            os.writeObject(new MessageDisconnect("Client shutdown"));
            Logger.logInfo("Disconnected from server");
        }
    }

    static void displayWelcome(Session s){
        System.out.println("\n" + "=".repeat(60));
        System.out.println("    REMOTE SHELL CLIENT");
        System.out.println("=".repeat(60));
        System.out.println("User: " + s.usernameFull + " (" + s.username + ")");
        System.out.println("Server: " + s.serverOS);
        System.out.println("Current directory: " + s.currentDirectory);
        System.out.println("-".repeat(60));
        System.out.println("Available commands:");
        System.out.println("  (e)xecute  - Execute shell command");
        System.out.println("  (u)pload   - Upload file to server");
        System.out.println("  (d)ownload - Download file from server");
        System.out.println("  (c)d       - Change directory");
        System.out.println("  (p)wd      - Print working directory");
        System.out.println("  (q)uit     - Exit client");
        System.out.println("=".repeat(60) + "\n");
    }

    static Message getCommand(Session ses, Scanner in) {
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

        return new MessageDownload(remotePath);
    }

    static MessageChdir inputChdir(Scanner in) {
        System.out.print("Enter new directory: ");
        String newDir = in.nextLine().trim();
        if (newDir.isEmpty()) {
            return null;
        }

        return new MessageChdir(newDir);
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
    }

    static byte translateCmd(String str) {
        str = str.trim().toLowerCase();
        Byte r = commands.get(str);
        return (r == null ? 0 : r.byteValue());
    }

    static void printPrompt(Session ses) {
        System.out.print(ses.username + "@" + ses.currentDirectory + "> ");
        System.out.flush();
    }

    static boolean processCommand(Session s, Message msg, ObjectInputStream is, ObjectOutputStream os)
            throws IOException, ClassNotFoundException {
        if (msg != null) {
            Logger.logDebug("Sending command type: " + msg.getId());
            os.writeObject(msg);
            MessageResult res = (MessageResult) is.readObject();

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
                        printDownloadResult((MessageDownloadResult) res);
                        break;
                    case Protocol.CMD_CHDIR:
                        printChdirResult(s, (MessageChdirResult) res);
                        break;
                    case Protocol.CMD_GETDIR:
                        printGetdirResult(s, (MessageGetdirResult) res);
                        break;
                    default:
                        Logger.logWarning("Unknown result type: " + res.getId());
                        break;
                }
            }
            return true;
        }
        return false;
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

    static void printDownloadResult(MessageDownloadResult msg){
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FILE DOWNLOAD RESULT");
        System.out.println("=".repeat(60));
        System.out.println("File name: " + msg.fileName);
        System.out.println("Total size: " + msg.fileSize + " bytes");
        System.out.println("Downloaded: " + msg.dataSize + " bytes");
        System.out.println("Partial: " + msg.isPartial);

        if (msg.fileData != null && msg.dataSize > 0) {
            System.out.println("\nFile content preview (first 500 bytes):");
            String preview = new String(msg.fileData, 0, (int)Math.min(500, msg.dataSize));
            System.out.println(preview);
            if (msg.dataSize > 500) {
                System.out.println("... [truncated]");
            }
        }

        System.out.println("=".repeat(60));
    }

    static void printChdirResult(Session s, MessageChdirResult msg){
        s.currentDirectory = msg.newDirectory;
        System.out.println("Directory changed: " + msg.oldDirectory + " -> " + msg.newDirectory);
    }

    static void printGetdirResult(Session s, MessageGetdirResult msg){
        s.currentDirectory = msg.currentDirectory;
        System.out.println("Current directory: " + msg.currentDirectory);
    }
}
