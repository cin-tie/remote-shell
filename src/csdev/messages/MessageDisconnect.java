package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageDisconnect class: client disconnection message
 * @author cin-tie
 * @version 1.0
 */
public class MessageDisconnect extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String reason;       // Reason of disconnection

    public MessageDisconnect() {
        super(Protocol.CMD_DISCONNECT);
        this.reason = "Client disconnected";
    }

    public MessageDisconnect(String reason) {
        super(Protocol.CMD_DISCONNECT);
        this.reason = reason;
    }
}
