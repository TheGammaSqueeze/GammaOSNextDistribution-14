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

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.app.StatusBarManager.DISABLE_HOME;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;

import com.android.car.ui.FocusParkingView;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.StatusBarIconController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Set;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarControllerTest extends SysuiTestCase {

    private static final String TOP_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.TopNotificationPanelViewMediator";
    private static final String BOTTOM_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.BottomNotificationPanelViewMediator";
    private CarSystemBarController mCarSystemBar;
    private CarSystemBarViewFactory mCarSystemBarViewFactory;
    private TestableResources mTestableResources;
    private Context mSpiedContext;
    private MockitoSession mSession;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private ButtonSelectionStateController mButtonSelectionStateController;
    @Mock
    private ButtonRoleHolderController mButtonRoleHolderController;
    @Mock
    private UserNameViewController mUserNameViewController;
    @Mock
    private MicPrivacyChipViewController mMicPrivacyChipViewController;
    @Mock
    private CameraPrivacyChipViewController mCameraPrivacyChipViewController;
    @Mock
    private FeatureFlags mFeatureFlags;
    @Mock
    private QuickControlsEntryPointsController mQuickControlsEntryPointsController;
    @Mock
    private StatusIconPanelViewController.Builder mPanelControllerBuilder;
    @Mock
    private StatusIconPanelViewController mPanelController;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private CarSystemBarElementInitializer mCarSystemBarElementInitializer;

    @Before
    public void setUp() throws Exception {
        mSession = ExtendedMockito.mockitoSession()
            .initMocks(this)
            .spyStatic(CarSystemUIUserUtil.class)
            .strictness(Strictness.LENIENT)
            .startMocking();
        mTestableResources = mContext.getOrCreateTestableResources();
        mSpiedContext = spy(mContext);
        when(mSpiedContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        mCarSystemBarViewFactory = new CarSystemBarViewFactory(mSpiedContext, mFeatureFlags,
                mQuickControlsEntryPointsController, mock(UserTracker.class),
                mCarSystemBarElementInitializer);
        setupPanelControllerBuilderMocks();

        // Needed to inflate top navigation bar.
        mDependency.injectMockDependency(DarkIconDispatcher.class);
        mDependency.injectMockDependency(StatusBarIconController.class);
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private CarSystemBarController createSystemBarController() {
        return new CarSystemBarController(mSpiedContext, mUserTracker, mCarSystemBarViewFactory,
                mCarServiceProvider, mButtonSelectionStateController, () -> mUserNameViewController,
                () -> mMicPrivacyChipViewController, () -> mCameraPrivacyChipViewController,
                mButtonRoleHolderController,
                new SystemBarConfigs(mTestableResources.getResources()),
                () -> mPanelControllerBuilder);
    }

    @Test
    public void testRemoveAll_callsButtonRoleHolderControllerRemoveAll() {
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.removeAll();

        verify(mButtonRoleHolderController).removeAll();
    }

    @Test
    public void testRemoveAll_callsButtonSelectionStateControllerRemoveAll() {
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.removeAll();

        verify(mButtonSelectionStateController).removeAll();
    }

    @Test
    public void testRemoveAll_callsPrivacyChipViewControllerRemoveAll() {
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.removeAll();

        verify(mMicPrivacyChipViewController).removeAll();
        verify(mCameraPrivacyChipViewController).removeAll();
    }

    @Test
    public void testGetTopWindow_topDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        // If Top Notification Panel is used but top navigation bar is not enabled, SystemUI is
        // expected to crash.
        mTestableResources.addOverride(R.string.config_notificationPanelViewMediator,
                BOTTOM_NOTIFICATION_PANEL);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getTopWindow();

        assertThat(window).isNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getTopWindow();

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window1 = mCarSystemBar.getTopWindow();
        ViewGroup window2 = mCarSystemBar.getTopWindow();

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetBottomWindow_bottomDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        // If Bottom Notification Panel is used but bottom navigation bar is not enabled,
        // SystemUI is expected to crash.
        mTestableResources.addOverride(R.string.config_notificationPanelViewMediator,
                TOP_NOTIFICATION_PANEL);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getBottomWindow();

        assertThat(window).isNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getBottomWindow();

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window1 = mCarSystemBar.getBottomWindow();
        ViewGroup window2 = mCarSystemBar.getBottomWindow();

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetLeftWindow_leftDisabled_returnsNull() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mCarSystemBar = createSystemBarController();
        ViewGroup window = mCarSystemBar.getLeftWindow();
        assertThat(window).isNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_returnsWindow() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getLeftWindow();

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window1 = mCarSystemBar.getLeftWindow();
        ViewGroup window2 = mCarSystemBar.getLeftWindow();

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetRightWindow_rightDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getRightWindow();

        assertThat(window).isNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getRightWindow();

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window1 = mCarSystemBar.getRightWindow();
        ViewGroup window2 = mCarSystemBar.getRightWindow();

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testSetTopWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getTopWindow();
        mCarSystemBar.setTopWindowVisibility(View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetTopWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getTopWindow();
        mCarSystemBar.setTopWindowVisibility(View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetBottomWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getBottomWindow();
        mCarSystemBar.setBottomWindowVisibility(View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetBottomWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getBottomWindow();
        mCarSystemBar.setBottomWindowVisibility(View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetLeftWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getLeftWindow();
        mCarSystemBar.setLeftWindowVisibility(View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetLeftWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getLeftWindow();
        mCarSystemBar.setLeftWindowVisibility(View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetRightWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getRightWindow();
        mCarSystemBar.setRightWindowVisibility(View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetRightWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBar = createSystemBarController();

        ViewGroup window = mCarSystemBar.getRightWindow();
        mCarSystemBar.setRightWindowVisibility(View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testRegisterBottomBarTouchListener_createViewFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        Set<View.OnTouchListener> controllers = bottomBar.getStatusBarWindowTouchListeners();
        assertThat(controllers).isNotNull();
        assertThat(controllers.size()).isEqualTo(0);
        mCarSystemBar.registerBottomBarTouchListener(mock(View.OnTouchListener.class));
        controllers = bottomBar.getStatusBarWindowTouchListeners();

        assertThat(controllers).isNotNull();
        assertThat(controllers.size()).isEqualTo(1);
    }

    @Test
    public void testRegisterBottomBarTouchListener_registerFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.registerBottomBarTouchListener(mock(View.OnTouchListener.class));
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        Set<View.OnTouchListener> controllers = bottomBar.getStatusBarWindowTouchListeners();

        assertThat(controllers).isNotNull();
        assertThat(controllers.size()).isEqualTo(1);
    }

    @Test
    public void testRegisterNotificationController_createViewFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarController.NotificationsShadeController controller =
                bottomBar.getNotificationsPanelController();
        assertThat(controller).isNull();
        mCarSystemBar.registerNotificationController(
                mock(CarSystemBarController.NotificationsShadeController.class));
        controller = bottomBar.getNotificationsPanelController();

        assertThat(controller).isNotNull();
    }

    @Test
    public void testRegisterNotificationController_registerFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.registerNotificationController(
                mock(CarSystemBarController.NotificationsShadeController.class));
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarController.NotificationsShadeController controller =
                bottomBar.getNotificationsPanelController();

        assertThat(controller).isNotNull();
    }

    @Test
    public void testRegisterHvacController_createViewFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarController.HvacPanelController controller = bottomBar.getHvacPanelController();
        assertThat(controller).isNull();
        mCarSystemBar.registerHvacPanelController(
                mock(CarSystemBarController.HvacPanelController.class));
        controller = bottomBar.getHvacPanelController();

        assertThat(controller).isNotNull();
    }

    @Test
    public void testRegisterHvacController_registerFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        mCarSystemBar.registerHvacPanelController(
                mock(CarSystemBarController.HvacPanelController.class));
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarController.HvacPanelController controller = bottomBar.getHvacPanelController();

        assertThat(controller).isNotNull();
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomNavigationButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View bottomNavButtons = bottomBar.findViewById(R.id.nav_buttons);

        mCarSystemBar.showAllNavigationButtons(/* isSetUp= */ true);

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBar.showAllNavigationButtons(/* isSetUp= */ true);

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View occlusionButtons = bottomBar.findViewById(R.id.occlusion_buttons);

        mCarSystemBar.showAllNavigationButtons(/* isSetUp= */ true);

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomKeyguardButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBar.showAllKeyguardButtons(/* isSetUp= */ true);

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View bottomNavButtons = bottomBar.findViewById(R.id.nav_buttons);

        mCarSystemBar.showAllKeyguardButtons(/* isSetUp= */ true);

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View occlusionButtons = bottomBar.findViewById(R.id.occlusion_buttons);

        mCarSystemBar.showAllKeyguardButtons(/* isSetUp= */ true);

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomOcclusionButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View occlusionButtons = bottomBar.findViewById(R.id.occlusion_buttons);

        mCarSystemBar.showAllOcclusionButtons(/* isSetUp= */ true);

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View bottomNavButtons = bottomBar.findViewById(R.id.nav_buttons);

        mCarSystemBar.showAllOcclusionButtons(/* isSetUp= */ true);

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View keyguardButtons = bottomBar.findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBar.showAllOcclusionButtons(/* isSetUp= */ true);

        assertThat(keyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testToggleAllNotificationsUnseenIndicator_bottomEnabled_hasUnseen_setCorrectly() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarButton notifications = bottomBar.findViewById(R.id.notifications);

        boolean hasUnseen = true;
        mCarSystemBar.toggleAllNotificationsUnseenIndicator(/* isSetUp= */ true,
                hasUnseen);

        assertThat(notifications.getUnseen()).isTrue();
    }

    @Test
    public void testToggleAllNotificationsUnseenIndicator_bottomEnabled_noUnseen_setCorrectly() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        CarSystemBarButton notifications = bottomBar.findViewById(R.id.notifications);

        boolean hasUnseen = false;
        mCarSystemBar.toggleAllNotificationsUnseenIndicator(/* isSetUp= */ true,
                hasUnseen);

        assertThat(notifications.getUnseen()).isFalse();
    }

    @Test
    public void testSetSystemBarStates_stateUpdated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        clearSystemBarStates();

        mCarSystemBar.setSystemBarStates(DISABLE_HOME, /* state2= */ 0);

        assertThat(mCarSystemBar.getStatusBarState()).isEqualTo(DISABLE_HOME);
    }

    @Test
    public void testSetSystemBarStates_state2Updated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        clearSystemBarStates();

        mCarSystemBar.setSystemBarStates(0, DISABLE2_QUICK_SETTINGS);

        assertThat(mCarSystemBar.getStatusBarState2()).isEqualTo(DISABLE2_QUICK_SETTINGS);
    }

    @Test
    public void testRefreshSystemBar_homeDisabled() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        clearSystemBarStates();
        CarSystemBarButton button = bottomBar.findViewById(R.id.home);
        assertThat(button.getDisabled()).isFalse();

        mCarSystemBar.setSystemBarStates(DISABLE_HOME, /* state2= */ 0);

        assertThat(button.getDisabled()).isTrue();
    }

    @Test
    public void testRefreshSystemBar_phoneNavDisabled() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        clearSystemBarStates();
        CarSystemBarButton button = bottomBar.findViewById(R.id.phone_nav);
        assertThat(button.getDisabled()).isFalse();

        setLockTaskModeLocked(/* locked= */true);

        assertThat(button.getDisabled()).isTrue();
    }

    @Test
    public void testRefreshSystemBar_appGridisabled() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        clearSystemBarStates();
        CarSystemBarButton button = bottomBar.findViewById(R.id.grid_nav);
        assertThat(button.getDisabled()).isFalse();

        mCarSystemBar.setSystemBarStates(DISABLE_HOME, /* state2= */ 0);

        assertThat(button.getDisabled()).isTrue();
    }

    @Test
    public void testRefreshSystemBar_notificationDisabled() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        clearSystemBarStates();
        CarSystemBarButton button = bottomBar.findViewById(R.id.notifications);
        assertThat(button.getDisabled()).isFalse();

        mCarSystemBar.setSystemBarStates(DISABLE_NOTIFICATION_ICONS, /* state2= */ 0);

        assertThat(button.getDisabled()).isTrue();
    }

    @Test
    public void testRefreshSystemBar_disableQcFlagOn_qcHidden() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView topBar = mCarSystemBar.getTopBar(/* isSetUp= */ true);
        View qcView = topBar.findViewById(R.id.qc_entry_points_container);
        clearSystemBarStates();
        assertThat(qcView.getVisibility()).isEqualTo(View.VISIBLE);

        mCarSystemBar.setSystemBarStates(0, DISABLE2_QUICK_SETTINGS);

        assertThat(qcView.getVisibility()).isEqualTo(View.INVISIBLE);
    }

    @Test
    public void testRefreshSystemBar_lockTaskModeOn_qcHidden() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView topBar = mCarSystemBar.getTopBar(/* isSetUp= */ true);
        View qcView = topBar.findViewById(R.id.qc_entry_points_container);
        clearSystemBarStates();
        assertThat(qcView.getVisibility()).isEqualTo(View.VISIBLE);

        setLockTaskModeLocked(/* locked= */ true);

        assertThat(qcView.getVisibility()).isEqualTo(View.INVISIBLE);
    }

    @Test
    public void testRefreshSystemBar_disableQcFlagOn_userSwitcherHidden() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView topBar = mCarSystemBar.getTopBar(/* isSetUp= */ true);

        View userSwitcher = topBar.findViewById(R.id.user_name_container);
        clearSystemBarStates();
        assertThat(userSwitcher.getVisibility()).isEqualTo(View.VISIBLE);

        mCarSystemBar.setSystemBarStates(0, DISABLE2_QUICK_SETTINGS);

        assertThat(userSwitcher.getVisibility()).isEqualTo(View.INVISIBLE);
    }

    @Test
    public void testRefreshSystemBar_lockTaskModeOn_userSwitcherHidden() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBar = createSystemBarController();
        CarSystemBarView topBar = mCarSystemBar.getTopBar(/* isSetUp= */ true);
        View userSwitcher = topBar.findViewById(R.id.user_name_container);
        clearSystemBarStates();
        assertThat(userSwitcher.getVisibility()).isEqualTo(View.VISIBLE);

        setLockTaskModeLocked(/* locked= */ true);

        assertThat(userSwitcher.getVisibility()).isEqualTo(View.INVISIBLE);
    }

    @Test
    public void cacheAndHideFocus_doesntCallHideFocus_if_focusParkingViewIsFocused() {
        mCarSystemBar = createSystemBarController();
        View mockFocusParkingView = mock(FocusParkingView.class);
        View mockContainerView = mock(View.class);
        when(mockContainerView.findFocus()).thenReturn(mockFocusParkingView);

        int returnFocusedViewId = mCarSystemBar.cacheAndHideFocus(mockContainerView);

        assertThat(returnFocusedViewId).isEqualTo(View.NO_ID);
    }

    @Test
    public void testDriverHomeOnDriverSystemUI_isVisible() {
        doReturn(false).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, /* value= */ true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View driverHomeButton = bottomBar.findViewById(R.id.home);
        View passengerHomeButton = bottomBar.findViewById(R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testPassengerHomeOnSecondarySystemUI_isVisible() {
        doReturn(true).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBar = createSystemBarController();

        CarSystemBarView bottomBar = mCarSystemBar.getBottomBar(/* isSetUp= */ true);
        View driverHomeButton = bottomBar.findViewById(R.id.home);
        View passengerHomeButton = bottomBar.findViewById(R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    private void clearSystemBarStates() {
        if (mCarSystemBar != null) {
            mCarSystemBar.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
        }
        setLockTaskModeLocked(false);
    }

    private void setLockTaskModeLocked(boolean locked) {
        when(mActivityManager.getLockTaskModeState()).thenReturn(locked
                ? ActivityManager.LOCK_TASK_MODE_LOCKED
                : ActivityManager.LOCK_TASK_MODE_NONE);
        mCarSystemBar.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
    }

    private void setupPanelControllerBuilderMocks() {
        when(mPanelControllerBuilder.setXOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setYOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setGravity(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setDisabledWhileDriving(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.setShowAsDropDown(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.build(any(), anyInt(), anyInt())).thenReturn(mPanelController);
    }
}
