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
package com.android.compatibility.common.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Helper for user-related needs.
 */
public final class UserUtil {

    // TODO(b/271153404): static import from tradefed instead
    private static final String RUN_TESTS_AS_USER_KEY = "RUN_TESTS_AS_USER";

    private final TestInformation mTestInfo;

    public UserUtil(TestInformation testInfo) {
        mTestInfo = testInfo;
    }

    /**
    * Gets the id of the user running the test.
    *
    * <p>Typically, the user running the test is the {@link ITestDevice#getCurrentUser()
    * current user}, but it could be different if set by a target preparer.
    */
    public int getTestUserId() throws DeviceNotAvailableException {
        String propValue = mTestInfo.properties().get(RUN_TESTS_AS_USER_KEY);
        if (propValue != null && !propValue.isEmpty()) {
            CLog.d("getTestUserId(): returning '%s'", propValue);
            try {
                return Integer.parseInt(propValue);
            } catch (Exception e) {
                String errorMessage = "error parsing property " + RUN_TESTS_AS_USER_KEY
                        + " (value=" + propValue + ")";
                CLog.e("getTestUserId(): %s, e=%s", errorMessage, e);
                throw new IllegalStateException(errorMessage, e);
            }
        }
        ITestDevice device = mTestInfo.getDevice();
        int currentUserId = device.getCurrentUser();
        CLog.d("getTestUserId(): property %s not set, returning current user (%d)",
                RUN_TESTS_AS_USER_KEY, currentUserId);
        return currentUserId;
    }
}
