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
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchDataActivity
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.stepsRecordFromTestApp
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.insertRecords
import android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords
import androidx.test.uiautomator.By
import java.time.Instant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/** CTS test for HealthConnect Categories screen. */
class CategoriesFragmentTest : HealthConnectBaseTest() {

    companion object {

        @JvmStatic
        @BeforeClass
        fun setup() {
            if (!TestUtils.isHardwareSupported()) {
                return
            }
            val records: List<Record> = listOf(stepsRecordFromTestApp(), stepsRecordFromTestApp())
            insertRecords(records)
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
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
    fun categoriesFragment_openAllCategories() {
        context.launchDataActivity {
            clickOnText("See all categories")
            waitDisplayed(By.text("Nutrition"))
        }
    }

    // TODO(b/274920669) Fix flaky tests
    //    @Test
    //    fun categoriesFragment_deleteAllData() {
    //        val records: List<Record> =
    // listOf(stepsRecordFromTestApp(Instant.now().minus(ofDays(100))))
    //        insertRecords(records)
    //
    //        context.launchDataActivity {
    //            waitDisplayed(By.text("Activity"))
    //
    //            clickOnText("Delete all data")
    //            clickOnText("Delete all data")
    //            clickOnText("Next")
    //            clickOnText("Delete")
    //            clickOnText("Done")
    //        }
    //
    //        context.launchDataActivity { waitNotDisplayed(By.text("Activity")) }
    //    }
    //
    //    @Test
    //    fun categoriesFragment_withDataOlderThanRange_deletesAllDataInRange_showsCategory() {
    //        insertRecords(listOf(stepsRecordFromTestApp(Instant.now().minus(ofDays(20)))))
    //
    //        context.launchDataActivity {
    //            waitDisplayed(By.text("Activity"))
    //
    //            clickOnText("Delete all data")
    //            clickOnText("Delete last 7 days")
    //            clickOnText("Next")
    //            clickOnText("Delete")
    //            clickOnText("Done")
    //
    //            waitDisplayed(By.text("Activity"))
    //        }
    //    }
    //
    //    @Test
    //    fun categoriesFragment_withNoDataBeforeRange_deletesAllDataInRange_removesCategory() {
    //        insertRecords(listOf(stepsRecordFromTestApp(Instant.now().minus(ofDays(20)))))
    //
    //        context.launchDataActivity {
    //            clickOnText("Delete all data")
    //            clickOnText("Delete last 30 days")
    //            clickOnText("Next")
    //            clickOnText("Delete")
    //            clickOnText("Done")
    //
    //            waitNotDisplayed(By.text("Activity"))
    //        }
    //    }
}
