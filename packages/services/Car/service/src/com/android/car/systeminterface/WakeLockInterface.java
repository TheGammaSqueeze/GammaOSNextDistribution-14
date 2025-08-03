/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.systeminterface;

import android.car.builtin.power.PowerManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Display;

import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;

/**
 * Interface that abstracts wake lock operations
 */
public interface WakeLockInterface {

    /**
     * Releases all wakelocks.
     *
     * @param displayId Target display
     */
    void releaseAllWakeLocks(int displayId);

    /**
     * Acquires partial wakelock.
     *
     * @param displayId Target display
     * @see android.os.PowerManager#PARTIAL_WAKE_LOCK
     */
    void switchToPartialWakeLock(int displayId);

    /**
     * Acquires full wakelock. This can wake up the display if display is asleep.
     *
     * @param displayId Target display
     */
    void switchToFullWakeLock(int displayId);

    class DefaultImpl implements WakeLockInterface {
        private static final String TAG = WakeLockInterface.class.getSimpleName();

        private final Context mContext;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseArray<Pair</* Full */WakeLock, /* Partial */WakeLock>>
                mPerDisplayWakeLocks = new SparseArray<>();

        DefaultImpl(Context context) {
            mContext = context;
            DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
            displayManager.registerDisplayListener(mDisplayListener, /* handler= */ null);


            for (Display display : displayManager.getDisplays()) {
                int displayId = display.getDisplayId();
                Pair<WakeLock, WakeLock> wakeLockPair = createWakeLockPair(displayId);
                synchronized (mLock) {
                    mPerDisplayWakeLocks.put(displayId, wakeLockPair);
                }
            }
        }

        @Override
        public void switchToPartialWakeLock(int displayId) {
            Pair<WakeLock, WakeLock> wakeLockPair;
            synchronized (mLock) {
                wakeLockPair = mPerDisplayWakeLocks.get(displayId);
            }
            if (wakeLockPair == null) {
                Slogf.w(TAG, "WakeLocks for display %d is null", displayId);
                return;
            }
            WakeLock partialWakeLock = wakeLockPair.second;
            WakeLock fullWakeLock = wakeLockPair.first;
            if (!partialWakeLock.isHeld()) {
                // CPMS controls the wake lock duration, so we don't use timeout when calling
                // acquire().
                partialWakeLock.acquire();
            }
            if (fullWakeLock.isHeld()) {
                fullWakeLock.release();
            }
        }

        @Override
        public void switchToFullWakeLock(int displayId) {
            Pair<WakeLock, WakeLock> wakeLockPair;
            synchronized (mLock) {
                wakeLockPair = mPerDisplayWakeLocks.get(displayId);
            }
            if (wakeLockPair == null) {
                Slogf.w(TAG, "WakeLocks for display %d is null", displayId);
                return;
            }
            WakeLock fullWakeLock = wakeLockPair.first;
            WakeLock partialWakeLock = wakeLockPair.second;
            if (!fullWakeLock.isHeld()) {
                // CPMS controls the wake lock duration, so we don't use timeout when calling
                // acquire().
                fullWakeLock.acquire();
            }
            if (partialWakeLock.isHeld()) {
                partialWakeLock.release();
            }
        }

        @Override
        public void releaseAllWakeLocks(int displayId) {
            Pair<WakeLock, WakeLock> wakeLockPair;
            synchronized (mLock) {
                wakeLockPair = mPerDisplayWakeLocks.get(displayId);
            }
            if (wakeLockPair == null) {
                Slogf.w(TAG, "WakeLocks for display %d is null", displayId);
                return;
            }
            WakeLock fullWakeLock = wakeLockPair.first;
            WakeLock partialWakeLock = wakeLockPair.second;
            if (fullWakeLock.isHeld()) {
                fullWakeLock.release();
            }
            if (partialWakeLock.isHeld()) {
                partialWakeLock.release();
            }
        }

        private Pair<WakeLock, WakeLock> createWakeLockPair(int displayId) {
            StringBuilder tag = new StringBuilder(CarLog.TAG_POWER).append(":")
                    .append(displayId);
            WakeLock fullWakeLock = PowerManagerHelper.newWakeLock(mContext,
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    tag.toString(), displayId);
            WakeLock partialWakeLock = PowerManagerHelper.newWakeLock(mContext,
                    PowerManager.PARTIAL_WAKE_LOCK, tag.toString(), displayId);
            Slogf.d(TAG, "createWakeLockPair displayId=%d", displayId);
            return Pair.create(fullWakeLock, partialWakeLock);
        }

        DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Slogf.d(TAG, "onDisplayAdded displayId=%d", displayId);
                Pair<WakeLock, WakeLock> wakeLockPair = createWakeLockPair(displayId);

                synchronized (mLock) {
                    mPerDisplayWakeLocks.put(displayId, wakeLockPair);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Slogf.d(TAG, "onDisplayRemoved displayId=%d", displayId);
                Pair<WakeLock, WakeLock> wakeLockPair;
                synchronized (mLock) {
                    wakeLockPair = mPerDisplayWakeLocks.get(displayId);
                    if (wakeLockPair == null) {
                        Slogf.w(TAG, "WakeLocks for display %d is null", displayId);
                        return;
                    }
                    mPerDisplayWakeLocks.remove(displayId);
                }
                WakeLock fullWakeLock = wakeLockPair.first;
                WakeLock partialWakeLock = wakeLockPair.second;
                if (fullWakeLock.isHeld()) {
                    fullWakeLock.release();
                }
                if (partialWakeLock.isHeld()) {
                    partialWakeLock.release();
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // ignore
            }
        };
    }
}
