// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.downloader;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.android.downloader.DataUrl.DataUrlException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for DataUrl parsing. */
@RunWith(JUnit4.class)
public class DataUrlTest {
  @Test
  public void invalidScheme() {
    assertThrows(DataUrlException.class, () -> DataUrl.parseFromString("http://example.com"));
  }

  @Test
  public void invalidSyntax() {
    assertThrows(DataUrlException.class, () -> DataUrl.parseFromString("data:foobar"));
  }

  @Test
  public void missingEncoding() {
    assertThrows(DataUrlException.class, () -> DataUrl.parseFromString("data:text/plain,foobar"));
  }

  @Test
  public void invalidEncoding() {
    assertThrows(
        DataUrlException.class, () -> DataUrl.parseFromString("data:text/plain;base32,foobar"));
  }

  @Test
  public void invalidBase64Data() {
    // Note that '*' is not a valid character in base64.
    assertThrows(
        DataUrlException.class, () -> DataUrl.parseFromString("data:text/plain;base64,foobar*"));
  }

  @Test
  public void validData() {
    // Note: 'Zm9vYmFy' is the base64-encoding of 'foobar'.
    DataUrl dataUrl = DataUrl.parseFromString("data:text/plain;base64,Zm9vYmFy");
    assertThat(dataUrl.data()).isEqualTo("foobar".getBytes(UTF_8));
    assertThat(dataUrl.mimeType()).isEqualTo("text/plain");
  }

  @Test
  public void validData_notUrl() {
    // Note: 'Zm9vLiw/YmFy' is the base64-encoding of 'foo.,?bar'. Because there's a slash in the
    // base64 payload, this isn't a url-safe base64 encoding, so this test will verify we fall
    // back to regular base64 encoding instead of just url-safe encoding.
    DataUrl dataUrl = DataUrl.parseFromString("data:text/plain;base64,Zm9vLiw/YmFy");
    assertThat(dataUrl.data()).isEqualTo("foo.,?bar".getBytes(UTF_8));
    assertThat(dataUrl.mimeType()).isEqualTo("text/plain");
  }
}
