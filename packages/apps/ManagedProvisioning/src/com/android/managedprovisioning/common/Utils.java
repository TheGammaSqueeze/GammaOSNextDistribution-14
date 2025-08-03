/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;
import static android.content.pm.PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.WebActivity;
import com.android.managedprovisioning.util.LazyStringResource;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupcompat.template.FooterButton.ButtonType;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.DeviceHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Class containing various auxiliary methods.
 */
public class Utils {
    public static final String SHA256_TYPE = "SHA-256";

    // value chosen to match UX designs; when updating check status bar icon colors
    private static final int THRESHOLD_BRIGHT_COLOR = 190;

    public Utils() {}

    /**
     * Returns the system apps currently available to a given user.
     *
     * <p>Calls the {@link IPackageManager} to retrieve all system apps available to a user and
     * returns their package names.
     *
     * @param ipm an {@link IPackageManager} object
     * @param userId the id of the user to check the apps for
     */
    public Set<String> getCurrentSystemApps(IPackageManager ipm, int userId) {
        Set<String> apps = new HashSet<>();
        List<ApplicationInfo> aInfos = null;
        try {
            aInfos = ipm.getInstalledApplications(
                    MATCH_UNINSTALLED_PACKAGES | MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS, userId)
                    .getList();
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        for (ApplicationInfo aInfo : aInfos) {
            if ((aInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                apps.add(aInfo.packageName);
            }
        }
        return apps;
    }

    /**
     * Disables a given component in a given user.
     *
     * @param toDisable the component that should be disabled
     * @param userId the id of the user where the component should be disabled.
     */
    public void disableComponent(ComponentName toDisable, int userId) {
        setComponentEnabledSetting(
                IPackageManager.Stub.asInterface(ServiceManager.getService("package")),
                toDisable,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                userId);
    }

    /**
     * Enables a given component in a given user.
     *
     * @param toEnable the component that should be enabled
     * @param userId the id of the user where the component should be disabled.
     */
    public void enableComponent(ComponentName toEnable, int userId) {
        setComponentEnabledSetting(
                IPackageManager.Stub.asInterface(ServiceManager.getService("package")),
                toEnable,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                userId);
    }

    /**
     * Disables a given component in a given user.
     *
     * @param ipm an {@link IPackageManager} object
     * @param toDisable the component that should be disabled
     * @param userId the id of the user where the component should be disabled.
     */
    @VisibleForTesting
    void setComponentEnabledSetting(IPackageManager ipm, ComponentName toDisable,
            int enabledSetting, int userId) {
        try {
            ipm.setComponentEnabledSetting(toDisable,
                    enabledSetting, PackageManager.DONT_KILL_APP,
                    userId, "managedprovisioning");
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        } catch (Exception e) {
            ProvisionLogger.logw("Component not found, not changing enabled setting: "
                + toDisable.toShortString());
        }
    }

    /**
     * Check the validity of the admin component name supplied, or try to infer this componentName
     * from the package.
     *
     * We are supporting lookup by package name for legacy reasons.
     *
     * If dpcComponentName is supplied (not null): dpcPackageName is ignored.
     * Check that the package of dpcComponentName is installed, that dpcComponentName is a
     * receiver in this package, and return it. The receiver can be in disabled state.
     *
     * Otherwise: dpcPackageName must be supplied (not null).
     * Check that this package is installed, try to infer a potential device admin in this package,
     * and return it.
     */
    @NonNull
    @VisibleForTesting
    public ComponentName findDeviceAdmin(String dpcPackageName, ComponentName dpcComponentName,
            Context context, int userId) throws IllegalProvisioningArgumentException {
        if (dpcComponentName != null) {
            dpcPackageName = dpcComponentName.getPackageName();
        }
        if (dpcPackageName == null) {
            throw new IllegalProvisioningArgumentException("Neither the package name nor the"
                    + " component name of the admin are supplied");
        }
        PackageInfo pi;
        try {
            pi = context.getPackageManager().getPackageInfoAsUser(dpcPackageName,
                    PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS,
                    userId);
        } catch (NameNotFoundException e) {
            throw new IllegalProvisioningArgumentException("Dpc " + dpcPackageName
                    + " is not installed. ", e);
        }

        final ComponentName componentName = findDeviceAdminInPackageInfo(dpcPackageName,
                dpcComponentName, pi);
        if (componentName == null) {
            throw new IllegalProvisioningArgumentException("Cannot find any admin receiver in "
                    + "package " + dpcPackageName + " with component " + dpcComponentName);
        }
        return componentName;
    }

    /**
     * If dpcComponentName is not null: dpcPackageName is ignored.
     * Check that the package of dpcComponentName is installed, that dpcComponentName is a
     * receiver in this package, and return it. The receiver can be in disabled state.
     *
     * Otherwise, try to infer a potential device admin component in this package info.
     *
     * @return infered device admin component in package info. Otherwise, null
     */
    @Nullable
    public ComponentName findDeviceAdminInPackageInfo(@NonNull String dpcPackageName,
            @Nullable ComponentName dpcComponentName, @NonNull PackageInfo pi) {
        if (dpcComponentName != null) {
            if (!isComponentInPackageInfo(dpcComponentName, pi)) {
                ProvisionLogger.logw("The component " + dpcComponentName + " isn't registered in "
                        + "the apk");
                return null;
            }
            return dpcComponentName;
        } else {
            return findDeviceAdminInPackage(dpcPackageName, pi);
        }
    }

    /**
     * Finds a device admin in a given {@link PackageInfo} object.
     *
     * <p>This function returns {@code null} if no or multiple admin receivers were found, and if
     * the package name does not match dpcPackageName.</p>
     * @param packageName packge name that should match the {@link PackageInfo} object.
     * @param packageInfo package info to be examined.
     * @return admin receiver or null in case of error.
     */
    @Nullable
    private ComponentName findDeviceAdminInPackage(String packageName, PackageInfo packageInfo) {
        if (packageInfo == null || !TextUtils.equals(packageInfo.packageName, packageName)) {
            return null;
        }

        ComponentName mdmComponentName = null;
        for (ActivityInfo ai : packageInfo.receivers) {
            if (TextUtils.equals(ai.permission, android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                if (mdmComponentName != null) {
                    ProvisionLogger.logw("more than 1 device admin component are found");
                    return null;
                } else {
                    mdmComponentName = new ComponentName(packageName, ai.name);
                }
            }
        }
        return mdmComponentName;
    }

    private boolean isComponentInPackageInfo(ComponentName dpcComponentName,
            PackageInfo pi) {
        for (ActivityInfo ai : pi.receivers) {
            if (dpcComponentName.getClassName().equals(ai.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return if a given package has testOnly="true", in which case we'll relax certain rules
     * for CTS.
     *
     * The system allows this flag to be changed when an app is updated. But
     * {@link DevicePolicyManager} uses the persisted version to do actual checks for relevant
     * dpm command.
     *
     * @see DevicePolicyManagerService#isPackageTestOnly for more info
     */
    public static boolean isPackageTestOnly(PackageManager pm, String packageName, int userHandle) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        try {
            final ApplicationInfo ai = pm.getApplicationInfoAsUser(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, userHandle);
            return ai != null && (ai.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

    }

    /**
     * Returns whether the current user is the system user.
     */
    public boolean isCurrentUserSystem() {
        return UserHandle.myUserId() == UserHandle.USER_SYSTEM;
    }

    /**
     * Returns whether the device is currently managed.
     */
    public boolean isDeviceManaged(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm.isDeviceManaged();
    }

    /**
     * Returns true if the given package requires an update.
     *
     * <p>There are two cases where an update is required:
     * 1. The package is not currently present on the device.
     * 2. The package is present, but the version is below the minimum supported version.
     *
     * @param packageName the package to be checked for updates
     * @param minSupportedVersion the minimum supported version
     * @param context a {@link Context} object
     */
    public boolean packageRequiresUpdate(String packageName, int minSupportedVersion,
            Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            // Always download packages if no minimum version given.
            if (minSupportedVersion != PackageDownloadInfo.DEFAULT_MINIMUM_VERSION
                    && packageInfo.versionCode >= minSupportedVersion) {
                return false;
            }
        } catch (NameNotFoundException e) {
            // Package not on device.
        }

        return true;
    }

    /**
     * Returns the first existing managed profile if any present, null otherwise.
     *
     * <p>Note that we currently only support one managed profile per device.
     */
    // TODO: Add unit tests
    @Nullable
    public UserHandle getManagedProfile(Context context) {
        DevicePolicyManager devicePolicyManager =
                requireNonNull(
                        /* obj= */ context.getSystemService(DevicePolicyManager.class),
                        /* message= */ "Unable to obtain DevicePolicyManager");
        int currentUserId = UserHandle.myUserId();
        List<UserHandle> managedProfiles =
                devicePolicyManager.getPolicyManagedProfiles(UserHandle.of(currentUserId));
        if (managedProfiles.isEmpty()) {
            return null;
        }
        return managedProfiles.get(0);
    }

    /**
     * Returns whether FRP is supported on the device.
     */
    public boolean isFrpSupported(Context context) {
        Object pdbManager = context.getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);
        return pdbManager != null;
    }


    /**
     * Returns {@code true} if the admin-integrated flow should be performed.
     *
     * <p>This method must not be called before the admin app has been installed. If it has not
     * yet been installed, consider using {@link
     * #checkAdminIntegratedFlowPreconditions(ProvisioningParams)}.
     *
     * <p>To perform the admin-integrated flow, all of the following criteria must be fulfilled:
     * <ul>
     *     <li>All of the preconditions in {@link
     *     #checkAdminIntegratedFlowPreconditions(ProvisioningParams)}</li>
     *     <li>The DPC has an activity with intent filter with action {@link
     *     DevicePolicyManager#ACTION_GET_PROVISIONING_MODE}</li>
     *     <li>The DPC has an activity with intent filter with action {@link
     *     DevicePolicyManager#ACTION_ADMIN_POLICY_COMPLIANCE}</li>
     * </ul>
     */
    public boolean canPerformAdminIntegratedFlow(Context context, ProvisioningParams params,
            PolicyComplianceUtils policyComplianceUtils,
            GetProvisioningModeUtils provisioningModeUtils) {
        if (!checkAdminIntegratedFlowPreconditions(params)) {
            return false;
        }
        boolean isPolicyComplianceScreenAvailable =
                policyComplianceUtils.isPolicyComplianceActivityResolvableForUser(context, params,
                        this, UserHandle.SYSTEM);
        if (!isPolicyComplianceScreenAvailable) {
            ProvisionLogger.logi("Policy compliance DPC screen not available.");
            return false;
        }
        boolean isGetProvisioningModeScreenAvailable =
                provisioningModeUtils.isGetProvisioningModeActivityResolvable(context, params);
        if (!isGetProvisioningModeScreenAvailable) {
            ProvisionLogger.logi("Get provisioning mode DPC screen not available.");
            return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if the admin-integrated flow preconditions are met.
     *
     * <p>This method can be called before the admin app has been installed. Returning {@code true}
     * does not mean the admin-integrated flow should be performed (for that, use {@link
     * #canPerformAdminIntegratedFlow(Context, ProvisioningParams, PolicyComplianceUtils,
     * GetProvisioningModeUtils)}), but returning {@code false} can be used as an early indication
     * that it should <i>not</i> be performed.
     *
     * <p>The preconditions are:
     * <ul>
     *     <li>Provisioning was started using {@link
     *     DevicePolicyManager#ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}</li>
     *     <li>The provisioning is not triggered by NFC</li>
     *     <li>This is not a financed device provisioning</li>
     * </ul>
     */
    public boolean checkAdminIntegratedFlowPreconditions(ProvisioningParams params) {
        if (isFinancedDeviceAction(params.provisioningAction)) {
            ProvisionLogger.logi("Financed device provisioning");
            return false;
        }
        if (!params.startedByTrustedSource) {
            ProvisionLogger.logi("Provisioning not started by a trusted source");
            return false;
        }
        return true;
    }

    /**
     * Factory resets the device.
     */
    public void factoryReset(Context context, String reason) {
        context.getSystemService(DevicePolicyManager.class).wipeDevice(/* flags=*/ 0);
    }

    /**
     * Returns whether the given provisioning action is a profile owner action.
     */
    // TODO: Move the list of device owner actions into a Globals class.
    public final boolean isProfileOwnerAction(String action) {
        return ACTION_PROVISION_MANAGED_PROFILE.equals(action);
    }

    /**
     * Returns whether the given provisioning action is a device owner action.
     */
    // TODO: Move the list of device owner actions into a Globals class.
    public final boolean isDeviceOwnerAction(String action) {
        return ACTION_PROVISION_MANAGED_DEVICE.equals(action);
    }

    /**
     * Returns whether the given provisioning action is a financed device action.
     */
    public final boolean isFinancedDeviceAction(String action) {
        return ACTION_PROVISION_FINANCED_DEVICE.equals(action);
    }

    /**
     * Returns whether the device currently has connectivity.
     */
    public boolean isConnectedToNetwork(Context context) {
        NetworkInfo info = getActiveNetworkInfo(context);
        return info != null && info.isConnected();
    }

    public boolean isMobileNetworkConnectedToInternet(Context context) {
        final ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return Arrays.stream(connectivityManager.getAllNetworks())
                .map(connectivityManager::getNetworkCapabilities)
                .filter(Objects::nonNull)
                .anyMatch(this::isNetworkConnectedToInternetViaCellular);
    }

    /**
     * Returns whether the device is currently connected to specific network type, such as
     * {@link ConnectivityManager#TYPE_WIFI} or {@link ConnectivityManager#TYPE_ETHERNET}
     *
     * {@see ConnectivityManager}
     *
     * @deprecated use one of
     * {@link #isNetworkConnectedToInternetViaEthernet(NetworkCapabilities)},
     * {@link #isNetworkConnectedToInternetViaWiFi(NetworkCapabilities)}
     * {@link #isNetworkConnectedToInternetViaCellular(NetworkCapabilities)}
     */
    @Deprecated
    public boolean isNetworkTypeConnected(Context context, int... types) {
        final NetworkInfo networkInfo = getActiveNetworkInfo(context);
        if (networkInfo != null && networkInfo.isConnected()) {
            final int activeNetworkType = networkInfo.getType();
            for (int type : types) {
                if (activeNetworkType == type) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the network is active (can receive and send data)
     */
    public boolean isNetworkActive(NetworkCapabilities network) {
        return network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Checks if the network has transport {@link NetworkCapabilities#TRANSPORT_ETHERNET} and
     * {@link #isNetworkActive}
     */
    public boolean isNetworkConnectedToInternetViaEthernet(NetworkCapabilities network) {
        return network.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                && isNetworkActive(network);
    }

    /**
     * Checks if the network has transport {@link NetworkCapabilities#TRANSPORT_WIFI} and
     * {@link #isNetworkActive}
     */
    public boolean isNetworkConnectedToInternetViaWiFi(NetworkCapabilities network) {
        return network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && isNetworkActive(network);
    }

    /**
     * Checks if the network has transport {@link NetworkCapabilities#TRANSPORT_CELLULAR} and
     * {@link #isNetworkActive}
     */
    public boolean isNetworkConnectedToInternetViaCellular(NetworkCapabilities network) {
        return network.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && isNetworkActive(network);
    }

    /**
     * Returns the active network info of the device.
     *
     * @deprecated use {@link #getActiveNetworkCapabilities(Context)}
     */
    @Deprecated
    public NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    /**
     * Retrieves {@link NetworkCapabilities} of the currently active network
     * or `null` if there is no active network
     */
    @Nullable
    public NetworkCapabilities getActiveNetworkCapabilities(Context context) {
        var cn = requireNonNull(context.getSystemService(ConnectivityManager.class),
                "Unable to obtain ConnectivityManager");
        return cn.getNetworkCapabilities(cn.getActiveNetwork());
    }

    /**
     * Returns whether encryption is required on this device.
     *
     * <p>Encryption is required if the device is not currently encrypted and the persistent
     * system flag {@code persist.sys.no_req_encrypt} is not set.
     */
    public boolean isEncryptionRequired() {
        return !isPhysicalDeviceEncrypted()
                && !SystemProperties.getBoolean("persist.sys.no_req_encrypt", false);
    }

    /**
     * Returns whether the device is currently encrypted.
     */
    public boolean isPhysicalDeviceEncrypted() {
        return StorageManager.isEncrypted();
    }

    /**
     * Returns the wifi pick intent.
     */
    // TODO: Move this intent into a Globals class.
    public Intent getWifiPickIntent() {
        Intent wifiIntent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
        wifiIntent.putExtra("extra_prefs_show_button_bar", true);
        wifiIntent.putExtra("wifi_enable_next_on_connect", true);
        return wifiIntent;
    }

    /**
     * Returns whether the device is in headless system user mode.
     */
    public boolean isHeadlessSystemUserMode() {
        return UserManager.isHeadlessSystemUserMode();
    }

    /**
     * Returns whether the currently chosen launcher supports managed profiles.
     *
     * <p>A launcher is deemed to support managed profiles when its target API version is at least
     * {@link Build.VERSION_CODES#LOLLIPOP}.
     */
    public boolean currentLauncherSupportsManagedProfiles(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        PackageManager pm = context.getPackageManager();
        ResolveInfo launcherResolveInfo = pm.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (launcherResolveInfo == null) {
            return false;
        }
        try {
            // If the user has not chosen a default launcher, then launcherResolveInfo will be
            // referring to the resolver activity. It is fine to create a managed profile in
            // this case since there will always be at least one launcher on the device that
            // supports managed profile feature.
            ApplicationInfo launcherAppInfo = pm.getApplicationInfo(
                    launcherResolveInfo.activityInfo.packageName, 0 /* default flags */);
            return versionNumberAtLeastL(launcherAppInfo.targetSdkVersion);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns whether the given version number is at least lollipop.
     *
     * @param versionNumber the version number to be verified.
     */
    private boolean versionNumberAtLeastL(int versionNumber) {
        return versionNumber >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Computes the sha 256 hash of a byte array.
     */
    @Nullable
    public byte[] computeHashOfByteArray(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA256_TYPE);
            md.update(bytes);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + SHA256_TYPE + " not supported.", e);
            return null;
        }
    }

    /**
     * Computes a hash of a file with a spcific hash algorithm.
     */
    // TODO: Add unit tests
    @Nullable
    public byte[] computeHashOfFile(String fileLocation, String hashType) {
        InputStream fis = null;
        MessageDigest md;
        byte[] hash = null;
        try {
            md = MessageDigest.getInstance(hashType);
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + hashType + " not supported.", e);
            return null;
        }
        try {
            fis = new FileInputStream(fileLocation);

            byte[] buffer = new byte[256];
            int n = 0;
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    md.update(buffer, 0, n);
                }
            }
            hash = md.digest();
        } catch (IOException e) {
            ProvisionLogger.loge("IO error.", e);
        } finally {
            // Close input stream quietly.
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // Ignore.
            }
        }
        return hash;
    }

    /**
     * Returns whether given intent can be resolved for the user.
     */
    public boolean canResolveIntentAsUser(Context context, Intent intent, int userId) {
        return intent != null
                && context.getPackageManager().resolveActivityAsUser(intent, 0, userId) != null;
    }

    public boolean isPackageDeviceOwner(DevicePolicyManager dpm, String packageName) {
        final ComponentName deviceOwner = dpm.getDeviceOwnerComponentOnCallingUser();
        return deviceOwner != null && deviceOwner.getPackageName().equals(packageName);
    }

    public int getAccentColor(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    /**
     * Returns the theme's background color.
     */
    public int getBackgroundColor(Context context) {
        return getAttrColor(context, android.R.attr.colorBackground);
    }

    /**
     * Returns the theme's text primary color.
     */
    public int getTextPrimaryColor(Context context) {
        return getAttrColor(context, android.R.attr.textColorPrimary);
    }

    /**
     * Returns the theme's text secondary color.
     */
    public int getTextSecondaryColor(Context context) {
        return getAttrColor(context, android.R.attr.textColorSecondary);
    }

    private int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int attrColor = ta.getColor(0, 0);
        ta.recycle();
        return attrColor;
    }

    public void handleSupportUrl(Context context, CustomizationParams customizationParams,
            AccessibilityContextMenuMaker contextMenuMaker, TextView textView,
            String deviceProvider, String contactDeviceProvider,
            Consumer<Intent> clickHandler) {
        if (customizationParams.supportUrl == null) {
            textView.setText(contactDeviceProvider);
            return;
        }
        final Intent intent = WebActivity.createIntent(
                context, customizationParams.supportUrl);

        final ClickableSpanFactory spanFactory =
                new ClickableSpanFactory(getAccentColor(context), clickHandler);
        handlePartialClickableTextView(
                textView, contactDeviceProvider, deviceProvider, intent, spanFactory);

        contextMenuMaker.registerWithActivity(textView);
    }

    /**
     * Utility function to make a TextView partial clickable. It also associates the TextView with
     * an Intent. The intent will be triggered when the clickable part is clicked.
     *
     * @param textView The TextView which hosts the clickable string.
     * @param content The content of the TextView.
     * @param clickableString The substring which is clickable.
     * @param intent The Intent that will be launched.
     * @param clickableSpanFactory The factory which is used to create ClickableSpan to decorate
     *                             clickable string.
     */
    public void handlePartialClickableTextView(TextView textView, String content,
            String clickableString, Intent intent, ClickableSpanFactory clickableSpanFactory) {
        final SpannableString spannableString = new SpannableString(content);
        if (intent != null) {
            final ClickableSpan span = clickableSpanFactory.create(intent);
            final int startIdx = content.indexOf(clickableString);
            final int endIdx = startIdx + clickableString.length();

            spannableString.setSpan(span, startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        textView.setText(spannableString);
    }

    /**
     * Gets the device's current device owner admin component.
     */
    @Nullable
    public ComponentName getCurrentDeviceOwnerComponentName(DevicePolicyManager dpm) {
        return isHeadlessSystemUserMode()
                ? dpm.getDeviceOwnerComponentOnAnyUser()
                : dpm.getDeviceOwnerComponentOnCallingUser();
    }

    public static FooterButton addNextButton(GlifLayout layout, @NonNull OnClickListener listener) {
        return setPrimaryButton(layout, listener, ButtonType.NEXT, R.string.next);
    }

    /**
     * Adds an encryption primary button mixin to a {@link GlifLayout} screen.
     */
    public static FooterButton addEncryptButton(
            GlifLayout layout, @NonNull OnClickListener listener) {
        return setPrimaryButton(layout, listener, ButtonType.NEXT, R.string.encrypt);
    }

    public static FooterButton addAcceptAndContinueButton(GlifLayout layout,
        @NonNull OnClickListener listener) {
        return setPrimaryButton(layout, listener, ButtonType.NEXT, R.string.accept_and_continue);
    }

    /** Adds a primary "Cancel setup" button */
    public static FooterButton addResetButton(GlifLayout layout,
            @NonNull OnClickListener listener, @StringRes int resetButtonString) {
        return setPrimaryButton(layout, listener, ButtonType.CANCEL,
                resetButtonString);
    }

    private static FooterButton setPrimaryButton(GlifLayout layout, OnClickListener listener,
        @ButtonType int buttonType, @StringRes int label) {
        final FooterBarMixin mixin = layout.getMixin(FooterBarMixin.class);
        final FooterButton primaryButton = new FooterButton.Builder(layout.getContext())
            .setText(label)
            .setListener(listener)
            .setButtonType(buttonType)
            .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
            .build();
        mixin.setPrimaryButton(primaryButton);
        return primaryButton;
    }

    /** Adds a secondary "abort & reset" button. */
    public static FooterButton addAbortAndResetButton(GlifLayout layout,
            @NonNull OnClickListener listener) {
        final int buttonType = ButtonType.CANCEL;
        final int buttonLabel = R.string.fully_managed_device_cancel_setup_button;

        return addSecondaryButton(layout, listener, buttonType, buttonLabel);
    }

    private static FooterButton addSecondaryButton(GlifLayout layout,
            @NonNull OnClickListener listener,
            @ButtonType int buttonType, @StringRes int buttonLabel) {
        final FooterBarMixin mixin = layout.getMixin(FooterBarMixin.class);
        final FooterButton secondaryButton = new FooterButton.Builder(layout.getContext())
                .setText(buttonLabel)
                .setListener(listener)
                .setButtonType(buttonType)
                .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Secondary)
                .build();
        mixin.setSecondaryButton(secondaryButton);
        return secondaryButton;
    }

    public SimpleDialog.Builder createCancelProvisioningResetDialogBuilder(Context context) {
        CharSequence deviceName = DeviceHelper.getDeviceName(context);
        final int positiveResId = R.string.reset;
        final int negativeResId = R.string.device_owner_cancel_cancel;
        return getBaseDialogBuilder(positiveResId, negativeResId)
                .setMessage(
                        LazyStringResource.of(R.string.this_will_reset_take_back_first_screen,
                                deviceName))
                .setTitle(
                        LazyStringResource.of(R.string.stop_setup_reset_device_question,
                                deviceName));
    }

    /**
     * Create a builder for cancel provisioning dialog
     *
     * @return builder
     */
    public SimpleDialog.Builder createCancelProvisioningDialogBuilder() {
        final int positiveResId = R.string.profile_owner_cancel_ok;
        final int negativeResId = R.string.profile_owner_cancel_cancel;
        final int dialogMsgResId = R.string.profile_owner_cancel_message;
        return getBaseDialogBuilder(positiveResId, negativeResId).setMessage(dialogMsgResId);
    }

    public boolean shouldShowOwnershipDisclaimerScreen(ProvisioningParams params) {
        return !params.skipOwnershipDisclaimer;
    }

    public boolean isOrganizationOwnedAllowed(ProvisioningParams params) {
        int provisioningModes = params.initiatorRequestedProvisioningModes;
        return containsBinaryFlags(provisioningModes, FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                || containsBinaryFlags(provisioningModes, FLAG_SUPPORTED_MODES_DEVICE_OWNER);
    }

    public boolean isManagedProfileProvisioningStartedByDpc(
            Context context,
            ProvisioningParams params,
            SettingsFacade settingsFacade) {
        if (!ACTION_PROVISION_MANAGED_PROFILE.equals(params.provisioningAction)) {
            return false;
        }
        if (params.startedByTrustedSource) {
            return false;
        }
        return settingsFacade.isUserSetupCompleted(context);
    }

    /**
     * Returns {@code true} if {@code packageName} is installed on the primary user.
     */
    public boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            final ApplicationInfo ai = packageManager.getApplicationInfo(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            return ai != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private SimpleDialog.Builder getBaseDialogBuilder(int positiveResId, int negativeResId) {
        return new SimpleDialog.Builder()
                .setCancelable(false)
                .setNegativeButtonMessage(negativeResId)
                .setPositiveButtonMessage(positiveResId);
    }

    /**
     * Returns {@code true} if {@code value} contains the {@code flags} binary flags.
     */
    public boolean containsBinaryFlags(int value, int flags) {
        return (value & flags) == flags;
    }

    /**
     * Calls {@code callback} when {@code view} has been measured.
     */
    public void onViewMeasured(View view, Consumer<View> callback) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    callback.accept(view);
                }
            });
    }
}
