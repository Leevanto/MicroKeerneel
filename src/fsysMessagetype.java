// Requests to (and replies from) the fileSystem process.
// Requests carry the path as data0; ADD_FILE and WRITE_FILE also carry the contents (byte[]) as data1.
enum fsysMessagetype {
    SET_DIRECTORY,
    ADD_DIRECTORY,
    ADD_FILE,
    REMOVE_FILE,
    REMOVE_DIRECTORY,
    LIST,
    READ_FILE,
    WRITE_FILE,
    LIST_ENTRY,    // data0 = one entry's description
    LIST_END,
    FILE_CONTENTS, // data0 = byte[]
    ERROR;         // data0 = error text

    // Different from the disk's base, so a client talking to both can tell the replies apart.
    private static final int BASE = 200;

    protected int toWhat() {
        return BASE + this.ordinal();
    }

    protected static fsysMessagetype fromWhat(int what) {
        fsysMessagetype[] values = values();
        int i = what - BASE;
        if (i < 0 || i >= values.length) {
            throw new IllegalArgumentException("Unknown File System MessageType code: " + what);
        }
        return values[i];
    }
}
