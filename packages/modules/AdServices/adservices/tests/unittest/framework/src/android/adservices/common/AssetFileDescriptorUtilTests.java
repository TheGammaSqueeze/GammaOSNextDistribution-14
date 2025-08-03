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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.res.AssetFileDescriptor;

import com.android.adservices.concurrency.AdServicesExecutors;

import org.junit.Test;

import java.util.concurrent.ExecutorService;

public class AssetFileDescriptorUtilTests {
    private static final byte[] EXPECTED = new byte[] {1, 2, 3, 4};
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();

    @Test
    public void testSetupAssetFileDescriptorResponseReturnsCorrectResult() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                        EXPECTED, BLOCKING_EXECUTOR);
        byte[] result =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(assetFileDescriptor);
        assertThat(result).isEqualTo(EXPECTED);
        assertThat(result.length).isEqualTo(EXPECTED.length);
    }

    @Test
    public void testSetupAssetFileDescriptorResponseThrowsExceptionWhenBufferIsNull()
            throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                            null, BLOCKING_EXECUTOR);
                });
    }

    @Test
    public void testSetupAssetFileDescriptorResponseThrowsExceptionWhenExecutorServiceIsNull()
            throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(EXPECTED, null);
                });
    }

    @Test
    public void testReadAssetFileDescriptorIntoBufferThrowsExceptionWhenAssetFileDescriptorIsNull()
            throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(null);
                });
    }
}
