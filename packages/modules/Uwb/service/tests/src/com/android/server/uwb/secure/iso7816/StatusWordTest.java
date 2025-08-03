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

package com.android.server.uwb.secure.iso7816;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Test cases for {@link StatusWord}. */
public class StatusWordTest {

    @Test
    public void testFromInt_validStatusWord() {
        StatusWord sw = StatusWord.fromInt(0x9000);
        assertThat(sw).isEqualTo(StatusWord.SW_NO_ERROR);
    }

    @Test
    public void testToBytes_noError() {
        byte[] actual = StatusWord.SW_NO_ERROR.toBytes();
        byte[] expected = {(byte) 0x90, (byte) 0x00};
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testToBytes_unknown() {
        byte[] actual = StatusWord.fromInt(0xDEAD).toBytes();
        byte[] expected = {(byte) 0xDE, (byte) 0xAD};
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testFromInt_tooManyBits() {
        for (int sw : new int[] {-1, -70000, 70000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            boolean success = false;
            try {
                StatusWord.fromInt(sw);
            } catch (IllegalArgumentException e) {
                success = true;
            } finally {
                assertThat(success).isTrue();
            }
        }
    }

    @Test
    public void testFromInt_goodNumberOfBits() {
        for (int sw = 0; sw <= 0xfffe; sw += 100) {
            StatusWord.fromInt(sw);
        }
        StatusWord.fromInt(0xffff);
    }

    @Test
    public void testIsKnown() {
        assertThat(StatusWord.fromInt(0x9000).isKnown()).isTrue();
        assertThat(StatusWord.SW_NO_ERROR.isKnown()).isTrue();
        assertThat(StatusWord.fromInt(0x1234).isKnown()).isFalse();
    }

    @Test
    public void testCheckClassInvariant() throws IllegalAccessException {
        // must not have public constructors.
        assertThat(StatusWord.class.getConstructors()).isEmpty();

        // all creators must be static and must not allow the caller to set a message.
        int numCreators = 0;
        for (Method method : StatusWord.class.getMethods()) {
            if (method.getReturnType().isAssignableFrom(StatusWord.class)) {
                numCreators++;
                int modifiers = method.getModifiers();
                assertThat(Modifier.isStatic(modifiers)).isTrue();
                Class<?>[] params = method.getParameterTypes();
                assertThat(params).hasLength(1);
                assertThat(params[0]).isEqualTo(Integer.TYPE);
            }
        }
        assertThat(numCreators).isEqualTo(1);

        List<StatusWord> reflectivelyFoundKnownStatusWords = new ArrayList<>();
        for (Field field : StatusWord.class.getFields()) {
            int mod = field.getModifiers();
            if (field.getType().equals(StatusWord.class)
                    && Modifier.isPublic(mod)
                    && Modifier.isStatic(mod)
                    && Modifier.isFinal(mod)) {
                reflectivelyFoundKnownStatusWords.add((StatusWord) field.get(null));
            }
        }

        Set<StatusWord> expected = new HashSet<>(reflectivelyFoundKnownStatusWords);
        assertThat(StatusWord.ALL_KNOWN_STATUS_WORDS).isEqualTo(expected);
    }
}
