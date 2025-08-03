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
package android.adservices.adid;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link android.adservices.adid.AdId} */
@SmallTest
public final class AdIdTest extends AdServicesUnitTestCase {
    private static final String TEST_AD_ID = "TEST_AD_ID";
    private static final boolean TEST_LIMIT_AD_TRACKING_ENABLED = true;

    @Test
    public void testAdId() {
        AdId adId1 = new AdId(TEST_AD_ID, TEST_LIMIT_AD_TRACKING_ENABLED);

        // Validate the returned response is same to what we created
        expect.that(adId1.getAdId()).isEqualTo(TEST_AD_ID);
        expect.that(adId1.isLimitAdTrackingEnabled()).isEqualTo(TEST_LIMIT_AD_TRACKING_ENABLED);

        // Validate equals().
        AdId adId2 = new AdId(TEST_AD_ID, TEST_LIMIT_AD_TRACKING_ENABLED);
        expect.that(adId1).isEqualTo(adId2);

        // Validate hashcode().
        expect.that(adId1.hashCode()).isEqualTo(adId2.hashCode());
    }
}
