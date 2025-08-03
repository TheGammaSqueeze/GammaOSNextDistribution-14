/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permissionui.cts

import com.android.compatibility.common.util.CtsDownstreamingTest
import org.junit.Test

// NoOp test class so that at least one GTS test passes on all platforms.
// b/235606392 for reference. Will be removed once we move all downstreaming
// CtsPermissionUiTestCases to GTS.
@CtsDownstreamingTest
class PermissionNoOpGtsTest {

    @Test fun shouldAlwaysPass() {}
}
