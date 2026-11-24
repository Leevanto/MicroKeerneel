 enum kernelmessagetype {
    CREATE_PROCESS,
    LOCATE,
    LOCATE_RESPONSE,
    RESCHEDULE,
     EXIT;



     protected int toWhat() {
        return this.ordinal();
    }

    protected static kernelmessagetype fromWhat(int what) {
        kernelmessagetype[] values = values();
        if (what < 0 || what >= values.length) {
            throw new IllegalArgumentException("Unknown KernelMessageType code: " + what);
        }
        return values[what];
    }

}
