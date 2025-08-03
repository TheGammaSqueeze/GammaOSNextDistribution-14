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
 */ package android.adservices.debuggablects;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;

abstract class AdServicesDebuggableTestCase extends AdServicesCtsTestCase {

    // TODO(b/299104530): add more stuff from AndroidTestDebuggable.xml, make sure all tests
    // extend this, then clean up that XML
    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.forAllApisEnabledTests()
                .setCompatModeFlags()
                .setPpapiAppAllowList(sPackageName);
    }
}
