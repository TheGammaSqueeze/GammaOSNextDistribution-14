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
package com.android.adservices.tests.cts.topics;

import static com.android.adservices.service.FlagsConstants.KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;

abstract class CtsTopicsEndToEndTestCase extends AdServicesCtsTestCase {

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledOnClearSlateTests()
                .setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE)
                .setTopicsKillSwitch(false)
                .setTopicsOnDeviceClassifierKillSwitch(false)
                .setFlag(KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT, true)
                .setFlag(KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, true)
                .setFlag(KEY_ENABLE_ENROLLMENT_TEST_SEED, true)
                .setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                .setSystemProperty(KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                .setCompatModeFlags();
    }
}
