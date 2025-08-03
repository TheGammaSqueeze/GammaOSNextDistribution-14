/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
import static android.view.WindowInsetsController.APPEARANCE_OPAQUE_STATUS_BARS;
import static android.view.WindowInsetsController.BEHAVIOR_DEFAULT;

import static com.android.systemui.car.systembar.SystemBarConfigs.BOTTOM;
import static com.android.systemui.car.systembar.SystemBarConfigs.LEFT;
import static com.android.systemui.car.systembar.SystemBarConfigs.RIGHT;
import static com.android.systemui.car.systembar.SystemBarConfigs.TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.RemoteException;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.ArrayMap;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.InOrderImpl;

import java.util.Arrays;
import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarTest extends SysuiTestCase {

    private CarSystemBar mCarSystemBar;
    private TestableResources mTestableResources;
    private FakeExecutor mExecutor;

    @Mock
    private CarSystemBarController mCarSystemBarController;
    @Mock
    private LightBarController mLightBarController;
    @Mock
    private SysuiDarkIconDispatcher mStatusBarIconController;
    @Mock
    private LightBarTransitionsController mLightBarTransitionsController;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private CarDeviceProvisionedController mDeviceProvisionedController;
    @Mock
    private AutoHideController mAutoHideController;
    @Mock
    private ButtonSelectionStateListener mButtonSelectionStateListener;
    @Mock
    private ButtonRoleHolderController mButtonRoleHolderController;
    @Mock
    private IStatusBarService mBarService;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private ButtonSelectionStateController mButtonSelectionStateController;
    @Mock
    private PhoneStatusBarPolicy mIconPolicy;
    @Mock
    private StatusBarIconController mIconController;
    @Mock
    private StatusBarSignalPolicy mSignalPolicy;
    @Mock
    private HvacController mHvacController;

    private RegisterStatusBarResult mBarResult;
    private AppearanceRegion[] mAppearanceRegions;
    private FakeExecutor mUiBgExecutor;
    private SystemBarConfigs mSystemBarConfigs;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mExecutor = new FakeExecutor(new FakeSystemClock());
        mUiBgExecutor = new FakeExecutor(new FakeSystemClock());
        when(mStatusBarIconController.getTransitionsController()).thenReturn(
                mLightBarTransitionsController);
        mAppearanceRegions = new AppearanceRegion[]{
                new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect())
        };
        mBarResult = new RegisterStatusBarResult(
                /* icons= */ new ArrayMap<>(),
                /* disabledFlags1= */ 0,
                /* appearance= */ 0,
                mAppearanceRegions,
                /* imeWindowVis= */ 0,
                /* imeBackDisposition= */ 0,
                /* showImeSwitcher= */ false,
                /* disabledFlags2= */ 0,
                /* imeToken= */ null,
                /* navbarColorMangedByIme= */ false,
                BEHAVIOR_DEFAULT,
                WindowInsets.Type.defaultVisible(),
                /* packageName= */ null,
                /* transientBarTypes= */ 0,
                /* letterboxDetails= */ null);
        try {
            when(mBarService.registerStatusBar(any())).thenReturn(mBarResult);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        when(mCarSystemBarController.getTopWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBottomWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getLeftWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getRightWindow()).thenReturn(mock(ViewGroup.class));
        initCarSystemBar();
    }

    private void initCarSystemBar() {
        FakeDisplayTracker displayTracker = new FakeDisplayTracker(mContext);
        mSystemBarConfigs = new SystemBarConfigs(mTestableResources.getResources());
        mCarSystemBar = new CarSystemBar(mContext, mCarSystemBarController, mLightBarController,
                mStatusBarIconController, mWindowManager, mDeviceProvisionedController,
                new CommandQueue(mContext, displayTracker), mAutoHideController,
                mButtonSelectionStateListener, mExecutor, mUiBgExecutor, mBarService,
                () -> mKeyguardStateController, () -> mIconPolicy, mHvacController, mSignalPolicy,
                mSystemBarConfigs,
                mock(ConfigurationController.class), mock(CarSystemBarRestartTracker.class),
                displayTracker, Optional.empty(), null);
        mCarSystemBar.setSignalPolicy(mSignalPolicy);
    }

    @Test
    public void restartNavbars_refreshesTaskChanged() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mButtonSelectionStateListener).onTaskStackChanged();
    }

    @Test
    public void restartNavBars_newUserNotSetupWithKeyguardShowing_showsKeyguardButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllKeyguardButtons(false);
    }

    @Test
    public void restartNavbars_newUserIsSetupWithKeyguardHidden_showsNavigationButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());
        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        when(mKeyguardStateController.isShowing()).thenReturn(false);

        deviceProvisionedCallbackCaptor.getValue().onUserSetupChanged();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllNavigationButtons(true);
    }

    @Test
    public void restartNavBars_lightAppearance_darkensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect());

        mCarSystemBar.start();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ true, /* animate= */ false);
    }

    @Test
    public void restartNavBars_opaqueAppearance_lightensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_OPAQUE_STATUS_BARS, new Rect());

        mCarSystemBar.start();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ false, /* animate= */ false);
    }

    @Test
    public void showTransient_wrongDisplayId_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();

        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        int insetTypes = 0;
        mCarSystemBar.showTransient(randomDisplay, insetTypes, false);

        assertThat(mCarSystemBar.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_noStatusBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();

        int insetTypes = 0;
        mCarSystemBar.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBar.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_statusBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBar.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
    }

    @Test
    public void showTransient_correctDisplayId_noNavBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();

        int insetTypes = 0;
        mCarSystemBar.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBar.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_navBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBar.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_wrongDisplayId_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        mCarSystemBar.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        mCarSystemBar.abortTransient(randomDisplay, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_noInsets_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        mCarSystemBar.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        mCarSystemBar.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_statusBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        mCarSystemBar.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBar.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void abortTransient_correctDisplayId_navBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBar.start();
        mCarSystemBar.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBar.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBar.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBar.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void disable_wrongDisplayId_notSetStatusBarState() {
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;

        mCarSystemBar.disable(randomDisplay, 0, 0, false);

        verify(mCarSystemBarController, never()).setSystemBarStates(anyInt(), anyInt());
    }

    @Test
    public void disable_correctDisplayId_setSystemBarStates() {
        mCarSystemBar.disable(Display.DEFAULT_DISPLAY, 0, 0, false);

        verify(mCarSystemBarController).setSystemBarStates(0, 0);
    }

    @Test
    public void onConfigChanged_toggleNightMode() {
        // get the current mode and then change to the opposite
        boolean isNightMode = mContext.getResources().getConfiguration().isNightModeActive();
        Configuration config = new Configuration();
        config.uiMode =
                isNightMode ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        UiModeManager mockUiModeManager = mock(UiModeManager.class);
        mCarSystemBar.setUiModeManager(mockUiModeManager);

        mCarSystemBar.onConfigChanged(config);

        verify(mockUiModeManager).setNightModeActivated(!isNightMode);
    }

    @Test
    public void onConfigChanged_callOnClick_profilePickerViewIsSelected() {
        // alternative profile picker used on multi-display systems
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());
        Configuration config = new Configuration();
        config.uiMode = mContext.getResources().getConfiguration().isNightModeActive()
                ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        View mockProfilePickerView = mock(View.class);
        when(mockProfilePickerView.isSelected()).thenReturn(true);
        CarSystemBarView mockTopBarView = mock(CarSystemBarView.class);
        when(mockTopBarView.findViewById(R.id.user_name)).thenReturn(mockProfilePickerView);
        when(mCarSystemBarController.getTopBar(anyBoolean())).thenReturn(mockTopBarView);
        initCarSystemBar();

        mCarSystemBar.start();
        mCarSystemBar.onConfigChanged(config);

        InOrder inOrder = new InOrderImpl(Arrays.asList(mockProfilePickerView, mockTopBarView));
        inOrder.verify(mockTopBarView).findViewById(R.id.user_name);
        inOrder.verify(mockProfilePickerView).callOnClick();
        inOrder.verify(mockTopBarView).findViewById(R.id.user_name);
        inOrder.verify(mockProfilePickerView).callOnClick();
    }

    @Test
    public void onConfigChanged_callQuickControlsOnClickFromClassName_forSelectedQuickControl() {
        String clsName = "testClsName";
        Configuration config = new Configuration();
        config.uiMode = mContext.getResources().getConfiguration().isNightModeActive()
                ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        when(mCarSystemBarController.getSelectedQuickControlsClassName()).thenReturn(clsName);
        initCarSystemBar();

        mCarSystemBar.start();
        mCarSystemBar.onConfigChanged(config);

        verify(mCarSystemBarController, times(2))
                .callQuickControlsOnClickFromClassName(clsName);
    }

    @Test
    public void restartSystemBars_newSystemBarConfig_recreatesSystemBars() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        when(mCarSystemBarController.getTopWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBottomWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getLeftWindow()).thenReturn(null);
        when(mCarSystemBarController.getRightWindow()).thenReturn(null);

        initCarSystemBar();
        mCarSystemBar.start();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(TOP)).isNotNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(BOTTOM)).isNotNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(LEFT)).isNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(RIGHT)).isNull();

        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mSystemBarConfigs = new SystemBarConfigs(mTestableResources.getResources());
        when(mCarSystemBarController.getTopWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBottomWindow()).thenReturn(null);
        when(mCarSystemBarController.getLeftWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getRightWindow()).thenReturn(mock(ViewGroup.class));
        mCarSystemBar.restartSystemBars();

        verify(mCarSystemBarController, times(1)).removeAll();
        verify(mCarSystemBarController, times(1)).resetSystemBarConfigs();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(TOP)).isNotNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(BOTTOM)).isNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(LEFT)).isNotNull();
        assertThat(mCarSystemBar.getSystemBarWindowBySide(RIGHT)).isNotNull();
    }

    private void waitForDelayableExecutor() {
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }
}
