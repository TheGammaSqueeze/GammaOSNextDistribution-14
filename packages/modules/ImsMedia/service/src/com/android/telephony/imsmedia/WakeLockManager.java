/**
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
package com.android.telephony.imsmedia;

import android.content.Context;
import android.os.PowerManager;
import android.telephony.imsmedia.RtpConfig;

import androidx.annotation.VisibleForTesting;

import com.android.telephony.imsmedia.util.Log;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;

/**
 * WakeLockManager makes sure that the process is not suspended when the device switches to
 * doze mode by acquiring wake lock based on media direction of audio, video and text sessions.
 */
public class WakeLockManager {
    private static final String TAG = "WakeLockManager";
    private static final String WAKELOCK_TAG = "imsmedia.insession_lock";
    private static WakeLockManager sWakeLockManager;

    @VisibleForTesting
    protected PowerManager.WakeLock mWakeLock;
    // Sessions for which lock is acquired.
    private HashSet<Integer> mWakeLockAcquiredSessions = new HashSet<>();

    private WakeLockManager() {
        Context context = ImsMediaApplication.getAppContext();

        if (context != null) {
            PowerManager powerManager = context.getSystemService(PowerManager.class);
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

            // Wake locks are reference counted by default. Still we want to make sure it's true.
            mWakeLock.setReferenceCounted(true);
            Log.d(TAG, "WakeLockManager - initialized. Wakelock:" + mWakeLock.toString());
        } else {
            Log.e(TAG, "WakeLockManager not initialized. Context is null");
        }
    }

    /**
     * Get the instance of WakeLockManager
     * @return instance of WakeLockManager
     */
    public static WakeLockManager getInstance() {
        if (sWakeLockManager == null) {
            sWakeLockManager = new WakeLockManager();
        }

        return sWakeLockManager;
    }

    /**
     * Acquires wake lock by incrementing the reference counter of WakeLock.
     * WakeLockManager should be initialized before calling.
     */
    private void acquireLock() {
        if (mWakeLock != null) {
            mWakeLock.acquire();
            Log.d(TAG, "acquireLock. " + mWakeLock.toString());
            return;
        }
        Log.e(TAG, "acquireLock - WakeLockManager not initialized");
    }

    /**
     * Releases wake lock by decrementing the reference counter of WakeLock.
     * WakeLockManager should be initialized before calling.
     */
    private void releaseLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            Log.d(TAG, "releaseLock. " + mWakeLock.toString());
            return;
        }

        if (mWakeLock == null) {
            Log.e(TAG, "releaseLock - WakeLockManager not initialized");
        }
    }

    /**
     * Releases all wake locks by decrementing the reference counter to zero.
     * WakeLockManager should be initialized before calling.
     */
    public synchronized void cleanup() {
        Log.d(TAG, "cleanup");
        if (mWakeLockAcquiredSessions.size() > 0) {
            Iterator<Integer> iterator = mWakeLockAcquiredSessions.iterator();
            while (iterator.hasNext()) {
                Integer sessionId = iterator.next();
                Log.e(TAG, "LEAKED session-id: " + sessionId);
            }

            mWakeLockAcquiredSessions.clear();
        }

        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.e(TAG, "LEAKED wakelock: " + mWakeLock);

            // Release leaked wakelock
            while (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    /**
     * Helper method used by all session classes (audio, video and text) to acquire or release
     * wake lock based on session media direction.
     *
     * @param sessionId Id of the session
     * @param mediaDirection current media direction of the session.
     */
    public synchronized void manageWakeLockOnMediaDirectionUpdate(
            int sessionId, final @RtpConfig.MediaDirection int mediaDirection) {
        try {
            boolean wakeLockAcquired = mWakeLockAcquiredSessions.contains(sessionId);
            Log.dc(TAG, "manageWakeLockOnMediaDirectionUpdate - SessionId:" + sessionId
                    + ", mediaDirection:" + mediaDirection);

            if (wakeLockAcquired && mediaDirection == RtpConfig.MEDIA_DIRECTION_NO_FLOW) {
                releaseLock();
                mWakeLockAcquiredSessions.remove(sessionId);

                if (mWakeLock.isHeld()) {
                    Log.d(TAG, "Wakelock still held for other sessions. SessionCount:"
                            + mWakeLockAcquiredSessions.size()
                            + ", Wakelock:" + mWakeLock.toString());

                    if (mWakeLockAcquiredSessions.size() <= 0) {
                        cleanup();
                    }
                }
            } else if (!wakeLockAcquired && mediaDirection != RtpConfig.MEDIA_DIRECTION_NO_FLOW) {
                acquireLock();
                mWakeLockAcquiredSessions.add(sessionId);
            } /* else {
                if (wakeLockAcquired && mediaDirection != RtpConfig.MEDIA_DIRECTION_NO_FLOW) {
                    * No operation as Lock is already acquired.
                    * Lock is acquired only once per session.
                }
                if (!wakeLockAcquired && mediaDirection == RtpConfig.MEDIA_DIRECTION_NO_FLOW) {
                    * No need to acquire lock as media is not flowing.
                    * Example: During VoLTE calls, audio stream is processed in Modem and
                    AP can goto sleep.
                    * Session are configured and ready in this state to handle handover from VoLTE
                    to WiFi.
                }
            }*/
        } catch (Exception e) {
            Log.e(TAG, "Exception in manageWakeLockOnMediaDirectionUpdate:" + e.getMessage());
            e.printStackTrace();
        }
    }

    void dump(PrintWriter writer) {
        writer.println("\n--- WakeLockManager ---\n");
        writer.println(mWakeLock.toString());
        writer.println("Sessions holding wakelock:\n\tCount: " + mWakeLockAcquiredSessions.size());
        Iterator<Integer> iterator = mWakeLockAcquiredSessions.iterator();
        while (iterator.hasNext()) {
            writer.println("\tSession Id:" + iterator.next());
        }
        writer.println("\n--- END : WakeLockManager ---\n");
    }
}
