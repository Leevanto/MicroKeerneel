
public class goodbyeworld extends userprocess {

    private static final String CONSOLE_NAME = "console";
    private static final int MESSAGE_COUNT = 100;

    protected goodbyeworld(String name, int queueCapacity) {
        super(name, queueCapacity);
    }

    @Override
    protected void main(){
        int consolePid = locate(CONSOLE_NAME);

        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            sendMessage(new message(consolePid, 0, "GoodbyeWorld " + i));
            cooperate(); // give up the CPU here if our quantum is used up
        }
    }

    private int locate(String name) {
        sendMessage(new message(KERNEL_PID, kernelmessagetype.LOCATE.toWhat(), name));

        message response = getMessage();
        if (response == null) {
            return -1; // we were shut down while waiting
        }
        int pid = (int) response.getData(0);

        System.out.println(getName() + " Located " + name + ", pid:" + pid);

        return pid;
    }
}
