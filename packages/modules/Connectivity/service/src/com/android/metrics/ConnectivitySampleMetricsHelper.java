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

package com.android.metrics;

import android.annotation.NonNull;
import android.app.StatsManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.StatsEvent;

import com.android.modules.utils.HandlerExecutor;

import java.util.List;
import java.util.function.Supplier;

/**
 * A class to register, sample and send connectivity state metrics.
 */
public class ConnectivitySampleMetricsHelper implements StatsManager.StatsPullAtomCallback {
    private static final String TAG = ConnectivitySampleMetricsHelper.class.getSimpleName();

    final Supplier<StatsEvent> mDelegate;

    /**
     * Start collecting metrics.
     * @param context some context to get services
     * @param connectivityServiceHandler the connectivity service handler
     * @param atomTag the tag to collect metrics from
     * @param delegate a method returning data when called on the handler thread
     */
    // Unfortunately it seems essentially impossible to unit test this method. The only thing
    // to test is that there is a call to setPullAtomCallback, but StatsManager is final and
    // can't be mocked without mockito-extended. Using mockito-extended in FrameworksNetTests
    // would have a very large impact on performance, while splitting the unit test for this
    // class in a separate target would make testing very hard to manage. Therefore, there
    // can unfortunately be no unit tests for this method, but at least it is very simple.
    public static void start(@NonNull final Context context,
            @NonNull final Handler connectivityServiceHandler,
            final int atomTag,
            @NonNull final Supplier<StatsEvent> delegate) {
        final ConnectivitySampleMetricsHelper metrics =
                new ConnectivitySampleMetricsHelper(delegate);
        final StatsManager mgr = context.getSystemService(StatsManager.class);
        if (null == mgr) return; // No metrics for you
        mgr.setPullAtomCallback(atomTag, null /* metadata */,
                new HandlerExecutor(connectivityServiceHandler), metrics);
    }

    public ConnectivitySampleMetricsHelper(@NonNull final Supplier<StatsEvent> delegate) {
        mDelegate = delegate;
    }

    @Override
    public int onPullAtom(final int atomTag, final List<StatsEvent> data) {
        Log.d(TAG, "Sampling data for atom : " + atomTag);
        data.add(mDelegate.get());
        return StatsManager.PULL_SUCCESS;
    }
}
