/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.test.AndroidTestCase;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class PowerManager_WakeLockTest extends AndroidTestCase {
    private static final String TAG = "PowerManager_WakeLockTest";

    private final Executor mExec = Executors.newSingleThreadExecutor();
    boolean mCurrentState1;
    boolean mCurrentState2;

    /**
     * Test points:
     * 1 Makes sure the device is on at the level you asked when you created the wake lock
     * 2 Release your claim to the CPU or screen being on
     * 3 Sets whether this WakeLock is ref counted
     */
    public void testPowerManagerWakeLock() throws InterruptedException {
        PowerManager pm = (PowerManager)  getContext().getSystemService(Context.POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
        assertNotNull(wl.toString());

        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());

        // Try ref-counted acquire/release
        wl.setReferenceCounted(true);
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());

        // Try non-ref-counted
        wl.setReferenceCounted(false);
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.acquire();
        assertTrue(wl.isHeld());
        wl.release();
        assertFalse(wl.isHeld());

        // test acquire(long)
        wl.acquire(PowerManagerTest.TIME);
        assertTrue(wl.isHeld());
        Thread.sleep(PowerManagerTest.TIME + PowerManagerTest.MORE_TIME);
        assertFalse(wl.isHeld());
    }

    public void testWakeLockTimeout() throws Exception {
        final PowerManager pm = getContext().getSystemService(PowerManager.class);

        final WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        lock.acquire(2000);
        SystemClock.sleep(4000);

        lock.release();
    }

    /**
     * setStateListener() is called before acquire(), the callback is sent to framework by
     * acquire() call.
     * @throws Exception
     */
    public void testWakeLockCallback() throws Exception {
        final PowerManager pm = getContext().getSystemService(PowerManager.class);
        final WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        final Semaphore semaphore = new Semaphore(0);
        lock.setStateListener(mExec, new PowerManager.WakeLockStateListener() {
            @Override
            public void onStateChanged(boolean enabled) {
                mCurrentState1 = enabled;
                semaphore.release();
            }
        });
        lock.acquire();
        assertTrue(semaphore.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState1);

        lock.release();
        assertTrue(semaphore.tryAcquire(5, SECONDS));
        assertFalse(mCurrentState1);

        lock.acquire();
        assertTrue(semaphore.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState1);

        lock.release();
        assertTrue(semaphore.tryAcquire(5, SECONDS));
        assertFalse(mCurrentState1);
    }

    /**
     * setStateListener() can be called after acquire() call to change the listener.
     * @throws Exception
     */
    public void testWakeLockCallback_changeListener() throws Exception {
        final PowerManager pm = getContext().getSystemService(PowerManager.class);
        final WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        final Semaphore semaphore1 = new Semaphore(0);
        final Semaphore semaphore2 = new Semaphore(0);
        final PowerManager.WakeLockStateListener listener1 =
                new PowerManager.WakeLockStateListener() {
            @Override
            public void onStateChanged(boolean enabled) {
                mCurrentState1 = enabled;
                semaphore1.release();
            }
        };
        final PowerManager.WakeLockStateListener listener2 =
                new PowerManager.WakeLockStateListener() {
            @Override
            public void onStateChanged(boolean enabled) {
                mCurrentState2 = enabled;
                semaphore2.release();
            }
        };

        lock.setStateListener(mExec, listener1);
        lock.setReferenceCounted(false);

        lock.acquire();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState1);

        // setStateListener() is called after acquire() to change the listener.
        lock.setStateListener(mExec, listener2);

        // old listener is not notified.
        assertFalse(semaphore1.tryAcquire(5, SECONDS));
        // new listener is notified.
        assertTrue(semaphore2.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState2);

        lock.release();
        assertTrue(semaphore2.tryAcquire(5, SECONDS));
        assertFalse(mCurrentState2);

        lock.acquire();
        assertTrue(semaphore2.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState2);

        // after acquire(), change back to listener1.
        lock.setStateListener(mExec, listener1);
        lock.acquire();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState1);

        lock.release();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));
        assertFalse(mCurrentState1);
    }

    /**
     * After acquire(), setStateListener() to a null listener.
     * @throws Exception
     */
    public void testWakeLockCallback_nullListener() throws Exception {
        final PowerManager pm = getContext().getSystemService(PowerManager.class);
        final WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        final Semaphore semaphore1 = new Semaphore(0);
        final PowerManager.WakeLockStateListener listener1 =
                new PowerManager.WakeLockStateListener() {
                    @Override
                    public void onStateChanged(boolean enabled) {
                        mCurrentState1 = enabled;
                        semaphore1.release();
                    }
                };
        lock.setReferenceCounted(false);

        lock.setStateListener(mExec, listener1);

        lock.acquire();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));
        assertTrue(mCurrentState1);

        // set a null listener to cancel the listener.
        lock.setStateListener(mExec, null);

        lock.release();
        // old listener does not get notified.
        assertFalse(semaphore1.tryAcquire(5, SECONDS));

        lock.setStateListener(mExec, listener1);
        lock.acquire();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));

        lock.release();
        assertTrue(semaphore1.tryAcquire(5, SECONDS));
    }
}
