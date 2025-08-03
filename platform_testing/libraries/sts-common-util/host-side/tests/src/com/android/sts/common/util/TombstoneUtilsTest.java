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

package com.android.sts.common.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.server.os.TombstoneProtos.*;
import com.android.sts.common.CommandUtil;
import com.android.sts.common.ProcessUtil;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Unit tests for {@link TombstoneUtils}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TombstoneUtilsTest extends BaseHostJUnit4Test {

    private static List<Tombstone> sTombstones;
    private static final List<Tombstone> EMPTY_TOMBSTONE_LIST = Collections.emptyList();

    @BeforeClass
    public static void setUp() throws IOException {
        String logcat = null;
        try (InputStream is =
                TombstoneUtilsTest.class.getClassLoader().getResourceAsStream("logcat.txt")) {
            logcat = new String(is.readAllBytes());
        }
        sTombstones = TombstoneParser.parseLogcat(logcat);
    }

    @Test
    @Ignore
    public void dumpCurrentTombstones() throws Exception {
        File tmp = new File("/tmp/tombstones");
        if (!tmp.exists()) {
            tmp.mkdir();
        }
        for (File f : tmp.listFiles()) {
            f.delete();
        }

        int index = 0;
        for (Tombstone tombstone : sTombstones) {
            try (FileOutputStream os =
                    new FileOutputStream(String.format("%s/tombstone-%03d.pb", tmp, index))) {
                tombstone.writeTo(os);
            }
            index++;
        }
    }

    @Test
    // If this test fails, update the res/tombstones files with the results of
    // `dumpCurrentTombstones`. Diff the files to ensure that the fields are updating as expected
    // and we're not losing data.
    public void testGetAllCrashes() throws Exception {
        List<Tombstone> expectedResults = new ArrayList<>();
        int index = 0;
        while (true) {
            String filename = String.format("tombstones/tombstone-%03d.pb", index);
            index++;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
                if (is == null) {
                    // couldn't find resource; stopping
                    break;
                }
                Tombstone tombstone = Tombstone.parseFrom(is.readAllBytes());
                expectedResults.add(tombstone);
            }
        }
        assertThat(expectedResults).isEqualTo(sTombstones);
    }

    @Test
    public void testValidCrash() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(
                                                Pattern.compile("synthetic_process_0"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testMissingName() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("")));
    }

    @Test
    public void testSIGABRT() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones, new TombstoneUtils.Config().setProcessPatterns("installd"));
    }

    @Test
    public void testFaultAddressBelowMin() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_1")));
    }

    @Test
    public void testIgnoreMinAddressCheck() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(false)
                                        .setProcessPatterns(
                                                Pattern.compile("synthetic_process_1"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testBadAbortMessage() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("generic")));
    }

    @Test
    public void testGoodAndBadCrashes() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(
                                                Pattern.compile("synthetic_process_0"),
                                                Pattern.compile("generic"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testNullFaultAddress() throws Exception {
        List<Tombstone> crashes = new ArrayList<>();
        crashes.add(
                Tombstone.newBuilder()
                        .addCommandLine("com.android.bluetooth")
                        .setSignalInfo(
                                Signal.newBuilder().setName(TombstoneUtils.Signals.SIGSEGV).build())
                        .build());
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                crashes,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(
                                                Pattern.compile("com\\.android\\.bluetooth"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testAbortMessageInclude() throws Exception {
        List<Tombstone> crashes = new ArrayList<>();
        crashes.add(
                Tombstone.newBuilder()
                        .addCommandLine("com.android.bluetooth")
                        .setSignalInfo(
                                Signal.newBuilder().setName(TombstoneUtils.Signals.SIGABRT).build())
                        .setAbortMessage(
                                "'[FATAL:allocation_tracker.cc(143)] Check failed: map_entry !="
                                        + " allocations.end().")
                        .build());

        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                crashes,
                                new TombstoneUtils.Config()
                                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                                        .appendAbortMessageIncludes("Check failed:")
                                        .setProcessPatterns(
                                                Pattern.compile("com\\.android\\.bluetooth"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);

        TombstoneUtils.assertNoSecurityCrashes(
                crashes,
                new TombstoneUtils.Config()
                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                        .appendAbortMessageIncludes("include not matches")
                        .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth")));
    }

    @Test
    public void testAbortMessageExclude() throws Exception {
        List<Tombstone> crashes = new ArrayList<>();
        crashes.add(
                Tombstone.newBuilder()
                        .addCommandLine("com.android.bluetooth")
                        .setSignalInfo(
                                Signal.newBuilder().setName(TombstoneUtils.Signals.SIGABRT).build())
                        .setAbortMessage(
                                "'[FATAL:allocation_tracker.cc(143)] Check failed: map_entry !="
                                        + " allocations.end().")
                        .build());

        TombstoneUtils.assertNoSecurityCrashes(
                crashes,
                new TombstoneUtils.Config()
                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                        .appendAbortMessageExcludes("Check failed:")
                        .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth")));

        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                crashes,
                                new TombstoneUtils.Config()
                                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                                        .appendAbortMessageExcludes("exclude not matches")
                                        .setProcessPatterns(
                                                Pattern.compile("com\\.android\\.bluetooth"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testAbortMessageExcludeCannotLink() throws Exception {
        List<Tombstone> crashes = new ArrayList<>();
        crashes.add(
                Tombstone.newBuilder()
                        .addCommandLine("/data/local/tmp/CVE-2020-0073")
                        .setSignalInfo(
                                Signal.newBuilder().setName(TombstoneUtils.Signals.SIGABRT).build())
                        .setAbortMessage(
                                "'CANNOT LINK EXECUTABLE \"/data/local/tmp/CVE-2020-0073\": library"
                                        + " \"libnfc-nci.so\" (\"(default)\","
                                        + " \"/data/local/tmp/CVE-2020-0073\", \"\") not found'")
                        .build());

        TombstoneUtils.assertNoSecurityCrashes(
                crashes,
                new TombstoneUtils.Config()
                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                        .setProcessPatterns(Pattern.compile("CVE-2020-0073")));

        crashes.add(
                Tombstone.newBuilder()
                        .addCommandLine("/data/local/tmp/CVE-2015-6616-2")
                        .setSignalInfo(
                                Signal.newBuilder().setName(TombstoneUtils.Signals.SIGABRT).build())
                        .setAbortMessage(
                                "'CANNOT LINK EXECUTABLE \"/data/local/tmp/CVE-2015-6616-2\":"
                                    + " cannot locate symbol \""
                                    + "_ZN7android14MediaExtractor17CreateFromServiceERKNS_2spINS_10DataSourceEEEPKc\""
                                    + " referenced by \"/data/local/tmp/CVE-2015-6616-2\"...'")
                        .build());

        TombstoneUtils.assertNoSecurityCrashes(
                crashes,
                new TombstoneUtils.Config()
                        .appendSignals(TombstoneUtils.Signals.SIGABRT)
                        .setProcessPatterns(Pattern.compile("CVE-2015-6616-2")));
    }

    @Test
    public void testBacktraceFilterIncludeFilename() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern("libaudioutils", null))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);

        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern("libstagefright", null),
                                                new BacktraceFilterPattern(
                                                        "libaudioflinger\\.so", null))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);

        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceIncludes(new BacktraceFilterPattern("libstagefright", null)));
    }

    @Test
    public void testBacktraceFilterExcludeFilename() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceExcludes(new BacktraceFilterPattern("libaudioutils", null)));
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceExcludes(
                                new BacktraceFilterPattern("libstagefright", null),
                                new BacktraceFilterPattern("libaudioflinger\\.so", null)));

        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceExcludes(
                                                new BacktraceFilterPattern(
                                                        "libstagefright", null))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testBacktraceFilterIncludeMethodName() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern(
                                                        null, "memcpy_to_float"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern(null, "strlen"),
                                                new BacktraceFilterPattern(
                                                        null, "memcpy_[^_]+_float"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);

        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceIncludes(new BacktraceFilterPattern(null, "strlen")));
    }

    @Test
    public void testBacktraceFilterExcludeMethodName() throws Exception {
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceExcludes(new BacktraceFilterPattern(null, "memcpy_to_float")));
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceExcludes(
                                new BacktraceFilterPattern(null, "strlen"),
                                new BacktraceFilterPattern(null, "memcpy_[^_]+_float")));
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceExcludes(
                                                new BacktraceFilterPattern(null, "strlen"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testBacktraceFilterCombinations() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern(null, null))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setIgnoreLowFaultAddress(true)
                                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                        .setBacktraceIncludes(
                                                new BacktraceFilterPattern(
                                                        "libaudioutils", "memcpy"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
        TombstoneUtils.assertNoSecurityCrashes(
                sTombstones,
                new TombstoneUtils.Config()
                        .setIgnoreLowFaultAddress(true)
                        .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                        .setBacktraceIncludes(
                                new BacktraceFilterPattern("libaudioutils", "strlen")));
    }

    @Test
    public void testMteAlwaysFails() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setProcessPatterns(
                                                Pattern.compile("com.redacted.mte-fail"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testAsanAlwaysFails() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                sTombstones,
                                new TombstoneUtils.Config()
                                        .setProcessPatterns(Pattern.compile("avrc_poc"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }

    @Test
    public void testWithAssertNoCrashesCatchesCrashRoot() throws Exception {
        assumeTrue(getDevice().enableAdbRoot());
        try {
            testWithAssertNoCrashesCatchesCrash();
        } finally {
            CommandUtil.runAndCheck(getDevice(), "rm -f /data/tombstones/*");
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testWithAssertNoCrashesCatchesCrashNonRoot() throws Exception {
        getDevice().disableAdbRoot();
        testWithAssertNoCrashesCatchesCrash();
    }

    private void testWithAssertNoCrashesCatchesCrash() throws Exception {
        String pgrepRegex = "sleep";
        ProcessUtil.killAll(
                getDevice(), pgrepRegex, /* timeoutMs */ 60_000, /* expectExist */ false);
        try (AutoCloseable a =
                TombstoneUtils.withAssertNoSecurityCrashes(
                        getDevice(), new TombstoneUtils.Config().setIgnoreLowFaultAddress(false))) {
            // need root to execute kill
            boolean wasAdbRoot = getDevice().isAdbRoot();
            assumeTrue(getDevice().enableAdbRoot());

            final ITestDevice device = getDevice();
            new java.lang.Thread(
                            () -> {
                                try {
                                    device.executeShellCommand("sleep 60");
                                } catch (DeviceNotAvailableException e) {
                                }
                            })
                    .start();
            Map<Integer, String> pidsMap = ProcessUtil.waitProcessRunning(getDevice(), pgrepRegex);
            Optional<Integer> pid = pidsMap.keySet().stream().findAny();
            // can't use Optional Truth checks in this branch
            assertThat(pid.isPresent()).isTrue();
            ProcessUtil.killPid(
                    getDevice(), pid.get(), /* SIGSEGV */ 11, ProcessUtil.PROCESS_WAIT_TIMEOUT_MS);
            if (!wasAdbRoot) {
                // restore previous root
                getDevice().disableAdbRoot();
            }
        } catch (AssertionError e) {
            // expected
            assertThat(e).hasMessageThat().contains("sleep");
            return;
        }
        fail("should have thrown an exception and detected the security crash");
    }

    @Test
    public void testWithAssertNoCrashesForNoCrashPriorCrashes() throws Exception {
        String pgrepRegex = "sleep";
        assumeTrue(getDevice().enableAdbRoot());
        ProcessUtil.killAll(
                getDevice(), pgrepRegex, /* timeoutMs */ 60_000, /* expectExist */ false);
        try {
            final ITestDevice device = getDevice();
            new java.lang.Thread(
                            () -> {
                                try {
                                    device.executeShellCommand("sleep 60");
                                } catch (DeviceNotAvailableException e) {
                                }
                            })
                    .start();
            Map<Integer, String> pidsMap = ProcessUtil.waitProcessRunning(getDevice(), pgrepRegex);
            Optional<Integer> pid = pidsMap.keySet().stream().findAny();
            assertThat(pid.isPresent()).isTrue();

            ProcessUtil.killPid(
                    getDevice(), pid.get(), /* SIGSEGV */ 11, ProcessUtil.PROCESS_WAIT_TIMEOUT_MS);

            try (AutoCloseable a =
                    TombstoneUtils.withAssertNoSecurityCrashes(
                            getDevice(), new TombstoneUtils.Config())) {}
        } finally {
            CommandUtil.runAndCheck(getDevice(), "rm -f /data/tombstones/*");
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testWithAssertNoCrashesForNoCrashNoPriorCrashes() throws Exception {
        assumeTrue(getDevice().enableAdbRoot());
        CommandUtil.runAndCheck(getDevice(), "rm -f /data/tombstones/*");
        try (AutoCloseable a =
                TombstoneUtils.withAssertNoSecurityCrashes(
                        getDevice(), new TombstoneUtils.Config())) {
        } finally {
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testEmptyTombstoneProtoNotSecurityCrash() throws Exception {
        assertFalse(
                TombstoneUtils.isSecurityCrash(
                        Tombstone.newBuilder().build(), new TombstoneUtils.Config()));
    }

    @Test
    public void testTidNotOverwrittenForMultithreadedProcess() throws Exception {
        assertThat(
                        TombstoneUtils.getSecurityCrashes(
                                        sTombstones,
                                        new TombstoneUtils.Config()
                                                .setProcessPatterns(
                                                        Pattern.compile(
                                                                "android.hardware.sensors@2.0-service.multihal"))
                                                .setBacktraceIncludes(
                                                        new BacktraceFilterPattern(
                                                                "sensors.ssc.so",
                                                                "sensor_factory::discover_sensors"))))
                .isNotEqualTo(EMPTY_TOMBSTONE_LIST);
    }
}
