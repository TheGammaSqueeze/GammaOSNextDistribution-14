/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.model;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ORGANIZATION_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORT_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_USE_MOBILE_DATA;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_CLOUD_ENROLLMENT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_MANAGED_ACCOUNT;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;

import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences.DEFAULT_PROVISIONING_ID;
import static com.android.managedprovisioning.common.StoreUtils.accountToPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.getObjectAttrFromPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.getStringAttrFromPersistableBundle;
import static com.android.managedprovisioning.common.StoreUtils.putPersistableBundlableIfNotNull;

import static java.util.Objects.requireNonNull;

import android.accounts.Account;
import android.annotation.IntDef;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.util.AtomicFile;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.internal.util.FastXmlSerializer;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.PersistableBundlable;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Provisioning parameters for Device Owner and Profile Owner provisioning.
 */
public final class ProvisioningParams extends PersistableBundlable {
    public static final long DEFAULT_LOCAL_TIME = -1;
    public static final boolean DEFAULT_STARTED_BY_TRUSTED_SOURCE = false;
    public static final boolean DEFAULT_IS_NFC = false;
    public static final boolean DEFAULT_IS_QR_PROVISIONING = false;
    public static final boolean DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_KEEP_ACCOUNT_MIGRATED = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_USE_MOBILE_DATA = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_IS_ORGANIZATION_OWNED = false;
    public static final ArrayList<Integer> DEFAULT_EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES =
            new ArrayList<>();
    public static final int DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES = 0;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_PERMISSION_GRANT_OPT_OUT = false;
    public static final boolean DEFAULT_EXTRA_ALLOW_OFFLINE = false;
    public static final boolean DEFAULT_EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT = false;


    // Intent extra used internally for passing data between activities and service.
    public static final String EXTRA_PROVISIONING_PARAMS = "provisioningParams";

    public static final int FLOW_TYPE_UNSPECIFIED = 0;
    public static final int FLOW_TYPE_LEGACY = 1;
    public static final int FLOW_TYPE_ADMIN_INTEGRATED = 2;

    @IntDef(prefix = { "PROVISIONING_TRIGGER_" }, value = {
            PROVISIONING_TRIGGER_UNSPECIFIED,
            PROVISIONING_TRIGGER_CLOUD_ENROLLMENT,
            PROVISIONING_TRIGGER_QR_CODE,
            PROVISIONING_TRIGGER_MANAGED_ACCOUNT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProvisioningTrigger {}

    @IntDef(prefix = { "FLOW_TYPE_" }, value = {
            FLOW_TYPE_UNSPECIFIED,
            FLOW_TYPE_LEGACY,
            FLOW_TYPE_ADMIN_INTEGRATED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlowType {}

    private static final String TAG_PROVISIONING_ID = "provisioning-id";
    private static final String TAG_PROVISIONING_PARAMS = "provisioning-params";
    private static final String TAG_WIFI_INFO = "wifi-info";
    private static final String TAG_PACKAGE_DOWNLOAD_INFO = "download-info";
    private static final String TAG_STARTED_BY_TRUSTED_SOURCE = "started-by-trusted-source";
    private static final String TAG_IS_NFC = "started-is-nfc";
    private static final String TAG_IS_QR_PROVISIONING = "is-qr-provisioning";
    private static final String TAG_PROVISIONING_ACTION = "provisioning-action";
    private static final String TAG_IS_ORGANIZATION_OWNED_PROVISIONING =
            "is-organization-owned-provisioning";
    private static final String TAG_ALLOWED_PROVISIONING_MODES =
            "allowed-provisioning-modes";
    private static final String TAG_INITIATOR_REQUESTED_PROVISIONING_MODES =
            "initiator-requested-provisioning-modes";
    private static final String TAG_FLOW_TYPE = "flow-type";
    private static final String TAG_ALLOW_PROVISIONING_AFTER_USER_SETUP_COMPLETE =
            "allow-provisioning-after-user-setup-complete";
    private static final String TAG_PROVISIONING_TRIGGER = "provisioning-trigger";
    private static final String TAG_SKIP_OWNERSHIP_DISCLAIMER = "skip-ownership-disclaimer";
    private static final String TAG_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE =
            "provisioning-return-before-policy-compliance";
    private static final String TAG_DEVICE_OWNER_PERMISSION_GRANT_OPT_OUT =
            "device-owner-opt-out-of-permission-grants";
    private static final String TAG_ALLOW_OFFLINE = "allow-offline";
    private static final String TAG_ROLE_HOLDER_PACKAGE_DOWNLOAD_INFO =
            "role-holder-download-info";
    private static final String TAG_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT =
            "provisioning-should-launch-result-intent";

    public static final Parcelable.Creator<ProvisioningParams> CREATOR
            = new Parcelable.Creator<ProvisioningParams>() {
        @Override
        public ProvisioningParams createFromParcel(Parcel in) {
            return new ProvisioningParams(in);
        }

        @Override
        public ProvisioningParams[] newArray(int size) {
            return new ProvisioningParams[size];
        }
    };

    public final long provisioningId;

    @Nullable
    public final String timeZone;

    public final long localTime;

    @Nullable
    public final Locale locale;

    /** WiFi configuration. */
    @Nullable
    public final WifiInfo wifiInfo;

    public final boolean useMobileData;

    /**
     * Package name of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     * <p>
     * In most cases, it is preferable to access the admin package name using
     * {@link #inferDeviceAdminPackageName}.
     */
    @Deprecated
    @Nullable
    public final String deviceAdminPackageName;

    /**
     * {@link ComponentName} of the device admin package.
     *
     * <p>At least one one of deviceAdminPackageName and deviceAdminComponentName should be
     * non-null.
     * <p>
     * In most cases, it is preferable to access the admin component name using
     * {@link #inferDeviceAdminComponentName(Utils, Context, int)} .
     */
    @Nullable
    public final ComponentName deviceAdminComponentName;

    public final String organizationName;
    public final String supportUrl;

    /** {@link Account} that should be migrated to the managed profile. */
    @Nullable
    public final Account accountToMigrate;

    /** True if the account will not be removed from the calling user after it is migrated. */
    public final boolean keepAccountMigrated;

    /** Provisioning action comes along with the provisioning data. */
    public final String provisioningAction;

    /** The download information of device admin package. */
    @Nullable
    public final PackageDownloadInfo deviceAdminDownloadInfo;

    /** List of disclaimers */
    @Nullable
    public final DisclaimersParam disclaimersParam;

    /**
     * Custom key-value pairs from enterprise mobility management which are passed to device admin
     * package after provisioning.
     *
     * <p>Note that {@link ProvisioningParams} is not immutable because this field is mutable.
     */
    @Nullable
    public final PersistableBundle adminExtrasBundle;

    /**
     * True iff provisioning flow was started by a trusted app. This includes Nfc bump and QR code.
     */
    public final boolean startedByTrustedSource;

    public final boolean isNfc;

    public final boolean isQrProvisioning;

    /** True if all system apps should be enabled after provisioning. */
    public final boolean leaveAllSystemAppsEnabled;

    /** True if device encryption should be skipped. */
    public final boolean skipEncryption;

    public final boolean skipEducationScreens;

    /**
     * True if the provisioning is done on a device owned by the organization.
     *
     * <p>For the admin-integrated flow, this field is only set after the admin app
     * picks a provisioning mode.
     */
    public final boolean isOrganizationOwnedProvisioning;

    /**
     * {@link ArrayList} of {@link Integer} containing a subset of {{@link
     * DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE}, {@link
     * DevicePolicyManager#PROVISIONING_MODE_FULLY_MANAGED_DEVICE}, {@link
     * DevicePolicyManager#PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE}}.
     **/
    public final ArrayList<Integer> allowedProvisioningModes;

    /**
     * Integer specifying what provisioning modes have been specified by the provisioning
     * initiator.
     *
     * <p>Can be a combination of {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED}, {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_PERSONALLY_OWNED}, {@link
     * DevicePolicyManager#FLAG_SUPPORTED_MODES_DEVICE_OWNER} or if not relevant to the
     * admin-integrated flow, {@link ProvisioningParams#DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES}.
     *
     */
    public final int initiatorRequestedProvisioningModes;

    /** True if provisioning after user setup complete should be allowed. */
    public final boolean allowProvisioningAfterUserSetupComplete;

    /**
     * The type of flow to be performed.
     * <p>Must be one of {@link #FLOW_TYPE_UNSPECIFIED}, {@link #FLOW_TYPE_LEGACY} or {@link
     * #FLOW_TYPE_ADMIN_INTEGRATED}.
     **/
    public final @FlowType int flowType;

    /**
     * The way provisioning was started.
     * <p>Can be one of {@link DevicePolicyManager#PROVISIONING_TRIGGER_CLOUD_ENROLLMENT}, {@link
     * DevicePolicyManager#PROVISIONING_TRIGGER_QR_CODE}, {@link
     * DevicePolicyManager#PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER} or {@link
     * DevicePolicyManager#PROVISIONING_TRIGGER_UNSPECIFIED}.
     */
    public final @ProvisioningTrigger int provisioningTrigger;

    /**
     * Whether to skip the ownership disclaimer.
     */
    public final boolean skipOwnershipDisclaimer;

    /**
     * True if the provisioning flow should return before starting the admin app's {@link
     * DevicePolicyManager#ACTION_ADMIN_POLICY_COMPLIANCE} handler. Default value is {@code true}.
     */
    public final boolean returnBeforePolicyCompliance;

    /**
     * True if the device owner has opted out of controlling permission grants for
     * sensors-related permissions. See {@link DevicePolicyManager#setPermissionGrantState}
     */
    public final boolean deviceOwnerPermissionGrantOptOut;


    /**
     * {@code true} if offline provisioning is allowed
     *
     * <p>In T this can also be used to force platform side provisioning.
     *
     * @see DevicePolicyManager#EXTRA_PROVISIONING_ALLOW_OFFLINE
     */
    public final boolean allowOffline;

    /**
     * {@code true} if provisioning should launch the result intent returned by the
     * device manager role holder.
     *
     * {@code false} by default.
     *
     * @see DevicePolicyManager#EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT
     */
    public final boolean provisioningShouldLaunchResultIntent;

    /** The download information of the role holder package. */
    @Nullable
    public final PackageDownloadInfo roleHolderDownloadInfo;

    public static String inferStaticDeviceAdminPackageName(ComponentName deviceAdminComponentName,
            String deviceAdminPackageName) {
        if (deviceAdminComponentName != null) {
            return deviceAdminComponentName.getPackageName();
        }
        return deviceAdminPackageName;
    }

    public String inferDeviceAdminPackageName() {
        return inferStaticDeviceAdminPackageName(deviceAdminComponentName, deviceAdminPackageName);
    }

    /**
     * Due to legacy reason, DPC is allowed to provide either package name or the component name.
     * If component name is not {@code null}, we will return it right away. Otherwise, we will
     * infer the component name.
     * <p>
     * In most cases, it is preferable to access the admin component name using this method.
     * But if the purpose is to verify the device admin component name, you should use
     * {@link Utils#findDeviceAdmin(String, ComponentName, Context, int)} instead.
     */
    public ComponentName inferDeviceAdminComponentName(Utils utils, Context context, int userId)
            throws IllegalProvisioningArgumentException {
        if (deviceAdminComponentName != null) {
            return deviceAdminComponentName;
        }
        return utils.findDeviceAdmin(
                deviceAdminPackageName, deviceAdminComponentName, context, userId);
    }

    private ProvisioningParams(Builder builder) {
        provisioningId = builder.mProvisioningId;
        timeZone = builder.mTimeZone;
        localTime = builder.mLocalTime;
        locale = builder.mLocale;

        wifiInfo = builder.mWifiInfo;
        useMobileData = builder.mUseMobileData;

        deviceAdminComponentName = builder.mDeviceAdminComponentName;
        deviceAdminPackageName = builder.mDeviceAdminPackageName;
        organizationName = builder.mOrganizationName;
        supportUrl = builder.mSupportUrl;

        deviceAdminDownloadInfo = builder.mDeviceAdminDownloadInfo;
        disclaimersParam = builder.mDisclaimersParam;

        adminExtrasBundle = builder.mAdminExtrasBundle;

        startedByTrustedSource = builder.mStartedByTrustedSource;
        isNfc = builder.mIsNfc;
        isQrProvisioning = builder.mIsQrProvisioning;
        leaveAllSystemAppsEnabled = builder.mLeaveAllSystemAppsEnabled;
        skipEncryption = builder.mSkipEncryption;
        accountToMigrate = builder.mAccountToMigrate;
        provisioningAction = builder.mProvisioningAction;
        skipEducationScreens = builder.mSkipEducationScreens;
        keepAccountMigrated = builder.mKeepAccountMigrated;

        isOrganizationOwnedProvisioning = builder.mIsOrganizationOwnedProvisioning;
        allowedProvisioningModes = builder.mAllowedProvisioningModes;
        initiatorRequestedProvisioningModes = builder.mInitiatorRequestedProvisioningModes;
        flowType = builder.mFlowType;
        allowProvisioningAfterUserSetupComplete = builder.mAllowProvisioningAfterUserSetupComplete;
        provisioningTrigger = builder.mProvisioningTrigger;
        skipOwnershipDisclaimer = builder.mSkipOwnershipDisclaimer;
        returnBeforePolicyCompliance = builder.mReturnBeforePolicyCompliance;
        deviceOwnerPermissionGrantOptOut = builder.mDeviceOwnerPermissionGrantOptOut;
        allowOffline = builder.mAllowOffline;
        roleHolderDownloadInfo = builder.mRoleHolderDownloadInfo;
        provisioningShouldLaunchResultIntent = builder.mProvisioningShouldLaunchResultIntent;

        if (!builder.mSkipValidation) {
            requireNonNull(provisioningAction);
            validateFields();
        }
    }

    private ProvisioningParams(Parcel in) {
        this(createBuilderFromPersistableBundle(
                PersistableBundlable.getPersistableBundleFromParcel(in)));
    }

    private void validateFields() {
        checkArgument(deviceAdminPackageName != null || deviceAdminComponentName != null);
    }

    @Override
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle bundle = new PersistableBundle();

        bundle.putLong(TAG_PROVISIONING_ID, provisioningId);
        bundle.putString(EXTRA_PROVISIONING_TIME_ZONE, timeZone);
        bundle.putLong(EXTRA_PROVISIONING_LOCAL_TIME, localTime);
        bundle.putString(EXTRA_PROVISIONING_LOCALE, StoreUtils.localeToString(locale));
        putPersistableBundlableIfNotNull(bundle, TAG_WIFI_INFO, wifiInfo);
        bundle.putBoolean(EXTRA_PROVISIONING_USE_MOBILE_DATA, useMobileData);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, deviceAdminPackageName);
        bundle.putString(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                StoreUtils.componentNameToString(deviceAdminComponentName));
        bundle.putString(EXTRA_PROVISIONING_ORGANIZATION_NAME, organizationName);
        bundle.putString(EXTRA_PROVISIONING_SUPPORT_URL, supportUrl);
        bundle.putPersistableBundle(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, accountToMigrate == null
                ? null : accountToPersistableBundle(accountToMigrate));
        bundle.putString(TAG_PROVISIONING_ACTION, provisioningAction);
        putPersistableBundlableIfNotNull(bundle, TAG_PACKAGE_DOWNLOAD_INFO,
                deviceAdminDownloadInfo);
        putPersistableBundlableIfNotNull(bundle, EXTRA_PROVISIONING_DISCLAIMERS,
                disclaimersParam);
        bundle.putPersistableBundle(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, adminExtrasBundle);
        bundle.putBoolean(TAG_STARTED_BY_TRUSTED_SOURCE, startedByTrustedSource);
        bundle.putBoolean(TAG_IS_NFC, isNfc);
        bundle.putBoolean(TAG_IS_QR_PROVISIONING, isQrProvisioning);
        bundle.putBoolean(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                leaveAllSystemAppsEnabled);
        bundle.putBoolean(EXTRA_PROVISIONING_SKIP_ENCRYPTION, skipEncryption);
        bundle.putBoolean(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, skipEducationScreens);
        bundle.putBoolean(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, keepAccountMigrated);
        bundle.putBoolean(TAG_IS_ORGANIZATION_OWNED_PROVISIONING, isOrganizationOwnedProvisioning);
        bundle.putIntArray(TAG_ALLOWED_PROVISIONING_MODES,
                integerArrayListToIntArray(allowedProvisioningModes));
        bundle.putInt(TAG_INITIATOR_REQUESTED_PROVISIONING_MODES,
                initiatorRequestedProvisioningModes);
        bundle.putInt(TAG_FLOW_TYPE, flowType);
        bundle.putBoolean(TAG_ALLOW_PROVISIONING_AFTER_USER_SETUP_COMPLETE,
                allowProvisioningAfterUserSetupComplete);
        bundle.putInt(TAG_PROVISIONING_TRIGGER, provisioningTrigger);
        bundle.putBoolean(TAG_SKIP_OWNERSHIP_DISCLAIMER, skipOwnershipDisclaimer);
        bundle.putBoolean(TAG_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE,
                returnBeforePolicyCompliance);
        bundle.putBoolean(TAG_DEVICE_OWNER_PERMISSION_GRANT_OPT_OUT,
                deviceOwnerPermissionGrantOptOut);
        bundle.putBoolean(TAG_ALLOW_OFFLINE, allowOffline);
        putPersistableBundlableIfNotNull(bundle, TAG_ROLE_HOLDER_PACKAGE_DOWNLOAD_INFO,
                roleHolderDownloadInfo);
        bundle.putBoolean(TAG_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT,
                provisioningShouldLaunchResultIntent);
        return bundle;
    }

    /* package */ static ProvisioningParams fromPersistableBundle(PersistableBundle bundle) {
        return createBuilderFromPersistableBundle(bundle).build();
    }

    private static Builder createBuilderFromPersistableBundle(PersistableBundle bundle) {
        Builder builder = new Builder(/* skipValidation= */ false);
        builder.setProvisioningId(bundle.getLong(TAG_PROVISIONING_ID, DEFAULT_PROVISIONING_ID));
        builder.setTimeZone(bundle.getString(EXTRA_PROVISIONING_TIME_ZONE));
        builder.setLocalTime(bundle.getLong(EXTRA_PROVISIONING_LOCAL_TIME));
        builder.setLocale(getStringAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_LOCALE, StoreUtils::stringToLocale));
        builder.setUseMobileData(bundle.getBoolean(EXTRA_PROVISIONING_USE_MOBILE_DATA));
        builder.setWifiInfo(getObjectAttrFromPersistableBundle(bundle,
                TAG_WIFI_INFO, WifiInfo::fromPersistableBundle));
        builder.setDeviceAdminPackageName(bundle.getString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME));
        builder.setDeviceAdminComponentName(getStringAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, StoreUtils::stringToComponentName));
        builder.setOrganizationName(bundle.getString(EXTRA_PROVISIONING_ORGANIZATION_NAME));
        builder.setSupportUrl(bundle.getString(EXTRA_PROVISIONING_SUPPORT_URL));
        builder.setAccountToMigrate(getObjectAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, StoreUtils::persistableBundleToAccount));
        builder.setProvisioningAction(bundle.getString(TAG_PROVISIONING_ACTION));
        builder.setDeviceAdminDownloadInfo(getObjectAttrFromPersistableBundle(bundle,
                TAG_PACKAGE_DOWNLOAD_INFO, PackageDownloadInfo::fromPersistableBundle));
        builder.setDisclaimersParam(getObjectAttrFromPersistableBundle(bundle,
                EXTRA_PROVISIONING_DISCLAIMERS, DisclaimersParam::fromPersistableBundle));
        builder.setAdminExtrasBundle(bundle.getPersistableBundle(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
        builder.setStartedByTrustedSource(bundle.getBoolean(TAG_STARTED_BY_TRUSTED_SOURCE));
        builder.setIsNfc(bundle.getBoolean(TAG_IS_NFC));
        builder.setIsQrProvisioning(bundle.getBoolean(TAG_IS_QR_PROVISIONING));
        builder.setSkipEncryption(bundle.getBoolean(EXTRA_PROVISIONING_SKIP_ENCRYPTION));
        builder.setLeaveAllSystemAppsEnabled(bundle.getBoolean(
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED));
        builder.setSkipEducationScreens(bundle.getBoolean(EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS));
        builder.setKeepAccountMigrated(bundle.getBoolean(
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION));
        builder.setIsOrganizationOwnedProvisioning(bundle.getBoolean(
                TAG_IS_ORGANIZATION_OWNED_PROVISIONING));
        builder.setAllowedProvisioningModes(
                intArrayToIntegerArrayList(bundle.getIntArray(TAG_ALLOWED_PROVISIONING_MODES)));
        builder.setInitiatorRequestedProvisioningModes(bundle.getInt(
                TAG_INITIATOR_REQUESTED_PROVISIONING_MODES));
        builder.setFlowType(bundle.getInt(TAG_FLOW_TYPE));
        builder.setAllowProvisioningAfterUserSetupComplete(bundle.getBoolean(
                TAG_ALLOW_PROVISIONING_AFTER_USER_SETUP_COMPLETE));
        builder.setProvisioningTrigger(bundle.getInt(TAG_PROVISIONING_TRIGGER));
        builder.setSkipOwnershipDisclaimer(bundle.getBoolean(TAG_SKIP_OWNERSHIP_DISCLAIMER));
        builder.setReturnBeforePolicyCompliance(bundle.getBoolean(
                TAG_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE));
        builder.setDeviceOwnerPermissionGrantOptOut(
                bundle.getBoolean(TAG_DEVICE_OWNER_PERMISSION_GRANT_OPT_OUT));
        builder.setAllowOffline(bundle.getBoolean(TAG_ALLOW_OFFLINE));
        builder.setRoleHolderDownloadInfo(getObjectAttrFromPersistableBundle(
                bundle,
                TAG_ROLE_HOLDER_PACKAGE_DOWNLOAD_INFO,
                PackageDownloadInfo::fromPersistableBundle));
        builder.setProvisioningShouldLaunchResultIntent(
                bundle.getBoolean(TAG_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT));
        return builder;
    }

    private static ArrayList<Integer> intArrayToIntegerArrayList(int[] intArray) {
        return Arrays.stream(intArray).boxed().collect(Collectors.toCollection(ArrayList::new));
    }

    private static int[] integerArrayListToIntArray(ArrayList<Integer> arrayList) {
        return arrayList.stream().mapToInt(Integer::valueOf).toArray();
    }

    public Builder toBuilder() {
        return createBuilderFromPersistableBundle(toPersistableBundle());
    }

    @Override
    public String toString() {
        return "ProvisioningParams values: " + toPersistableBundle().toString();
    }

    /**
     * Saves the ProvisioningParams to the specified file.
     */
    public void save(File file) {
        ProvisionLogger.logd("Saving ProvisioningParams to " + file);
        AtomicFile atomicFile = null;
        FileOutputStream stream = null;
        try {
            atomicFile = new AtomicFile(file);
            stream = atomicFile.startWrite();
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(stream, StandardCharsets.UTF_8.name());
            serializer.startDocument(null, true);
            serializer.startTag(null, TAG_PROVISIONING_PARAMS);
            toPersistableBundle().saveToXml(serializer);
            serializer.endTag(null, TAG_PROVISIONING_PARAMS);
            serializer.endDocument();
            atomicFile.finishWrite(stream);
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to save Provisioning Params to "
                    + " file " + file, e);
            file.delete();
            if (atomicFile != null) {
                atomicFile.failWrite(stream);
            }
        }
    }

    public void cleanUp() {
        if (disclaimersParam != null) {
            disclaimersParam.cleanUp();
        }
    }

    /**
     * Loads the ProvisioningParams From the specified file.
     */
    public static ProvisioningParams load(File file) {
        if (!file.exists()) {
            return null;
        }
        ProvisionLogger.logd("Loading ProvisioningParams from " + file);
        try (FileInputStream stream = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            return load(parser);
        } catch (IOException | XmlPullParserException e) {
            ProvisionLogger.loge("Caught exception while trying to load the provisioning params"
                    + " from file " + file, e);
            return null;
        }
    }

    private static ProvisioningParams load(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        int type;
        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
             if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                 continue;
             }
             String tag = parser.getName();
             switch (tag) {
                 case TAG_PROVISIONING_PARAMS:
                     return createBuilderFromPersistableBundle(
                             PersistableBundle.restoreFromXml(parser)).build();
             }
        }
        return new Builder(/* skipValidation= */ false).build();
    }

    public final static class Builder {
        private final boolean mSkipValidation;
        private long mProvisioningId;
        private String mTimeZone;
        private long mLocalTime = DEFAULT_LOCAL_TIME;
        private Locale mLocale;
        private WifiInfo mWifiInfo;
        private String mDeviceAdminPackageName;
        private ComponentName mDeviceAdminComponentName;
        private String mOrganizationName;
        private String mSupportUrl;
        private Account mAccountToMigrate;
        private String mProvisioningAction;
        private PackageDownloadInfo mDeviceAdminDownloadInfo;
        private DisclaimersParam mDisclaimersParam;
        private PersistableBundle mAdminExtrasBundle;
        private boolean mStartedByTrustedSource = DEFAULT_STARTED_BY_TRUSTED_SOURCE;
        private boolean mIsNfc = DEFAULT_IS_NFC;
        private boolean mIsQrProvisioning = DEFAULT_IS_QR_PROVISIONING;
        private boolean mLeaveAllSystemAppsEnabled = DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED;
        private boolean mSkipEncryption = DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION;
        private boolean mSkipEducationScreens = DEFAULT_EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
        private boolean mKeepAccountMigrated = DEFAULT_EXTRA_PROVISIONING_KEEP_ACCOUNT_MIGRATED;
        private boolean mUseMobileData = DEFAULT_EXTRA_PROVISIONING_USE_MOBILE_DATA;
        private boolean mIsOrganizationOwnedProvisioning =
                DEFAULT_EXTRA_PROVISIONING_IS_ORGANIZATION_OWNED;
        private ArrayList<Integer> mAllowedProvisioningModes =
                DEFAULT_EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES;
        private int mInitiatorRequestedProvisioningModes =
                DEFAULT_EXTRA_PROVISIONING_SUPPORTED_MODES;
        private @FlowType int mFlowType = FLOW_TYPE_UNSPECIFIED;
        private boolean mAllowProvisioningAfterUserSetupComplete = false;
        private @ProvisioningTrigger int mProvisioningTrigger = PROVISIONING_TRIGGER_UNSPECIFIED;
        private boolean mSkipOwnershipDisclaimer =
                DEFAULT_EXTRA_PROVISIONING_SKIP_OWNERSHIP_DISCLAIMER;
        private boolean mReturnBeforePolicyCompliance =
                DEFAULT_EXTRA_PROVISIONING_RETURN_BEFORE_POLICY_COMPLIANCE;
        private boolean mDeviceOwnerPermissionGrantOptOut =
                DEFAULT_EXTRA_PROVISIONING_PERMISSION_GRANT_OPT_OUT;
        private boolean mAllowOffline = DEFAULT_EXTRA_ALLOW_OFFLINE;
        public PackageDownloadInfo mRoleHolderDownloadInfo;
        private boolean mProvisioningShouldLaunchResultIntent =
                DEFAULT_EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT;

        public Builder() {
            this(/* skipValidation= */ false);
        }

        public Builder(boolean skipValidation) {
            mSkipValidation = skipValidation;
        }

        public Builder setProvisioningId(long provisioningId) {
            mProvisioningId = provisioningId;
            return this;
        }

        public Builder setTimeZone(String timeZone) {
            mTimeZone = timeZone;
            return this;
        }

        public Builder setLocalTime(long localTime) {
            mLocalTime = localTime;
            return this;
        }

        public Builder setLocale(Locale locale) {
            mLocale = locale;
            return this;
        }

        public Builder setWifiInfo(WifiInfo wifiInfo) {
            mWifiInfo = wifiInfo;
            return this;
        }

        @Deprecated
        public Builder setDeviceAdminPackageName(String deviceAdminPackageName) {
            mDeviceAdminPackageName = deviceAdminPackageName;
            return this;
        }

        public Builder setDeviceAdminComponentName(ComponentName deviceAdminComponentName) {
            mDeviceAdminComponentName = deviceAdminComponentName;
            return this;
        }

        public Builder setOrganizationName(String organizationName) {
            mOrganizationName = organizationName;
            return this;
        }

        public Builder setSupportUrl(String supportUrl) {
            mSupportUrl = supportUrl;
            return this;
        }

        public Builder setAccountToMigrate(Account accountToMigrate) {
            mAccountToMigrate = accountToMigrate;
            return this;
        }

        public Builder setProvisioningAction(String provisioningAction) {
            mProvisioningAction = provisioningAction;
            return this;
        }

        public Builder setDeviceAdminDownloadInfo(PackageDownloadInfo deviceAdminDownloadInfo) {
            mDeviceAdminDownloadInfo = deviceAdminDownloadInfo;
            return this;
        }

        public Builder setDisclaimersParam(DisclaimersParam disclaimersParam) {
            mDisclaimersParam = disclaimersParam;
            return this;
        }

        public Builder setAdminExtrasBundle(PersistableBundle adminExtrasBundle) {
            mAdminExtrasBundle = adminExtrasBundle;
            return this;
        }

        public Builder setStartedByTrustedSource(boolean startedByTrustedSource) {
            mStartedByTrustedSource = startedByTrustedSource;
            return this;
        }

        public Builder setIsNfc(boolean isNfc) {
            mIsNfc = isNfc;
            return this;
        }

        public Builder setIsQrProvisioning(boolean qrProvisioning) {
            mIsQrProvisioning = qrProvisioning;
            return this;
        }

        public Builder setLeaveAllSystemAppsEnabled(boolean leaveAllSystemAppsEnabled) {
            mLeaveAllSystemAppsEnabled = leaveAllSystemAppsEnabled;
            return this;
        }

        public Builder setSkipEncryption(boolean skipEncryption) {
            mSkipEncryption = skipEncryption;
            return this;
        }

        public Builder setSkipEducationScreens(boolean skipEducationScreens) {
            mSkipEducationScreens = skipEducationScreens;
            return this;
        }

        public Builder setKeepAccountMigrated(boolean keepAccountMigrated) {
            mKeepAccountMigrated = keepAccountMigrated;
            return this;
        }

        public Builder setUseMobileData(boolean useMobileData) {
            mUseMobileData = useMobileData;
            return this;
        }

        public Builder setIsOrganizationOwnedProvisioning(boolean isOrganizationOwnedProvisioning) {
            mIsOrganizationOwnedProvisioning = isOrganizationOwnedProvisioning;
            return this;
        }

        public Builder setFlowType(@FlowType int flowType) {
            mFlowType = flowType;
            return this;
        }

        public Builder setAllowProvisioningAfterUserSetupComplete(
                boolean allowProvisioningAfterUserSetupComplete) {
            mAllowProvisioningAfterUserSetupComplete = allowProvisioningAfterUserSetupComplete;
            return this;
        }

        public Builder setProvisioningTrigger(@ProvisioningTrigger int provisioningTrigger) {
            mProvisioningTrigger = provisioningTrigger;
            return this;
        }

        public Builder setAllowedProvisioningModes(ArrayList<Integer> provisioningModes) {
            mAllowedProvisioningModes = new ArrayList<>(provisioningModes);
            return this;
        }

        public Builder setInitiatorRequestedProvisioningModes(
                int initiatorRequestedProvisioningModes) {
            mInitiatorRequestedProvisioningModes = initiatorRequestedProvisioningModes;
            return this;
        }

        /**
         * See {@link ProvisioningParams#skipOwnershipDisclaimer}.
         */
        public Builder setSkipOwnershipDisclaimer(boolean skipOwnershipDisclaimer) {
            mSkipOwnershipDisclaimer = skipOwnershipDisclaimer;
            return this;
        }

        /**
         * Setter for {@link #returnBeforePolicyCompliance}.
         */
        public Builder setReturnBeforePolicyCompliance(boolean returnBeforePolicyCompliance) {
            mReturnBeforePolicyCompliance = returnBeforePolicyCompliance;
            return this;
        }

        /**
         * Setter for whether the admin has opted out of controlling permission grants.
         */
        public Builder setDeviceOwnerPermissionGrantOptOut(boolean optout) {
            mDeviceOwnerPermissionGrantOptOut = optout;
            return this;
        }

        /**
         * Setter for whether offline provisioning is allowed.
         *
         * @see DevicePolicyManager#EXTRA_PROVISIONING_ALLOW_OFFLINE
         */
        public Builder setAllowOffline(boolean allowOffline) {
            mAllowOffline = allowOffline;
            return this;
        }

        /**
         * Setter for the role holder download info.
         */
        public Builder setRoleHolderDownloadInfo(PackageDownloadInfo roleHolderDownloadInfo) {
            mRoleHolderDownloadInfo = roleHolderDownloadInfo;
            return this;
        }

        /**
         * Setter for whether provisioning should launch the result intent returned by the
         * device manager role holder.
         *
         * @see DevicePolicyManager#EXTRA_PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT
         */
        public Builder setProvisioningShouldLaunchResultIntent(
                boolean provisioningShouldLaunchResultIntent) {
            mProvisioningShouldLaunchResultIntent = provisioningShouldLaunchResultIntent;
            return this;
        }

        /**
         * Builds the {@link ProvisioningParams} object. Note that {@link
         * #setProvisioningAction(String)} and {@link #setDeviceAdminComponentName(ComponentName)}
         * methods must be called with a non-null parameter before this is called.
         */
        public ProvisioningParams build() {
            return new ProvisioningParams(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Creates a {@link Builder} with the option to skip validation.
         */
        public static Builder builder(boolean skipValidation) {
            return new Builder(skipValidation);
        }
    }
}
