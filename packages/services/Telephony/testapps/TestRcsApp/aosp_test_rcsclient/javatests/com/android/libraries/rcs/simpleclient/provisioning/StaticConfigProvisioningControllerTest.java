/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.provisioning;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.support.annotation.RequiresPermission;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StaticConfigProvisioningControllerTest {

    private static final byte[] CONFIG_DATA = "<xml></xml>".getBytes();

    private StaticConfigProvisioningController client;
    private Optional<byte[]> configXmlData = Optional.empty();
    private ProvisioningStateChangeCallback cb =
            configXml -> configXmlData = Optional.ofNullable(configXml);

    @Before
    public void setUp() {
        client = StaticConfigProvisioningController.createForSubscriptionId(/*subscriptionId=*/ 2);
        client.onConfigurationChange(cb);
    }

    @Test
    @RequiresPermission(value = "Manifest.permission.READ_PRIVILEGED_PHONE_STATE")
    public void whenGetConfigCalled_returnsCorrectXmlData() throws Exception {
        client.register();
        client.getProvisioningManager().getCallbackForTests().onConfigurationChanged(CONFIG_DATA);

        assertThat(client.isRcsVolteSingleRegistrationCapable()).isTrue();
        assertThat(client.getLatestConfiguration()).isEqualTo(CONFIG_DATA);
        assertThat(configXmlData.get()).isEqualTo(CONFIG_DATA);
        client.unRegister();
    }

    @Test
    @RequiresPermission(value = "Manifest.permission.READ_PRIVILEGED_PHONE_STATE")
    public void whenGetConfigCalled_throwsErrorWhenNoConfigPresent() throws Exception {
        client.register();
        client.triggerReconfiguration();

        assertThat(client.isRcsVolteSingleRegistrationCapable()).isTrue();
        assertThrows(IllegalStateException.class, () -> client.getLatestConfiguration());
        assertThat(configXmlData.isPresent()).isFalse();

        client.unRegister();
    }

    @Test
    @RequiresPermission(value = "Manifest.permission.READ_PRIVILEGED_PHONE_STATE")
    public void unRegister_failsWhenCalledWithoutRegister() {
        assertThrows(IllegalStateException.class, () -> client.unRegister());
    }
}
