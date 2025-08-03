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

package android.security.cts;

import static org.junit.Assume.assumeFalse;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_187957589 extends NonRootSecurityTestCase {
    /**
     * b/187957589
     * Vulnerability Behaviour: out of bounds write in noteAtomLogged for negative atom ids.
     */
    @Test
    @AsbSecurityTest(cveBugId = 187957589)
    public void testPocBug_187957589() throws Exception {
        assumeFalse(moduleIsPlayManaged("com.google.android.os.statsd"));
        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("statsd");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
            AdbUtils.runPoc("Bug-187957589", getDevice());
            // Sleep to ensure statsd was able to process the injected event.
            RunUtil.getDefault().sleep(5_000);
        }
    }
}
