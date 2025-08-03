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

package android.virtualdevice.cts.applaunch;

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.content.pm.PackageManager.ACTION_REQUEST_PERMISSIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_PERMISSION_NAME;
import static android.virtualdevice.cts.common.StreamedAppConstants.PERMISSION_TEST_ACTIVITY;
import static android.virtualdevice.cts.common.StreamedAppConstants.STREAMED_APP_PACKAGE;
import static android.virtualdevice.cts.common.VirtualDeviceRule.BLOCKED_ACTIVITY_COMPONENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDevicePermissionTest {

    private static final String NORMAL_PERMISSION_GRANTED =
            Manifest.permission.HIGH_SAMPLING_RATE_SENSORS;
    private static final String NORMAL_PERMISSION_NOT_GRANTED = Manifest.permission.SET_ALARM;
    // Dangerous permissions specified in AndroidManifest.xml are automatically granted to CTS apps
    private static final String DANGEROUS_PERMISSION_GRANTED = Manifest.permission.RECORD_AUDIO;
    // Tests have not been granted CAMERA permission as per AndroidManifest.xml
    private static final String DANGEROUS_PERMISSION_NOT_GRANTED =
            Manifest.permission.READ_PHONE_NUMBERS;
    private static final String PRIVILEGED_PERMISSION = Manifest.permission.LOCATION_BYPASS;
    private static final String SIGNATURE_PERMISSION =
            Manifest.permission.READ_APP_SPECIFIC_LOCALES;

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            GRANT_RUNTIME_PERMISSIONS, REVOKE_RUNTIME_PERMISSIONS);

    private final Context mContext =
            getInstrumentation().getContext().createDeviceContext(Context.DEVICE_ID_DEFAULT);
    private final PackageManager mPackageManager = mContext.getPackageManager();

    private VirtualDevice mVirtualDevice;
    private int mVirtualDisplayId;
    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        createVirtualDeviceAndDisplay(VirtualDeviceRule.DEFAULT_VIRTUAL_DEVICE_PARAMS);

        // Revoke the dangerous permissions of the test app - it is only used here to test the
        // behavior of the permission dialogs but dangerous permissions are automatically granted to
        // CTS apps.
        mPackageManager.revokeRuntimePermission(STREAMED_APP_PACKAGE, DANGEROUS_PERMISSION_GRANTED,
                UserHandle.of(mContext.getUserId()));
        assertThat(
                mPackageManager.checkPermission(DANGEROUS_PERMISSION_GRANTED, STREAMED_APP_PACKAGE))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void normalPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(
                checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void privilegedPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(mRule.runWithTemporaryPermission(
                () -> checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, DEFAULT_DISPLAY),
                PRIVILEGED_PERMISSION))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void privilegedPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(mRule.runWithTemporaryPermission(
                () -> checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, mVirtualDisplayId),
                PRIVILEGED_PERMISSION))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void privilegedPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void privilegedPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(mRule.runWithTemporaryPermission(
                () -> checkPermissionStateOnDevice(SIGNATURE_PERMISSION, DEFAULT_DISPLAY),
                SIGNATURE_PERMISSION))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void signaturePermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(mRule.runWithTemporaryPermission(
                () -> checkPermissionStateOnDevice(SIGNATURE_PERMISSION, mVirtualDisplayId),
                SIGNATURE_PERMISSION))
                .isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    public void signaturePermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @RequiresFlagsEnabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialog_streamingEnabled_isShown() {
        verifyComponentShownAfterPermissionRequest(getPermissionDialogComponentName());
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogDefaultParams_streamingDisabled_showsBlockedDialog() {
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogAllowlisted_streamingDisabled_showsBlockedDialog() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(
                        Set.of(PERMISSION_TEST_ACTIVITY, getPermissionDialogComponentName()))
                .build();
        createVirtualDeviceAndDisplay(params);
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void permissionDialogDynamicallyAllowlisted_streamingDisabled_showsBlockedDialog() {
        mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        mVirtualDevice.addActivityPolicyExemption(PERMISSION_TEST_ACTIVITY);
        mVirtualDevice.addActivityPolicyExemption(getPermissionDialogComponentName());
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogInBlocklist_streamingEnabled_showsBlockedDialog() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(getPermissionDialogComponentName()))
                .build();
        createVirtualDeviceAndDisplay(params);
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @RequiresFlagsEnabled({Flags.FLAG_STREAM_PERMISSIONS, Flags.FLAG_DYNAMIC_POLICY})
    @Test
    public void permissionDialogInDynamicBlocklist_streamingEnabled_showsBlockedDialog() {
        mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
        mVirtualDevice.addActivityPolicyExemption(getPermissionDialogComponentName());
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @Test
    public void allowlistPolicy_permissionDialogNotAllowlisted_showsBlockedDialog() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(PERMISSION_TEST_ACTIVITY))
                .build();
        createVirtualDeviceAndDisplay(params);
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void dynamicAllowlistPolicy_permissionDialogNotAllowlisted_showsBlockedDialog() {
        mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        mVirtualDevice.addActivityPolicyExemption(PERMISSION_TEST_ACTIVITY);
        verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
    }

    private ComponentName getPermissionDialogComponentName() {
        Intent intent = new Intent(ACTION_REQUEST_PERMISSIONS);
        intent.setPackage(mPackageManager.getPermissionControllerPackageName());
        return intent.resolveActivity(mPackageManager);
    }

    private int checkPermissionStateOnDevice(String permissionName, int displayId) {
        EmptyActivity activity = mRule.startActivityOnDisplaySync(displayId, EmptyActivity.class);
        return activity.checkSelfPermission(permissionName);
    }

    private void verifyComponentShownAfterPermissionRequest(ComponentName componentName) {
        requestPermissionOnDevice(DANGEROUS_PERMISSION_GRANTED, mVirtualDisplayId);
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(mVirtualDisplayId), eq(PERMISSION_TEST_ACTIVITY), anyInt());
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(mVirtualDisplayId), eq(componentName), anyInt());
    }

    private void requestPermissionOnDevice(String permissionName, int displayId) {
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(PERMISSION_TEST_ACTIVITY)
                .putExtra(EXTRA_PERMISSION_NAME, permissionName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        mRule.sendIntentToDisplay(intent, displayId);
    }

    private void createVirtualDeviceAndDisplay(VirtualDeviceParams params) {
        mVirtualDevice = mRule.createManagedVirtualDevice(params);
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
    }
}
