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
package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;
import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nullable;

/** Target preparer for running tests in a user that is started in the visible in the background. */
@OptionClass(alias = "visible-background-user-preparer")
public class VisibleBackgroundUserPreparer extends BaseTargetPreparer {

    @VisibleForTesting public static final int INVALID_DISPLAY = -1; // same as android.view.Display
    @VisibleForTesting public static final int DEFAULT_DISPLAY = 0; // same as android.view.Display

    @Option(
            name = "reuse-test-user",
            description =
                    "Whether or not to reuse already created tradefed test user, or remove them "
                            + " and re-create them between module runs.")
    private boolean mReuseTestUser;

    @Option(name = "display-id", description = "Which display to start the user visible on")
    private int mDisplayId = INVALID_DISPLAY;

    private Integer mUserId;
    private boolean mUserAlreadyVisible;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        if (!device.isVisibleBackgroundUsersSupported()) {
            throw new TargetSetupError("feature not supported", device.getDeviceDescriptor());
        }
        CLog.i("setUp(): mReuseTestUser=%b, mDisplayId=%d", mReuseTestUser, mDisplayId);

        mUserId = UserHelper.createUser(device, mReuseTestUser);

        startUserVisibleOnBackground(testInfo, device, mUserId);

        device.waitForDeviceAvailable();
        device.postBootSetup();
    }

    public void setDisplayId(int displayId) {
        if (displayId == INVALID_DISPLAY) {
            throw new IllegalArgumentException(
                    "Cannot set it as INVALID_DISPLAY (" + INVALID_DISPLAY + ")");
        }
        mDisplayId = displayId;
    }

    @VisibleForTesting
    public @Nullable Integer getDisplayId() {
        return mDisplayId;
    }

    private void startUserVisibleOnBackground(
            TestInformation testInfo, ITestDevice device, int userId)
            throws TargetSetupError, DeviceNotAvailableException {
        int displayId = mDisplayId;
        if (displayId == INVALID_DISPLAY) {
            // If display is not explicitly set (by option / setter), get the first available one
            Set<Integer> displays = device.listDisplayIdsForStartingVisibleBackgroundUsers();
            CLog.d("Displays: %s", displays);
            if (displays.isEmpty()) {
                throw new TargetSetupError(
                        String.format("No display available to start to user '%d'", userId),
                        device.getDeviceDescriptor());
            }
            Iterator<Integer> iterator = displays.iterator();
            displayId = iterator.next();
            if (displayId == DEFAULT_DISPLAY
                    && device.isVisibleBackgroundUsersOnDefaultDisplaySupported()) {
                // Ignore default display - it's a special case where the display id should have
                // been passed directly
                CLog.d(
                        "Ignoring DEFAULT_DISPLAY because device supports background users on"
                                + " default display");
                if (!iterator.hasNext()) {
                    throw new TargetSetupError(
                            String.format(
                                    "Only DEFAULT_DISPLAY available to start to user '%d'", userId),
                            device.getDeviceDescriptor());
                }
                displayId = iterator.next();
            }
        }

        mUserAlreadyVisible = device.isUserVisibleOnDisplay(userId, displayId);
        if (mUserAlreadyVisible) {
            CLog.d(
                    "startUserVisibleOnBackground(): user %d already visible on display %d",
                    userId, displayId);
        } else {
            CLog.d(
                    "startUserVisibleOnBackground(): starting user %d visible on display %d",
                    userId, displayId);

            if (!device.startVisibleBackgroundUser(userId, displayId, /* waitFlag= */ true)) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to start to user '%s' on display %d", mUserId, displayId),
                        device.getDeviceDescriptor());
            }
        }

        CLog.i("Setting test property %s=%d", RUN_TESTS_AS_USER_KEY, mUserId);
        testInfo.properties().put(RUN_TESTS_AS_USER_KEY, Integer.toString(mUserId));
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (mUserId == null) {
            CLog.d("Skipping teardown because no user was created or reused");
            return;
        }
        // Clean property at teardown
        testInfo.properties().remove(RUN_TESTS_AS_USER_KEY);
        if (e instanceof DeviceNotAvailableException) {
            CLog.d("Skipping teardown due to dnae: %s", e.getMessage());
            return;
        }
        ITestDevice device = testInfo.getDevice();

        stopTestUser(device);

        if (!mReuseTestUser) {
            device.removeUser(mUserId);
        }
    }

    private void stopTestUser(ITestDevice device) throws DeviceNotAvailableException {
        if (mUserAlreadyVisible) {
            CLog.d("stopTestUser(): user %d was already visible on start", mUserId);
            return;
        }
        CLog.d("stopTestUser(): stopping user %d ", mUserId);
        if (!device.stopUser(mUserId, /* waitFlag= */ true, /* forceFlag= */ true)) {
            CLog.e("Failed to stop user '%d'", mUserId);
        }
    }
}
