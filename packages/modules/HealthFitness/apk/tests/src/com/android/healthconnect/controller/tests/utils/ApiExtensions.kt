package com.android.healthconnect.controller.tests.utils

import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.Record

fun ReadRecordsRequestUsingFilters<Record>.fromDataSource(packageName: String): Boolean {
    return this.dataOrigins.any { dataOrigin -> dataOrigin.packageName == packageName }
}

fun ReadRecordsRequestUsingFilters<Record>.fromTimeRange(
    sourceTimeFilter: TimeInstantRangeFilter
): Boolean {
    val thisTimeRangeFilter = this.timeRangeFilter
    if (thisTimeRangeFilter !is TimeInstantRangeFilter) return false
    return thisTimeRangeFilter.startTime == sourceTimeFilter.startTime &&
        thisTimeRangeFilter.endTime == sourceTimeFilter.endTime
}

fun ReadRecordsRequestUsingFilters<Record>.forDataType(dataType: Class<out Record>): Boolean {
    return this.recordType == dataType
}
