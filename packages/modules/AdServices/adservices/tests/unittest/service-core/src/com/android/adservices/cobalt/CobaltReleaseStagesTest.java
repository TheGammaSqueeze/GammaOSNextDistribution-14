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

package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.CobaltReleaseStages.getReleaseStage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.cobalt.ReleaseStage;

import org.junit.Rule;
import org.junit.Test;

public final class CobaltReleaseStagesTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void defaultStage_isGa() throws Exception {
        assertThat(getReleaseStage(CobaltConstants.DEFAULT_RELEASE_STAGE))
                .isEqualTo(ReleaseStage.GA);
    }

    @Test
    public void knownStagesConverted() throws Exception {
        assertThat(getReleaseStage("DEBUG")).isEqualTo(ReleaseStage.DEBUG);
        assertThat(getReleaseStage("FISHFOOD")).isEqualTo(ReleaseStage.FISHFOOD);
        assertThat(getReleaseStage("DOGFOOD")).isEqualTo(ReleaseStage.DOGFOOD);
        assertThat(getReleaseStage("OPEN_BETA")).isEqualTo(ReleaseStage.OPEN_BETA);
        assertThat(getReleaseStage("GA")).isEqualTo(ReleaseStage.GA);
    }

    @Test
    public void unknownStages_throwsCobaltInitializationException() throws Exception {
        assertThrows(
                CobaltInitializationException.class,
                () -> getReleaseStage("RELEASE_STAGE_UNKNOWN"));
        assertThrows(CobaltInitializationException.class, () -> getReleaseStage("other"));
    }

    @Test
    public void emptyStage_throwsCobaltInitializationException() throws Exception {
        assertThrows(CobaltInitializationException.class, () -> getReleaseStage(""));
    }

    @Test
    public void nullStage_throwsNullPointerException() throws Exception {
        assertThrows(NullPointerException.class, () -> getReleaseStage(null));
    }
}
