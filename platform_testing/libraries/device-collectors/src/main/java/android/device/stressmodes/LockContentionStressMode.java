/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.device.stressmodes;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.listener.InstrumentationRunListener;

import org.junit.runner.Description;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.BiConsumer;

// TODO(b/169588447): Consider converting to @Rule
public class LockContentionStressMode extends InstrumentationRunListener {
    private static final String TAG = LockContentionStressMode.class.getSimpleName();
    private int mPauseBetweenAttemptsMs;
    private double mProbabilityOfHoldingLock;
    private int mMaxHoldingLockTimeMs;
    private final CyclicBarrier mEnterStress = new CyclicBarrier(4);
    private final CyclicBarrier mLeaveStress = new CyclicBarrier(4);
    private IBinder mHoldLockToken;

    public LockContentionStressMode() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();

        final PackageManager pm = context.getPackageManager();
        createLockContentionThread("Contention-PM", pm::holdLock);

        final WindowManager wm = context.getSystemService(WindowManager.class);
        createLockContentionThread("Contention-WM", wm::holdLock);

        final ActivityManager am = context.getSystemService(ActivityManager.class);
        createLockContentionThread("Contention-AM", am::holdLock);
    }

    private void createLockContentionThread(
            String threadName, BiConsumer<IBinder, Integer> holdLockMethod) {
        new Thread(
                        () -> {
                            while (true) {
                                lockContentionUntilToldToLeave(holdLockMethod);
                            }
                        },
                        threadName)
                .start();
    }

    // Creates a lock contention for a single invocation of the test.
    private void lockContentionUntilToldToLeave(BiConsumer<IBinder, Integer> holdLockMethod) {
        try {
            // Sync with the main thread before entering stress mode.
            mEnterStress.await();

            // While the main thread doesn't indicate that it wants to leave the stress mode...
            while (mLeaveStress.getNumberWaiting() == 0) {
                SystemClock.sleep(mPauseBetweenAttemptsMs);
                if (Math.random() < mProbabilityOfHoldingLock) {
                    holdLockMethod.accept(
                            mHoldLockToken, (int) (Math.random() * mMaxHoldingLockTimeMs));
                }
            }

            // Sync with the main thread after leaving stress mode.
            mLeaveStress.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        super.testRunStarted(description);
        final Bundle args = InstrumentationRegistry.getArguments();
        final String intensity = args.getString("lock-stress-intensity", "P50");
        Log.e(TAG, "lock-stress-intensity: " + intensity);
        switch (intensity) {
            case "P50":
                mPauseBetweenAttemptsMs = 5;
                mProbabilityOfHoldingLock = 0.5;
                mMaxHoldingLockTimeMs = 100;
                break;
            case "P95":
                mPauseBetweenAttemptsMs = 0;
                mProbabilityOfHoldingLock = 1;
                mMaxHoldingLockTimeMs = 100;
                break;
            default:
                throw new IllegalArgumentException(
                        "lock-stress-intensity: incorrect value: " + intensity);
        }
    }

    @Override
    public final void testStarted(Description description) throws Exception {
        Log.d(
                TAG,
                "LockContentionStressMode.testStarted: entering lock contention mode: "
                        + "mPauseBetweenAttemptsMs="
                        + mPauseBetweenAttemptsMs
                        + ", mProbabilityOfHoldingLock="
                        + mProbabilityOfHoldingLock
                        + ", mMaxHoldingLockTimeMs="
                        + mMaxHoldingLockTimeMs);

        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mHoldLockToken = getInstrumentation().getContext().getPackageManager().getHoldLockToken();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

        mEnterStress.await(); // Sync with stress threads before entering stress mode.
        super.testStarted(description);
    }

    @Override
    public final void testFinished(Description description) throws Exception {
        mLeaveStress.await(); // Sync with stress threads before leaving stress mode.
        mHoldLockToken = null;
        super.testFinished(description);
        Log.d(TAG, "LockContentionStressMode.testFinished: leaving lock contention mode");
    }
}
