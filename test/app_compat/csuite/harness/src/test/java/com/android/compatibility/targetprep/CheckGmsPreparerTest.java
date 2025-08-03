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
package com.android.compatibility.targetprep;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.ddmlib.Log;
import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;

@RunWith(JUnit4.class)
public final class CheckGmsPreparerTest {
    private CheckGmsPreparer mPreparer;
    private LogCaptor mLogCaptor;

    @Before
    public void setUp() throws Exception {
        mPreparer = new CheckGmsPreparer();
        new OptionSetter(mPreparer).setOptionValue(CheckGmsPreparer.OPTION_ENABLE, "true");

        mLogCaptor = new LogCaptor();
        Log.addLogger(mLogCaptor);
    }

    @After
    public void tearDown() {
        Log.removeLogger(mLogCaptor);
    }

    @Test
    public void setUp_checkDisabledAndGmsAbsent_doesNotReboot() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsent();
        disablePreparer(mPreparer);

        mPreparer.setUp(createTestInfo(device));

        Mockito.verify(device, Mockito.never()).reboot();
        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .doesNotContain("GMS");
    }

    @Test
    public void tearDown_checkDisabledAndGmsAbsent_doesNotLog() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsent();
        disablePreparer(mPreparer);

        mPreparer.tearDown(createTestInfo(device), null);

        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .doesNotContain("GMS");
    }

    @Test
    public void tearDown_setUpThrows_doesNotCheck() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsent();
        TestInformation testInfo = createTestInfo(device);
        assertThrows(TargetSetupError.class, () -> mPreparer.setUp(testInfo));
        mLogCaptor.reset();
        Mockito.reset(device);
        Mockito.when(device.executeShellV2Command(Mockito.any()))
                .thenReturn(createFailedCommandResult());

        mPreparer.tearDown(testInfo, null);

        Mockito.verify(device, Mockito.never()).executeShellV2Command(Mockito.any());
    }

    @Test
    public void tearDown_setUpRecoveredGms_checksGms() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsentAndRecoverable();
        TestInformation testInfo = createTestInfo(device);
        mPreparer.setUp(testInfo);
        mLogCaptor.reset();
        Mockito.reset(device);
        Mockito.when(device.executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND))
                .thenReturn(createSuccessfulCommandResult());

        mPreparer.tearDown(testInfo, null);

        Mockito.verify(device, Mockito.atLeast(1))
                .executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND);
    }

    @Test
    public void tearDown_setUpFoundGms_checksGms() throws Exception {
        ITestDevice device = createDeviceWithGmsPresent();
        TestInformation testInfo = createTestInfo(device);
        mPreparer.setUp(testInfo);
        Mockito.reset(device);
        mLogCaptor.reset();
        Mockito.when(device.executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND))
                .thenReturn(createSuccessfulCommandResult());

        mPreparer.tearDown(testInfo, null);

        Mockito.verify(device, Mockito.atLeast(1))
                .executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND);
    }

    @Test
    public void setUp_gmsPresent_doesNotReboot() throws Exception {
        ITestDevice device = createDeviceWithGmsPresent();

        mPreparer.setUp(createTestInfo(device));

        Mockito.verify(device, Mockito.never()).reboot();
        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .doesNotContain("GMS");
    }

    @Test
    public void setUp_gmsProcessRecoveredAfterReboot_doesNotThrow() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsentAndRecoverable();

        mPreparer.setUp(createTestInfo(device));

        Mockito.verify(device, Mockito.times(1)).reboot();
        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .contains("GMS");
    }

    @Test
    public void setUp_gmsProcessNotRecoveredAfterReboot_throwsException() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsent();

        assertThrows(TargetSetupError.class, () -> mPreparer.setUp(createTestInfo(device)));
        Mockito.verify(device, Mockito.times(1)).reboot();
        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .contains("GMS");
    }

    @Test
    public void tearDown_gmsProcessPresent_doesNotLog() throws Exception {
        ITestDevice device = createDeviceWithGmsPresent();

        mPreparer.tearDown(createTestInfo(device), null);

        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .doesNotContain("GMS");
    }

    @Test
    public void tearDown_gmsProcessAbsent_logsError() throws Exception {
        ITestDevice device = createDeviceWithGmsAbsent();

        mPreparer.tearDown(createTestInfo(device), null);

        assertThat(mLogCaptor.getLogItems())
                .comparingElementsUsing(createContainsErrorLogCorrespondence())
                .contains("GMS");
    }

    private static void disablePreparer(CheckGmsPreparer preparer) throws Exception {
        new OptionSetter(preparer).setOptionValue(CheckGmsPreparer.OPTION_ENABLE, "false");
    }

    private static Correspondence<LogItem, String> createContainsErrorLogCorrespondence() {
        return Correspondence.from(
                (LogItem actual, String expected) -> {
                    return actual.getLogLevel() == LogLevel.ERROR
                            && actual.getMessage().contains(expected);
                },
                "has an error log that contains");
    }

    private static ITestDevice createDeviceWithGmsAbsentAndRecoverable() throws Exception {
        ITestDevice device = Mockito.mock(ITestDevice.class);
        Mockito.doReturn(createFailedCommandResult())
                .doReturn(createSuccessfulCommandResult())
                .when(device)
                .executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND);
        return device;
    }

    private static ITestDevice createDeviceWithGmsPresent() throws Exception {
        ITestDevice device = Mockito.mock(ITestDevice.class);
        Mockito.when(device.executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND))
                .thenReturn(createSuccessfulCommandResult());
        return device;
    }

    private static ITestDevice createDeviceWithGmsAbsent() throws Exception {
        ITestDevice device = Mockito.mock(ITestDevice.class);
        Mockito.when(device.executeShellV2Command(CheckGmsPreparer.CHECK_GMS_COMMAND))
                .thenReturn(createFailedCommandResult());
        return device;
    }

    private static final class LogCaptor implements ILogOutput {
        private ArrayList<LogItem> mLogItems = new ArrayList<>();

        void reset() {
            mLogItems.clear();
        }

        ArrayList<LogItem> getLogItems() {
            return mLogItems;
        }

        @Override
        public void printLog(LogLevel logLevel, String tag, String message) {
            mLogItems.add(new LogItem(logLevel, tag, message));
        }

        @Override
        public void printAndPromptLog(LogLevel logLevel, String tag, String message) {
            printLog(logLevel, tag, message);
        }
    }

    private static final class LogItem {
        private LogLevel mLogLevel;
        private String mMessage;

        LogLevel getLogLevel() {
            return mLogLevel;
        }

        String getMessage() {
            return mMessage;
        }

        LogItem(LogLevel logLevel, @SuppressWarnings("unused") String tag, String message) {
            mLogLevel = logLevel;
            mMessage = message;
        }
    }

    private static TestInformation createTestInfo(ITestDevice device) {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", device);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        return commandResult;
    }
}
