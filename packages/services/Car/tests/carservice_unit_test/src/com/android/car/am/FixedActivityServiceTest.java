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

package com.android.car.am;

import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;

import static com.android.car.CarLog.TAG_AM;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.cluster.ClusterActivityState;
import android.car.hardware.power.CarPowerManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FixedActivityServiceTest extends AbstractExtendedMockitoTestCase {

    private static final long RECHECK_INTERVAL_MARGIN_MS = 600;

    private static final int VALID_DISPLAY_ID = 1;

    @Mock
    private Context mContext;
    @Mock
    private CarActivityService mActivityService;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private CarUserService mCarUserService;
    @Mock
    private CarPowerManager mCarPowerManager;
    @Mock
    private UserHandleHelper mUserHandleHelper;
    @Mock
    private Display mValidDisplay;

    private FixedActivityService mFixedActivityService;

    public FixedActivityServiceTest() {
        super(TAG_AM);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
                .spyStatic(ActivityManager.class)
                .spyStatic(ActivityManagerHelper.class)
                .spyStatic(CarLocalServices.class);
    }

    @Before
    public void setUp() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        doReturn(mCarUserService).when(() -> CarLocalServices.getService(CarUserService.class));
        doReturn(mCarPowerManager).when(() -> CarLocalServices.createCarPowerManager(mContext));
        when(mDisplayManager.getDisplay(VALID_DISPLAY_ID)).thenReturn(mValidDisplay);
        when(mValidDisplay.getState()).thenReturn(Display.STATE_ON);
        mFixedActivityService = new FixedActivityService(mContext,
                mActivityService, mDisplayManager, mUserHandleHelper);
    }

    @After
    public void tearDown() {
        if (mFixedActivityService != null) {
            mFixedActivityService.release();
        }
        CarServiceUtils.finishAllHandlerTasks();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_noRunningActivity()
            throws Exception {
        int userId = 100;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_alreadyRunningActivity()
            throws Exception {
        int userId = 100;
        int taskId = 1234;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        clearInvocations(mContext);
        assertThat(ret).isTrue();

        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        // startActivityAsUser should not called at this time.
        verify(mContext, never()).startActivityAsUser(any(Intent.class), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_runNewActivity() throws Exception {
        int userId = 100;
        int taskId = 1234;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        Intent anotherIntent = expectComponentAvailable("test_package_II", "com.test.dude_II",
                userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();

        // Start activity with new package
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(anotherIntent,
                options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(anotherIntent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_WithNewExtras() throws Exception {
        int userId = 100;
        int taskId = 1234;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();

        ClusterActivityState clusterActivityState = ClusterActivityState.create(
                /* visible= */ true, /* unobscuredBounds= */ new Rect(1, 2, 3, 4));
        Intent intentWithExtras = new Intent(intent).putExtra(
                Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE, clusterActivityState.toBundle());
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intentWithExtras,
                options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(intentWithExtras), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_WithModifiedExtras() throws Exception {
        int userId = 100;
        int taskId = 1234;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ClusterActivityState clusterActivityState = ClusterActivityState.create(
                /* visible= */ true, /* unobscuredBounds= */ new Rect(1, 2, 3, 4));
        intent.putExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE, clusterActivityState.toBundle());
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();

        ClusterActivityState newClusterActivityState = ClusterActivityState
                .create(/* visible= */ true, /* unobscuredBounds= */ new Rect(5, 6, 7, 8));
        Intent intentWithModifiedExtras = new Intent(intent).putExtra(
                Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE, newClusterActivityState.toBundle());
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intentWithModifiedExtras, options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(intentWithModifiedExtras), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_WithTwoExtras() throws Exception {
        int userId = 100;
        int taskId = 1234;
        // The key is selected to have the bigger hashCode() than CAR_EXTRA_CLUSTER_ACTIVITY_STATE.
        String additionalExtraKey = "___DUMMY_KEY___";
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ClusterActivityState clusterActivityState = ClusterActivityState.create(
                /* visible= */ true, /* unobscuredBounds= */ new Rect(1, 2, 3, 4));
        intent.putExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE, clusterActivityState.toBundle());
        intent.putExtra(additionalExtraKey, 1);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();

        Intent intentWithAdditionalExtras = new Intent(intent);
        intent.putExtra(additionalExtraKey, 2);
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intentWithAdditionalExtras, options, VALID_DISPLAY_ID, userId);
        verify(mContext).startActivityAsUser(eq(intentWithAdditionalExtras), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplay_relaunchWithPackageUpdated() throws Exception {
        int userId = 100;
        int taskId = 1234;
        String packageName = "test_package";
        String className = "com.test.dude";
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        Intent intent = expectComponentAvailable(packageName, className, userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId),
                createEmptyTaskInfo(),  // Updating package will crash the app
                createRootTaskInfo(intent, userId, VALID_DISPLAY_ID, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        verify(mContext).registerReceiverForAllUsers(receiverCaptor.capture(),
                any(IntentFilter.class), eq(null), eq(null), anyInt());
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        clearInvocations(mContext);
        assertThat(ret).isTrue();

        // Update package
        SystemClock.sleep(RECHECK_INTERVAL_MARGIN_MS);
        int appId = 987;
        BroadcastReceiver receiver = receiverCaptor.getValue();
        Intent packageIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageIntent.setData(new Uri.Builder().path("Any package").build());
        packageIntent.putExtra(Intent.EXTRA_UID, UserHandle.getUid(userId, appId));
        receiver.onReceive(mContext, packageIntent);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        clearInvocations(mContext);

        SystemClock.sleep(RECHECK_INTERVAL_MARGIN_MS);
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        // Activity should not be launched.
        verify(mContext, never()).startActivityAsUser(any(Intent.class), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }


    @Test
    public void testStartFixedActivityModeForDisplayAndUser_runOnDifferentDisplay()
            throws Exception {
        int userId = 100;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        Intent anotherIntent = expectComponentAvailable("test_package_II", "com.test.dude_II",
                userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();

        int anotherValidDisplayId = VALID_DISPLAY_ID + 1;
        when(mDisplayManager.getDisplay(anotherValidDisplayId)).thenReturn(mValidDisplay);
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(anotherIntent,
                options, anotherValidDisplayId, userId);
        verify(mContext).startActivityAsUser(eq(anotherIntent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_invalidDisplay() throws Exception {
        int userId = 100;
        mockAmGetCurrentUser(userId);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        int invalidDisplayId = Display.DEFAULT_DISPLAY;

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options,
                invalidDisplayId, userId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_unavailableDisplay() throws Exception {
        int userId = 100;
        mockAmGetCurrentUser(userId);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        int unavailableDisplayId = VALID_DISPLAY_ID + 1;

        boolean started = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, unavailableDisplayId, userId);
        assertThat(started).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_nonActiveDisplay() throws Exception {
        int mockDisplayId = VALID_DISPLAY_ID + 1;
        Display mockDisplay = Mockito.mock(Display.class);
        when(mDisplayManager.getDisplay(mockDisplayId)).thenReturn(mockDisplay);
        when(mockDisplay.getState()).thenReturn(Display.STATE_OFF, Display.STATE_ON);

        int userId = 100;
        mockAmGetCurrentUser(userId);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(mockDisplayId);

        // Display.STATE_OFF
        boolean started1 = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, mockDisplayId, userId);
        assertThat(started1).isFalse();

        verify(mContext, never()).startActivityAsUser(any(Intent.class), any(Bundle.class),
                any(UserHandle.class));

        // Display.STATE_ON
        boolean started2 = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, mockDisplayId, userId);
        assertThat(started2).isTrue();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Bundle> activityOptionsCaptor = ArgumentCaptor.forClass(Bundle.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);

        verify(mContext, times(1)).startActivityAsUser(intentCaptor.capture(),
                activityOptionsCaptor.capture(), userHandleCaptor.capture());
        assertThat(userHandleCaptor.getAllValues().get(0)).isEqualTo(UserHandle.of(userId));
        assertThat(ActivityOptions.fromBundle(activityOptionsCaptor.getAllValues().get(0))
                .getLaunchDisplayId()).isEqualTo(mockDisplayId);
        Intent capturedIntent =  intentCaptor.getAllValues().get(0);
        assertThat(capturedIntent.getComponent()).isEqualTo(intent.getComponent());
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_displayRemoved()
            throws Exception {
        int displayToBeRemoved = VALID_DISPLAY_ID + 1;
        when(mDisplayManager.getDisplay(displayToBeRemoved)).thenReturn(mValidDisplay);
        int userId = 100;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        boolean started = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, displayToBeRemoved, userId);
        assertThat(started).isTrue();
        assertThat(mFixedActivityService.hasRunningFixedActivity(displayToBeRemoved)).isTrue();

        // The display is still valid.
        mFixedActivityService.launchIfNecessary();
        assertThat(mFixedActivityService.hasRunningFixedActivity(displayToBeRemoved)).isTrue();

        // The display is removed.
        when(mDisplayManager.getDisplay(displayToBeRemoved)).thenReturn(null);
        mFixedActivityService.launchIfNecessary();
        assertThat(mFixedActivityService.hasRunningFixedActivity(displayToBeRemoved)).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_notAllowedUser() {
        int currentUserId = 100;
        int notAllowedUserId = 101;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        int displayId = VALID_DISPLAY_ID;
        mockAmGetCurrentUser(currentUserId);
        expectNoProfileUser(currentUserId);

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options,
                displayId, notAllowedUserId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_invalidComponent() throws Exception {
        int userId = 100;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent invalidIntent = expectComponentUnavailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(invalidIntent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStopFixedActivityMode() throws Exception {
        int userId = 100;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // Start an activity
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, userId);
        assertThat(ret).isTrue();
        // To check if monitoring is started.
        verify(() -> ActivityManagerHelper.registerProcessObserverCallback(
                any(ActivityManagerHelper.ProcessObserverCallback.class)));

        mFixedActivityService.stopFixedActivityMode(VALID_DISPLAY_ID);
        verify(() -> ActivityManagerHelper.unregisterProcessObserverCallback(
                any(ActivityManagerHelper.ProcessObserverCallback.class)));
    }

    @Test
    public void testStopFixedActivityMode_invalidDisplayId() throws Exception {
        mFixedActivityService.stopFixedActivityMode(Display.DEFAULT_DISPLAY);
        verify(() -> ActivityManagerHelper.unregisterProcessObserverCallback(
                any(ActivityManagerHelper.ProcessObserverCallback.class)), never());
    }

    @Test
    public void onUserSwitch_clearsRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 100,
                /* toUserId = */ 101,
                /* runningFixedActivityExpected = */ false);
    }

    @Test
    public void onUserSwitchFromSystemUser_noChangeInRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ UserHandle.USER_SYSTEM,
                /* toUserId = */ 101,
                /* runningFixedActivityExpected = */ true);
    }

    @Test
    public void onUserSwitchToUserWithEnabledProfile_noChangeInRunningActivities()
            throws Exception {
        when(mUserHandleHelper.getEnabledProfiles(101))
                .thenReturn(Arrays.asList(UserHandle.of(100)));
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 100,
                /* toUserId = */ 101,
                /* runningFixedActivityExpected = */ true);
    }

    @Test
    public void onUserSwitchToSameUser_noChangeInRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 101,
                /* toUserId = */ 101,
                /* runningFixedActivityExpected = */ true);
    }

    @Test
    public void userSwitchedToNotAllowedUser_launchesBlankActivity() throws Exception {
        String blankActivityComponentName = "package/blankActivityClassName";
        int userId = 100;
        int taskId = 1234;
        int notAllowedUserId = 101;
        when(mContext.getString(anyInt())).thenReturn(blankActivityComponentName);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        List<ActivityManager.RunningTaskInfo> rootTaskInfo = createRootTaskInfo(intent, userId,
                VALID_DISPLAY_ID, taskId);
        expectRootTaskInfo(rootTaskInfo);

        mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, VALID_DISPLAY_ID, userId);

        mockAmGetCurrentUser(notAllowedUserId);
        mFixedActivityService.launchIfNecessary();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Bundle> activityOptionsCaptor = ArgumentCaptor.forClass(Bundle.class);
        ArgumentCaptor<UserHandle> userHandleCaptor = ArgumentCaptor.forClass(UserHandle.class);

        verify(mContext, times(2)).startActivityAsUser(intentCaptor.capture(),
                activityOptionsCaptor.capture(), userHandleCaptor.capture());

        // Called when startFixedActivityModeForDisplayAndUser().
        assertThat(userHandleCaptor.getAllValues().get(0)).isEqualTo(UserHandle.of(userId));
        assertThat(ActivityOptions.fromBundle(activityOptionsCaptor.getAllValues().get(0))
                .getLaunchDisplayId()).isEqualTo(VALID_DISPLAY_ID);
        Intent capturedIntent =  intentCaptor.getAllValues().get(0);
        assertThat(capturedIntent.getComponent()).isEqualTo(intent.getComponent());

        // Called when launchIfNecessary().
        assertThat(userHandleCaptor.getAllValues().get(1))
                .isEqualTo(UserHandle.of(notAllowedUserId));
        assertThat(ActivityOptions.fromBundle(activityOptionsCaptor.getAllValues().get(1))
                .getLaunchDisplayId()).isEqualTo(VALID_DISPLAY_ID);
        Intent blankActivityIntent =  intentCaptor.getAllValues().get(1);
        assertThat(blankActivityIntent.getComponent()).isEqualTo(
                ComponentName.unflattenFromString(blankActivityComponentName));
        assertThat(blankActivityIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK)
                .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThat(blankActivityIntent.getFlags() & Intent.FLAG_ACTIVITY_NO_HISTORY)
                .isEqualTo(Intent.FLAG_ACTIVITY_NO_HISTORY);
    }

    @Test
    public void testDisplayListener_startsActivityWhenDisplayTurnsOn() throws Exception {
        ArgumentCaptor<DisplayListener> displayListenerCaptor =
                ArgumentCaptor.forClass(DisplayListener.class);
        mFixedActivityService.init();
        verify(mDisplayManager).registerDisplayListener(displayListenerCaptor.capture(), any());
        DisplayListener displayListener = displayListenerCaptor.getValue();

        int mockDisplayId = VALID_DISPLAY_ID + 2;
        Display mockDisplay = Mockito.mock(Display.class);
        when(mDisplayManager.getDisplay(mockDisplayId)).thenReturn(mockDisplay);
        when(mockDisplay.getState()).thenReturn(Display.STATE_OFF);

        int userId = 100;
        mockAmGetCurrentUser(userId);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(mockDisplayId);

        // The display is OFF, and the activity is not started.
        boolean started1 = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, mockDisplayId, userId);
        assertThat(started1).isFalse();
        verify(mContext, never()).startActivityAsUser(
                any(Intent.class), any(Bundle.class), any(UserHandle.class));

        // Turn ON the display and invoke the display listener.
        when(mockDisplay.getState()).thenReturn(Display.STATE_ON);
        displayListener.onDisplayChanged(mockDisplayId);

        // Now verify that the activity has started.
        verify(mContext, times(1)).startActivityAsUser(
                any(Intent.class), any(Bundle.class), any(UserHandle.class));
    }

    private void testClearingOfRunningActivitiesOnUserSwitch(int fromUserId, int toUserId,
            boolean runningFixedActivityExpected) throws Exception {
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(VALID_DISPLAY_ID);
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", fromUserId);
        mockAmGetCurrentUser(fromUserId);
        expectNoActivityStack();
        doAnswer(invocation -> {
            CarUserManager.UserLifecycleListener userLifecycleListener =
                    (CarUserManager.UserLifecycleListener) invocation.getArgument(1);
            mockAmGetCurrentUser(toUserId);
            userLifecycleListener.onEvent(new CarUserManager.UserLifecycleEvent(
                    CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, toUserId));
            return null;
        }).when(mCarUserService).addUserLifecycleListener(any(), any());

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, VALID_DISPLAY_ID, fromUserId);
        assertThat(ret).isTrue();
        verify(mCarUserService).addUserLifecycleListener(any(), any());

        if (runningFixedActivityExpected) {
            assertThat(mFixedActivityService.hasRunningFixedActivity(VALID_DISPLAY_ID)).isTrue();
        } else {
            assertThat(mFixedActivityService.hasRunningFixedActivity(VALID_DISPLAY_ID)).isFalse();
        }
    }

    private void expectNoProfileUser(@UserIdInt int userId) {
        when(mUserHandleHelper.getEnabledProfiles(userId)).thenReturn(new ArrayList<UserHandle>());
    }

    private Intent expectComponentUnavailable(String pkgName, String className,
            @UserIdInt int userId) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName component = new ComponentName(pkgName, className);
        intent.setComponent(component);
        ActivityInfo activityInfo = new ActivityInfo();
        // To make sure there is no matched activity
        activityInfo.name = component.getClassName() + ".unavailable";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.activities = new ActivityInfo[] { activityInfo };
        when(mPackageManager.getPackageInfoAsUser(component.getPackageName(),
                PackageManager.GET_ACTIVITIES, userId)).thenReturn(packageInfo);
        return intent;
    }

    private Intent expectComponentAvailable(String pkgName, String className, @UserIdInt int userId)
            throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName component = new ComponentName(pkgName, className);
        intent.setComponent(component);
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.name = component.getClassName();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.activities = new ActivityInfo[] { activityInfo };
        when(mPackageManager.getPackageInfoAsUser(component.getPackageName(),
                PackageManager.GET_ACTIVITIES, userId)).thenReturn(packageInfo);
        return intent;
    }

    private void expectNoActivityStack() throws Exception {
        when(mActivityService.getVisibleTasksInternal(anyInt())).thenReturn(createEmptyTaskInfo());
    }

    private void expectRootTaskInfo(List<ActivityManager.RunningTaskInfo>... taskInfos)
            throws Exception {
        OngoingStubbing<List<ActivityManager.RunningTaskInfo>> stub = when(
                mActivityService.getVisibleTasksInternal(anyInt()));
        for (List<ActivityManager.RunningTaskInfo> taskInfo : taskInfos) {
            stub = stub.thenReturn(taskInfo);
        }
    }

    private List<ActivityManager.RunningTaskInfo> createEmptyTaskInfo() {
        return new ArrayList<>();
    }

    private List<ActivityManager.RunningTaskInfo> createRootTaskInfo(Intent intent,
            @UserIdInt int userId, int displayId, int taskId) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.topActivity = intent.getComponent().clone();
        taskInfo.taskId = taskId;
        taskInfo.userId = userId;
        taskInfo.displayId = displayId;
        List<ActivityManager.RunningTaskInfo> topTasks = new ArrayList<>();
        topTasks.add(taskInfo);
        return topTasks;
    }
}
