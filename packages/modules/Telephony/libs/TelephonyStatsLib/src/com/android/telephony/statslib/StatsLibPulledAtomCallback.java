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

import android.app.StatsManager;
import android.content.Context;
import android.util.Log;
import android.util.StatsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * StatsLibPulledAtomCallback class
 *
 * <p>This class registers to statsd. Called once a day for this class pull stat to send to statsd.
 */
class StatsLibPulledAtomCallback implements StatsManager.StatsPullAtomCallback {

    private static final String LOG_TAG = StatsLibPulledAtomCallback.class.getSimpleName();
    private static final boolean DBG = true;
    private static final StatsManager.PullAtomMetadata POLICY_PULL_DAILY =
            new StatsManager.PullAtomMetadata.Builder()
                    .setCoolDownMillis(5L)
                    .setTimeoutMillis(2L)
                    .build();
    private final StatsManager mStatsManager;
    private final StatsLibStorage mStatsLibStorage;
    private final HashMap<Integer, PulledCallback> mRegisteredCallback;

    /**
     * Constructor of StatsLibPulledAtomCallback
     *
     * @param context Context
     */
    StatsLibPulledAtomCallback(Context context) {
        mRegisteredCallback = new HashMap<>();
        mStatsLibStorage = new StatsLibStorage(context);
        mStatsManager = context.getSystemService(StatsManager.class);
        log("created StatsLibPulledAtomCallback.");
    }

    /** get a StatsLibStorage, which stores pulled atoms */
    StatsLibStorage getStatsLibStorage() {
        return mStatsLibStorage;
    }

    private void log(String s) {
        if (DBG) Log.d(LOG_TAG, s);
    }

    /**
     * Register a callback
     *
     * @param atomTag The tag of the atom for this puller callback.
     */
    void registerAtom(int atomTag) {
        registerAtom(atomTag, new EmptyCallback());
    }

    /**
     * Register a callback
     *
     * @param atomTag The tag of the atom for this puller callback.
     */
    void registerAtom(int atomTag, PulledCallback callback) {
        if (!mRegisteredCallback.containsKey(atomTag)) {
            mStatsLibStorage.init(atomTag);
            mStatsLibStorage.loadFromFile(atomTag);
            mStatsManager.setPullAtomCallback(
                    atomTag, POLICY_PULL_DAILY, new MetricExecutor(), this);
            mRegisteredCallback.put(atomTag, callback);
        }
    }

    /**
     * is registered a callback
     *
     * @param atomTag The tag of the atom for this puller callback.
     */
    boolean isRegisteredAtom(int atomTag) {
        return mRegisteredCallback.containsKey(atomTag);
    }

    /**
     * Register a callback
     *
     * @param atomTag The tag of the atom for this puller callback.
     */
    void unregisterAtom(int atomTag) {
        if (mRegisteredCallback.containsKey(atomTag)) {
            mStatsManager.clearPullAtomCallback(atomTag);
            mRegisteredCallback.remove(atomTag);
            mStatsLibStorage.saveToFile(atomTag);
        }
    }

    @Override
    public int onPullAtom(int atomTag, List<StatsEvent> data) {
        log("onPullAtom: atomTag:" + atomTag);
        AtomsPulled[] arrayPulled = getStatsLibStorage().popPulledAtoms(atomTag);
        for (AtomsPulled pulled : arrayPulled) {
            final StatsEvent.Builder builder = StatsEvent.newBuilder();
            builder.setAtomId(pulled.getStatsId());
            pulled.build(builder);
            data.add(builder.build());
        }

        PulledCallback callback = mRegisteredCallback.get(atomTag);
        if (callback == null) {
            return StatsManager.PULL_SUCCESS;
        }
        List<AtomsPulled> list = new ArrayList<>();
        callback.onPulledCallback(atomTag, list);
        for (AtomsPulled pulled : list) {
            final StatsEvent.Builder builder = StatsEvent.newBuilder();
            builder.setAtomId(pulled.getStatsId());
            pulled.build(builder);
            data.add(builder.build());
        }

        return StatsManager.PULL_SUCCESS;
    }

    private static class MetricExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public String toString() {
            return "METRIC_EXECUTOR";
        }
    }

    static class EmptyCallback implements PulledCallback {
        public void onPulledCallback(int atomTag, List<AtomsPulled> data) {}
    }
}
