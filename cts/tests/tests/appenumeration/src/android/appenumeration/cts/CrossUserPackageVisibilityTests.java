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

package android.appenumeration.cts;

import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;
import static android.Manifest.permission.SUSPEND_APPS;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_TEST;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Constants.TARGET_STUB_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_STUB_SHARED_USER_APK;
import static android.appenumeration.cts.Utils.installExistPackageForUser;
import static android.appenumeration.cts.Utils.installPackage;
import static android.appenumeration.cts.Utils.installPackageForUser;
import static android.appenumeration.cts.Utils.uninstallPackage;
import static android.appenumeration.cts.Utils.uninstallPackageForUser;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN;
import static android.content.pm.PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.SuspendDialogInfo;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Verify that app without holding the {@link android.Manifest.permission#INTERACT_ACROSS_USERS}
 * can't detect the existence of another app in the different users on the device via the
 * side channel attacks.
 */
@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4.class)
public class CrossUserPackageVisibilityTests {
    private static final String CTS_SHIM_PACKAGE_NAME = "com.android.cts.ctsshim";
    private static final String PROPERTY_BOOLEAN = "android.cts.PROPERTY_BOOLEAN";

    private static final ComponentName TEST_ACTIVITY_COMPONENT_NAME = ComponentName.createRelative(
            TARGET_STUB, ACTIVITY_CLASS_TEST);
    private static final ComponentName TEST_INSTRUMENTATION_COMPONENT_NAME =
            ComponentName.createRelative(
                    TARGET_STUB, "android.appenumeration.testapp.DummyInstrumentation");

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private PackageManager mPackageManager;
    private UserReference mCurrentUser;
    private UserReference mOtherUser;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();

        // Get users
        final UserReference primaryUser = sDeviceState.primaryUser();
        if (primaryUser.id() == mContext.getUserId()) {
            mCurrentUser = primaryUser;
            mOtherUser = sDeviceState.secondaryUser();
        } else {
            mCurrentUser = UserReference.of(mContext.getUser());
            mOtherUser = primaryUser;
        }

        uninstallPackage(TARGET_STUB);
        uninstallPackage(TARGET_STUB_SHARED_USER);
    }

    @After
    public void tearDown() {
        uninstallPackage(TARGET_STUB);
        uninstallPackage(TARGET_STUB_SHARED_USER);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testIsPackageSuspended_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));
    }

    @Test
    public void testGetTargetSdkVersion_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));
    }

    @Test
    public void testCheckSignatures_cannotDetectStubPkg() {
        final String selfPackageName = mContext.getPackageName();
        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
    }

    @Test
    public void testGetAllIntentFilters_cannotDetectStubPkg() {
        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();
    }

    @Test
    public void testGetInstallerPackageName_cannotDetectStubPkg() {
        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));
    }

    @Test
    public void testGetInstallSourceInfo_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));
    }

    @Test
    public void testGetApplicationEnabledSetting_cannotDetectStubPkg() {
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getApplicationEnabledSetting(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getApplicationEnabledSetting(TARGET_STUB));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testGetApplicationEnabledSetting_canSeeHiddenUntilInstalled() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        uninstallPackageForUser(CTS_SHIM_PACKAGE_NAME, mCurrentUser);
        mPackageManager.setSystemAppState(
                CTS_SHIM_PACKAGE_NAME, SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        try {
            // no throw exception
            mPackageManager.getApplicationEnabledSetting(CTS_SHIM_PACKAGE_NAME);
        } finally {
            mPackageManager.setSystemAppState(
                    CTS_SHIM_PACKAGE_NAME, SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE);
            installExistPackageForUser(CTS_SHIM_PACKAGE_NAME, mCurrentUser);
        }
    }

    @Test
    public void testGetComponentEnabledSetting_cannotDetectStubPkg() {
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getComponentEnabledSetting(TEST_ACTIVITY_COMPONENT_NAME));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getComponentEnabledSetting(TEST_ACTIVITY_COMPONENT_NAME));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testSetApplicationEnabledSetting_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(CHANGE_COMPONENT_ENABLED_STATE);
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationEnabledSetting(
                        TARGET_STUB, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationEnabledSetting(
                        TARGET_STUB, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testSetComponentEnabledSetting_cannotDetectStubPkg() {
        final ComponentName componentName = ComponentName.createRelative(
                TARGET_STUB, ACTIVITY_CLASS_TEST);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(CHANGE_COMPONENT_ENABLED_STATE);
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSetting(
                        componentName, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSetting(
                        componentName, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testCheckUidSignatures_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.checkSignatures(uidStub, uidStubSharedUser))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
    }

    @Test
    public void testHasUidSigningCertificate_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final PackageInfo stubInfo =
                mPackageManager.getPackageInfo(TARGET_STUB,
                        PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        final PackageInfo stubSharedUserInfo =
                mPackageManager.getPackageInfo(TARGET_STUB_SHARED_USER,
                        PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.hasSigningCertificate(
                stubInfo.applicationInfo.uid,
                stubInfo.signingInfo.getApkContentsSigners()[0].toByteArray(),
                PackageManager.CERT_INPUT_RAW_X509)).isFalse();
        assertThat(mPackageManager.hasSigningCertificate(
                stubSharedUserInfo.applicationInfo.uid,
                stubSharedUserInfo.signingInfo.getApkContentsSigners()[0].toByteArray(),
                PackageManager.CERT_INPUT_RAW_X509)).isFalse();
    }

    @Test
    public void testGetNameForUid_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.getNameForUid(uidStub)).isNull();
        assertThat(mPackageManager.getNameForUid(uidStubSharedUser)).isNull();
    }

    @Test
    public void testGetNamesForUids_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        final List<String> names = Arrays.asList(
                mPackageManager.getNamesForUids(new int[] {uidStub, uidStubSharedUser}));
        assertThat(names).hasSize(2);
        names.forEach(name -> assertThat(name).isNull());
    }

    @Test
    public void testGetPackageProperty_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TARGET_STUB));
    }

    @Test
    public void testGetComponentProperty_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TEST_ACTIVITY_COMPONENT_NAME));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TEST_ACTIVITY_COMPONENT_NAME));
    }

    @Test
    public void testSetApplicationCategoryHint_cannotDetectStubPkg() {
        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationCategoryHint(
                        TARGET_STUB, ApplicationInfo.CATEGORY_PRODUCTIVITY));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationCategoryHint(
                        TARGET_STUB, ApplicationInfo.CATEGORY_PRODUCTIVITY));
    }

    @Test
    public void testGetUnsuspendablePackages_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        String [] unsuspendable =
                mPackageManager.getUnsuspendablePackages(new String[] {TARGET_STUB});
        assertThat(unsuspendable).asList().contains(TARGET_STUB);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        unsuspendable = mPackageManager.getUnsuspendablePackages(new String[] {TARGET_STUB});
        assertThat(unsuspendable).asList().contains(TARGET_STUB);
    }

    @Test
    public void testGetInstallReason_cannotDetectStubPkg() {
        assertThat(mPackageManager.getInstallReason(TARGET_STUB, Process.myUserHandle()))
                .isEqualTo(PackageManager.INSTALL_REASON_UNKNOWN);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.getInstallReason(TARGET_STUB, Process.myUserHandle()))
                .isEqualTo(PackageManager.INSTALL_REASON_UNKNOWN);
    }

    @Test
    public void testSetDistractingPackageRestrictions_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        String [] distractedPkg = mPackageManager.setDistractingPackageRestrictions(
                new String[] {TARGET_STUB}, PackageManager.RESTRICTION_NONE);
        assertThat(distractedPkg).asList().contains(TARGET_STUB);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        distractedPkg = mPackageManager.setDistractingPackageRestrictions(
                new String[] {TARGET_STUB}, PackageManager.RESTRICTION_NONE);
        assertThat(distractedPkg).asList().contains(TARGET_STUB);
    }

    @Test
    public void testQueryApplicationProperty_cannotDetectStubPkg() {
        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        List<PackageManager.Property> properties =
                mPackageManager.queryApplicationProperty(PROPERTY_BOOLEAN).stream()
                        .filter(property -> property.getPackageName().equals(TARGET_STUB))
                        .collect(Collectors.toList());
        assertThat(properties).isEmpty();
    }

    @Test
    public void testQueryActivityProperty_cannotDetectStubPkg() {
        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        List<PackageManager.Property> properties =
                mPackageManager.queryActivityProperty(PROPERTY_BOOLEAN).stream()
                        .filter(property -> property.getPackageName().equals(TARGET_STUB))
                        .collect(Collectors.toList());
        assertThat(properties).isEmpty();
    }

    @Test
    public void testQueryProviderProperty_cannotDetectStubPkg() {
        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        List<PackageManager.Property> properties =
                mPackageManager.queryProviderProperty(PROPERTY_BOOLEAN).stream()
                        .filter(property -> property.getPackageName().equals(TARGET_STUB))
                        .collect(Collectors.toList());
        assertThat(properties).isEmpty();
    }

    @Test
    public void testQueryReceiverProperty_cannotDetectStubPkg() {
        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        List<PackageManager.Property> properties =
                mPackageManager.queryReceiverProperty(PROPERTY_BOOLEAN).stream()
                        .filter(property -> property.getPackageName().equals(TARGET_STUB))
                        .collect(Collectors.toList());
        assertThat(properties).isEmpty();
    }

    @Test
    public void testQueryServiceProperty_cannotDetectStubPkg() {
        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        List<PackageManager.Property> properties =
                mPackageManager.queryServiceProperty(PROPERTY_BOOLEAN).stream()
                        .filter(property -> property.getPackageName().equals(TARGET_STUB))
                        .collect(Collectors.toList());
        assertThat(properties).isEmpty();
    }

    @Test
    public void testSetInstallerPackageName_cannotDetectStubPkg() {
        final Exception ex1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setInstallerPackageName(
                        TARGET_STUB, null /* installerPackageName */));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final Exception ex2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setInstallerPackageName(
                        TARGET_STUB, null /* installerPackageName */));
        assertThat(ex1.getMessage()).isEqualTo(ex2.getMessage());
    }

    @Test
    public void testSetPackagesSuspended_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        final SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setMessage("Test message")
                .build();
        String [] suspendedPkg = mPackageManager.setPackagesSuspended(
                new String[] {TARGET_STUB}, true /* suspended */, null /* appExtras */,
                null /* launcherExtras */, dialogInfo);
        assertThat(suspendedPkg).asList().contains(TARGET_STUB);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        suspendedPkg = mPackageManager.setPackagesSuspended(
                new String[] {TARGET_STUB}, true /* suspended */, null /* appExtras */,
                null /* launcherExtras */, dialogInfo);
        assertThat(suspendedPkg).asList().contains(TARGET_STUB);
    }

    @Test
    public void testGetInstrumentationInfo_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstrumentationInfo(
                        TEST_INSTRUMENTATION_COMPONENT_NAME, 0 /* flags */));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstrumentationInfo(
                        TEST_INSTRUMENTATION_COMPONENT_NAME, 0 /* flags */));
    }

    @Test
    public void testQueryInstrumentation_cannotDetectStubPkg() {
        assertThat(mPackageManager.queryInstrumentation(TARGET_STUB, 0 /* flags */)).isEmpty();

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.queryInstrumentation(TARGET_STUB, 0 /* flags */)).isEmpty();
    }

    @Test
    public void testHasSigningCertificate_cannotDetectStubPkg() throws Exception {
        installPackage(TARGET_STUB_APK);
        final PackageInfo stubInfo =
                mPackageManager.getPackageInfo(TARGET_STUB,
                        PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);

        assertThat(mPackageManager.hasSigningCertificate(
                TARGET_STUB,
                stubInfo.signingInfo.getApkContentsSigners()[0].toByteArray(),
                PackageManager.CERT_INPUT_RAW_X509)).isFalse();
    }

    @Test
    public void testCanPackageQuery_cannotDetectStubPkg() throws Exception {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.canPackageQuery(TARGET_STUB, TARGET_STUB_SHARED_USER));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);
        installPackageForUser(TARGET_STUB_SHARED_USER_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.canPackageQuery(TARGET_STUB, TARGET_STUB_SHARED_USER));
    }

    @Test
    public void testQueryContentProviders_cannotDetectStubPkg() throws Exception {
        installPackage(TARGET_STUB_APK);
        final int uid = mPackageManager.getPackageUid(TARGET_STUB, PackageInfoFlags.of(0));
        final int crossUid = UserHandle.getUid(mOtherUser.id(), UserHandle.getAppId(uid));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);

        assertThrows(SecurityException.class,
                () -> mPackageManager.queryContentProviders(
                        TARGET_STUB, crossUid, PackageManager.ComponentInfoFlags.of(0)));
    }
}
