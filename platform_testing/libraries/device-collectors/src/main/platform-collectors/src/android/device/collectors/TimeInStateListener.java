/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.android.helpers.TimeInStateHelper;

/**
 * A {@link TimeInStateListener} captures time_in_state frequency stats in the format of
 * "[frequency] [time]" in each line which means that the total duration in the [frequency] state is
 * equal to [time].
 *
 * <p>Options:
 *
 * <ul>
 *   <li>-e key-source-mapping [mapping] : a comma-separated list of mapping "[key]@[source]" to
 *       provide [key] as a keyword part of the metric key and [source] as the source location
 *       containing the time_in_state data.
 * </ul>
 *
 * <p>Do NOT throw exception anywhere in this class. We don't want to halt the test when metrics
 * collection fails.
 */
@OptionClass(alias = "time-in-state-listener")
public class TimeInStateListener extends BaseCollectionListener<Long> {

    private static final String TAG = TimeInStateListener.class.getSimpleName();
    @VisibleForTesting static final String ARG_SEPARATOR = ",";
    @VisibleForTesting static final String ARG_KEY = "key-source-mapping";

    public TimeInStateListener() {
        createHelperInstance(new TimeInStateHelper());
    }

    @VisibleForTesting
    public TimeInStateListener(Bundle args, TimeInStateHelper helper) {
        super(args, helper);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String keyArgString = args.getString(ARG_KEY);
        if (keyArgString == null) {
            Log.w(TAG, "No time_in_state provided. Nothing will be collected");
            return;
        }
        ((TimeInStateHelper) mHelper).setUp(keyArgString.split(ARG_SEPARATOR));
    }
}
