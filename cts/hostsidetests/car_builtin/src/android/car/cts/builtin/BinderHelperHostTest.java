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

import static android.car.cts.builtin.os.GetInitialUserInfoCommand.OK_STATUS_RETURN_HEADER;

import static com.google.common.truth.Truth.assertThat;

import android.car.cts.builtin.os.DumpDrivingStateServiceCommand;
import android.car.cts.builtin.os.GetInitialUserInfoCommand;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class BinderHelperHostTest extends CarBuiltinApiHostCtsBase {

    // When a car shell command (such as, "cmd car_service get-do-activities") is called, it
    // triggers both BinderHelper.onTransactForCmd and
    // BinderHelper.ShellCommandListener.onShellCommand calls.
    @Test
    public void testOnTransactForCmd() throws Exception {
        // setup
        GetInitialUserInfoCommand infoCmd = new GetInitialUserInfoCommand(getDevice());

        // execution and assertion
        infoCmd.executeWith();
        assertThat(infoCmd.returnStartsWith(OK_STATUS_RETURN_HEADER)).isTrue();
    }

    // When the "dumpsys car_service --services CarDrivingStateService" shell command is called,
    // it triggers the BinderHelper.dumpRemoteCallbackList() builtin API.
    @Test
    public void testDumpRemoteCallbackList() throws Exception {
        // setup
        DumpDrivingStateServiceCommand dumpCmd = new DumpDrivingStateServiceCommand(getDevice());

        // execution and assertion
        dumpCmd.executeWith();
        assertThat(dumpCmd.hasRemoteCallbackListDump()).isTrue();
    }
}
