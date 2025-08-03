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

package com.android.server.nearby.util.identity;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class CallerIdentityTest {
    private static final int UID = 100;
    private static final int PID = 10002;
    private static final String PACKAGE_NAME = "package_name";
    private static final String ATTRIBUTION_TAG = "attribution_tag";

    @Test
    public void testToString() {
        CallerIdentity callerIdentity =
                CallerIdentity.forTest(UID, PID, PACKAGE_NAME, ATTRIBUTION_TAG);
        assertThat(callerIdentity.toString()).isEqualTo("100/package_name[attribution_tag]");
        assertThat(callerIdentity.isSystemServer()).isFalse();
    }

    @Test
    public void testHashCode() {
        CallerIdentity callerIdentity =
                CallerIdentity.forTest(UID, PID, PACKAGE_NAME, ATTRIBUTION_TAG);
        CallerIdentity callerIdentity1 =
                CallerIdentity.forTest(UID, PID, PACKAGE_NAME, ATTRIBUTION_TAG);
        assertThat(callerIdentity.hashCode()).isEqualTo(callerIdentity1.hashCode());
    }
}
