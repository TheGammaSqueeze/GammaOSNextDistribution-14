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

package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.media.Session2Command;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link android.media.Session2Command}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class Session2CommandTest {
    private static final int TEST_COMMAND_CODE = 10000;
    private static final int TEST_RESULT_CODE = 0;
    private static final String TEST_CUSTOM_ACTION = "testAction";
    private Bundle mTestCustomExtras;
    private Bundle mTestResultData;

    @Before
    public void setUp() {
        mTestCustomExtras = new Bundle();
        mTestResultData = new Bundle();
    }

    @Test
    public void testConstructorWithCommandCodeCustom() {
        assertThrows(IllegalArgumentException.class,
                () -> new Session2Command(Session2Command.COMMAND_CODE_CUSTOM));
    }

    @Test
    public void testConstructorWithNullAction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Session2Command(null, null));
    }

    @Test
    public void testGetCommandCode() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertThat(commandWithCode.getCommandCode()).isEqualTo(TEST_COMMAND_CODE);

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertThat(commandWithAction.getCommandCode())
                .isEqualTo(Session2Command.COMMAND_CODE_CUSTOM);
    }

    @Test
    public void testGetCustomAction() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertThat(commandWithCode.getCustomAction()).isNull();

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertThat(commandWithAction.getCustomAction()).isEqualTo(TEST_CUSTOM_ACTION);
    }

    @Test
    public void testGetCustomExtras() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertThat(commandWithCode.getCustomExtras()).isNull();

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertThat(commandWithAction.getCustomExtras()).isEqualTo(mTestCustomExtras);
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        Session2Command command = new Session2Command(TEST_COMMAND_CODE);
        assertThat(command.describeContents()).isEqualTo(expected);
    }

    @Test
    public void testWriteToParcel() {
        Session2Command command = new Session2Command(TEST_CUSTOM_ACTION, null);
        Parcel dest = Parcel.obtain();
        command.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        assertThat(dest.readInt()).isEqualTo(command.getCommandCode());
        assertThat(dest.readString()).isEqualTo(command.getCustomAction());
        assertThat(dest.readBundle()).isEqualTo(command.getCustomExtras());
    }

    @Test
    public void testEquals() {
        Session2Command commandWithCode1 = new Session2Command(TEST_COMMAND_CODE);
        Session2Command commandWithCode2 = new Session2Command(TEST_COMMAND_CODE);
        assertThat(commandWithCode1.equals(commandWithCode2)).isTrue();

        Session2Command commandWithAction1 = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        Session2Command commandWithAction2 = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertThat(commandWithAction1.equals(commandWithAction2)).isTrue();
    }

    @Test
    public void testHashCode() {
        Session2Command commandWithCode1 = new Session2Command(TEST_COMMAND_CODE);
        Session2Command commandWithCode2 = new Session2Command(TEST_COMMAND_CODE);
        assertThat(commandWithCode2.hashCode()).isEqualTo(commandWithCode1.hashCode());
    }

    @Test
    public void testGetResultCodeAndData() {
        Session2Command.Result result = new Session2Command.Result(TEST_RESULT_CODE,
                mTestResultData);
        assertThat(result.getResultCode()).isEqualTo(TEST_RESULT_CODE);
        assertThat(result.getResultData()).isEqualTo(mTestResultData);
    }
}
