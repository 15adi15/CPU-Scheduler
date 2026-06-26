import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    static class JobQueue {
        LinkedList<PCB> jobqueue = new LinkedList<>();// a non thread safe data structure
        final Lock queuelock = new ReentrantLock();// declared a lock

        // for producer(new process)
        public void addJob(PCB p) {
            queuelock.lock();
            try {
                jobqueue.add(p);
            } finally {
                queuelock.unlock();
            }
        }

        // for consumer(memory manager)
        public PCB getJob() {
            queuelock.lock();
            try {
                if (!jobqueue.isEmpty()) {
                    return jobqueue.poll();
                } else {
                    return null;
                }
            } finally {
                queuelock.unlock();
            }
        }

    }

    static class MemoryManager implements Runnable {

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
