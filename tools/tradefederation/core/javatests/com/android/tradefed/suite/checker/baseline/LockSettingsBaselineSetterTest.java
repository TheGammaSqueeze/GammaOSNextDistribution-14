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

package com.android.tradefed.suite.checker.baseline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LockSettingsBaselineSetter}. */
@RunWith(JUnit4.class)
public final class LockSettingsBaselineSetterTest {

    private ITestDevice mMockDevice;
    private LockSettingsBaselineSetter mSetter;
    private JSONObject mJsonObject;
    private static final String SETTING_NAME = "test";
    private static final String SETTING_STRING = "{\"clear_pwds\": [\"0000\", \"1234\"]}";
    private static final String GET_LOCK_SCREEN_COMMAND = "locksettings get-disabled";
    private static final String LOCK_SCREEN_OFF_COMMAND = "locksettings set-disabled true";
    private static final String CLEAR_PWD_COMMAND = "locksettings clear --old %s";

    @Before
    public void setup() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        mJsonObject = new JSONObject(SETTING_STRING);
        mSetter = new LockSettingsBaselineSetter(mJsonObject, SETTING_NAME);
    }

    @Test
    public void lockSettingsDeviceBaselineSetter_noPasswordField_throwsException()
            throws Exception {
        mJsonObject.remove("clear_pwds");
        assertThrows(
                JSONException.class,
                () -> new LockSettingsBaselineSetter(mJsonObject, SETTING_NAME));
    }

    /** Test that the setter skips removing passwords when lock-screen is turned off. */
    @Test
    public void setBaseline_lockScreenOff_skipRemovingPasswords() throws Exception {
        when(mMockDevice.executeShellCommand(GET_LOCK_SCREEN_COMMAND)).thenReturn("true");
        assertTrue(mSetter.setBaseline(mMockDevice));
        verify(mMockDevice).executeShellCommand(GET_LOCK_SCREEN_COMMAND);
        verify(mMockDevice, never()).executeShellCommand(LOCK_SCREEN_OFF_COMMAND);
        verify(mMockDevice, never()).executeShellCommand(String.format(CLEAR_PWD_COMMAND, "0000"));
        verify(mMockDevice, never()).executeShellCommand(String.format(CLEAR_PWD_COMMAND, "1234"));
    }

    /** Test that the setter removes passwords successfully. */
    @Test
    public void setBaseline_setSucceeds_passwordsRemoved() throws Exception {
        when(mMockDevice.executeShellCommand(GET_LOCK_SCREEN_COMMAND)).thenReturn("false", "true");
        assertTrue(mSetter.setBaseline(mMockDevice));
        verify(mMockDevice, times(2)).executeShellCommand(GET_LOCK_SCREEN_COMMAND);
        verify(mMockDevice).executeShellCommand(LOCK_SCREEN_OFF_COMMAND);
        verify(mMockDevice).executeShellCommand(String.format(CLEAR_PWD_COMMAND, "0000"));
        verify(mMockDevice).executeShellCommand(String.format(CLEAR_PWD_COMMAND, "1234"));
    }

    /** Test that the setter returns false when the baseline is failed to set. */
    @Test
    public void setBaseline_setFails_returnFalse() throws Exception {
        when(mMockDevice.executeShellCommand(GET_LOCK_SCREEN_COMMAND)).thenReturn("false");
        assertFalse(mSetter.setBaseline(mMockDevice));
    }
}
