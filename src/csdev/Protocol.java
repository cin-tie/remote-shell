package csdev;

/**
 * <p>CMD interface: Client message IDs
 * @author cin-tie
 * @version 1.0
 */
interface CMD{
    static final byte CMD_CONNECT     = 1;  // Connect
    static final byte CMD_DISCONNECT  = 2;  // Disconnect
    static final byte CMD_EXECUTE     = 3;  // Execute shell command
    static final byte CMD_UPLOAD      = 4;  // Upload file to server
    static final byte CMD_DOWNLOAD    = 5;  // Download file from server
    static final byte CMD_CHDIR       = 6;  // Change directory
    static final byte CMD_GETDIR      = 7;  // Get current directory
}

/**
 * <p>RESULT interface: Result codes
 * @author cin-tie
 * @version 1.0
 */
interface RESULT{
    static final int RESULT_CODE_OK    = 1;  // Ok
    static final int RESULT_CODE_ERROR = -1; // Error
}

/**
 * <p>PORT interface: Port #
 * @author cin-tie
 * @version 1.0
 */
interface PORT{
    static final int PORT = 8072;
}

/**
 * <p>Protocol class: Protocol constants
 * @author cin-tie
 * @version 1.0
 */
public class Protocol implements CMD, RESULT, PORT{
    private static final byte CMD_MIN = CMD_CONNECT;
    private static final byte CMD_MAX = CMD_GETDIR;

    private static boolean validID(byte id){
        return id >= CMD_MIN && id <= CMD_MAX;
    }
}