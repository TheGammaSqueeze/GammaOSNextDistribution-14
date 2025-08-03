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

package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.android.intentresolver.any
import com.android.intentresolver.eq
import com.android.intentresolver.nullable
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.isNull
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class LastChosenManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testTargetIntent = Intent("TestAction")

    @Mock lateinit var mockContentResolver: ContentResolver
    @Mock lateinit var mockIPackageManager: IPackageManager

    private lateinit var lastChosenManager: LastChosenManager

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        lastChosenManager =
            PackageManagerLastChosenManager(mockContentResolver, testDispatcher, testTargetIntent) {
                mockIPackageManager
            }
    }

    @Test
    fun getLastChosen_returnsLastChosenActivity() =
        testScope.runTest {
            // Arrange
            val testResolveInfo = ResolveInfo()
            whenever(mockIPackageManager.getLastChosenActivity(any(), nullable(), any()))
                .thenReturn(testResolveInfo)

            // Act
            val lastChosen = lastChosenManager.getLastChosen()
            runCurrent()

            // Assert
            verify(mockIPackageManager)
                .getLastChosenActivity(
                    eq(testTargetIntent),
                    isNull(),
                    eq(PackageManager.MATCH_DEFAULT_ONLY),
                )
            assertThat(lastChosen).isSameInstanceAs(testResolveInfo)
        }

    @Test
    fun setLastChosen_setsLastChosenActivity() =
        testScope.runTest {
            // Arrange
            val testComponent = ComponentName("TestPackage", "TestClass")
            val testIntent = Intent().apply { component = testComponent }
            val testIntentFilter = IntentFilter()
            val testMatch = 456

            // Act
            lastChosenManager.setLastChosen(testIntent, testIntentFilter, testMatch)
            runCurrent()

            // Assert
            verify(mockIPackageManager)
                .setLastChosenActivity(
                    eq(testIntent),
                    isNull(),
                    eq(PackageManager.MATCH_DEFAULT_ONLY),
                    eq(testIntentFilter),
                    eq(testMatch),
                    eq(testComponent),
                )
        }
}
