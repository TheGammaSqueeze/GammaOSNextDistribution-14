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
import static android.content.pm.ApplicationInfo.CATEGORY_AUDIO;
import static android.content.pm.ApplicationInfo.CATEGORY_VIDEO;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;

import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_LAUNCHABLES;
import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_MEDIA_SERVICES;
import static com.android.car.carlauncher.AppLauncherUtils.PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR;
import static com.android.car.carlauncher.AppLauncherUtils.TOS_DISABLED_APPS_SEPARATOR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.media.CarMediaManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.media.MediaBrowserService;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class AppLauncherUtilsTest extends AbstractExtendedMockitoTestCase {
    private static final String TEST_DISABLED_APP_1 = "com.android.car.test.disabled1";
    private static final String TEST_DISABLED_APP_2 = "com.android.car.test.disabled2";
    private static final String TEST_ENABLED_APP = "com.android.car.test.enabled";
    private static final String TEST_TOS_DISABLED_APP_1 = "com.android.car.test.tosdisabled1";
    private static final String TEST_TOS_DISABLED_APP_2 = "com.android.car.test.tosdisabled2";
    private static final String TEST_VIDEO_APP = "com.android.car.test.video";
    // Default media app
    private static final String TEST_MEDIA_TEMPLATE_MBS = "com.android.car.test.mbs";
    // Video app that has a MBS defined but has its own launch activity
    private static final String TEST_VIDEO_MBS = "com.android.car.test.video.mbs";
    // NDO App that has opted in its MBS to launch in car
    private static final String TEST_NDO_MBS_LAUNCHABLE = "com.android.car.test.mbs.launchable";
    // NDO App that has opted out its MBS to launch in car
    private static final String TEST_NDO_MBS_NOT_LAUNCHABLE =
            "com.android.car.test.mbs.notlaunchable";

    private static final String CUSTOM_MEDIA_PACKAGE = "com.android.car.radio";
    private static final String CUSTOM_MEDIA_CLASS = "com.android.car.radio.service";
    private static final String CUSTOM_MEDIA_COMPONENT = CUSTOM_MEDIA_PACKAGE
            + "/" + CUSTOM_MEDIA_CLASS;
    private static final String TEST_MIRROR_APP_PKG = "com.android.car.test.mirroring";
    private static final String TOS_INTENT_NAME = "intent:#Intent;action="
            + "com.android.car.SHOW_USER_TOS_ACTIVITY;B.show_value_prop=true;"
            + "S.mini_flow_extra=GTOS_GATED_FLOW;end";
    private static final String TOS_INTENT_VERIFY = "#Intent;action="
            + "com.android.car.SHOW_USER_TOS_ACTIVITY;B.show_value_prop=true;"
            + "S.mini_flow_extra=GTOS_GATED_FLOW;end";


    @Mock private Context mMockContext;
    @Mock private LauncherApps mMockLauncherApps;
    @Mock private PackageManager mMockPackageManager;
    @Mock private AppLauncherUtils.ShortcutsListener mMockShortcutsListener;

    @Mock private Resources mResources;

    @Mock private LauncherActivityInfo mRadioLauncherActivityInfo;

    private CarMediaManager mCarMediaManager;
    private CarPackageManager mCarPackageManager;
    private Car mCar;

    @Before
    public void setUp() throws Exception {
        // Need for CarMediaManager to get the user from the context.
        when(mMockContext.getUser()).thenReturn(UserHandle.of(ActivityManager.getCurrentUser()));

        mCar = Car.createCar(mMockContext, /* handler = */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        mCarPackageManager = null;
                        mCarMediaManager = null;
                        return;
                    }
                    mCarPackageManager = (CarPackageManager) car.getCarManager(Car.PACKAGE_SERVICE);
                    mCarPackageManager = Mockito.spy(mCarPackageManager);
                    mCarMediaManager = (CarMediaManager) car.getCarManager(Car.CAR_MEDIA_SERVICE);
                    when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
                });
    }

    @After
    public void tearDown() throws Exception {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(Settings.Secure.class);
    }

    @Test
    public void testGetLauncherApps_MediaCenterAppSwitcher() {
        mockSettingsStringCalls();
        mockPackageManagerQueries();

        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(eq(
                com.android.car.media.common.R.array.custom_media_packages)))
                .thenReturn(new String[]{CUSTOM_MEDIA_COMPONENT});

        // Setup custom media component
        when(mMockLauncherApps.getActivityList(any(), any()))
                .thenReturn(List.of(mRadioLauncherActivityInfo));
        when(mRadioLauncherActivityInfo.getComponentName())
                .thenReturn(new ComponentName(CUSTOM_MEDIA_PACKAGE, CUSTOM_MEDIA_CLASS));
        when(mRadioLauncherActivityInfo.getName())
                .thenReturn(CUSTOM_MEDIA_CLASS);

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, mCarMediaManager, mMockShortcutsListener,
                TEST_MIRROR_APP_PKG,  /* mirroringAppRedirect= */ null);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // Only media apps should be present
        assertEquals(Set.of(
                        TEST_MEDIA_TEMPLATE_MBS,
                        TEST_NDO_MBS_LAUNCHABLE,
                        CUSTOM_MEDIA_PACKAGE),
                appMetaData.stream()
                        .map(am -> am.getComponentName().getPackageName())
                        .collect(Collectors.toSet()));

        // This should include all MBS discovered
        assertEquals(5, launcherAppsInfo.getMediaServices().size());

        mockPmGetApplicationEnabledSetting(COMPONENT_ENABLED_STATE_ENABLED, TEST_DISABLED_APP_1,
                TEST_DISABLED_APP_2);

        launchAllApps(appMetaData);

        // Media apps should do only switching and not launch activity
        verify(mMockContext, never()).startActivity(any(), any());
    }

    @Test
    public void testGetLauncherApps_Launcher() {
        mockSettingsStringCalls();
        mockPackageManagerQueries();

        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(eq(
                com.android.car.media.common.R.array.custom_media_packages)))
                .thenReturn(new String[]{CUSTOM_MEDIA_COMPONENT});

        // Setup custom media component
        when(mMockLauncherApps.getActivityList(any(), any()))
                .thenReturn(List.of(mRadioLauncherActivityInfo));
        when(mRadioLauncherActivityInfo.getComponentName())
                .thenReturn(new ComponentName(CUSTOM_MEDIA_PACKAGE, CUSTOM_MEDIA_CLASS));
        when(mRadioLauncherActivityInfo.getName())
                .thenReturn(CUSTOM_MEDIA_CLASS);

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ true, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, mCarMediaManager, mMockShortcutsListener,
                TEST_MIRROR_APP_PKG,  /* mirroringAppRedirect= */ null);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();
        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.

        assertEquals(Set.of(
                        TEST_MEDIA_TEMPLATE_MBS,
                        TEST_NDO_MBS_LAUNCHABLE,
                        CUSTOM_MEDIA_PACKAGE,
                        TEST_DISABLED_APP_1,
                        TEST_DISABLED_APP_2),
                appMetaData.stream()
                        .map(am -> am.getComponentName().getPackageName())
                        .collect(Collectors.toSet()));


        // This should include all MBS discovered
        assertEquals(5, launcherAppsInfo.getMediaServices().size());

        mockPmGetApplicationEnabledSetting(COMPONENT_ENABLED_STATE_ENABLED, TEST_DISABLED_APP_1,
                TEST_DISABLED_APP_2);

        launchAllApps(appMetaData);

        verify(mMockPackageManager).setApplicationEnabledSetting(
                eq(TEST_DISABLED_APP_1), eq(COMPONENT_ENABLED_STATE_ENABLED), eq(0));

        verify(mMockPackageManager).setApplicationEnabledSetting(
                eq(TEST_DISABLED_APP_2), eq(COMPONENT_ENABLED_STATE_ENABLED), eq(0));

        verify(mMockContext, times(5)).startActivity(any(), any());

        verify(mMockPackageManager, never()).setApplicationEnabledSetting(
                eq(TEST_ENABLED_APP), anyInt(), eq(0));
    }


    @Test
    public void testGetLauncherAppsWithEnableAndTosDisabledApps() {
        mockSettingsStringCalls();
        mockTosPackageManagerQueries();

        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(eq(
                com.android.car.media.common.R.array.custom_media_packages)))
                .thenReturn(new String[]{CUSTOM_MEDIA_COMPONENT});

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, mCarMediaManager, mMockShortcutsListener,
                TEST_MIRROR_APP_PKG,  /* mirroringAppRedirect= */ null);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.
        assertEquals(3, appMetaData.size());

        Resources resources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(resources);
        when(resources.getString(anyInt())).thenReturn(TOS_INTENT_NAME);

        launchAllApps(appMetaData);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).startActivity(intentCaptor.capture(), any());

        String intentUri = intentCaptor.getAllValues().get(0).toUri(0);
        assertEquals(TOS_INTENT_VERIFY, intentUri);
    }

    @Test
    public void testGetLauncherAppsWithEnableAndTosDisabledDistractionOptimizedApps() {
        mockSettingsStringCalls();
        mockTosPackageManagerQueries();

        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(eq(
                com.android.car.media.common.R.array.custom_media_packages)))
                .thenReturn(new String[]{CUSTOM_MEDIA_COMPONENT});

        doReturn(true)
                .when(mCarPackageManager)
                .isActivityDistractionOptimized(eq(TEST_TOS_DISABLED_APP_1), any());
        doReturn(true)
                .when(mCarPackageManager)
                .isActivityDistractionOptimized(eq(TEST_TOS_DISABLED_APP_2), any());

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, mCarMediaManager, mMockShortcutsListener,
                TEST_MIRROR_APP_PKG,  /* mirroringAppRedirect= */ null);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.
        assertEquals(3, appMetaData.size());

        Resources resources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(resources);
        when(resources.getString(anyInt())).thenReturn(TOS_INTENT_NAME);

        launchAllApps(appMetaData);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext, times(2)).startActivity(intentCaptor.capture(), any());

        String intentUri = intentCaptor.getAllValues().get(0).toUri(0);
        assertEquals(TOS_INTENT_VERIFY, intentUri);
    }

    private void mockPackageManagerQueries() {
        // setup a media template app that uses media service
        ApplicationInfo mbsAppInfo = new ApplicationInfo();
        mbsAppInfo.category = CATEGORY_AUDIO;
        ResolveInfo mbs = constructServiceResolveInfo(TEST_MEDIA_TEMPLATE_MBS);

        try {
            Intent mbsIntent = new Intent();
            mbsIntent.setComponent(mbs.getComponentInfo().getComponentName());
            mbsIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

            when(mMockPackageManager.getApplicationInfo(mbs.getComponentInfo().packageName, 0))
                    .thenReturn(mbsAppInfo);

            doReturn(Arrays.asList(mbs)).when(mMockPackageManager).queryIntentServices(
                    argThat((Intent i) -> i != null
                            && mbs.getComponentInfo().getComponentName().equals(i.getComponent())),
                    eq(PackageManager.GET_META_DATA));

            when(mMockPackageManager.getLaunchIntentForPackage(mbs.getComponentInfo().packageName))
                    .thenReturn(null);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        // setup a NDO Video app that has MBS but also its own activity, MBS won't be surfaced
        ApplicationInfo videoAppInfo = new ApplicationInfo();
        videoAppInfo.category = CATEGORY_VIDEO;
        ResolveInfo videoApp = constructServiceResolveInfo(TEST_VIDEO_MBS);
        try {
            Intent videoMbsIntent = new Intent();
            videoMbsIntent.setComponent(videoApp.getComponentInfo().getComponentName());
            videoMbsIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

            when(mMockPackageManager.getApplicationInfo(videoApp.getComponentInfo().packageName,
                    0))
                    .thenReturn(videoAppInfo);

            doReturn(Arrays.asList(videoApp)).when(mMockPackageManager).queryIntentServices(
                    argThat((Intent i) -> i != null
                            && videoApp.getComponentInfo().getComponentName()
                                    .equals(i.getComponent())),
                    eq(PackageManager.GET_META_DATA));

            when(mMockPackageManager.getLaunchIntentForPackage(
                    videoApp.getComponentInfo().packageName))
                    .thenReturn(new Intent());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        // setup a NDO app that has MBS opted out of launch in car
        ApplicationInfo notlaunchableMBSInfo = new ApplicationInfo();
        notlaunchableMBSInfo.category = CATEGORY_VIDEO;
        ResolveInfo notlaunchableMBSApp = constructServiceResolveInfo(TEST_NDO_MBS_NOT_LAUNCHABLE);

        try {
            Intent notlaunachableMbsIntent = new Intent();
            notlaunachableMbsIntent.setComponent(
                    notlaunchableMBSApp.getComponentInfo().getComponentName());
            notlaunachableMbsIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

            when(mMockPackageManager.getApplicationInfo(
                    notlaunchableMBSApp.getComponentInfo().packageName, 0))
                    .thenReturn(notlaunchableMBSInfo);


            notlaunchableMBSApp.serviceInfo.metaData = new Bundle();
            notlaunchableMBSApp.serviceInfo.metaData
                    .putBoolean("androidx.car.app.launchable", false);

            doReturn(Arrays.asList(notlaunchableMBSApp))
                    .when(mMockPackageManager).queryIntentServices(
                    argThat((Intent i) -> i != null
                            && notlaunchableMBSApp.getComponentInfo().getComponentName()
                                    .equals(i.getComponent())),
                    eq(PackageManager.GET_META_DATA));

            when(mMockPackageManager.getLaunchIntentForPackage(
                    notlaunchableMBSApp.getComponentInfo().packageName))
                    .thenReturn(new Intent());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }


        // setup a NDO app that has MBS opted in to launch in car
        ApplicationInfo launchableMBSInfo = new ApplicationInfo();
        launchableMBSInfo.category = CATEGORY_VIDEO;
        ResolveInfo launchableMBSApp = constructServiceResolveInfo(TEST_NDO_MBS_LAUNCHABLE);
        try {
            Intent mbsIntent = new Intent();
            mbsIntent.setComponent(launchableMBSApp.getComponentInfo().getComponentName());
            mbsIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

            when(mMockPackageManager.getApplicationInfo(
                    launchableMBSApp.getComponentInfo().packageName,
                    0))
                    .thenReturn(launchableMBSInfo);


            launchableMBSApp.serviceInfo.metaData = new Bundle();
            launchableMBSApp.serviceInfo.metaData.putBoolean("androidx.car.app.launchable", true);

            doReturn(Arrays.asList(launchableMBSApp)).when(mMockPackageManager).queryIntentServices(
                    argThat((Intent i) -> i != null
                            && launchableMBSApp.getComponentInfo().getComponentName()
                            .equals(i.getComponent())),
                    eq(PackageManager.GET_META_DATA));

            when(mMockPackageManager.getLaunchIntentForPackage(
                    launchableMBSApp.getComponentInfo().packageName))
                    .thenReturn(new Intent());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        when(mMockPackageManager.queryIntentServices(any(), eq(PackageManager.GET_RESOLVED_FILTER)))
                .thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            if (intent.getAction().equals(MediaBrowserService.SERVICE_INTERFACE)) {
                return Arrays.asList(mbs, videoApp, notlaunchableMBSApp, launchableMBSApp,
                        constructServiceResolveInfo(CUSTOM_MEDIA_PACKAGE));
            }
            return new ArrayList<>();
        });

        // setup activities
        when(mMockPackageManager.queryIntentActivities(any(), any())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            PackageManager.ResolveInfoFlags flags = args.getArgument(1);
            List<ResolveInfo> resolveInfoList = new ArrayList<>();
            if (intent.getAction().equals(Intent.ACTION_MAIN)) {
                if ((flags.getValue() & MATCH_DISABLED_UNTIL_USED_COMPONENTS) != 0) {
                    resolveInfoList.add(constructActivityResolveInfo(TEST_DISABLED_APP_1));
                    resolveInfoList.add(constructActivityResolveInfo(TEST_DISABLED_APP_2));
                }
                // Keep custom media component in both MBS and Activity with Launch Intent
                resolveInfoList.add(constructActivityResolveInfo(CUSTOM_MEDIA_PACKAGE));
                // Add apps which will have their own Launcher Activity
                resolveInfoList.add(constructActivityResolveInfo(TEST_VIDEO_MBS));
                resolveInfoList.add(constructActivityResolveInfo(TEST_NDO_MBS_LAUNCHABLE));
                resolveInfoList.add(constructActivityResolveInfo(TEST_NDO_MBS_NOT_LAUNCHABLE));
            }

            return resolveInfoList;
        });
    }

    private void mockTosPackageManagerQueries() {
        ResolveInfo resolveInfo = constructServiceResolveInfo(TEST_ENABLED_APP);
        try {
            when(mMockPackageManager.getServiceInfo(
                    resolveInfo
                            .getComponentInfo().getComponentName(),
                    PackageManager.GET_META_DATA))
                    .thenReturn(new ServiceInfo());
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        when(mMockPackageManager.queryIntentServices(any(), anyInt())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            if (intent.getAction().equals(MediaBrowserService.SERVICE_INTERFACE)) {
                return Collections.singletonList(resolveInfo);
            }
            return new ArrayList<>();
        });
        when(mMockPackageManager.queryIntentActivities(any(), any())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            PackageManager.ResolveInfoFlags flags = args.getArgument(1);
            List<ResolveInfo> resolveInfoList = new ArrayList<>();
            if (intent.getAction().equals(Intent.ACTION_MAIN)) {
                if ((flags.getValue() & MATCH_DISABLED_COMPONENTS) != 0) {
                    resolveInfoList.add(constructActivityResolveInfo(TEST_TOS_DISABLED_APP_1));
                    resolveInfoList.add(constructActivityResolveInfo(TEST_TOS_DISABLED_APP_2));
                }
                resolveInfoList.add(constructActivityResolveInfo(TEST_ENABLED_APP));
            }
            return resolveInfoList;
        });
    }

    private void mockPmGetApplicationEnabledSetting(int enabledState, String... packages) {
        for (String pkg : packages) {
            when(mMockPackageManager.getApplicationEnabledSetting(pkg)).thenReturn(enabledState);
        }
    }

    private void mockSettingsStringCalls() {
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenAnswer(args -> {
                    Context context = mock(Context.class);
                    ContentResolver contentResolver = mock(ContentResolver.class);
                    when(context.getContentResolver()).thenReturn(contentResolver);
                    return context;
                });

        doReturn(TEST_DISABLED_APP_1 + PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR
                + TEST_DISABLED_APP_2)
                .when(() -> Settings.Secure.getString(any(ContentResolver.class),
                        eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)));

        doReturn(TEST_TOS_DISABLED_APP_1 + TOS_DISABLED_APPS_SEPARATOR
                + TEST_TOS_DISABLED_APP_2)
                .when(() -> Settings.Secure.getString(any(ContentResolver.class),
                        eq(KEY_UNACCEPTED_TOS_DISABLED_APPS)));
    }

    private void launchAllApps(List<AppMetaData> appMetaData) {
        for (AppMetaData meta : appMetaData) {
            Consumer<Context> launchCallback = meta.getLaunchCallback();
            launchCallback.accept(mMockContext);
        }
    }

    private static ResolveInfo constructActivityResolveInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = packageName + ".activity";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        return info;
    }

    private static ResolveInfo constructServiceResolveInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = packageName;
        info.serviceInfo.name = packageName + ".service";
        info.serviceInfo.applicationInfo = new ApplicationInfo();
        return info;
    }
}
