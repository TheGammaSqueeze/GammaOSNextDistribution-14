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

package com.android.car.evs;

import android.hardware.HardwareBuffer;

import com.android.car.internal.evs.EvsHalWrapper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * EvaHalWrapper impl used by updatable car service.
 */
public final class EvsHalWrapperImpl extends EvsHalWrapper {


    /**
     * Because of its dependency on FMQ type, android.hardware.automotive.evs@1.1 interface does
     * not support Java backend.  Therefore, all hwbinder transactions happen in native methods
     * declared below.
     */

    static {
        System.loadLibrary("carservicejni");
    }

    private final EvsHalWrapper.HalEventCallback mCallback;

    private final Object mLock = new Object();

    /** Stores a service handle initialized in native methods */
    @GuardedBy("mLock")
    private long mNativeEvsServiceObj;

    /** Constructor */
    public EvsHalWrapperImpl(EvsHalWrapper.HalEventCallback callback) {
        super();
        mCallback = callback;
    }

    /**
     * Create a {@code EvsHalWrapperImpl} object with a given JNI library that implements native
     * methods.
     */
    @VisibleForTesting
    static EvsHalWrapperImpl create(EvsHalWrapper.HalEventCallback callback,
            String jniLibraryName) {
        System.loadLibrary(jniLibraryName);
        return new EvsHalWrapperImpl(callback);
    }

    @Override
    public boolean init() {
        long handle = nativeCreateServiceHandle();
        if (handle == 0) {
            return false;
        }
        synchronized (mLock) {
            mNativeEvsServiceObj = handle;
        }
        return true;
    }

    @Override
    public void release() {
        long handle;
        synchronized (mLock) {
            handle = mNativeEvsServiceObj;
            mNativeEvsServiceObj = 0;
        }
        if (handle == 0) {
            return;
        }
        nativeDestroyServiceHandle(handle);
    }

    @Override
    public boolean isConnected() {
        return getNativeHandle() != 0;
    }

    @Override
    public boolean connectToHalServiceIfNecessary() {
        if (!isConnected() && !init()) {
            return false;
        }

        return nativeConnectToHalServiceIfNecessary(getNativeHandle());
    }

    @Override
    public void disconnectFromHalService() {
        nativeDisconnectFromHalService(getNativeHandle());
    }

    @Override
    public boolean openCamera(String cameraId) {
        return nativeOpenCamera(getNativeHandle(), cameraId);
    }

    @Override
    public void closeCamera() {
        nativeCloseCamera(getNativeHandle());
    }

    @Override
    public boolean requestToStartVideoStream() {
        return nativeRequestToStartVideoStream(getNativeHandle());
    }

    @Override
    public void requestToStopVideoStream() {
        nativeRequestToStopVideoStream(getNativeHandle());
    }

    @Override
    public void doneWithFrame(int bufferId) {
        nativeDoneWithFrame(getNativeHandle(), bufferId);
    }

    @VisibleForTesting
    boolean setServiceHandle(long handleToUse) {
        if (handleToUse == 0) {
            return false;
        }

        long handleToDestroy;
        synchronized (mLock) {
            handleToDestroy = mNativeEvsServiceObj;
            mNativeEvsServiceObj = handleToUse;
        }

        nativeDestroyServiceHandle(handleToDestroy);
        return true;
    }

    @VisibleForTesting
    long createServiceHandleForTest() {
        return nativeCreateServiceHandleForTest();
    }


    @VisibleForTesting
    void triggerBinderDied() {
        nativeTriggerBinderDied(getNativeHandle());
    }

    private long getNativeHandle() {
        synchronized (mLock) {
            return mNativeEvsServiceObj;
        }
    }

    /** EVS stream event handler called after a native handler */
    private void postNativeEventHandler(int eventType) {
        mCallback.onHalEvent(eventType);
    }

    /** EVS frame handler called after a native handler */
    private void postNativeFrameHandler(int id, HardwareBuffer buffer) {
        mCallback.onFrameEvent(id, buffer);
    }

    /** EVS service death handler called after a native handler */
    private void postNativeDeathHandler() {
        mCallback.onHalDeath();
    }

    /** Attempts to connect to the HAL service if it has not done yet */
    private native boolean nativeConnectToHalServiceIfNecessary(long handle);

    /** Attempts to disconnect from the HAL service */
    private native void nativeDisconnectFromHalService(long handle);

    /** Attempts to open a target camera device */
    private native boolean nativeOpenCamera(long handle, String cameraId);

    /** Requests to close a target camera device */
    private native void nativeCloseCamera(long handle);

    /** Requests to start a video stream */
    private native boolean nativeRequestToStartVideoStream(long handle);

    /** Requests to stop a video stream */
    private native void nativeRequestToStopVideoStream(long handle);

    /** Request to return an used buffer */
    private native void nativeDoneWithFrame(long handle, int bufferId);

    /** Trigger a onBinderDied callback for tests */
    private native void nativeTriggerBinderDied(long handle);

    /** Creates a EVS service handle */
    private static native long nativeCreateServiceHandle();

    /** Creates a EVS service handle for tests */
    private static native long nativeCreateServiceHandleForTest();

    /** Destroys a EVS service handle */
    private static native void nativeDestroyServiceHandle(long handle);
}
