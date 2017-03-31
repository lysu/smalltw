package io.github.lysu.smalltw;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by robi on 31/03/2017.
 */
public class SystemTimer implements Timer {

    private String executorName;
    private long tickMs;
    private int wheelSize;
    private long startMs;
    private ExecutorService taskExecutor;
    private DelayQueue<TimerTaskList> delayQueue;
    private AtomicInteger taskCounter;
    private TimingWheel timingWheel;
    private ReentrantReadWriteLock lock;
    private ReentrantReadWriteLock.ReadLock readLock;
    private ReentrantReadWriteLock.WriteLock writeLock;


    public SystemTimer(final String executorName, long tickMs, int wheelSize, long startMs) {
        this.executorName = executorName;
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.startMs = startMs;
        this.taskExecutor = Executors.newFixedThreadPool(2100, r -> {
            Thread thread = new Thread(r);
            thread.setName("timer-executor-" + executorName);
            return thread;
        });
        this.delayQueue = new DelayQueue<>();
        this.taskCounter = new AtomicInteger(0);
        this.timingWheel = new TimingWheel(tickMs, wheelSize, startMs, taskCounter, delayQueue);
        this.lock = new ReentrantReadWriteLock();
        this.readLock = this.lock.readLock();
        this.writeLock = this.lock.writeLock();
    }

    @Override
    public void add(TimerTask timerTask) {
        this.readLock.lock();
        try {
            addTimerTaskEntry(new TimerTaskList.TimerTaskEntry(timerTask,
                    timerTask.getDelayMs() + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))
            );
        } finally {
            readLock.unlock();
        }
    }

    private void addTimerTaskEntry(TimerTaskList.TimerTaskEntry timerTaskEntry) {
        if (!timingWheel.add(timerTaskEntry)) {
            taskExecutor.submit(timerTaskEntry.getTimerTask());
        }
    }

    @Override
    public boolean advanceClock(long timeoutMs) {
        try {
            TimerTaskList bucket = delayQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (bucket != null) {
                writeLock.lock();
                try {
                    while (bucket != null) {
                        timingWheel.advanceClock(bucket.getExpiration());
                        bucket.flush(timerTaskEntry -> {
                            addTimerTaskEntry(timerTaskEntry);
                            return null;
                        });
                        bucket = delayQueue.poll();
                    }
                } finally {
                    writeLock.unlock();
                }
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int size() {
        return taskCounter.get();
    }

    @Override
    public void shutdown() {
        this.taskExecutor.shutdown();
    }
}


