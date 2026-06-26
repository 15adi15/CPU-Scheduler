import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
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
        int prioritynumber;

        // constructor for a process
        public PCB() {
            this.Pid = ThreadLocalRandom.current().nextInt(1, 1000);
            this.state = "NEW";
            this.burstTime = ThreadLocalRandom.current().nextInt(0, 15);
            this.Timeremaining = this.burstTime;
            this.memoryRequired = ThreadLocalRandom.current().nextInt(1, 50);
            this.arrivalTime = GlobalClock.systemTime.get();
            this.lastUsedTime = arrivalTime;
        }

        // Getters for process id and process state and memory required
        public int getPid() {
            return Pid;
        }

        public String getState() {
            return state;
        }

        public int getMemoryRequired() {
            return memoryRequired;
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
        static LinkedList<PCB> jobqueue = new LinkedList<>();// a non thread safe data structure
        static final Lock queuelock = new ReentrantLock();// declared a lock

        // for producer(new process)
        public static void addJob(PCB p) {
            queuelock.lock();
            try {
                jobqueue.add(p);
            } finally {
                queuelock.unlock();
            }
        }

        // for consumer(memory manager)
        public static PCB getJob() {
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

        int availablememory = 256;
        // we will create an list that will store the process
        List<PCB> loadedprocess = new ArrayList<>();

        @Override
        public void run() {
            // a continuos while loop
            while (true) {
                try {
                    PCB processtoAllocate = null;
                    if (!MLFQ.swapqueue.isEmpty()) {
                        processtoAllocate = MLFQ.swapqueue.poll();
                    } else {
                        processtoAllocate = JobQueue.getJob();
                    }

                    if (processtoAllocate != null) {
                        // we will check the required memory
                        int reqmemo = processtoAllocate.getMemoryRequired();
                        while (reqmemo > availablememory) {// we shall remove the oldestprocess(smallest lut)
                            PCB oldestprocess = loadedprocess.get(0);
                            for (int i = 1; i < loadedprocess.size(); i++) {
                                if (oldestprocess.lastUsedTime > loadedprocess.get(i).lastUsedTime) {
                                    oldestprocess = loadedprocess.get(i);
                                }
                            } // now we have oldest process , remove it and add its memory to ram
                            availablememory += oldestprocess.getMemoryRequired();
                            loadedprocess.remove(oldestprocess);
                            MLFQ.removeProcess(oldestprocess);
                            // add the oldest removed process in the swap queue
                            MLFQ.swapqueue.add(oldestprocess);
                        }
                        // now we have enough memory
                        loadedprocess.add(processtoAllocate);
                        availablememory -= processtoAllocate.getMemoryRequired();
                        System.out.println("--------Successfully allocated memory in RAM--------");
                        MLFQ.addprocess(processtoAllocate);
                    } else {
                        Thread.sleep(2);
                    }
                } catch (InterruptedException e) {
                    // TODO: handle exception
                }
            }
        }
    }

    static class MLFQ {
        static LinkedList<PCB> queue0 = new LinkedList<>(); // queue 0: round robin 10ms (high priority)
        static LinkedList<PCB> queue1 = new LinkedList<>(); // queue 1: round robin 20 ms (medium priority)
        static LinkedList<PCB> queue2 = new LinkedList<>(); // queue 2: FCFS

        static ConcurrentLinkedQueue<PCB> swapqueue = new ConcurrentLinkedQueue<>();

        static Lock accessLock = new ReentrantLock();

        // method 1---adding new proccess to the queue 0
        public static void addprocess(PCB p) {
            accessLock.lock();
            try {
                p.prioritynumber = 0;
                queue0.add(p);
            } finally {
                accessLock.unlock();
            }
        }

        // method 2---demote the process
        public static void demoteprocess(int prioritynum, PCB p) {
            accessLock.lock();
            try {
                if (prioritynum == 1) {
                    p.prioritynumber = 1;
                    queue1.add(p);
                } else if (prioritynum == 2) {
                    p.prioritynumber = 2;
                    queue2.add(p);
                }
            } finally {
                accessLock.unlock();
            }
        }

        // method 3----the cpu will fetch the highest priority process.
        public static PCB getHighPriorityProcess() {
            accessLock.lock();
            try {
                if (!queue0.isEmpty()) {
                    return queue0.poll();
                }
                if (!queue1.isEmpty()) {
                    return queue1.poll();
                }
                if (!queue2.isEmpty()) {
                    return queue2.poll();
                }

            } finally {
                accessLock.unlock();
            }
            return null;
        }

        // logic to prevent starvation
        public static void boostStarveProcess() {
            accessLock.lock();
            int currtime = GlobalClock.systemTime.get();// we note the system time
            try {
                if (!queue1.isEmpty()) {
                    for (int i = queue1.size() - 1; i >= 0; i--) {
                        if (currtime - queue1.get(i).lastUsedTime > 5000) {
                            // boast the process!
                            addprocess(queue1.remove(i));
                        }
                    }
                }
                if (!queue2.isEmpty()) {
                    for (int i = queue2.size() - 1; i >= 0; i--) {
                        if (currtime - queue2.get(i).lastUsedTime > 5000) {
                            addprocess(queue2.remove(i));
                        }
                    }
                }
            } finally {
                accessLock.unlock();
            }
        }

        // a special method used by MMU only to remove process from the respective
        // queues
        public static void removeProcess(PCB p) {
            accessLock.lock();
            try {
                queue0.remove(p);
                queue1.remove(p);
                queue2.remove(p);
            } finally {
                accessLock.unlock();
            }
        }
    }

    static class bgAntiStarvation implements Runnable {
        // this class will contain the daemon thread that will run continuosly to
        // prevent process starvation
        public void run() {
            Thread antistarvationthread = new Thread(() -> {
                while (true) {
                    try {
                        MLFQ.boostStarveProcess();
                        Thread.sleep(6000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            antistarvationthread.setDaemon(true);
            antistarvationthread.start();
        }
    }

    public static void main(String arg[]) {

    }

}
