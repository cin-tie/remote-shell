package csdev;

/**
 * <p>CMD interface: Client message IDs
 * @author cin-tie
 * @version 1.0
 */
interface CMD{
    byte CMD_CONNECT     = 1;  // Connect
    byte CMD_DISCONNECT  = 2;  // Disconnect
    byte CMD_EXECUTE     = 3;  // Execute shell command
    byte CMD_UPLOAD      = 4;  // Upload file to server
    byte CMD_DOWNLOAD    = 5;  // Download file from server
    byte CMD_CHDIR       = 6;  // Change directory
    byte CMD_GETDIR      = 7;  // Get current directory
}

/**
 * <p>RESULT interface: Result codes
 * @author cin-tie
 * @version 1.0
 */
interface RESULT{
    int RESULT_CODE_OK    = 1;  // Ok
    int RESULT_CODE_ERROR = -1; // Error
}

/**
 * <p>PORT interface: Port #
 * @author cin-tie
 * @version 1.0
 */
interface PORT{
    int PORT = 8072;
}

interface PROTOCOL{
    String TCP = "TCP";
    String UDP = "UDP";
}

/**
 * <p>Protocol class: Protocol constants
 * @author cin-tie
 * @version 1.0
 */
public class Protocol implements CMD, RESULT, PORT,  PROTOCOL{
    private static final byte CMD_MIN = CMD_CONNECT;
    private static final byte CMD_MAX = CMD_GETDIR;

    public static boolean validID(byte id){
        return id >= CMD_MIN && id <= CMD_MAX;
    }
    public static boolean validProtocol(String protocol){ return TCP.equalsIgnoreCase(protocol) || UDP.equalsIgnoreCase(protocol); }
}