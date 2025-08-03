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

package android.adservices.adselection;

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_AD_SELECTION_ID_2;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

public class AdSelectionFromOutcomesInputTest {
    private static final String CALLER_PACKAGE_NAME = "com.app.test";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidAdSelectionFromOutcomesInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionFromOutcomesInput inputParams = createAdSelectionFromOutcomesInput(config);

        assertEquals(config, inputParams.getAdSelectionFromOutcomesConfig());
        assertEquals(CALLER_PACKAGE_NAME, inputParams.getCallerPackageName());
    }

    @Test
    public void testParcelValidInputSuccess() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionFromOutcomesInput inputParams = createAdSelectionFromOutcomesInput(config);

        Parcel p = Parcel.obtain();
        inputParams.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionFromOutcomesInput fromParcel =
                AdSelectionFromOutcomesInput.CREATOR.createFromParcel(p);

        assertEquals(config, inputParams.getAdSelectionFromOutcomesConfig());
        assertEquals(inputParams.getCallerPackageName(), fromParcel.getCallerPackageName());
    }

    @Test
    public void testAdSelectionFromOutcomesInputUnsetAdOutcomesBuildFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionFromOutcomesInput.Builder()
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testAdSelectionFromOutcomesInputUnsetCallerPackageNameBuildFailure() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionFromOutcomesInput.Builder()
                            .setAdSelectionFromOutcomesConfig(config)
                            .build();
                });
    }

    @Test
    public void testAdSelectionFromOutcomesInputDescribeContents() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesInput obj = createAdSelectionFromOutcomesInput(config);

        assertEquals(obj.describeContents(), 0);
    }

    @Test
    public void testEqualInputsHaveSameHashCode() {
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesInput obj1 = createAdSelectionFromOutcomesInput(config);
        AdSelectionFromOutcomesInput obj2 = createAdSelectionFromOutcomesInput(config);

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualInputsHaveDifferentHashCode() {
        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(SAMPLE_AD_SELECTION_ID_2));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SAMPLE_SELECTION_LOGIC_URI_2);

        AdSelectionFromOutcomesInput obj1 = createAdSelectionFromOutcomesInput(config1);
        AdSelectionFromOutcomesInput obj2 = createAdSelectionFromOutcomesInput(config2);
        AdSelectionFromOutcomesInput obj3 = createAdSelectionFromOutcomesInput(config3);

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }

    private AdSelectionFromOutcomesInput createAdSelectionFromOutcomesInput(
            AdSelectionFromOutcomesConfig config) {
        return new AdSelectionFromOutcomesInput.Builder()
                .setAdSelectionFromOutcomesConfig(config)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }
}
