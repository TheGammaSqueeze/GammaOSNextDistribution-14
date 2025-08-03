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

package android.devicepolicy.cts;


import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.os.UserManager.DISALLOW_BLUETOOTH_SHARING;

import static com.android.bedstead.nene.bluetooth.Bluetooth.OPP_LAUNCHER_CLASS;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_BLUETOOTH;
import static com.android.bedstead.nene.permissions.CommonPermissions.BLUETOOTH_CONNECT;
import static com.android.bedstead.nene.permissions.CommonPermissions.LOCAL_MAC_ADDRESS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_BLUETOOTH;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.Bluetooth;
import com.android.bedstead.harrier.policies.DisallowBluetooth;
import com.android.bedstead.harrier.policies.DisableBluetoothContactSharing;
import com.android.bedstead.harrier.policies.DisallowBluetoothGlobally;
import com.android.bedstead.harrier.policies.DisallowBluetoothPreU;
import com.android.bedstead.harrier.policies.DisallowBluetoothSharing;
import com.android.bedstead.harrier.policies.DisallowBluetoothSharingGlobally;
import com.android.bedstead.harrier.policies.DisallowBluetoothSharingPreU;
import com.android.bedstead.harrier.policies.DisallowConfigBluetooth;
import com.android.bedstead.harrier.policies.DisallowConfigBluetoothGlobally;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalBluetoothSettingsStep;
import com.android.interactive.steps.quicksettings.CanYouEnableBluetoothInQuickSettingsStep;
import com.android.interactive.steps.settings.CanYouPairANewBluetoothDeviceStep;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.After;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_BLUETOOTH)
public final class BluetoothTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final BluetoothManager sBluetoothManager =
            sContext.getSystemService(BluetoothManager.class);
    private static final BluetoothAdapter sBluetoothAdapter = sBluetoothManager.getAdapter();

    private static final String VALID_ADDRESS = "01:02:03:04:05:06";
    private static final byte[] VALID_ADDRESS_BYTES =
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

    private static final boolean OPP_ENABLED =
            TestApis.systemProperties().getBoolean("bluetooth.profile.opp.enabled", false);

    /** An intent to share an image file. If Bluetooth sharing is allowed, it should be
     * handled by BluetoothOppLauncherActivity.
     */
    private static final Intent FILE_SHARING_INTENT = createFileSharingIntent();
    private static final ComponentName OPP_LAUNCHER_COMPONENT = new ComponentName(
            TestApis.bluetooth().findPackageName(), OPP_LAUNCHER_CLASS);
    private static final PackageManager sPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();

    private static Intent createFileSharingIntent() {
        final Intent result = new Intent(Intent.ACTION_SEND);
        final Uri uri = Uri.parse("content://foo/bar");
        result.setDataAndType(uri, "image/*");
        return result;
    }

    @After
    public void teardown() {
        clearUserRestriction(DISALLOW_BLUETOOTH);
        clearUserRestriction(DISALLOW_BLUETOOTH_SHARING);
        clearUserRestriction(DISALLOW_CONFIG_BLUETOOTH);
    }

    private void clearUserRestriction(String restriction) {
        try {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);
        } catch (SecurityException | IllegalStateException e) {
            // Expected for some states
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    @PolicyAppliesTest(policy = Bluetooth.class)
    public void disable_bluetoothIsDisabled() {
        // TODO(b/220306133): bluetooth from background
        Assume.assumeTrue("Cannot run in background",
                TestApis.users().current().isForeground());

        BlockingBroadcastReceiver r = sDeviceState.registerBroadcastReceiverForUser(
                sDeviceState.dpc().user(), BluetoothAdapter.ACTION_STATE_CHANGED,
                this::isStateDisabled);

        try (PermissionContext p =
                     sDeviceState.dpc().permissions().withPermission(BLUETOOTH_CONNECT)) {
            // For some reason it doesn't always immediately recognise that the permission has
            // been granted to the DPC
            Poll.forValue("return value for disable from adapter",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().disable())
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            r.awaitForBroadcast();

            Poll.forValue("Bluetooth Enabled for DPC",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().isEnabled())
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
            assertThat(TestApis.bluetooth().isEnabled()).isFalse();
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled
    @PolicyAppliesTest(policy = Bluetooth.class)
    public void enable_bluetoothIsEnabled() {
        // TODO(b/220306133): bluetooth from background
        Assume.assumeTrue("Cannot run in background",
                TestApis.users().current().isForeground());

        BlockingBroadcastReceiver r = sDeviceState.registerBroadcastReceiverForUser(
                sDeviceState.dpc().user(), BluetoothAdapter.ACTION_STATE_CHANGED,
                this::isStateEnabled);

        try (PermissionContext p =
                     sDeviceState.dpc().permissions().withPermission(BLUETOOTH_CONNECT)) {
            // For some reason it doesn't always immediately recognise that the permission has
            // been granted to the DPC
            Poll.forValue("return value for enable from adapter",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().enable())
                    .toBeEqualTo(true)
                    .errorOnFail().await();
            r.awaitForBroadcast();

            Poll.forValue("Bluetooth Enabled for DPC",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().isEnabled())
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            assertThat(TestApis.bluetooth().isEnabled()).isTrue();
        }
    }

    @Test
    @RequireRunOnWorkProfile
    @EnsureHasPermission(BLUETOOTH_CONNECT)
    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    public void listenUsingRfcommWithServiceRecord_inManagedProfile_returnsValidSocket()
            throws IOException {
        BluetoothServerSocket socket = null;
        try {
            socket = sBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    "test", UUID.randomUUID());

            assertThat(socket).isNotNull();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private boolean isStateEnabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                == BluetoothAdapter.STATE_ON;
    }

    private boolean isStateDisabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                == BluetoothAdapter.STATE_OFF;
    }

    @Test
    @RequireRunOnWorkProfile
    @EnsureHasPermission({LOCAL_MAC_ADDRESS, BLUETOOTH_CONNECT})
    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    public void getAddress_inManagedProfile_returnsValidAddress() {
        assertThat(BluetoothAdapter.checkBluetoothAddress(sBluetoothAdapter.getAddress())).isTrue();
    }

    @Test
    @RequireRunOnWorkProfile
    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled // This method should work even with bluetooth disabled
    public void getRemoteDevice_inManagedProfile_validAddress_works() {
        BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(VALID_ADDRESS);

        assertThat(device.getAddress()).isEqualTo(VALID_ADDRESS);
    }

    @Test
    @RequireRunOnWorkProfile
    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled // This method should work even with bluetooth disabled
    public void getRemoteDevice_inManagedProfile_validAddressBytes_works() {
        BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(VALID_ADDRESS_BYTES);

        assertThat(device.getAddress()).isEqualTo(VALID_ADDRESS);
    }

    @Test
    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void newManagedProfile_disallowBluetoothSharingIsSet() {
        try (RemoteDpc dpc = RemoteDpc.createWorkProfile()) {
            assertThat(TestApis.devicePolicy().userRestrictions(dpc.user())
                    .isSet(DISALLOW_BLUETOOTH_SHARING)).isTrue();
        }
    }

    @Test
    @EnsureHasNoDpc
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void clearDisallowBluetoothSharing_newManagedProfile_disallowBluetoothSharingIsNotSet() {
        try (RemoteDpc dpc = RemoteDpc.createWorkProfile()) {
            dpc.devicePolicyManager().clearUserRestriction(
                    dpc.componentName(), DISALLOW_BLUETOOTH_SHARING);

            assertThat(TestApis.devicePolicy().userRestrictions(dpc.user())
                    .isSet(DISALLOW_BLUETOOTH_SHARING)).isFalse();
        }
    }

    @CannotSetPolicyTest(policy = DisallowBluetoothSharingPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_preU_disallowBluetoothSharing_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING));
    }

    @CannotSetPolicyTest(policy = DisallowBluetoothSharing.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void addUserRestriction_disallowBluetoothSharing_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING));
    }

    @CannotSetPolicyTest(policy = DisallowBluetoothSharingGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    @Ignore("No global setters of this restriction")
    public void addUserRestrictionGlobally_disallowBluetoothSharing_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                        DISALLOW_BLUETOOTH_SHARING));
    }

    @PolicyAppliesTest(policy = DisallowBluetoothSharingPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_preU_disallowBluetoothSharing_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowBluetoothSharing.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void addUserRestriction_disallowBluetoothSharing_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowBluetoothSharingGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    @Ignore("No global setters of this restriction")
    public void addUserRestrictionGlobally_disallowBluetoothSharing_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isTrue();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetoothSharingPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_preU_disallowBluetoothSharing_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isFalse();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetoothSharing.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void addUserRestriction_disallowBluetoothSharing_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isFalse();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetoothSharingGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void addUserRestrictionGlobally_disallowBluetoothSharing_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                DISALLOW_BLUETOOTH_SHARING);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH_SHARING))
                .isFalse();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_BLUETOOTH_SHARING)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void share_disallowBluetoothAndSharingRestrictionsAreNotSet_canShare() {
        Poll.forValue("Opp Launcher Component Enabled",
                () -> TestApis.packages().activity(OPP_LAUNCHER_COMPONENT)
                        .isEnabled(TestApis.users().system()))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();

        Assume.assumeTrue("We can't test resolving if opp is disabled", OPP_ENABLED);

        List<ResolveInfo> resolveInfos = sPackageManager.queryIntentActivities(
                FILE_SHARING_INTENT, /* flags= */ 0);
        assertThat(resolveInfosContainsActivity(resolveInfos, OPP_LAUNCHER_COMPONENT)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_BLUETOOTH_SHARING)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH_SHARING")
    public void share_disallowBluetoothSharingRestrictionIsSet_canNotShare() {
        Poll.forValue("Opp Launcher Component Enabled",
                () -> TestApis.packages().activity(OPP_LAUNCHER_COMPONENT)
                        .isEnabled(TestApis.users().system()))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();

        Assume.assumeTrue("We can't test resolving if opp is disabled", OPP_ENABLED);

        List<ResolveInfo> resolveInfos = sPackageManager.queryIntentActivities(
                FILE_SHARING_INTENT, /* flags= */ 0);
        assertThat(resolveInfosContainsActivity(resolveInfos, OPP_LAUNCHER_COMPONENT)).isFalse();
    }

    @CannotSetPolicyTest(policy = DisallowBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @RequireNotHeadlessSystemUserMode(reason =
            "This is special cased so it's only usable by profile owner on 'main' user"
                    + "- we need to simplify this state")
    public void addUserRestriction_disallowBluetooth_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH));
    }

    @CannotSetPolicyTest(policy = DisallowBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void addUserRestrictionGlobally_disallowBluetooth_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                        DISALLOW_BLUETOOTH));
    }

    @CannotSetPolicyTest(policy = DisallowBluetoothPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    @RequireNotHeadlessSystemUserMode(reason =
            "This is special cased so it's only usable by profile owner on 'main' user"
                    + "- we need to simplify this state")
    public void addUserRestriction_preU_disallowBluetooth_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH));
    }

    @EnsureHasUserRestriction(DISALLOW_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @RequireNotHeadlessSystemUserMode(reason = "b/276405672 bluetooth restriction not enforced on secondary users")
    public void share_disallowBluetoothRestrictionIsSet_canNotShare() {
        Poll.forValue("Opp Launcher Component Enabled",
                () -> TestApis.packages().activity(OPP_LAUNCHER_COMPONENT)
                        .isEnabled(TestApis.users().system()))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();

        Assume.assumeTrue("We can't test resolving if opp is disabled", OPP_ENABLED);

        List<ResolveInfo> resolveInfos = sPackageManager.queryIntentActivities(
                FILE_SHARING_INTENT, /* flags= */ 0);
        assertThat(resolveInfosContainsActivity(resolveInfos, OPP_LAUNCHER_COMPONENT)).isFalse();
    }

    private boolean resolveInfosContainsActivity(
            Collection<ResolveInfo> resolveInfos, ComponentName activity) {
        return resolveInfos.stream()
                .anyMatch(r -> r.activityInfo != null
                        && r.activityInfo.getComponentName().equals(activity));
    }

    @CannotSetPolicyTest(policy = DisallowConfigBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void addUserRestriction_disallowConfigBluetooth_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_BLUETOOTH));
    }

    @CannotSetPolicyTest(policy = DisallowConfigBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void addUserRestrictionGlobally_disallowConfigBluetooth_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                        DISALLOW_CONFIG_BLUETOOTH));
    }

    @PolicyAppliesTest(policy = DisallowConfigBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void addUserRestriction_disallowConfigBluetooth_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_CONFIG_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_BLUETOOTH))
                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowConfigBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    @Ignore("No global setters of this restriction")
    public void addUserRestrictionGlobally_disallowConfigBluetooth_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                DISALLOW_CONFIG_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_BLUETOOTH))
                .isTrue();
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void addUserRestriction_disallowConfigBluetooth_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_CONFIG_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_BLUETOOTH))
                .isFalse();
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void addUserRestrictionGlobally_disallowConfigBluetooth_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                DISALLOW_CONFIG_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_BLUETOOTH))
                .isFalse();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    @NotFullyAutomated(reason = "CanYouChangeBluetoothSettingsStep") // TODO: Automate
    public void disallowConfigBluetoothIsNotSet_canPairNewBluetoothDevice() throws Exception {
        Step.execute(NavigateToPersonalBluetoothSettingsStep.class);

        assertThat(Step.execute(CanYouPairANewBluetoothDeviceStep.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH")
    public void disallowConfigBluetoothIsSet_cannotPairNewBluetoothDevice() throws Exception {
        Step.execute(NavigateToPersonalBluetoothSettingsStep.class);

        assertThat(Step.execute(CanYouPairANewBluetoothDeviceStep.class)).isFalse();
        // TODO: Check expectation for policy transparency
//        assertThat(
//                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
//                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void addUserRestriction_disallowBluetooth_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH))
                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @Ignore("No global setters of this restriction")
    public void addUserRestrictionGlobally_disallowBluetooth_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH))
                .isTrue();
    }

    @PolicyAppliesTest(policy = DisallowBluetoothPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_preU_disallowBluetooth_isSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH))
                .isTrue();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetooth.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void addUserRestriction_disallowBluetooth_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH))
                .isFalse();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetoothGlobally.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void addUserRestrictionGlobally_disallowBluetooth_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH))
                .isFalse();
    }

    @PolicyDoesNotApplyTest(policy = DisallowBluetoothPreU.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_preU_disallowBluetooth_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), DISALLOW_BLUETOOTH);

        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_BLUETOOTH)).isFalse();
    }

    @EnsureBluetoothDisabled
    @EnsureDoesNotHaveUserRestriction(DISALLOW_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void disallowBluetoothIsNotSet_canEnableBluetoothInQuickSettings() throws Exception {
        assertThat(Step.execute(CanYouEnableBluetoothInQuickSettingsStep.class)).isTrue();
    }

    @EnsureBluetoothDisabled
    @EnsureHasUserRestriction(DISALLOW_BLUETOOTH)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_BLUETOOTH")
    public void disallowBluetoothIsSet_canNotEnableBluetoothInQuickSettings() throws Exception {
        assertThat(Step.execute(CanYouEnableBluetoothInQuickSettingsStep.class)).isFalse();

    }

    // TODO(268616930): Enable tests for policy transparency in settings for disallow bluetooth

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setBluetoothContactSharingDisabled")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisableBluetoothContactSharing.class)
    public void setBluetoothContactSharingDisabled_policyApplies_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(
                    TestApis.devicePolicy().getBluetoothContactSharingDisabled()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setBluetoothContactSharingDisabled")
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisableBluetoothContactSharing.class)
    public void setBluetoothContactSharingDisabled_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(
                    sDeviceState.dpc().devicePolicyManager().getBluetoothContactSharingDisabled(
                            sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setBluetoothContactSharingDisabled")
    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = DisableBluetoothContactSharing.class)
    public void setBluetoothContactSharingDisabled_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            assertThat(
                    TestApis.devicePolicy().getBluetoothContactSharingDisabled()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ false);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setBluetoothContactSharingDisabled")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DisableBluetoothContactSharing.class)
    public void setBluetoothContactSharingDisabled_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setBluetoothContactSharingDisabled(
                    sDeviceState.dpc().componentName(), /* disabled= */ true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(
                            EventId.SET_BLUETOOTH_CONTACT_SHARING_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        }
    }
}
