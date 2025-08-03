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

package com.android.tests.odsign;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * This class tests odrefresh for the cases where all the APEXes are initially factory-installed
 * and the cache info exists, which is the normal case.
 *
 * Both the tests in the base class and the tests in this class are run with the setup of this
 * class.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OdrefreshFactoryWithCacheInfoHostTest extends OdrefreshFactoryHostTestBase {
    @Test
    public void verifyNoCompilationWhenSystemIsGood() throws Exception {
        // Only the cache info should exist.
        mTestUtils.assertFilesExist(Set.of(OdsignTestUtils.CACHE_INFO_FILE));
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedPrimaryBootImage());
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedBootImageMainlineExtension());
        mTestUtils.assertFilesNotExist(mTestUtils.getSystemServerExpectedArtifacts());

        // Run again.
        long timeMs = mTestUtils.getCurrentTimeMs();
        mTestUtils.runOdrefresh();

        // Nothing should change.
        mTestUtils.assertNotModifiedAfter(Set.of(OdsignTestUtils.CACHE_INFO_FILE), timeMs);
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedPrimaryBootImage());
        mTestUtils.assertFilesNotExist(mTestUtils.getExpectedBootImageMainlineExtension());
        mTestUtils.assertFilesNotExist(mTestUtils.getSystemServerExpectedArtifacts());
    }
}
