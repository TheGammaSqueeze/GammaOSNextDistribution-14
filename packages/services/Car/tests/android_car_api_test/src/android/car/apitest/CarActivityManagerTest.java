/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.view.Display;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;

@MediumTest
public final class CarActivityManagerTest extends CarApiTestBase {
    private static final String TAG = CarActivityManagerTest.class.getSimpleName();

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED
    private static final int FEATURE_UNDEFINED = -1;

    private CarActivityManager mCarActivityManager;

    private final ComponentName mTestActivity = new ComponentName("test.pkg", "test.activity");

    @Before
    public void setUp() throws Exception {
        mCarActivityManager = (CarActivityManager) getCar().getCarManager(Car.CAR_ACTIVITY_SERVICE);
        assertThat(mCarActivityManager).isNotNull();
    }

    @Test
    public void testSetPersistentActivity() {
        // Set
        int retSet = mCarActivityManager.setPersistentActivity(
                mTestActivity, Display.DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER);
        assertThat(retSet).isEqualTo(CarActivityManager.RESULT_SUCCESS);

        // Remove
        int retRemove = mCarActivityManager.setPersistentActivity(
                mTestActivity, Display.DEFAULT_DISPLAY, FEATURE_UNDEFINED);
        assertThat(retRemove).isEqualTo(CarActivityManager.RESULT_SUCCESS);
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidDisplayId() {
        int invalidDisplayId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mTestActivity, invalidDisplayId, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidFeatureId() {
        int unknownFeatureId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mTestActivity, Display.DEFAULT_DISPLAY, unknownFeatureId));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForUnknownActivity() {
        // Tries to remove the Activity without registering it.
        assertThrows(ActivityNotFoundException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mTestActivity, Display.DEFAULT_DISPLAY, FEATURE_UNDEFINED));
    }
}
