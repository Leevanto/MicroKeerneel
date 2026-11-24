import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

//Process only runs when the kernel allows it.
//User programs extend this must call cooperate() regularly.
public abstract class userprocess extends Process {
    protected static final int KERNEL_PID = 0;
    private static final long QUANTUM_MS = 200;

    // The one semaphore: permission to run. Starts at 0 = frozen.
    // The process calls acquire(); the kernel calls release() through wake()
    private final Semaphore runPermit = new Semaphore(0);
    private volatile message forKernel;
    // The boolean from the diagram. The timer thread sets it to true;
    // cooperate() checks it; each new turn resets it to false.
    private volatile boolean timeUp;
    private final Timer quantumTimer;
    private TimerTask quantumTask;



    protected userprocess (String name, int queuecapacity){
        super(name, queuecapacity);
        this.quantumTimer = new Timer(name + "-quantum", true); // daemon thread

    }

    // final: programs can't override run() and skip waiting for the kernel.
    @Override
    public final void run() {
        if (!waitForTurn()) {   // frozen until the kernel
            return;
        }
        main();
        finish();
    }

    protected abstract void main();


    // Programs call this regularly. If the quantum is used up, give up the CPU.
    protected void cooperate() {
        if (timeUp) {
            stop(processstate.QUANTUM_EXPIRED);
        }
    }

    // block until the kernel calls wake().
    // Runs at the start of every turn, including the first.
    private boolean waitForTurn() {
        try {
            runPermit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        timeUp = false; // "time is not up" - we're running now
        quantumTask = new TimerTask() {
            @Override
            public void run() {
                timeUp = true;
            }
        };
        quantumTimer.schedule(quantumTask, quantumMs()); // a TimerTask can only be scheduled once
        return true;
    }

    // main() returned: send EXIT. The kernel never schedules again and
    // removes once the exchange has sent everything left in our outbox.
    private void finish() {
        cancelQuantum();
        quantumTimer.cancel(); // ends the timer's background thread
        forKernel = new message(KERNEL_PID, kernelmessagetype.EXIT.toWhat());
    }

    // Tell the kernel why we're stopping, then freeze until it wakes up.
    private boolean stop(processstate reason) {
        cancelQuantum(); // don't let this turn's alarm go off during the next turn
        forKernel = reschedule(reason);
        return waitForTurn();
    }

    // Length of one turn. A program can override this to get a different
    // time slice (idle uses a short one).
    protected long quantumMs() {
        return QUANTUM_MS;
    }

    private void cancelQuantum() {
        if (quantumTask != null) {
            quantumTask.cancel();
        }
    }

    private message reschedule(processstate reason) {
        return new message(KERNEL_PID, kernelmessagetype.RESCHEDULE.toWhat(), reason);
    }

    // Inbox empty -> stop and let the kernel run someone else.
    // When the kernel wakes up, mail has arrived, and the loop picks it up.
    @Override
    protected message getMessage() {
        Optional<message> maybeMessage;
        while (!(maybeMessage = getIncomingQueue().getMessage()).isPresent()) {
            if (!stop(processstate.INBOX_EMPTY)) {
                return null;
            }
        }
        return maybeMessage.get();
    }

    // Outbox full -> stop until the kernel wakes up with room available.
    @Override
    protected void sendMessage(message m) {
        while (!getOutgoingQueue().writeMessage(m)) {
            if (!stop(processstate.OUTBOX_FULL)) {
                return;
            }
        }
    }

    // Called by the kernel: the release() end of the dotted line.
    protected void wake() {
        runPermit.release();
    }

    // Called by the exchange: take the message for the kernel and empty the slot.
    protected message takeForKernel() {
        message m = forKernel;
        forKernel = null;
        return m;
    }

}
