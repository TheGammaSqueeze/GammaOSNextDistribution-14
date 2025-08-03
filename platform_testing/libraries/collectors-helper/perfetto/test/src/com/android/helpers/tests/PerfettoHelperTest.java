/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.helpers.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.helpers.PerfettoHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Android Unit tests for {@link PerfettoHelper}.
 *
 * <p>To run: Have a valid text perfetto config under
 * /data/misc/perfetto-traces/trace_config.textproto. Use trace_config_detailed.textproto from
 * prebuilts/tools/linux-x86_64/perfetto/configs. TODO: b/119020380 to keep track of automating the
 * above step. atest CollectorsHelperTest:com.android.helpers.tests.PerfettoHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class PerfettoHelperTest {

    private static final String REMOVE_CMD = "rm %s";
    private static final String FILE_SIZE_IN_BYTES = "wc -c %s";
    private static final String DEFAULT_CFG =
            """
            buffers: {
            size_kb: 63488
            fill_policy: RING_BUFFER
            }

            data_sources {
            config {
                name: "linux.process_stats"
                target_buffer: 0
                # polled per-process memory counters and process/thread names.
                # If you don't want the polled counters, remove the "process_stats_config"
                # section, but keep the data source itself as it still provides on-demand
                # thread/process naming for ftrace data below.
                process_stats_config {
                scan_all_processes_on_start: true
                }
            }
            }
            """;

    private PerfettoHelper mPerfettoHelper;
    private boolean isPerfettoStartSuccess = false;

    @Before
    public void setUp() {
        mPerfettoHelper = new PerfettoHelper();
        mPerfettoHelper.setPerfettoConfigRootDir("/data/misc/perfetto-traces/");
        mPerfettoHelper.setPerfettoStartBgWait(true);
        isPerfettoStartSuccess = false;
    }

    @After
    public void teardown() throws IOException {
        if (isPerfettoStartSuccess) {
            mPerfettoHelper.setPerfettoConfigRootDir("/data/misc/perfetto-traces/");
            UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            mPerfettoHelper.stopCollecting(1000, "data/local/tmp/out.perfetto-trace");
            uiDevice.executeShellCommand(String.format(REMOVE_CMD,
                "/data/local/tmp/out.perfetto-trace"));
        }
    }

    /** Test perfetto choose config file over config */
    @Test(expected = IllegalStateException.class)
    public void testNullConfigAndConfigFileName() throws Exception {
        mPerfettoHelper.startCollecting();
    }

    /** Test perfetto choose config file over config */
    @Test
    public void testChoosesFileOverConfig() throws Exception {
        PerfettoHelper helper = spy(new PerfettoHelper());
        doReturn(true).when(helper).startCollectingFromConfigFile(anyString(), anyBoolean());
        helper.setIsTextProtoConfig(true);
        helper.setTextProtoConfig(DEFAULT_CFG);
        helper.setConfigFileName("trace_config.textproto");
        helper.startCollecting();
        verify(helper, times(0)).startCollectingFromConfigFile(anyString(), anyBoolean());
        verify(helper, times(1)).startCollectingFromConfig(anyString());
    }

    /**
     * Test perfetto collection returns false if the config file name is null.
     */
    @Test
    public void testNullConfigName() throws Exception {
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile(null, false));
    }

    /**
     * Test perfetto collection returns false if the config file name is empty.
     */
    @Test
    public void testEmptyConfigName() throws Exception {
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile("", false));
    }

    /** Test perfetto collection returns false if the config is empty. */
    @Test
    public void testEmptyConfig() throws Exception {
        assertFalse(mPerfettoHelper.startCollectingFromConfig(""));
    }

    @Test
    public void testNullRootDirName() throws Exception {
        mPerfettoHelper.setPerfettoConfigRootDir(null);
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", false));
    }

    /** Test perfetto collection returns false if the config file name is empty. */
    @Test
    public void testEmptyRootDirName() throws Exception {
        mPerfettoHelper.setPerfettoConfigRootDir("");
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", false));
    }

    /** Test perfetto collection returns false if the perfetto config file does not exist. */
    @Test
    public void testNoConfigFile() throws Exception {
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile("no_config.pb", false));
    }

    /**
     * Test perfetto collection returns true if the valid perfetto config file.
     */
    @Test
    public void testPerfettoStartSuccess() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
        isPerfettoStartSuccess = true;
    }

    /** Test perfetto collection returns true if the valid perfetto config file. */
    @Test
    public void testPerfettoStartConfigSuccess() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfig(DEFAULT_CFG));
        isPerfettoStartSuccess = true;
    }

    /** Test perfetto collection returns true if the background wait option is not used. */
    @Test
    public void testPerfettoStartSuccessNoBgWait() throws Exception {
        mPerfettoHelper.setPerfettoStartBgWait(false);
        assertTrue(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
        isPerfettoStartSuccess = true;
    }

    /**
     * Test if the path name is prefixed with /.
     */
    @Test
    public void testPerfettoValidOutputPath() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
        isPerfettoStartSuccess = true;
        assertTrue(mPerfettoHelper.stopCollecting(1000, "data/local/tmp/out.perfetto-trace"));
    }

    /** Test the invalid output path. */
    @Test
    public void testPerfettoInvalidOutputPath() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
        isPerfettoStartSuccess = true;
        // Don't have permission to create new folder under /data
        assertFalse(mPerfettoHelper.stopCollecting(1000, "/data/xxx/xyz/out.perfetto-trace"));
    }

    /**
     * Test perfetto collection returns true and output file size greater than zero if the valid
     * perfetto config file used.
     */
    @Test
    public void testPerfettoSuccess() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
        isPerfettoStartSuccess = true;
        assertTrue(mPerfettoHelper.stopCollecting(1000, "/data/local/tmp/out.perfetto-trace"));
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String[] fileStats =
                uiDevice.executeShellCommand(
                                String.format(
                                        FILE_SIZE_IN_BYTES, "/data/local/tmp/out.perfetto-trace"))
                        .split(" ");
        int fileSize = Integer.parseInt(fileStats[0].trim());
        assertTrue(fileSize > 0);
    }

    /**
     * Test perfetto collection returns true and output file size greater than zero if the valid
     * perfetto config file used.
     */
    @Test
    public void testPerfettoConfigSuccess() throws Exception {
        assertTrue(mPerfettoHelper.startCollectingFromConfig(DEFAULT_CFG));
        isPerfettoStartSuccess = true;
        assertTrue(mPerfettoHelper.stopCollecting(1000, "/data/local/tmp/out.perfetto-trace"));
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String[] fileStats =
                uiDevice.executeShellCommand(
                                String.format(
                                        FILE_SIZE_IN_BYTES, "/data/local/tmp/out.perfetto-trace"))
                        .split(" ");
        int fileSize = Integer.parseInt(fileStats[0].trim());
        assertTrue(fileSize > 0);
    }

    /**
     * Test perfetto collection returns false when referring to the config root directory
     * which does not contain perfetto config file.
     */
    @Test
    public void testPerfettoFailureInvalidConfigRoot() throws Exception {
        mPerfettoHelper.setPerfettoConfigRootDir("/data/misc/invalid-folder/");
        assertFalse(mPerfettoHelper.startCollectingFromConfigFile("trace_config.textproto", true));
    }

}
