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

package com.android.bedstead.nene.broadcasts;

import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.Versions;

/**
 * Test Apis related to broadcasts.
 */
public final class Broadcasts {

    public static final Broadcasts sInstance = new Broadcasts();

    private Broadcasts() {

    }

    /**
     * Wait for all broadcasts currently scheduled to be delivered.
     *
     * <p>This should be used very infrequently and only when you know why it is needed. A
     * descriptive reason should be included.
     */
    @Experimental
    public void waitForBroadcastBarrier(String reason) {
        if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            ShellCommand.builder("am")
                    .addOperand("wait-for-broadcast-barrier")
                    .validate(s -> s.contains("Loopers drained!") || s.contains("barrier passed"))
                    .executeOrThrowNeneException("Error waiting for broadcast barrier");
        } else {
            ShellCommand.builder("am")
                    .addOperand("wait-for-broadcast-idle")
                    .validate(s -> s.contains("are idle!"))
                    .executeOrThrowNeneException("Error waiting for broadcast idle");
        }
    }

    /**
     * Wait for all broadcasts with the specified intent action that are currently scheduled
     * to be dispatched.
     *
     * <p>This should be used very infrequently and only when you know why it is needed. A
     * descriptive reason should be included.
     */
    @Experimental
    public void waitForBroadcastDispatch(String broadcastAction, String reason) {
        if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            ShellCommand.builder("am")
                    .addOperand("wait-for-broadcast-dispatch")
                    .addOption("-a", broadcastAction)
                    .executeOrThrowNeneException("Error waiting for broadcast barrier");
        } else {
            waitForBroadcastBarrier(reason);
        }
    }
}
