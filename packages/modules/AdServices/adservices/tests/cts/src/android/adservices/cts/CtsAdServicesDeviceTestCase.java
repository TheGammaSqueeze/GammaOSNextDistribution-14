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
package android.adservices.cts;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;

abstract class CtsAdServicesDeviceTestCase extends AdServicesCtsTestCase {

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        // NOTE: currently it's only used by AdServicesCommonManagerTest, so it's setting the
        // flags used by it. Once / if it's used by tests that don't need (or cannot have) them,
        // we'd need to split this method
        return AdServicesFlagsSetterRule.withDefaultLogcatTags()
                .setCompatModeFlags()
                .setPpapiAppAllowList(mPackageName);
    }
}
