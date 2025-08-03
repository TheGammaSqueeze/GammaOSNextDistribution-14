/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar.element;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.util.SparseIntArray;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.CommandQueue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Controls the notification of CarSystemBarElements that should or should not be disabled based on
 * the current status bar manager flags.
 */
@SysUISingleton
public class CarSystemBarElementStatusBarDisableController {
    private final CommandQueue mCommandQueue;
    private final ActivityManager mActivityManager;
    private final List<DataItem> mListeners = new ArrayList<>();
    private final SparseIntArray mStatusBarStates = new SparseIntArray();
    private final SparseIntArray mStatusBar2States = new SparseIntArray();
    private boolean mLockTaskModeLocked = false;

    private final CommandQueue.Callbacks mCommandQueueCallback = new CommandQueue.Callbacks() {
        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {
            refreshSystemBarDisabledStates(displayId, state1, state2);
        }
    };

    @Inject
    public CarSystemBarElementStatusBarDisableController(Context context,
            CommandQueue commandQueue) {
        mCommandQueue = commandQueue;
        mActivityManager = context.getSystemService(ActivityManager.class);
    }

    /**
     * Add a StatusBar disable listener for a given element.
     * @param listener the listener for the element controller
     * @param displayId the display id the view is located on
     * @param disableFlags the {@link StatusBarManager.DisableFlags} specified by the element
     * @param disable2Flags the {@link StatusBarManager.Disable2Flags} specified by the element
     * @param disableForLockTaskModeLocked whether the view is affected by the system lock task mode
     */
    public void addListener(@NonNull Listener listener, int displayId,
            @StatusBarManager.DisableFlags int disableFlags,
            @StatusBarManager.Disable2Flags int disable2Flags,
            boolean disableForLockTaskModeLocked) {
        boolean wasEmpty = mListeners.isEmpty();
        DataItem item = new DataItem(listener, displayId, disableFlags, disable2Flags,
                disableForLockTaskModeLocked);
        synchronized (mListeners) {
            mListeners.add(item);
            if (wasEmpty) {
                mCommandQueue.addCallback(mCommandQueueCallback);
            } else {
                notifyDataItem(item);
            }
        }
    }

    /** Remove the specified StatusBar disable listener */
    public void removeListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.removeIf(item -> item.sameOrEmpty(listener));
            if (mListeners.isEmpty()) {
                mCommandQueue.removeCallback(mCommandQueueCallback);
            }
        }
    }

    private void refreshSystemBarDisabledStates(
            int displayId,
            @StatusBarManager.DisableFlags int disableFlags,
            @StatusBarManager.Disable2Flags int disable2Flags) {
        int diff = mStatusBarStates.get(displayId) ^ disableFlags;
        int diff2 = mStatusBar2States.get(displayId) ^ disable2Flags;
        boolean lockTaskModeLocked =
                mActivityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED;
        boolean lockTaskModeChanged = mLockTaskModeLocked == lockTaskModeLocked;
        if (diff == 0 && diff2 == 0 && !lockTaskModeChanged) return;

        mStatusBarStates.put(displayId, disableFlags);
        mStatusBar2States.put(displayId, disable2Flags);
        mLockTaskModeLocked = lockTaskModeLocked;

        synchronized (mListeners) {
            List<DataItem> changedList = mListeners.stream().filter(
                    item -> item.isAffectedByChange(displayId, diff, diff2,
                            lockTaskModeChanged)).toList();
            changedList.forEach(this::notifyDataItem);
        }
    }

    private void notifyDataItem(DataItem item) {
        Listener listener = item.mListener.get();
        if (listener != null) {
            int displayId = item.mDisplayId;
            listener.onStatusBarDisabledStateChanged(
                    item.isDisabledByFlags(mStatusBarStates.get(displayId),
                            mStatusBar2States.get(displayId), mLockTaskModeLocked));
        }
    }

    public interface Listener {
        /** Notifies the status bar disabled state has changed for this element. */
        void onStatusBarDisabledStateChanged(boolean disabled);
    }

    private static class DataItem {
        WeakReference<Listener> mListener;
        int mDisplayId;
        @StatusBarManager.DisableFlags
        int mDisableFlags;
        @StatusBarManager.Disable2Flags
        int mDisable2Flags;
        boolean mDisableForLockTaskModeLocked;

        DataItem(Listener listener, int displayId, int disableFlags, int disable2Flags,
                boolean lockTaskMode) {
            mListener = new WeakReference<>(listener);
            mDisplayId = displayId;
            mDisableFlags = disableFlags;
            mDisable2Flags = disable2Flags;
            mDisableForLockTaskModeLocked = lockTaskMode;
        }

        boolean isAffectedByChange(int displayId, int diff, int diff2,
                boolean lockTaskModeChanged) {
            if (mListener.get() == null) return false;
            if (mDisplayId != displayId) return false;
            return ((mDisableFlags & diff) > 0) || ((mDisable2Flags & diff2) > 0) || (
                    mDisableForLockTaskModeLocked && lockTaskModeChanged);
        }

        boolean isDisabledByFlags(int disableFlags, int disable2Flags, boolean lockTaskModeLocked) {
            return ((mDisableFlags & disableFlags) > 0) || ((mDisable2Flags & disable2Flags) > 0)
                    || (mDisableForLockTaskModeLocked && lockTaskModeLocked);
        }

        boolean sameOrEmpty(Listener listener) {
            return mListener.get() == null || mListener.get().equals(listener);
        }
    }
}
