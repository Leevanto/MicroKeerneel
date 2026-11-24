public class console extends userprocess {

    protected console(String name, int queueCapacity) {
        super(name, queueCapacity);
    }

    @Override
    protected void main() {
        while (true) {
            message m = getMessage(); // blocks until something arrives
            if (m == null) {
                return; // interrupted
            }
            System.out.println(m.getData(0));
            cooperate(); // give up the CPU here if our quantum is used up
        }
    }
}
