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

package com.android.federatedcompute.services.data;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ODPAuthorizationTokenTest {
    private static final String TOKEN = "b3c4dc4a-768b-415d-8adb-d3aa2206b7bb";

    private static final String OWNER_IDENTIFIER = "atp1";

    private static final String OWNER_IDENTIFIER2 = "atp2";

    private static final long NOW = 1000000L;

    private static final long ONE_HOUR = 60 * 60 * 60 * 1000L;

    @Test
    public void testBuilderAndEquals() {
        ODPAuthorizationToken token1 = new ODPAuthorizationToken.Builder()
                .setOwnerIdentifier(OWNER_IDENTIFIER)
                .setAuthorizationToken(TOKEN)
                .setCreationTime(NOW)
                .setExpiryTime(NOW + ONE_HOUR).build();
        ODPAuthorizationToken token2 = new ODPAuthorizationToken.Builder()
                .setOwnerIdentifier(OWNER_IDENTIFIER)
                .setAuthorizationToken(TOKEN)
                .setCreationTime(NOW)
                .setExpiryTime(NOW + ONE_HOUR).build();

        assertEquals(token1, token2);

        ODPAuthorizationToken token3 = new ODPAuthorizationToken.Builder()
                .setOwnerIdentifier(OWNER_IDENTIFIER2)
                .setAuthorizationToken(TOKEN)
                .setCreationTime(NOW)
                .setExpiryTime(NOW + ONE_HOUR).build();

        assertNotEquals(token3, token1);
        assertNotEquals(token3, token2);
    }

    @Test
    public void testBuildTwiceThrows() {
        ODPAuthorizationToken.Builder builder = new ODPAuthorizationToken.Builder()
                .setOwnerIdentifier(OWNER_IDENTIFIER)
                .setAuthorizationToken(TOKEN)
                .setCreationTime(NOW)
                .setExpiryTime(NOW + ONE_HOUR);
        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.build());

    }
}
