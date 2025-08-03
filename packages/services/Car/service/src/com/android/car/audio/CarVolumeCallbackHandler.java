/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.car.media.ICarVolumeCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages callbacks for changes in car volume
 */
final class CarVolumeCallbackHandler extends RemoteCallbackList<ICarVolumeCallback>  {
    private static final String REQUEST_HANDLER_THREAD_NAME = "CarVolumeCallback";

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            REQUEST_HANDLER_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<List<IBinder>> mUidToBindersMap = new SparseArray<>();

    void release() {
    }

    public void registerCallback(IBinder binder, int uid, boolean priority) {
        ICarVolumeCallback callback = ICarVolumeCallback.Stub.asInterface(binder);
        synchronized (mLock) {
            registerCallbackLocked(callback, uid, priority);
            List<IBinder> binders = mUidToBindersMap.get(uid);
            if (binders == null) {
                binders = new ArrayList<IBinder>();
                mUidToBindersMap.put(uid, binders);
            }

            if (!binders.contains(binder)) {
                binders.add(binder);
            }
        }
    }

    public void unregisterCallback(IBinder binder, int uid) {
        ICarVolumeCallback callback = ICarVolumeCallback.Stub.asInterface(binder);
        synchronized (mLock) {
            unregisterCallbackLocked(callback);
            List<IBinder> binders = mUidToBindersMap.get(uid);
            if (binders == null) {
                // callback is not registered. nothing to remove.
                return;
            }

            if (binders.contains(binder)) {
                binders.remove(binder);
            }

            if (binders.isEmpty()) {
                mUidToBindersMap.remove(uid);
            }
        }
    }

    private void registerCallbackLocked(ICarVolumeCallback callback, int uid, boolean priority) {
        register(callback, new CallerPriorityCookie(uid, priority));
    }

    private void unregisterCallbackLocked(ICarVolumeCallback callback) {
        unregister(callback);
    }

    // handle the special case where an app registers to both ICarVolumeCallback
    // and ICarVolumeEventCallback.
    // priority is given to ICarVolumeEventCallback
    //  - when registered, deprioritize ICarVolumeCallback
    //  - when unregistered, reprioritize ICarVolumeCallback
    void checkAndRepriotize(int uid, boolean priority) {
        synchronized (mLock) {
            if (mUidToBindersMap.contains(uid)) {
                List<IBinder> binders = mUidToBindersMap.get(uid);
                // Re-register with new priority.
                // cannot check the priority without broadcast. forced to unregister and register
                // again even if priority is same. optimize in future.
                for (int i = 0; i < binders.size(); i++) {
                    ICarVolumeCallback callback =
                            ICarVolumeCallback.Stub.asInterface(binders.get(i));
                    unregisterCallbackLocked(callback);
                    registerCallbackLocked(callback, uid, priority);
                }
            }
        }
    }

    public void onVolumeGroupChange(int zoneId, int groupId, int flags) {
        mHandler.post(() -> {
            int count = beginBroadcast();
            for (int index = 0; index < count; index++) {
                CallerPriorityCookie cookie = (CallerPriorityCookie) getBroadcastCookie(index);
                ICarVolumeCallback callback = getBroadcastItem(index);
                if (!cookie.mPriority) {
                    continue;
                }

                try {
                    callback.onGroupVolumeChanged(zoneId, groupId, flags);
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_AUDIO, "Failed to callback onGroupVolumeChanged", e);
                }
            }
            finishBroadcast();
        });
    }

    public void onGroupMuteChange(int zoneId, int groupId, int flags) {
        mHandler.post(() -> {
            int count = beginBroadcast();
            for (int index = 0; index < count; index++) {
                CallerPriorityCookie cookie = (CallerPriorityCookie) getBroadcastCookie(index);
                ICarVolumeCallback callback = getBroadcastItem(index);
                if (!cookie.mPriority) {
                    continue;
                }

                try {
                    callback.onGroupMuteChanged(zoneId, groupId, flags);
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_AUDIO, "Failed to callback onGroupMuteChanged", e);
                }
            }
            finishBroadcast();
        });
    }

    void onMasterMuteChanged(int zoneId, int flags) {
        mHandler.post(() -> {
            int count = beginBroadcast();
            for (int index = 0; index < count; index++) {
                ICarVolumeCallback callback = getBroadcastItem(index);
                try {
                    callback.onMasterMuteChanged(zoneId, flags);
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_AUDIO, "Failed to callback onMasterMuteChanged", e);
                }
            }
            finishBroadcast();
        });
    }

    @Override
    public void onCallbackDied(ICarVolumeCallback callback, Object cookie) {
        CallerPriorityCookie caller = (CallerPriorityCookie) cookie;
        // when client dies, clean up obsolete user-id from the list
        synchronized (mLock) {
            mUidToBindersMap.remove(caller.mUid);
        }
    }

    private static final class CallerPriorityCookie {
        public final int mUid;
        public final boolean mPriority;

        CallerPriorityCookie(int uid, boolean priority) {
            mUid = uid;
            mPriority = priority;
        }
    }
}
