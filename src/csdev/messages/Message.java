package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>Message base class
 * @author cin-tie
 * @version 1.0
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private byte id;    // Message id

    public byte getId(){
        return id;
    }

    protected Message(){
        assert(false);
    }

    protected Message(byte id){
        assert(Protocol.validID(id));
        this.id = id;
    }
}