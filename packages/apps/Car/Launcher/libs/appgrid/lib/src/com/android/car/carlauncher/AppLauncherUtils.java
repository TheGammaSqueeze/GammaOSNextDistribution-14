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

package com.android.car.carlauncher;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;
import static android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS;
import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Activity;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.carlaunchercommon.shortcuts.AppInfoShortcutItem;
import com.android.car.carlaunchercommon.shortcuts.ForceStopShortcutItem;
import com.android.car.carlaunchercommon.shortcuts.PinShortcutItem;
import com.android.car.dockutil.Flags;
import com.android.car.dockutil.events.DockEventSenderHelper;
import com.android.car.media.common.source.MediaSourceUtil;
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup;

import com.google.common.collect.Sets;

import java.lang.annotation.Retention;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Util class that contains helper method used by app launcher classes.
 */
public class AppLauncherUtils {
    private static final String TAG = "AppLauncherUtils";
    private static final String ANDROIDX_CAR_APP_LAUNCHABLE = "androidx.car.app.launchable";

    @Retention(SOURCE)
    @IntDef({APP_TYPE_LAUNCHABLES, APP_TYPE_MEDIA_SERVICES})
    @interface AppTypes {}

    static final int APP_TYPE_LAUNCHABLES = 1;
    static final int APP_TYPE_MEDIA_SERVICES = 2;

    // This value indicates if TOS has not been accepted by the user
    private static final String TOS_NOT_ACCEPTED = "1";
    // This value indicates if TOS is in uninitialized state
    private static final String TOS_UNINITIALIZED = "0";
    static final String TOS_DISABLED_APPS_SEPARATOR = ",";
    static final String PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";";

    // Max no. of uses tags in automotiveApp XML. This is an arbitrary limit to be defensive
    // to bad input.
    private static final int MAX_APP_TYPES = 64;
    private static final String PACKAGE_URI_PREFIX = "package:";

    private AppLauncherUtils() {
    }

    /**
     * Comparator for {@link AppMetaData} that sorts the list
     * by the "displayName" property in ascending order.
     */
    static final Comparator<AppMetaData> ALPHABETICAL_COMPARATOR = Comparator
            .comparing(AppMetaData::getDisplayName, String::compareToIgnoreCase);

    /**
     * Helper method that launches the app given the app's AppMetaData.
     */
    public static void launchApp(Context context, Intent intent) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(context.getDisplay().getDisplayId());
        context.startActivity(intent, options.toBundle());
    }

    /** Bundles application and services info. */
    static class LauncherAppsInfo {
        /*
         * Map of all car launcher components' (including launcher activities and media services)
         * metadata keyed by ComponentName.
         */
        private final Map<ComponentName, AppMetaData> mLaunchables;

        /** Map of all the media services keyed by ComponentName. */
        private final Map<ComponentName, ResolveInfo> mMediaServices;

        LauncherAppsInfo(@NonNull Map<ComponentName, AppMetaData> launchablesMap,
                @NonNull Map<ComponentName, ResolveInfo> mediaServices) {
            mLaunchables = launchablesMap;
            mMediaServices = mediaServices;
        }

        /** Returns true if all maps are empty. */
        boolean isEmpty() {
            return mLaunchables.isEmpty() && mMediaServices.isEmpty();
        }

        /**
         * Returns whether the given componentName is a media service.
         */
        boolean isMediaService(ComponentName componentName) {
            return mMediaServices.containsKey(componentName);
        }

        /** Returns the {@link AppMetaData} for the given componentName. */
        @Nullable
        AppMetaData getAppMetaData(ComponentName componentName) {
            return mLaunchables.get(componentName);
        }

        /** Returns a new list of all launchable components' {@link AppMetaData}. */
        @NonNull
        List<AppMetaData> getLaunchableComponentsList() {
            return new ArrayList<>(mLaunchables.values());
        }

        /** Returns list of Media Services for the launcher **/
        @NonNull
        Map<ComponentName, ResolveInfo> getMediaServices() {
            return mMediaServices;
        }
    }

    private static final LauncherAppsInfo EMPTY_APPS_INFO = new LauncherAppsInfo(
            Collections.emptyMap(), Collections.emptyMap());

    /*
     * Gets the media source in a given package. If there are multiple sources in the package,
     * returns the first one.
     */
    static ComponentName getMediaSource(@NonNull PackageManager packageManager,
            @NonNull String packageName) {
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

        List<ResolveInfo> mediaServices = packageManager.queryIntentServices(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER);

        if (mediaServices == null || mediaServices.isEmpty()) {
            return null;
        }
        String defaultService = mediaServices.get(0).serviceInfo.name;
        if (!TextUtils.isEmpty(defaultService)) {
            return new ComponentName(packageName, defaultService);
        }
        return null;
    }

    /**
     * Gets all the components that we want to see in the launcher in unsorted order, including
     * launcher activities and media services.
     *
     * @param appsToHide            A (possibly empty) list of apps (package names) to hide
     * @param appTypes              Types of apps to show (e.g.: all, or media sources only)
     * @param openMediaCenter       Whether launcher should navigate to media center when the
     *                              user selects a media source.
     * @param launcherApps          The {@link LauncherApps} system service
     * @param carPackageManager     The {@link CarPackageManager} system service
     * @param packageManager        The {@link PackageManager} system service
     *                              of such apps are always excluded.
     * @param carMediaManager       The {@link CarMediaManager} system service
     * @return a new {@link LauncherAppsInfo}
     */
    @NonNull
    static LauncherAppsInfo getLauncherApps(
            Context context,
            @NonNull Set<String> appsToHide,
            @AppTypes int appTypes,
            boolean openMediaCenter,
            LauncherApps launcherApps,
            CarPackageManager carPackageManager,
            PackageManager packageManager,
            CarMediaManager carMediaManager,
            ShortcutsListener shortcutsListener,
            String mirroringAppPkgName,
            Intent mirroringAppRedirect) {

        if (launcherApps == null || carPackageManager == null || packageManager == null
                || carMediaManager == null) {
            return EMPTY_APPS_INFO;
        }

        // Using new list since we require a mutable list to do removeIf.
        List<ResolveInfo> mediaServices = new ArrayList<>();
        mediaServices.addAll(
                packageManager.queryIntentServices(
                        new Intent(MediaBrowserService.SERVICE_INTERFACE),
                        PackageManager.GET_RESOLVED_FILTER));

        List<LauncherActivityInfo> availableActivities =
                launcherApps.getActivityList(null, Process.myUserHandle());

        int launchablesSize = mediaServices.size() + availableActivities.size();
        Map<ComponentName, AppMetaData> launchablesMap = new HashMap<>(launchablesSize);
        Map<ComponentName, ResolveInfo> mediaServicesMap = new HashMap<>(mediaServices.size());
        Set<String> mEnabledPackages = new ArraySet<>(launchablesSize);
        Set<String> tosDisabledPackages = getTosDisabledPackages(context);
        Set<ComponentName> mediaServiceComponents = mediaServices.stream()
                .map(resolveInfo -> new ComponentName(resolveInfo.serviceInfo.packageName,
                        resolveInfo.serviceInfo.name))
                .collect(Collectors.toSet());

        Set<String> customMediaComponents = Sets.newHashSet(
                context.getResources().getStringArray(
                        com.android.car.media.common.R.array.custom_media_packages));

        // Process media services
        if ((appTypes & APP_TYPE_MEDIA_SERVICES) != 0) {
            for (ResolveInfo info : mediaServices) {
                String packageName = info.serviceInfo.packageName;
                String className = info.serviceInfo.name;
                ComponentName componentName = new ComponentName(packageName, className);
                mediaServicesMap.put(componentName, info);
                mEnabledPackages.add(packageName);
                if (shouldAddToLaunchables(context, componentName, appsToHide,
                        customMediaComponents, appTypes, APP_TYPE_MEDIA_SERVICES)) {
                    CharSequence displayName = info.serviceInfo.loadLabel(packageManager);
                    AppMetaData appMetaData = new AppMetaData(
                            displayName,
                            componentName,
                            info.serviceInfo.loadIcon(packageManager),
                            /* isDistractionOptimized= */ true,
                            /* isMirroring = */ false,
                            /* isDisabledByTos= */ tosDisabledPackages.contains(packageName),
                            contextArg -> {
                                if (openMediaCenter) {
                                    AppLauncherUtils.launchApp(contextArg,
                                            createMediaLaunchIntent(componentName));
                                } else {
                                    selectMediaSourceAndFinish(contextArg, componentName,
                                            carMediaManager);
                                }
                            },
                            buildShortcuts(componentName, displayName, shortcutsListener,
                                    carMediaManager, mediaServiceComponents));
                    launchablesMap.put(componentName, appMetaData);
                }
            }
        }

        // Process activities
        if ((appTypes & APP_TYPE_LAUNCHABLES) != 0) {
            for (LauncherActivityInfo info : availableActivities) {
                ComponentName componentName = info.getComponentName();
                mEnabledPackages.add(componentName.getPackageName());
                if (shouldAddToLaunchables(context, componentName, appsToHide,
                        customMediaComponents, appTypes, APP_TYPE_LAUNCHABLES)) {
                    boolean isDistractionOptimized =
                            isActivityDistractionOptimized(carPackageManager,
                                    componentName.getPackageName(), info.getName());
                    boolean isDisabledByTos = tosDisabledPackages
                            .contains(componentName.getPackageName());

                    CharSequence displayName = info.getLabel();
                    boolean isMirroring = componentName.getPackageName()
                            .equals(mirroringAppPkgName);
                    AppMetaData appMetaData = new AppMetaData(
                            displayName,
                            componentName,
                            info.getBadgedIcon(0),
                            isDistractionOptimized,
                            isMirroring,
                            isDisabledByTos,
                            contextArg -> {
                                if (componentName.getPackageName().equals(mirroringAppPkgName)) {
                                    Log.d(TAG, "non-media service package name "
                                            + "equals mirroring pkg name");
                                }
                                AppLauncherUtils.launchApp(contextArg,
                                        isMirroring ? mirroringAppRedirect :
                                                createAppLaunchIntent(componentName));
                            },
                            buildShortcuts(componentName, displayName, shortcutsListener,
                                    carMediaManager, mediaServiceComponents));
                    launchablesMap.put(componentName, appMetaData);
                }
            }

            List<ResolveInfo> disabledActivities = getDisabledActivities(context, packageManager,
                    mEnabledPackages);
            for (ResolveInfo info : disabledActivities) {
                String packageName = info.activityInfo.packageName;
                String className = info.activityInfo.name;
                ComponentName componentName = new ComponentName(packageName, className);
                if (!shouldAddToLaunchables(context, componentName, appsToHide,
                        customMediaComponents, appTypes, APP_TYPE_LAUNCHABLES)) {
                    continue;
                }
                boolean isDistractionOptimized =
                        isActivityDistractionOptimized(carPackageManager, packageName, className);
                boolean isDisabledByTos = tosDisabledPackages.contains(packageName);

                CharSequence displayName = info.activityInfo.loadLabel(packageManager);
                AppMetaData appMetaData = new AppMetaData(
                        displayName,
                        componentName,
                        info.activityInfo.loadIcon(packageManager),
                        isDistractionOptimized,
                        /* isMirroring = */ false,
                        isDisabledByTos,
                        contextArg -> {
                            packageManager.setApplicationEnabledSetting(packageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                            // Fetch the current enabled setting to make sure the setting is synced
                            // before launching the activity. Otherwise, the activity may not
                            // launch.
                            if (packageManager.getApplicationEnabledSetting(packageName)
                                    != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                                throw new IllegalStateException(
                                        "Failed to enable the disabled package [" + packageName
                                                + "]");
                            }
                            Log.i(TAG, "Successfully enabled package [" + packageName + "]");
                            AppLauncherUtils.launchApp(contextArg,
                                    createAppLaunchIntent(componentName));
                        },
                        buildShortcuts(componentName, displayName, shortcutsListener,
                                carMediaManager, mediaServiceComponents));
                launchablesMap.put(componentName, appMetaData);
            }

            List<ResolveInfo> restrictedActivities = getTosDisabledActivities(
                    context,
                    packageManager,
                    mEnabledPackages
            );
            for (ResolveInfo info: restrictedActivities) {
                String packageName = info.activityInfo.packageName;
                String className = info.activityInfo.name;
                ComponentName componentName = new ComponentName(packageName, className);

                boolean isDistractionOptimized =
                        isActivityDistractionOptimized(carPackageManager, packageName, className);
                boolean isDisabledByTos = tosDisabledPackages.contains(packageName);

                AppMetaData appMetaData = new AppMetaData(
                        info.activityInfo.loadLabel(packageManager),
                        componentName,
                        info.activityInfo.loadIcon(packageManager),
                        isDistractionOptimized,
                        /* isMirroring = */ false,
                        isDisabledByTos,
                        contextArg -> {
                            Intent tosIntent = getIntentForTosAcceptanceFlow(contextArg);
                            launchApp(contextArg, tosIntent);
                        },
                        null
                );
                launchablesMap.put(componentName, appMetaData);
            }
        }

        return new LauncherAppsInfo(launchablesMap, mediaServicesMap);
    }

    /**
     * Gets the intent for launching the TOS acceptance flow
     *
     * @param context The app context
     * @return TOS intent, or null
     */
    @Nullable
    public static Intent getIntentForTosAcceptanceFlow(Context context) {
        String tosIntentName =
                context.getResources().getString(R.string.user_tos_activity_intent);
        try {
            return Intent.parseUri(tosIntentName, Intent.URI_ANDROID_APP_SCHEME);
        } catch (URISyntaxException se) {
            Log.e(TAG, "Invalid intent URI in user_tos_activity_intent", se);
            return null;
        }
    }

    private static Consumer<Pair<Context, View>> buildShortcuts(
            ComponentName componentName, CharSequence displayName,
            ShortcutsListener shortcutsListener, CarMediaManager carMediaManager,
            Set<ComponentName> mediaServiceComponents) {
        return pair -> {
            CarUiShortcutsPopup.Builder carUiShortcutsPopupBuilder =
                    new CarUiShortcutsPopup.Builder()
                            .addShortcut(new ForceStopShortcutItem(
                                    pair.first,
                                    componentName.getPackageName(),
                                    displayName,
                                    carMediaManager,
                                    mediaServiceComponents
                            ))
                            .addShortcut(new AppInfoShortcutItem(pair.first,
                                    componentName.getPackageName(),
                                    UserHandle.getUserHandleForUid(Process.myUid())));
            if (Flags.dockFeature()) {
                carUiShortcutsPopupBuilder
                        .addShortcut(buildPinToDockShortcut(componentName, pair.first));
            }
            CarUiShortcutsPopup carUiShortcutsPopup = carUiShortcutsPopupBuilder
                    .build(pair.first, pair.second);

            carUiShortcutsPopup.show();
            shortcutsListener.onShortcutsShow(carUiShortcutsPopup);
        };
    }

    private static CarUiShortcutsPopup.ShortcutItem buildPinToDockShortcut(
            ComponentName componentName, Context context) {
        DockEventSenderHelper mHelper = new DockEventSenderHelper(context);
        return new PinShortcutItem(context.getResources(), /* isItemPinned= */ false,
                /* pinItemClickDelegate= */ () -> mHelper.sendPinEvent(componentName),
                /* unpinItemClickDelegate= */ () -> mHelper.sendUnpinEvent(componentName)
        );
    }

    private static List<ResolveInfo> getDisabledActivities(Context context,
            PackageManager packageManager, Set<String> enabledPackages) {
        return getActivitiesFromSystemPreferences(
                context,
                packageManager,
                enabledPackages,
                KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
                PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR);
    }

    private static List<ResolveInfo> getTosDisabledActivities(
            Context context,
            PackageManager packageManager,
            Set<String> enabledPackages) {
        return getActivitiesFromSystemPreferences(
                context,
                packageManager,
                enabledPackages,
                KEY_UNACCEPTED_TOS_DISABLED_APPS,
                PackageManager.MATCH_DISABLED_COMPONENTS,
                TOS_DISABLED_APPS_SEPARATOR);
    }

    /**
     * Get a list of activities from packages in system preferences by key
     * @param context the app context
     * @param packageManager The PackageManager
     * @param enabledPackages Set of packages enabled by system
     * @param settingsKey Key to read from system preferences
     * @param sep Separator
     *
     * @return List of activities read from system preferences
     */
    private static List<ResolveInfo> getActivitiesFromSystemPreferences(
            Context context,
            PackageManager packageManager,
            Set<String> enabledPackages,
            String settingsKey,
            int filter,
            String sep) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(contentResolverForUser, settingsKey);
        Set<String> packages = TextUtils.isEmpty(settingsValue) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsValue.split(
                        sep)));

        if (packages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolveInfo> allActivities = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER
                        | filter));

        List<ResolveInfo> activities = new ArrayList<>();
        for (int i = 0; i < allActivities.size(); ++i) {
            ResolveInfo info = allActivities.get(i);
            if (!enabledPackages.contains(info.activityInfo.packageName)
                    && packages.contains(info.activityInfo.packageName)) {
                activities.add(info);
            }
        }
        return activities;
    }

    private static boolean shouldAddToLaunchables(Context context,
            @NonNull ComponentName componentName,
            @NonNull Set<String> appsToHide,
            @NonNull Set<String> customMediaComponents,
            @AppTypes int appTypesToShow,
            @AppTypes int componentAppType) {
        if (appsToHide.contains(componentName.getPackageName())) {
            return false;
        }
        switch (componentAppType) {
            // Process media services
            case APP_TYPE_MEDIA_SERVICES:
                // For a media service in customMediaComponents, if its application's launcher
                // activity will be shown in the Launcher, don't show the service's icon in the
                // Launcher.
                if (customMediaComponents.contains(componentName.flattenToString())) {
                    if ((appTypesToShow & APP_TYPE_LAUNCHABLES) != 0) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "MBS for custom media app " + componentName
                                    + " is skipped in app launcher");
                        }
                        return false;
                    }
                    // Media switcher use case should still show
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "MBS for custom media app " + componentName
                                + " is included in media switcher");
                    }
                    return true;
                }
                // Only Keep MBS that is a media template
                return new MediaSourceUtil(context).isMediaTemplate(componentName);
            // Process activities
            case APP_TYPE_LAUNCHABLES:
                return true;
            default:
                Log.e(TAG, "Invalid componentAppType : " + componentAppType);
                return false;
        }
    }

    private static void selectMediaSourceAndFinish(Context context, ComponentName componentName,
            CarMediaManager carMediaManager) {
        try {
            carMediaManager.setMediaSource(componentName, CarMediaManager.MEDIA_SOURCE_MODE_BROWSE);
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    /**
     * Gets if an activity is distraction optimized.
     *
     * @param carPackageManager The {@link CarPackageManager} system service
     * @param packageName       The package name of the app
     * @param activityName      The requested activity name
     * @return true if the supplied activity is distraction optimized
     */
    static boolean isActivityDistractionOptimized(
            CarPackageManager carPackageManager, String packageName, String activityName) {
        boolean isDistractionOptimized = false;
        // try getting distraction optimization info
        try {
            if (carPackageManager != null) {
                isDistractionOptimized =
                        carPackageManager.isActivityDistractionOptimized(packageName, activityName);
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected when getting DO info", e);
        }
        return isDistractionOptimized;
    }

    /**
     * Callback when a ShortcutsPopup View is shown
     */
    protected interface ShortcutsListener {

        void onShortcutsShow(CarUiShortcutsPopup carUiShortcutsPopup);
    }

    /**
     * Returns a set of packages that are disabled by tos
     *
     * @param context The application context
     * @return Set of packages disabled by tos
     */
    public static Set<String> getTosDisabledPackages(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(contentResolverForUser,
                KEY_UNACCEPTED_TOS_DISABLED_APPS);
        return TextUtils.isEmpty(settingsValue) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsValue.split(
                        TOS_DISABLED_APPS_SEPARATOR)));
    }

    private static Intent createMediaLaunchIntent(ComponentName componentName) {
        return new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE)
                .putExtra(Car.CAR_EXTRA_MEDIA_COMPONENT, componentName.flattenToString());
    }

    private static Intent createAppLaunchIntent(ComponentName componentName) {
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(componentName)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Check if a user has accepted TOS
     *
     * @param context The application context
     * @return true if the user has accepted Tos, false otherwise
     */
    public static boolean tosAccepted(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(
                contentResolverForUser,
                KEY_USER_TOS_ACCEPTED);
        return !Objects.equals(settingsValue, TOS_NOT_ACCEPTED);
    }

    /**
     * Check if TOS status is uninitialized
     *
     * @param context The application context
     *
     * @return true if tos is uninitialized, false otherwise
     */
    static boolean tosStatusUninitialized(Context context) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                        UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(
                contentResolverForUser,
                KEY_USER_TOS_ACCEPTED);
        return Objects.equals(settingsValue, TOS_UNINITIALIZED);
    }
}
