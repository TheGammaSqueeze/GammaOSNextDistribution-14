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

package com.android.server.uwb.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ObjectIdentifierTest {
    @Test
    public void equalValue() {
        ObjectIdentifier oid1 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010203"));
        ObjectIdentifier oid2 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010203"));

        assertThat(oid1).isEqualTo(oid2);
    }

    @Test
    public void notEqualValue() {
        ObjectIdentifier oid1 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010203"));
        ObjectIdentifier oid2 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010202"));

        assertThat(oid1).isNotEqualTo(oid2);
    }

    @Test
    public void sameInstance() {
        ObjectIdentifier oid1 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010203"));
        ObjectIdentifier oid2 = oid1;

        assertThat(oid1).isEqualTo(oid2);
    }

    @Test
    public void differentClasses() {
        ObjectIdentifier oid1 = ObjectIdentifier.fromBytes(
                DataTypeConversionUtil.hexStringToByteArray("010203"));

        assertThat(oid1).isNotEqualTo(0x0102);
    }
}
