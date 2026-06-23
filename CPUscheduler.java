import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CPUscheduler {

    static class PCB {
        // Attributes of a process
        volatile int Pid;
        volatile String state;
        int burstTime;
        int arrivalTime;
        int Timeremaining;
        int memoryRequired;
        int lastUsedTime;
        int completionTime;

        // constructor for a process
        public PCB() {
            this.Pid = ThreadLocalRandom.current().nextInt(1, 1000);
            this.state = "NEW";
            this.burstTime = ThreadLocalRandom.current().nextInt(0, 15);
            this.Timeremaining = this.burstTime;
            this.memoryRequired = ThreadLocalRandom.current().nextInt(1, 50);
            this.arrivalTime = GlobalClock.systemTime.get();
        }

        // Getters for process id and process state
        public int getPid() {
            return Pid;
        }

        public String getState() {
            return state;
        }

    }

    static class GlobalClock {
        public static AtomicInteger systemTime = new AtomicInteger(0);
        // static block of code runs exactly once when the class is booted up
        static {
            Thread increment = new Thread(() -> {
                while (true) {
                    try {
                        systemTime.incrementAndGet();
                        Thread.sleep(1);
                    } catch (Exception e) {
                        break;
                    }
                }
            });
            increment.setDaemon(true);
            increment.start();
        }

    }

    public static void main(String argsp[]) {
        // lets create few processes
        Runnable createprocess = new Runnable() {
            public void run() {
                for (int i = 0; i < 5; i++) {
                    PCB newprocess = new PCB();
                    System.out.println(
                            "New process created! " + "ProcessID:" + newprocess.getPid() + " " + "ProcessState:"
                                    + newprocess.getState() + " ," + "BurstTime:" + newprocess.burstTime);
                }
            }
        };
        Thread t = new Thread(createprocess);
        t.start();
    }

}
