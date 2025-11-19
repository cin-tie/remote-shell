package csdev.messages;

import java.io.Serializable;

public class MessageFragmentResult extends MessageResult implements Serializable {
    public String fileId;
    public int fragmentIndex;
    public boolean received;

    public MessageFragmentResult(String fileId, int fragmentIndex, boolean received) {
        this.fileId = fileId;
        this.fragmentIndex = fragmentIndex;
        this.received = received;
    }

    @Override
    public boolean Error() {
        return !received;
    }

    @Override
    public String getErrorMessage() {
        return received ? null : "Fragment transmission failed";
    }
}
