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
package android.adservices.appsetid;

import static android.adservices.appsetid.GetAppSetIdResult.SCOPE_APP;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;

import org.junit.Test;

/** Unit tests for {@link android.adservices.appsetid.AppSetId} */
@RequiresSdkLevelAtLeastS()
@SmallTest
public final class AppSetIdTest extends AdServicesUnitTestCase {
    private static final String TEST_APP_SET_ID = "TEST_APP_SET_ID";

    @Test
    public void testAppSetId() {
        AppSetId appSetId1 = new AppSetId(TEST_APP_SET_ID, SCOPE_APP);

        // Validate the returned response is same to what we created
        expect.that(appSetId1.getId()).isEqualTo(TEST_APP_SET_ID);
        expect.that(appSetId1.getScope()).isEqualTo(SCOPE_APP);

        // Validate equals().
        AppSetId appSetId2 = new AppSetId(TEST_APP_SET_ID, SCOPE_APP);
        expect.that(appSetId1).isEqualTo(appSetId2);

        // Validate hashcode().
        expect.that(appSetId1.hashCode()).isEqualTo(appSetId2.hashCode());
    }
}
