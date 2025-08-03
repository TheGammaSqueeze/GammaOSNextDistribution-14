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

package android.app.sdksandbox.hosttestutils;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

public class SecondaryUserUtils {

    private static final long NUMBER_OF_POLLS = 5 * 60;
    private static final long POLL_INTERVAL_IN_MILLIS = 1000;

    private final BaseHostJUnit4Test mTest;

    private int mOriginalUserId = -1;
    private int mSecondaryUserId = -1;

    public SecondaryUserUtils(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public boolean isMultiUserSupported() throws Exception {
        return mTest.getDevice().isMultiUserSupported();
    }

    public int createAndStartSecondaryUser() throws Exception {
        if (mSecondaryUserId != -1) {
            throw new IllegalStateException("Cannot create secondary user, it already exists");
        }
        mOriginalUserId = mTest.getDevice().getCurrentUser();
        String name = "SdkSandboxStorageHost_User" + System.currentTimeMillis();
        mSecondaryUserId = mTest.getDevice().createUser(name);
        // Note we can't install apps on a locked user, so we wait
        mTest.getDevice().startUser(mSecondaryUserId, /*waitFlag=*/ true);
        return mSecondaryUserId;
    }

    public void removeSecondaryUserIfNecessary() throws Exception {
        removeSecondaryUserIfNecessary(/*waitForUserDataDeletion=*/ false);
    }

    public void removeSecondaryUserIfNecessary(boolean waitForUserDataDeletion) throws Exception {
        if (mSecondaryUserId == -1) {
            return;
        }

        final int userBeingRemoved = mSecondaryUserId;
        // Set to -1 so that we can create new users later, even when removal goes wrong
        mSecondaryUserId = -1;

        if (mOriginalUserId != -1 && userBeingRemoved != -1) {
            // Can't remove the 2nd user without switching out of it
            assertThat(mTest.getDevice().switchUser(mOriginalUserId)).isTrue();
            mTest.getDevice().removeUser(userBeingRemoved);
            if (waitForUserDataDeletion) {
                waitForUserDataDeletion(userBeingRemoved);
            }
        }
    }

    public void switchToSecondaryUser() throws Exception {
        mTest.getDevice().switchUser(mSecondaryUserId);
        for (int i = 0; i < NUMBER_OF_POLLS; ++i) {
            if (mTest.getDevice().getCurrentUser() == mSecondaryUserId) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_IN_MILLIS);
        }
        fail("Could not switch to user " + mSecondaryUserId);
    }

    private void waitForUserDataDeletion(int userId) throws Exception {
        final String deSdkSandboxDataRootPath = "/data/misc_de/" + userId + "/sdksandbox";
        for (int i = 0; i < NUMBER_OF_POLLS; ++i) {
            if (!mTest.getDevice().isDirectory(deSdkSandboxDataRootPath)) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_IN_MILLIS);
        }
        fail("User data was not deleted for UserId " + userId);
    }
}
