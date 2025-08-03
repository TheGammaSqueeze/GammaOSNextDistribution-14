package android.security.cts;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_11282 extends NonRootSecurityTestCase {

    /**
     * CVE-2020-11282
     */
    @Test
    @AsbSecurityTest(cveBugId = 161374239)
    public void testPocCVE_2020_11282() throws Exception {
        assumeTrue(containsDriver(getDevice(), "/dev/kgsl-3d0"));
        AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2020-11282", getDevice(), 60);
    }
}
