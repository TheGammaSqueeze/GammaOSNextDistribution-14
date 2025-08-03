/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.role.controller.behavior;

import android.app.ActivityManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.service.voice.VoiceInteractionService;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.model.VisibilityMixin;
import com.android.role.controller.util.UserUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class for behavior of the assistant role.
 */
public class AssistantRoleBehavior implements RoleBehavior {

    private static final String LOG_TAG = AssistantRoleBehavior.class.getSimpleName();

    @Override
    public void onRoleAddedAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        if (packageManager.isDeviceUpgrading()) {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            List<String> packageNames = roleManager.getRoleHoldersAsUser(role.getName(), user);
            if (packageNames.isEmpty()) {
                // If the device was upgraded, and there isn't any legacy role holders, it means
                // user selected "None" in Settings and we need to keep that.
                role.onNoneHolderSelectedAsUser(user, context);
            }
        }
    }

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return !UserUtils.isProfile(user, context);
    }

    @Nullable
    @Override
    public List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return getQualifyingPackagesInternal(null, user, context);
    }

    @Nullable
    @Override
    public Boolean isPackageQualifiedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        return !getQualifyingPackagesInternal(packageName, user, context)
                .isEmpty();
    }

    @NonNull
    private List<String> getQualifyingPackagesInternal(@Nullable String filterPackageName,
            @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        ActivityManager userActivityManager = userContext.getSystemService(ActivityManager.class);
        PackageManager userPackageManager = userContext.getPackageManager();
        Set<String> packageNames = new ArraySet<>();

        if (!userActivityManager.isLowRamDevice()) {
            Intent serviceIntent = new Intent(VoiceInteractionService.SERVICE_INTERFACE);
            if (filterPackageName != null) {
                serviceIntent.setPackage(filterPackageName);
            }
            List<ResolveInfo> serviceResolveInfos = userPackageManager.queryIntentServices(
                    serviceIntent, PackageManager.GET_META_DATA
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            int serviceResolveInfosSize = serviceResolveInfos.size();
            for (int i = 0; i < serviceResolveInfosSize; i++) {
                ResolveInfo serviceResolveInfo = serviceResolveInfos.get(i);

                ServiceInfo serviceInfo = serviceResolveInfo.serviceInfo;
                if (!isAssistantVoiceInteractionService(userPackageManager, serviceInfo)) {
                    if (filterPackageName != null) {
                        Log.w(LOG_TAG, "Package " + filterPackageName
                                + " has an unqualified voice interaction service");
                    }
                    continue;
                }

                packageNames.add(serviceInfo.packageName);
            }
        }

        Intent activityIntent = new Intent(Intent.ACTION_ASSIST);
        if (filterPackageName != null) {
            activityIntent.setPackage(filterPackageName);
        }
        List<ResolveInfo> activityResolveInfos = userPackageManager.queryIntentActivities(
                activityIntent, PackageManager.MATCH_DEFAULT_ONLY
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        int activityResolveInfosSize = activityResolveInfos.size();
        for (int i = 0; i < activityResolveInfosSize; i++) {
            ResolveInfo activityResolveInfo = activityResolveInfos.get(i);

            ActivityInfo activityInfo = activityResolveInfo.activityInfo;
            if (!activityInfo.exported) {
                continue;
            }

            packageNames.add(activityInfo.packageName);
        }

        return new ArrayList<>(packageNames);
    }

    private boolean isAssistantVoiceInteractionService(@NonNull PackageManager pm,
            @NonNull ServiceInfo si) {
        if (!android.Manifest.permission.BIND_VOICE_INTERACTION.equals(si.permission)) {
            return false;
        }

        try (XmlResourceParser parser = si.loadXmlMetaData(pm,
                VoiceInteractionService.SERVICE_META_DATA)) {
            if (parser == null) {
                return false;
            }

            int type;
            do {
                type = parser.next();
            } while (type != XmlResourceParser.END_DOCUMENT && type != XmlResourceParser.START_TAG);

            String sessionService = null;
            String recognitionService = null;
            boolean supportsAssist = false;

            AttributeSet attrs = Xml.asAttributeSet(parser);
            int numAttrs = attrs.getAttributeCount();
            for (int i = 0; i < numAttrs; i++) {
                switch (attrs.getAttributeNameResource(i)) {
                    case android.R.attr.sessionService:
                        sessionService = attrs.getAttributeValue(i);
                        break;
                    case android.R.attr.recognitionService:
                        recognitionService = attrs.getAttributeValue(i);
                        break;
                    case android.R.attr.supportsAssist:
                        supportsAssist = attrs.getAttributeBooleanValue(i, false);
                        break;
                }
            }

            if (sessionService == null || recognitionService == null || !supportsAssist) {
                return false;
            }
        } catch (XmlPullParserException | IOException | Resources.NotFoundException ignored) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDefaultAssistant", false, user, context);
    }
}
