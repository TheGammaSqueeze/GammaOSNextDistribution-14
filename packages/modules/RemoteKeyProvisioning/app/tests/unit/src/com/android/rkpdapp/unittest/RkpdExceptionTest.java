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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.RkpdException.ErrorCode;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RkpdExceptionTest {
    @Presubmit
    @Test
    public void handlesUnknownHttpStatus() {
        RkpdException ex = RkpdException.createFromHttpError(123);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.HTTP_UNKNOWN_ERROR);
    }

    @Presubmit
    @Test
    public void handlesServerErrors() {
        for (int httpStatus = 500; httpStatus < 600; ++httpStatus) {
            RkpdException ex = RkpdException.createFromHttpError(
                    httpStatus);
            assertThat(ex).isNotNull();
            assertThat(ex).hasMessageThat().contains("HTTP");
            assertWithMessage(httpStatus + "should have been a server error")
                    .that(ex.getErrorCode()).isEqualTo(ErrorCode.HTTP_SERVER_ERROR);
        }
    }

    @Presubmit
    @Test
    public void handlesClientErrors() {
        for (int httpStatus = 400; httpStatus < 500; ++httpStatus) {
            RkpdException ex = RkpdException.createFromHttpError(
                    httpStatus);
            assertThat(ex).isNotNull();
            if (httpStatus == 444) {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_REGISTERED);
            } else {
                assertWithMessage(httpStatus + "should have been a client error")
                        .that(ex.getErrorCode()).isEqualTo(ErrorCode.HTTP_CLIENT_ERROR);
            }
            assertThat(ex).hasMessageThat().contains("HTTP");
        }
    }
}
