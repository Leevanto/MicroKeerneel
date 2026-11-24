// Exercises every filesystem action from its own home directory.
// Two of these run at once to show each process keeps its own current directory.
public class fsysTest extends userprocess {

    private static final String FS_NAME = "filesystem";

    private final String home;
    private int fsPid;

    protected fsysTest(String name, int queueCapacity, String home) {
        super(name, queueCapacity);
        this.home = home;
    }

    @Override
    protected void main() {
        fsPid = locate(FS_NAME);

        request(fsysMessagetype.ADD_DIRECTORY, "/" + home);
        request(fsysMessagetype.SET_DIRECTORY, "/" + home);
        request(fsysMessagetype.ADD_FILE, "notes.txt", ("hello from " + getName()).getBytes());
        request(fsysMessagetype.ADD_DIRECTORY, "sub");
        request(fsysMessagetype.ADD_FILE, "sub/big.bin", new byte[diskmessagetype.BLOCK_SIZE + 1]);
        list(".");
        list("sub");

        readFile("notes.txt");
        request(fsysMessagetype.WRITE_FILE, "notes.txt", ("rewritten by " + getName()).getBytes());
        readFile("./sub/../notes.txt"); // . and .. work like Unix
        list("notes.txt");

        readFile("missing.txt"); // should be an error

        request(fsysMessagetype.REMOVE_FILE, "sub/big.bin");
        request(fsysMessagetype.REMOVE_DIRECTORY, "sub");
        request(fsysMessagetype.REMOVE_FILE, "notes.txt");
        list(".");      // now empty
        list("/");
    }

    // Actions that only reply on error.
    private void request(fsysMessagetype what, String path) {
        sendMessage(new message(fsPid, what.toWhat(), path));
        cooperate();
    }

    private void request(fsysMessagetype what, String path, byte[] data) {
        sendMessage(new message(fsPid, what.toWhat(), path, data));
        cooperate();
    }

    private void list(String path) {
        sendMessage(new message(fsPid, fsysMessagetype.LIST.toWhat(), path));
        log("List " + path);
        while (true) {
            message m = getMessage();
            if (m == null || m.getWhat() == fsysMessagetype.LIST_END.toWhat()) break;
            if (m.getWhat() == fsysMessagetype.ERROR.toWhat()) {
                log("  error: " + m.getData(0));
                break;
            }
            log("  " + m.getData(0));
        }
    }

    private void readFile(String path) {
        sendMessage(new message(fsPid, fsysMessagetype.READ_FILE.toWhat(), path));
        message m = getMessage();
        if (m == null) return;
        if (m.getWhat() == fsysMessagetype.ERROR.toWhat()) {
            log("ReadFile " + path + " -> error: " + m.getData(0));
        } else {
            log("ReadFile " + path + " -> \"" + new String((byte[]) m.getData(0)) + "\"");
        }
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
