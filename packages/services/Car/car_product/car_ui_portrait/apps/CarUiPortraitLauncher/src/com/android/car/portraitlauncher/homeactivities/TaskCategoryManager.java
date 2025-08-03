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

package com.android.car.portraitlauncher.homeactivities;

import android.annotation.MainThread;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.car.media.CarMediaIntents;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.car.app.CarContext;

import com.android.car.carlauncher.AppGridActivity;
import com.android.car.carlauncher.CarLauncherUtils;
import com.android.car.portraitlauncher.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the task categories for {@link CarUiPortraitHomeScreen}, check which category a task
 * belongs to.
 */
class TaskCategoryManager {
    public static final String TAG = TaskCategoryManager.class.getSimpleName();
    private static final boolean DBG = Build.IS_DEBUGGABLE;
    /** Stub geo data to help query navigation intent. */
    private static final String STUB_GEO_DATA = "geo:0.0,0,0";

    private final ComponentName mAppGridActivityComponent;
    private final ComponentName mNotificationActivityComponent;
    private final ComponentName mRecentsActivityComponent;
    private final ComponentName mCalmModeComponent;
    private final ArraySet<ComponentName> mIgnoreOpeningRootTaskViewComponentsSet;
    private final Set<ComponentName> mFullScreenActivities;
    private final Set<ComponentName> mBackgroundActivities;
    private final Context mContext;
    private final ApplicationInstallUninstallReceiver mApplicationInstallUninstallReceiver;
    private final Set<OnApplicationInstallUninstallListener>
            mOnApplicationInstallUninstallListeners;
    private ComponentName mCurrentBackgroundApp;

    TaskCategoryManager(Context context) {
        mContext = context;

        mFullScreenActivities = new HashSet<>();
        mBackgroundActivities = new HashSet<>();
        mIgnoreOpeningRootTaskViewComponentsSet = convertToComponentNames(mContext.getResources()
                .getStringArray(R.array.config_ignoreOpeningForegroundDA));
        mAppGridActivityComponent = new ComponentName(context, AppGridActivity.class);
        mNotificationActivityComponent = ComponentName.unflattenFromString(
                mContext.getResources().getString(R.string.config_notificationActivity));
        mRecentsActivityComponent = ComponentName.unflattenFromString(mContext.getResources()
                .getString(com.android.internal.R.string.config_recentsComponentName));
        mCalmModeComponent = ComponentName.unflattenFromString(mContext.getResources()
                .getString(R.string.config_calmMode_componentName));
        mOnApplicationInstallUninstallListeners = new HashSet<>();

        updateFullScreenActivities();
        updateBackgroundActivityMap();

        mApplicationInstallUninstallReceiver = registerApplicationInstallUninstallReceiver(
                mContext);
    }

    /**
     * Refresh {@code mFullScreenActivities} and {@code mBackgroundActivities}.
     */
    void refresh() {
        updateFullScreenActivities();
        updateBackgroundActivityMap();
    }

    static boolean isHomeIntent(TaskInfo taskInfo) {
        return taskInfo.baseIntent != null
                && taskInfo.baseIntent.getCategories() != null
                && taskInfo.baseIntent.getCategories().contains(Intent.CATEGORY_HOME);
    }

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    /**
     * @return {@code true} if current task in panel was launched using media intent.
     */
    public static boolean isMediaApp(TaskInfo taskInfo) {
        return taskInfo != null && taskInfo.baseIntent != null
                && CarMediaIntents.ACTION_MEDIA_TEMPLATE.equals(taskInfo.baseIntent.getAction());
    }

    private static ArraySet<ComponentName> convertToComponentNames(String[] componentStrings) {
        ArraySet<ComponentName> componentNames = new ArraySet<>(componentStrings.length);
        for (int i = componentStrings.length - 1; i >= 0; i--) {
            componentNames.add(ComponentName.unflattenFromString(componentStrings[i]));
        }
        return componentNames;
    }

    void updateFullScreenActivities() {
        mFullScreenActivities.clear();
        Intent voiceIntent = new Intent(Intent.ACTION_VOICE_ASSIST, /* uri= */ null);
        List<ResolveInfo> result = mContext.getPackageManager().queryIntentActivitiesAsUser(
                voiceIntent, PackageManager.MATCH_ALL, ActivityManager.getCurrentUser());

        for (ResolveInfo info : result) {
            if (info == null || info.activityInfo == null
                    || info.activityInfo.getComponentName() == null) {
                continue;
            }
            if (mFullScreenActivities.add(info.activityInfo.getComponentName())) {
                logIfDebuggable("adding the following component to show on fullscreen: "
                        + info.activityInfo.getComponentName());
            }
        }

        mFullScreenActivities.addAll(convertToComponentNames(mContext.getResources()
                .getStringArray(R.array.config_fullScreenActivities)));
    }

    void updateBackgroundActivityMap() {
        mBackgroundActivities.clear();
        Intent intent = new Intent(CarContext.ACTION_NAVIGATE, Uri.parse(STUB_GEO_DATA));
        List<ResolveInfo> result = mContext.getPackageManager().queryIntentActivitiesAsUser(
                intent, PackageManager.MATCH_ALL, ActivityManager.getCurrentUser());

        for (ResolveInfo info : result) {
            if (info == null || info.activityInfo == null
                    || info.activityInfo.getComponentName() == null) {
                continue;
            }
            mBackgroundActivities.add(info.getComponentInfo().getComponentName());
        }

        mBackgroundActivities.addAll(convertToComponentNames(mContext.getResources()
                .getStringArray(R.array.config_backgroundActivities)));
        mBackgroundActivities.add(mCalmModeComponent);
    }

    /**
     * Returns whether the {@code TaskCategoryManager} is ready. If maps intent can be resolved,
     * the {@code TaskCategoryManager} is ready.
     */
    public boolean isReady() {
        Intent intent = CarLauncherUtils.getMapsIntent(mContext);
        return intent.resolveActivity(mContext.getPackageManager()) != null;
    }

    void registerOnApplicationInstallUninstallListener(
            OnApplicationInstallUninstallListener onApplicationInstallUninstallListener) {
        mOnApplicationInstallUninstallListeners.add(onApplicationInstallUninstallListener);
    }

    void unregisterOnApplicationInstallUninstallListener(
            OnApplicationInstallUninstallListener onApplicationInstallUninstallListener) {
        mOnApplicationInstallUninstallListeners.remove(onApplicationInstallUninstallListener);
    }

    boolean isBackgroundApp(TaskInfo taskInfo) {
        return mBackgroundActivities.contains(taskInfo.baseActivity);
    }

    boolean isBackgroundApp(ComponentName componentName) {
        return mBackgroundActivities.contains(componentName);
    }

    boolean isCurrentBackgroundApp(ComponentName componentName) {
        return mCurrentBackgroundApp != null && mCurrentBackgroundApp.equals(componentName);
    }

    ComponentName getCurrentBackgroundApp() {
        return mCurrentBackgroundApp;
    }

    void setCurrentBackgroundApp(ComponentName componentName) {
        mCurrentBackgroundApp = componentName;
    }

    boolean isAppGridActivity(ComponentName componentName) {
        return mAppGridActivityComponent.equals(componentName);
    }

    boolean isAppGridActivity(TaskInfo taskInfo) {
        return mAppGridActivityComponent.equals(taskInfo.baseActivity);
    }

    ComponentName getAppGridActivity() {
        return mAppGridActivityComponent;
    }

    List<ComponentName> getFullScreenActivitiesList() {
        return mFullScreenActivities.stream().toList();
    }

    List<ComponentName> getBackgroundActivitiesList() {
        return mBackgroundActivities.stream().toList();
    }

    boolean isFullScreenActivity(TaskInfo taskInfo) {
        return mFullScreenActivities.contains(taskInfo.baseActivity);
    }

    boolean isNotificationActivity(TaskInfo taskInfo) {
        return mNotificationActivityComponent.equals(taskInfo.baseActivity);
    }

    boolean isRecentsActivity(TaskInfo taskInfo) {
        return mRecentsActivityComponent.equals(taskInfo.baseActivity);
    }

    boolean isCalmModeActivity(TaskInfo taskInfo) {
        return mCalmModeComponent.equals(taskInfo.baseActivity);
    }

    boolean shouldIgnoreOpeningForegroundDA(TaskInfo taskInfo) {
        return taskInfo.baseIntent != null && mIgnoreOpeningRootTaskViewComponentsSet.contains(
                taskInfo.baseIntent.getComponent());
    }

    public void onDestroy() {
        mOnApplicationInstallUninstallListeners.clear();
        mContext.unregisterReceiver(mApplicationInstallUninstallReceiver);
    }

    /**
     * Returns a list of activities that are tracked as background activities with given
     * {@code packageName}.
     */
    List<ComponentName> getBackgroundActivitiesFromPackage(String packageName) {
        List<ComponentName> list = new ArrayList<>();
        for (ComponentName componentName : mBackgroundActivities) {
            if (componentName.getPackageName().equals(packageName)) {
                list.add(componentName);
            }
        }
        return list;
    }

    private ApplicationInstallUninstallReceiver registerApplicationInstallUninstallReceiver(
            Context context) {
        ApplicationInstallUninstallReceiver
                installUninstallReceiver = new ApplicationInstallUninstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        context.registerReceiver(installUninstallReceiver, filter);
        return installUninstallReceiver;
    }

    /**
     * Listener for application installation and uninstallation.
     */
    interface OnApplicationInstallUninstallListener {
        /**
         * Invoked when intent with {@link Intent.ACTION_PACKAGE_ADDED) is received.
         */
        void onAppInstalled(String packageName);
        /**
         * Invoked when intent with {@link Intent.ACTION_PACKAGE_REMOVED}} is received.
         */
        void onAppUninstall(String packageName);
    }

    private class ApplicationInstallUninstallReceiver extends BroadcastReceiver {
        @MainThread
        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            String action = intent.getAction();
            if (TextUtils.isEmpty(packageName) && TextUtils.isEmpty(action)) {
                logIfDebuggable(
                        "Invalid intent with packageName=" + packageName + ", action=" + action);
                // Ignoring empty announcements
                return;
            }

            refresh();

            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                for (OnApplicationInstallUninstallListener listener :
                        mOnApplicationInstallUninstallListeners) {
                    listener.onAppInstalled(packageName);
                }
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                for (OnApplicationInstallUninstallListener listener :
                        mOnApplicationInstallUninstallListeners) {
                    listener.onAppUninstall(packageName);
                }
            } else {
                logIfDebuggable("Skip action " + action + " for package" + packageName);
            }
        }
    }
}
