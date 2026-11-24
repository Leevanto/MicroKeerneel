import java.util.Optional;

public abstract class Process implements Runnable{
    private int pid;
    private final String name;

    private final messagequeue incoming;
    private final messagequeue outgoing;

    // How long the always-running processes nap
    // between checks of their queues.
    private static final long POLL_SLEEP_MS = 10;


    private final Thread thread;

    protected Process(String name, int queueCapacity) {
        this.name = name;
        this.incoming = new messagequeue(queueCapacity);
        this.outgoing = new messagequeue(queueCapacity);


        this.thread = new Thread(this);
    }

    // Short nap. Returns false if the thread was interrupted (time to quit).
    protected boolean pause() {
        try {
            Thread.sleep(POLL_SLEEP_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    protected int getPid() {
        return pid;
    }


    // Always-running processes wait by polling: try, nap, try again.
    // UserProcess overrides this to stop and give up the CPU instead.
    protected void sendMessage(message m) {
        while (!outgoing.writeMessage(m)) {
           if (!pause()){
               return;
           }
        }
    }

    // Blocks (by polling) until a message arrives.
    protected message getMessage() {
        Optional<message> maybeMessage;

        while (!(maybeMessage = incoming.getMessage()).isPresent()) {
            if (!pause()){
                return null;
            }
        }

        return maybeMessage.get();
    }

    protected void start() {
        thread.start();
    }


    // Non-blocking: returns a message if one is waiting, otherwise empty.
    protected Optional<message> tryGetMessage() {
        return incoming.getMessage();
    }

    protected String getName() {
        return name;
    }

    protected messagequeue getIncomingQueue() {
        return incoming;
    }

    protected messagequeue getOutgoingQueue() {
        return outgoing;
    }

    protected void setPid(int pid) {
        this.pid = pid;
    }


}
