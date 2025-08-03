/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.signature.cts.api.test;

import android.signature.cts.DexMember;
import java.util.Set;

public class HiddenApiTest extends android.signature.cts.api.HiddenApiTest {

    /**
     * Override to match only those members that specify both test-api and blocked.
     */
    @Override
    protected boolean shouldTestMember(DexMember member) {
        Set<String> flags = member.getHiddenapiFlags();
        return flags.contains("test-api") && flags.contains("blocked");
    }
}
