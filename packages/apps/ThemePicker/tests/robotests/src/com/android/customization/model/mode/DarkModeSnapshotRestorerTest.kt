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
 *
 */

package com.android.customization.model.mode

import androidx.test.filters.SmallTest
import com.android.wallpaper.testing.FakeSnapshotStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class DarkModeSnapshotRestorerTest {

    private lateinit var underTest: DarkModeSnapshotRestorer
    private lateinit var testScope: TestScope

    private var isActive = false

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        underTest =
            DarkModeSnapshotRestorer(
                backgroundDispatcher = testDispatcher,
                isActive = { isActive },
                setActive = { isActive = it },
            )
    }

    @Test
    fun `set up and restore - active`() =
        testScope.runTest {
            isActive = true

            val store = FakeSnapshotStore()
            store.store(underTest.setUpSnapshotRestorer(store = store))
            val storedSnapshot = store.retrieve()

            underTest.restoreToSnapshot(snapshot = storedSnapshot)
            assertThat(isActive).isTrue()
        }

    @Test
    fun `set up and restore - inactive`() =
        testScope.runTest {
            isActive = false

            val store = FakeSnapshotStore()
            store.store(underTest.setUpSnapshotRestorer(store = store))
            val storedSnapshot = store.retrieve()

            underTest.restoreToSnapshot(snapshot = storedSnapshot)
            assertThat(isActive).isFalse()
        }

    @Test
    fun `set up - deactivate - restore to active`() =
        testScope.runTest {
            isActive = true
            val store = FakeSnapshotStore()
            store.store(underTest.setUpSnapshotRestorer(store = store))
            val initialSnapshot = store.retrieve()

            underTest.store(isActivated = false)

            underTest.restoreToSnapshot(snapshot = initialSnapshot)
            assertThat(isActive).isTrue()
        }

    @Test
    fun `set up - activate - restore to inactive`() =
        testScope.runTest {
            isActive = false
            val store = FakeSnapshotStore()
            store.store(underTest.setUpSnapshotRestorer(store = store))
            val initialSnapshot = store.retrieve()

            underTest.store(isActivated = true)

            underTest.restoreToSnapshot(snapshot = initialSnapshot)
            assertThat(isActive).isFalse()
        }
}
