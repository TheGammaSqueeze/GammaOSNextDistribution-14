/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Process;

import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.PackageUtil;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PackageDeviceInfo collector.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PackageDeviceInfo extends DeviceInfo {

    private static final String PLATFORM = "android";
    private static final String PLATFORM_ANDROID_PERMISSION_PREFIX = "android.permission.";
    private static final String PLATFORM_MANIFEST_PERMISSION_PREFIX = "android.Manifest.permission.";

    private static final String PACKAGE = "package";
    private static final String NAME = "name";
    private static final String VERSION_NAME = "version_name";
    private static final String DIR = "dir";
    private static final String SYSTEM_PRIV = "system_priv";
    private static final String MIN_SDK = "min_sdk";
    private static final String TARGET_SDK = "target_sdk";

    private static final String REQUESTED_PERMISSIONS = "requested_permissions";
    private static final String DEFINED_PERMISSIONS = "defined_permissions";
    private static final String PERMISSION_NAME = "name";
    private static final String PERMISSION_FLAGS = "flags";
    private static final String PERMISSION_GROUP = "permission_group";
    private static final String PERMISSION_PROTECTION = "protection_level";
    private static final String PERMISSION_PROTECTION_FLAGS = "protection_level_flags";
    private static final String PERMISSION_IS_GRANTED = "is_granted";


    private static final String PERMISSION_TYPE = "type";
    private static final int PERMISSION_TYPE_SYSTEM = 1;
    private static final int PERMISSION_TYPE_OEM = 2;
    private static final int PERMISSION_TYPE_CUSTOM = 3;

    private static final String REQUESTED_ROLES = "requested_roles";
    private static final String ROLE_NAME = "name";

    private static final String HAS_SYSTEM_UID = "has_system_uid";

    private static final String SHARES_INSTALL_PERMISSION = "shares_install_packages_permission";
    private static final String INSTALL_PACKAGES_PERMISSION = "android.permission.INSTALL_PACKAGES";

    private static final String SHA256_CERT = "sha256_cert";

    private static final String SHA256_FILE = "sha256_file";

    private static final String CONFIG_NOTIFICATION_ACCESS = "config_defaultListenerAccessPackages";
    private static final String HAS_DEFAULT_NOTIFICATION_ACCESS = "has_default_notification_access";

    private static final String UID = "uid";
    private static final String IS_ACTIVE_ADMIN = "is_active_admin";

    private static final String CONFIG_ACCESSIBILITY_SERVICE = "config_defaultAccessibilityService";
    private static final String DEFAULT_ACCESSIBILITY_SERVICE = "is_default_accessibility_service";

    private static final HashSet<String> ADDITIONAL_ANDROID_PERMISSIONS = new HashSet<>(Arrays.asList(new String[] {
        "com.android.voicemail.permission.ADD_VOICEMAIL",
        "com.android.voicemail.permission.WRITE_VOICEMAIL",
        "com.android.voicemail.permission.READ_VOICEMAIL",
        "com.android.browser.permission.READ_HISTORY_BOOKMARKS",
        "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS",
        "com.android.alarm.permission.SET_ALARM",
        "com.android.launcher.permission.INSTALL_SHORTCUT",
        "com.android.launcher.permission.UNINSTALL_SHORTCUT",
        "com.android.permission.INSTALL_EXISTING_PACKAGES",
        "com.android.permission.USE_INSTALLER_V2",
        "com.android.permission.USE_SYSTEM_DATA_LOADERS",
        "android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE"
    }));


    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        final PackageManager pm = getContext().getPackageManager();

        final List<PackageInfo> allPackages =
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        final Set<String> defaultNotificationListeners =
                getColonSeparatedPackageList(CONFIG_NOTIFICATION_ACCESS);

        final Set<String> deviceAdminPackages = getActiveDeviceAdminPackages();

        final ComponentName defaultAccessibilityComponent = getDefaultAccessibilityComponent();

        final HashMap<String, List<String>> packageRolesData = getPackageRolesData();

        // Platform permission data used to tag permissions information with sourcing information
        final PackageInfo platformInfo = pm.getPackageInfo(PLATFORM , PackageManager.GET_PERMISSIONS);
        final Set<String> platformPermissions = new HashSet<String>();
        for (PermissionInfo permission : platformInfo.permissions) {
          platformPermissions.add(permission.name);
        }

        store.startArray(PACKAGE);
        for (PackageInfo pkg : allPackages) {
            store.startGroup();
            store.addResult(NAME, pkg.packageName);
            store.addResult(VERSION_NAME, pkg.versionName);

            collectRequestedPermissions(store, pm, platformPermissions, pkg);
            collectDefinedPermissions(store, platformPermissions, pkg);

            collectionApplicationInfo(store, pm, pkg);

            store.addResult(HAS_DEFAULT_NOTIFICATION_ACCESS,
                    defaultNotificationListeners.contains(pkg.packageName));

            store.addResult(IS_ACTIVE_ADMIN, deviceAdminPackages.contains(pkg.packageName));

            boolean isDefaultAccessibilityComponent = false;
            if (defaultAccessibilityComponent != null) {
              isDefaultAccessibilityComponent = pkg.packageName.equals(
                      defaultAccessibilityComponent.getPackageName()
              );
            }
            store.addResult(DEFAULT_ACCESSIBILITY_SERVICE, isDefaultAccessibilityComponent);

            String sha256_cert = PackageUtil.computePackageSignatureDigest(pkg.packageName);
            store.addResult(SHA256_CERT, sha256_cert);

            String sha256_file = PackageUtil.computePackageFileDigest(pkg);
            store.addResult(SHA256_FILE, sha256_file);

            collectRoles(store, packageRolesData, pkg);

            store.endGroup();
        }
        store.endArray(); // "package"
    }

    private static void collectRequestedPermissions(DeviceInfoStore store,
                                           PackageManager pm,
                                           Set<String> systemPermissions,
                                           PackageInfo pkg) throws IOException
    {
        store.startArray(REQUESTED_PERMISSIONS);
        if (pkg.requestedPermissions != null && pkg.requestedPermissions.length > 0) {
            for (String permission : pkg.requestedPermissions) {
                if (permission == null) continue;

                try {
                    final PermissionInfo pi = pm.getPermissionInfo(permission, 0);

                    store.startGroup();
                    writePermissionsDetails(pi, store, systemPermissions);

                    boolean isGranted = pm.checkPermission(
                            permission, pkg.packageName) == pm.PERMISSION_GRANTED;
                    store.addResult(PERMISSION_IS_GRANTED, isGranted);

                    store.endGroup();
                } catch (PackageManager.NameNotFoundException e) {
                    // ignore unrecognized permission and continue
                }
            }
        }
        store.endArray();
    }

    private static void collectDefinedPermissions(DeviceInfoStore store,
                                                  Set<String> systemPermissions,
                                                  PackageInfo pkg) throws IOException {
        if (pkg.permissions != null && pkg.permissions.length > 0) {
            store.startArray(DEFINED_PERMISSIONS);
            for (PermissionInfo permission : pkg.permissions) {
                if (permission == null) continue;
                // Ignore "android" package defined AOSP permissions.
                if (pkg.packageName.equals(PLATFORM)
                        && isAndroidPermission(permission.name))
                    continue;

                store.startGroup();
                writePermissionsDetails(permission, store, systemPermissions);
                store.endGroup();

            }
            store.endArray();
        }
    }

    private static void collectionApplicationInfo(DeviceInfoStore store,
                                                  PackageManager pm,
                                                  PackageInfo pkg) throws IOException {
        final ApplicationInfo appInfo = pkg.applicationInfo;
        if (appInfo != null) {
            store.addResult(DIR, appInfo.sourceDir);
            store.addResult(SYSTEM_PRIV, appInfo.isPrivilegedApp());

            store.addResult(MIN_SDK, appInfo.minSdkVersion);
            store.addResult(TARGET_SDK, appInfo.targetSdkVersion);

            store.addResult(HAS_SYSTEM_UID, appInfo.uid < Process.FIRST_APPLICATION_UID);

            final boolean canInstall = sharesUidWithInstallerPackage(pm, appInfo.uid);
            store.addResult(SHARES_INSTALL_PERMISSION, canInstall);

            store.addResult(UID, appInfo.uid);
        }
    }

    private static boolean sharesUidWithInstallerPackage(PackageManager pm, int uid) {
        final String[] sharesUidWith = pm.getPackagesForUid(uid);

        if (sharesUidWith == null) {
            return false;
        }

        // Approx 20 permissions per package for rough estimate of sizing
        final int capacity = sharesUidWith.length * 20;
        final List<String> sharedPermissions = new ArrayList<>(capacity);
        for (String pkg :sharesUidWith){
            try {
                final PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);

                if (info.requestedPermissions == null) {
                    continue;
                }

                for (String p : info.requestedPermissions) {
                    if (p != null) {
                        sharedPermissions.add(p);
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                // ignore, continue
            }
        }

        return sharedPermissions.contains(PackageDeviceInfo.INSTALL_PACKAGES_PERMISSION);
    }

    private static void writePermissionsDetails(PermissionInfo pi,
                                                DeviceInfoStore store,
                                                Set<String> systemPermissions) throws IOException {
        final String permissionName = pi.name;
        store.addResult(PERMISSION_NAME, permissionName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            store.addResult(PERMISSION_FLAGS, pi.flags);
        } else {
            store.addResult(PERMISSION_FLAGS, 0);
        }

        store.addResult(PERMISSION_GROUP, pi.group);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            store.addResult(PERMISSION_PROTECTION, pi.getProtection());
            store.addResult(PERMISSION_PROTECTION_FLAGS, pi.getProtectionFlags());
        } else {
            store.addResult(PERMISSION_PROTECTION,
                    pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
            store.addResult(PERMISSION_PROTECTION_FLAGS,
                    pi.protectionLevel & ~PermissionInfo.PROTECTION_MASK_BASE);
        }

        final boolean isPlatformPermission = systemPermissions.contains(permissionName);
        if (isPlatformPermission) {
            final boolean isAndroidPermission = isAndroidPermission(permissionName);
            if (isAndroidPermission) {
            store.addResult(PERMISSION_TYPE, PERMISSION_TYPE_SYSTEM);
            } else {
            store.addResult(PERMISSION_TYPE, PERMISSION_TYPE_OEM);
            }
        } else {
            store.addResult(PERMISSION_TYPE, PERMISSION_TYPE_CUSTOM);
        }
    }

    private Set<String> getActiveDeviceAdminPackages() {
        final DevicePolicyManager dpm = (DevicePolicyManager)
                getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);

        final List<ComponentName> components = dpm.getActiveAdmins();
        if (components == null) {
            return new HashSet<>(0);
        }

        final HashSet<String> packages = new HashSet<>(components.size());
        for (ComponentName component : components) {
            packages.add(component.getPackageName());
        }

        return packages;
    }

    private ComponentName getDefaultAccessibilityComponent() {
        final String defaultAccessibilityServiceComponent =
                getRawDeviceConfig(CONFIG_ACCESSIBILITY_SERVICE);
        return ComponentName.unflattenFromString(defaultAccessibilityServiceComponent);
    }

    /**
     * Parses and returns a set of package ids from a configuration value
     * e.g config_defaultListenerAccessPackages
     **/
    private Set<String> getColonSeparatedPackageList(String name) {
        String raw = getRawDeviceConfig(name);
        String[] packages = raw.split(":");
        return new HashSet<>(Arrays.asList(packages));
    }

    /** Returns the value of a device configuration setting available in android.internal.R.* **/
    private String getRawDeviceConfig(String name) {
        return getContext()
                .getResources()
                .getString(getDeviceResourcesIdentifier(name, "string"));
    }

    private int getDeviceResourcesIdentifier(String name, String type) {
        return getContext()
                .getResources()
                .getIdentifier(name, type, "android");
    }

    /** Return a boolean value to whether the permission is an android permission defined by android package */
    private static boolean isAndroidPermission(String permissionName) {
        if(permissionName.startsWith(PLATFORM_ANDROID_PERMISSION_PREFIX)
            || permissionName.startsWith(PLATFORM_MANIFEST_PERMISSION_PREFIX)
            || ADDITIONAL_ANDROID_PERMISSIONS.contains(permissionName))
            return true;
        return false;
    }

    private static void collectRoles(DeviceInfoStore store,
                                     HashMap<String, List<String>> packageRolesData,
                                     PackageInfo pkg) throws IOException {
        String packageName = pkg.packageName;
        if(packageRolesData.containsKey(packageName)) {
            List<String> roleNames = packageRolesData.get(packageName);

            store.startArray(REQUESTED_ROLES);
            for(String roleName: roleNames) {
                store.startGroup();
                store.addResult(ROLE_NAME, roleName);
                store.endGroup();
            }
            store.endArray();
        }
    }

    /*
        Return a map of PackageName -> List of RoleNames held by that package
    */
    private HashMap<String, List<String>> getPackageRolesData() throws Exception {
        final RoleManager roleManager = getContext().getSystemService(RoleManager.class);
        HashMap<String, List<String>> packageRolesData = new HashMap<>();

        for(String roleName: RolesUtil.ROLE_NAMES) {
            List<String> packageNames = getRoleHolders(roleName, roleManager);

            for(String packageName: packageNames) {
                packageRolesData.putIfAbsent(packageName, new ArrayList<>());
                packageRolesData.get(packageName).add(roleName);
            }
        }
        return packageRolesData;
    }

    public static List<String> getRoleHolders(String roleName, RoleManager roleManager) throws Exception {
        return callWithShellPermissionIdentity(
                () -> roleManager.getRoleHolders(roleName),
                        Manifest.permission.MANAGE_ROLE_HOLDERS);
    }
}

