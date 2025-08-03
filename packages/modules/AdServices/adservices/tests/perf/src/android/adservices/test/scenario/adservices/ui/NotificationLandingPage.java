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

package android.adservices.test.scenario.adservices.ui;

import android.content.Context;
import android.os.Trace;
import android.platform.test.scenario.annotation.Scenario;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@Scenario
@RunWith(JUnit4.class)
public class NotificationLandingPage {
    private static final String TAG = "UiTestLabel";
    private static final String UI_NOTIFICATION_LATENCY_METRIC = "UI_NOTIFICATION_LATENCY_METRIC";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static UiDevice sDevice;

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setDebugUxFlagsForRvcUx()
                    .setFlag(FlagsConstants.KEY_ENABLE_ADEXT_DATA_SERVICE_DEBUG_PROXY, "false");

    @Before
    public void setup() throws Exception {
        UiUtils.setFlipFlow(true);
        UiUtils.setAsEuDevice();
        UiUtils.enableGa();
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void teardown() {
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testNotificationLandingPage() throws Exception {
        UiConstants.UX ux = UiConstants.UX.GA_UX;
        if( SdkLevel.isAtLeastR() && !SdkLevel.isAtLeastS() ) {
            ux = UiConstants.UX.RVC_UX;
        }

        Trace.beginSection("NotificationTriggerEvent");
        AdservicesWorkflows.testNotificationActivityFlow(
                sContext, sDevice, true, ux, true, false, true);
        Trace.endSection();
    }

}
