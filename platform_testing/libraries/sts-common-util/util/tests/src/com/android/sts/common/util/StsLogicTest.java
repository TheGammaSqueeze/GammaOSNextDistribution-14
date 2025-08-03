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

import static org.junit.Assert.*;

import com.android.compatibility.common.util.BusinessLogicMapStore;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

@RunWith(DeviceJUnit4ClassRunner.class)
public class StsLogicTest extends BaseHostJUnit4Test {

    private static final long[] PLATFORM_BUG = {1_000_000_000L};
    private static final long[] KERNEL_BUG = {2_000_000_000L};
    private static final long[] MAINLINE_BUG = {3_000_000_000L};

    static {
        new BusinessLogicSetStore()
                .putSet(
                        "kernel_bugs",
                        Arrays.stream(KERNEL_BUG).mapToObj(Long::toString).toArray(String[]::new));
        new BusinessLogicMapStore().putMap("security_bulletins", null);
        new BusinessLogicMapStore().putMap("sts_modification_times", null);
        new BusinessLogicMapStore()
                .putMap(
                        "bugid_mainline_modules",
                        "#",
                        Arrays.stream(MAINLINE_BUG)
                                .mapToObj((bugId) -> Long.toString(bugId) + "#module 1,module 2")
                                .toArray(String[]::new));
    }

    @Test
    public final void testGetDeviceSplForPlatformBug() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(PLATFORM_BUG)
                        .setPlatformSpl("2022-01-01")
                        .setKernelBuildDate("2020-01-01")
                        .setShouldUseKernelSpl(true);
        assertEquals(
                "should use platform SPL because this is not a kernel test."
                        + BusinessLogicSetStore.getSet("kernel_bugs"),
                logic.getPlatformSpl(),
                logic.getDeviceSpl());
    }

    @Test
    public final void testGetDeviceSplUsesKernelBuildDate() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(KERNEL_BUG)
                        .setPlatformSpl("2022-01-01")
                        .setKernelBuildDate("2020-01-01")
                        .setShouldUseKernelSpl(true);
        assertEquals(
                "should use kernel build date because the kernel stopped being updated.",
                logic.getKernelBuildDate().get(),
                logic.getDeviceSpl());
    }

    @Test
    public final void testGetDeviceSplForKernelBugWithBadKernelBuildDate() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(KERNEL_BUG)
                        .setPlatformSpl("2022-01-01")
                        .setKernelBuildDate(null)
                        .setShouldUseKernelSpl(true);
        assertEquals(
                "should use platform SPL because the kernel build date couldn't be read.",
                logic.getPlatformSpl(),
                logic.getDeviceSpl());
    }

    @Test
    public final void testGetDeviceSplUsesPlatformSplWhenShouldntUseKernel() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(KERNEL_BUG)
                        .setPlatformSpl("2022-01-01")
                        .setKernelBuildDate("2020-01-01")
                        .setShouldUseKernelSpl(false);
        assertEquals(
                "should use platform SPL because the option to use kernel build date was off.",
                logic.getPlatformSpl(),
                logic.getDeviceSpl());
    }

    @Test
    public final void testGetDeviceSplSkipsKernelBuildDateWhenRecent() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(KERNEL_BUG)
                        .setPlatformSpl("2022-02-28")
                        .setKernelBuildDate("2022-01-01")
                        .setShouldUseKernelSpl(true);
        assertEquals(
                "should use platform spl because the kernel is too new.",
                logic.getPlatformSpl(),
                logic.getDeviceSpl());
    }

    @Test
    public final void testNoSkipMainlineNoFlag() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(MAINLINE_BUG);
        assertFalse(
                "shouldn't skip because the flag isn't set.",
                logic.shouldSkipMainline());
    }

    @Test
    public final void testSkipMainlineWithFlag() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(MAINLINE_BUG)
                        .setShouldSkipMainlineTests(true);
        assertTrue(
                "should skip because the flag is set and this is a Mainline CVE.",
                logic.shouldSkipMainline());
    }

    @Test
    public final void testNoSkipMainlineNotCve() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setCveBugIds(null)
                        .setShouldSkipMainlineTests(true);
        assertFalse(
                "shouldn't Mainline skip because this test is not a CVE test.",
                logic.shouldSkipMainline());
    }

    @Test
    public final void testNoSkipMainlineNotMainlineCve() throws Exception {
        StsLogic logic =
                new StsLogicMock()
                        .setShouldSkipMainlineTests(true);
        assertFalse(
                "shouldn't Mainline skip because this test is not a Mainline CVE.",
                logic.shouldSkipMainline());
    }

    private static class StsLogicMock implements StsLogic {

        private long[] cveBugIds = PLATFORM_BUG;
        private LocalDate platformSpl;
        private LocalDate releaseBulletinSpl;
        private Optional<LocalDate> kernelBuildDate;
        private boolean shouldUseKernelSpl = false;
        private boolean shouldSkipMainlineTests = false;

        {
            setPlatformSpl("2022-01-01");
            setReleaseBulletinSpl("2022-01-01");
            setKernelBuildDate("2022-01-01");
        }

        public StsLogicMock setCveBugIds(long... cveBugIds) {
            this.cveBugIds = cveBugIds;
            return this;
        }

        public StsLogicMock setPlatformSpl(String platformSpl) {
            this.platformSpl = SplUtils.localDateFromSplString(platformSpl);
            return this;
        }

        public StsLogicMock setReleaseBulletinSpl(String releaseBulletinSpl) {
            this.releaseBulletinSpl = SplUtils.localDateFromSplString(releaseBulletinSpl);
            return this;
        }

        public StsLogicMock setKernelBuildDate(String kernelBuildDate) {
            if (kernelBuildDate == null) {
                this.kernelBuildDate = Optional.empty();
                return this;
            }
            this.kernelBuildDate = Optional.of(SplUtils.localDateFromSplString(kernelBuildDate));
            return this;
        }

        public StsLogicMock setShouldUseKernelSpl(boolean shouldUseKernelSpl) {
            this.shouldUseKernelSpl = shouldUseKernelSpl;
            return this;
        }

        public StsLogicMock setShouldSkipMainlineTests(boolean shouldSkipMainlineTests) {
            this.shouldSkipMainlineTests = shouldSkipMainlineTests;
            return this;
        }

        @Override
        public Description getTestDescription() {
            throw new UnsupportedOperationException(
                    "Please override the method that provides the details from the test"
                            + " Description");
        }

        @Override
        public long[] getCveBugIds() {
            return this.cveBugIds;
        }

        @Override
        public LocalDate getPlatformSpl() {
            return this.platformSpl;
        }

        @Override
        public Optional<LocalDate> getKernelBuildDate() {
            return this.kernelBuildDate;
        }

        @Override
        public boolean shouldUseKernelSpl() {
            return this.shouldUseKernelSpl;
        }

        @Override
        public boolean shouldSkipMainlineTests() {
            return this.shouldSkipMainlineTests;
        }

        @Override
        public LocalDate getReleaseBulletinSpl() {
            return this.releaseBulletinSpl;
        }

        @Override
        public void logInfo(String logTag, String format, Object... args) {
            // log nothing
        }

        @Override
        public void logDebug(String logTag, String format, Object... args) {
            // log nothing
        }

        @Override
        public void logWarn(String logTag, String format, Object... args) {
            // log nothing
        }

        @Override
        public void logError(String logTag, String format, Object... args) {
            // log nothing
        }
    }
}
