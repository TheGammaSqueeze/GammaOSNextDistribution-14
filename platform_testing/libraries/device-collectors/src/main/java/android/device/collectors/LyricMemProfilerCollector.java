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

import android.os.Bundle;
import android.device.collectors.annotations.OptionClass;

import com.android.helpers.LyricMemProfilerHelper;

@OptionClass(alias = "lyric-mem-profiler-collector")
public class LyricMemProfilerCollector extends BaseCollectionListener<Integer> {

    private static final String TAG = LyricMemProfilerCollector.class.getSimpleName();
    private static final String PROFILE_PERIOD_KEY = "profile-period-ms-enable";

    // separate PID name by ',' character
    private static final String PROFILE_PID_NAME_LIST = "profile-camera-pid-name-list";
    private static final String PROFILE_METRIC_NAME_LIST = "profile-camera-metric-name-list";

    private static final String[] DEFAULT_PID_NAME_LIST = {
        "provider@", "cameraserver", "id.GoogleCamera"
    };
    private static final String[] DEFAULT_METRIC_NAME_LIST = {
        "CameraProvider", "CameraServer", "CameraApp"
    };
    private LyricMemProfilerHelper mLyricMemProfilerHelper = new LyricMemProfilerHelper();

    public LyricMemProfilerCollector() {
        createHelperInstance(mLyricMemProfilerHelper);
    }

    /** Adds the options for total pss collector. */
    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        String argString = args.getString(PROFILE_PERIOD_KEY);
        String[] pidNameList = DEFAULT_PID_NAME_LIST;
        String[] metricNameList = DEFAULT_METRIC_NAME_LIST;
        if (argString != null) {
            mLyricMemProfilerHelper.setProfilePeriodMs(Integer.parseInt(argString));
        }
        argString = args.getString(PROFILE_PID_NAME_LIST);
        if (argString != null) {
            pidNameList = argString.split(",");
        }
        argString = args.getString(PROFILE_METRIC_NAME_LIST);
        if (argString != null) {
            metricNameList = argString.split(",");
        }
        mLyricMemProfilerHelper.setProfileCameraProcName(pidNameList);
        mLyricMemProfilerHelper.setProfileMetricName(metricNameList);
    }
}
