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

package android.autofillservice.cts.unittests;

import static android.service.autofill.SavedDatasetsInfo.TYPE_OTHER;
import static android.service.autofill.SavedDatasetsInfo.TYPE_PASSWORDS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.service.autofill.SavedDatasetsInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link SavedDatasetsInfo}.
 */
@RunWith(AndroidJUnit4.class)
public class SavedDatasetsInfoTest {

    @Test
    public void testType_passwords() {
        assertThat(new SavedDatasetsInfo(TYPE_PASSWORDS, 0).getType()).isEqualTo(TYPE_PASSWORDS);
    }

    @Test
    public void testType_other() {
        assertThat(new SavedDatasetsInfo(TYPE_OTHER, 0).getType()).isEqualTo(TYPE_OTHER);
    }

    @Test
    public void testCount_zero() {
        assertThat(new SavedDatasetsInfo(TYPE_PASSWORDS, 0).getCount()).isEqualTo(0);
    }

    @Test
    public void testCount_greaterThanZero() {
        assertThat(new SavedDatasetsInfo(TYPE_OTHER, 25).getCount()).isEqualTo(25);
    }

    @Test
    public void testCount_negative_throws() {
        assertThrows(() -> new SavedDatasetsInfo(TYPE_PASSWORDS, -1));
    }

    @Test
    public void testEquals_true() {
        SavedDatasetsInfo info1 = new SavedDatasetsInfo(TYPE_PASSWORDS, 5);
        SavedDatasetsInfo info2 = new SavedDatasetsInfo(TYPE_PASSWORDS, 5);

        assertThat(info1).isEqualTo(info2);
    }

    @Test
    public void testEquals_differentType_false() {
        SavedDatasetsInfo info1 = new SavedDatasetsInfo(TYPE_PASSWORDS, 5);
        SavedDatasetsInfo info2 = new SavedDatasetsInfo(TYPE_OTHER, 5);

        assertThat(info1).isNotEqualTo(info2);
    }

    @Test
    public void testEquals_differentCount_false() {
        SavedDatasetsInfo info1 = new SavedDatasetsInfo(TYPE_PASSWORDS, 5);
        SavedDatasetsInfo info2 = new SavedDatasetsInfo(TYPE_PASSWORDS, 9);

        assertThat(info1).isNotEqualTo(info2);
    }
}
