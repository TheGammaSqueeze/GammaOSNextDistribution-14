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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ADMIN;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public final class DeviceAdminInfoTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    static ComponentName getReceiverComponent() {
        return new ComponentName("android.admin.app", "android.admin.app.CtsDeviceAdminReceiver");
    }

    static ComponentName getSecondReceiverComponent() {
        return new ComponentName("android.admin.app", "android.admin.app.CtsDeviceAdminReceiver2");
    }

    static ComponentName getThirdReceiverComponent() {
        return new ComponentName("android.admin.app", "android.admin.app.CtsDeviceAdminReceiver3");
    }

    static ComponentName getVisibleComponent() {
        return new ComponentName(
                "android.admin.app", "android.admin.app.CtsDeviceAdminReceiverVisible");
    }

    static ComponentName getInvisibleComponent() {
        return new ComponentName(
                "android.admin.app", "android.admin.app.CtsDeviceAdminReceiverInvisible");
    }

    @EnsureHasDeviceOwner
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#getComponent")
    public void getComponent_isCorrect() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(sDeviceState.dpc().componentName()).isEqualTo(info.getComponent());
    }

    @EnsureHasDeviceOwner
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#getPackageName")
    public void getPackageName_isCorrect() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(sDeviceState.dpc().componentName()
                .getPackageName()).isEqualTo(info.getPackageName());
    }

    @EnsureHasDeviceOwner
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#getReceiverName")
    public void getReceiverName_isCorrect() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(sDeviceState.dpc().componentName()
                .getClassName()).isEqualTo(info.getReceiverName());
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#supportsTransferOwnership")
    public void supportsTransferOwnership_deviceAdminSupportsTransferOwnership_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.supportsTransferOwnership()).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#supportsTransferOwnership")
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void supportsTransferOwnership_deviceAdminDoesNotSupportTransferOwnership_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.supportsTransferOwnership()).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_LIMIT_PASSWORD"})
    public void usesPolicy_usesPolicyLimitPassword_deviceAdminSupportsLimitPassword_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_LIMIT_PASSWORD"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyLimitPassword_deviceAdminDoesNotSupportLimitPassword_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_RESET_PASSWORD"})
    public void usesPolicy_usesPolicyResetPassword_deviceAdminSupportsResetPassword_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_RESET_PASSWORD)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_RESET_PASSWORD"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyResetPassword_deviceAdminDoesNotSupportResetPassword_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_RESET_PASSWORD)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_WATCH_LOGIN"})
    public void usesPolicy_usesPolicyWatchLogin_deviceAdminSupportsWatchLogin_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_WATCH_LOGIN)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_WATCH_LOGIN"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyWatchLogin_deviceAdminDoesNotSupportWatchLogin_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_WATCH_LOGIN)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_WIPE_DATA"})
    public void usesPolicy_usesPolicyWipeData_deviceAdminSupportsWipeData_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_WIPE_DATA)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_WIPE_DATA"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyWipeData_deviceAdminDoesNotSupportWipeData_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_WIPE_DATA)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_ENCRYPTED_STORAGE"})
    public void usesPolicy_usesEncryptedStorage_deviceAdminSupportsEncryptedStorage_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_ENCRYPTED_STORAGE)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_ENCRYPTED_STORAGE"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesEncryptedStorage_deviceAdminDoesNotSupportEncryptedStorage_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_ENCRYPTED_STORAGE)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_DISABLE_CAMERA"})
    public void usesPolicy_usesPolicyDisableCamera_deviceAdminSupportsDisableCamera_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_DISABLE_CAMERA"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyDisableCamera_deviceAdminDoesNotSupportDisableCamera_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_DISABLE_KEYGUARD_FEATURES"})
    public void usesPolicy_usesPolicyDisableKeyguardFeatures_deviceAdminSupportsDisableKeyguardFeatures_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_DISABLE_KEYGUARD_FEATURES"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyDisableKeyguardFeatures_deviceAdminDoesNotSupportDisableKeyguardFeatures_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_SETS_GLOBAL_PROXY"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicySetsGlobalProxy_deviceAdminSupportsGlobalProxy_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_SETS_GLOBAL_PROXY"})
    public void usesPolicy_usesPolicySetsGlobalProxy_deviceAdminDoesNotSupportGlobalProxy_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_FORCE_LOCK"})
    public void usesPolicy_usesPolicyForceLock_deviceAdminSupportsForceLock_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_FORCE_LOCK)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_FORCE_LOCK"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyForceLock_deviceAdminDoesNotSupportForceLock_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_FORCE_LOCK)).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_EXPIRE_PASSWORD"})
    public void usesPolicy_usesPolicyExpirePassword_deviceAdminSupportsExpirePassword_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#usesPolicy",
            "android.app.admin.DeviceAdminInfo#USES_POLICY_EXPIRE_PASSWORD"})
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void usesPolicy_usesPolicyExpirePassword_deviceAdminDoesNotSupportExpirePassword_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.usesPolicy(DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD)).isFalse();
    }

    private enum DeviceAdminPolicyTag {
        USES_POLICY_WATCH_LOGIN("watch-login", DeviceAdminInfo.USES_POLICY_WATCH_LOGIN),
        USES_POLICY_RESET_PASSWORD("reset-password", DeviceAdminInfo.USES_POLICY_RESET_PASSWORD),
        USES_POLICY_FORCE_LOCK("force-lock", DeviceAdminInfo.USES_POLICY_FORCE_LOCK),
        USES_POLICY_WIPE_DATA("wipe-data", DeviceAdminInfo.USES_POLICY_WIPE_DATA),
        USES_POLICY_SETS_GLOBAL_PROXY("set-global-proxy", DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY),
        USES_POLICY_EXPIRE_PASSWORD("expire-password", DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD),
        USES_ENCRYPTED_STORAGE("encrypted-storage", DeviceAdminInfo.USES_ENCRYPTED_STORAGE),
        USES_POLICY_DISABLE_CAMERA("disable-camera", DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA),
        USES_POLICY_DISABLE_KEYGUARD_FEATURES("disable-keyguard-features", DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES);

        final String tag;
        final int usesPolicyFlag;

        DeviceAdminPolicyTag(String tag, int usesPolicyFlag) {
            this.tag = tag;
            this.usesPolicyFlag = usesPolicyFlag;
        }
    }

    @EnsureHasDeviceOwner // getTagForPolicy could be static so we don't actually care about the device admin - but we need one to get the info
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#getTagForPolicy"
    })
    public void getTagForPolicy_returnsCorrectTag(
            @EnumTestParameter(DeviceAdminPolicyTag.class) DeviceAdminPolicyTag policyTag) throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.getTagForPolicy(policyTag.usesPolicyFlag)).isEqualTo(policyTag.tag);
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#describeContents")
    public void describeContents_returnsAtLeastZero() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.describeContents()).isAtLeast(0);
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#getActivityInfo")
    public void getActivityInfo_returnsActivityInfo() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.getActivityInfo().getComponentName()).isEqualTo(
                sDeviceState.dpc().componentName());
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#isVisible")
    public void isVisible_deviceAdminIsVisible_returnsTrue() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.isVisible()).isTrue();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = "android.app.admin.DeviceAdminInfo#isVisible")
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void isVisible_deviceAdminIsNotVisible_returnsFalse() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.isVisible()).isFalse();
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#getHeadlessDeviceOwnerMode",
            "android.app.admin.DeviceAdminInfo#HEADLESS_DEVICE_OWNER_MODE_AFFILIATED"
    })
    public void getHeadlessDeviceOwnerMode_supportsAffiliated_returnsAffiliated() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.getHeadlessDeviceOwnerMode())
                .isEqualTo(DeviceAdminInfo.HEADLESS_DEVICE_OWNER_MODE_AFFILIATED);
    }

    @EnsureHasDeviceOwner // We need something with a DeviceAdminReceiver
    @Test
    @ApiTest(apis = {
            "android.app.admin.DeviceAdminInfo#getHeadlessDeviceOwnerMode",
            "android.app.admin.DeviceAdminInfo#HEADLESS_DEVICE_OWNER_MODE_UNSUPPORTED"
    })
    @Ignore // TODO(279011496): Specify that the Device Owner must have the metadata relating to the below
    public void getHeadlessDeviceOwnerMode_isUnsupported_returnsUnsupported() throws Exception {
        DeviceAdminInfo info = createDeviceAdminInfo();

        assertThat(info.getHeadlessDeviceOwnerMode())
                .isEqualTo(DeviceAdminInfo.HEADLESS_DEVICE_OWNER_MODE_UNSUPPORTED);
    }

    private DeviceAdminInfo createDeviceAdminInfo() throws Exception {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = sContext.getPackageManager()
                .getReceiverInfo(sDeviceState.dpc().componentName(), PackageManager.GET_META_DATA);
        return new DeviceAdminInfo(sContext, resolveInfo);
    }

}
