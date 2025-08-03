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

package com.android.server.sdksandbox.verifier;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.sdksandbox.protobuf.InvalidProtocolBufferException;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.Map;

public class ApiAllowlistProviderUnitTest {

    @Test
    public void loadPlatformApiAllowlist_succeeds() throws Exception {
        ApiAllowlistProvider apiAllowlistProvider = new ApiAllowlistProvider();
        Map<Long, AllowedApisList> loaded = apiAllowlistProvider.loadPlatformApiAllowlist();

        assertThat(loaded).isNotNull();
    }

    @Test
    public void loadPlatformApiAllowlist_throwsIOException() throws Exception {
        ApiAllowlistProvider apiAllowlistProvider =
                new ApiAllowlistProvider("resource_does_not_exist");

        assertThrows(
                FileNotFoundException.class, () -> apiAllowlistProvider.loadPlatformApiAllowlist());
    }

    @Test
    public void loadPlatformApiAllowlist_throwsInvalidPbException() throws Exception {
        ApiAllowlistProvider apiAllowlistProvider =
                new ApiAllowlistProvider("verification/corrupt_allowlist.binarypb");

        assertThrows(
                InvalidProtocolBufferException.class,
                () -> apiAllowlistProvider.loadPlatformApiAllowlist());
    }
}
