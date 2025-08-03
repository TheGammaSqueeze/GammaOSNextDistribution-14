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

package com.android.server.wifi.entitlement.http;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unit tests for {@link StreamUtils}.
 */
@SmallTest
public class StreamUtilsTest extends WifiBaseTest {
    @Test
    public void inputStreamToString() throws Exception {
        String expectedString = "expected";
        try (InputStream in = new ByteArrayInputStream(expectedString.getBytes(UTF_8))) {
            String resultString = StreamUtils.inputStreamToString(in);
            assertThat(resultString).isEqualTo(expectedString);
        }
    }

    @Test
    public void inputStreamToString_nullInputStream_shouldThrowException() {
        assertThrows(IOException.class, () -> StreamUtils.inputStreamToString(null));
    }

    @Test
    public void inputStreamToString_readThrowsIOException_shouldThrowException() throws Exception {
        InputStream in = mock(InputStream.class);
        when(in.read(any())).thenThrow(new IOException());

        assertThrows(IOException.class, () -> StreamUtils.inputStreamToString(in));
    }

    @Test
    public void inputStreamToStringSafe() throws Exception {
        String expectedString = "expected";
        try (InputStream in = new ByteArrayInputStream(expectedString.getBytes(UTF_8))) {
            String resultString = StreamUtils.inputStreamToStringSafe(in);
            assertThat(resultString).isEqualTo(expectedString);
        }
    }

    @Test
    public void inputStreamToStringSafe_nullInputStream_shouldReturnEmpty() throws Exception {
        String resultString = StreamUtils.inputStreamToStringSafe(null);

        assertThat(resultString).isEmpty();
    }

    @Test
    public void inputStreamToStringSafe_readThrowsIOException_shouldReturnEmpty() throws Exception {
        InputStream in = mock(InputStream.class);
        when(in.read(any())).thenThrow(new IOException());

        String resultString = StreamUtils.inputStreamToStringSafe(in);

        assertThat(resultString).isEmpty();
    }

    @Test
    public void inputStreamToGunzipString() throws Exception {
        String expectedString = "expected";
        try (InputStream in = new ByteArrayInputStream(HttpClient.toGzipBytes(expectedString))) {
            String resultString = StreamUtils.inputStreamToGunzipString(in);
            assertThat(resultString).isEqualTo(expectedString);
        }
    }
}
