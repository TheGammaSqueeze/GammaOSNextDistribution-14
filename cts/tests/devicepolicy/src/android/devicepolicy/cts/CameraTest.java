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

package android.devicepolicy.cts;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.FEATURE_CAMERA;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CAMERA;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.stats.devicepolicy.EventId;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowCamera;
import com.android.bedstead.harrier.policies.DisallowCameraPermissionBased;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequireFeature(FEATURE_CAMERA)
@RunWith(BedsteadJUnit4.class)
public final class CameraTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final CameraManager sCameraManager =
            sContext.getSystemService(CameraManager.class);
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String CALLED_FROM_PARENT = "calledFromParent";
    private static final String NOT_CALLED_FROM_PARENT = "notCalledFromParent";

    /**
     * see {@code DevicePolicyManager.POLICY_DISABLE_CAMERA}
     */
    private static final String POLICY_DISABLE_CAMERA = "policy_disable_camera";
    /**
     * see {@code DevicePolicyManager.EXTRA_RESTRICTION}
     */
    private static final String EXTRA_RESTRICTION = "android.app.extra.RESTRICTION";

    private static AppOpsManager sAppOpsManager = sContext.getSystemService(AppOpsManager.class);

    private static final String TAG = "CameraUtils";

    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledTrue_policyDoesNotApply_cameraNotDisabled() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), true);

            assertThat(sLocalDevicePolicyManager
                    .getCameraDisabled(null)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledTrue_cameraDisabledLocally() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), true);

            assertThat(sLocalDevicePolicyManager
                    .getCameraDisabled(null)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledFalse_cameraEnabledLocally() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sLocalDevicePolicyManager
                .getCameraDisabled(null)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledTrue_cameraDisabledAtDPC() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getCameraDisabled(null)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledFalse_cameraEnabledAtDPC() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(null)).isFalse();
    }

    @Ignore("b/201753989 Properly define behaviour of setCameraDisabled on secondary user POs")
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledTrue_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true));
    }

    @Ignore("b/201753989 Properly define behaviour of setCameraDisabled on secondary user POs")
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabledFalse_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getCameraDisabled")
    public void getCameraDisabled_forAdmin_setToTrue_returnsTrue() {
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getCameraDisabled(sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getCameraDisabled")
    public void getCameraDisabled_forAdmin_setToFalse_returnsFalse() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(sDeviceState.dpc().componentName())).isFalse();
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void openCamera_cameraEnabled_successful() throws Exception {
        Assume.assumeTrue("Need to be foreground to access camera",
                TestApis.users().instrumented().isForeground());

        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        PollingCheck.waitFor(() ->
                sAppOpsManager.unsafeCheckOp(
                        AppOpsManager.OPSTR_CAMERA,
                        android.os.Process.myUid(),
                        sContext.getOpPackageName()) == 0);

        assertCanOpenCamera();
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @CanSetPolicyTest(policy = DisallowCamera.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void openCamera_cameraDisabled_unsuccessful() throws Exception {
        Assume.assumeTrue("Need to be foreground to access camera",
                TestApis.users().instrumented().isForeground());

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), true);

            PollingCheck.waitFor(() ->
                    sAppOpsManager.unsafeCheckOp(
                            AppOpsManager.OPSTR_CAMERA,
                            android.os.Process.myUid(),
                            sContext.getOpPackageName()) == 1);

            assertCanNotOpenCamera();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setCameraDisabled(sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCameraPermissionBased.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setCameraDisabled")
    public void setCameraDisabled_notDpc_IllegalStateExceptionThrown() {
        assertThrows(IllegalStateException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true));
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_CAMERA"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowCamera.class)
    public void createAdminSupportIntent_disallowCamera_createsIntent() {
        boolean originalCameraDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_CAMERA);

            assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(POLICY_DISABLE_CAMERA);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), originalCameraDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_CAMERA"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisallowCamera.class)
    public void createAdminSupportIntent_allowCamera_doesNotCreate() {
        boolean originalCameraDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(sDeviceState.dpc().componentName());
        try {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_CAMERA);

            assertThat(intent).isNull();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), originalCameraDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setCameraDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    public void setCameraDisabled_true_wasLogged() {
        boolean originalCameraDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = sDeviceState.dpc().isParentInstance();

            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_CAMERA_DISABLED_VALUE)
                            .whereAdminPackageName().isEqualTo(
                                    sDeviceState.dpc().componentName().getPackageName())
                            .whereBoolean().isEqualTo(true)
                            .whereStrings().contains(isParentInstance ?
                                CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), originalCameraDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setCameraDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowCamera.class)
    public void setCameraDisabled_false_wasLogged() {
        boolean originalCameraDisabledStatus = sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(sDeviceState.dpc().componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = sDeviceState.dpc().isParentInstance();

            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);

            assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_CAMERA_DISABLED_VALUE)
                            .whereAdminPackageName().isEqualTo(
                                    sDeviceState.dpc().componentName().getPackageName())
                            .whereBoolean().isEqualTo(false)
                            .whereStrings().contains(isParentInstance ?
                                CALLED_FROM_PARENT : NOT_CALLED_FROM_PARENT))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setCameraDisabled(
                    sDeviceState.dpc().componentName(), originalCameraDisabledStatus
            );
        }
    }

    private void assertCanOpenCamera() throws Exception {
        boolean successfullyOpened;

        String[] cameraIdList = sCameraManager.getCameraIdList();
        if (cameraIdList == null || cameraIdList.length == 0) {
            successfullyOpened = false;
        } else {
            String cameraId = cameraIdList[0];
            // TODO(b/202012931): Use a BlockingCallback once it is possible to use it in
            // conjunction with StateCallback
            CameraTest.CameraCallback callback = new CameraTest.CameraCallback();
            sCameraManager.openCamera(cameraId, callback, new Handler(Looper.getMainLooper()));
            successfullyOpened = callback.waitForResult();
        }

        assertThat(successfullyOpened).isTrue();
    }

    private void assertCanNotOpenCamera() {
        boolean successfullyOpened;

        try {
            String[] cameraIdList = sCameraManager.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                successfullyOpened = false;
            } else {
                String cameraId = cameraIdList[0];
                CameraTest.CameraCallback callback = new CameraTest.CameraCallback();
                sCameraManager.openCamera(cameraId, callback, new Handler(Looper.getMainLooper()));
                successfullyOpened = callback.waitForResult();
            }
        } catch (Exception e) {
            Log.d(TAG,
                    "Exception when opening camera but we expected to not be able to open it",
                    e);
            successfullyOpened = false;
        }

        assertThat(successfullyOpened).isFalse();
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private static class CameraCallback extends CameraDevice.StateCallback {

        private static final int OPEN_TIMEOUT_SECONDS = 5;

        private final CountDownLatch mLatch = new CountDownLatch(1);

        private AtomicBoolean mResult = new AtomicBoolean(false);

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "open camera successfully");
            mResult.set(true);
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            mLatch.countDown();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "disconnect camera");
            mLatch.countDown();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Fail to open camera, error code = " + error);
            mLatch.countDown();
        }

        public boolean waitForResult() throws InterruptedException {
            mLatch.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return mResult.get();
        }
    }

    @CannotSetPolicyTest(policy = {DisallowCamera.class, DisallowCameraPermissionBased.class},
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CAMERA")
    public void setUserRestriction_disallowCamera_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CAMERA));
    }

    @PolicyAppliesTest(policy = {DisallowCamera.class, DisallowCameraPermissionBased.class})
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CAMERA")
    public void setUserRestriction_disallowCamera_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CAMERA);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CAMERA))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CAMERA);
        }
    }

    @PolicyDoesNotApplyTest(policy = {DisallowCamera.class, DisallowCameraPermissionBased.class})
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CAMERA")
    public void setUserRestriction_disallowCamera_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CAMERA);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CAMERA))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CAMERA);
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_CAMERA)
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CAMERA")
    @Test
    public void openCamera_disallowCameraRestrictionIsNotSet_successful() throws Exception {
        assertCanOpenCamera();
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @EnsureHasUserRestriction(DISALLOW_CAMERA)
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CAMERA")
    @Test
    public void openCamera_disallowCameraRestrictionIsSet_unsuccessful() throws Exception {
        assertCanNotOpenCamera();
    }

    // TODO: Test policy transparency

}
