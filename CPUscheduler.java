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

        public static void spawnprocess(int limit) {
            Thread spwanprocess = new Thread(() -> {
                for (int i = 0; i <= limit; i++) {
                    PCB p = new PCB();
                    JobQueue.addJob(p);
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(5, 30));
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
            });
            spwanprocess.start();
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

        static int availablememory = 256;
        // we will create an list that will store the process
        static List<PCB> loadedprocess = new ArrayList<>();

        static Lock memorylock = new ReentrantLock();

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
                            System.out.printf("%s [MemManager] RAM Full! Swapping out PID-%d (LRU) to load PID-%d.\n",
                                    String.format("[Time: %04dms]", GlobalClock.systemTime.get()), oldestprocess.Pid,
                                    processtoAllocate.Pid);
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
                        System.out.printf("%s [MemManager] Allocated memory for PID-%d.\n",
                                String.format("[Time: %04dms]", GlobalClock.systemTime.get()), processtoAllocate.Pid);
                        MLFQ.addprocess(processtoAllocate);
                    } else {
                        Thread.sleep(2);
                    }
                } catch (InterruptedException e) {
                    // TODO: handle exception
                }
            }
        }

        // a special method that will only be accessed by the cpu to clear of memroy of
        // a process once it is successfully terminated
        public static void freememory(PCB p) {
            memorylock.lock();
            try {
                MemoryManager.availablememory += p.memoryRequired;
                MemoryManager.loadedprocess.remove(p);
            } finally {
                memorylock.unlock();
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
                System.out.printf("%s [MLFQ] PID-%d added to Queue 0.\n",
                        String.format("[Time: %04dms]", GlobalClock.systemTime.get()), p.Pid);
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

    static class CPUcore implements Runnable {
        int coreId;

        // a constructor for cpu core's
        public CPUcore(int coreId) {
            this.coreId = coreId;
        }

        public void run() {
            while (true) {
                try {
                    // step one --> get a process from MLFQ
                    PCB process = MLFQ.getHighPriorityProcess();
                    if (process != null) {
                        System.out.printf("%s [Core-%d] Fetched PID-%d. Executing...\n",
                                String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                process.Pid);
                        process.lastUsedTime = GlobalClock.systemTime.get();
                        if (process.prioritynumber == 0) {
                            if (process.Timeremaining <= 10) {
                                Thread.sleep(process.Timeremaining);
                                // terminate the process successfully and free up its memory
                                MemoryManager.freememory(process);
                                process.completionTime = GlobalClock.systemTime.get();
                                System.out.printf("%s [Core-%d] PID-%d Completed successfully!\n",
                                        String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                        process.Pid);

                            } else {
                                Thread.sleep(10);
                                process.Timeremaining -= 10;
                                System.out.printf("%s [Core-%d] Quantum expired for PID-%d. Demoting to Queue 1.\n",
                                        String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                        process.Pid);
                                MLFQ.demoteprocess(1, process);
                            }
                        } else if (process.prioritynumber == 1) {
                            if (process.Timeremaining <= 20) {
                                Thread.sleep(process.Timeremaining);
                                MemoryManager.freememory(process);
                                process.completionTime = GlobalClock.systemTime.get();

                                System.out.printf("%s [Core-%d] PID-%d Completed successfully!\n",
                                        String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                        process.Pid);

                            } else {
                                Thread.sleep(20);
                                process.Timeremaining -= 20;
                                System.out.printf("%s [Core-%d] Quantum expired for PID-%d. Demoting to Queue 1.\n",
                                        String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                        process.Pid);
                                MLFQ.demoteprocess(2, process);
                            }
                        } else if (process.prioritynumber == 2) {
                            Thread.sleep(process.Timeremaining);
                            MemoryManager.freememory(process);
                            process.completionTime = GlobalClock.systemTime.get();

                            System.out.printf("%s [Core-%d] PID-%d Completed successfully!\n",
                                    String.format("[Time: %04dms]", GlobalClock.systemTime.get()), this.coreId,
                                    process.Pid);

                        }
                    } else {
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        }
    }

    public static void main(String arg[]) {

        System.out.println("Starting OS Boot Sequence...");

        // 1. Start the Memory Manager
        Thread memoryManager = new Thread(new MemoryManager());
        memoryManager.setDaemon(true);
        memoryManager.start();

        // 2. Start the Anti-Starvation Daemon
        new bgAntiStarvation().run();

        // 3. Start 4 CPU Cores
        for (int i = 1; i <= 4; i++) {
            Thread core = new Thread(new CPUcore(i));
            core.setDaemon(true);
            core.start();
        }

        // 4. Telling the PCB class to spawn 30 random processes in the background
        System.out.println("OS Boot Complete. Spawning processes...\n");
        PCB.spawnprocess(30);

        // 5. Keeping the main thread alive to watch the simulation
        try {
            Thread.sleep(5000);
            System.out.println("\n--- SIMULATION FINISHED ---");
        } catch (InterruptedException e) {
        }
    }

}
