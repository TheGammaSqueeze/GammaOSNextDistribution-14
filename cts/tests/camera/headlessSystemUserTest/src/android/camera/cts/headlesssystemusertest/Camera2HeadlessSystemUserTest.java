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

package android.camera.cts.headlesssystemusertest;

import static android.Manifest.permission.CAMERA_HEADLESS_SYSTEM_USER;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

import android.app.ActivityManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.cts.Camera2ParameterizedTestCase;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.CameraTestUtils.MockStateCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.internal.camera.flags.Flags;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Camera2HeadlessSystemUserTest extends Camera2ParameterizedTestCase {
    private static final String TAG = "Camera2HeadlessSystemUserTest";
    private static final int SWITCH_USER_WAIT_TIME = 5000;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private BlockingStateCallback mCameraListener;
    private ActivityManager mActivityManager;
    private UserManager mUserManager;
    private String[] mCameraIdsUnderTest;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        assertNotNull("Can't get activity manager", mActivityManager);
        mUserManager  = mContext.getSystemService(UserManager.class);
        assertNotNull("Can't get user manager", mUserManager);
        mCameraIdsUnderTest = getCameraIdsUnderTest();
        assertNotNull("Camera ids shouldn't be null", mCameraIdsUnderTest);
        mCameraListener = spy(new BlockingStateCallback());
    }

    @Override
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;
        super.tearDown();
    }

    /**
     * Verifies that System Headless User can open the camera.
     * This test is only valid for devices running in Headless
     * System User Mode.
     */
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_HSUM_PERMISSION)
    @Test
    public void testHeadlessSystemUser_OpenCamera() throws Exception {
        assumeTrue(mUserManager.isSystemUser());
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            CameraDevice camera = null;
            try {
                MockStateCallback mockListener = MockStateCallback.mock();
                mCameraListener = new BlockingStateCallback(mockListener);
                openCameraAsHeadlessSystemUser(mCameraIdsUnderTest[i]);
                camera = CameraTestUtils.verifyCameraStateOpened(mCameraIdsUnderTest[i],
                        mockListener);
            } finally {
                if (camera != null) {
                    camera.close();
                }
            }
        }
    }

    /**
     * Verifies that the System Headless User cannot open the camera
     * without the permission CAMERA_HEADLESS_SYSTEM_USER.
     * This test is only valid for devices running in Headless
     * System User Mode.
     */
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_HSUM_PERMISSION)
    @Test
    public void testHeadlessSystemUser_InvalidAccess() throws Exception {
        assumeTrue(mUserManager.isSystemUser());
        assumeFalse("Skipping test for system camera.", mAdoptShellPerm);
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                MockStateCallback mockListener = MockStateCallback.mock();
                mCameraListener = new BlockingStateCallback(mockListener);
                mCameraManager.openCamera(mCameraIdsUnderTest[i], mCameraListener, mHandler);
                fail("Open camera as headless system user without permission should have thrown"
                        + "security exception" + mCameraIdsUnderTest[i]);
            } catch (SecurityException e) {
                Log.i(TAG, "Got the SecurityException on missing permission as expected");
            }
        }
    }

    /**
     * Verifies that the System Headless User continues to access the camera
     * even after foreground user has switched to a different user.
     * This test is only valid for devices running in Headless
     * System User Mode.
     */
    @EnsureHasSecondaryUser
    @RequireHeadlessSystemUserMode(reason = "tests headless user behaviour")
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_HSUM_PERMISSION)
    @Test
    public void testHeadlessSystemUser_SwitchForegroundUser() throws Exception {
        assumeTrue(mUserManager.isSystemUser());
        assumeTrue("Skipping test for devices which doesn't support multiple users.",
                UserManager.supportsMultipleUsers());
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            CameraDevice camera = null;
            try {
                MockStateCallback mockListener = MockStateCallback.mock();
                mCameraListener = new BlockingStateCallback(mockListener);
                openCameraAsHeadlessSystemUser(mCameraIdsUnderTest[i]);
                camera = CameraTestUtils.verifyCameraStateOpened(mCameraIdsUnderTest[i],
                        mockListener);
                switchUser(UserHandle.MIN_SECONDARY_USER_ID);
                verifyNoMoreInteractions(mockListener);
            } finally {
                if (camera != null) {
                    camera.close();
                }
            }
        }
    }

    private void openCameraAsHeadlessSystemUser(String cameraId) throws Exception {
        if (mAdoptShellPerm) {
            mCameraManager.openCamera(cameraId, mCameraListener, mHandler);
        } else {
            try (PermissionContext permissionContext =
                    TestApis.permissions().withPermission(CAMERA_HEADLESS_SYSTEM_USER)) {
                mCameraManager.openCamera(cameraId, mCameraListener, mHandler);
            }
        }
        mCameraListener.waitForState(BlockingStateCallback.STATE_OPENED,
                CameraTestUtils.CAMERA_OPEN_TIMEOUT_MS);
    }

    private static int getCurrentUserId() {
        String cmd = "am get-current-user";
        String retStr = SystemUtil.runShellCommand(cmd);
        Log.d(TAG, "getCurrentUserId: " + retStr);
        return Integer.parseInt(retStr.trim());
    }

    private static void waitUntilUserCurrent(int userId) throws Exception {
        PollingCheck.waitFor(SWITCH_USER_WAIT_TIME, () -> userId == getCurrentUserId());
    }

    private void switchUser(int userId) throws Exception {
        String cmd = "am switch-user " + userId;
        SystemUtil.runShellCommand(cmd);
        waitUntilUserCurrent(userId);
        if (mAdoptShellPerm) {
            assertEquals(userId, mActivityManager.getCurrentUser());
        } else {
            try (PermissionContext permissionContext =
                    TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
                assertEquals(userId, mActivityManager.getCurrentUser());
            }
        }
    }
}
