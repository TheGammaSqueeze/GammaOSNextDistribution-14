/*
 * Copyright 2023 The Android Open Source Project
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

package android.virtualdevice.cts.applaunch;

import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;
import static android.virtualdevice.cts.common.StreamedAppConstants.CUSTOM_HOME_ACTIVITY;
import static android.virtualdevice.cts.common.StreamedAppConstants.DEFAULT_HOME_ACTIVITY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.WallpaperManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.WindowManagerState;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for home support on displays created by virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_HOME)
public class VirtualDeviceHomeTest {

    private static final VirtualDisplayConfig.Builder HOME_DISPLAY_CONFIG =
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    private static final VirtualDisplayConfig.Builder UNTRUSTED_HOME_DISPLAY_CONFIG =
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    private static final VirtualDisplayConfig.Builder AUTO_MIRROR_HOME_DISPLAY_CONFIG =
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
    private static final VirtualDisplayConfig.Builder PUBLIC_HOME_DISPLAY_CONFIG =
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.withAdditionalPermissions(
            CHANGE_COMPONENT_ENABLED_STATE);

    private final Context mContext = getInstrumentation().getContext();

    private VirtualDisplay mVirtualDisplay;

    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        assumeTrue(isHomeSupportedOnVirtualDisplay());
    }

    /**
     * Home activities and wallpaper are not shown on untrusted displays.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_untrustedVirtualDisplay() {
        createVirtualDeviceAndHomeDisplay(UNTRUSTED_HOME_DISPLAY_CONFIG, /* homeComponent= */ null);

        verify(mActivityListener, never()).onTopActivityChanged(anyInt(), any(), anyInt());
        assertThat(isWallpaperOnVirtualDisplay(mRule.getWmState())).isFalse();
    }

    /**
     * Home activities and wallpaper are not shown on auto-mirror displays.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_autoMirrorVirtualDisplay() {
        createVirtualDeviceAndHomeDisplay(AUTO_MIRROR_HOME_DISPLAY_CONFIG,
                /* homeComponent= */ null);

        verify(mActivityListener, never()).onTopActivityChanged(anyInt(), any(), anyInt());
        assertThat(isWallpaperOnVirtualDisplay(mRule.getWmState())).isFalse();
    }

    /**
     * Home activities and wallpaper are not shown on public displays without the flag
     * VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_publicVirtualDisplay() {
        createVirtualDeviceAndHomeDisplay(PUBLIC_HOME_DISPLAY_CONFIG, /* homeComponent= */ null);

        verify(mActivityListener, never()).onTopActivityChanged(anyInt(), any(), anyInt());
        assertThat(isWallpaperOnVirtualDisplay(mRule.getWmState())).isFalse();
    }

    /**
     * Wallpaper is shown on virtual displays that support home without a custom home component.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_noHomeComponent_showsWallpaper() {
        assumeTrue(WallpaperManager.getInstance(mContext).isWallpaperSupported());
        try (HomeActivitySession ignored = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            createVirtualDeviceAndHomeDisplay(/* homeComponent= */ null);
            assertThat(mRule.getWmState().waitForWithAmState(
                    this::isWallpaperOnVirtualDisplay, "Wallpaper is on virtual display"))
                    .isTrue();
        }
    }

    /**
     * Wallpaper is shown on virtual displays that support home with a custom home component.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_withHomeComponent_showsWallpaper() {
        assumeTrue(WallpaperManager.getInstance(mContext).isWallpaperSupported());
        createVirtualDeviceAndHomeDisplay(CUSTOM_HOME_ACTIVITY);
        assertThat(mRule.getWmState().waitForWithAmState(
                this::isWallpaperOnVirtualDisplay, "Wallpaper is on virtual display"))
                .isTrue();
    }

    /**
     * The device-default secondary home activity is started on virtual displays that support home
     * if the didn't specify a custom home component. That activity is at the hierarchy root.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_noCustomHomeComponent() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay(/* homeComponent= */ null);
            assertActivityOnVirtualDisplay(homeComponent);

            EmptyActivity activity =
                    mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
            assertActivityOnVirtualDisplay(activity.getComponentName());

            activity.finish();
            mRule.getWmState().waitAndAssertActivityRemoved(activity.getComponentName());

            assertActivityOnVirtualDisplay(homeComponent);
        }
    }

    /**
     * The device-default secondary home activity is started on virtual displays that support home
     * if the didn't specify a custom home component. That activity is resolved when a home intent
     * is sent to the relevant display.
     */
    @ApiTest(apis = {"android.hardware.display.VirtualDisplayConfig.Builder#setHomeSupported"})
    @Test
    public void virtualDeviceHome_noCustomHomeComponent_sendHomeIntent() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay(/* homeComponent= */ null);
            assertActivityOnVirtualDisplay(homeComponent);

            EmptyActivity activity =
                    mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
            assertActivityOnVirtualDisplay(activity.getComponentName());

            sendHomeIntentOnVirtualDisplay();
            assertActivityOnVirtualDisplay(homeComponent);
        }
    }

    /**
     * The device-default secondary home activity is started on virtual displays that support home
     * if they specified an invalid custom home component.
     */
    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceParams.Builder#setHomeComponent"})
    @Test
    public void virtualDeviceHome_invalidCustomHomeComponent_fallbackToDefaultSecondaryHome() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay(new ComponentName("foo.bar", "foo.bar.Baz"));
            assertActivityOnVirtualDisplay(homeComponent);
        }
    }

    /**
     * The explicitly specified custom home activity is started on virtual displays that support
     * home. That activity is at the hierarchy root.
     */
    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceParams.Builder#setHomeComponent"})
    @Test
    public void virtualDeviceHome_withCustomHomeComponent() {
        createVirtualDeviceAndHomeDisplay(CUSTOM_HOME_ACTIVITY);
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);

        EmptyActivity activity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        assertActivityOnVirtualDisplay(activity.getComponentName());

        activity.finish();
        mRule.getWmState().waitAndAssertActivityRemoved(activity.getComponentName());

        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);
    }

    /**
     * The explicitly specified custom home activity is started on virtual displays that support
     * home. That activity is resolved when a home intent is sent to the relevant display.
     */
    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceParams.Builder#setHomeComponent"})
    @Test
    public void virtualDeviceHome_withCustomHomeComponent_sendHomeIntent() {
        createVirtualDeviceAndHomeDisplay(CUSTOM_HOME_ACTIVITY);
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);

        EmptyActivity activity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        assertActivityOnVirtualDisplay(activity.getComponentName());

        sendHomeIntentOnVirtualDisplay();
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);
    }

    private void createVirtualDeviceAndHomeDisplay(ComponentName homeComponent) {
        createVirtualDeviceAndHomeDisplay(HOME_DISPLAY_CONFIG, homeComponent);
    }

    private void createVirtualDeviceAndHomeDisplay(
            VirtualDisplayConfig.Builder virtualDisplayConfigBuilder, ComponentName homeComponent) {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder().setHomeComponent(homeComponent).build());
        virtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        mVirtualDisplay = mRule.createManagedVirtualDisplay(
                virtualDevice, virtualDisplayConfigBuilder.setHomeSupported(true).build());
    }

    private void sendHomeIntentOnVirtualDisplay() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mRule.sendIntentToDisplay(intent, mVirtualDisplay);
    }

    private void assertActivityOnVirtualDisplay(ComponentName componentName) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()), eq(componentName),
                eq(mContext.getUserId()));
        reset(mActivityListener);
    }

    private boolean isWallpaperOnVirtualDisplay(WindowManagerState state) {
        return state.getMatchingWindowType(TYPE_WALLPAPER).stream().anyMatch(
                w -> w.getDisplayId() == mVirtualDisplay.getDisplay().getDisplayId());
    }

    private boolean isHomeSupportedOnVirtualDisplay() {
        try {
            return mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsSystemDecorsOnSecondaryDisplays", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume this device support system decorations.
            return true;
        }
    }

    /**
     * HomeActivitySession is used to replace the default home component, so that you can use
     * your preferred home for testing within the session. The original default home will be
     * restored automatically afterward.
     */
    private class HomeActivitySession implements AutoCloseable {
        private final PackageManager mPackageManager;
        private final ComponentName mOrigHome;
        private final ComponentName mSessionHome;

        HomeActivitySession(ComponentName sessionHome) {
            mSessionHome = sessionHome;
            mPackageManager = mContext.getPackageManager();
            mOrigHome = getDefaultHomeComponent();

            mPackageManager.setComponentEnabledSetting(mSessionHome,
                    COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
            setDefaultHome(mSessionHome);
        }

        @Override
        public void close() {
            mPackageManager.setComponentEnabledSetting(mSessionHome,
                    COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
            if (mOrigHome != null) {
                setDefaultHome(mOrigHome);
            }
        }

        private void setDefaultHome(ComponentName componentName) {
            SystemUtil.runShellCommand("cmd package set-home-activity --user "
                    + android.os.Process.myUserHandle().getIdentifier() + " "
                    + componentName.flattenToString());
        }

        ComponentName getCurrentSecondaryHomeComponent() {
            boolean useSystemProvidedLauncher = mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_useSystemProvidedLauncherForSecondary",
                            "bool", "android"));
            return useSystemProvidedLauncher ? getDefaultSecondaryHomeComponent() : mSessionHome;
        }

        private ComponentName getDefaultHomeComponent() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return resolveHomeIntent(intent);
        }

        private ComponentName getDefaultSecondaryHomeComponent() {
            int resId = Resources.getSystem().getIdentifier(
                    "config_secondaryHomePackage", "string", "android");
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_SECONDARY_HOME);
            intent.setPackage(mContext.getResources().getString(resId));
            return resolveHomeIntent(intent);
        }

        private ComponentName resolveHomeIntent(Intent intent) {
            final ResolveInfo resolveInfo =
                    mContext.getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY);
            assumeNotNull(resolveInfo);
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            return new ComponentName(activityInfo.packageName, activityInfo.name);
        }
    }
}
