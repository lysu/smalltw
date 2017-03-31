package io.github.lysu.smalltw;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by robi on 31/03/2017.
 */
public class TimerTest {

    @Test
    public void testSystemTimer() throws InterruptedException {



        CountDownLatch countDownLatch = new CountDownLatch(5);

        long currentTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        Timer timer = new SystemTimer("test", 1, 20, currentTime);

        long start = System.currentTimeMillis();
        timer.add(new TimerTask(300) {
            @Override
            public void run() {
                runPrint(start, countDownLatch);
            }
        });
        timer.add(new TimerTask(3000) {
            @Override
            public void run() {
                runPrint(start, countDownLatch);
            }
        });
        timer.add(new TimerTask(3000) {
            @Override
            public void run() {
                runPrint(start, countDownLatch);
            }
        });
        timer.add(new TimerTask(1000) {
            @Override
            public void run() {
                runPrint(start, countDownLatch);
            }
        });
        timer.add(new TimerTask(30000) {
            @Override
            public void run() {
                runPrint(start, countDownLatch);
            }
        });

//        new Thread(() -> {
            while (true) {
                timer.advanceClock(0);
            }
//        }).start();

//        countDownLatch.await();
    }

    private void runPrint(long start, CountDownLatch countDownLatch) {
        System.out.println("run @ " + (System.currentTimeMillis() - start));
        countDownLatch.countDown();
    }

}
