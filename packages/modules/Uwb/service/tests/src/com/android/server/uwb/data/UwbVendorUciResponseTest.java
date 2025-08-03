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

package com.android.server.uwb.data;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Test;

public class UwbVendorUciResponseTest {
    @Test
    public void equalValues() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));

        assertThat(response1).isEqualTo(response2);
    }

    @Test
    public void sameInstance() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = response1;

        assertThat(response1).isEqualTo(response2);
    }

    @Test
    public void notEqualStatus() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x1,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));

        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    public void notEqualGid() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 2,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));

        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    public void notEqualOid() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 1,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));

        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    public void notEqualPayload() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));
        UwbVendorUciResponse response2 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0B"));

        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    public void differentClasses() {
        UwbVendorUciResponse response1 = new UwbVendorUciResponse(
                /* status= */ (byte) 0x0,
                /* gid= */ 1,
                /* oid= */ 2,
                /* payload=*/ DataTypeConversionUtil.hexStringToByteArray("0A0B0A"));

        assertThat(response1).isNotEqualTo(0);
    }
}
