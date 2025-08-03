/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.remoteaccess.hal;

import android.annotation.Nullable;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.hardware.automotive.remoteaccess.ApState;
import android.hardware.automotive.remoteaccess.IRemoteAccess;
import android.hardware.automotive.remoteaccess.IRemoteTaskCallback;
import android.hardware.automotive.remoteaccess.ScheduleInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to mediate communication between CarRemoteAccessSerevice and remote access HAL.
 */
public final class RemoteAccessHalWrapper implements IBinder.DeathRecipient {

    private static final String TAG = CarLog.tagFor(RemoteAccessHalWrapper.class);
    private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String NOT_SUPPORTED_V1_MESSAGE =
            "is not supported by remote access HAL v1";

    private final Object mLock = new Object();
    // Callback which is given by the instance creator.
    private final RemoteAccessHalCallback mRemoteAccessHalCallback;
    // Callback which is registered to remote access HAL.
    private final IRemoteTaskCallback mRemoteTaskCallback = new RemoteTaskCallbackImpl(this);

    private final AtomicBoolean mConnecting = new AtomicBoolean();
    @GuardedBy("mLock")
    private IBinder mBinder;
    @GuardedBy("mLock")
    private IRemoteAccess mRemoteAccessHal;

    private IRemoteAccess mTestRemoteAccessHal;

    public RemoteAccessHalWrapper(RemoteAccessHalCallback callback) {
        this(callback, /* testRemoteAccessHal= */ null);
    }

    /* For car service test. */
    @VisibleForTesting
    public RemoteAccessHalWrapper(RemoteAccessHalCallback callback,
            IRemoteAccess testRemoteAccessHal) {
        mRemoteAccessHalCallback = Objects.requireNonNull(callback, "Callback cannot be null");
        mTestRemoteAccessHal = testRemoteAccessHal;
    }

    /** Initializes connection to remote task HAL service. */
    public void init() {
        try {
            connectToHal();
        } catch (Exception e) {
            Slogf.wtf(TAG, e, "Cannot connect to remote access HAL");
        }
    }

    /** Releases the internal resources. */
    public void release() {
        IRemoteAccess remoteAccessHal;
        synchronized (mLock) {
            remoteAccessHal = mRemoteAccessHal;
            mRemoteAccessHal = null;
        }
        try {
            if (remoteAccessHal != null) {
                remoteAccessHal.clearRemoteTaskCallback();
            }
        } catch (RemoteException e) {
            Slogf.w(TAG, e, "Failed to clear remote task callback");
        }
        clearBinder();
    }

    @Override
    public void binderDied() {
        Slogf.w(TAG, "Remote access HAL service died");
        synchronized (mLock) {
            mRemoteAccessHal = null;
            mBinder = null;
        }
        try {
            connectToHal();
        } catch (Exception e) {
            Slogf.wtf(TAG, e, "Cannot connect to remote access HAL");
        }
    }

    /** Check {@link IRemoteAccess#getVehicleId()}. */
    public String getVehicleId() {
        IRemoteAccess remoteAccessHal = getRemoteAccessHal();
        try {
            return remoteAccessHal.getVehicleId();
        } catch (RemoteException | RuntimeException e) {
            throw new IllegalStateException("Failed to get vehicle ID", e);
        }
    }

    /** Check {@link IRemoteAccess#getWakeupServiceName()}. */
    public String getWakeupServiceName() {
        IRemoteAccess remoteAccessHal = getRemoteAccessHal();
        try {
            return remoteAccessHal.getWakeupServiceName();
        } catch (RemoteException | RuntimeException e) {
            throw new IllegalStateException("Failed to get wakeup service name", e);
        }
    }

    /** Check {@link IRemoteAccess#getProcessorId()}. */
    public String getProcessorId() {
        IRemoteAccess remoteAccessHal = getRemoteAccessHal();
        try {
            return remoteAccessHal.getProcessorId();
        } catch (RemoteException | RuntimeException e) {
            throw new IllegalStateException("Failed to get processor ID", e);
        }
    }

    /** Check {@link IRemoteAccess#notifyApStateChange(ApState)}. */
    public boolean notifyApStateChange(boolean isReadyForRemoteTask, boolean isWakeupRequired) {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            ApState state = new ApState();
            state.isReadyForRemoteTask = isReadyForRemoteTask;
            state.isWakeupRequired = isWakeupRequired;
            remoteAccessHal.notifyApStateChange(state);
        } catch (RemoteException | RuntimeException e) {
            Slogf.w(TAG, e, "Failed to notify power state change: isReadyForRemoteTask=%b, "
                    + "isWakeupRequired=%b", isReadyForRemoteTask, isWakeupRequired);
            return false;
        }
        return true;
    }

    /**
     * Check {@link IRemoteAccess#isTaskScheduleSupported}.
     *
     * <p>Returns {@code false} if error happens.
     */
    public boolean isTaskScheduleSupported() {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            if (remoteAccessHal.getInterfaceVersion() < 2) {
                Slogf.w(TAG, "isTaskScheduleSupported %s, default to false",
                        NOT_SUPPORTED_V1_MESSAGE);
                return false;
            }
            return remoteAccessHal.isTaskScheduleSupported();
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call isTaskScheduleSupported, default to false");
            return false;
        }
    }

    /**
     * Check {@link IRemoteAccess#scheduleTask}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Client should handle the thrown exception.
     */
    public void scheduleTask(ScheduleInfo scheduleInfo)
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            remoteAccessHal.scheduleTask(scheduleInfo);
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call scheduleTask with scheduleInfo: %s", scheduleInfo);
            throw e;
        }
    }

    /**
     * Check {@link IRemoteAccess#unscheduleTask}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Do nothing if error happens.
     */
    public void unscheduleTask(String clientId, String scheduleId)
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            remoteAccessHal.unscheduleTask(clientId, scheduleId);
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call unscheduleTask with clientId: %s, scheduleId: %s",
                    clientId, scheduleId);
            throw e;
        }
    }

    /**
     * Check {@link IRemoteAccess#unscheduleAllTasks}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Do nothing if error happens.
     */
    public void unscheduleAllTasks(String clientId)
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            remoteAccessHal.unscheduleAllTasks(clientId);
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call unscheduleAllTasks with clientId: %s", clientId);
            throw e;
        }
    }

    /**
     * Check {@link IRemoteAccess#isTaskScheduled}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Returns {@code false} if error happens.
     */
    public boolean isTaskScheduled(String clientId, String scheduleId)
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            return remoteAccessHal.isTaskScheduled(clientId, scheduleId);
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call isTaskScheduled with clientId: %s, scheduleId: %s,"
                    + " default to false", clientId, scheduleId);
            throw e;
        }
    }

    /**
     * Check {@link IRemoteAccess#getAllPendingScheduledTasks}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Client should handle the thrown exception.
     */
    public List<ScheduleInfo> getAllPendingScheduledTasks(String clientId)
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            return remoteAccessHal.getAllPendingScheduledTasks(clientId);
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call getAllPendingScheduledTasks with clientId: %s",
                    clientId);
            throw e;
        }
    }

    /**
     * Check {@link IRemoteAccess#getSupportedTaskTypesForScheduling}.
     *
     * <p>Client should check {@link isTaskScheduleSupported} is {@code true} before calling this.
     *
     * <p>Client should handle the thrown exception.
     */
    public int[] getSupportedTaskTypesForScheduling()
            throws RemoteException, ServiceSpecificException {
        try {
            IRemoteAccess remoteAccessHal = getRemoteAccessHal();
            return remoteAccessHal.getSupportedTaskTypesForScheduling();
        } catch (RemoteException | ServiceSpecificException e) {
            Slogf.w(TAG, e, "Failed to call getSupportedTaskTypesForScheduling");
            throw e;
        }
    }


    private void connectToHal() {
        if (!mConnecting.compareAndSet(/* expect= */ false, /* update= */ true)) {
            Slogf.w(TAG, "Connecting to remote access HAL is in progress");
            return;
        }
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);

        IRemoteAccess remoteAccessHal;
        if (mTestRemoteAccessHal != null) {
            remoteAccessHal = mTestRemoteAccessHal;
        } else {
            t.traceBegin("connect-to-remote-access-hal");
            IBinder binder = getRemoteAccessHalService();
            t.traceEnd();
            if (binder == null) {
                mConnecting.set(/* newValue= */ false);
                throw new IllegalStateException("Remote access HAL not found");
            }

            try {
                binder.linkToDeath(this, /* flags= */ 0);
            } catch (RemoteException e) {
                mConnecting.set(/* newValue= */ false);
                throw new IllegalStateException(
                        "Failed to link a death recipient to remote access HAL", e);
            }

            synchronized (mLock) {
                if (mBinder != null) {
                    Slogf.w(TAG, "Remote access HAL is already connected");
                    binder.unlinkToDeath(this, /* flags= */ 0);
                    mConnecting.set(/* newValue= */ false);
                    return;
                }
                mBinder = binder;
                mRemoteAccessHal = IRemoteAccess.Stub.asInterface(mBinder);
                remoteAccessHal = mRemoteAccessHal;
            }
            mConnecting.set(/* newValue= */ false);
        }
        try {
            remoteAccessHal.setRemoteTaskCallback(mRemoteTaskCallback);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to set remote task callback", e);
        }
        Slogf.i(TAG, "Connected to remote access HAL");
    }

    private void clearBinder() {
        synchronized (mLock) {
            if (mBinder == null) {
                return;
            }
            mBinder.unlinkToDeath(this, /* flags= */ 0);
            mBinder = null;
        }
    }

    private IRemoteAccess getRemoteAccessHal() {
        synchronized (mLock) {
            if (mTestRemoteAccessHal != null) {
                return mTestRemoteAccessHal;
            }
            if (mRemoteAccessHal == null) {
                throw new IllegalStateException("Remote access HAL is not ready");
            }
            return mRemoteAccessHal;
        }
    }

    private void onRemoteTaskRequested(String clientId, byte[] data) {
        mRemoteAccessHalCallback.onRemoteTaskRequested(clientId, data);
    }

    /**
     * Connects to remote access HAL.
     *
     * <p>If there are multiple service implementations, connection is made in an order of service
     * declaration.
     */
    @VisibleForTesting
    @Nullable
    public static IBinder getRemoteAccessHalService() {
        String[] instances = ServiceManagerHelper.getDeclaredInstances(IRemoteAccess.DESCRIPTOR);
        if (instances == null || instances.length == 0) {
            Slogf.e(TAG, "No remote access HAL service found");
            return null;
        }
        // If there are many remote access HAL instances, they are traversed in a declaring order.
        for (int i = 0; i < instances.length; i++) {
            String fqName = IRemoteAccess.DESCRIPTOR + "/" + instances[i];
            IBinder binder = ServiceManagerHelper.waitForDeclaredService(fqName);
            if (binder != null) {
                Slogf.i(TAG, "Connected to remote access HAL instance(%s)", fqName);
                return binder;
            }
        }
        return null;
    }

    private static final class RemoteTaskCallbackImpl extends IRemoteTaskCallback.Stub {
        private final WeakReference<RemoteAccessHalWrapper> mHalWrapper;

        RemoteTaskCallbackImpl(RemoteAccessHalWrapper halWrapper) {
            mHalWrapper = new WeakReference<>(halWrapper);
        }

        @Override
        public void onRemoteTaskRequested(String clientId, byte[] data) {
            if (DEBUG) {
                Slogf.d(TAG, "onRemoteTaskRequested is called: clientId = %s, data size = %d",
                        clientId, data == null ? 0 : data.length);
            }
            RemoteAccessHalWrapper halWrapper = mHalWrapper.get();
            if (halWrapper == null) {
                Slogf.w(TAG, "RemoteAccessHalWrapper is not available: clientId = %s", clientId);
                return;
            }
            halWrapper.onRemoteTaskRequested(clientId, data);
        }

        @Override
        public String getInterfaceHash() {
            return IRemoteTaskCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IRemoteTaskCallback.VERSION;
        }
    }
}
