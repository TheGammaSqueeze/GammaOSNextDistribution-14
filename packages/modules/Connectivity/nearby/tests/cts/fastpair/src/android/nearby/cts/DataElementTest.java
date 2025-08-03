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

package android.nearby.cts;

import static android.nearby.DataElement.DataType.PRIVATE_IDENTITY;
import static android.nearby.DataElement.DataType.PROVISIONED_IDENTITY;
import static android.nearby.DataElement.DataType.PUBLIC_IDENTITY;
import static android.nearby.DataElement.DataType.SALT;
import static android.nearby.DataElement.DataType.TRUSTED_IDENTITY;
import static android.nearby.DataElement.DataType.TX_POWER;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.DataElement;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class DataElementTest {

    private static final int KEY = 1;
    private static final byte[] VALUE = new byte[]{1, 1, 1, 1};

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBuilder() {
        DataElement dataElement = new DataElement(KEY, VALUE);

        assertThat(dataElement.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(dataElement.getValue(), VALUE)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteParcel() {
        DataElement dataElement = new DataElement(KEY, VALUE);

        Parcel parcel = Parcel.obtain();
        dataElement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DataElement elementFromParcel = DataElement.CREATOR.createFromParcel(
                parcel);
        parcel.recycle();

        assertThat(elementFromParcel.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(elementFromParcel.getValue(), VALUE)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void describeContents() {
        DataElement dataElement = new DataElement(KEY, VALUE);
        assertThat(dataElement.describeContents()).isEqualTo(0);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCreatorNewArray() {
        DataElement[] elements =
                DataElement.CREATOR.newArray(2);
        assertThat(elements.length).isEqualTo(2);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEquals() {
        DataElement dataElement = new DataElement(KEY, VALUE);
        DataElement dataElement2 = new DataElement(KEY, VALUE);

        assertThat(dataElement.equals(dataElement2)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIsIdentity() {
        DataElement privateIdentity = new DataElement(PRIVATE_IDENTITY, new byte[]{1, 2, 3});
        DataElement trustedIdentity = new DataElement(TRUSTED_IDENTITY, new byte[]{1, 2, 3});
        DataElement publicIdentity = new DataElement(PUBLIC_IDENTITY, new byte[]{1, 2, 3});
        DataElement provisionedIdentity =
                new DataElement(PROVISIONED_IDENTITY, new byte[]{1, 2, 3});

        DataElement salt = new DataElement(SALT, new byte[]{1, 2, 3});
        DataElement txPower = new DataElement(TX_POWER, new byte[]{1, 2, 3});

        assertThat(privateIdentity.isIdentityDataType()).isTrue();
        assertThat(trustedIdentity.isIdentityDataType()).isTrue();
        assertThat(publicIdentity.isIdentityDataType()).isTrue();
        assertThat(provisionedIdentity.isIdentityDataType()).isTrue();
        assertThat(salt.isIdentityDataType()).isFalse();
        assertThat(txPower.isIdentityDataType()).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_notEquals() {
        DataElement dataElement = new DataElement(KEY, VALUE);
        DataElement dataElement2 = new DataElement(KEY, new byte[]{1, 2, 1, 1});
        DataElement dataElement3 = new DataElement(6, VALUE);

        assertThat(dataElement.equals(dataElement2)).isFalse();
        assertThat(dataElement.equals(dataElement3)).isFalse();
    }
}
