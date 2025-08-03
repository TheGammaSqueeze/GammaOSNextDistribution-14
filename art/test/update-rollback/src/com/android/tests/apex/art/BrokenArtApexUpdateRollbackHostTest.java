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

package com.android.tests.apex.art;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.GenericLogcatEventParser;
import com.android.tradefed.util.StreamUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test checking whether broken ART APEX updates are properly rolled back.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BrokenArtApexUpdateRollbackHostTest extends BaseHostJUnit4Test {

    private static final long EVENT_TIMEOUT_MS = 30 * 1000L;

    private ApexUpdateLogcatEventParser mLogcatEventParser;

    private static ArtApexTestUtils sTestUtils;

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        sTestUtils = new ArtApexTestUtils(testInfo);
        sTestUtils.installTestApex();
    }

    @Test
    public void verifyArtApexIsRolledBack() throws Exception {
        startLogcatListener();

        {
            ApexUpdateLogcatEventParser.LogcatEvent result =
                    mLogcatEventParser.waitForEvent(EVENT_TIMEOUT_MS);
            assertNotNull(result);
            assertEquals(ApexUpdateLogcatEventType.SESSION_REVERTED, result.getEventType());
            CLog.i("Found event type " + result.getEventType() + " in logcat: "
                    + result.getMessage());
        }

        {
            ApexUpdateLogcatEventParser.LogcatEvent result =
                    mLogcatEventParser.waitForEvent(EVENT_TIMEOUT_MS);
            assertNotNull(result);
            assertEquals(ApexUpdateLogcatEventType.ROLLBACK_SUCCESS, result.getEventType());
            CLog.i("Found event type " + result.getEventType() + " in logcat: "
                    + result.getMessage());
        }

        stopLogcatListener();
    }

    // TODO(b/185672266): When tombstones are preserved across rollbacks, add a check verifying the
    // presence of a tombstone for the crash that trigerred the rollback.

    // TODO(b/185672266): When logcat entries are preserved across rollbacks, add a check verifying
    // the presence of relevant logcat entries (e.g. a stack trace for the native crash).

    @AfterClassWithInfo
    public static void afterClassWithDevice(TestInformation testInfo) throws Exception {
        // Note: This should not be necessary, as the "broken" ART APEX is expected to be rolled
        // back.
        sTestUtils.uninstallTestApex();
    }

    private void startLogcatListener() {
        if (mLogcatEventParser == null) {
            mLogcatEventParser = ApexUpdateLogcatEventParserFactory.buildParser(getDevice());
        }
        mLogcatEventParser.start();
    }

    private void stopLogcatListener() {
        StreamUtil.close(mLogcatEventParser);
    }
}


/** Event types for {@link ApexUpdateLogcatEventParser}. */
enum ApexUpdateLogcatEventType {
    SESSION_REVERTED,
    ROLLBACK_SUCCESS,
}

/** Logcat parser for APEX update related events. */
class ApexUpdateLogcatEventParser extends GenericLogcatEventParser<ApexUpdateLogcatEventType> {
    public ApexUpdateLogcatEventParser(ITestDevice device) {
        super(device);
    }
}

/** Creates a ApexUpdateLogcatEventParser populated with event triggers for APEX update tests. */
class ApexUpdateLogcatEventParserFactory {

    public static ApexUpdateLogcatEventParser buildParser(ITestDevice device) {
        ApexUpdateLogcatEventParser parser = new ApexUpdateLogcatEventParser(device);
        return registerEventTriggers(parser);
    }

    public static ApexUpdateLogcatEventParser registerEventTriggers(
        ApexUpdateLogcatEventParser parser) {
        // Note: Events registered first are matched first.

        // Look for a line like:
        //
        //   04-17 16:22:09.776  1728  1728 D PackageInstallerSession: Marking session 530520784 as failed: Session reverted due to crashing native process: zygote
        //
        parser.registerEventTrigger(
                LogLevel.DEBUG,
                "PackageInstallerSession",
                "Session reverted due to crashing native process: zygote",
                ApexUpdateLogcatEventType.SESSION_REVERTED);

        // Look for a line like:
        //
        //   04-17 16:22:23.938  1728  1880 I WatchdogRollbackLogger: Watchdog event occurred with type: ROLLBACK_SUCCESS logPackage: VersionedPackage[com.google.android.modulemetadata/310000000] rollbackReason: REASON_NATIVE_CRASH_DURING_BOOT failedPackageName: zygote
        //
        parser.registerEventTrigger(
                LogLevel.INFO,
                "WatchdogRollbackLogger",
                "Watchdog event occurred with type: ROLLBACK_SUCCESS",
                ApexUpdateLogcatEventType.ROLLBACK_SUCCESS);

        return parser;
    }
}
