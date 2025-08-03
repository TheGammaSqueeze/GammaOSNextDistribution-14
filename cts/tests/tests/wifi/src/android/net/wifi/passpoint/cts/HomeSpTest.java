/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.passpoint.cts;

import android.net.wifi.cts.WifiJUnit3TestBase;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
public class HomeSpTest extends WifiJUnit3TestBase {
    /**
     * Verify that the anyOis set and get APIs work as expected
     */
    public void testAnyOis() throws Exception {
        HomeSp homeSp = new HomeSp();
        assertNull(homeSp.getMatchAnyOis());
        final long[] ois = new long[]{0x1000, 0x2000};
        homeSp.setMatchAnyOis(ois);
        final long[] profileOis = homeSp.getMatchAnyOis();
        assertTrue(Arrays.equals(ois, profileOis));
    }

    /**
     * Verify that the allOis set and get APIs work as expected
     */
    public void testAllOis() throws Exception {
        HomeSp homeSp = new HomeSp();
        assertNull(homeSp.getMatchAllOis());
        final long[] ois = new long[]{0x1000, 0x2000};
        homeSp.setMatchAllOis(ois);
        final long[] profileOis = homeSp.getMatchAllOis();
        assertTrue(Arrays.equals(ois, profileOis));
    }

    /**
     * Verify that the OtherHomePartners set and get APIs work as expected
     */
    public void testOtherHomePartners() throws Exception {
        HomeSp homeSp = new HomeSp();
        final Collection<String> homePartners = Arrays.asList("other-provider1", "other-provider2");
        homeSp.setOtherHomePartnersList(homePartners);
        final Collection<String> profileHomePartners = homeSp.getOtherHomePartnersList();
        assertTrue(homePartners.equals(profileHomePartners));
    }
}