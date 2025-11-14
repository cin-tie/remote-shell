package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageExecuteResult class: command execution result
 * @author cin-tie
 * @version 1.0
 */
public class MessageExecuteResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public String output;        // Command stdout
    public String error;         // Command stderr
    public int exitCode;         // Command exit code
    public long executionTime;   // Execution time in milliseconds
    public String workingDir;    // Working directory where command was executed

    public MessageExecuteResult(String errorMessage){
        super(Protocol.CMD_EXECUTE, errorMessage);
        this.output = "";
        this.error = "";
        this.exitCode = -1;
        this.executionTime = 0;
        this.workingDir = "";
    }

    public MessageExecuteResult(String output, String error, int exitCode, long executionTime, String workingDir) {
        super(Protocol.CMD_EXECUTE);
        this.output = output;
        this.error = error;
        this.exitCode = exitCode;
        this.executionTime = executionTime;
        this.workingDir = workingDir;
    }
}
