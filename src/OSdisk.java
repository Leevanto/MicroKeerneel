import java.io.RandomAccessFile;
import java.io.IOException;

// The disk's contents live in a real file on the host, accessed through
// RandomAccessFile. The file is split into numBlocks blocks of BLOCK_SIZE
// (4096) bytes each, so block n starts at byte offset n * BLOCK_SIZE.
// Every request moves exactly one block; a client that needs more data
// sends more messages.

public class OSdisk extends userprocess {
    private final String fileName; // host file that holds the disk's contents
    private final int numBlocks; // size of the disk, in blocks
    private RandomAccessFile file; // opened in main(), on the disk's own thread

    protected OSdisk(String name, int queueCapacity, String fileName, int numBlocks) {
        super(name, queueCapacity);

        // A disk with no blocks could never answer a read or a write.
        if (numBlocks <= 0) throw new IllegalArgumentException("numBlocks must be positive");
        this.fileName = fileName;
        this.numBlocks = numBlocks;
    }

    @Override
    protected void main() {

        // Open the backing file ("rw" creates it if it doesn't exist) and make it
        // exactly numBlocks blocks long. Setting the length up front means a block
        // that was never written reads back as zeros instead of hitting end-of-file.
        try {
            file = new RandomAccessFile(fileName, "rw");
            file.setLength((long) numBlocks * diskmessagetype.BLOCK_SIZE);
        } catch (IOException e) {
            // Without its file the disk can't do anything, so the process ends.
            System.err.println("Disk: cannot open " + fileName + ": " + e.getMessage());
            return;
        }

        // Server loop: handle one request at a time, in the order they arrive.
        while (true) {
            message msg = getMessage(); // blocks until something arrives
            if (msg == null) break;     // interrupted

            // Turn the message's "what" code into a request type. fromWhat throws
            // for a code it doesn't know; that still gets an ERROR reply, so the
            // sender is never left waiting for an answer that won't come.
            diskmessagetype type;
            try {
                type = diskmessagetype.fromWhat(msg.getWhat());
            } catch (IllegalArgumentException e) {
                type = null;
            }
            if (type == null) {
                error(msg, "Unknown disk request " + msg.getWhat());
            } else {
                switch (type) {
                    case READ -> handleRead(msg);
                    case WRITE -> handleWrite(msg);
                    case GET_SIZE -> reply(msg, diskmessagetype.SIZE_RESULT, numBlocks, null);
                    // READ_RESULT, SIZE_RESULT and ERROR are replies, not requests.
                    default -> error(msg, "Unknown disk request " + type);
                }
            }
            cooperate(); // give up the CPU here if our quantum is used up
        }
    }


    // READ: data0 = block number.
    // Replies READ_RESULT with the block number (data0) and its 4096 bytes (data1).
    private void handleRead(message msg) {
        // data0 must be an Integer; the pattern match also unboxes it into block.
        if (!(msg.getData(0) instanceof Integer block)) {
            error(msg, "Read needs a block number");
            return;
        }
        if (!inRange(block)) {
            error(msg, "Read: block " + block + " is out of range (0-" + (numBlocks - 1) + ")");
            return;
        }

        // A new buffer for every read: the reply is passed by reference, so reusing
        // one buffer would let a later read change data a client already received.
        byte[] buffer = new byte[diskmessagetype.BLOCK_SIZE];
        try {

            // Jump to the start of the block, then read the whole block.
            // The cast to long keeps block * BLOCK_SIZE from overflowing an int.
            file.seek((long) block * diskmessagetype.BLOCK_SIZE);
            file.readFully(buffer);
        } catch (IOException e) {
            error(msg, "Read: I/O error on block " + block + ": " + e.getMessage());
            return;
        }
        reply(msg, diskmessagetype.READ_RESULT, block, buffer);
    }

    // Replies only on error; a successful write sends nothing back.
    // Every check runs before the file is touched
    private void handleWrite(message msg) {
        if (!(msg.getData(0) instanceof Integer block)) {
            error(msg, "Write: missing block number");
            return;
        }
        if (!inRange(block)) {
            error(msg, "Write: block " + block + " is out of range (0-" + (numBlocks - 1) + ")");
            return;
        }
        // A missing data1 is null, and null fails instanceof, so it lands here too.
        if (!(msg.getData(1) instanceof byte[] data) || data.length == 0) {
            error(msg, "Write: block data is missing");
            return;
        }
        if (data.length != diskmessagetype.BLOCK_SIZE) {
            error(msg, "Write: block data must be " + diskmessagetype.BLOCK_SIZE + " bytes, got " + data.length);
            return;
        }
        try {
            // Jump to the start of the block and overwrite all of it.
            file.seek((long) block * diskmessagetype.BLOCK_SIZE);
            file.write(data, 0, diskmessagetype.BLOCK_SIZE);
        } catch (IOException e) {
            error(msg, "Write: I/O error on block " + block + ": " + e.getMessage());
        }
    }

    private boolean inRange(int block) {
        return block >= 0 && block < numBlocks;
    }

    // Sends a reply to whoever sent the request
    private void reply(message request, diskmessagetype what, Object data1, Object data2) {
        sendMessage(new message(request.getSenderPid(), what.toWhat(), data1, data2));
    }

    private void error(message request, String text) {
        reply(request, diskmessagetype.ERROR, text, null);
    }
}
