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

import static com.google.uwb.support.fira.FiraParams.PACS_PROFILE_SERVICE_ID;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.profile.ServiceProfile;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceProfileTest {
    @Test
    public void testServiceProfile() {
        int serviceID = PACS_PROFILE_SERVICE_ID;

        ServiceProfile config =
                new ServiceProfile.Builder()
                        .setServiceID(serviceID)
                        .build();

        assertEquals(config.getServiceID(), serviceID);

        ServiceProfile fromBundle = ServiceProfile.fromBundle(config.toBundle());

        assertEquals(fromBundle.getBundleVersion(), config.getBundleVersion());
        assertEquals(fromBundle.getServiceID(), serviceID);
    }
}
