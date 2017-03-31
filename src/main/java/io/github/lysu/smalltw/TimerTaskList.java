package io.github.lysu.smalltw;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Created by robi on 30/03/2017.
 */
public class TimerTaskList implements Delayed {

    private AtomicInteger taskCounter;
    private TimerTaskEntry root;
    private AtomicLong expiration;

    public TimerTaskList(AtomicInteger taskCounter) {
        this.taskCounter = taskCounter;
        this.root = new TimerTaskEntry(null, -1);
        this.root.next = root;
        this.root.prev = root;
        this.expiration = new AtomicLong(-1L);
    }

    // Apply the supplied function to each of tasks in this list
    public void foreach(Function<TimerTask, Void> f) {
        synchronized (this) {
            TimerTaskEntry entry = root.next;
            while (entry != root) {
                TimerTaskEntry nextEntry = entry.next;
                f.apply(entry.timerTask);
                entry = nextEntry;
            }
        }
    }

    public void flush(Function<TimerTaskEntry, Void> f) {
        synchronized (this) {
            TimerTaskEntry head = root.next;
            while (head != root) {
                remove(head);
                f.apply(head);
                head = root.next;
            }
            expiration.set(-1L);
        }
    }

    public long getDelay(TimeUnit unit) {
        return unit.convert(
                Math.max(this.getExpiration() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), 0),
                TimeUnit.MILLISECONDS);
    }

    public int compareTo(Delayed d) {
        TimerTaskList other = (TimerTaskList) d;

        if (getExpiration() < other.getExpiration()) {
            return -1;
        }
        if (getExpiration() > other.getExpiration()) {
            return 1;
        }
        return 0;
    }

    public void add(TimerTaskEntry timerTaskEntry) {
        boolean done = false;
        while (!done) {
            // Remove the timer task entry if it is already in any other list
            // We do this outside of the sync block below to avoid deadlocking.
            // We may retry until timerTaskEntry.list becomes null.
            timerTaskEntry.remove();

            synchronized (this) {
                done = timerTaskEntry.addEntryTo(this);
            }
        }
    }

    public void remove(TimerTaskEntry timerTaskEntry) {
        synchronized (this) {
            timerTaskEntry.removeEntryFrom(this);
        }
    }

    public boolean setExpiration(long expirationMs) {
        return this.expiration.getAndSet(expirationMs) != expirationMs;
    }

    public long getExpiration() {
        return this.expiration.get();
    }

    public static class TimerTaskEntry {

        private TimerTask timerTask;
        private long expirationMs;

        private volatile TimerTaskList list;
        private TimerTaskEntry next;
        private TimerTaskEntry prev;


        public TimerTaskEntry(TimerTask timerTask, long expirationMs) {
            this.timerTask = timerTask;
            this.expirationMs = expirationMs;
            if (timerTask != null) {
                timerTask.setTimerTaskEntry(this);
            }
        }

        public void remove() {
            TimerTaskList currentList = list;
            // If remove is called when another thread is moving the entry from a task entry list to another,
            // this may fail to remove the entry due to the change of value of list. Thus, we retry until the list becomes null.
            // In a rare case, this thread sees null and exits the loop, but the other thread insert the entry to another list later.
            while (currentList != null) {
                currentList.remove(this);
                currentList = list;
            }
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public TimerTask getTimerTask() {
            return timerTask;
        }

        public boolean isCancelled() {
            return this.timerTask.getTimerTaskEntry() != this;
        }

        private boolean addEntryTo(TimerTaskList timerTaskList) {
            boolean done = false;
            synchronized (this) {
                if (list == null) {
                    // put the timer task entry to the end of the list. (root.prev points to the tail entry)
                    TimerTaskEntry tail = timerTaskList.root.prev;
                    next = timerTaskList.root;
                    prev = tail;
                    list = timerTaskList;
                    tail.next = this;
                    timerTaskList.root.prev = this;
                    timerTaskList.taskCounter.incrementAndGet();
                    done = true;
                }
            }
            return done;
        }

        private void removeEntryFrom(TimerTaskList timerTaskList) {
            synchronized (this) {
                if (list.equals(timerTaskList)) {
                    next.prev = prev;
                    prev.next = next;
                    next = null;
                    prev = null;
                    list = null;
                    timerTaskList.taskCounter.decrementAndGet();
                }
            }
        }
    }

}
