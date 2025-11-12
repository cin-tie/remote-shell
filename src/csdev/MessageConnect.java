package csdev;

import java.io.Serializable;

/**
 * <p>MessageConnect class: Client connection request
 * @author cin-tie
 * @version 1.0
 */
public class MessageConnect extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String username;         // Nickname
    public String usernameFull;     // Full name
    public String password;         // Password for auth

    public MessageConnect(String username, String usernameFull){
        super(Protocol.CMD_CONNECT);
        this.username = username;
        this.usernameFull = usernameFull;
        this.password = "";
    }

    public MessageConnect(String username, String usernameFull, String password){
        super(Protocol.CMD_CONNECT);
        this.username = username;
        this.usernameFull = usernameFull;
        this.password = password;
    }
}
