/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assume.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.compatibility.common.util.BusinessLogicMapStore;

import org.junit.runner.Description;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Common STS extra business logic for host-side and device-side to implement. */
public interface StsLogic {

    static final String LOG_TAG = StsLogic.class.getSimpleName();

    // keep in sync with google3:
    // //wireless/android/partner/apbs/*/config/xtsbgusinesslogic/sts_business_logic.gcl
    List<String> STS_EXTRA_BUSINESS_LOGIC_FULL =
            Arrays.asList(
                    new String[] {
                        "uploadSpl",
                        "uploadModificationTime",
                        "uploadKernelBugs",
                        "uploadMainlineModules",
                        "declaredSpl",
                        "fridaAssetTemplate",
                        "mainline",
                    });
    List<String> STS_EXTRA_BUSINESS_LOGIC_INCREMENTAL =
            Arrays.asList(
                    new String[] {
                        "uploadSpl",
                        "uploadModificationTime",
                        "uploadKernelBugs",
                        "uploadMainlineModules",
                        "declaredSpl",
                        "incremental",
                        "fridaAssetTemplate",
                        "mainline",
                    });

    // intentionally empty because declaredSpl and incremental skipping is not desired when
    // developing STS tests.
    // Exceptions:
    // * uploads
    // * mainline skipping (this is behind an additional flag)
    // * frida (necessary for frida tests)
    List<String> STS_EXTRA_BUSINESS_LOGIC_DEVELOP =
            Arrays.asList(
                    new String[] {
                        "fridaAssetTemplate",
                        "uploadMainlineModules",
                        "mainline",
                    });

    Description getTestDescription();

    LocalDate getPlatformSpl();

    Optional<LocalDate> getKernelBuildDate();

    boolean shouldUseKernelSpl();

    boolean shouldSkipMainlineTests();

    LocalDate getReleaseBulletinSpl();

    static List<String> getExtraBusinessLogicForPlan(String stsDynamicPlan) {
        switch (stsDynamicPlan) {
            case "incremental":
                return STS_EXTRA_BUSINESS_LOGIC_INCREMENTAL;
            case "full":
                return STS_EXTRA_BUSINESS_LOGIC_FULL;
            case "develop":
                return STS_EXTRA_BUSINESS_LOGIC_DEVELOP;
            default:
                throw new RuntimeException(
                        "Could not find Dynamic STS plan in InstrumentationRegistry arguments");
        }
    }

    default long[] getCveBugIds() {
        AsbSecurityTest annotation = getTestDescription().getAnnotation(AsbSecurityTest.class);
        if (annotation == null) {
            return null;
        }
        return annotation.cveBugId();
    }

    default boolean isBugSplDataKnownMissing() {
        long[] bugIds = getCveBugIds();
        if (bugIds == null) {
            // no spl data, don't complain
            return true;
        }
        // true if the bug id is older than ~ June 2020
        return Arrays.stream(bugIds).min().getAsLong() < 157905780;
    }

    default LocalDate getDeviceSpl() {
        LocalDate platformSpl = getPlatformSpl();
        if (shouldUseKernelSpl()) {
            Set<String> bugIds = BusinessLogicSetStore.getSet("kernel_bugs");
            boolean isKernel = false;
            for (long bugId : getCveBugIds()) {
                isKernel |= bugIds.contains(Long.toString(bugId));
            }
            if (isKernel) {
                Optional<LocalDate> kernelBuildDate = getKernelBuildDate();
                if (!kernelBuildDate.isPresent()) {
                    logWarn(LOG_TAG, "could not read kernel SPL, falling back to platform SPL");
                    return platformSpl;
                }
                if (kernelBuildDate.get().plusMonths(2).isAfter(platformSpl)) {
                    // if the kernel was built within the past 2 months, it's likely still in
                    // support and the platform SPL is still canonical.
                    // 2 months because the future ASB is at most (partner + public) months ahead
                    logInfo(LOG_TAG, "kernel SPL is too recent, using platform SPL");
                    return platformSpl;
                }
                // the kernel build date can be used to approximate the SPL of the device/build
                // when it stopped getting kernel updates
                return kernelBuildDate.get();
            }
        }
        return platformSpl;
    }

    default LocalDate getMinTestSpl() {
        Map<String, String> map = BusinessLogicMapStore.getMap("security_bulletins");
        if (map == null) {
            throw new IllegalArgumentException("Could not find the security bulletin map");
        }
        LocalDate minSpl = null;
        for (long cveBugId : getCveBugIds()) {
            String splString = map.get(Long.toString(cveBugId));
            if (splString == null) {
                // This bug id wasn't found in the map.
                // This is a new test or the bug was removed from the bulletin and this is an old
                // binary. Neither is a critical issue and the test will run in these cases.
                // New test: developer should be able to write the test without getting blocked.
                // Removed bug + old binary: test will run.
                logWarn(LOG_TAG, "could not find the CVE bug %d in the spl map", cveBugId);
                continue;
            }
            LocalDate spl = SplUtils.localDateFromSplString(splString);
            if (minSpl == null) {
                minSpl = spl;
            } else if (spl.isBefore(minSpl)) {
                minSpl = spl;
            }
        }
        return minSpl;
    }

    default LocalDate getMinModificationDate() {
        Map<String, String> map = BusinessLogicMapStore.getMap("sts_modification_times");
        if (map == null) {
            throw new IllegalArgumentException("Could not find the modification date map");
        }
        LocalDate minModificationDate = null;
        for (long cveBugId : getCveBugIds()) {
            String modificationMillisString = map.get(Long.toString(cveBugId));
            if (modificationMillisString == null) {
                logInfo(
                        LOG_TAG,
                        "Could not find the CVE bug %d in the modification date map",
                        cveBugId);
                continue;
            }
            LocalDate modificationDate =
                    SplUtils.localDateFromMillis(Long.parseLong(modificationMillisString));
            if (minModificationDate == null) {
                minModificationDate = modificationDate;
            } else if (modificationDate.isBefore(minModificationDate)) {
                minModificationDate = modificationDate;
            }
        }
        return minModificationDate;
    }

    default boolean shouldSkipIncremental() {
        logDebug(LOG_TAG, "filtering by incremental");

        long[] bugIds = getCveBugIds();
        if (bugIds == null) {
            // There were no @AsbSecurityTest annotations
            logInfo(LOG_TAG, "not an ASB test");
            return false;
        }

        // check if test spl is older than the past 6 months from the device spl
        LocalDate deviceSpl = getDeviceSpl();
        LocalDate incrementalCutoffSpl = deviceSpl.plusMonths(-6);

        LocalDate minTestModifiedDate = getMinModificationDate();
        if (minTestModifiedDate == null) {
            // could not get the modification date - run the test
            if (isBugSplDataKnownMissing()) {
                logDebug(LOG_TAG, "no data for this old test");
                return true;
            }
            return false;
        }
        if (minTestModifiedDate.isAfter(incrementalCutoffSpl)) {
            logDebug(LOG_TAG, "the test was recently modified");
            return false;
        }

        LocalDate minTestSpl = getMinTestSpl();
        if (minTestSpl == null) {
            // could not get the test spl - run the test
            logWarn(LOG_TAG, "could not get the test SPL");
            return false;
        }
        if (minTestSpl.isAfter(incrementalCutoffSpl)) {
            logDebug(LOG_TAG, "the test has a recent SPL");
            return false;
        }

        logDebug(LOG_TAG, "test should skip");
        return true;
    }

    default boolean shouldSkipDeclaredSpl() {
        if (getCveBugIds() == null) {
            // There were no @AsbSecurityTest annotations
            logInfo(LOG_TAG, "not an ASB test");
            return false;
        }

        LocalDate minTestSpl = getMinTestSpl();
        if (!isBugSplDataKnownMissing()) {
            LocalDate releaseBulletinSpl = getReleaseBulletinSpl();
            if (releaseBulletinSpl != null) {
                // this is a QA environment

                // assert that the test has a known SPL when we expect the data to be fresh
                assertNotNull("Unknown SPL for new CVE", minTestSpl);

                // set the days to be the same so we only compare year-month
                releaseBulletinSpl = releaseBulletinSpl.withDayOfMonth(minTestSpl.getDayOfMonth());
                // the test SPL can't be equal to or after the release bulletin SPL
                assertFalse(
                        "Newer SPL than release bulletin", releaseBulletinSpl.isBefore(minTestSpl));
            } else {
                // we are in a live environment; don't run tests that have their SPL deferred
                if (minTestSpl == null) {
                    // can't find the test SPL for this ASB test; skip
                    return true;
                }
            }
        }
        if (minTestSpl == null) {
            // no SPL for this test; run normally
            return false;
        }

        // skip if the test is newer than the device SPL
        LocalDate deviceSpl = getDeviceSpl();
        return minTestSpl.isAfter(deviceSpl);
    }

    default boolean shouldSkipMainline() {
        // check if the flag to skip mainline tests has been set to true
        if (!shouldSkipMainlineTests()) {
            return false;
        }

        long[] bugIds = getCveBugIds();
        if (bugIds == null) {
            // There were no @AsbSecurityTest annotations
            logInfo(LOG_TAG, "not an ASB test");
            return false;
        }

        Map<String, String> bugModulesMap = BusinessLogicMapStore.getMap("bugid_mainline_modules");
        for (long bugId : bugIds) {
            if (bugModulesMap.containsKey(Long.toString(bugId))) {
                return true;
            }
        }
        return false;
    }

    default void skip(String message) {
        assumeTrue(message, false);
    }

    public void logInfo(String logTag, String format, Object... args);

    public void logDebug(String logTag, String format, Object... args);

    public void logWarn(String logTag, String format, Object... args);

    public void logError(String logTag, String format, Object... args);
}
