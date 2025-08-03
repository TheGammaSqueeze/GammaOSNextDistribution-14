package android.security.cts;

import static org.junit.Assert.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc20_01 extends NonRootSecurityTestCase {
    /**
     * CVE-2019-14002
     */
    @Test
    @AsbSecurityTest(cveBugId = 142271274)
    public void testPocCVE_2019_14002() throws Exception {
        String result =
                AdbUtils.runCommandLine(
                        "dumpsys package com.qualcomm.qti.callenhancement", getDevice());
        assertNotMatchesMultiLine("READ_EXTERNAL_STORAGE.*?WRITE_EXTERNAL_STORAGE", result);
    }
}
