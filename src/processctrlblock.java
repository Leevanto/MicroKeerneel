import java.util.LinkedList;

public class processctrlblock {
    private static int nextPid = 0;

    private final int pid;
    private final String name;
    private final Process process;

    private final LinkedList<message> messageQueue;

    // Why this process last stopped. Only the kernel reads or writes it.
    private processstate state = processstate.RUNNABLE;

    protected processctrlblock(String name, Process process) {
        this.pid = nextPid++;
        this.name = name;
        this.process = process;
        this.messageQueue = new LinkedList<>();
    }

    protected int getPid() {
        return pid;
    }

    protected String getName() {
        return name;
    }

    protected Process getProcess() {
        return process;
    }

    protected LinkedList<message> getMessageQueue() {
        return messageQueue;
    }

    protected synchronized void setState(processstate state) {
        this.state = state;
    }

    protected synchronized processstate getState() {
        return state;
    }

    // A fresh boot numbers processes from 0 again, so the kernel is always pid 0.
    protected static void resetPids() {
        nextPid = 0;
    }

    // if process is waiting for `reason` now clears, so it can run again.
    // Checking/changing synchronized step never overwrites a different state.
    protected synchronized boolean readyIf(processstate reason) {
        if (state == reason) {
            state = processstate.RUNNABLE;
            return true;
        }
        return false;
    }
}
