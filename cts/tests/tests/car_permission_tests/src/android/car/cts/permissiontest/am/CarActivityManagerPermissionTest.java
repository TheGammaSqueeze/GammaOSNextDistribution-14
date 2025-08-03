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

package android.car.cts.permissiontest.am;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarSystemUIProxy;
import android.car.app.CarTaskViewControllerCallback;
import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.view.Display;
import android.window.DisplayAreaOrganizer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class contains security permission tests for {@link CarActivityManager}.
 */
@RunWith(AndroidJUnit4.class)
public class CarActivityManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private CarActivityManager mCarActivityManager;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mCarActivityManager = (CarActivityManager) mCar.getCarManager(Car.CAR_ACTIVITY_SERVICE);
    }

    @Test
    public void testSetPersistentActivity_requiresPermission() {
        ComponentName activity = new ComponentName("testPkg", "testActivity");
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.setPersistentActivity(activity, Display.DEFAULT_DISPLAY,
                        DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
    }

    @Test
    public void testSetPersistentActivities_requiresPermission() {
        ComponentName activity = new ComponentName("testPkg", "testActivity");
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.setPersistentActivitiesOnRootTask(List.of(activity),
                        new Binder()));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
    }

    @Test
    public void testRegisterTaskMonitor_requiresPermission() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.registerTaskMonitor());

        assertThat(e).hasMessageThat().contains(android.Manifest.permission.MANAGE_ACTIVITY_TASKS);
    }

    @Test
    public void testGetVisibleTasks_requiresPermission() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.getVisibleTasks());

        assertThat(e).hasMessageThat().contains(android.Manifest.permission.MANAGE_ACTIVITY_TASKS);
    }

    @Test
    public void testCreateTaskMirroringToken_requiresPermission() {
        int taskId = 9999;
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.createTaskMirroringToken(taskId));

        assertThat(e).hasMessageThat().contains(android.Manifest.permission.MANAGE_ACTIVITY_TASKS);
    }

    @Test
    public void testCreateDisplayMirroringToken_requiresPermission() {
        int taskId = 9999;
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.createDisplayMirroringToken(taskId));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_MIRROR_DISPLAY);
    }

    @Test
    public void testGetMirroredSurface_requiresPermission() {
        IBinder token = new Binder();
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.getMirroredSurface(token));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_ACCESS_MIRRORRED_SURFACE);
    }

    @Test
    public void testMoveRootTaskToDisplay_requiresPermission() {
        int taskId = 9999;
        int displayId = 999;
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.moveRootTaskToDisplay(taskId, displayId));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH);
    }

    @Test
    public void registerCarSystemUIProxy_requiresPermission() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.registerCarSystemUIProxy(mock(CarSystemUIProxy.class)));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_MANAGE_CAR_SYSTEM_UI)
    public void getCarTaskViewController_requiresPermission_INTERACT_ACROSS_USERS() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.getCarTaskViewController(mock(Activity.class),
                        mock(Executor.class), mock(CarTaskViewControllerCallback.class)));

        assertThat(e).hasMessageThat().contains(android.Manifest.permission.INTERACT_ACROSS_USERS);
    }

    @Test
    @EnsureHasPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void getCarTaskViewController_requiresPermission_PERMISSION_MANAGE_CAR_SYSTEM_UI() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> mCarActivityManager.getCarTaskViewController(mock(Activity.class),
                        mock(Executor.class), mock(CarTaskViewControllerCallback.class)));

        assertThat(e).hasMessageThat().contains(Car.PERMISSION_MANAGE_CAR_SYSTEM_UI);
    }
}
