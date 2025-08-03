/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_IMEI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SERIAL_NUMBER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_STATE;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_PERSONALLY_OWNED;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE;
import static android.app.admin.DevicePolicyManager.STATUS_MANAGED_USERS_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.STATUS_OK;

import static com.android.managedprovisioning.TestUtils.assertBundlesEqual;
import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_LOCAL_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentInterface;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.VectorDrawable;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderHelper;
import com.android.managedprovisioning.common.DeviceManagementRoleHolderUpdaterHelper;
import com.android.managedprovisioning.common.FeatureFlagChecker;
import com.android.managedprovisioning.common.GetProvisioningModeUtils;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.PolicyComplianceUtils;
import com.android.managedprovisioning.common.RoleGranter;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import com.android.managedprovisioning.parser.MessageParser;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivityController.UiParams;
import com.android.managedprovisioning.util.LazyStringResource;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SmallTest
public class PreProvisioningActivityControllerTest {
    private static final String TEST_MDM_PACKAGE = "com.test.mdm";
    private static final String TEST_MDM_PACKAGE_LABEL = "Test MDM";
    private static final CharSequence DEFAULT_DEVICE_NAME = "device";
    private static final ComponentName TEST_MDM_COMPONENT_NAME = new ComponentName(TEST_MDM_PACKAGE,
            "com.test.mdm.DeviceAdmin");
    private static final String TEST_BOGUS_PACKAGE = "com.test.bogus";
    private static final String TEST_WIFI_SSID = "\"TestNet\"";
    private static final String MP_PACKAGE_NAME = "com.android.managedprovisioning";
    private static final int TEST_USER_ID = 10;
    private static final PackageDownloadInfo PACKAGE_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setCookieHeader("COOKIE_HEADER")
                    .setLocation("LOCATION")
                    .setMinVersion(1)
                    .setPackageChecksum(new byte[] {1})
                    .setSignatureChecksum(new byte[] {1})
                    .build();
    public static final ProvisioningParams DOWNLOAD_ROLE_HOLDER_PARAMS_WITH_ALLOW_OFFLINE =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                    .setDeviceAdminComponentName(TEST_MDM_COMPONENT_NAME)
                    .setRoleHolderDownloadInfo(PACKAGE_DOWNLOAD_INFO)
                    .setAllowOffline(true)
                    .build();
    public static final ProvisioningParams DOWNLOAD_ROLE_HOLDER_PARAMS =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                    .setDeviceAdminComponentName(TEST_MDM_COMPONENT_NAME)
                    .setRoleHolderDownloadInfo(PACKAGE_DOWNLOAD_INFO)
                    .build();
    public static final ProvisioningParams ALLOW_OFFLINE_PARAMS =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                    .setDeviceAdminComponentName(TEST_MDM_COMPONENT_NAME)
                    .setAllowOffline(true)
                    .build();
    private static final String TEST_IMEI = "my imei";
    private static final String DISCLAIMER_HEADER = "header1";
    private static final Uri DISCLAIMER_CONTENT_URI =
            Uri.parse("file:///test.example.uri/disclaimers.txt");
    private static final DisclaimersParam.Disclaimer[] OTHER_DISCLAIMERS =
            {new DisclaimersParam.Disclaimer("header2", "content2")};
    private static final DisclaimersParam.Disclaimer[] DISCLAIMERS =
            {new DisclaimersParam.Disclaimer(DISCLAIMER_HEADER, DISCLAIMER_CONTENT_URI.toString())};
    private static final DisclaimersParam DISCLAIMERS_PARAM =
            new DisclaimersParam.Builder().setDisclaimers(DISCLAIMERS).build();
    private static final DisclaimersParam OTHER_DISCLAIMERS_PARAM =
            new DisclaimersParam.Builder().setDisclaimers(OTHER_DISCLAIMERS).build();
    private static final Parcelable[] DISCLAIMERS_EXTRA = createDisclaimersExtra();
    private static final String LOCALE_EXTRA = "en_US";
    private static final Locale LOCALE = Locale.US;
    private static final Locale OTHER_LOCALE = Locale.CANADA;
    private static final String INVALID_LOCALE_EXTRA = "INVALIDLOCALE";
    private static final long LOCAL_TIME_EXTRA = 1234L;
    private static final long OTHER_LOCAL_TIME = 4321L;
    private static final String TIME_ZONE_EXTRA = "GMT";
    private static final String OTHER_TIME_ZONE = "GMT+1";
    private static final String TEST_ROLE_HOLDER_PACKAGE_NAME = "test.roleholder.package";
    private static final String TEST_ROLE_HOLDER_UPDATER_PACKAGE_NAME =
            "test.roleholderupdater.package";
    private static final FeatureFlagChecker sFeatureFlagChecker = createFeatureFlagChecker();
    private static final RoleGranter sRoleGranter =
            (context, user, roleName, packageName, callback) -> callback.accept(true);
    private static final DeviceManagementRoleHolderHelper
            DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER =
            new DeviceManagementRoleHolderHelper(
                    TEST_ROLE_HOLDER_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageName) -> true,
                    /* resolveIntentChecker= */ (intent, packageManager) -> true,
                    /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                    sFeatureFlagChecker,
                    sRoleGranter);
    private static final DeviceManagementRoleHolderHelper
            DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT =
            new DeviceManagementRoleHolderHelper(
                    TEST_ROLE_HOLDER_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageName) -> false,
                    /* resolveIntentChecker= */ (intent, packageManager) -> false,
                    /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                    sFeatureFlagChecker,
                    sRoleGranter);
    private static final String EMPTY_PACKAGE_NAME = "";
    private static final DeviceManagementRoleHolderHelper
            DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_CONFIGURED =
            new DeviceManagementRoleHolderHelper(
                    EMPTY_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageManager) -> false,
                    /* resolveIntentChecker= */ (intent, packageManager) -> false,
                    /* roleHolderStubChecker= */ (packageName, packageManager) -> false,
                    sFeatureFlagChecker,
                    sRoleGranter);
    private static final DeviceManagementRoleHolderUpdaterHelper
            ROLE_HOLDER_UPDATER_HELPER =
            new DeviceManagementRoleHolderUpdaterHelper(
                    TEST_ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                    TEST_ROLE_HOLDER_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageName) -> true,
                    /* intentResolverChecker= */ (intent) -> true,
                    sFeatureFlagChecker);
    private static final DeviceManagementRoleHolderUpdaterHelper
            ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED =
            new DeviceManagementRoleHolderUpdaterHelper(
                    TEST_ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                    TEST_ROLE_HOLDER_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageName) -> false,
                    /* intentResolverChecker= */ (intent) -> true,
                    sFeatureFlagChecker);
    private static final DeviceManagementRoleHolderUpdaterHelper
            ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED =
            new DeviceManagementRoleHolderUpdaterHelper(
                    /* roleHolderUpdaterPackageName= */ null,
                    TEST_ROLE_HOLDER_PACKAGE_NAME,
                    /* packageInstallChecker= */ (packageName) -> false,
                    /* intentResolverChecker= */ (intent) -> true,
                    sFeatureFlagChecker);
    private static final PersistableBundle ROLE_HOLDER_STATE = createRoleHolderStateBundle();
    private static final String TEST_CALLING_PACKAGE = "com.test.calling.package";
    private static final Bundle ADDITIONAL_EXTRAS_DEFAULT = new Bundle();
    private static final Bundle ADDITIONAL_EXTRAS_SUCCESSFUL_UPDATE =
            createSuccessfulUpdateAdditionalExtras();
    private static final Bundle GET_DEVICE_NAME_BUNDLE = new Bundle();
    private final Context stringContext = ApplicationProvider.getApplicationContext();

    @Mock
    private Context mContext;
    @Mock
    Resources mResources;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private UserManager mUserManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private PersistentDataBlockManager mPdbManager;
    @Mock
    private PreProvisioningActivityController.Ui mUi;
    @Mock
    private MessageParser mMessageParser;
    @Mock
    private Utils mUtils;
    @Mock
    private SettingsFacade mSettingsFacade;
    @Mock
    private Intent mIntent;
    @Mock
    private EncryptionController mEncryptionController;
    @Mock
    private TimeLogger mTimeLogger;
    @Mock
    private ManagedProvisioningSharedPreferences mSharedPreferences;
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private ContentInterface mContentInterface;

    private ProvisioningParams mParams;
    private PreProvisioningViewModel mViewModel;
    private ContentResolver mContentResolver;

    private PreProvisioningActivityController mController;
    public static final PersistableBundle TEST_ADMIN_BUNDLE = new PersistableBundle();
    private static boolean sDelegateProvisioningToRoleHolderEnabled;

    static {
        TEST_ADMIN_BUNDLE.putInt("someKey", 123);
    }
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mock(ContentResolver.class,
                withSettings().useConstructor(mContext, mContentInterface));

        System.setProperty("dexmaker.dexcache",
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext().getCacheDir().toString());

        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);

        when(mContext.getSystemServiceName(UserManager.class))
                .thenReturn(Context.USER_SERVICE);
        when(mContext.getSystemService(UserManager.class))
                .thenReturn(mUserManager);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(mKeyguardManager);
        when(mContext.getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE))
                .thenReturn(mPdbManager);
        when(mContext.getPackageName()).thenReturn(MP_PACKAGE_NAME);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
        when(mContext.getSystemServiceName(TelephonyManager.class))
                .thenReturn(Context.TELEPHONY_SERVICE);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mTelephonyManager.getImei()).thenReturn(TEST_IMEI);

        when(mUserManager.getProcessUserId()).thenReturn(TEST_USER_ID);

        when(mUtils.getActiveNetworkCapabilities(any(Context.class)))
                .thenReturn(new NetworkCapabilities());
        when(mUtils.isEncryptionRequired()).thenReturn(false);
        when(mUtils.currentLauncherSupportsManagedProfiles(mContext)).thenReturn(true);
        when(mUserManager.canAddMoreManagedProfiles(anyInt(), anyBoolean()))
                .thenReturn(true);

        when(mPackageManager.getApplicationIcon(anyString())).thenReturn(new VectorDrawable());
        when(mPackageManager.getApplicationLabel(any())).thenReturn(TEST_MDM_PACKAGE_LABEL);
        when(mPackageManager.resolveActivity(any(), anyInt())).thenReturn(new ResolveInfo());

        when(mKeyguardManager.inKeyguardRestrictedInputMode()).thenReturn(false);
        when(mDevicePolicyManager.getStorageEncryptionStatus())
                .thenReturn(DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(false);

        mViewModel = new PreProvisioningViewModel(
                mTimeLogger,
                mMessageParser,
                mEncryptionController, new PreProvisioningViewModel.DefaultConfig());

        mController = createControllerWithRoleHolderUpdaterNotPresent();

        disableRoleHolderDelegation();
    }

    private PreProvisioningActivityController createControllerWithRoleHolderHelpers(
            DeviceManagementRoleHolderHelper deviceManagementRoleHolderHelper,
            DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper) {
        return new PreProvisioningActivityController(
                mContext,
                mUi,
                mUtils,
                mSettingsFacade,
                mSharedPreferences,
                new PolicyComplianceUtils(),
                new GetProvisioningModeUtils(),
                mViewModel,
                (context, provisioningId) -> parcelables -> DISCLAIMERS_PARAM,
                deviceManagementRoleHolderHelper,
                roleHolderUpdaterHelper);
    }

    @Test
    public void testManagedProfile() throws Exception {
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.continueProvisioningAfterUserConsent());
        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).startProvisioning(mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_hasRoleHolderUpdaterInstalled_startsRoleHolderUpdater()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderUpdaterInstalled();
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        verify(mUi).onParamsValidated(mParams);
        // THEN start profile provisioning
        verify(mUi).startRoleHolderUpdater(/* isRoleHolderRequestedUpdate= */ false);
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_hasRoleHolderValidAndInstalled_updaterNotInstalled_startsRoleHolder()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start role holder provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).startRoleHolderProvisioning(any(Intent.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(true);
    }

    @Test
    public void testTrustedSource_roleHolderDownloadExtra_downloadsRoleHolder() throws Exception {
        enableRoleHolderDelegation();
        when(mSettingsFacade.isDuringSetupWizard(any())).thenReturn(false);
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED);

        // GIVEN an intent to provision via trusted source
        prepareMocksForTrustedSourceIntent(DOWNLOAD_ROLE_HOLDER_PARAMS);

        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN download role holder
        verify(mUi).onParamsValidated(DOWNLOAD_ROLE_HOLDER_PARAMS);
        verify(mUi).startPlatformDrivenRoleHolderDownload();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_roleHolderDownloadExtraAndAllowOffline_startsPlatformProvidedProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        when(mSettingsFacade.isDuringSetupWizard(any())).thenReturn(false);
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED);

        // GIVEN an intent to provision via trusted source
        prepareMocksForManagedProfileIntent(DOWNLOAD_ROLE_HOLDER_PARAMS_WITH_ALLOW_OFFLINE);

        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(DOWNLOAD_ROLE_HOLDER_PARAMS_WITH_ALLOW_OFFLINE);
        verify(mUi).initiateUi(any(UiParams.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_noRoleHolderAndRoleHolderDownloadExtra_failsProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        when(mSettingsFacade.isDuringSetupWizard(any())).thenReturn(false);
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED);

        // GIVEN an intent to provision via trusted source
        prepareMocksForManagedProfileIntent(DOWNLOAD_ROLE_HOLDER_PARAMS);

        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(DOWNLOAD_ROLE_HOLDER_PARAMS);
        verify(mUi).showErrorAndClose(any(), anyInt(), anyString());
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_hasRoleHolderValidAndInstalled_updaterNotDefined_startsRoleHolder()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start role holder provisioning
        verify(mUi).onParamsValidated(any());
        verify(mUi).startRoleHolderProvisioning(any(Intent.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(true);
    }

    @Test
    public void testManagedProfile_roleHolderStarted_startedWithoutState()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);

            // simulate that the updater has run and returned back, starting DMRH
            mController.startAppropriateProvisioning(
                    mIntent, ADDITIONAL_EXTRAS_DEFAULT, TEST_CALLING_PACKAGE);
        });

        // THEN start profile provisioning
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUi).onParamsValidated(any());
        verify(mUi).startRoleHolderUpdater(false);
        verify(mUi).startRoleHolderProvisioning(intentArgumentCaptor.capture());
        assertThat(intentArgumentCaptor.getValue().hasExtra(EXTRA_ROLE_HOLDER_STATE)).isFalse();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_withoutRoleHolderUpdaterAndNoRoleHolderInstalled_startsPlatformProvidedProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_CONFIGURED,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).initiateUi(any(UiParams.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testFinancedDevice_provisioningStarted()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER);
        // GIVEN an intent to provision a managed profile
        prepareMocksForFinancedDeviceIntent();
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start financed device provisioning
        verify(mUi).onParamsValidated(any());
        verify(mUi).prepareFinancedDeviceFlow(any(ProvisioningParams.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_roleHolderRequestedUpdate_restartsWithProvidedState()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);

            // simulate that the updater has run and returned back, starting DMRH
            mController.startAppropriateProvisioning(
                    mIntent, ADDITIONAL_EXTRAS_DEFAULT, TEST_CALLING_PACKAGE);

            // simulate that DMRH has returned RESULT_UPDATE_ROLE_HOLDER with state
            mController.startRoleHolderUpdater(
                    /* isRoleHolderRequestedUpdate= */ true, ROLE_HOLDER_STATE);

            // simulate that DMRH updater updated DMRH and now it restarts it with
            // state returned before
            mController.startAppropriateProvisioning(
                    mIntent, ADDITIONAL_EXTRAS_SUCCESSFUL_UPDATE, TEST_CALLING_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(any());
        verify(mUi).startRoleHolderUpdater(false);
        verify(mUi).startRoleHolderUpdater(true);
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUi, times(2)).startRoleHolderProvisioning(intentArgumentCaptor.capture());
        Intent resultIntent = intentArgumentCaptor.getAllValues().get(1);
        assertBundlesEqual(
                resultIntent.getParcelableExtra(EXTRA_ROLE_HOLDER_STATE),
                ROLE_HOLDER_STATE);
        assertThat(resultIntent.getIntExtra(EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE, RESULT_CANCELED))
                .isEqualTo(RESULT_OK);
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_roleHolderRequestedUpdate_updateFailsOnce_restartsWithProvidedState()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderValidAndInstalledWithUpdater(
                ROLE_HOLDER_UPDATER_HELPER);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);

            // simulate that the updater has run and returned back, starting DMRH
            mController.startAppropriateProvisioning(
                    mIntent, ADDITIONAL_EXTRAS_DEFAULT, TEST_CALLING_PACKAGE);

            // simulate that DMRH has returned RESULT_UPDATE_ROLE_HOLDER with state
            mController.startRoleHolderUpdater(
                    /* isRoleHolderRequestedUpdate= */ true, ROLE_HOLDER_STATE);

            // simulate that DMRH updater failed and starts again with last state
            mController.startRoleHolderUpdaterWithLastState(
                    /* isRoleHolderRequestedUpdate= */ true);

            // simulate that DMRH updater updated DMRH and now it restarts it with
            // state returned before
            mController.startAppropriateProvisioning(
                    mIntent, ADDITIONAL_EXTRAS_SUCCESSFUL_UPDATE, TEST_CALLING_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(any());
        ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(mUi, times(3)).startRoleHolderUpdater(booleanArgumentCaptor.capture());
        assertThat(booleanArgumentCaptor.getAllValues().get(0)).isFalse();
        assertThat(booleanArgumentCaptor.getAllValues().get(1)).isTrue();
        assertThat(booleanArgumentCaptor.getAllValues().get(2)).isTrue();
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mUi, times(2)).startRoleHolderProvisioning(intentArgumentCaptor.capture());
        Intent resultIntent = intentArgumentCaptor.getAllValues().get(1);
        assertBundlesEqual(
                resultIntent.getParcelableExtra(EXTRA_ROLE_HOLDER_STATE),
                ROLE_HOLDER_STATE);
        assertThat(resultIntent.getIntExtra(EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE, RESULT_CANCELED))
                .isEqualTo(RESULT_OK);
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_roleHolderReady_startsRoleHolderProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).startRoleHolderProvisioning(any(Intent.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_allowOffline_startsPlatformProvidedProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(ALLOW_OFFLINE_PARAMS);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(ALLOW_OFFLINE_PARAMS);
        verify(mUi).initiateUi(any(UiParams.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_roleHolderNotConfigured_startsPlatformProvidedProvisioning()
            throws Exception {
        enableRoleHolderDelegation();
        mController = createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_CONFIGURED,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_DEFINED);
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).initiateUi(any(UiParams.class));
        verifyNoMoreInteractions(mUi);
        verify(mSharedPreferences).setIsProvisioningFlowDelegatedToRoleHolder(false);
    }

    @Test
    public void testManagedProfile_provisioningNotAllowed() throws Exception {
        // GIVEN an intent to provision a managed profile, but provisioning mode is not allowed
        prepareMocksForManagedProfileIntent(false);
        when(mDevicePolicyManager.checkProvisioningPrecondition(
                ACTION_PROVISION_MANAGED_PROFILE, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_MANAGED_USERS_NOT_SUPPORTED);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContentInterface.call(anyString(), anyString(), any(), any()))
                .thenReturn(GET_DEVICE_NAME_BUNDLE);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(
                eq(LazyStringResource.of(R.string.cant_add_work_profile)),
                eq(LazyStringResource.of(
                        R.string.work_profile_cant_be_added_contact_admin, DEFAULT_DEVICE_NAME)),
                any());
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_nullCallingPackage() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null);
        // THEN error is shown
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any(String.class));
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_invalidCallingPackage() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, "com.android.invalid.dpc");
        // THEN error is shown
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any(String.class));
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_withEncryption() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating managed profile provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        verify(mUi).onParamsValidated(mParams);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // THEN show encryption screen
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_afterEncryption() throws Exception {
        // GIVEN managed profile provisioning continues after successful encryption. In this case
        // we don't set the startedByTrustedSource flag.
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_PROFILE, false);
        // WHEN initiating with a continuation intent
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, MP_PACKAGE_NAME));
        verify(mUi).onParamsValidated(mParams);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.continueProvisioningAfterUserConsent());
        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).startProvisioning(mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_badLauncher() throws Exception {
        // GIVEN that the current launcher does not support managed profiles
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.currentLauncherSupportsManagedProfiles(mContext)).thenReturn(false);
        // WHEN initiating managed profile provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        verify(mUi).onParamsValidated(mParams);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show a dialog indicating that the current launcher is invalid
        verify(mUi).showCurrentLauncherInvalid();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_wrongPackage() throws Exception {
        // GIVEN that the provisioning intent tries to set a package different from the caller
        // as owner of the profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, TEST_BOGUS_PACKAGE);
        // THEN show an error dialog and do not continue
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any());
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_frp() throws Exception {
        // GIVEN managed profile provisioning is invoked from SUW with FRP active
        prepareMocksForManagedProfileIntent(false);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContentInterface.call(anyString(), anyString(), any(), any()))
                .thenReturn(GET_DEVICE_NAME_BUNDLE);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        // THEN show an error dialog and do not continue
        verify(mUi).showErrorAndClose(
                eq(LazyStringResource.of(R.string.cant_set_up_device)),
                eq(LazyStringResource.of(R.string.device_has_reset_protection_contact_admin,
                        DEFAULT_DEVICE_NAME)),
                any());
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testCheckFactoryResetProtection_skipFrp() throws Exception {
        // GIVEN managed profile provisioning is invoked from SUW with FRP active
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(false);
        // setting the data block size to any number greater than 0 to simulate FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // GIVEN there is a persistent data package.
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(anyInt())).thenReturn("test.persistent.data");
        // GIVEN the persistent data package is a system app.
        PackageInfo packageInfo = new PackageInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        packageInfo.applicationInfo = applicationInfo;
        when(mPackageManager.getPackageInfo(eq("test.persistent.data"), anyInt()))
                .thenReturn(packageInfo);

        // WHEN factory reset protection is checked for trusted source device provisioning.
        ProvisioningParams provisioningParams = createParams(true, false, null,
                ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE, TEST_MDM_PACKAGE);
        boolean result = mController.checkFactoryResetProtection(
                provisioningParams, "test.persistent.data");

        // THEN the check is successful despite the FRP data presence.
        assertThat(result).isTrue();
    }

    @Test
    public void testManagedProfile_skipEncryption() throws Exception {
        // GIVEN an intent to provision a managed profile with skip encryption
        prepareMocksForManagedProfileIntent(true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        verify(mUi).onParamsValidated(mParams);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.continueProvisioningAfterUserConsent());
        // THEN start profile provisioning
        verify(mUi).onParamsValidated(mParams);
        verify(mUi).startProvisioning(mParams);
        verify(mUi, never()).requestEncryption(any(ProvisioningParams.class));
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    @Test
    public void testManagedProfile_encryptionNotSupported() throws Exception {
        // GIVEN an intent to provision a managed profile on an unencrypted device that does not
        // support encryption
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        when(mDevicePolicyManager.getStorageEncryptionStatus())
                .thenReturn(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContentInterface.call(anyString(), anyString(), any(), any()))
                .thenReturn(GET_DEVICE_NAME_BUNDLE);
        // WHEN initiating provisioning
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
        verify(mUi).onParamsValidated(mParams);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // THEN show an error indicating that this device does not support encryption
        verify(mUi).showErrorAndClose(
                eq(LazyStringResource.of(R.string.cant_set_up_device)),
                eq(LazyStringResource.of(R.string.device_doesnt_allow_encryption_contact_admin,
                        DEFAULT_DEVICE_NAME)),
                any());
        verifyNoMoreInteractions(mUi);
    }

    // TODO(b/177575786): Migrate outdated PreProvisioningControllerTest tests to robolectric
    /*
        @Test
public void testNfc_afterEncryption() throws Exception {
        // GIVEN provisioning was started via an NFC tap and we have gone through encryption
        // in this case the device gets resumed with the DO intent and startedByTrustedSource flag
        // set
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_DEVICE, true);
        // WHEN continuing NFC provisioning after encryption
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testQr() throws Exception {
        // GIVEN provisioning was started via a QR code and device is already encrypted
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testQr_skipEncryption() throws Exception {
        // GIVEN provisioning was started via a QR code with encryption skipped
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any());
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testQr_withEncryption() throws Exception {
        // GIVEN provisioning was started via a QR code with encryption necessary
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show encryption screen
        verifyInitiateDeviceOwnerUi();
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testQr_frp() throws Exception {
        // GIVEN provisioning was started via a QR code, but the device is locked with FRP
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testDeviceOwner() throws Exception {
        // GIVEN device owner provisioning was started and device is already encrypted
        prepareMocksForDoIntent(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testDeviceOwner_skipEncryption() throws Exception {
        // GIVEN device owner provisioning was started with skip encryption flag
        prepareMocksForDoIntent(true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any());
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    // TODO: There is a difference in behaviour here between the managed profile and the device
    // owner case: In managed profile case, we invoke encryption after user clicks next, but in
    // device owner mode we invoke it straight away. Also in theory no need to update
    // the UI elements if we're moving away from this activity straight away.
        @Test
public void testDeviceOwner_withEncryption() throws Exception {
        // GIVEN device owner provisioning is started with encryption needed
        prepareMocksForDoIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN update the UI elements and show encryption screen
        verifyInitiateDeviceOwnerUi();
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testDeviceOwner_afterEncryption() throws Exception {
        // GIVEN device owner provisioning is continued after encryption. In this case we do not set
        // the startedByTrustedSource flag.
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_DEVICE, false);
        // WHEN provisioning is continued
        mController.initiateProvisioning(mIntent, null, null);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

        @Test
public void testDeviceOwner_frp() throws Exception {
        // GIVEN device owner provisioning is invoked with FRP active
        prepareMocksForDoIntent(false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }*/

    @Test
    public void testParamsNotLoaded_throwsException() {
        assertThrows(IllegalStateException.class, () -> mController.getParams());
    }

    @Test
    public void testInitiateProvisioning_showsWifiPicker() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .build();
        initiateProvisioning(params);
        verify(mUi).requestWifiPick();
    }

    @Test
    public void testInitiateProvisioning_useMobileData_showsWifiPicker() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setUseMobileData(true)
                .build();
        initiateProvisioning(params);
        verify(mUi).requestWifiPick();
    }

    @Test
    public void testInitiateProvisioning_useMobileData_noWifiPicker() {
        when(mUtils.isMobileNetworkConnectedToInternet(mContext)).thenReturn(true);
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setUseMobileData(true)
                .build();
        initiateProvisioning(params);
        verify(mUi, never()).requestWifiPick();
    }

    @Test
    public void testInitiateProvisioning_connectedToWifiOrEthernet_noWifiPicker() {
        when(mUtils.getActiveNetworkCapabilities(any())).thenReturn(new NetworkCapabilities());
        when(mUtils.isNetworkConnectedToInternetViaWiFi(any())).thenReturn(true);

        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .build();
        initiateProvisioning(params);
        verify(mUi, never()).requestWifiPick();
    }

    @Test
    public void testInitiateProvisioning_noAdminDownloadInfo_noWifiPicker() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setDeviceAdminDownloadInfo(null)
                .build();
        initiateProvisioning(params);
        verify(mUi, never()).requestWifiPick();
    }

    @Test
    public void testInitiateProvisioning_wifiInfo_noWifiPicker() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setWifiInfo(new WifiInfo.Builder().setSsid(TEST_WIFI_SSID).build())
                .build();
        initiateProvisioning(params);
        verify(mUi, never()).requestWifiPick();
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_orgDevice_exactExtras() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.size()).isEqualTo(5);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_orgDevice_imeiPassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getString(EXTRA_PROVISIONING_IMEI)).isEqualTo(TEST_IMEI);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_orgDevice_serialNumberPassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SERIAL_NUMBER)).isTrue();
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_nonOrgDevice_adminBundlePassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_MANAGED_PROFILE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.keySet()).contains(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        assertThat((PersistableBundle) bundle.getParcelable(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
                .isEqualTo(TEST_ADMIN_BUNDLE);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_nonOrgDevice_allowedModesPassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_MANAGED_PROFILE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getIntegerArrayList(EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES))
                .containsExactly(PROVISIONING_MODE_MANAGED_PROFILE);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_nonOrgDevice_hasExactlyTwoExtras() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_MANAGED_PROFILE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.size()).isEqualTo(2);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_orgDevice_adminBundlePassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat((PersistableBundle) bundle.getParcelable(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
                .isEqualTo(TEST_ADMIN_BUNDLE);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManagedDevice_adminBundlePassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_FULLY_MANAGED_DEVICE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.keySet()).contains(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        assertThat((PersistableBundle) bundle.getParcelable(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
                .isEqualTo(TEST_ADMIN_BUNDLE);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManagedDevice_exactExtras() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.size()).isEqualTo(5);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManagedDevice_imeiPassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getString(EXTRA_PROVISIONING_IMEI)).isEqualTo(TEST_IMEI);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManagedDevice_serialNumberPassed() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SERIAL_NUMBER)).isTrue();
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManaged_hasOptOutExtra() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE,
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getBoolean(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManaged_optOutExtraIsTrue() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .setDeviceOwnerPermissionGrantOptOut(true)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getBoolean(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(true);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_managedProfile_optOutExtraIsFalseByDefault() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_MANAGED_PROFILE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_fullyManaged_optOutExtraIsFalse() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(true)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .setDeviceOwnerPermissionGrantOptOut(false)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.getBoolean(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_managedProfileByo_optOutExtraNotPresent() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(
                                List.of(PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_managedProfile_optOutExtraNotPresent() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(List.of(PROVISIONING_MODE_MANAGED_PROFILE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .setDeviceOwnerPermissionGrantOptOut(true)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testGetAdditionalExtrasForGetProvisioningModeIntent_managedProfileByo_optOutExtraHasNoEffect() {
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setIsOrganizationOwnedProvisioning(false)
                .setAdminExtrasBundle(TEST_ADMIN_BUNDLE)
                .setAllowedProvisioningModes(
                        new ArrayList<>(
                                List.of(PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE)))
                .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .setDeviceOwnerPermissionGrantOptOut(true)
                .build();
        initiateProvisioning(params);

        Bundle bundle = mController.getAdditionalExtrasForGetProvisioningModeIntent();

        assertThat(bundle.containsKey(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT))
                .isEqualTo(false);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileModeWithAccountMigratedExtraTrue_setsParamToTrue() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, true);
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().keepAccountMigrated).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileModeWithAccountMigratedExtraFalse_setsParamToFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, false);
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().keepAccountMigrated).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileMode_accountMigratedIsFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().keepAccountMigrated).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedDeviceModeWithAccountMigratedExtraTrue_accountMigratedIsFalse() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, true);
        final ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().keepAccountMigrated).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileModeWithLeaveSystemAppsEnabledTrue_setsParamToTrue() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true);
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().leaveAllSystemAppsEnabled).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileModeWithLeaveSystemAppsEnabledFalse_setsParamToFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, false);
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().leaveAllSystemAppsEnabled).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedProfileMode_leaveSystemAppsEnabledIsFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().leaveAllSystemAppsEnabled).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_managedDeviceModeWithLeaveSystemAppsEnabledTrue_paramIsFalse() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true);
        final ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().leaveAllSystemAppsEnabled).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_adminBundlePassed_setsParam() {
        PersistableBundle testAdminExtrasBundle = new PersistableBundle();
        testAdminExtrasBundle.putInt("key1", 2);
        testAdminExtrasBundle.putString("key2", "value2");
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, testAdminExtrasBundle);
        final ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().adminExtrasBundle).isEqualTo(testAdminExtrasBundle);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_adminBundlePassedWithPreexistingAdminBundle_appendsValues() {
        PersistableBundle resultingAdminBundle = new PersistableBundle();
        resultingAdminBundle.putInt("key1", 2);
        resultingAdminBundle.putInt("someKey", 124);
        PersistableBundle existingAdminBundle = new PersistableBundle();
        existingAdminBundle.putInt("key2", 3);
        existingAdminBundle.putInt("someKey", 123);
        PersistableBundle expectedResult = new PersistableBundle();
        expectedResult.putInt("key1", 2);
        expectedResult.putInt("key2", 3);
        expectedResult.putInt("someKey", 124);
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, resultingAdminBundle);
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setAdminExtrasBundle(existingAdminBundle)
                .setKeepAccountMigrated(false)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE
                )))
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().adminExtrasBundle.toString())
                .isEqualTo(expectedResult.toString());
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noAdminBundleResult_existingAdminBundleRetained() {
        PersistableBundle existingAdminBundle = new PersistableBundle();
        existingAdminBundle.putInt("key2", 3);
        existingAdminBundle.putInt("someKey", 123);
        Intent resultIntent = createResultIntentWithManagedProfile();
        final ProvisioningParams params = createProvisioningParamsBuilderForInitiateProvisioning()
                .setAdminExtrasBundle(existingAdminBundle)
                .setKeepAccountMigrated(false)
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE
                )))
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().adminExtrasBundle.toString())
                .isEqualTo(existingAdminBundle.toString());
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_validDisclaimersWithWorkProfile_works() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS, DISCLAIMERS_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().disclaimersParam).isEqualTo(DISCLAIMERS_PARAM);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_validDisclaimersWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS, DISCLAIMERS_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().disclaimersParam).isEqualTo(DISCLAIMERS_PARAM);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noDisclaimersSet_isNull() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().disclaimersParam).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingDisclaimers_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS, DISCLAIMERS_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setDisclaimersParam(OTHER_DISCLAIMERS_PARAM)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().disclaimersParam).isEqualTo(DISCLAIMERS_PARAM);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_validLocaleWithWorkProfile_ignored() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_LOCALE, LOCALE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().locale).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_validLocaleWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LOCALE, LOCALE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().locale).isEqualTo(LOCALE);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noLocaleSet_isNull() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().locale).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingLocale_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LOCALE, LOCALE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setLocale(OTHER_LOCALE)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().locale).isEqualTo(LOCALE);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_invalidLocale_ignored() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LOCALE, INVALID_LOCALE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().locale).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_localTimeWithWorkProfile_ignored() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_LOCAL_TIME, LOCAL_TIME_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().localTime).isEqualTo(DEFAULT_LOCAL_TIME);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_localTimeWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LOCAL_TIME, LOCAL_TIME_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().localTime).isEqualTo(LOCAL_TIME_EXTRA);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noLocalTimeSet_isDefaultLocalTime() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().localTime).isEqualTo(DEFAULT_LOCAL_TIME);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingLocalTime_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_LOCAL_TIME, LOCAL_TIME_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setLocalTime(OTHER_LOCAL_TIME)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().localTime).isEqualTo(LOCAL_TIME_EXTRA);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_timeZoneWithWorkProfile_ignored() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_TIME_ZONE, TIME_ZONE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().timeZone).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_timeZoneWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_TIME_ZONE, TIME_ZONE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().timeZone).isEqualTo(TIME_ZONE_EXTRA);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noTimeZoneSet_isNull() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().timeZone).isNull();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingTimeZone_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_TIME_ZONE, TIME_ZONE_EXTRA);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setTimeZone(OTHER_TIME_ZONE)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().timeZone).isEqualTo(TIME_ZONE_EXTRA);
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_skipEncryptionWithWorkProfile_works() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, /* value= */ true);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().skipEncryption).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_skipEncryptionWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, /* value= */ true);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().skipEncryption).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noSkipEncryptionSet_isFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().skipEncryption).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingSkipEncryption_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setSkipEncryption(false)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().skipEncryption).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_deviceOwnerPermissionGrantOptOutWithWorkProfile_ignored() {
        Intent resultIntent = createResultIntentWithManagedProfile()
                .putExtra(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT, /* value= */ true);
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().deviceOwnerPermissionGrantOptOut).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_deviceOwnerPermissionGrantOptOutWithDeviceOwner_works() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT, /* value= */ true);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().deviceOwnerPermissionGrantOptOut).isTrue();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_noDeviceOwnerPermissionGrantOptOutSet_isFalse() {
        Intent resultIntent = createResultIntentWithManagedProfile();
        ProvisioningParams params = createProvisioningParamsBuilderForManagedProfile()
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().deviceOwnerPermissionGrantOptOut).isFalse();
    }

    @Test
    public void testUpdateProvisioningParamsFromIntent_withPreExistingDeviceOwnerPermissionGrantOptOut_replaced() {
        Intent resultIntent = createResultIntentWithFullyManagedDevice()
                .putExtra(EXTRA_PROVISIONING_SENSORS_PERMISSION_GRANT_OPT_OUT, /* value= */ true);
        ProvisioningParams params = createProvisioningParamsBuilderForFullyManagedDevice()
                .setDeviceOwnerPermissionGrantOptOut(false)
                .build();
        initiateProvisioning(params);

        mController.updateProvisioningParamsFromIntent(resultIntent);

        assertThat(mController.getParams().deviceOwnerPermissionGrantOptOut).isTrue();
    }

    @Test
    public void testInitiateProvisioning_withActionProvisionManagedDevice_failsSilently()
            throws Exception {
        prepareMocksForDoIntent(/* skipEncryption= */ false);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE);
        });

        verify(mUi, never()).initiateUi(any());
        verify(mUi).abortProvisioning();
        verifyNoMoreInteractions(mUi);
    }
    private static Parcelable[] createDisclaimersExtra() {
        Bundle disclaimer = new Bundle();
        disclaimer.putString(
                EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER);
        disclaimer.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, DISCLAIMER_CONTENT_URI);
        return new Parcelable[]{ disclaimer };
    }

    private ProvisioningParams.Builder createProvisioningParamsBuilderForInitiateProvisioning() {
        return createProvisioningParamsBuilder()
                .setDeviceAdminDownloadInfo(PACKAGE_DOWNLOAD_INFO);
    }

    private void prepareMocksForManagedProfileIntent(ProvisioningParams params) throws Exception {
        final String action = ACTION_PROVISION_MANAGED_PROFILE;
        when(mIntent.getAction()).thenReturn(action);
        when(mUtils.findDeviceAdmin(TEST_MDM_PACKAGE, null, mContext, UserHandle.myUserId()))
                .thenReturn(TEST_MDM_COMPONENT_NAME);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(true);
        when(mDevicePolicyManager.checkProvisioningPrecondition(action, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(params);
    }

    private void prepareMocksForManagedProfileIntent(boolean skipEncryption) throws Exception {
        final String action = ACTION_PROVISION_MANAGED_PROFILE;
        when(mIntent.getAction()).thenReturn(action);
        when(mUtils.findDeviceAdmin(TEST_MDM_PACKAGE, null, mContext, UserHandle.myUserId()))
                .thenReturn(TEST_MDM_COMPONENT_NAME);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(true);
        when(mDevicePolicyManager.checkProvisioningPrecondition(action, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(false, skipEncryption, null, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForFinancedDeviceIntent() throws Exception {
        final String action = ACTION_PROVISION_FINANCED_DEVICE;
        when(mIntent.getAction()).thenReturn(action);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityViaTrustedApp"));
        when(mUtils.findDeviceAdmin(TEST_MDM_PACKAGE, null, mContext, UserHandle.myUserId()))
                .thenReturn(TEST_MDM_COMPONENT_NAME);
        when(mDevicePolicyManager.checkProvisioningPrecondition(action, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(false, false, null, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForTrustedSourceIntent(ProvisioningParams params) throws Exception {
        when(mIntent.getAction())
                .thenReturn(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityViaTrustedApp"));
        when(mDevicePolicyManager.checkProvisioningPrecondition(anyString(), eq(TEST_MDM_PACKAGE)))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(params);
    }

    private void prepareMocksForDoIntent(boolean skipEncryption) throws Exception {
        final String action = ACTION_PROVISION_MANAGED_DEVICE;
        when(mIntent.getAction()).thenReturn(action);
        when(mDevicePolicyManager.checkProvisioningPrecondition(action, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(false, skipEncryption, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForAfterEncryption(String action, boolean startedByTrustedSource)
            throws Exception {
        when(mIntent.getAction()).thenReturn(ACTION_RESUME_PROVISIONING);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityAfterEncryption"));
        when(mDevicePolicyManager.checkProvisioningPrecondition(action, TEST_MDM_PACKAGE))
                .thenReturn(STATUS_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(
                        startedByTrustedSource, false, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private ProvisioningParams.Builder createParamsBuilder(
            boolean startedByTrustedSource, boolean skipEncryption,
            String wifiSsid, String action, String packageName) {
        ProvisioningParams.Builder builder = ProvisioningParams.Builder.builder()
                .setStartedByTrustedSource(startedByTrustedSource)
                .setSkipEncryption(skipEncryption)
                .setProvisioningAction(action)
                .setDeviceAdminPackageName(packageName);
        if (!TextUtils.isEmpty(wifiSsid)) {
            builder.setWifiInfo(WifiInfo.Builder.builder().setSsid(wifiSsid).build());
        }
        return builder;
    }

    private ProvisioningParams createParams(boolean startedByTrustedSource, boolean skipEncryption,
            String wifiSsid, String action, String packageName) {
        return mParams = createParamsBuilder(
                startedByTrustedSource, skipEncryption, wifiSsid, action, packageName).build();
    }

    private void verifyInitiateProfileOwnerUi() {
        verify(mUi).initiateUi(any());
    }

    private void verifyInitiateDeviceOwnerUi() {
        verify(mUi).initiateUi(any());
    }

    private ProvisioningParams.Builder createProvisioningParamsBuilder() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setStartedByTrustedSource(true)
                .setDeviceAdminComponentName(TEST_MDM_COMPONENT_NAME);
    }

    private void initiateProvisioning(ProvisioningParams params) {
        try {
            when(mMessageParser.parse(any(Intent.class))).thenReturn(params);
        } catch (IllegalProvisioningArgumentException e) {
            // will never happen
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mController.initiateProvisioning(mIntent, TEST_MDM_PACKAGE));
    }

    private ProvisioningParams.Builder createProvisioningParamsBuilderForFullyManagedDevice() {
        return createProvisioningParamsBuilderForInitiateProvisioning()
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )));
    }

    private ProvisioningParams.Builder createProvisioningParamsBuilderForManagedProfile() {
        return createProvisioningParamsBuilderForInitiateProvisioning()
                .setAllowedProvisioningModes(new ArrayList<>(List.of(
                        PROVISIONING_MODE_MANAGED_PROFILE
                )));
    }

    private Intent createResultIntentWithManagedProfile() {
        return new Intent()
                .putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_MANAGED_PROFILE);
    }

    private Intent createResultIntentWithFullyManagedDevice() {
        return new Intent()
                .putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
    }

    private PreProvisioningActivityController createControllerWithRoleHolderUpdaterInstalled() {
        return createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER);
    }

    private PreProvisioningActivityController
    createControllerWithRoleHolderValidAndInstalledWithUpdater(
            DeviceManagementRoleHolderUpdaterHelper updaterHelper) {
        return createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER,
                updaterHelper);
    }

    private PreProvisioningActivityController createControllerWithRoleHolderUpdaterNotPresent() {
        return createControllerWithRoleHolderHelpers(
                DEVICE_MANAGEMENT_ROLE_HOLDER_HELPER_NOT_PRESENT,
                ROLE_HOLDER_UPDATER_HELPER_UPDATER_NOT_INSTALLED);
    }

    private static PersistableBundle createRoleHolderStateBundle() {
        PersistableBundle result = new PersistableBundle();
        result.putString("key1", "value1");
        result.putInt("key2", 2);
        result.putBoolean("key3", true);
        return result;
    }

    private static FeatureFlagChecker createFeatureFlagChecker() {
        return () -> sDelegateProvisioningToRoleHolderEnabled;
    }

    private void enableRoleHolderDelegation() {
        sDelegateProvisioningToRoleHolderEnabled = true;
    }

    private void disableRoleHolderDelegation() {
        sDelegateProvisioningToRoleHolderEnabled = false;
    }

    private static Bundle createSuccessfulUpdateAdditionalExtras() {
        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_ROLE_HOLDER_UPDATE_RESULT_CODE, RESULT_OK);
        return bundle;
    }
}
