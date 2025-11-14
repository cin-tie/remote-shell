package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageUploadResult class: file upload result
 * @author cin-tie
 * @version 1.0
 */
public class MessageUploadResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public String filePath;         // Uploaded file path
    public long fileSize;           // Uploaded file size
    public boolean fileExists;      // Existence

    public MessageUploadResult(String errorMessage) {
        super(Protocol.CMD_UPLOAD, errorMessage);
        this.filePath = "";
        this.fileSize = 0;
        this.fileExists = false;
    }

    public  MessageUploadResult(String filePath, long fileSize, boolean fileExists) {
        super(Protocol.CMD_UPLOAD);
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileExists = fileExists;
    }
}
