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

package com.android.bedstead.nene.logcat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class LogcatTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @Test
    @Interactive
    public void throwsException_exceptionContainsActualServerSideInformation() {
        // TODO: Ask the user to enable Binder.LOG_RUNTIME_EXCEPTION
        try {
            sDevicePolicyManager.getPasswordExpirationTimeout(
                    new ComponentName("A.B", "C"));

            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            SystemServerException systemServerException =
                    TestApis.logcat().findSystemServerException(expected);

            assertThat(systemServerException).hasMessageThat().contains("does not exist");
            assertThat(systemServerException).hasCauseThat().isInstanceOf(SecurityException.class);
        }
    }

    @Test
    public void dump_filtersCorrectly() {
        TestApis.logcat().clear();

        Log.e("testlog", "TEST123");

        assertThat(TestApis.logcat().dump(l -> l.contains("TEST123"))).isNotEmpty();
    }

}
