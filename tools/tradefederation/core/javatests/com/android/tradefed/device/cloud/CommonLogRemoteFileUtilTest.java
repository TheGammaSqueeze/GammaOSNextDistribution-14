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

package com.android.tradefed.device.cloud;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.google.common.net.HostAndPort;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link CommonLogRemoteFileUtil}. */
@RunWith(JUnit4.class)
public class CommonLogRemoteFileUtilTest {
    @Mock private IRunUtil mRunUtil;
    @Mock private ITestLogger mTestLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFetchCommonFilesWithLogsEntries() throws ConfigurationException {
        GceAvdInfo info = new GceAvdInfo("mock-instance", HostAndPort.fromString("192.0.2.2"));
        List<GceAvdInfo.LogFileEntry> logs = info.getLogs();
        logs.add(new GceAvdInfo.LogFileEntry("/test/text", LogDataType.TEXT, "log1.txt"));
        logs.add(new GceAvdInfo.LogFileEntry("/test/log2.txt", LogDataType.UNKNOWN, ""));
        logs.add(new GceAvdInfo.LogFileEntry("/tombstones", LogDataType.DIR, "tombstones-zip"));
        TestDeviceOptions options = new TestDeviceOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("use-oxygen", "false");
        setter.setOptionValue(TestDeviceOptions.INSTANCE_TYPE_OPTION, "CUTTLEFISH");
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        CommonLogRemoteFileUtil.fetchCommonFiles(mTestLogger, info, options, mRunUtil);

        Mockito.verify(mTestLogger)
                .testLog(Mockito.eq("log1.txt"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        Mockito.verify(mTestLogger)
                .testLog(
                        Mockito.startsWith("log2"), Mockito.eq(LogDataType.UNKNOWN), Mockito.any());
        Mockito.verify(mTestLogger, Mockito.times(2))
                .testLog(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testFetchCommonFilesWithoutLogsEntries() throws ConfigurationException {
        GceAvdInfo info = new GceAvdInfo("mock-instance", HostAndPort.fromString("192.0.2.2"));
        TestDeviceOptions options = new TestDeviceOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("use-oxygen", "false");
        setter.setOptionValue(TestDeviceOptions.INSTANCE_TYPE_OPTION, "CUTTLEFISH");
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        CommonLogRemoteFileUtil.fetchCommonFiles(mTestLogger, info, options, mRunUtil);

        Mockito.verify(mTestLogger)
                .testLog(
                        Mockito.eq("cuttlefish_launcher.log"),
                        Mockito.eq(LogDataType.CUTTLEFISH_LOG),
                        Mockito.any());
    }
}
