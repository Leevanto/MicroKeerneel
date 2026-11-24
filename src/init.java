// The first user program. It asks the kernel to create every other

public class init extends userprocess{
    private static final int QUEUE_CAPACITY = 16;

    protected init(String name, int queueCapacity) {
        super(name, queueCapacity);
    }

    @Override
    protected void main() {
        // console first, so it exists before the others LOCATE it.
        spawn(new console("console", QUEUE_CAPACITY));
        spawn(new OSdisk("disk", QUEUE_CAPACITY, "disk.img", 16));
        spawn(new fileSystem("filesystem", QUEUE_CAPACITY));

        spawn(new diskTest("diskTest", QUEUE_CAPACITY));
        spawn(new fsysTest("fileSystemtestA", QUEUE_CAPACITY, "a"));
        spawn(new fsysTest("fileSystemtestB", QUEUE_CAPACITY, "b"));
    }

    private void spawn(Process program) {
        sendMessage(new message(KERNEL_PID, kernelmessagetype.CREATE_PROCESS.toWhat(), program));
        cooperate();
    }



}
