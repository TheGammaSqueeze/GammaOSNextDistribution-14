/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.audio;

import android.car.builtin.util.Slogf;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.ICarVolumeEventCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.internal.annotations.GuardedBy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages callbacks for volume events
 */
final class CarVolumeEventHandler extends RemoteCallbackList<ICarVolumeEventCallback> {
    private static final String REQUEST_HANDLER_THREAD_NAME = "CarVolumeCallback";

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            REQUEST_HANDLER_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Set<Integer> mUids = new HashSet<Integer>();

    void release() {
        synchronized (mLock) {
            mUids.clear();
        }
    }

    public void registerCarVolumeEventCallback(ICarVolumeEventCallback callback, int uid) {
        Objects.requireNonNull(callback, "Car volume  callback can not be null");

        synchronized (mLock) {
            register(callback, uid);
            mUids.add(uid);
        }
    }

    public void unregisterCarVolumeEventCallback(ICarVolumeEventCallback callback, int uid) {
        Objects.requireNonNull(callback, "Car volume  callback can not be null");

        synchronized (mLock) {
            unregister(callback);
            mUids.remove(uid);
        }
    }

    boolean checkIfUidIsRegistered(int uid) {
        synchronized (mLock) {
            return mUids.contains(uid);
        }
    }

    void onVolumeGroupEvent(List<CarVolumeGroupEvent> events) {
        mHandler.post(() -> {
            int count = beginBroadcast();
            for (int index = 0; index < count; index++) {
                try {
                    ICarVolumeEventCallback callback = getBroadcastItem(index);
                    callback.onVolumeGroupEvent(List.copyOf(events));
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_AUDIO, "Failed to callback onVolumeGroupEvent", e);
                }
            }
            finishBroadcast();
        });
    }

    void onMasterMuteChanged(int zoneId, int flags) {
        mHandler.post(() -> {
            int count = beginBroadcast();
            for (int index = 0; index < count; index++) {
                try {
                    ICarVolumeEventCallback callback = getBroadcastItem(index);
                    callback.onMasterMuteChanged(zoneId, flags);
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_AUDIO, "Failed to callback onMasterMuteChanged", e);
                }
            }
            finishBroadcast();
        });
    }

    @Override
    public void onCallbackDied(ICarVolumeEventCallback callback, Object cookie) {
        int uid = (int) cookie;
        // when client dies, clean up obsolete user-id from the list
        synchronized (mLock) {
            mUids.remove(uid);
        }
    }
}
