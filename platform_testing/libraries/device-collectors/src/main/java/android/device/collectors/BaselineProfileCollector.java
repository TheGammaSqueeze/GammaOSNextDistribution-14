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
package android.device.collectors;

import static junit.framework.Assert.assertTrue;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FilenameFilter;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Collects baseline profiles generated from the android.platform.test.rule.BaselineProfileRule.
 *
 * <p>Both a {@link org.junit.Rule} and {@link org.junit.runner.notification.RunListener} (this) are
 * required. {@code BaselineProfileRule}, which holds the profile generation logic, uses JUnit's
 * rule interface for application, which the listener interface doesn't support. It's registered as
 * a {@link org.junit.ClassRule}; however, class-level rules are outside of the metric collection
 * scope, hence the need for this collector.
 */
@OptionClass(alias = "baseline-profile-collector")
public class BaselineProfileCollector extends BaseMetricListener {
    private static final String LOG_TAG = BaselineProfileCollector.class.getSimpleName();

    // This option is sourced from the androidx.benchmark library. Passing it will set it in both
    // the androidx.benchmark library and here so that this collector can pull the Baseline Profile
    // from the same place it's stored. A longer-term fix would pull the output location from the
    // underlying androidx.benchmark.macro.junit4.BaselineProfileRule; however that's not possible
    // with today's API.
    @VisibleForTesting static final String ADDITIONAL_TEST_OUTPUT_DIR = "additionalTestOutputDir";
    // This is also directly pulled from the androidx.benchmark naming conventions. It's fragile.
    @VisibleForTesting static final String BASELINE_PROFILE_SUFFIX = "baseline-prof.txt";

    private String mAdditionalTestOutputDir;

    public BaselineProfileCollector() {
        super();
    }

    /** Constructor only used for testing. */
    @VisibleForTesting
    BaselineProfileCollector(Bundle args) {
        super(args);
    }

    @Override
    public void onTestFail(DataRecord testData, Description description, Failure failure) {
        // Consider reporting failure metrics from this listener in the future for evaluation.
        Log.w(LOG_TAG, "Test failures may cause degredation in baseline profile performance!");
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        File outputDirectory = new File(mAdditionalTestOutputDir);
        File[] baselineProfiles =
                outputDirectory.listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File directory, String name) {
                                return name.endsWith(BASELINE_PROFILE_SUFFIX);
                            }
                        });

        if (baselineProfiles.length == 1) {
            runData.addStringMetric("baseline-profile", baselineProfiles[0].getAbsolutePath());
        } else {
            int index = 1;
            for (File baselineProfile : baselineProfiles) {
                runData.addStringMetric(
                        String.format("baseline-profile%d", index),
                        baselineProfile.getAbsolutePath());
                index++;
            }
        }
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();

        assertTrue(
                "Specify the additionalTestOutput option to use this. See comments for more"
                        + " details.",
                args.containsKey(ADDITIONAL_TEST_OUTPUT_DIR));
        mAdditionalTestOutputDir = args.getString(ADDITIONAL_TEST_OUTPUT_DIR);
    }
}
