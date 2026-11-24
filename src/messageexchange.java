import java.util.LinkedList;
import java.util.Optional;


public class messageexchange extends Process {


    private static final int MAX_PROCESSES = 100;
    private static final long LOOP_SLEEP_MS = 50;
    private static final int KERNEL_PID = 0;

    private final processctrlblock[] pcbs;
    //the registry this whole class,
    private int registered = 0;

    protected messageexchange(int pid, String name, int queueCapacity) {
        super(name, queueCapacity);
        this.pcbs = new processctrlblock[MAX_PROCESSES];
    }

    protected synchronized void registerProcess(processctrlblock pcb) {
        if (registered >= pcbs.length) {
            throw new IllegalStateException("MessageExchange is full - increase MAX_PROCESSES");
        }
        pcbs[registered++] = pcb;
    }

    @Override
    public void run() {
        while (true) {
            int count;
            synchronized (this) {
                count = registered;
            }

            for (int i = 0; i < count; i++) {
                processctrlblock pcb = pcbs[i];
                if (pcb == null) {
                    continue;
                }
                deliverBacklog(pcb);
                deliverOutgoing(pcb);
                deliverForKernel(pcb);
            }
            try {
                Thread.sleep(LOOP_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void deliverBacklog(processctrlblock pcb) {
        LinkedList<message> backlog = pcb.getMessageQueue();

        while (!backlog.isEmpty()) {
            message m = backlog.peek();
            if (pcb.getProcess().getIncomingQueue().writeMessage(m)) {
                backlog.poll();
                pcb.readyIf(processstate.INBOX_EMPTY); // it has mail now
            } else {
                // Still full - stop here to preserve order; retry next pass.
                break;
            }
        }
    }

    private void deliverOutgoing(processctrlblock pcb) {
        Optional<message> maybeMessage = pcb.getProcess().getOutgoingQueue().getMessage();
        if (!maybeMessage.isPresent()) {
            return;
        }
        pcb.readyIf(processstate.OUTBOX_FULL); // we just made room in its outbox

        message m = maybeMessage.get();
        m.setSenderPid(pcb.getPid());
        deliver(m, findByPid(m.getTargetPid()));
    }

    // Put a message in the target's inbox, or hold it in the PCB backlog.
    // If mail is already being held, new mail goes behind it so the target
    // receives messages in the order they were sent.
    private void deliver(message m, processctrlblock target) {
        if (target == null) {
            return; // unknown pid - message is dropped
        }

        LinkedList<message> backlog = target.getMessageQueue();
        if (backlog.isEmpty() && target.getProcess().getIncomingQueue().writeMessage(m)) {
            target.readyIf(processstate.INBOX_EMPTY); // it has mail now
        } else {
            backlog.add(m);
        }
    }

    private synchronized processctrlblock findByPid(int pid) {
        for (int i = 0; i < registered; i++) {
            if (pcbs[i] != null && pcbs[i].getPid() == pid) {
                return pcbs[i];
            }
        }
        return null;
    }

    // If a user process left a RESCHEDULE in its forKernel slot, carry it to
    // the kernel. This skips the outbox, so it works even when the outbox is full.
    private void deliverForKernel(processctrlblock pcb) {
        if (!(pcb.getProcess() instanceof userprocess)) {
            return;
        }
        message m = ((userprocess) pcb.getProcess()).takeForKernel();
        if (m == null) {
            return;
        }

        m.setSenderPid(pcb.getPid());
        deliver(m, findByPid(KERNEL_PID));
    }

    // Called by the kernel when it removes an exited process.
    protected synchronized void unregisterProcess(int pid) {
        for (int i = 0; i < registered; i++) {
            if (pcbs[i] != null && pcbs[i].getPid() == pid) {
                pcbs[i] = null;
                return;
            }
        }
    }

}
