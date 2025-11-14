package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageDownload class: download file from server
 * @author cin-tie
 * @version 1.0
 */
public class MessageDownload extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String filePath;     // Full path to file on server
    public long offset;         // File offset for partial download
    public long length;         // Number of bytes to download

    public MessageDownload(String filePath){
        super(Protocol.CMD_DOWNLOAD);
        this.filePath = filePath;
        this.offset = 0;
        this.length = -1;   // Download entire file
    }

    public MessageDownload(String filePath, long offset, long length){
        super(Protocol.CMD_DOWNLOAD);
        this.filePath = filePath;
        this.offset = offset;
        this.length = length;
    }
}
