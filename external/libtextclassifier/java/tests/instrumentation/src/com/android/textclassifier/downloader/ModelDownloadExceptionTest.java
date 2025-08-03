/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.downloader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModelDownloadExceptionTest {
  private static final int ERROR_CODE = ModelDownloadException.FAILED_TO_DOWNLOAD_OTHER;
  private static final int DOWNLOADER_LIB_ERROR_CODE = 500;

  @Test
  public void getErrorCode_constructor1() {
    ModelDownloadException e = new ModelDownloadException(ERROR_CODE, new Exception());
    assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
  }

  @Test
  public void getErrorCode_constructor2() {
    ModelDownloadException e = new ModelDownloadException(ERROR_CODE, "error_msg");
    assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE);
    assertThat(e.getDownloaderLibErrorCode())
        .isEqualTo(ModelDownloadException.DEFAULT_DOWNLOADER_LIB_ERROR_CODE);
  }

  @Test
  public void getErrorCode_constructor3() {
    ModelDownloadException e =
        new ModelDownloadException(ERROR_CODE, DOWNLOADER_LIB_ERROR_CODE, "error_msg");
    assertThat(e.getErrorCode()).isEqualTo(ERROR_CODE);
    assertThat(e.getDownloaderLibErrorCode()).isEqualTo(DOWNLOADER_LIB_ERROR_CODE);
  }
}
