import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class kernel extends Process {
    private static final String KERNEL_NAME = "kernel";
    private static final String MESSAGE_EXCHANGE_NAME = "messageExchange";
    private static final String IDLE_NAME = "idle";
    private static final int DEFAULT_QUEUE_CAPACITY = 16;

    // Set to true to print every scheduling decision.
    private static final boolean TRACE_SCHEDULING = false;

    private final messageexchange messageExchange;


    private final List<processctrlblock> pcbs = new CopyOnWriteArrayList<>();
    private final processctrlblock idlePcb;

    // Told about every kernel decision, e.g. "schedule helloworld". May be null.
    private final Consumer<String> eventlistener;

    // The user process that currently holds the CPU, or null if none.
    private volatile processctrlblock current = null;
    // Round-robin position: pid of the last process we scheduled (idle doesn't count).
    private int lastScheduledPid = -1;

    protected kernel(Process[] toStart) {
        this(toStart, TRACE_SCHEDULING ? text -> System.out.println("[kernel] " + text) : null);
    }

    protected kernel(Process[] toStart, Consumer<String> eventlistener) {
        super(KERNEL_NAME, DEFAULT_QUEUE_CAPACITY);

        this.eventlistener = eventlistener;
        processctrlblock.resetPids(); // fresh boot: the kernel is pid 0

        processctrlblock kernelPcb = new processctrlblock(KERNEL_NAME, this);
        this.setPid(kernelPcb.getPid());
        pcbs.add(kernelPcb);

        messageExchange = new messageexchange(-1, MESSAGE_EXCHANGE_NAME, DEFAULT_QUEUE_CAPACITY);
        processctrlblock mePcb = new processctrlblock(MESSAGE_EXCHANGE_NAME, messageExchange);
        messageExchange.setPid(mePcb.getPid());
        pcbs.add(mePcb);
        messageExchange.registerProcess(kernelPcb);
        messageExchange.registerProcess(mePcb);

        idlePcb = registerNewProcess(new idle(IDLE_NAME, DEFAULT_QUEUE_CAPACITY));

        for (Process p : toStart) {
            registerNewProcess(p);
        }

        messageExchange.start();
        start();
    }

    private processctrlblock registerNewProcess(Process p) {
        processctrlblock pcb = new processctrlblock(p.getName(), p);
        p.setPid(pcb.getPid());
        pcbs.add(pcb);
        messageExchange.registerProcess(pcb);

        // user process thread starts and immediately freezes until scheduled.
        p.start();
        return pcb;
    }

    private processctrlblock findByName(String name) {
        for (processctrlblock pcb : pcbs) {
            if (pcb.getName().equals(name)) {
                return pcb;
            }
        }
        return null;
    }

    @Override
    public void run() {
        while (true) {

            // Handle everything waiting in the inbox.
            Optional<message> maybeMessage;
            while ((maybeMessage = tryGetMessage()).isPresent()) {
                handleMessage(maybeMessage.get());
            }

            reapFinished();

            // If nobody is running, try to start. This also catches
            // processes whose waiting has cleared since last looked.
            if (current == null) {
                scheduleNext();
            }
            if (!pause()) {
                return;
            }
        }
    }

    private void handleMessage(message m) {
        kernelmessagetype type;
        try {
            type = kernelmessagetype.fromWhat(m.getWhat());
        } catch (IllegalArgumentException e) {
            return; // unrecognized "what" - ignore and keep going
        }

        switch (type) {
            case CREATE_PROCESS:
                handleCreateProcess(m);
                break;
            case LOCATE:
                handleLocate(m);
                break;
            case RESCHEDULE:
                handleReschedule(m);
                break;
            case EXIT:
                handleExit(m);
                break;
            default:
                break; // LOCATE_RESPONSE shouldn't arrive here; ignore
        }

    }

    private void handleCreateProcess(message m) {
        if (!(m.getData(0) instanceof Process)) {
            return;
        }
        Process p = (Process) m.getData(0);
        registerNewProcess(p);
        event("create " + p.getName() + " by pid " + m.getSenderPid());
    }

    private void handleLocate(message m) {
        if (!(m.getData(0) instanceof String)) {
            return;
        }
        String name = (String) m.getData(0);
        processctrlblock found = findByName(name);
        int pid = (found != null) ? found.getPid() : -1; // -1 = "not found"

        sendMessage(new message(m.getSenderPid(), kernelmessagetype.LOCATE_RESPONSE.toWhat(), pid));
    }

    // The running process has stopped. Record why, then pick who runs next.
    private void handleReschedule(message m) {
        if (!(m.getData(0) instanceof processstate)) {
            return;
        }
        processctrlblock pcb = currentSender(m);
        if (pcb == null) {
            return;
        }

        processstate reason = (processstate) m.getData(0);
        pcb.setState(reason);
        event("stop " + pcb.getName() + " " + reason);

        // The exchange may have fixed the problem before we recorded it (it saw
        // RUNNABLE, so it didn't flip anything). Check once more so the process
        // isn't left waiting forever.
        if (reason == processstate.INBOX_EMPTY && pcb.getProcess().getIncomingQueue().hasMessages()) {
            pcb.readyIf(processstate.INBOX_EMPTY);
        } else if (reason == processstate.OUTBOX_FULL && pcb.getProcess().getOutgoingQueue().hasRoom()) {
            pcb.readyIf(processstate.OUTBOX_FULL);
        }

        current = null;
        scheduleNext();
    }


    // The running process's main() returned. Never schedule it again.
    // It stays registered until its outbox is empty.
    private void handleExit(message m) {
        processctrlblock pcb = currentSender(m);
        if (pcb == null) {
            return;
        }
        pcb.setState(processstate.FINISHED);
        event("exit " + pcb.getName());

        current = null;
        scheduleNext();
    }


    // Remove finished processes once the exchange has sent everything left in
    // their outbox, so their last messages aren't lost.
    private void reapFinished() {
        for (processctrlblock pcb : pcbs) {
            if (pcb.getState() == processstate.FINISHED
                    && !pcb.getProcess().getOutgoingQueue().hasMessages()) {
                pcbs.remove(pcb);
                messageExchange.unregisterProcess(pcb.getPid());
                event("reap " + pcb.getName());
            }
        }
    }

    private void event(String text) {
        if (eventlistener != null) {
            eventlistener.accept(text);
        }
    }

    // Round-robin over the processes that can run; idle if there are none.
    private void scheduleNext() {
        processctrlblock next = pickNext();
        if (next == null) {
            next = idlePcb; // nothing else can run
        } else {
            lastScheduledPid = next.getPid();
        }

        current = next;
        next.setState(processstate.RUNNABLE);
        event("schedule " + next.getName());
        ((userprocess) next.getProcess()).wake(); // release(): the dotted line
    }

    // Only the running process can stop or exit. The exchange stamped the
    // sender pid, so this can't be faked.
    private processctrlblock currentSender(message m) {
        processctrlblock running = current;
        if (running == null || running.getPid() != m.getSenderPid()) {
            return null;
        }
        return running;
    }

    // The first process that can run with a pid after the last one we
    // scheduled, wrapping around. pcbs is always in pid order.
    private processctrlblock pickNext() {
        processctrlblock firstThatCanRun = null;
        for (processctrlblock pcb : pcbs) {
            if (!canRun(pcb)) {
                continue;
            }
            if (pcb.getPid() > lastScheduledPid) {
                return pcb;
            }
            if (firstThatCanRun == null) {
                firstThatCanRun = pcb;
            }
        }
        return firstThatCanRun;
    }

    // Trusts the PCB state: the exchange sets a waiting process back to
    // RUNNABLE once its reason for waiting has cleared.
    private boolean canRun(processctrlblock pcb) {
        if (pcb == idlePcb || !(pcb.getProcess() instanceof userprocess)) {
            return false; // idle is the fallback; kernel and exchange are never scheduled
        }
        processstate state = pcb.getState();
        return state == processstate.RUNNABLE || state == processstate.QUANTUM_EXPIRED;
    }
}
