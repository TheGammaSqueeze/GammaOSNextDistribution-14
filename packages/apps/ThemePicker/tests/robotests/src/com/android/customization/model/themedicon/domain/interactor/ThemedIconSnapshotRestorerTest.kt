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

package com.android.customization.model.themedicon.domain.interactor

import androidx.test.filters.SmallTest
import com.android.customization.model.themedicon.data.repository.ThemeIconRepository
import com.android.wallpaper.testing.FakeSnapshotStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class ThemedIconSnapshotRestorerTest {

    private lateinit var underTest: ThemedIconSnapshotRestorer
    private var isActivated = false

    @Before
    fun setUp() {
        isActivated = false
        underTest =
            ThemedIconSnapshotRestorer(
                isActivated = { isActivated },
                setActivated = { isActivated = it },
                interactor =
                    ThemedIconInteractor(
                        repository = ThemeIconRepository(),
                    )
            )
    }

    @Test
    fun `set up and restore - active`() = runTest {
        isActivated = true

        val store = FakeSnapshotStore()
        store.store(underTest.setUpSnapshotRestorer(store = store))
        val storedSnapshot = store.retrieve()

        underTest.restoreToSnapshot(snapshot = storedSnapshot)
        assertThat(isActivated).isTrue()
    }

    @Test
    fun `set up and restore - inactive`() = runTest {
        isActivated = false

        val store = FakeSnapshotStore()
        store.store(underTest.setUpSnapshotRestorer(store = store))
        val storedSnapshot = store.retrieve()

        underTest.restoreToSnapshot(snapshot = storedSnapshot)
        assertThat(isActivated).isFalse()
    }

    @Test
    fun `set up - deactivate - restore to active`() = runTest {
        isActivated = true
        val store = FakeSnapshotStore()
        store.store(underTest.setUpSnapshotRestorer(store = store))
        val initialSnapshot = store.retrieve()

        underTest.store(isActivated = false)

        underTest.restoreToSnapshot(snapshot = initialSnapshot)
        assertThat(isActivated).isTrue()
    }

    @Test
    fun `set up - activate - restore to inactive`() = runTest {
        isActivated = false
        val store = FakeSnapshotStore()
        store.store(underTest.setUpSnapshotRestorer(store = store))
        val initialSnapshot = store.retrieve()

        underTest.store(isActivated = true)

        underTest.restoreToSnapshot(snapshot = initialSnapshot)
        assertThat(isActivated).isFalse()
    }
}
