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

package com.android.server.uwb.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.profile.UuidBundleWrapper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UuidBundleWrapperTest {
    @Test
    public void testStatus() {
        UUID serviceInstanceID = new UUID(100, 50);
        UuidBundleWrapper
                status = new UuidBundleWrapper.Builder()
                .setServiceInstanceID(Optional.of(serviceInstanceID))
                .build();

        assertEquals(status.getServiceInstanceID().get(), serviceInstanceID);

        UuidBundleWrapper
                fromBundle = UuidBundleWrapper.fromBundle(status.toBundle());

        assertEquals(fromBundle.getServiceInstanceID().get(), serviceInstanceID);
    }

    @Test
    public void testEmpty() {
        UuidBundleWrapper
                status = new UuidBundleWrapper.Builder()
                .build();

        assertTrue(status.getServiceInstanceID().isEmpty());

        UuidBundleWrapper
                fromBundle = UuidBundleWrapper.fromBundle(status.toBundle());

        assertTrue(fromBundle.getServiceInstanceID().isEmpty());
    }
}
