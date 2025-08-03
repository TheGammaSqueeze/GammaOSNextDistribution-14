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

package android.jobscheduler.cts;

import static android.jobscheduler.cts.JobThrottlingTest.setScreenState;
import static android.jobscheduler.cts.JobThrottlingTest.setTestPackageStandbyBucket;
import static android.jobscheduler.cts.TestAppInterface.TEST_APP_PACKAGE;

import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.job.JobInfo;
import android.content.Context;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AppOpsUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ExpeditedJobTest {
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2_000;
    private static final String APP_OP_GET_USAGE_STATS = "android:get_usage_stats";

    private Context mContext;
    private UiDevice mUiDevice;
    private int mTestJobId;
    private NetworkingHelper mNetworkingHelper;
    private TestAppInterface mTestAppInterface;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestJobId = (int) (SystemClock.uptimeMillis() / 1000);
        mTestAppInterface = new TestAppInterface(mContext, mTestJobId);
        setTestPackageStandbyBucket(mUiDevice, JobThrottlingTest.Bucket.ACTIVE);
        AppOpsUtils.setOpMode(TEST_APP_PACKAGE, APP_OP_GET_USAGE_STATS,
                AppOpsManager.MODE_ALLOWED);
        mNetworkingHelper = new NetworkingHelper(
                InstrumentationRegistry.getInstrumentation(), mContext);
    }

    @After
    public void tearDown() throws Exception {
        mTestAppInterface.cleanup();
        AppOpsUtils.reset(TEST_APP_PACKAGE);
        mNetworkingHelper.tearDown();
    }

    @Test
    public void testJobUidState_noRequiredNetwork() throws Exception {
        // Turn screen off so any lingering activity close processing from previous tests
        // don't affect this one.
        setScreenState(mUiDevice, false);
        mTestAppInterface.scheduleJob(Map.of(
                TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true
        ), Collections.emptyMap());
        mTestAppInterface.forceRunJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));
        mTestAppInterface.assertJobUidState(ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND,
                0,
                227 /* ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ + 2 */);
    }

    @Test
    public void testJobUidState_withRequiredNetwork() throws Exception {
        // Turn screen off so any lingering activity close processing from previous tests
        // don't affect this one.
        setScreenState(mUiDevice, false);
        mNetworkingHelper.setAllNetworksEnabled(true);
        mTestAppInterface.scheduleJob(
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true,
                        TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE, true
                ),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE,
                        JobInfo.NETWORK_TYPE_ANY
                )
        );
        mTestAppInterface.forceRunJob();
        assertTrue("Job did not start after scheduling",
                mTestAppInterface.awaitJobStart(DEFAULT_WAIT_TIMEOUT_MS));
        mTestAppInterface.assertJobUidState(ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND,
                ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK,
                227 /* ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ + 2 */);
    }

    /** Test that EJs for the TOP app start immediately and there is no limit on the number. */
    @Test
    @LargeTest
    public void testTopEJUnlimited() throws Exception {
        final int standardConcurrency = 64;
        final int numEjs = standardConcurrency + 1;
        setScreenState(mUiDevice, true);
        mTestAppInterface.startAndKeepTestActivity(true);
        for (int i = 0; i < numEjs; ++i) {
            mTestAppInterface.scheduleJob(
                    Map.of(TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, true),
                    Map.of(TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, i));
        }
        for (int i = 0; i < numEjs; ++i) {
            assertTrue("Job did not start after scheduling",
                    mTestAppInterface.awaitJobStart(i, DEFAULT_WAIT_TIMEOUT_MS));
        }
    }
}
