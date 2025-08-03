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

package android.car.server.wm.cts;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.UserManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

// TODO(b/267678351): Mark this test with RequireCheckerRule
public class MultiUserMultiDisplayPolicyTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private UserManager mUserManager;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        assumeTrue(mUserManager.isVisibleBackgroundUsersSupported());
    }

    @Test
    public void testDriverCanLaunchActivityInDriverDisplay() {
        // STOPSHIP(b/266651512): Implement the test
        // Check if the system has the driver.
    }

    @Test
    public void testPassengerCanLaunchActivityInPassengerDisplay() {
        // STOPSHIP(b/266651512): Implement the test
        // Check if the system has the passenger.
    }

    @Test
    public void testDriverCanNotLaunchActivityInPassengerDisplay() {
        // STOPSHIP(b/266651512): Implement the test
        // Check if the system has the driver and the passenger.
    }

    @Test
    public void testPassengerCanNotLaunchActivityInDriverDisplay() {
        // STOPSHIP(b/266651512): Implement the test
        // Check if the system has the driver and the passenger.
    }

    @Test
    public void testPassengerCanNotLaunchActivityInOtherPassengerDisplay() {
        // STOPSHIP(b/266651512): Implement the test
        // Check if the system has at least 2 passenger.
    }
}
