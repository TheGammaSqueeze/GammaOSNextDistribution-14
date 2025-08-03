package com.android.development;

public class ProgressBar {

    private final String mLabel;
    private final long mMax;
    private final Thread mThread;

    private final Object lock = new Object();

    private volatile int mCurPercent = 0;
    private volatile boolean mRunning = true;

    public ProgressBar(String label, long max) {
        mLabel = label;
        mMax = max;
        mThread = new Thread(this::loop);
        mThread.start();
    }

    public void update(long value) {
        synchronized (lock) {
            int p = (int) ((100 * value) / mMax);

            if (p != mCurPercent) {
                mCurPercent = p;
                lock.notify();
            }
        }
    }

    public void finish() {
        mRunning = false;
        synchronized (lock) {
            lock.notify();
        }
        try {
            mThread.join();
        } catch (InterruptedException e) { }
    }

    private void loop() {
        while (mRunning) {
            synchronized (lock) {
                System.out.print('\r');
                System.out.print(mLabel + ": " + mCurPercent + "%");
                try {
                    lock.wait();
                } catch (InterruptedException e) { }
            }
        }
        System.out.print('\r');
        System.out.println(mLabel + ": Done");
    }
}
