package android.security.cts;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_11173 extends NonRootSecurityTestCase {

    /**
     * CVE-2020-11173
     */
    @Test
    @AsbSecurityTest(cveBugId = 160605709)
    public void testPocCVE_2020_11173() throws Exception {
        if (containsDriver(getDevice(), "/dev/adsprpc-smd")
                && containsDriver(getDevice(), "/dev/ion")) {
            AdbUtils.runPocNoOutput("CVE-2020-11173", getDevice(), 300);
        }
    }
}
