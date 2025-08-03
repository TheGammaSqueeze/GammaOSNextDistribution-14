/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.multiuser.cts;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.multiuser.cts.PermissionHelper.adoptShellPermissionIdentity;
import static android.multiuser.cts.TestingUtils.sContext;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.hardware.display.DisplayManager;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;

/**
 * Base class for tests that exercise APIs related to user visibility (as defined by
 * {@link UserManager#isUserVisible()}).
 */
@RunWith(BedsteadJUnit4.class)
// NOTE: must be public because of JUnit rules
public abstract class UserVisibilityTestCase {

    private static final String TAG = UserVisibilityTestCase.class.getSimpleName();

    protected static final String CMD_DUMP_MEDIATOR = "dumpsys user --visibility-mediator";

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final LogShellCommandRule mLogShellCommandRule = new LogShellCommandRule();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    protected UserManager mUserManager;

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();

        mLogShellCommandRule.addCommand(CMD_DUMP_MEDIATOR);
    }

    /**
     * Creates and starts a new user visible in background on secondary display, and run a test on
     * it.
     *
     * @param test to be run
     */
    protected void runTestOnSecondaryDisplay(VisibleBackgroundUserOnSecondaryDisplayTester test) {
        int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
        runTestOnDisplay(displayId, test);
    }

    /**
     * Creates and starts a new user visible in background in the
     * {@link android.view.Display#DEFAULT_DISPLAY default display}, and run a test on it.
     *
     * @param test to be run
     */
    protected void runTestOnDefaultDisplay(VisibleBackgroundUserOnDefaultDisplayTester test) {
        runTestOnDisplay(DEFAULT_DISPLAY, (user, displayId, instance) -> test.run(user, instance));
    }

    /**
     * Creates and starts a new user visible in background in the given display, and run a test on
     * it.
     *
     * @param test to be run
     */
    protected void runTestOnDisplay(int displayId,
            VisibleBackgroundUserOnSecondaryDisplayTester test) {
        Log.d(TAG, "Creating bg user");
        try (UserReference user = TestApis.users().createUser().name("childless_user").create()) {
            startVisibleBackgroundUser(user, displayId);
            try {
                TestApp testApp = sDeviceState.testApps().any();
                try (TestAppInstance instance = testApp.install(user)) {
                    test.run(user, displayId, instance);
                }
            } finally {
                user.stop();
            } // startBackgroundUserOnSecondaryDisplay(user)
        } // new user
    }

    /**
     * Creats and starts a new user (with a profile) in background on a secondary display and run a
     *  test on it.
     *
     * @param test to be run
     */
    protected void runTestOnSecondaryDisplay(
            VisibleBackgroundUserAndProfileOnSecondaryDisplayTester tester) {
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference user = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "user: id=" + user.id());
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + user.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(user)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
                startVisibleBackgroundUser(user, displayId);
                try {
                    // Make sure profile is stopped, as it could have been automatically started
                    // with parent user
                    Log.d(TAG, "Stopping profile " + profile.id()); profile.stop();
                    TestApp testApp = sDeviceState.testApps().any();
                    try (TestAppInstance instance = testApp.install(profile)) {
                        tester.run(user, profile, displayId, instance);
                    } // test instance
                } finally {
                    user.stop();
                } // startBackgroundUserOnSecondaryDisplay(user)
            } // new profile
        } // new user
    }

    interface VisibleBackgroundUserOnSecondaryDisplayTester {
        void run(UserReference user, int displayId, TestAppInstance instance);
    }

    interface VisibleBackgroundUserAndProfileOnSecondaryDisplayTester {
        void run(UserReference user, UserReference profile, int displayId,
                TestAppInstance instance);
    }

    interface VisibleBackgroundUserOnDefaultDisplayTester {
        void run(UserReference user, TestAppInstance instance);
    }

    // TODO(b/240736142): methods below are a temporary workaround until proper annotation or test
    // API are available

    protected int getSecondaryDisplayIdForStartingVisibleBackgroundUser() {
        int[] displayIds = null;
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation,
                INTERACT_ACROSS_USERS)) {
            displayIds = sContext.getSystemService(ActivityManager.class)
                    .getDisplayIdsForStartingVisibleBackgroundUsers();
        }
        Log.d(TAG, "getSecondaryDisplayIdForStartingVisibleBackgroundUser(): displays returned by "
                + "AM:" + Arrays.toString(displayIds));
        if (displayIds != null) {
            for (int displayId : displayIds) {
                if (displayId != DEFAULT_DISPLAY) {
                    Log.d(TAG, "getDisplayForBackgroundUserOnDisplay(): returning first non-DEFAULT"
                            + " display from the list (" + displayId + ")");
                    return displayId;
                }
            }
        }

        DisplayManager displayManager = sContext.getSystemService(DisplayManager.class);
        Display[] allDisplays = displayManager.getDisplays();
        throw new IllegalStateException("Device supports background users visible on displays, but "
                + "doesn't have any display available to start a user. Current displays: "
                + Arrays.toString(allDisplays));
    }

    protected void startVisibleBackgroundUser(UserReference user, int displayId) {
        int userId = user.id();
        boolean started = tryToStartVisibleBackgroundUser(userId, displayId);
        assertWithMessage("started visible background user %s on display %s", userId, displayId)
                .that(started).isTrue();
        Poll.forValue("User running unlocked", () -> user.isRunning() && user.isUnlocked())
                .toBeEqualTo(true)
                .errorOnFail()
                .timeout(Duration.ofMinutes(1))
                .await();
    }

    protected boolean tryToStartVisibleBackgroundUser(int userId, int displayId) {
        Log.d(TAG, "tryToStartVisibleBackgroundUser(): user=" + userId + ", display=" + displayId);
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation,
                INTERACT_ACROSS_USERS)) {
            boolean started = sContext.getSystemService(ActivityManager.class)
                    .startUserInBackgroundVisibleOnDisplay(userId, displayId);
            Log.d(TAG, "Returning started=" + started);
            return started;
        }
    }
}
