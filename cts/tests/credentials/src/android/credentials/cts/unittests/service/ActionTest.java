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

package android.credentials.cts.unittests.service;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.slice.Slice;
import android.credentials.cts.unittests.TestUtils;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.Action;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class ActionTest {

    private final Slice mSlice = new Slice.Builder(Uri.parse("foo://bar"), null).addText(
            "some text", null, List.of(Slice.HINT_TITLE)).build();

    @Test
    public void testConstructor_nullSlice_throwsNPE() {
        assertThrows(NullPointerException.class, () -> new Action(null));
    }

    @Test
    public void testConstructor_success() {
        final Action action = new Action(mSlice);
        assertThat(action.getSlice()).isSameInstanceAs(mSlice);
    }

    @Test
    public void testWriteToParcel_success() {
        final Action action1 = new Action(mSlice);

        final Action action2 = TestUtils.cloneParcelable(action1);
        TestUtils.assertEquals(action2.getSlice(), action1.getSlice());
    }
}
