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

package android.car.cts.builtin.os;

import static com.google.common.truth.Truth.assertThat;

import android.car.builtin.os.ParcelHelper;
import android.os.Parcel;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ParcelHelperTest {

    private static final String TAG = ParcelHelperTest.class.getSimpleName();
    private final String[] mInitData = {"Hello, ", "Android", "Auto", "!"};

    @Test
    public void testBlobAccess() {
        String blobContent = "Hello, Android Auto!";
        Parcel p = Parcel.obtain();

        // setup
        byte[] inBlob = blobContent.getBytes();
        ParcelHelper.writeBlob(p, inBlob);
        p.setDataPosition(0);

        // execution
        byte[] outBlob = ParcelHelper.readBlob(p);

        // assertion
        assertThat(outBlob).isEqualTo(inBlob);
    }

    @Test
    public void testArraySetAccess() {
        ArraySet<Object> inputSet = new ArraySet<>(mInitData);
        ArraySet<?> outputSet = null;
        Parcel p = Parcel.obtain();

        // setup
        ArraySet emptySet = ParcelHelper.readArraySet(p, /* loader = */ null);
        assertThat(emptySet.size()).isEqualTo(0);
        ParcelHelper.writeArraySet(p, inputSet);
        p.setDataPosition(0);

        // execution
        outputSet = ParcelHelper.readArraySet(p, String.class.getClassLoader());

        // assertion
        assertThat(outputSet).containsExactlyElementsIn(inputSet);
    }

    @Test
    public void testStringArrayAccess() {
        String[] inputArray = mInitData;
        Parcel p = Parcel.obtain();

        // setup
        p.writeStringArray(inputArray);
        p.setDataPosition(0);

        // execution
        String[] outputArray = ParcelHelper.readStringArray(p);

        // assertion
        assertThat(outputArray).isEqualTo(inputArray);
    }
}
