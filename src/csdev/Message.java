package csdev;

import java.io.Serializable;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private byte id;

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