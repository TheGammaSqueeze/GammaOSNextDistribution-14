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

package com.android.telephony.statslib;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.StatsEvent;
import android.util.StatsLog;

import com.android.internal.annotations.VisibleForTesting;

/** Statslib class */
public class StatsLib {

    private static final String LOG_TAG = StatsLib.class.getSimpleName();
    private static final boolean DBG = true;
    private static final int DEFAULT_FREQUENCY_WRITING_PUSHED_ATOM_IN_MILLS = 50;

    private final StatsLibPulledAtomCallback mStatsLibPulledAtomCallback;
    private final WritePushedAtomHandler mHandler;

    /** Default constructor. */
    public StatsLib(Context context) {
        mStatsLibPulledAtomCallback = new StatsLibPulledAtomCallback(context);
        log("created StatsLib.");
        HandlerThread handlerThread = new HandlerThread("StatsLibStorage");
        handlerThread.start();
        mHandler = new WritePushedAtomHandler(handlerThread.getLooper());
    }

    @VisibleForTesting
    protected StatsLib(StatsLibPulledAtomCallback cb) {
        mStatsLibPulledAtomCallback = cb;
        log("created StatsLib.");
        HandlerThread handlerThread = new HandlerThread("StatsLibStorage");
        handlerThread.start();
        mHandler = new WritePushedAtomHandler(handlerThread.getLooper());
    }

    /**
     * Registers the target atom to be pulled.
     *
     * @param statsId the pulled atom tag to register to take data from.
     */
    public void registerPulledAtomCallback(int statsId) {
        mStatsLibPulledAtomCallback.registerAtom(statsId);
    }

    /**
     * Registers the target atom to be pulled.
     *
     * @param statsId The tag of the atom for this puller callback.
     * @param callback callback to be registered.
     */
    public void registerPulledAtomCallback(int statsId, PulledCallback callback) {
        mStatsLibPulledAtomCallback.registerAtom(statsId, callback);
    }

    /**
     * checks whether stats id was already registered or not.
     *
     * @param statsId The tag of the atom for this puller callback.
     * @return true already registered.
     */
    public boolean isRegisteredPulledAtomCallback(int statsId) {
        return mStatsLibPulledAtomCallback.isRegisteredAtom(statsId);
    }

    /**
     * Unregisters the target atom being pulled.
     *
     * @param statsId The tag of the atom to remove callback and tag
     */
    public void unregisterPulledAtomCallback(int statsId) {
        if (isRegisteredPulledAtomCallback(statsId)) {
            mStatsLibPulledAtomCallback.unregisterAtom(statsId);
        }
    }

    /**
     * Write the pushed atoms
     *
     * @param pushed AtomsPushed
     */
    public void write(AtomsPushed pushed) {
        if (pushed == null) {
            loge("writePushedAtoms: pushed is null");
            return;
        }
        mHandler.sendMessage(Message.obtain(mHandler, 0, pushed));
    }

    protected void onWritePushedAtom(AtomsPushed pushed) {
        final StatsEvent.Builder builder = StatsEvent.newBuilder();
        builder.setAtomId(pushed.getStatsId());
        pushed.build(builder);
        builder.usePooledBuffer();
        StatsLog.write(builder.build());
        log("writePushedAtoms: pushed=" + pushed);

        append(pushed);
    }

    private class WritePushedAtomHandler extends Handler {
        /**
         * Use the provided {@link Looper} instead of the default one.
         *
         * @param looper The looper, must not be null.
         */
        WritePushedAtomHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            try {
                AtomsPushed pushed = (AtomsPushed) message.obj;
                onWritePushedAtom(pushed);
                /* Atom logging frequency should not exceed once per 10 milliseconds (i.e.
                 * consecutive atom calls should be at least 10 milliseconds apart). This ensures
                 * that our logging socket is not spammed so that the socket does not drop data. If
                 * your logging line might trigger frequently, we suggest putting a guardrail to
                 * check that at least 1 second has passed since the last atom push.
                 */
                Thread.sleep(DEFAULT_FREQUENCY_WRITING_PUSHED_ATOM_IN_MILLS);
            } catch (InterruptedException | ClassCastException e) {
                loge("WritePushedAtomHandler, e:" + e);
            }
        }
    }

    /**
     * Append the Pushed atoms
     *
     * @param pushed AtomsPushed
     */
    private void append(AtomsPushed pushed) {
        StatsLibStorage storage = getStorage();
        if (storage == null) {
            loge("appendPushedAtoms: storage is null");
            return;
        }
        storage.appendPushedAtoms(pushed);
        log("appendPushedAtoms: pushed=" + pushed);
    }

    /**
     * Append the Pulled atoms
     *
     * @param pulled AtomsPulled
     */
    public void append(AtomsPulled pulled) {
        if (pulled == null) {
            return;
        }
        StatsLibStorage storage = getStorage();
        if (storage == null) {
            loge("appendPulledAtoms: storage is null");
            return;
        }
        if (!isRegisteredPulledAtomCallback(pulled.getStatsId())) {
            registerPulledAtomCallback(pulled.getStatsId());
        }
        storage.appendPulledAtoms(pulled);
        log("appendPulledAtoms: pulled=" + pulled);
    }

    private StatsLibStorage getStorage() {
        if (mStatsLibPulledAtomCallback == null) {
            return null;
        }
        return mStatsLibPulledAtomCallback.getStatsLibStorage();
    }

    private void log(String s) {
        if (DBG) Log.d(LOG_TAG, s);
    }

    private void loge(String s) {
        Log.e(LOG_TAG, s);
    }
}
