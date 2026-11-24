import java.util.Optional;


public class messagequeue {

    private final message[] buffer;
    private final int capacity;
    private volatile int head = 0;
    private volatile int tail = 0;


    protected messagequeue(int capacity) {
        this.capacity = capacity + 1;
        this.buffer = new message[this.capacity];
    }

    protected boolean writeMessage(message m){
        int currenttail = tail;
        //the true loop in the ring XD
        int nexttail = (currenttail + 1) % capacity;

        if (nexttail == head){
            return false;
        }

        buffer[currenttail] = m;
        tail = nexttail;
        return true;
    }

    protected Optional<message> getMessage(){
        int currenthead = head;
        if (currenthead == tail){
            return Optional.empty();
        }

        message m = buffer[currenthead];
        buffer[currenthead] = null;
        head = (currenthead + 1) % capacity;

        return Optional.of(m);
    }

    // Read-only checks the kernel uses to decide if a waiting process can run.
    // Safe from any thread: they only read the volatile head and tail.
    protected boolean hasMessages() {
        return head != tail;
    }

    protected boolean hasRoom() {
        return (tail + 1) % capacity != head;
    }

}
