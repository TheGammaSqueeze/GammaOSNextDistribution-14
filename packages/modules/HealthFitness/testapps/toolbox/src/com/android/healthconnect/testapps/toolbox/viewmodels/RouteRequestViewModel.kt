/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.healthconnect.testapps.toolbox.viewmodels

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.ExerciseSessionRecord
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.readRecords
import java.time.Instant
import kotlinx.coroutines.launch

class RouteRequestViewModel : ViewModel() {

    private val _exerciseSessionRecords = MutableLiveData<Result<List<ExerciseSessionRecord>>>()
    val exerciseSessionRecords: LiveData<Result<List<ExerciseSessionRecord>>>
        get() = _exerciseSessionRecords

    fun readExerciseSessionRecords(context: Context) {
        val healthConnectManager = context.getSystemService(HealthConnectManager::class.java)!!

        val request =
            ReadRecordsRequestUsingFilters.Builder(ExerciseSessionRecord::class.java)
                .setTimeRangeFilter(
                    TimeInstantRangeFilter.Builder().setEndTime(Instant.now()).build())
                .setAscending(false)
                .setPageSize(10)
                .build()

        viewModelScope.launch {
            try {
                val response = readRecords(healthConnectManager, request)
                _exerciseSessionRecords.postValue(Result.success(response))
            } catch (e: Exception) {
                _exerciseSessionRecords.postValue(Result.failure(e))
            }
        }
    }
}
