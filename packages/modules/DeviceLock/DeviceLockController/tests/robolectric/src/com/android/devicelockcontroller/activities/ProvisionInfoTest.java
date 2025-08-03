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

package com.android.devicelockcontroller.activities;

import static com.google.common.truth.Truth.assertThat;

import com.android.devicelockcontroller.activities.ProvisionInfo.ProvisionInfoType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProvisionInfoTest {

    private static final int DRAWABLE_ID_1 = 0;
    private static final int DRAWABLE_ID_2 = 1;
    private static final int TEXT_ID_1 = 10;
    private static final int TEXT_ID_2 = 11;
    private static final int TYPE_1 = ProvisionInfoType.REGULAR;
    private static final int TYPE_2 = ProvisionInfoType.TERMS_AND_CONDITIONS;

    @Test
    public void equals_withOneDifferentFieldValue_returnsFalse() {
        ProvisionInfo p1 = new ProvisionInfo(DRAWABLE_ID_1, TEXT_ID_1, TYPE_1);
        ProvisionInfo p10 = new ProvisionInfo(DRAWABLE_ID_2, TEXT_ID_1, TYPE_1);
        ProvisionInfo p12 = new ProvisionInfo(DRAWABLE_ID_1, TEXT_ID_2, TYPE_1);
        ProvisionInfo p13 = new ProvisionInfo(DRAWABLE_ID_1, TEXT_ID_1, TYPE_2);

        assertThat(p1).isNotEqualTo(p10);
        assertThat(p1.hashCode()).isNotEqualTo(p10.hashCode());
        assertThat(p1).isNotEqualTo(p12);
        assertThat(p1.hashCode()).isNotEqualTo(p12.hashCode());
        assertThat(p1).isNotEqualTo(p13);
        assertThat(p1.hashCode()).isNotEqualTo(p13.hashCode());
    }

    @Test
    public void equals_withSameFieldValues_returnsTrue() {
        ProvisionInfo p1 = new ProvisionInfo(DRAWABLE_ID_1, TEXT_ID_1, TYPE_1);
        ProvisionInfo p2 = new ProvisionInfo(DRAWABLE_ID_1, TEXT_ID_1, TYPE_1);

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
