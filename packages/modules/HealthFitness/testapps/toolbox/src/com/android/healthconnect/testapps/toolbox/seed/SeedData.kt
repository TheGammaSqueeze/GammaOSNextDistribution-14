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
package com.android.healthconnect.testapps.toolbox.seed

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.Device
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Mass
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.insertRecords
import java.time.Duration.ofDays
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Random
import kotlinx.coroutines.runBlocking

class SeedData(private val context: Context, private val manager: HealthConnectManager) {

    companion object {
        const val NUMBER_OF_SERIES_RECORDS_TO_INSERT = 200L
    }

    fun seedData() {
        runBlocking {
            try {
                seedMenstruationData()
                seedStepsData()
                seedHeartRateData(10)
            } catch (ex: Exception) {
                throw ex
            }
        }
    }

    private suspend fun seedStepsData() {
        val start = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val records =
            (1L..50).map { count ->
                getStepsRecord(count, start.plus(ofMinutes(count)))
            }

        insertRecords(records, manager)
    }

    private suspend fun seedMenstruationData() {
        val today = Instant.now()
        val periodRecord =
            MenstruationPeriodRecord.Builder(getMetaData(), today.minus(ofDays(5L)), today).build()
        val flowRecords =
            (-5..0).map { days ->
                MenstruationFlowRecord.Builder(
                        getMetaData(),
                        today.plus(ofDays(days.toLong())),
                        MenstruationFlowType.FLOW_MEDIUM)
                    .build()
            }
        insertRecords(
            buildList {
                add(periodRecord)
                addAll(flowRecords)
            },
            manager)
    }

    suspend fun seedHeartRateData(numberOfRecordsPerBatch: Long) {
        val start = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val random = Random()
        val records =
            (1L..numberOfRecordsPerBatch).map { timeOffset ->
                val hrSamples = ArrayList<Pair<Long, Instant>>()
                repeat(10) { i ->
                    hrSamples.add(
                        Pair(getValidHeartRate(random), start.plus(ofMinutes(timeOffset + i))))
                }
                getHeartRateRecord(
                    hrSamples,
                    start.plus(ofMinutes(timeOffset)),
                    start.plus(ofMinutes(timeOffset + 100)))
            }
        insertRecords(records, manager)
    }

    private fun getHeartRateRecord(
        heartRateValues: List<Pair<Long, Instant>>,
        start: Instant,
        end: Instant,
    ): HeartRateRecord {
        return HeartRateRecord.Builder(
                getMetaData(),
                start,
                end,
                heartRateValues.map { HeartRateRecord.HeartRateSample(it.first, it.second) })
            .build()
    }

    private fun getValidHeartRate(random: Random): Long {
        return (random.nextInt(20) + 80).toLong()
    }

    private fun getStepsRecord(count: Long, time: Instant): StepsRecord {
        return StepsRecord.Builder(getMetaData(), time, time.plusSeconds(30), count).build()
    }

    private fun getMetaData(): Metadata {
        val device: Device =
            Device.Builder().setManufacturer(MANUFACTURER).setModel(MODEL).setType(1).build()
        val dataOrigin = DataOrigin.Builder().setPackageName(context.packageName).build()
        return Metadata.Builder().setDevice(device).setDataOrigin(dataOrigin).build()
    }
}
