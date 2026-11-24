public class Main {

    public static void main(String[] args) {
        int queueCapacity = 16;

        Process[] toStart = {new init("init", queueCapacity)};
        new kernel(toStart);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Main is done waiting.");
    }
}
