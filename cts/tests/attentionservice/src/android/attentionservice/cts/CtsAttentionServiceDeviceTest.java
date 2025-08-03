/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */
package android.attentionservice.cts;

import static android.service.attention.AttentionService.PROXIMITY_UNKNOWN;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.res.Resources;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.attention.AttentionService;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * This suite of test ensures that AttentionManagerService behaves correctly when properly bound
 * to an AttentionService implementation
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize CtsTestAttentionService in instantMode.")
public class CtsAttentionServiceDeviceTest {
    private static final String SERVICE_ENABLED = "service_enabled";
    private static final String FAKE_SERVICE_PACKAGE =
            CtsTestAttentionService.class.getPackage().getName();
    private static final double SUCCESSFUL_PROXIMITY_DISTANCE = 1.0;
    private final boolean isTestable =
            !TextUtils.isEmpty(getAttentionServiceComponent());

    private final Resources mResources = getInstrumentation().getTargetContext()
            .getResources();
    private boolean mIsProximitySupported;

    @Rule
    public final DeviceConfigStateChangerRule mLookAllTheseRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    DeviceConfig.NAMESPACE_ATTENTION_MANAGER_SERVICE,
                    SERVICE_ENABLED,
                    "true");

    @Before
    public void setUp() {
        assumeTrue("Feature not available on this device. Skipping test.", isTestable);
        try {
            mIsProximitySupported = mResources.getBoolean(
                    mResources.getIdentifier("config_enableProximityService", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // this would mean resource is not found in device. skip test
        }
        clearTestableAttentionService();
        CtsTestAttentionService.reset();
        bindToTestService();
    }

    @After
    public void tearDown() {
        clearTestableAttentionService();
    }

    @Test
    public void testProximityUpdates_OnSuccess() {
        assumeTrue("Proximity Feature not available on this device. Skipping test.",
                mIsProximitySupported);

        assertThat(CtsTestAttentionService.hasCurrentProximityUpdates()).isFalse();

        /** From manager, call onStartProximityUpdates() on test service */
        callStartProximityUpdates();

        /** From test service, verify that onStartProximityUpdate was called */
        assertThat(CtsTestAttentionService.hasCurrentProximityUpdates()).isTrue();

        /** From test service, respond with a range */
        CtsTestAttentionService.respondProximity(
                SUCCESSFUL_PROXIMITY_DISTANCE);

        /** From manager, verify proximity update callback was received*/
        assertThat(getLastTestProximityUpdateCallbackCode())
                .isEqualTo(SUCCESSFUL_PROXIMITY_DISTANCE);
    }

    @Test
    public void testProximityUpdates_OnCancelledFromManager() {
        assumeTrue("Proximity Feature not available on this device. Skipping test.",
                mIsProximitySupported);

        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();

        /** From manager, call onStartProximityUpdates() on test service */
        callStartProximityUpdates();

        /** From test service, verify that onStartProximityUpdate was called */
        assertThat(CtsTestAttentionService.hasCurrentProximityUpdates()).isTrue();

        /** From manager, cancel the update */
        callStopProximityUpdates();

        /** From test service, verify that the update was cancelled */
        assertThat(CtsTestAttentionService.hasCurrentProximityUpdates()).isFalse();

        /** From manager, verify that the proximity callback was called with
         * PRXIMITY_UNKNOWN */
        assertThat(getLastTestProximityUpdateCallbackCode()).isEqualTo(
                PROXIMITY_UNKNOWN);
    }

    @Test
    public void testAttentionService_OnSuccess() {
        /** From manager, call CheckAttention() on test service */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();
        callCheckAttention();

        /** From test service, verify that onCheckAttention was called */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isTrue();

        /** From test service, respond with onSuccess */
        CtsTestAttentionService.respondSuccess(
                AttentionService.ATTENTION_SUCCESS_PRESENT);

        /** From manager, verify onSuccess callback was received*/
        assertThat(getLastTestCallbackCode()).isEqualTo(AttentionService.ATTENTION_SUCCESS_PRESENT);
    }

    @Test
    public void testAttentionService_OnCancelledFromManager() {
        /** From manager, call CheckAttention() on test service */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();
        callCheckAttention();

        /** From test service, verify that onCheckAttention was called */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isTrue();

        /** From manager, cancel the check */
        callCancelAttention();

        /** From test service, verify that the check was cancelled */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();

        /** From manager, verify that the onFailure callback was called with
         * ATTENTION_FAILURE_CANCELLED */
        assertThat(getLastTestCallbackCode()).isEqualTo(
                AttentionService.ATTENTION_FAILURE_CANCELLED);
    }

    @Test
    public void testAttentionService_OnCancelledFromService() {
        /** From manager, call CheckAttention() on test service */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();
        callCheckAttention();

        /** From test service, verify that onCheckAttention was called */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isTrue();

        /** From test service, cancel the check and respond with ATTENTION_FAILURE_CANCELLED */
        CtsTestAttentionService.respondFailure(AttentionService.ATTENTION_FAILURE_CANCELLED);

        /** From test service, verify that the check was cancelled */
        assertThat(CtsTestAttentionService.hasPendingChecks()).isFalse();

        /** From manager, verify that the onFailure callback was called with
         * ATTENTION_FAILURE_CANCELLED */
        assertThat(getLastTestCallbackCode()).isEqualTo(
                AttentionService.ATTENTION_FAILURE_CANCELLED);
    }

    private void bindToTestService() {
        /** On Manager, bind to test service */
        assertThat(getAttentionServiceComponent()).isNotEqualTo(FAKE_SERVICE_PACKAGE);
        assertThat(setTestableAttentionService(FAKE_SERVICE_PACKAGE)).isTrue();
        assertThat(getAttentionServiceComponent()).contains(FAKE_SERVICE_PACKAGE);
    }

    private String getAttentionServiceComponent() {
        return runShellCommand("cmd attention getAttentionServiceComponent");
    }

    private int getLastTestCallbackCode() {
        return Integer.parseInt(runShellCommand("cmd attention getLastTestCallbackCode"));
    }

    private double getLastTestProximityUpdateCallbackCode() {
        return Double.parseDouble(
                runShellCommand("cmd attention getLastTestProximityUpdateCallbackCode"));
    }

    /**
     * This call is asynchronous (manager spawns + binds to service and then asynchronously makes a
     * check attention call).
     * As such, we need to ensure consistent testing results, by waiting until we receive a response
     * in our test service w/ CountDownLatch(s).
     */
    private void callCheckAttention() {
        wakeUpScreen();
        Boolean isSuccess = runShellCommand("cmd attention call checkAttention")
                .equals("true");
        assertThat(isSuccess).isTrue();
        CtsTestAttentionService.onReceivedResponse();
    }

    private void callCancelAttention() {
        runShellCommand("cmd attention call cancelCheckAttention");
        CtsTestAttentionService.onReceivedResponse();
    }

    private void callStartProximityUpdates() {
        wakeUpScreen();
        Boolean isSuccess = runShellCommand("cmd attention call onStartProximityUpdates")
                .equals("true");
        assertThat(isSuccess).isTrue();
        CtsTestAttentionService.onReceivedResponse();
    }

    private void callStopProximityUpdates() {
        runShellCommand("cmd attention call onStopProximityUpdates");
        CtsTestAttentionService.onReceivedResponse();
    }

    private boolean setTestableAttentionService(String service) {
        return runShellCommand("cmd attention setTestableAttentionService " + service)
                .equals("true");
    }

    private void clearTestableAttentionService() {
        runShellCommand("cmd attention clearTestableAttentionService");
    }

    private void wakeUpScreen() {
        runShellCommand("input keyevent KEYCODE_WAKEUP");
    }
}
