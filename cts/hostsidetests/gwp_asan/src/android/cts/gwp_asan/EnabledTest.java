/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.gwp_asan;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class EnabledTest extends GwpAsanBaseTest {
    protected String getTestApk() {
        return "CtsGwpAsanEnabled.apk";
    }

    @Test
    public void testGwpAsanEnabled() throws Exception {
        runTest(".GwpAsanActivityTest", "testEnablement");
        runTest(".GwpAsanServiceTest", "testEnablement");
    }

    @Test
    public void testCrashToDropbox() throws Exception {
        runTest(".GwpAsanActivityTest", "testCrashToDropboxEnabled");
        runTest(".GwpAsanActivityTest", "testCrashToDropboxDefault");
        runTest(".GwpAsanServiceTest", "testCrashToDropboxEnabled");
        runTest(".GwpAsanServiceTest", "testCrashToDropboxDefault");
    }

    @Test
    public void testAppExitInfo() throws Exception {
        resetAppExitInfo();
        runTest(".GwpAsanActivityTest", "testCrashToDropboxDefault");
        runTest(".GwpAsanActivityTest", "checkAppExitInfo");
    }
}
