/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.qc;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsQCRegistryTest {
    @Before
    public void setUp() {
        SettingsQCRegistry.sUriToQC.clear();
        SettingsQCRegistry.sUriToQC.put(QCTestUtils.URI, TestSettingsQCItem.class);
    }

    @Test
    public void getQCClassByUri_validUri_returnsClass() {
        Class<? extends SettingsQCItem> clazz = SettingsQCRegistry.getQCClassByUri(QCTestUtils.URI);
        assertThat(clazz).isNotNull();
    }

    @Test
    public void getQCClassByUri_invalidUri_returnsNull() {
        Class<? extends SettingsQCItem> clazz = SettingsQCRegistry.getQCClassByUri(Uri.EMPTY);
        assertThat(clazz).isNull();
    }

    @Test
    public void removeParameterFromUri_removesParameters() {
        Uri uri = SettingsQCRegistry.removeParameterFromUri(QCTestUtils.URI);
        assertThat(uri).isEqualTo(QCTestUtils.URI);
    }

    @Test
    public void isValidUri_validUri_returnsTrue() {
        assertThat(SettingsQCRegistry.isValidUri(QCTestUtils.URI)).isTrue();
    }

    @Test
    public void isValidUri_invalidUri_returnsFalse() {
        assertThat(SettingsQCRegistry.isValidUri(Uri.EMPTY)).isFalse();
    }

    @Test
    public void isValidAction_validAction_returnsTrue() {
        assertThat(SettingsQCRegistry.isValidAction(QCTestUtils.URI.toString())).isTrue();
    }

    @Test
    public void isValidAction_invalidAction_returnsFalse() {
        assertThat(SettingsQCRegistry.isValidAction(Uri.EMPTY.toString())).isFalse();
    }
}
