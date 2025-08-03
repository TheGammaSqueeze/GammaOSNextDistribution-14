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

import android.car.cts.builtin.pm.ComponentEnabledStateCommand;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class PackageManagerHelperHostTest extends CarBuiltinApiHostCtsBase {

    private static final String PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME =
            "android.car.cts.builtin.apps.pm";

    // The car shell command "cmd car_service control-component-enabled-state" triggered by
    // ComponentEnabledStateCommand invokes
    //   1. PackageManagerHelper.setApplicationEnabledSettingForUser
    //   2. PackageManagerHelper.getApplicationEnabledSettingForUser
    // builtin APIs.
    @Test
    public void testApplicationEnabledSetting() throws Exception {
        // setup
        ComponentEnabledStateCommand shellCmd = new ComponentEnabledStateCommand(getDevice());

        shellCmd.executeWith(ComponentEnabledStateCommand.COMMAND_ACTION_GET,
                PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME);
        assertThat(shellCmd.getCurrentState())
                .isEqualTo(ComponentEnabledStateCommand.COMPONENT_ENABLED_STATE_DEFAULT);

        shellCmd.executeWith(ComponentEnabledStateCommand.COMMAND_ACTION_ENABLE,
                PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME);
        assertThat(shellCmd.getNewState())
                .isEqualTo(ComponentEnabledStateCommand.COMPONENT_ENABLED_STATE_ENABLED);

        shellCmd.executeWith(ComponentEnabledStateCommand.COMMAND_ACTION_DISABLE_UNTIL_USED,
                PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME);
        assertThat(shellCmd.getNewState()).isEqualTo(ComponentEnabledStateCommand
                .COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);

        shellCmd.executeWith(ComponentEnabledStateCommand.COMMAND_ACTION_DEFAULT,
                PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME);
        assertThat(shellCmd.getNewState())
                .isEqualTo(ComponentEnabledStateCommand.COMPONENT_ENABLED_STATE_DEFAULT);

        shellCmd.executeWith(ComponentEnabledStateCommand.COMMAND_ACTION_GET,
                PACKAGE_MANAGER_HELPER_APP_PACKAGE_NAME);
        assertThat(shellCmd.getCurrentState())
                .isEqualTo(ComponentEnabledStateCommand.COMPONENT_ENABLED_STATE_DEFAULT);
    }
}
