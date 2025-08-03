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

import android.media.Session2Command;
import android.media.Session2CommandGroup;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Tests {@link android.media.Session2CommandGroup}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class Session2CommandGroupTest {
    private static final int TEST_COMMAND_CODE_1 = 10000;
    private static final int TEST_COMMAND_CODE_2 = 10001;
    private static final int TEST_COMMAND_CODE_3 = 10002;

    @Test
    public void testHasCommand() {
        Session2Command testCommand = new Session2Command(TEST_COMMAND_CODE_1);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(testCommand);
        Session2CommandGroup commandGroup = builder.build();
        assertThat(commandGroup.hasCommand(TEST_COMMAND_CODE_1)).isTrue();
        assertThat(commandGroup.hasCommand(testCommand)).isTrue();
        assertThat(commandGroup.hasCommand(TEST_COMMAND_CODE_2)).isFalse();
    }

    @Test
    public void testGetCommands() {
        Session2Command command1 = new Session2Command(TEST_COMMAND_CODE_1);
        Session2Command command2 = new Session2Command(TEST_COMMAND_CODE_2);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(command1);
        Session2CommandGroup commandGroup = builder.build();

        Set<Session2Command> commands = commandGroup.getCommands();
        assertThat(commands.contains(command1)).isTrue();
        assertThat(commands.contains(command2)).isFalse();
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        Session2Command command = new Session2Command(TEST_COMMAND_CODE_1);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(command);
        Session2CommandGroup commandGroup = builder.build();
        assertThat(commandGroup.describeContents()).isEqualTo(expected);
    }

    @Test
    public void testWriteToParcel() {
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(new Session2Command(TEST_COMMAND_CODE_1))
                .addCommand(new Session2Command(TEST_COMMAND_CODE_2));
        Session2CommandGroup commandGroup = builder.build();
        Parcel dest = Parcel.obtain();
        commandGroup.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        Session2CommandGroup commandGroupFromParcel =
                Session2CommandGroup.CREATOR.createFromParcel(dest);
        assertThat(commandGroupFromParcel.getCommands()).isEqualTo(commandGroup.getCommands());
        dest.recycle();
    }

    @Test
    public void testBuilder() {
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(new Session2Command(TEST_COMMAND_CODE_1));
        Session2CommandGroup commandGroup = builder.build();
        Session2CommandGroup.Builder newBuilder = new Session2CommandGroup.Builder(commandGroup);
        Session2CommandGroup newCommandGroup = newBuilder.build();
        assertThat(newCommandGroup.getCommands()).isEqualTo(commandGroup.getCommands());
    }

    @Test
    public void testAddAndRemoveCommand() {
        Session2Command testCommand1 = new Session2Command(TEST_COMMAND_CODE_1);
        Session2Command testCommand2 = new Session2Command(TEST_COMMAND_CODE_2);
        Session2Command testCommand3 = new Session2Command(TEST_COMMAND_CODE_3);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(testCommand1)
                .addCommand(testCommand2)
                .addCommand(testCommand3);
        builder.removeCommand(testCommand1)
                .removeCommand(testCommand2);
        Session2CommandGroup commandGroup = builder.build();
        assertThat(commandGroup.hasCommand(testCommand1)).isFalse();
        assertThat(commandGroup.hasCommand(testCommand2)).isFalse();
        assertThat(commandGroup.hasCommand(testCommand3)).isTrue();
    }
}
