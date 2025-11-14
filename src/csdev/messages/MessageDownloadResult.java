package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageDownloadResult class: file download result
 * @author cin-tie
 * @version 1.0
 */
public class MessageDownloadResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public String fileName;         // Original file name
    public long fileSize;           // Total file size
    public long dataSize;           // Actual downloaded data size
    public byte[] fileData;         // File content
    public boolean isPartial;       // Whether this is a partial download

    public MessageDownloadResult(String errorMessage){
        super(Protocol.CMD_DOWNLOAD, errorMessage);
        this.fileName = "";
        this.fileSize = 0;
        this.dataSize = 0;
        this.fileData = null;
        this.isPartial = false;
    }

    public MessageDownloadResult(String fileName, long fileSize, byte[] fileData,  boolean isPartial){
        super(Protocol.CMD_DOWNLOAD);
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileData = fileData;
        this.dataSize = fileData != null ? fileData.length : 0;
        this.isPartial = isPartial;
    }
}
