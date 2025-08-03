/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.utils

import android.app.DatePickerDialog
import android.content.Context
import java.time.Instant

/** Factory for {@link DatePickerDialog}. */
object DatePickerFactory {
    fun create(context: Context, selectedDate: Instant, maxDate: Instant): DatePickerDialog {
        val datePickerDialog = DatePickerDialog(context)
        val datePicker = datePickerDialog.datePicker
        datePicker.maxDate = maxDate.toEpochMilli()

        val date = selectedDate.toLocalDate()
        // date picker takes month starting from 0, local date return month starting from 1
        val month = date.month.value - 1
        datePicker.updateDate(date.year, month, date.dayOfMonth)

        return datePickerDialog
    }
}
