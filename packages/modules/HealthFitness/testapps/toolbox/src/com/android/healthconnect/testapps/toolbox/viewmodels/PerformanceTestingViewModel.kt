package com.android.healthconnect.testapps.toolbox.viewmodels

import android.health.connect.HealthConnectManager
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.TimeRangeFilter
import android.health.connect.datatypes.HeartRateRecord
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.testapps.toolbox.seed.SeedData
import com.android.healthconnect.testapps.toolbox.seed.SeedData.Companion.NUMBER_OF_SERIES_RECORDS_TO_INSERT
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PerformanceTestingViewModel : ViewModel() {

    private val _performanceInsertedRecordsState =
        MutableLiveData<PerformanceInsertedRecordsState>()
    val performanceInsertedRecordsState: LiveData<PerformanceInsertedRecordsState>
        get() = _performanceInsertedRecordsState

    private val _performanceReadRecordsState = MutableLiveData<PerformanceReadRecordsState>()
    val performanceReadRecordsState: LiveData<PerformanceReadRecordsState>
        get() = _performanceReadRecordsState

    fun beginInsertingData(seedDataInParallel: Boolean) {
        _performanceInsertedRecordsState.postValue(
            PerformanceInsertedRecordsState.BeginInserting(seedDataInParallel))
    }

    fun beginReadingData() {
        _performanceReadRecordsState.postValue(PerformanceReadRecordsState.BeginReading)
    }

    fun insertRecordsForPerformanceTesting(
        numberOfBatches: Long,
        numberOfRecordsPerBatch: Long,
        seedDataClass: SeedData,
    ) {
        viewModelScope.launch {
            try {
                for (i in 1..numberOfBatches) {
                    withContext(Dispatchers.IO) {
                        seedDataClass.seedHeartRateData(numberOfRecordsPerBatch)
                    }
                }
                _performanceInsertedRecordsState.postValue(PerformanceInsertedRecordsState.Success)
            } catch (ex: Exception) {
                _performanceInsertedRecordsState.postValue(
                    PerformanceInsertedRecordsState.Error(ex.localizedMessage))
            }
        }
    }

    fun insertRecordsForPerformanceTestingOverASpanOfTime(
        numberOfBatches: Long,
        numberOfRecordsPerBatch: Long,
        numberOfMinutes: Long,
        seedDataClass: SeedData,
    ) {
        viewModelScope.launch {
            try {

                val frequency =
                    scheduleTaskAtFixedRate(numberOfBatches, numberOfMinutes) {
                        insertBlocking(seedDataClass, numberOfRecordsPerBatch, 5)
                    }

                _performanceInsertedRecordsState.postValue(
                    PerformanceInsertedRecordsState.InsertingOverSpanOfTime(frequency))
            } catch (ex: Exception) {
                _performanceInsertedRecordsState.postValue(
                    PerformanceInsertedRecordsState.Error(ex.localizedMessage))
            }
        }
    }

    fun insertRecordsForPerformanceTestingInParallel(
        numberOfRecordsToInsert: Long,
        seedDataClass: SeedData,
    ) {
        var numOfCalls: Int =
            ceil(numberOfRecordsToInsert.toDouble() / NUMBER_OF_SERIES_RECORDS_TO_INSERT).toInt()

        viewModelScope.launch(Dispatchers.IO) {
            val numberOfRecordsFailedToInsert = AtomicLong(0L)
            val workerPool: ExecutorService = Executors.newFixedThreadPool(4)

            while (numOfCalls-- > 0) {
                workerPool.execute {
                    val recordsToInsertInIteration =
                        min(numberOfRecordsToInsert, NUMBER_OF_SERIES_RECORDS_TO_INSERT)
                    try {
                        insertBlocking(seedDataClass, recordsToInsertInIteration, 5)
                    } catch (ex: Exception) {
                        numberOfRecordsFailedToInsert.getAndAdd(recordsToInsertInIteration)
                    }
                }
            }

            try {
                workerPool.shutdown()
                workerPool.awaitTermination(2, TimeUnit.MINUTES)
                if (numberOfRecordsFailedToInsert.get() > 0L) {
                    _performanceInsertedRecordsState.postValue(
                        PerformanceInsertedRecordsState.Error(
                            "Failed to insert ${numberOfRecordsFailedToInsert.get()}"))
                } else {
                    _performanceInsertedRecordsState.postValue(
                        PerformanceInsertedRecordsState.Success)
                }
            } catch (ex: Exception) {
                _performanceInsertedRecordsState.postValue(
                    PerformanceInsertedRecordsState.Error(ex.localizedMessage))
            }
        }
    }

    private fun insertBlocking(
        seedDataClass: SeedData,
        numberOfRecordsToInsert: Long,
        retryCount: Int,
    ) {
        try {
            runBlocking { seedDataClass.seedHeartRateData(numberOfRecordsToInsert) }
        } catch (ex: Exception) {
            if (retryCount != 0) {
                insertBlocking(seedDataClass, numberOfRecordsToInsert, retryCount - 1)
            }
        }
    }

    fun readRecordsForPerformanceTesting(
        numberOfBatches: Long,
        numberOfRecordsPerBatch: Long,
        manager: HealthConnectManager,
    ) {
        val timeRangeFilter = getReadTimeRangeFilter()

        viewModelScope.launch {
            try {
                for (i in 1..numberOfBatches) {
                    withContext(Dispatchers.IO) {
                        GeneralUtils.readRecords(
                            HeartRateRecord::class.java,
                            timeRangeFilter,
                            numberOfRecordsPerBatch,
                            manager)
                    }
                }

                _performanceReadRecordsState.postValue(PerformanceReadRecordsState.Success)
            } catch (ex: Exception) {
                _performanceReadRecordsState.postValue(
                    PerformanceReadRecordsState.Error(ex.localizedMessage))
            }
        }
    }

    fun readRecordsForPerformanceTestingOverASpanOfTime(
        numberOfBatches: Long,
        numberOfRecordsPerBatch: Long,
        numberOfMinutes: Long,
        manager: HealthConnectManager,
    ) {
        val timeRangeFilter = getReadTimeRangeFilter()

        viewModelScope.launch {
            try {
                val frequency =
                    scheduleTaskAtFixedRate(numberOfBatches, numberOfMinutes) {
                        runBlocking {
                            GeneralUtils.readRecords(
                                HeartRateRecord::class.java,
                                timeRangeFilter,
                                numberOfRecordsPerBatch,
                                manager)
                        }
                    }
                _performanceReadRecordsState.postValue(
                    PerformanceReadRecordsState.ReadingOverSpanOfTime(frequency))
            } catch (ex: Exception) {
                _performanceReadRecordsState.postValue(
                    PerformanceReadRecordsState.Error(ex.localizedMessage))
            }
        }
    }

    private fun getReadTimeRangeFilter(): TimeRangeFilter {
        val start = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val end = start.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(59))
        return TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
    }

    private fun scheduleTaskAtFixedRate(
        numberOfBatches: Long,
        numberOfMinutes: Long,
        runnable: Runnable,
    ): Long {
        val scheduler = Executors.newScheduledThreadPool(1)
        val taskFrequency: Long = (numberOfMinutes * 60 * 1000) / numberOfBatches // In milliseconds

        val handler =
            scheduler.scheduleAtFixedRate(
                runnable, taskFrequency, taskFrequency, TimeUnit.MILLISECONDS)
        val canceller = Runnable { handler.cancel(false) }
        scheduler.schedule(canceller, numberOfMinutes, TimeUnit.MINUTES)
        return taskFrequency
    }

    sealed class PerformanceInsertedRecordsState {
        data class Error(val errorMessage: String) : PerformanceInsertedRecordsState()
        object Success : PerformanceInsertedRecordsState()
        data class InsertingOverSpanOfTime(val timeDifferenceBetweenEachBatchInsert: Long) :
            PerformanceInsertedRecordsState()

        data class BeginInserting(val seedDataInParallel: Boolean) :
            PerformanceInsertedRecordsState()
    }

    sealed class PerformanceReadRecordsState {
        data class Error(val errorMessage: String) : PerformanceReadRecordsState()
        object Success : PerformanceReadRecordsState()
        object BeginReading : PerformanceReadRecordsState()
        data class ReadingOverSpanOfTime(val timeDifferenceBetweenEachBatchInsert: Long) :
            PerformanceReadRecordsState()
    }
}
