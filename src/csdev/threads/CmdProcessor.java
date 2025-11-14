package csdev.threads;

/**
 * <p>Command processor interface
 * @author cin-tie
 * @version 1.0
 */
public interface CmdProcessor {
    void putHandler(String shortName, String fullName, CmdHandler handler);

    int lastError();
    boolean command(String cmd);
    boolean command(String cmd, int[] err);
}
