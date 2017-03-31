package io.github.lysu.smalltw;

/**
 * Created by robi on 31/03/2017.
 */
public interface Timer {

    void add(TimerTask timerTask);

    boolean advanceClock(long timeoutMs);

    int size();

    void shutdown();

}