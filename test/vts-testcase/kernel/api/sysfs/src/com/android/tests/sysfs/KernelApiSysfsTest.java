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
package com.android.tests.sysfs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RequiresDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.TargetFileUtils;
import com.android.tradefed.util.TargetFileUtils.FilePermission;
import com.android.tradefed.log.LogUtil.CLog;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

/* A test to check check sysfs files. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class KernelApiSysfsTest extends BaseHostJUnit4Test {
    /* Check required files in /sys/class/android_usb if they exist. */
    @RequiresDevice
    @Test
    public void testAndroidUSB() throws Exception {
        String state = "/sys/class/android_usb/android0/state";
        if (getDevice().doesFileExist(state)) {
            assertTrue(TargetFileUtils.isReadOnly(state, getDevice()));
            String content = getDevice().pullFileContents(state).trim();
            HashSet<String> possibles =
                    new HashSet<>(Arrays.asList("DISCONNECTED", "CONNECTED", "CONFIGURED"));
            assertTrue(possibles.contains(content));
        }
    }

    /**
     * Check the format of cpu online file.
     *
     * Confirm /sys/devices/system/cpu/online exists and is read-only.
     * Parse contents to ensure it is a comma-separated series of ranges
     * (%d-%d) and/or integers.
     */
    @Test
    public void testCpuOnlineFormat() throws Exception {
        String filePath = "/sys/devices/system/cpu/online";
        assertTrue(TargetFileUtils.isReadOnly(filePath, getDevice()));
        String content = getDevice().pullFileContents(filePath).trim();
        assertTrue(content.matches("(\\d+(-\\d+)?)(,\\d+(-\\d+)?)*"));
    }

    private void isReadOnlyAndIntegerContent(String filePath) throws Exception {
        assertTrue("Failed readonly check of " + filePath,
                TargetFileUtils.isReadOnly(filePath, getDevice()));
        String content = getDevice().pullFileContents(filePath).trim();
        // Confirm the content is integer.
        Long.parseLong(content);
    }

    private void isReadWriteAndIntegerContent(String filePath) throws Exception {
        assertTrue("Failed readwrite check of " + filePath,
                TargetFileUtils.isReadWriteOnly(filePath, getDevice()));
        String content = getDevice().pullFileContents(filePath).trim();
        // Confirm the content is integer.
        Long.parseLong(content);
    }

    /**
     * Check each cpu's scaling_cur_freq, scaling_min_freq, scaling_max_freq,
     * scaling_available_frequencies, and time_in_state files.
     */
    @Test
    public void testPerCpuCpufreq() throws Exception {
        String filePath = "/sys/devices/system/cpu/online";
        assertTrue(TargetFileUtils.isReadOnly(filePath, getDevice()));
        String onlineCpus = getDevice().pullFileContents(filePath).trim();
        String[] cpuRanges = onlineCpus.split(",");
        List<Integer> cpuList = new ArrayList<>();
        Pattern p = Pattern.compile("(\\d+)(-\\d+)?");
        for (String range : cpuRanges) {
            Matcher m = p.matcher(range);
            assertTrue("Malformatted range in " + filePath, m.find());
            int startCpu = Integer.parseInt(m.group(1));
            int endCpu = startCpu;
            if (m.group(2) != null)
                endCpu = Integer.parseInt(m.group(2).substring(1));
            for (int i = startCpu; i <= endCpu; i++) {
                cpuList.add(i);
            }
        }

        String f;
        String content;
        for (int cpu : cpuList) {
            f = String.format("/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", cpu);
            if (getDevice().doesFileExist(f)) {
                isReadOnlyAndIntegerContent(f);
            }

            f = String.format("/sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq", cpu);
            if (getDevice().doesFileExist(f)) {
                isReadWriteAndIntegerContent(f);
            }

            f = String.format("/sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq", cpu);
            if (getDevice().doesFileExist(f)) {
                isReadWriteAndIntegerContent(f);
            }

            f = String.format(
                    "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_available_frequencies", cpu);
            if (getDevice().doesFileExist(f)) {
                assertTrue(TargetFileUtils.isReadOnly(f, getDevice()));
                content = getDevice().pullFileContents(f).trim();
                if (!Strings.isNullOrEmpty(content)) {
                    String[] availFreqs = content.split(" ");
                    for (String freq : availFreqs) {
                        Long.parseLong(freq);
                    }
                }
            }

            f = String.format("/sys/devices/system/cpu/cpu%d/cpufreq/stats/time_in_state", cpu);
            if (getDevice().doesFileExist(f)) {
                assertTrue(TargetFileUtils.isReadOnly(f, getDevice()));
                content = getDevice().pullFileContents(f).trim();
                if (!Strings.isNullOrEmpty(content)) {
                    for (String line : content.split("\\n")) {
                        String[] values = line.split(" ");
                        for (String value : values) {
                            Long.parseLong(value);
                        }
                    }
                }
            }
        }
    }

    /* Check /sys/kernel/wakeup_reasons/last_resume_reason. */
    @Test
    public void testLastResumeReason() throws Exception {
        String filePath = "/sys/kernel/wakeup_reasons/last_resume_reason";
        assertTrue(TargetFileUtils.isReadOnly(filePath, getDevice()));
    }

    /* Check the value of /sys/devices/system/cpu/kernel_max. */
    @Test
    public void testKernelMax() throws Exception {
        String filePath = "/sys/devices/system/cpu/kernel_max";
        isReadOnlyAndIntegerContent(filePath);
    }

    private String[] findFiles(String dir, String nameFilter) throws Exception {
        String output = getDevice().executeShellCommand(
                String.format("find %s -name \"%s\" -maxdepth 1 -type l", dir, nameFilter));
        if (output.trim().isEmpty()) {
            return new String[0];
        }
        return output.split("\r?\n");
    }

    /* Check for /sys/class/net/?/mtu. */
    @Test
    public void testNetMTU() throws Exception {
        String[] dirList = findFiles("/sys/class/net", "*");
        for (String entry : dirList) {
            isReadWriteAndIntegerContent(entry + "/mtu");
        }
    }

    /* Read a config option from /proc/config.gz. If the option is missing, return null. */
    private String getKernelConfigValue(String config) throws Exception {
        assertTrue(config.startsWith("CONFIG_"));
        Pattern p = Pattern.compile(config + "=(.*)");
        String output =
                getDevice().executeShellCommand("zcat /proc/config.gz | grep " + config);
        Matcher m = p.matcher(output);
        if (!m.find())
            return null;
        else
            return m.group(1);
    }

    /* Read a string value from the config and return it without quotes. */
    private String getKernelConfigUnquotedString(String config) throws Exception {
        String value = getKernelConfigValue(config);
        if (value == null)
            return value;
        int len = value.length();
        /*
         * Assuming that the config contains a quoted string. If this is not the case, use
         * getKernelConfigValue() instead.
         */
        assertTrue((len >= 2) && (value.charAt(0) == '"') && (value.charAt(len - 1) == '"'));
        value = value.substring(1, len - 1);
        return value;
    }

    /* If RTC is present, check that /dev/rtc matches CONFIG_RTC_HCTOSYS_DEVICE */
    @Test
    public void testRtcHctosys() throws Exception {
        String[] rtcList = findFiles("/sys/class/rtc", "rtc*");
        assumeTrue("Device has RTC", rtcList.length != 0);
        String rtc = getKernelConfigUnquotedString("CONFIG_RTC_HCTOSYS_DEVICE");
        if (rtc == null)
            fail("Could not find CONFIG_RTC_HCTOSYS_DEVICE");
        String rtc_link = getDevice().executeShellCommand("readlink /dev/rtc");
        if (rtc_link.isEmpty()) {
            if (getDevice().doesFileExist("/dev/rtc0")) {
                rtc_link = "rtc0";
            } else {
                fail("Neither /dev/rtc nor /dev/rtc0 exist");
            }
        }
        assertTrue(String.format("(%s) does not match RTC_HCTOSYS_DEVICE (%s)", rtc_link, rtc),
                rtc.equals(rtc_link));
    }

    /* Check that locking and unlocking a wake lock works.. */
    @Test
    public void testWakeLock() throws Exception {
        String wakeLockPath = "/sys/power/wake_lock";
        String wakeUnLockPath = "/sys/power/wake_unlock";
        String lockName = "KernelApiSysfsTestWakeLock" + UUID.randomUUID().toString();

        // Enable wake lock
        getDevice().executeShellCommand(String.format("echo %s > %s", lockName, wakeLockPath));

        // Confirm wake lock is enabled
        String results = getDevice().pullFileContents(wakeLockPath).trim();
        HashSet<String> activeSources = new HashSet<>(Arrays.asList(results.split(" ")));
        assertTrue(String.format("active wake lock not reported in %s", wakeLockPath),
                activeSources.contains(lockName));

        // Disable wake lock
        getDevice().executeShellCommand(String.format("echo %s > %s", lockName, wakeUnLockPath));

        // Confirm wake lock is no longer enabled
        results = getDevice().pullFileContents(wakeLockPath).trim();
        activeSources = new HashSet<>(Arrays.asList(results.split(" ")));
        assertTrue(String.format("inactive wake lock reported in %s", wakeLockPath),
                !activeSources.contains(lockName));

        results = getDevice().pullFileContents(wakeUnLockPath).trim();
        activeSources = new HashSet<>(Arrays.asList(results.split(" ")));
        assertTrue(String.format("inactive wake lock not reported in %s", wakeUnLockPath),
                activeSources.contains(lockName));
    }

    @Test
    public void testWakeupCount() throws Exception {
        String filePath = "/sys/power/wakeup_count";
        assertTrue("Failed readwrite check of " + filePath,
                TargetFileUtils.isReadWriteOnly(filePath, getDevice()));
    }

    /* /sys/power/state controls the system sleep states. */
    @Test
    public void testSysPowerState() throws Exception {
        String filePath = "/sys/power/state";
        assertTrue("Failed readwrite check of " + filePath,
                TargetFileUtils.isReadWriteOnly(filePath, getDevice()));
        String content = getDevice().pullFileContents(filePath).trim();
        HashSet<String> allowedStates =
                new HashSet<String>(Arrays.asList("freeze", "mem", "disk", "standby"));
        for (String state : content.split(" ")) {
            assertTrue(String.format("Invalid system power state: '%s'", state),
                    allowedStates.contains(state));
        }
    }

    /* Get the kernel version (at least the major/minor numbers) as an array of integers. */
    private int[] getKernelVersion() throws Exception {
        String versionStr = getDevice().executeShellCommand("uname -r");
        Pattern p = Pattern.compile("([0-9.]+)");
        Matcher m = p.matcher(versionStr);
        assertTrue("Bad version: " + versionStr, m.find());
        int[] res = Arrays.stream(m.group(1).split("\\.")).mapToInt(Integer::parseInt).toArray();
        assertTrue("Missing major or minor version: " + Arrays.toString(res), res.length > 1);
        return res;
    }

    /* /sys/module/kfence/parameters/sample_interval contains KFENCE sampling rate. */
    @Test
    public void testKfenceSampleRate() throws Exception {
        final int kRecommendedSampleRate = 500;
        int[] version = getKernelVersion();
        int kernel_major = version[0];
        int kernel_minor = version[1];

        assumeTrue("CONFIG_ARM64 not set, skipping the test",
                   getKernelConfigValue("CONFIG_ARM64") != null);

        // Do not require KFENCE for kernels < 5.10.
        assumeTrue(kernel_major >= 5);
        assumeTrue(kernel_major > 5 || kernel_minor >= 10);

        String filePath = "/sys/module/kfence/parameters/sample_interval";
        assertTrue("Failed readwrite check of " + filePath,
                TargetFileUtils.isReadWriteOnly(filePath, getDevice()));
        String content = getDevice().pullFileContents(filePath).trim();
        int sampleRate = Integer.parseInt(content);
        assertTrue(
                "Bad KFENCE sample rate: " + sampleRate + ", should be " + kRecommendedSampleRate,
                sampleRate == kRecommendedSampleRate);
    }

    /* Ensure kernel stack initialization is enabled. */
    @Test
    public void testKernelStackInitialization() throws Exception {
        int[] version = getKernelVersion();
        int kernel_major = version[0];
        int kernel_minor = version[1];

        // Do not require stack initialization for kernels < 5.4.
        assumeTrue(kernel_major >= 5);
        assumeTrue(kernel_major > 5 || kernel_minor >= 4);

        /*
         * Upstream Linux kernels may use CONFIG_INIT_STACK_ALL instead, but new (>= 5.4)
         * android-common kernels only have CONFIG_INIT_STACK_ALL_ZERO.
         */
        String configName = "CONFIG_INIT_STACK_ALL_ZERO";
        String hasInitialization = getKernelConfigValue(configName);
        assertTrue(configName + " not enabled in the kernel config", hasInitialization != null);
    }
}
