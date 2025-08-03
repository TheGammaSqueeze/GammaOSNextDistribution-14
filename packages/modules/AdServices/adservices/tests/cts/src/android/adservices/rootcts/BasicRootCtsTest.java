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

package android.adservices.rootcts;

import static com.google.common.truth.Truth.assertThat;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Test;

// TODO(b/300182869): Delete test and replace with something useful.
public class BasicRootCtsTest {

    @Test
    public void testIamRoot() {
        assertThat(hasRootAccess()).isTrue();
    }

    private static boolean hasRootAccess() {
        try {
            return ShellUtils.runShellCommand("su root ls").getBytes() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
