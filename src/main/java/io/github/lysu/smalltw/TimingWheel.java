package io.github.lysu.smalltw;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by robi on 30/03/2017.
 */
public class TimingWheel {

    private long tickMs;
    private int wheelSize;
    private long startMs;
    private AtomicInteger taskCounter;
    private DelayQueue<TimerTaskList> queue;
    private List<TimerTaskList> buckets;

    private long interval;
    private long currentTime;

    private volatile TimingWheel overflowWheel;

    public TimingWheel(long tickMs, int wheelSize, long startMs, AtomicInteger taskCounter, DelayQueue<TimerTaskList> queue) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.startMs = startMs;
        this.taskCounter = taskCounter;
        this.queue = queue;
        this.interval = tickMs * wheelSize;
        this.currentTime = startMs - (startMs % tickMs);
        this.buckets = new ArrayList<>(wheelSize);
        for (int i = 0; i < wheelSize; i++) {
            this.buckets.add(new TimerTaskList(taskCounter));
        }
    }

    public boolean add(TimerTaskList.TimerTaskEntry timerTaskEntry) {
        long expiration = timerTaskEntry.getExpirationMs();

        if (timerTaskEntry.isCancelled()) {
            // Cancelled
            return false;
        } else if (expiration < currentTime + tickMs) {
            // Already expired
            return false;
        } else if (expiration < currentTime + interval) {
            // Put in its own bucket
            long virtualId = expiration / tickMs;
            TimerTaskList bucket = buckets.get((int) (virtualId % wheelSize));
            bucket.add(timerTaskEntry);

            // Set the bucket expiration time
            if (bucket.setExpiration(virtualId * tickMs)) {
                // The bucket needs to be enqueued because it was an expired bucket
                // We only need to enqueue the bucket when its expiration time has changed, i.e. the wheel has advanced
                // and the previous buckets gets reused; further calls to set the expiration within the same wheel cycle
                // will pass in the same value and hence return false, thus the bucket with the same expiration will not
                // be enqueued multiple times.
                queue.offer(bucket);
            }
            return true;
        } else {
            // Out of the interval. Put it into the parent timer
            if (overflowWheel == null) {
                addOverflowWheel();
            }
            return overflowWheel.add(timerTaskEntry);
        }

    }

    private void addOverflowWheel() {
        synchronized (this) {
            if (overflowWheel == null) {
                overflowWheel = new TimingWheel(
                        this.interval,
                        this.wheelSize,
                        this.currentTime,
                        this.taskCounter,
                        queue
                );
            }
        }
    }

    public void advanceClock(long timeMs) {
        if (timeMs >= currentTime + tickMs) {
            currentTime = timeMs - (timeMs % tickMs);
            // Try to advance the clock of the overflow wheel if present
            if (overflowWheel != null) {
                overflowWheel.advanceClock(currentTime);
            }
        }
    }

}
