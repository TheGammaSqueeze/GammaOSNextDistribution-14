/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.sandbox.topics;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.service.FlagsConstants;

abstract class CtsSandboxedTopicsManagerTestsTestCase extends AdServicesCtsTestCase {

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    protected static final long TEST_EPOCH_JOB_PERIOD_MS = 3_000;

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                .setFlag(FlagsConstants.KEY_TOPICS_KILL_SWITCH, false)
                .setFlag(FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, true)
                // TODO(b/328101177): should set flags below (instead of system properties), but
                // the test could fail if some other test set these properties(and didn't properly
                // reset them)
                .setSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                .setFlag(FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true);
    }
}
