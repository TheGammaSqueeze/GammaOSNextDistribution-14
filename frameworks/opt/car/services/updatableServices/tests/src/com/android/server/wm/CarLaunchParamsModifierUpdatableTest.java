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

package com.android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.FLAG_PRIVATE;
import static android.view.Display.FLAG_TRUSTED;
import static android.view.Display.INVALID_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaOrganizer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.policy.AttributeCache;
import com.android.server.LocalServices;
import com.android.server.display.color.ColorDisplayService;
import com.android.server.input.InputManagerService;
import com.android.server.pm.UserManagerInternal;
import com.android.server.policy.WindowManagerPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.function.Function;

/**
 * Tests for {@link CarLaunchParamsModifier}
 * Build/Install/Run:
 *  atest FrameworkOptCarServicesUpdatableTest:CarLaunchParamsModifierUpdatableTest
 */
@RunWith(AndroidJUnit4.class)
public class CarLaunchParamsModifierUpdatableTest {
    // TODO(b/262267582): Use these constants directly in the tests and remove the corresponding
    // mock variables.
    private static final int PASSENGER_DISPLAY_ID_10 = 10;
    private static final int PASSENGER_DISPLAY_ID_11 = 11;
    private static final int RANDOM_DISPLAY_ID_99 = 99;
    private static final int VIRTUAL_DISPLAY_ID_2 = 2;
    private static final int FEATURE_MAP_ID = 1111;

    private MockitoSession mMockingSession;

    private CarLaunchParamsModifier mModifier;
    private CarLaunchParamsModifierUpdatableImpl mUpdatable;
    private CarLaunchParamsModifierInterface mBuiltin;

    private Context mContext;
    private WindowManagerService mWindowManagerService;
    private final WindowManagerGlobalLock mWindowManagerGlobalLock = new WindowManagerGlobalLock();

    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private ActivityTaskManagerService mActivityTaskManagerService;
    @Mock
    private ActivityTaskSupervisor mActivityTaskSupervisor;
    @Mock
    private RecentTasks mRecentTasks;
    @Mock
    private ColorDisplayService.ColorDisplayServiceInternal mColorDisplayServiceInternal;
    @Mock
    private RootWindowContainer mRootWindowContainer;
    @Mock
    private LaunchParamsController mLaunchParamsController;
    @Mock
    private PackageConfigPersister mPackageConfigPersister;
    @Mock
    private InputManagerService mInputManagerService;
    @Mock
    private UserManagerInternal mUserManagerInternal;

    @Mock
    private Display mDisplay0ForDriver;
    private TaskDisplayArea mDisplayArea0ForDriver;
    @Mock
    private Display mDisplay1Private;
    private TaskDisplayArea mDisplayArea1Private;
    @Mock
    private Display mDisplay10ForPassenger;
    private TaskDisplayArea mDisplayArea10ForPassenger;
    @Mock
    private Display mDisplay11ForPassenger;
    private TaskDisplayArea mDisplayArea11ForPassenger;
    @Mock
    private Display mDisplay2Virtual;
    private TaskDisplayArea mDisplayArea2Virtual;
    private TaskDisplayArea mMapTaskDisplayArea;

    // All mocks from here before CarLaunchParamsModifier are arguments for
    // LaunchParamsModifier.onCalculate() call.
    @Mock
    private Task mTask;
    @Mock
    private ActivityInfo.WindowLayout mWindowLayout;
    @Mock
    private ActivityRecord mActivityRecordActivity;
    @Mock
    private ActivityRecord mActivityRecordSource;
    @Mock
    private ActivityOptions mActivityOptions;
    @Mock
    private LaunchParamsController.LaunchParams mCurrentParams;
    @Mock
    private LaunchParamsController.LaunchParams mOutParams;

    private TaskDisplayArea mockDisplay(Display display, int displayId, int flags, int type) {
        when(mDisplayManager.getDisplay(displayId)).thenReturn(display);
        when(display.getDisplayId()).thenReturn(displayId);
        when(display.getFlags()).thenReturn(flags);
        when(display.getType()).thenReturn(type);

        // Return the same id as the display for simplicity
        DisplayContent dc = mock(DisplayContent.class);
        TaskDisplayArea defaultTaskDisplayArea = new TaskDisplayArea(dc, mWindowManagerService,
                "defaultTDA#" + displayId, DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER);
        when(mRootWindowContainer.getDisplayContent(displayId)).thenReturn(dc);
        when(mRootWindowContainer.getDisplayContentOrCreate(displayId)).thenReturn(dc);
        when(dc.getDisplay()).thenReturn(display);
        when(dc.getDefaultTaskDisplayArea()).thenReturn(defaultTaskDisplayArea);
        when(dc.isTrusted()).thenReturn((flags & FLAG_TRUSTED) == FLAG_TRUSTED);
        return defaultTaskDisplayArea;
    }

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(ActivityTaskManager.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        mContext = getInstrumentation().getTargetContext();
        spyOn(mContext);
        doReturn(mDisplayManager).when(mContext).getSystemService(eq(DisplayManager.class));

        doReturn(mActivityTaskManagerService).when(() -> ActivityTaskManager.getService());
        mActivityTaskManagerService.mTaskSupervisor = mActivityTaskSupervisor;
        when(mActivityTaskSupervisor.getLaunchParamsController()).thenReturn(
                mLaunchParamsController);
        mActivityTaskManagerService.mSupportsMultiDisplay = true;
        mActivityTaskManagerService.mRootWindowContainer = mRootWindowContainer;
        mActivityTaskManagerService.mPackageConfigPersister = mPackageConfigPersister;
        mActivityTaskManagerService.mWindowOrganizerController =
                new WindowOrganizerController(mActivityTaskManagerService);
        mActivityTaskManagerService.mContext = mContext;
        when(mActivityTaskManagerService.getTransitionController()).thenCallRealMethod();
        when(mActivityTaskManagerService.getRecentTasks()).thenReturn(mRecentTasks);
        when(mActivityTaskManagerService.getGlobalLock()).thenReturn(mWindowManagerGlobalLock);
        when(mActivityTaskManagerService.getUiContext()).thenReturn(mContext);

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        when(mUserManagerInternal.getUserAssignedToDisplay(anyInt()))
                .thenReturn(UserHandle.USER_NULL);
        when(mUserManagerInternal.getMainDisplayAssignedToUser(anyInt()))
                .thenReturn(INVALID_DISPLAY);

        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.removeServiceForTest(ImeTargetVisibilityPolicy.class);
        mWindowManagerService = WindowManagerService.main(
                mContext, mInputManagerService, /* showBootMsgs= */ false, /* policy= */ null,
                mActivityTaskManagerService,
                /* displayWindowSettingsProvider= */ null, () -> new SurfaceControl.Transaction(),
                /* surfaceControlFactory= */ null);
        mActivityTaskManagerService.mWindowManager = mWindowManagerService;
        mRootWindowContainer.mWindowManager = mWindowManagerService;

        AttributeCache.init(getInstrumentation().getTargetContext());
        LocalServices.addService(ColorDisplayService.ColorDisplayServiceInternal.class,
                mColorDisplayServiceInternal);
        when(mActivityOptions.getLaunchDisplayId()).thenReturn(INVALID_DISPLAY);
        mDisplayArea0ForDriver = mockDisplay(mDisplay0ForDriver, DEFAULT_DISPLAY,
                FLAG_TRUSTED, /* type= */ 0);
        DisplayContent defaultDC = mRootWindowContainer.getDisplayContentOrCreate(DEFAULT_DISPLAY);
        mMapTaskDisplayArea = new TaskDisplayArea(
                defaultDC, mWindowManagerService, "MapTDA", FEATURE_MAP_ID);
        doAnswer((invocation) -> {
            Function<TaskDisplayArea, TaskDisplayArea> callback = invocation.getArgument(0);
            return callback.apply(mMapTaskDisplayArea);
        }).when(defaultDC).getItemFromTaskDisplayAreas(any());
        when(mActivityRecordSource.getDisplayContent()).thenReturn(defaultDC);

        mDisplayArea10ForPassenger = mockDisplay(mDisplay10ForPassenger, PASSENGER_DISPLAY_ID_10,
                FLAG_TRUSTED, /* type= */ 0);
        mDisplayArea11ForPassenger = mockDisplay(mDisplay11ForPassenger, PASSENGER_DISPLAY_ID_11,
                FLAG_TRUSTED, /* type= */ 0);
        mDisplayArea1Private = mockDisplay(mDisplay1Private, 1,
                FLAG_TRUSTED | FLAG_PRIVATE, /* type= */ 0);
        mDisplayArea2Virtual = mockDisplay(mDisplay2Virtual, VIRTUAL_DISPLAY_ID_2,
                FLAG_PRIVATE, /* type= */ 0);

        mModifier = new CarLaunchParamsModifier(mContext);
        mBuiltin = mModifier.getBuiltinInterface();
        mUpdatable = new CarLaunchParamsModifierUpdatableImpl(mBuiltin);
        mModifier.setUpdatable(mUpdatable);
        mModifier.init();
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.removeServiceForTest(WindowManagerPolicy.class);
        LocalServices.removeServiceForTest(ColorDisplayService.ColorDisplayServiceInternal.class);
        // If the exception is thrown during the MockingSession setUp, mMockingSession can be null.
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    private void assertDisplayIsAllowed(@UserIdInt int userId, Display display) {
        mTask.mUserId = userId;
        mCurrentParams.mPreferredTaskDisplayArea = mBuiltin.getDefaultTaskDisplayAreaOnDisplay(
                display.getDisplayId()).getTaskDisplayArea();
        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, mActivityOptions, /* request= */ null , /* phase= */ 0,
                mCurrentParams, mOutParams))
                .isEqualTo(LaunchParamsController.LaunchParamsModifier.RESULT_SKIP);
    }

    private void assertDisplayIsReassigned(@UserIdInt int userId, Display displayRequested,
            Display displayAssigned) {
        assertThat(displayRequested.getDisplayId()).isNotEqualTo(displayAssigned.getDisplayId());
        mTask.mUserId = userId;
        TaskDisplayArea requestedTaskDisplayArea = mBuiltin.getDefaultTaskDisplayAreaOnDisplay(
                displayRequested.getDisplayId()).getTaskDisplayArea();
        TaskDisplayArea assignedTaskDisplayArea = mBuiltin.getDefaultTaskDisplayAreaOnDisplay(
                displayAssigned.getDisplayId()).getTaskDisplayArea();
        mCurrentParams.mPreferredTaskDisplayArea = requestedTaskDisplayArea;
        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, mActivityOptions, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams))
                .isEqualTo(LaunchParamsController.LaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea).isEqualTo(assignedTaskDisplayArea);
    }

    private void assertDisplayIsAssigned(
            @UserIdInt int userId, TaskDisplayArea expectedDisplayArea) {
        if (mTask != null) {
            mTask.mUserId = userId;
        }
        mCurrentParams.mPreferredTaskDisplayArea = null;
        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, mActivityOptions, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams))
                .isEqualTo(LaunchParamsController.LaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea).isEqualTo(expectedDisplayArea);
    }

    private void assertNoDisplayIsAssigned(@UserIdInt int userId) {
        mTask.mUserId = userId;
        mCurrentParams.mPreferredTaskDisplayArea = null;
        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, mActivityOptions, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams))
                .isEqualTo(LaunchParamsController.LaunchParamsModifier.RESULT_SKIP);
        assertThat(mOutParams.mPreferredTaskDisplayArea).isNull();
    }

    private ActivityRecord buildActivityRecord(String packageName, String className) {
        ActivityInfo info = new ActivityInfo();
        info.packageName = packageName;
        info.name = className;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        Intent intent = new Intent();
        intent.setClassName(packageName, className);

        return new ActivityRecord.Builder(mActivityTaskManagerService)
                .setIntent(intent)
                .setActivityInfo(info)
                .setConfiguration(new Configuration())
                .build();
    }

    private ActivityRecord buildActivityRecord(ComponentName componentName) {
        return buildActivityRecord(componentName.getPackageName(), componentName.getClassName());
    }

    @Test
    public void testNoPolicySet() {
        final int randomUserId = 1000;
        // policy not set set, so do not apply any enforcement.
        assertDisplayIsAllowed(randomUserId, mDisplay0ForDriver);
        assertDisplayIsAllowed(randomUserId, mDisplay10ForPassenger);

        assertDisplayIsAllowed(UserHandle.USER_SYSTEM, mDisplay0ForDriver);
        assertDisplayIsAllowed(UserHandle.USER_SYSTEM, mDisplay10ForPassenger);

        assertDisplayIsAllowed(ActivityManager.getCurrentUser(), mDisplay0ForDriver);
        assertDisplayIsAllowed(ActivityManager.getCurrentUser(), mDisplay10ForPassenger);
    }

    private void assertAllDisplaysAllowedForUser(int userId) {
        assertDisplayIsAllowed(userId, mDisplay0ForDriver);
        assertDisplayIsAllowed(userId, mDisplay10ForPassenger);
        assertDisplayIsAllowed(userId, mDisplay11ForPassenger);
    }

    @Test
    public void testAllowAllForDriverDuringBoot() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});

        // USER_SYSTEM should be allowed always
        assertAllDisplaysAllowedForUser(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testAllowAllForDriverAfterUserSwitching() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});

        final int driver1 = 10;
        mModifier.handleCurrentUserSwitching(driver1);
        assertAllDisplaysAllowedForUser(driver1);
        assertAllDisplaysAllowedForUser(UserHandle.USER_SYSTEM);

        final int driver2 = 10;
        mModifier.handleCurrentUserSwitching(driver2);
        assertAllDisplaysAllowedForUser(driver2);
        assertAllDisplaysAllowedForUser(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testPassengerAllowed() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(passengerUserId,
                new int[]{mDisplay10ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerChange() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        int passengerUserId1 = 100;
        mUpdatable.setDisplayAllowListForUser(passengerUserId1,
                new int[]{mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId1, mDisplay11ForPassenger);

        int passengerUserId2 = 101;
        mUpdatable.setDisplayAllowListForUser(passengerUserId2,
                new int[]{mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId2, mDisplay11ForPassenger);
        // 11 not allowed, so reassigned to the 1st passenger display
        assertDisplayIsReassigned(passengerUserId1, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerNotAllowed() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(
                passengerUserId, new int[]{mDisplay10ForPassenger.getDisplayId()});

        assertDisplayIsReassigned(passengerUserId, mDisplay0ForDriver, mDisplay10ForPassenger);
        assertDisplayIsReassigned(passengerUserId, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerNotAllowedAfterUserSwitch() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(
                passengerUserId, new int[]{mDisplay11ForPassenger.getDisplayId()});
        assertDisplayIsAllowed(passengerUserId, mDisplay11ForPassenger);

        mModifier.handleCurrentUserSwitching(2);

        assertDisplayIsReassigned(passengerUserId, mDisplay0ForDriver, mDisplay10ForPassenger);
        assertDisplayIsReassigned(passengerUserId, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerNotAllowedAfterAssigningCurrentUser() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(
                passengerUserId, new int[]{mDisplay11ForPassenger.getDisplayId()});
        assertDisplayIsAllowed(passengerUserId, mDisplay11ForPassenger);

        mUpdatable.setDisplayAllowListForUser(
                UserHandle.USER_SYSTEM, new int[]{mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsReassigned(passengerUserId, mDisplay0ForDriver, mDisplay10ForPassenger);
        assertDisplayIsReassigned(passengerUserId, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerDisplayRemoved() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(passengerUserId,
                new int[]{mDisplay10ForPassenger.getDisplayId(),
                        mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId, mDisplay10ForPassenger);
        assertDisplayIsAllowed(passengerUserId, mDisplay11ForPassenger);

        mUpdatable.getDisplayListener().onDisplayRemoved(mDisplay11ForPassenger.getDisplayId());

        assertDisplayIsAllowed(passengerUserId, mDisplay10ForPassenger);
        assertDisplayIsReassigned(passengerUserId, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testPassengerDisplayRemovedFromSetPassengerDisplays() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(passengerUserId,
                new int[]{mDisplay10ForPassenger.getDisplayId(),
                        mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId, mDisplay10ForPassenger);
        assertDisplayIsAllowed(passengerUserId, mDisplay11ForPassenger);

        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId, mDisplay10ForPassenger);
        assertDisplayIsReassigned(passengerUserId, mDisplay11ForPassenger, mDisplay10ForPassenger);
    }

    @Test
    public void testIgnorePrivateDisplay() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int passengerUserId = 100;
        mUpdatable.setDisplayAllowListForUser(passengerUserId,
                new int[]{mDisplay10ForPassenger.getDisplayId(),
                        mDisplay10ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(passengerUserId, mDisplay1Private);
    }

    @Test
    public void testDriverPassengerSwap() {
        mUpdatable.setPassengerDisplays(new int[]{mDisplay10ForPassenger.getDisplayId(),
                mDisplay11ForPassenger.getDisplayId()});

        final int wasDriver = 10;
        final int wasPassenger = 11;
        mModifier.handleCurrentUserSwitching(wasDriver);
        mUpdatable.setDisplayAllowListForUser(wasPassenger,
                new int[]{mDisplay10ForPassenger.getDisplayId(),
                        mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(wasDriver, mDisplay0ForDriver);
        assertDisplayIsAllowed(wasDriver, mDisplay10ForPassenger);
        assertDisplayIsAllowed(wasDriver, mDisplay11ForPassenger);
        assertDisplayIsReassigned(wasPassenger, mDisplay0ForDriver, mDisplay10ForPassenger);
        assertDisplayIsAllowed(wasPassenger, mDisplay10ForPassenger);
        assertDisplayIsAllowed(wasPassenger, mDisplay11ForPassenger);

        final int driver = wasPassenger;
        final int passenger = wasDriver;
        mModifier.handleCurrentUserSwitching(driver);
        mUpdatable.setDisplayAllowListForUser(passenger,
                new int[]{mDisplay10ForPassenger.getDisplayId(),
                        mDisplay11ForPassenger.getDisplayId()});

        assertDisplayIsAllowed(driver, mDisplay0ForDriver);
        assertDisplayIsAllowed(driver, mDisplay10ForPassenger);
        assertDisplayIsAllowed(driver, mDisplay11ForPassenger);
        assertDisplayIsReassigned(passenger, mDisplay0ForDriver, mDisplay10ForPassenger);
        assertDisplayIsAllowed(passenger, mDisplay10ForPassenger);
        assertDisplayIsAllowed(passenger, mDisplay11ForPassenger);
    }

    @Test
    public void testEmbeddedActivityCanLaunchOnVirtualDisplay() {
        // The launch request comes from the Activity in Virtual display.
        DisplayContent dc = mRootWindowContainer.getDisplayContentOrCreate(VIRTUAL_DISPLAY_ID_2);
        when(mActivityRecordSource.getDisplayContent()).thenReturn(dc);
        mActivityRecordActivity = buildActivityRecord("testPackage", "testActivity");
        mActivityRecordActivity.info.flags = ActivityInfo.FLAG_ALLOW_EMBEDDED;

        // ATM will launch the Activity in the source display, since no display is assigned.
        assertNoDisplayIsAssigned(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testNonEmbeddedActivityWithExistingTaskDoesNotChangeDisplay() {
        // The launch request comes from the Activity in Virtual display.
        DisplayContent dc = mRootWindowContainer.getDisplayContentOrCreate(VIRTUAL_DISPLAY_ID_2);
        when(mActivityRecordSource.getDisplayContent()).thenReturn(dc);
        mActivityRecordActivity = buildActivityRecord("testPackage", "testActivity");
        // No setting of FLAG_ALLOW_EMBEDDED and mTask.

        assertNoDisplayIsAssigned(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testNonEmbeddedActivityWithNewTaskMoviesToDefaultDisplay() {
        // The launch request comes from the Activity in Virtual display.
        DisplayContent dc = mRootWindowContainer.getDisplayContentOrCreate(VIRTUAL_DISPLAY_ID_2);
        when(mActivityRecordSource.getDisplayContent()).thenReturn(dc);
        mActivityRecordActivity = buildActivityRecord("testPackage", "testActivity");
        // No setting of FLAG_ALLOW_EMBEDDED.
        mTask = null;  // ATM will assign 'null' to 'task' argument for new task case.

        assertDisplayIsAssigned(UserHandle.USER_SYSTEM, mDisplayArea0ForDriver);
    }

    @Test
    public void testSourceDisplayFromProcessDisplayIfAvailable() {
        int userId = 108;
        String processName = "processName";
        int processUid = 11;
        when(mActivityRecordActivity.getProcessName())
                .thenReturn(processName);
        when(mActivityRecordActivity.getUid())
                .thenReturn(processUid);
        mUpdatable.setPassengerDisplays(new int[]{mDisplay11ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});
        mUpdatable.setDisplayAllowListForUser(userId,
                new int[]{mDisplay10ForPassenger.getDisplayId()});
        WindowProcessController controller = mock(WindowProcessController.class);
        when(mActivityTaskManagerService.getProcessController(processName, processUid))
                .thenReturn(controller);
        when(controller.getTopActivityDisplayArea())
                .thenReturn(mDisplayArea10ForPassenger);
        mCurrentParams.mPreferredTaskDisplayArea = null;
        mTask.mUserId = userId;

        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, /* options= */ null, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams)).isEqualTo(TaskLaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea)
                .isEqualTo(mDisplayArea10ForPassenger);
    }

    @Test
    public void testSourceDisplayFromLaunchingDisplayIfAvailable() {
        int userId = 108;
        int launchedFromPid = 1324;
        int launchedFromUid = 325;
        when(mActivityRecordActivity.getLaunchedFromPid())
                .thenReturn(launchedFromPid);
        when(mActivityRecordActivity.getLaunchedFromUid())
                .thenReturn(launchedFromUid);
        mUpdatable.setPassengerDisplays(new int[]{mDisplay11ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});
        mUpdatable.setDisplayAllowListForUser(userId,
                new int[]{mDisplay10ForPassenger.getDisplayId()});
        WindowProcessController controller = mock(WindowProcessController.class);
        when(mActivityTaskManagerService.getProcessController(launchedFromPid, launchedFromUid))
                .thenReturn(controller);
        when(controller.getTopActivityDisplayArea())
                .thenReturn(mDisplayArea10ForPassenger);
        mCurrentParams.mPreferredTaskDisplayArea = null;
        mTask.mUserId = userId;

        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, /* options= */ null, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams)).isEqualTo(TaskLaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea)
                .isEqualTo(mDisplayArea10ForPassenger);
    }

    @Test
    public void testSourceDisplayFromCallingDisplayIfAvailable() {
        int userId = 108;
        ActivityStarter.Request request = fakeRequest();
        mUpdatable.setPassengerDisplays(new int[]{mDisplay11ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});
        mUpdatable.setDisplayAllowListForUser(userId,
                new int[]{mDisplay10ForPassenger.getDisplayId()});
        WindowProcessController controller = mock(WindowProcessController.class);
        when(mActivityTaskManagerService.getProcessController(request.realCallingPid,
                request.realCallingUid))
                .thenReturn(controller);
        when(controller.getTopActivityDisplayArea())
                .thenReturn(mDisplayArea10ForPassenger);
        mCurrentParams.mPreferredTaskDisplayArea = null;
        mTask.mUserId = userId;

        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, /* options= */ null, request, /* phase= */ 0, mCurrentParams,
                mOutParams)).isEqualTo(TaskLaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea)
                .isEqualTo(mDisplayArea10ForPassenger);
    }

    @Test
    public void testSourceDisplayIgnoredIfNotInAllowList() {
        ActivityStarter.Request request = fakeRequest();
        mUpdatable.setPassengerDisplays(new int[]{mDisplay11ForPassenger.getDisplayId(),
                mDisplay10ForPassenger.getDisplayId()});
        WindowProcessController controller = mock(WindowProcessController.class);
        when(mActivityTaskManagerService.getProcessController(anyString(), anyInt()))
                .thenReturn(controller);
        when(mActivityTaskManagerService.getProcessController(anyInt(), anyInt()))
                .thenReturn(controller);
        when(controller.getTopActivityDisplayArea())
                .thenReturn(mDisplayArea10ForPassenger);
        mCurrentParams.mPreferredTaskDisplayArea = null;
        mTask.mUserId = 10;

        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, /* options= */ null, request, /* phase= */ 0, mCurrentParams,
                mOutParams))
                .isEqualTo(TaskLaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea)
                .isEqualTo(mDisplayArea11ForPassenger);
    }

    @Test
    public void testSetPersistentActivityThrowsExceptionForInvalidDisplayId() {
        ComponentName mapActivity = new ComponentName("testMapPkg", "mapActivity");
        int invalidDisplayId = 999990;

        assertThrows(IllegalArgumentException.class,
                () -> mUpdatable.setPersistentActivity(mapActivity,
                        invalidDisplayId, DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void testSetPersistentActivityThrowsExceptionForInvalidFeatureId() {
        ComponentName mapActivity = new ComponentName("testMapPkg", "mapActivity");
        int invalidFeatureId = 999990;

        assertThrows(IllegalArgumentException.class,
                () -> mUpdatable.setPersistentActivity(mapActivity,
                        DEFAULT_DISPLAY, invalidFeatureId));
    }

    @Test
    public void testPersistentActivityOverridesTDA() {
        ComponentName mapActivityName = new ComponentName("testMapPkg", "mapActivity");
        mActivityRecordActivity = buildActivityRecord(mapActivityName);

        int ret = mUpdatable.setPersistentActivity(
                mapActivityName, DEFAULT_DISPLAY, FEATURE_MAP_ID);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

        assertDisplayIsAssigned(UserHandle.USER_SYSTEM, mMapTaskDisplayArea);
    }

    @Test
    public void testRemovePersistentActivity() {
        ComponentName mapActivityName = new ComponentName("testMapPkg", "mapActivity");
        mActivityRecordActivity = buildActivityRecord(mapActivityName);

        int ret = mUpdatable.setPersistentActivity(
                mapActivityName, DEFAULT_DISPLAY, FEATURE_MAP_ID);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);
        // Removes the existing persistent Activity assignment.
        ret = mUpdatable.setPersistentActivity(mapActivityName, DEFAULT_DISPLAY,
                DisplayAreaOrganizer.FEATURE_UNDEFINED);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

        assertNoDisplayIsAssigned(UserHandle.USER_SYSTEM);
    }

    @Test
    public void testRemoveUnknownPersistentActivityThrowsException() {
        ComponentName mapActivity = new ComponentName("testMapPkg", "mapActivity");

        assertThrows(ServiceSpecificException.class,
                () -> mUpdatable.setPersistentActivity(mapActivity, DEFAULT_DISPLAY,
                        DisplayAreaOrganizer.FEATURE_UNDEFINED));
    }

    @Test
    public void testWindowingMode_forPassengerActivityOptions_updatedInParams() {
        int userId = 108;
        int launchedFromPid = 1324;
        int launchedFromUid = 325;
        when(mActivityOptions.getLaunchWindowingMode()).thenReturn(6);
        when(mActivityRecordActivity.getLaunchedFromPid()).thenReturn(launchedFromPid);
        when(mActivityRecordActivity.getLaunchedFromUid()).thenReturn(launchedFromUid);
        mUpdatable.setPassengerDisplays(new int[]{PASSENGER_DISPLAY_ID_11,
                PASSENGER_DISPLAY_ID_10});
        mUpdatable.setDisplayAllowListForUser(userId, new int[]{PASSENGER_DISPLAY_ID_10});
        WindowProcessController controller = mock(WindowProcessController.class);
        when(mActivityTaskManagerService.getProcessController(launchedFromPid, launchedFromUid))
                .thenReturn(controller);
        when(controller.getTopActivityDisplayArea()).thenReturn(mDisplayArea10ForPassenger);
        mCurrentParams.mPreferredTaskDisplayArea = null;
        mTask.mUserId = 108;

        assertThat(mModifier.onCalculate(mTask, mWindowLayout, mActivityRecordActivity,
                mActivityRecordSource, mActivityOptions, /* request= */ null, /* phase= */ 0,
                mCurrentParams, mOutParams)).isEqualTo(TaskLaunchParamsModifier.RESULT_DONE);
        assertThat(mOutParams.mPreferredTaskDisplayArea).isEqualTo(mDisplayArea10ForPassenger);
        assertThat(mOutParams.mWindowingMode).isEqualTo(6);
    }

    @Test
    public void testVisibleUserStartsButNoOccupantZoneIsAssigned() {
        // We have a Passenger display, but a visible user is started, but not an occupant zone is
        // assigned yet. This happens for Home when a visible user is started.
        mUpdatable.setPassengerDisplays(
                new int[]{PASSENGER_DISPLAY_ID_10, PASSENGER_DISPLAY_ID_11});
        int visibleUserId = 100;
        when(mUserManagerInternal.getUserAssignedToDisplay(PASSENGER_DISPLAY_ID_11))
                .thenReturn(visibleUserId);
        when(mActivityOptions.getLaunchDisplayId()).thenReturn(PASSENGER_DISPLAY_ID_11);

        // CarLaunchParamsModifier admires the launchDisplayId, not assigning a display.
        assertNoDisplayIsAssigned(visibleUserId);
    }

    @Test
    public void testVisibleUserUsesMainDisplayAsFallback_whenLaunchedOnRandomDisplay() {
        mUpdatable.setPassengerDisplays(
                new int[]{PASSENGER_DISPLAY_ID_10, PASSENGER_DISPLAY_ID_11});
        int visibleUserId = 100;
        when(mUserManagerInternal.getUserAssignedToDisplay(PASSENGER_DISPLAY_ID_11))
                .thenReturn(visibleUserId);
        when(mUserManagerInternal.getMainDisplayAssignedToUser(visibleUserId))
                .thenReturn(PASSENGER_DISPLAY_ID_11);
        // Try to start Activity on the non-main display.
        when(mActivityOptions.getLaunchDisplayId()).thenReturn(RANDOM_DISPLAY_ID_99);

        // For the visible user, fallbacks to the main display.
        assertDisplayIsAssigned(visibleUserId, mDisplayArea11ForPassenger);
    }

    private static ActivityStarter.Request fakeRequest() {
        ActivityStarter.Request request = new ActivityStarter.Request();
        request.realCallingPid = 1324;
        request.realCallingUid = 235;
        return request;
    }
}
