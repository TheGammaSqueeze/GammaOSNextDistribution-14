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

package com.android.systemui.car.privacy;

import static android.os.UserHandle.USER_SYSTEM;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.systemui.privacy.PrivacyDialog;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.privacy.logging.PrivacyLogger;
import com.android.systemui.settings.UserTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link
 * com.android.systemui.car.privacy.SensorQcPanel.SensorPrivacyElementsProvider}
 */
public abstract class PrivacyElementsProviderImpl implements
        SensorQcPanel.SensorPrivacyElementsProvider {
    private static final String TAG = "PrivacyElementsProviderImpl";
    private static final String EMPTY_APP_NAME = "";

    private static final Map<String, PrivacyType> PERM_GROUP_TO_PRIVACY_TYPE_MAP =
            Map.of(Manifest.permission_group.CAMERA, PrivacyType.TYPE_CAMERA,
                    Manifest.permission_group.MICROPHONE, PrivacyType.TYPE_MICROPHONE,
                    Manifest.permission_group.LOCATION, PrivacyType.TYPE_LOCATION);


    private final PermissionManager mPermissionManager;
    private final UserTracker mUserTracker;
    private final PrivacyLogger mPrivacyLogger;
    private final PackageManager mPackageManager;
    private final PrivacyItemController mPrivacyItemController;
    private final UserManager mUserManager;

    public PrivacyElementsProviderImpl(
            Context context,
            PermissionManager permissionManager,
            PackageManager packageManager,
            PrivacyItemController privacyItemController,
            UserTracker userTracker,
            PrivacyLogger privacyLogger) {
        mPermissionManager = permissionManager;
        mPackageManager = packageManager;
        mPrivacyItemController = privacyItemController;
        mUserTracker = userTracker;
        mPrivacyLogger = privacyLogger;

        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    public List<PrivacyDialog.PrivacyElement> getPrivacyElements() {
        List<PrivacyDialog.PrivacyElement> elements = filterAndSort(createPrivacyElements());
        mPrivacyLogger.logShowDialogContents(elements);
        return elements;
    }

    protected abstract PrivacyType getProviderPrivacyType();

    private List<PrivacyDialog.PrivacyElement> createPrivacyElements() {
        List<UserInfo> userInfos = mUserTracker.getUserProfiles();
        List<PermissionGroupUsage> permGroupUsages = getPermGroupUsages();
        mPrivacyLogger.logUnfilteredPermGroupUsage(permGroupUsages);
        List<PrivacyDialog.PrivacyElement> items = new ArrayList<>();

        permGroupUsages.forEach(usage -> {
            PrivacyType type =
                    verifyType(PERM_GROUP_TO_PRIVACY_TYPE_MAP.get(usage.getPermissionGroupName()));
            if (type == null) return;

            int userId = UserHandle.getUserId(usage.getUid());
            Optional<UserInfo> optionalUserInfo = userInfos.stream()
                    .filter(ui -> ui.id == userId)
                    .findFirst();
            if (!optionalUserInfo.isPresent() && userId != USER_SYSTEM) return;

            UserInfo userInfo =
                    optionalUserInfo.orElseGet(() -> mUserManager.getUserInfo(USER_SYSTEM));

            String appName = usage.isPhoneCall()
                    ? EMPTY_APP_NAME
                    : getLabelForPackage(usage.getPackageName(), usage.getUid());

            items.add(
                    new PrivacyDialog.PrivacyElement(
                            type,
                            usage.getPackageName(),
                            userId,
                            appName,
                            usage.getAttributionTag(),
                            /* attributionLabel= */ null,
                            usage.getProxyLabel(),
                            usage.getLastAccessTimeMillis(),
                            usage.isActive(),
                            userInfo.isManagedProfile(),
                            usage.isPhoneCall(),
                            usage.getPermissionGroupName(),
                            /* navigationIntent= */ null)
            );
        });

        return items;
    }

    private Optional<ApplicationInfo> getApplicationInfo(String packageName, int userId) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = mPackageManager
                    .getApplicationInfoAsUser(packageName, /* flags= */ 0, userId);
            return Optional.of(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Application info not found for: " + packageName);
            return Optional.empty();
        }
    }

    @WorkerThread
    private List<PermissionGroupUsage> getPermGroupUsages() {
        return mPermissionManager.getIndicatorAppOpUsageData();
    }

    @WorkerThread
    private String getLabelForPackage(String packageName, int userId) {
        Optional<ApplicationInfo> applicationInfo = getApplicationInfo(packageName, userId);

        if (!applicationInfo.isPresent()) return packageName;

        return (String) applicationInfo.get().loadLabel(mPackageManager);
    }

    /**
     * If {@link PrivacyType} is available then returns the argument, or else returns {@code null}.
     */
    @Nullable
    private PrivacyType verifyType(PrivacyType type) {
        if ((type == PrivacyType.TYPE_CAMERA || type == PrivacyType.TYPE_MICROPHONE)
                && mPrivacyItemController.getMicCameraAvailable()) {
            return type;
        } else if (type == PrivacyType.TYPE_LOCATION
                && mPrivacyItemController.getLocationAvailable()) {
            return type;
        } else {
            return null;
        }
    }

    private List<PrivacyDialog.PrivacyElement> filterAndSort(
            List<PrivacyDialog.PrivacyElement> list) {
        return list.stream()
                .filter(it -> it.getType() == getProviderPrivacyType())
                .sorted(new PrivacyElementComparator())
                .collect(Collectors.toList());
    }

    private static class PrivacyElementComparator
            implements Comparator<PrivacyDialog.PrivacyElement> {
        @Override
        public int compare(PrivacyDialog.PrivacyElement it1, PrivacyDialog.PrivacyElement it2) {
            if (it1.getActive() && !it2.getActive()) {
                return 1;
            } else if (!it1.getActive() && it2.getActive()) {
                return -1;
            } else {
                return Long.compare(it1.getLastActiveTimestamp(), it2.getLastActiveTimestamp());
            }
        }
    }
}
