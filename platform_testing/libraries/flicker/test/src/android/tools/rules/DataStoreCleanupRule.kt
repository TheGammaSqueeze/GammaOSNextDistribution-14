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

package android.tools.rules

import android.annotation.SuppressLint
import android.tools.flicker.datastore.DataStore
import android.tools.utils.TEST_SCENARIO
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@SuppressLint("VisibleForTests")
class DataStoreCleanupRule : TestWatcher() {
    override fun starting(description: Description?) {
        super.starting(description)
        resetDataStore()
    }

    override fun finished(description: Description?) {
        super.finished(description)
        resetDataStore()
    }

    private fun resetDataStore() {
        val backup = DataStore.backup()
        DataStore.clear()

        if (backup.cachedResults.containsKey(TEST_SCENARIO)) {
            backup.cachedResults.remove(TEST_SCENARIO)
        }

        if (backup.cachedFlickerServiceAssertions.containsKey(TEST_SCENARIO)) {
            backup.cachedFlickerServiceAssertions.remove(TEST_SCENARIO)
        }

        DataStore.restore(backup)
    }
}
