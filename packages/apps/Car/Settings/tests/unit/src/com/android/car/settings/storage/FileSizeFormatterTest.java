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

package com.android.car.settings.storage;

import static com.android.car.settings.storage.FileSizeFormatter.GIGABYTE_IN_BYTES;
import static com.android.car.settings.storage.FileSizeFormatter.MEGABYTE_IN_BYTES;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.icu.util.MeasureUnit;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FileSizeFormatterTest {

    private Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void formatFileSize_zero() {
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                0,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("0.00 GB");
    }

    @Test
    public void formatFileSize_smallSize() {
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                MEGABYTE_IN_BYTES * 11,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("0.01 GB");
    }

    @Test
    public void formatFileSize_lessThanOneSize() {
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                MEGABYTE_IN_BYTES * 155,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("0.16 GB");
    }

    @Test
    public void formatFileSize_greaterThanOneSize() {
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                MEGABYTE_IN_BYTES * 1551,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("1.6 GB");
    }

    @Test
    public void formatFileSize_greaterThanTen() {
        // Should round down due to truncation
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                GIGABYTE_IN_BYTES * 15 + MEGABYTE_IN_BYTES * 50,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("15 GB");
    }

    @Test
    public void formatFileSize_handlesNegativeFileSizes() {
        assertThat(
            FileSizeFormatter.formatFileSize(
                mContext,
                MEGABYTE_IN_BYTES * -155,
                MeasureUnit.GIGABYTE,
                GIGABYTE_IN_BYTES))
            .isEqualTo("-0.16 GB");
    }
}
