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

package android.adservices.cts;

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_1;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_1;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_SIGNALS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

public class AdSelectionFromOutcomesConfigTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidAdSelectionFromOutcomesConfigSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertEquals(1, config.getAdSelectionIds().size());
        assertEquals(SAMPLE_AD_SELECTION_ID_1, (long) config.getAdSelectionIds().get(0));
        assertEquals(SAMPLE_SELECTION_SIGNALS, config.getSelectionSignals());
        assertEquals(SAMPLE_SELECTION_LOGIC_URI_1, config.getSelectionLogicUri());
    }

    @Test
    public void testParcelValidInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        Parcel p = Parcel.obtain();
        config.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionFromOutcomesConfig fromParcel =
                AdSelectionFromOutcomesConfig.CREATOR.createFromParcel(p);

        assertEquals(config.getAdSelectionIds(), fromParcel.getAdSelectionIds());
        assertEquals(config.getSelectionSignals(), fromParcel.getSelectionSignals());
        assertEquals(config.getSelectionLogicUri(), fromParcel.getSelectionLogicUri());
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetAdOutcomeIds() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionFromOutcomesConfig.Builder()
                            .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                            .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                            .build();
                });
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetSelectionSignals() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionFromOutcomesConfig.Builder()
                            .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                            .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                            .build();
                });
    }

    @Test
    public void testBuildAdSelectionFromOutcomesConfigUnsetSelectionUri() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionFromOutcomesConfig.Builder()
                            .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                            .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testAdSelectionFromOutcomesConfigDescribeContents() {
        AdSelectionFromOutcomesConfig obj =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertEquals(obj.describeContents(), 0);
    }

    @Test
    public void testEqualInputsHaveSameHashCode() {
        AdSelectionFromOutcomesConfig obj1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig obj2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualInputsHaveDifferentHashCode() {
        AdSelectionFromOutcomesConfig obj1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig obj2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_2));
        AdSelectionFromOutcomesConfig obj3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SAMPLE_SELECTION_LOGIC_URI_2);

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
