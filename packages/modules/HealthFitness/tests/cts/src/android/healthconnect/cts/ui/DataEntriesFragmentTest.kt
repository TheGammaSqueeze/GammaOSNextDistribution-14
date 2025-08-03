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
package android.healthconnect.cts.ui

import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchDataActivity
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.distanceRecordFromTestApp
import android.healthconnect.cts.lib.UiTestUtils.stepsRecordFromTestApp
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.insertRecords
import android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords
import java.time.Instant
import java.time.Period.ofDays
import org.junit.AfterClass
import org.junit.Test

/** CTS test for HealthConnect Data entries screen. */
class DataEntriesFragmentTest : HealthConnectBaseTest() {

    companion object {
        private const val TAG = "DataEntriesFragmentTest"

        @JvmStatic
        @AfterClass
        fun tearDown() {
            if (!TestUtils.isHardwareSupported()) {
                return
            }
            verifyDeleteRecords(
                StepsRecord::class.java,
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build())
            verifyDeleteRecords(
                DistanceRecord::class.java,
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build())
        }
    }

    @Test
    fun dataEntries_showsInsertedEntry() {
        insertRecords(listOf(distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            // TODO(b/265789268): Fix "See all entries" view not found.
            //            clickOnText("See all entries")
            //
            //            waitDisplayed(By.text("0.5 km"))
        }
    }

    @Test
    fun dataEntries_changeUnit_showsUpdatedUnit() {
        insertRecords(listOf(distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")

            // TODO(b/265789268): Fix "See all entries" view not found.
            //            clickOnText("See all entries")
            //            clickOnContentDescription("More options")
            //            clickOnText("Set data units")
            //            clickOnText("Distance")
            //            clickOnText("Kilometers")
            //            navigateUp()
            //
            //            waitDisplayed(By.text("0.5 km"))
        }
    }

    @Test
    fun dataEntries_deletesData_showsNoData() {
        insertRecords(listOf(distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            // TODO(b/265789268): Fix "See all entries" view not found.
            //            clickOnText("See all entries")
            //
            //            // Delete entry
            //            clickOnContentDescription("Delete data entry")
            //            clickOnText("Delete")
            //            clickOnText("Done")
            //            waitDisplayed(By.text("No data"))
        }
    }

    @Test
    fun dataEntries_changeDate_updatesSelectedDate() {
        insertRecords(listOf(distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            // TODO(b/265789268): Fix "See all entries" view not found.
            //            clickOnText("See all entries")
            //
            //            clickOnContentDescription("Selected day")
            //            clickOnText("1")
            //            clickOnText("OK")
        }
    }

    @Test
    fun dataEntries_navigateToYesterday() {
        insertRecords(listOf(stepsRecordFromTestApp(12, Instant.now().minus(ofDays(1)))))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Steps")
            // TODO(b/265789268): Fix "See all entries" view not found.
            //            clickOnText("See all entries")
            //            clickOnContentDescription("Previous day")
            //            waitDisplayed(By.text("12 steps"))
        }
    }
}
