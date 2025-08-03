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

package android.platform.scenario.multiuser;

import android.platform.helpers.MultiUserHelper;
import androidx.test.InstrumentationRegistry;

public final class MultiUserConstants {

    // Shouldn't be too optimistic for the MU latency. The time to wait for system idle will be
    // 2 x (worst-case number) to make sure regression not affecting test itself.
    public static final long WAIT_FOR_IDLE_TIME_MS = 40000;
    // Default initial user does not change throughout the test process
    public static final int DEFAULT_INITIAL_USER = MultiUserHelper.getInstance().getInitialUser();
    public static final String INCLUDE_CREATION_TIME_KEY = "include_creation_time";
    public static final boolean INCLUDE_CREATION_TIME =
        Boolean.valueOf(
            InstrumentationRegistry.getArguments().getString(INCLUDE_CREATION_TIME_KEY, "false"));
    public static final String GUEST_NAME = "Guest";
    public static final String SECONDARY_USER_NAME = "SecondaryUser";

    private MultiUserConstants() {
        throw new RuntimeException("Class cannot be instantiated");
    }
}
