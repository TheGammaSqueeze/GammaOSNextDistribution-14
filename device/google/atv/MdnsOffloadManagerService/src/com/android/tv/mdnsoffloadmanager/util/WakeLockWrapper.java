package com.android.tv.mdnsoffloadmanager.util;

import android.os.PowerManager;

/**
 * Wrapper around {@link android.os.PowerManager.WakeLock} for testing purposes.
 */
public class WakeLockWrapper {
    private final PowerManager.WakeLock mLock;

    public WakeLockWrapper(PowerManager.WakeLock lock) {
        this.mLock = lock;
    }

    /**
     * @see PowerManager.WakeLock#acquire()
     */
    public void acquire(long timeout) {
        mLock.acquire(timeout);
    }

    /**
     * @see PowerManager.WakeLock#release()
     */
    public void release() {
        mLock.release();
    }
}
