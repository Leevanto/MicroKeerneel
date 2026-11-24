// Requests to (and replies from) the OSdisk process.
enum diskmessagetype {
    READ,        // data0 = block number (Integer)
    WRITE,       // data0 = block number (Integer), data1 = block contents (byte[BLOCK_SIZE])
    GET_SIZE,
    READ_RESULT, // data0 = block number, data1 = block contents
    SIZE_RESULT, // data0 = number of blocks
    ERROR;       // data0 = error text

    static final int BLOCK_SIZE = 4096;

    private static final int BASE = 100;

    protected int toWhat() {
        return BASE + this.ordinal();
    }

    protected static diskmessagetype fromWhat(int what) {
        diskmessagetype[] values = values();
        int i = what - BASE;
        if (i < 0 || i >= values.length) {
            throw new IllegalArgumentException("Unknown DiskMessageType code: " + what);
        }
        return values[i];
    }
}
