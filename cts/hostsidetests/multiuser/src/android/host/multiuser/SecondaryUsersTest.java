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
 * limitations under the License
 */
package android.host.multiuser;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertWithMessage;

import android.host.multiuser.BaseMultiUserTest.SupportsMultiUserRule;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Run: atest SecondaryUsersTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class SecondaryUsersTest extends BaseMultiUserTest {

    // Extra time to give the system to switch into secondary user after boot complete.
    private static final long SECONDARY_USER_BOOT_COMPLETE_TIMEOUT_MS = 100_000;

    private static final long POLL_INTERVAL_MS = 1_000;
    private static final long WAIT_FOR_DEVICE_READY_INTERVAL_MS = 10_000;
    private static final long WAIT_FOR_BOOT_COMPLETE_INTERVAL_MINUTES = 2;

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    @CddTest(requirement="9.5/A-1-2")
    @Test
    public void testSwitchToSecondaryUserBeforeBootComplete() throws Exception {
        assumeIsAutomotive();

        CLog.d("Rebooting");
        getDevice().nonBlockingReboot();
        CLog.d("Waiting " + WAIT_FOR_BOOT_COMPLETE_INTERVAL_MINUTES + " minutes for boot complete");
        getDevice().waitForBootComplete(TimeUnit.MINUTES.toMillis(2));
        CLog.d("Boot completed; waiting until current user is a secondary user");

        int currentUser = -10000; // UserHandle.USER_NULL;
        boolean isUserSecondary = false;
        long ti = System.currentTimeMillis();

        // TODO(b/208518721): Verify if current user is secondary when the UI is ready for user
        // interaction. A possibility is to check if the CarLauncher is started in the
        // Activity Stack, but this becomes tricky in OEM implementation, where CarLauncher is
        // replaced with another launcher. Launcher can usually identify by
        // android.intent.category.HOME (type=home) and priority = -1000. But there is no clear way
        // to determine this via adb.
        while (!isUserSecondary
                && System.currentTimeMillis() - ti < SECONDARY_USER_BOOT_COMPLETE_TIMEOUT_MS) {
            try {
                currentUser = getDevice().getCurrentUser();
                isUserSecondary = getDevice().isUserSecondary(currentUser);
                CLog.d("Current user: %d isSecondary: %b", currentUser, isUserSecondary);
                if (isUserSecondary) {
                    CLog.d("Saul Goodman!");
                    break;
                }
                CLog.v("Sleeping for %d ms as user %d is not a secondary user yet",
                        POLL_INTERVAL_MS, currentUser);
                RunUtil.getDefault().sleep(POLL_INTERVAL_MS);
            } catch (Exception e) {
                CLog.d("Device not available yet (%s); sleeping for %d ms", e,
                        WAIT_FOR_DEVICE_READY_INTERVAL_MS);
                RunUtil.getDefault().sleep(WAIT_FOR_DEVICE_READY_INTERVAL_MS);
            }
        }
        assertWithMessage("Current user (%s) is a secondary user after boot", currentUser)
                .that(isUserSecondary).isTrue();
    }
}
