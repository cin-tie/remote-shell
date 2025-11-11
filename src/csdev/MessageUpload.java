package csdev;

import java.io.Serializable;

/**
 * <p>MessageUpload class: upload file to server
 * @author cin-tie
 * @version 1.0
 */
public class MessageUpload extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String fileName;     // Target file name on server
    public String filePath;     // Target path on server
    public long fileSize;       // File size in bytes
    public byte[] fileData;     // File content
    public boolean overwrite;   // Overwrite if exists

    public  MessageUpload(String fileName, String filePath, byte[] fileData){
        super(Protocol.CMD_UPLOAD);
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileData = fileData;
        this.overwrite = false;
        this.fileSize = fileData != null ? fileData.length : 0;
    }

    public MessageUpload(String fileName, String filePath, byte[] fileData, boolean overwrite){
        super(Protocol.CMD_UPLOAD);
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileData = fileData;
        this.overwrite = overwrite;
        this.fileSize = fileData != null ? fileData.length : 0;
    }
}
