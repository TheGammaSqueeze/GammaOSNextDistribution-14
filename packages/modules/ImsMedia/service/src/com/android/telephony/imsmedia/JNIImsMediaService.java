/**
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

package com.android.telephony.imsmedia;

import android.content.res.AssetManager;
import android.os.Parcel;
import android.os.Process;
import android.util.ArrayMap;
import android.view.Surface;

import androidx.annotation.VisibleForTesting;

import com.android.telephony.imsmedia.util.Log;

/** JNI interface class to send message to libimsmediajni */
public class JNIImsMediaService {
    private static final String TAG = "JNIImsMediaService";
    private static final int THREAD_PRIORITY_REALTIME = -20;
    public static JNIImsMediaService sService = null;
    private final Object mLock = new Object();

    /** for media service based on type ex. audio, video, rtt */
    private static ArrayMap<Integer, JNIImsMediaListener> sListeners =
            new ArrayMap<Integer, JNIImsMediaListener>();

    /**
     * Gets instance object of BaseManager with the corresponding media type
     *
     * @param mediatype Audio/Video/Text type
     * @return the native instance of BaseManager
     */
    public static native long getInterface(int mediatype);

    /**
     * Send message to libimsmediajni to libimsmedia library to operate with corresponding
     * arguments
     *
     * @param nativeObject An unique object identifier of BaseManager to operate
     * @param sessionId An unique session identifier
     * @param baData A parameter to operate session
     */
    public static native void sendMessage(long nativeObject, int sessionId, byte[] baData);

    /**
     * Set preview surface to libimsmediajni and it delivers libimsmedia
     *
     * @param nativeObject An unique object identifier of BaseManager to operate
     * @param sessionId An unique session identifier
     * @param surface A preview surface
     */
    public static native void setPreviewSurface(long nativeObject, int sessionId, Surface surface);

    /**
     * Set display surface to libimsmediajni and it delivers libimsmedia
     *
     * @param nativeObject An unique object identifier of BaseManager to operate
     * @param sessionId An unique session identifier
     * @param surface A display surface
     */
    public static native void setDisplaySurface(long nativeObject, int sessionId, Surface surface);

    /**
     * Generates SPROP list for the given set of video configurations.
     *
     * @param videoConfig video configuration for which sprop should be generated.
     * @return returns the generated sprop value.
     */
    public static native String generateSprop(byte[] videoConfig);

    /**
     * Passes the application's asset manager reference to native which will be used to access
     * pause images assets during video call multitasking scenarios.
     *
     * @param assetManager Application's asset manager reference.
     */
    public static native void setAssetManager(AssetManager assetManager);

    /**
     * Set the libimsmedia library logging level and mode for debug
     *
     * @param logMode The log mode
     * @param debugLogMode The debug log mode
     */
    public static native void setLogMode(int logMode, int debugLogMode);

    /**
     * Gets intance of JNIImsMediaService for jni interface
     *
     * @return instance of JNIImsMediaService
     */
    public static JNIImsMediaService getInstance() {
        if (sService == null) {
            sService = new JNIImsMediaService();
        }
        return sService;
    }

    /**
     * Sets listener to get callback from libimsmediajni
     *
     * @param sessionId An unique object identifier the session to use as a key to acquire a paired
     * listener
     * @param listener A listener to set for getting messages
     */
    public static void setListener(final int sessionId, final JNIImsMediaListener listener) {
        Log.d(TAG, "setListener() - sessionId=" + sessionId);
        if (listener == null) {
            Log.e(TAG, "setListener() - null listener");
            return;
        }
        synchronized (sListeners) {
            sListeners.put(sessionId, listener);
        }
    }

    /**
     * Gets a listener with the key to match
     *
     * @param sessionId An unique key identifier to get the paired listener
     * @return A JNIImsMediaListener listener
     */
    public static JNIImsMediaListener getListener(final int sessionId) {
        JNIImsMediaListener listener = null;
        synchronized (sListeners) {
            listener = sListeners.get(sessionId);
        }

        return listener;
    }

    /**
     *  Clears listener container
     */
    public static void clearListener() {
        synchronized (sListeners) {
            sListeners.clear();
        }
    }

    @VisibleForTesting
    public static int getListenerSize() {
        return sListeners.size();
    }

    /**
     * Sends callback parcel message from libimsmediajni to java
     *
     * @param sessionId An unique key idenfier to find corresponding listener object to send message
     * @param baData byte array form of data to send
     * @return 1 if it is success to send data, -1 when it fails
     */
    public static int sendData2Java(final int sessionId, final byte[] baData) {
        Log.dc(TAG, "sendData2Java() - sessionId=" + sessionId);
        JNIImsMediaListener listener = getListener(sessionId);
        if (listener == null) {
            Log.e(TAG, "No listener :: sessionId=" + sessionId);
            return -1;
        }
        if (baData == null) {
            return -1;
        }
        // retrieve parcel object from pool
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(baData, 0, baData.length);
        parcel.setDataPosition(0);
        listener.onMessage(parcel);
        parcel.recycle();

        return 1;
    }

    /** Elevates the priority of audio thread to THREAD_PRIORITY_REALTIME. Increase in thead
     * priority will ensure AOC and JitterBuffer calls are made consistent every 20msecs.
     *
     * @param threadId is the id of the thread whose priority should to be increased.
     */
    public static void setAudioThreadPriority(int threadId) {
        Log.d(TAG, "setAudioThreadPriority. tid:" + threadId);
        Process.setThreadPriority(threadId, THREAD_PRIORITY_REALTIME);
    }

    /** local shared libimsmediajni library */
    static {
        try {
            Log.d(TAG, "libimsmedia :: loading");
            System.loadLibrary("imsmedia");
            Log.d(TAG, "libimsmedia :: load completed");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Loading fail : libimsmedia.so");
        }
    }
}
