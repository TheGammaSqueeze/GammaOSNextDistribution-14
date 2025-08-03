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
package android.rotationresolverservice.cts;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.platform.test.annotations.AppModeFull;
import android.service.rotationresolver.RotationResolutionRequest;
import android.service.rotationresolver.RotationResolverService;
import android.text.TextUtils;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * This suite of test ensures that RotationResolverManagerService behaves correctly when properly
 * bound to an RotationResolverService implementation
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize CtsTestRotationResolverService in instantMode.")
public class CtsRotationResolverServiceDeviceTest {

    private static final String NAMESPACE_ROTATION_RESOLVER = "rotation_resolver";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String FAKE_SERVICE_PACKAGE =
            CtsTestRotationResolverService.class.getPackage().getName();
    private static final String FAKE_PACKAGE_NAME = "package_name";
    private static final boolean FAKE_SHOULD_USE_CAMERA = true;
    private static final long FAKE_TIME_OUT = 1000L;
    private static final long TEMPORARY_SERVICE_DURATION = 5000L;
    private static final String USER_ID = "0";

    private final boolean isTestable =
            !TextUtils.isEmpty(getRotationResolverServiceComponent());
    private Context mContext;

    @Rule
    public final DeviceConfigStateChangerRule mLookAllTheseRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    NAMESPACE_ROTATION_RESOLVER,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Before
    public void setUp() {
        assumeTrue(VERSION.SDK_INT >= VERSION_CODES.S);
        assumeTrue("Feature not available on this device. Skipping test.", isTestable);
        mContext = ApplicationProvider.getApplicationContext();
        clearTestableRotationResolverService();
        CtsTestRotationResolverService.reset();
        bindToTestService();
    }

    @After
    public void tearDown() {
        clearTestableRotationResolverService();
    }

    @Test
    public void testRotationResolverService_OnSuccess() {
        /** From manager, call ResolveRotation() on test service */
        assertThat(CtsTestRotationResolverService.hasPendingChecks()).isFalse();
        callResolveRotation();

        /** From test service, verify that onResolveRotation was called */
        assertThat(CtsTestRotationResolverService.hasPendingChecks()).isTrue();

        /** From test service, respond with onSuccess */
        CtsTestRotationResolverService.respondSuccess(ROTATION_0);

        /** From manager, verify onSuccess callback was received*/
        assertThat(getLastTestCallbackCode()).isEqualTo(ROTATION_0);
    }

    @Test
    public void testRotationResolverService_OnCancelledFromService() {
        /** From manager, call ResolveRotation() on test service */
        assertThat(CtsTestRotationResolverService.hasPendingChecks()).isFalse();
        callResolveRotation();

        /** From test service, verify that onResolveRotation was called */
        assertThat(CtsTestRotationResolverService.hasPendingChecks()).isTrue();

        /** From test service, cancel the check and respond with
         * ROTATION_RESULT_FAILURE_CANCELLED */
        CtsTestRotationResolverService.respondFailure(
                RotationResolverService.ROTATION_RESULT_FAILURE_CANCELLED);

        /** From test service, verify that the check was cancelled */
        assertThat(CtsTestRotationResolverService.hasPendingChecks()).isFalse();

        /** From manager, verify that the onFailure callback was called with
         * ROTATION_RESULT_FAILURE_CANCELLED */
        assertThat(getLastTestCallbackCode()).isEqualTo(
                RotationResolverService.ROTATION_RESULT_FAILURE_CANCELLED);
    }

    @Test
    public void testRotationResolutionRequest_ConstructorSetsValues() {
        /* Construct a RotationResolutionObject */
        final RotationResolutionRequest request = new RotationResolutionRequest(FAKE_PACKAGE_NAME,
                ROTATION_0, ROTATION_90, FAKE_SHOULD_USE_CAMERA, FAKE_TIME_OUT);

        /* Verify the values are correctly set */
        assertThat(request.getForegroundPackageName()).isEqualTo(FAKE_PACKAGE_NAME);
        assertThat(request.getCurrentRotation()).isEqualTo(ROTATION_0);
        assertThat(request.getProposedRotation()).isEqualTo(ROTATION_90);
        assertThat(request.shouldUseCamera()).isEqualTo(FAKE_SHOULD_USE_CAMERA);
        assertThat(request.getTimeoutMillis()).isEqualTo(FAKE_TIME_OUT);
    }

    private void bindToTestService() {
        /** On Manager, bind to test service */
        assertThat(getRotationResolverServiceComponent()).isNotEqualTo(FAKE_SERVICE_PACKAGE);
        setTestableRotationResolverService(FAKE_SERVICE_PACKAGE);
        assertThat(getRotationResolverServiceComponent()).contains(FAKE_SERVICE_PACKAGE);
    }

    private String getRotationResolverServiceComponent() {
        return runShellCommand("cmd resolver get-bound-package %s", USER_ID);
    }

    private int getLastTestCallbackCode() {
        return Integer.parseInt(runShellCommand("cmd resolver get-last-resolution"));
    }

    /**
     * This call is asynchronous (manager spawns + binds to service and then asynchronously makes a
     * check attention call).
     * As such, we need to ensure consistent testing results, by waiting until we receive a response
     * in our test service w/ CountDownLatch(s).
     */
    private void callResolveRotation() {
        runShellCommand("cmd resolver resolve-rotation %s", USER_ID);
        CtsTestRotationResolverService.onReceivedResponse();
    }

    private void setTestableRotationResolverService(String service) {
        runShellCommand("cmd resolver set-temporary-service %s %s %s",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void clearTestableRotationResolverService() {
        runShellCommand("cmd resolver set-temporary-service %s", USER_ID);
    }

}
