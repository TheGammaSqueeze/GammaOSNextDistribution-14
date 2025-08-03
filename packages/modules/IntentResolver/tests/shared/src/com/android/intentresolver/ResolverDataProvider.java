/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;
import android.test.mock.MockResources;

import androidx.annotation.NonNull;

/**
 * Utility class used by resolver tests to create mock data
 */
public class ResolverDataProvider {

    static private int USER_SOMEONE_ELSE = 10;

    static ResolvedComponentInfo createResolvedComponentInfo(int i) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, UserHandle.USER_CURRENT));
    }

    public static ResolvedComponentInfo createResolvedComponentInfo(int i,
            UserHandle resolvedForUser) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, UserHandle.USER_CURRENT, resolvedForUser));
    }

    static ResolvedComponentInfo createResolvedComponentInfo(
            ComponentName componentName, Intent intent) {
        return new ResolvedComponentInfo(
                componentName,
                intent,
                createResolveInfo(componentName, UserHandle.USER_CURRENT));
    }

    public static ResolvedComponentInfo createResolvedComponentInfo(
            ComponentName componentName, Intent intent, UserHandle resolvedForUser) {
        return new ResolvedComponentInfo(
                componentName,
                intent,
                createResolveInfo(componentName, UserHandle.USER_CURRENT, resolvedForUser));
    }

    static ResolvedComponentInfo createResolvedComponentInfoWithOtherId(int i) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, USER_SOMEONE_ELSE));
    }

    public static ResolvedComponentInfo createResolvedComponentInfoWithOtherId(int i,
                                                                               UserHandle resolvedForUser) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, USER_SOMEONE_ELSE, resolvedForUser));
    }

    static ResolvedComponentInfo createResolvedComponentInfoWithOtherId(int i, int userId) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, userId));
    }

    public static ResolvedComponentInfo createResolvedComponentInfoWithOtherId(int i,
            int userId, UserHandle resolvedForUser) {
        return new ResolvedComponentInfo(
                createComponentName(i),
                createResolverIntent(i),
                createResolveInfo(i, userId, resolvedForUser));
    }

    public static ComponentName createComponentName(int i) {
        final String name = "component" + i;
        return new ComponentName("foo.bar." + name, name);
    }

    public static ResolveInfo createResolveInfo(int i, int userId) {
        return createResolveInfo(i, userId, UserHandle.of(userId));
    }

    public static ResolveInfo createResolveInfo(int i, int userId, UserHandle resolvedForUser) {
        return createResolveInfo(createActivityInfo(i), userId, resolvedForUser);
    }

    public static ResolveInfo createResolveInfo(ComponentName componentName, int userId) {
        return createResolveInfo(componentName, userId, UserHandle.of(userId));
    }

    public static ResolveInfo createResolveInfo(
            ComponentName componentName, int userId, UserHandle resolvedForUser) {
        return createResolveInfo(createActivityInfo(componentName), userId, resolvedForUser);
    }

    public static ResolveInfo createResolveInfo(
            ActivityInfo activityInfo, int userId, UserHandle resolvedForUser) {
        final ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.targetUserId = userId;
        resolveInfo.userHandle = resolvedForUser;
        return resolveInfo;
    }

    static ActivityInfo createActivityInfo(int i) {
        ActivityInfo ai = new ActivityInfo();
        ai.name = "activity_name" + i;
        ai.packageName = "foo_bar" + i;
        ai.enabled = true;
        ai.exported = true;
        ai.permission = null;
        ai.applicationInfo = createApplicationInfo();
        return ai;
    }

    static ActivityInfo createActivityInfo(ComponentName componentName) {
        ActivityInfo ai = new ActivityInfo();
        ai.name = componentName.getClassName();
        ai.packageName = componentName.getPackageName();
        ai.enabled = true;
        ai.exported = true;
        ai.permission = null;
        ai.applicationInfo = createApplicationInfo();
        ai.applicationInfo.packageName = componentName.getPackageName();
        return ai;
    }

    static ApplicationInfo createApplicationInfo() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.name = "app_name";
        ai.packageName = "foo.bar";
        ai.enabled = true;
        return ai;
    }

    static class PackageManagerMockedInfo {
        public Context ctx;
        public ApplicationInfo appInfo;
        public ActivityInfo activityInfo;
        public ResolveInfo resolveInfo;
        public String setAppLabel;
        public String setActivityLabel;
        public String setResolveInfoLabel;
    }

    /** Create a {@link PackageManagerMockedInfo} with all distinct labels. */
    static PackageManagerMockedInfo createPackageManagerMockedInfo(boolean hasOverridePermission) {
        return createPackageManagerMockedInfo(
                hasOverridePermission, "app_label", "activity_label", "resolve_info_label");
    }

    static PackageManagerMockedInfo createPackageManagerMockedInfo(
            boolean hasOverridePermission,
            String appLabel,
            String activityLabel,
            String resolveInfoLabel) {
        MockContext ctx = new MockContext() {
            @Override
            public PackageManager getPackageManager() {
                return new MockPackageManager() {
                    @Override
                    public int checkPermission(String permName, String pkgName) {
                        if (hasOverridePermission) return PERMISSION_GRANTED;
                        return PERMISSION_DENIED;
                    }
                };
            }

            @Override
            public Resources getResources() {
                return new MockResources() {
                    @NonNull
                    @Override
                    public String getString(int id) throws NotFoundException {
                        if (id == 1) return appLabel;
                        if (id == 2) return activityLabel;
                        if (id == 3) return resolveInfoLabel;
                        throw new NotFoundException();
                    }
                };
            }
        };

        ApplicationInfo appInfo = new ApplicationInfo() {
            @NonNull
            @Override
            public CharSequence loadLabel(@NonNull PackageManager pm) {
                return appLabel;
            }
        };
        appInfo.labelRes = 1;

        ActivityInfo activityInfo = new ActivityInfo() {
            @NonNull
            @Override
            public CharSequence loadLabel(@NonNull PackageManager pm) {
                return activityLabel;
            }
        };
        activityInfo.labelRes = 2;
        activityInfo.applicationInfo = appInfo;

        ResolveInfo resolveInfo = new ResolveInfo() {
            @NonNull
            @Override
            public CharSequence loadLabel(@NonNull PackageManager pm) {
                return resolveInfoLabel;
            }
        };
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.resolvePackageName = "super.fake.packagename";
        resolveInfo.labelRes = 3;

        PackageManagerMockedInfo mockedInfo = new PackageManagerMockedInfo();
        mockedInfo.activityInfo = activityInfo;
        mockedInfo.appInfo = appInfo;
        mockedInfo.ctx = ctx;
        mockedInfo.resolveInfo = resolveInfo;
        mockedInfo.setAppLabel = appLabel;
        mockedInfo.setActivityLabel = activityLabel;
        mockedInfo.setResolveInfoLabel = resolveInfoLabel;

        return mockedInfo;
    }

    static Intent createResolverIntent(int i) {
        return new Intent("intentAction" + i);
    }
}
