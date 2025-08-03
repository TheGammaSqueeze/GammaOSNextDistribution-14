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

package com.android.queryable.util;

import static com.android.bedstead.nene.utils.ParcelTest.parcelAndUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcelable;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@RunWith(BedsteadJUnit4.class)
public class SerializableParcelWrapperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String KEY = "Key";
    private static final String STRING_VALUE = "String";

    private final Bundle mBundle = new Bundle();

    @Test
    public void serialize_deserialize_isEqual() throws Exception {
        mBundle.putString(KEY, STRING_VALUE);
        SerializableParcelWrapper<Bundle> serializableParcelWrapper
                = new SerializableParcelWrapper<>(mBundle);

        byte[] serializedBytes = serialize(serializableParcelWrapper);
        SerializableParcelWrapper<Bundle> unserializedWrapper =
                deserialize(serializedBytes, Bundle.class);

        assertThat(unserializedWrapper.get().getString(KEY))
                .isEqualTo(serializableParcelWrapper.get().getString(KEY));
    }

    private byte[] serialize(SerializableParcelWrapper<?> wrapper) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(baos)) {
            outputStream.writeObject(wrapper);
        }
        baos.close();
        return baos.toByteArray();
    }

    private <E extends Parcelable> SerializableParcelWrapper<E> deserialize(
            byte[] bytes, Class<E> type) throws Exception {
        try(ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream inputStream = new ObjectInputStream(bais)) {
            return (SerializableParcelWrapper<E>) inputStream.readObject();
        }
    }

    @Test
    public void equals_areEqual_returnsTrue() {
        Bundle bundle = new Bundle();

        SerializableParcelWrapper<Bundle> serializableParcelWrapper1
                = new SerializableParcelWrapper<>(bundle);
        SerializableParcelWrapper<Bundle> serializableParcelWrapper2
                = new SerializableParcelWrapper<>(bundle);

        assertThat(serializableParcelWrapper1.equals(serializableParcelWrapper2)).isTrue();
    }

    @Test
    public void equals_areNotEqual_returnsFalse() {
        Bundle bundle1 = new Bundle();
        Bundle bundle2 = new Bundle();
        bundle1.putString(KEY, STRING_VALUE);

        SerializableParcelWrapper<Bundle> serializableParcelWrapper1
                = new SerializableParcelWrapper<>(bundle1);
        SerializableParcelWrapper<Bundle> serializableParcelWrapper2
                = new SerializableParcelWrapper<>(bundle2);

        assertThat(serializableParcelWrapper1.equals(serializableParcelWrapper2)).isFalse();
    }

    @Test
    public void hashcode_matchesContainedHashcode() {
        SerializableParcelWrapper<Bundle> serializableParcelWrapper
                = new SerializableParcelWrapper<>(mBundle);

        assertThat(serializableParcelWrapper.hashCode()).isEqualTo(mBundle.hashCode());
    }

    @Test
    public void parcel_parcelsCorrectly() {
        mBundle.putString(KEY, STRING_VALUE);
        SerializableParcelWrapper<Bundle> serializableParcelWrapper =
                new SerializableParcelWrapper<>(mBundle);

        SerializableParcelWrapper<Bundle> unParceled =
                parcelAndUnparcel(SerializableParcelWrapper.class, serializableParcelWrapper);

        assertThat(unParceled.get().getString(KEY))
                .isEqualTo(serializableParcelWrapper.get().getString(KEY));
    }
}
