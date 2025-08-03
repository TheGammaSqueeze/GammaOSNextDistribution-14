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

package android.car.cts.builtin;


import static com.google.common.truth.Truth.assertThat;

import android.car.cts.builtin.widget.CheckLockIsSecureCommand;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class LockPatternHelperHostTest extends CarBuiltinApiHostCtsBase {

    @Test
    public void testIsSecureApi() throws Exception {
        // setup
        CheckLockIsSecureCommand checkLockCmd = new CheckLockIsSecureCommand(getDevice());
        String userId = String.valueOf(getDevice().getCurrentUser());

        // execution and assertion
        checkLockCmd.executeWith(userId);
        // as the current user does not have any credentials, expect to be false
        assertThat(checkLockCmd.isSecure()).isFalse();
    }
}
