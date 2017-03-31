package io.github.lysu.smalltw;

/**
 * Created by robi on 31/03/2017.
 */
public abstract class TimerTask implements Runnable {

    private TimerTaskList.TimerTaskEntry timerTaskEntry;
    private long delayMs;

    public long getDelayMs() {
        return delayMs;
    }

    public TimerTask(long delayMs) {
        this.delayMs = delayMs;
    }

    public TimerTaskList.TimerTaskEntry getTimerTaskEntry() {
        return timerTaskEntry;
    }

    public void setTimerTaskEntry(TimerTaskList.TimerTaskEntry timerTaskEntry) {
        this.timerTaskEntry = timerTaskEntry;
    }
}
