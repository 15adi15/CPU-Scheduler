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
            // process id should be unique
            this.Pid = ThreadLocalRandom.current().nextInt(1, 1000);
            // every new process created should have "new" state
            this.state = "NEW";
            // we need to generate random bursttime
            this.burstTime = ThreadLocalRandom.current().nextInt(0, 15);
            // new process has time remaining equal to burst time
            this.Timeremaining = this.burstTime;
            // number of memory required for the process
            this.memoryRequired = ThreadLocalRandom.current().nextInt(1, 50);
            // arrivaltime of a process is equivalent to systemtime at that instance
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

        // a thread that keeps incrementing the systemTime by 1 ms

        Thread incrementsystemTime = new Thread();

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
