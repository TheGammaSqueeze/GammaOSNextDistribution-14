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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@RunWith(JUnit4.class)
public final class ParcelWrapperTest {
    @Test public void testRoundTrip() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putInt("x", 5);

        ParcelWrapper<Bundle> wrapper = new ParcelWrapper<>(bundle);

        try (ByteArrayOutputStream bstream = new ByteArrayOutputStream();
                ObjectOutputStream ostream = new ObjectOutputStream(bstream)) {
            ostream.writeObject(wrapper);

            byte[] serializedBytes = bstream.toByteArray();

            try (ByteArrayInputStream bStream2 = new ByteArrayInputStream(serializedBytes);
                    ObjectInputStream oStream2 = new ObjectInputStream(bStream2)) {
                ParcelWrapper<Bundle> wrapper2 = (ParcelWrapper<Bundle>) oStream2.readObject();

                assertEquals(5, wrapper2.get(Bundle.CREATOR).getInt("x"));
            }
        }
    }

    @Test public void testNullInput() throws Exception {
        ParcelWrapper<Bundle> wrapper = new ParcelWrapper<>((Bundle) null);

        try (ByteArrayOutputStream bstream = new ByteArrayOutputStream();
                ObjectOutputStream ostream = new ObjectOutputStream(bstream)) {
            ostream.writeObject(wrapper);

            byte[] serializedBytes = bstream.toByteArray();

            try (ByteArrayInputStream bStream2 = new ByteArrayInputStream(serializedBytes);
                    ObjectInputStream oStream2 = new ObjectInputStream(bStream2)) {
                ParcelWrapper<Bundle> wrapper2 = (ParcelWrapper<Bundle>) oStream2.readObject();

                assertNull(wrapper2.get(Bundle.CREATOR));
            }
        }
    }
}
