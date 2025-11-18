package csdev.messages;

import java.io.Serializable;

public class MessageFragment extends Message implements Serializable {
    public static final byte FRAGMENT_START = 1;
    public static final byte FRAGMENT_MIDDLE = 2;
    public static final byte FRAGMENT_END = 3;

    public byte fragmentType;
    public int totalFragments;
    public int fragmentIndex;
    public String fileId;
    public byte[] data;
    public int dataSize;

    public MessageFragment(byte fragmentType, int totalFragments, int fragmentIndex, String fileId, byte[] data, int dataSize) {
        this.fragmentType = fragmentType;
        this.totalFragments = totalFragments;
        this.fragmentIndex = fragmentIndex;
        this.fileId = fileId;
        this.data = data;
        this.dataSize = dataSize;
    }
}
