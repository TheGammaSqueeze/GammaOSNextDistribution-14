/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.storageaccess.cts.tests

import android.app.Activity.RESULT_CANCELED
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.storageaccess.cts.launchClientActivity
import android.storageaccess.cts.log
import android.storageaccess.cts.startActivityForFutureResult
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Test cases for [StorageVolume.createAccessIntent]. */
class ScopedDirectoryAccessClientTest : TestBase() {

    @Test
    @ApiTest(apis = ["android.os.storage.StorageVolume#createAccessIntent"])
    fun test_createAccessIntent_invalidPaths() {
        val invalidPaths = listOf(
                "",
                "/dev/null",
                "/../",
                "/HiddenStuff")

        volumes.forEach { volume ->
            invalidPaths.forEach { path ->
                assertNull("Should NOT be able get access intent for '$path' on $volume",
                        volume.createAccessIntent(path))
            }
        }

        // Also test root of the primary volume.
        assertNull("Should NOT be able get the Access Intent for root on the primary volume",
                primaryVolume.createAccessIntent(/* root */ null))
    }

    @Test
    @ApiTest(apis = ["android.os.storage.StorageVolume#createAccessIntent"])
    fun test_accessIntentActivityCancelled_ForAllVolumesAndDirectories() {
        val paths = listOf(
                null, // Root. Will skip for the primary volume.
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_DOCUMENTS)

        // We will be incrementing this every time we startActivityForResult().
        var requestCode = 100

        launchClientActivity { scenario ->
            volumes.forEach { volume ->
                paths.forEach innerLoop@ { path ->
                    // Skip root on the primary volume for which we don't get the access intent:
                    // see test_createAccessIntent_invalidPaths above.
                    if (volume.isPrimary && path == null) return@innerLoop

                    val intent = volume.createAccessIntent(path)
                    assertNotNull("Could NOT get access intent for '$path' on $volume", intent)

                    log("Launching Access Intent for '$path' on $volume: ${intent!!}")
                    // Launch the access intent "for result", with a unique (incremented)
                    // request code.
                    val futureResult = scenario.startActivityForFutureResult(intent, ++requestCode)

                    // We expect the "target" activity to send RESULT_CANCELED right away, so
                    // waiting for 5sec should be enough.
                    futureResult.get(5, TimeUnit.SECONDS).let {
                        assertEquals(/* expected */ requestCode, /* actual */ it.requestCode)
                        assertEquals(/* expected */ RESULT_CANCELED, /* actual */ it.resultCode)
                        assertNull(it.data)
                    }
                }
            }

            // Clean up the ActivityScenario (makes sure the Activity is "finished")
            scenario.close()
        }
    }

    private val storageManager: StorageManager
        get() = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    private val volumes: List<StorageVolume>
        get() = storageManager.storageVolumes.takeUnless { it.isEmpty() }
                ?: error("Could not retrieve storage volumes")

    private val primaryVolume: StorageVolume
        get() = storageManager.primaryStorageVolume
}