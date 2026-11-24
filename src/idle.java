public class idle extends userprocess{
    // A short turn, so the kernel soon gets a chance to check whether
    // someone else has become runnable.
    private static final long IDLE_QUANTUM_MS = 50;

    protected idle(String name, int queueCapacity) {
        super(name, queueCapacity);
    }

    @Override
    protected void main() {
        // Nap in short steps so we don't burn the host CPU, and cooperate so
        // we give the CPU back when our quantum is up.
        while (pause()) {
            cooperate();
        }
    }

    @Override
    protected long quantumMs() {
        return IDLE_QUANTUM_MS;
    }

}
