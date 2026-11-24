import java.util.Arrays;

// Writes several blocks to the disk, reads them back and checks the data matches.
public class diskTest extends userprocess {

    private static final String DISK_NAME = "disk";
    private static final int BLOCKS_TO_TEST = 4;

    private int diskPid;

    protected diskTest(String name, int queueCapacity) {
        super(name, queueCapacity);
    }

    @Override
    protected void main() {
        diskPid = locate(DISK_NAME);

        sendMessage(new message(diskPid, diskmessagetype.GET_SIZE.toWhat()));
        message size = receive();
        if (size == null) return;
        int numBlocks = (int) size.getData(0);
        log("GetSize -> " + numBlocks + " blocks");

        for (int block = 0; block < BLOCKS_TO_TEST; block++) {
            sendMessage(new message(diskPid, diskmessagetype.WRITE.toWhat(), block, pattern(block)));
            cooperate();
        }
        log("Wrote blocks 0-" + (BLOCKS_TO_TEST - 1));

        for (int block = 0; block < BLOCKS_TO_TEST; block++) {
            sendMessage(new message(diskPid, diskmessagetype.READ.toWhat(), block));
            message reply = receive();
            if (reply == null) return;
            boolean match = reply.getWhat() == diskmessagetype.READ_RESULT.toWhat()
                    && Arrays.equals((byte[]) reply.getData(1), pattern(block));
            log("Read block " + block + " -> " + (match ? "PASS, data matches" : "FAIL, data differs"));
            cooperate();
        }

        // These should all come back as errors.
        sendMessage(new message(diskPid, diskmessagetype.READ.toWhat(), numBlocks));
        receive();
        sendMessage(new message(diskPid, diskmessagetype.WRITE.toWhat(), 0, new byte[10]));
        receive();
        sendMessage(new message(diskPid, diskmessagetype.WRITE.toWhat(), 0));
        receive();
    }

    // Different contents for every block, so a mixed-up block is caught.
    private static byte[] pattern(int block) {
        byte[] data = new byte[diskmessagetype.BLOCK_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (block * 31 + i);
        }
        return data;
    }

    // Next reply from the disk; errors are printed as they arrive.
    private message receive() {
        message m = getMessage();
        if (m != null && m.getWhat() == diskmessagetype.ERROR.toWhat()) {
            log("Disk error (expected): " + m.getData(0));
        }
        return m;
    }

    private void log(String text) {
        System.out.println("[" + getName() + "] " + text);
    }

    private int locate(String name) {
        sendMessage(new message(KERNEL_PID, kernelmessagetype.LOCATE.toWhat(), name));

        message response = getMessage();
        if (response == null) {
            return -1; // we were shut down while waiting
        }
        return (int) response.getData(0);
    }
}
