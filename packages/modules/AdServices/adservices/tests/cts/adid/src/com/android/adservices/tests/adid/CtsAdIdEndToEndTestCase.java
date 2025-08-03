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
package com.android.adservices.tests.adid;

import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_API_APP_BLOCK_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;

abstract class CtsAdIdEndToEndTestCase extends AdServicesCtsTestCase {

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forAllApisEnabledTests()
                .setFlag(KEY_ADID_KILL_SWITCH, false)
                .setFlag(KEY_APP_CONFIG_RETURNS_ENABLED_BY_DEFAULT, true)
                .setFlag(KEY_ADID_REQUEST_PERMITS_PER_SECOND, 25.0)
                .setFlag(KEY_AD_ID_API_APP_BLOCK_LIST, "")
                .setCompatModeFlags();
    }
}
