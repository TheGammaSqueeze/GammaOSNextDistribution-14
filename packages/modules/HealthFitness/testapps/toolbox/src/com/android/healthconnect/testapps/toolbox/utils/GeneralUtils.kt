/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox.utils

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.InsertRecordsResponse
import android.health.connect.ReadRecordsRequest
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeRangeFilter
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.Device
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.Record
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlinx.coroutines.suspendCancellableCoroutine

class GeneralUtils {

    companion object {
        fun getMetaData(context: Context, recordUuid: String): Metadata {
            val device: Device =
                Device.Builder().setManufacturer(MANUFACTURER).setModel(MODEL).setType(1).build()
            val dataOrigin = DataOrigin.Builder().setPackageName(context.packageName).build()
            return Metadata.Builder()
                .setDevice(device)
                .setDataOrigin(dataOrigin)
                .setId(recordUuid)
                .build()
        }

        fun getMetaData(context: Context): Metadata {
            val device: Device =
                Device.Builder().setManufacturer(MANUFACTURER).setModel(MODEL).setType(1).build()
            val dataOrigin = DataOrigin.Builder().setPackageName(context.packageName).build()
            return Metadata.Builder().setDevice(device).setDataOrigin(dataOrigin).build()
        }

        suspend fun <T : Record> insertRecords(
            records: List<T>,
            manager: HealthConnectManager,
        ): List<Record> {

            val insertedRecords =
                try {
                    suspendCancellableCoroutine<InsertRecordsResponse> { continuation ->
                            manager.insertRecords(
                                records, Runnable::run, continuation.asOutcomeReceiver())
                        }
                        .records
                } catch (ex: Exception) {
                    throw ex
                }
            return insertedRecords
        }

        suspend fun <T : Record> updateRecords(
            records: List<T>,
            manager: HealthConnectManager,
        ) {
            try {
                suspendCancellableCoroutine<Void> { continuation ->
                    manager.updateRecords(records, Runnable::run, continuation.asOutcomeReceiver())
                }
            } catch (ex: Exception) {
                throw ex
            }
        }

        fun <T : Any> getStaticFieldNamesAndValues(
            obj: KClass<T>,
        ): EnumFieldsWithValues {
            val fieldNameToValue: MutableMap<String, Any> = emptyMap<String, Any>().toMutableMap()
            val fields: List<Field> =
                obj.java.declaredFields.filter { field ->
                    Modifier.isStatic(field.modifiers) && field.type == Int::class.java
                }
            for (field in fields) {
                fieldNameToValue[field.name] = field.get(obj)!!
            }
            return EnumFieldsWithValues(fieldNameToValue.toMap())
        }

        suspend fun readRecords(
            recordType: Class<out Record>,
            timeFilterRange: TimeRangeFilter,
            numberOfRecordsPerBatch: Long,
            manager: HealthConnectManager,
        ): List<Record> {
            val filter =
                ReadRecordsRequestUsingFilters.Builder(recordType)
                    .setTimeRangeFilter(timeFilterRange)
                    .setPageSize(numberOfRecordsPerBatch.toInt())
                    .build()
            val records =
                suspendCancellableCoroutine<ReadRecordsResponse<*>> { continuation ->
                        manager.readRecords(filter, Runnable::run, continuation.asOutcomeReceiver())
                    }
                    .records
            Log.d("READ_RECORDS", "Read ${records.size} records")
            return records
        }

        suspend fun <T : Record> readRecords(
            manager: HealthConnectManager,
            request: ReadRecordsRequest<T>,
        ): List<T> {
            val records =
                suspendCancellableCoroutine<ReadRecordsResponse<T>> { continuation ->
                        manager.readRecords(
                            request, Runnable::run, continuation.asOutcomeReceiver())
                    }
                    .records
            Log.d("READ_RECORDS", "Read ${records.size} records")
            return records
        }
    }
}
