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
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.UserHandle
import com.android.intentresolver.ResolvedComponentInfo
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.TargetInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class ResolvedComponentSortingTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeResolverComparator = FakeResolverComparator()

    private val resolvedComponentSorting =
        ResolvedComponentSortingImpl(testDispatcher, fakeResolverComparator)

    @Test
    fun sorted_onNullList_returnsNull() =
        testScope.runTest {
            // Arrange
            val testInput: List<ResolvedComponentInfo>? = null

            // Act
            val result = resolvedComponentSorting.sorted(testInput)
            runCurrent()

            // Assert
            assertThat(result).isNull()
        }

    @Test
    fun sorted_onEmptyList_returnsEmptyList() =
        testScope.runTest {
            // Arrange
            val testInput = emptyList<ResolvedComponentInfo>()

            // Act
            val result = resolvedComponentSorting.sorted(testInput)
            runCurrent()

            // Assert
            assertThat(result).isEmpty()
        }

    @Test
    fun sorted_returnsListSortedByGivenComparator() =
        testScope.runTest {
            // Arrange
            val testIntent = Intent("TestAction")
            val testInput =
                listOf(
                        ResolveInfo().apply {
                            activityInfo = ActivityInfo()
                            activityInfo.packageName = "TestPackage3"
                            activityInfo.name = "TestClass3"
                        },
                        ResolveInfo().apply {
                            activityInfo = ActivityInfo()
                            activityInfo.packageName = "TestPackage1"
                            activityInfo.name = "TestClass1"
                        },
                        ResolveInfo().apply {
                            activityInfo = ActivityInfo()
                            activityInfo.packageName = "TestPackage2"
                            activityInfo.name = "TestClass2"
                        },
                    )
                    .map {
                        it.targetUserId = UserHandle.USER_CURRENT
                        ResolvedComponentInfo(
                            ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                            testIntent,
                            it,
                        )
                    }

            // Act
            val result = async { resolvedComponentSorting.sorted(testInput) }
            runCurrent()

            // Assert
            assertThat(result.await()?.map { it.name.packageName })
                .containsExactly("TestPackage1", "TestPackage2", "TestPackage3")
                .inOrder()
        }

    @Test
    fun getScore_displayResolveInfo_returnsTheScoreAccordingToTheResolverComparator() {
        // Arrange
        val testTarget =
            DisplayResolveInfo.newDisplayResolveInfo(
                Intent(),
                ResolveInfo().apply {
                    activityInfo = ActivityInfo()
                    activityInfo.name = "TestClass"
                    activityInfo.applicationInfo = ApplicationInfo()
                    activityInfo.applicationInfo.packageName = "TestPackage"
                },
                Intent(),
            )

        // Act
        val result = resolvedComponentSorting.getScore(testTarget)

        // Assert
        assertThat(result).isEqualTo(1.23f)
    }

    @Test
    fun getScore_targetInfo_returnsTheScoreAccordingToTheResolverComparator() {
        // Arrange
        val mockTargetInfo = Mockito.mock(TargetInfo::class.java)

        // Act
        val result = resolvedComponentSorting.getScore(mockTargetInfo)

        // Assert
        assertThat(result).isEqualTo(1.23f)
    }

    @Test
    fun updateModel_updatesResolverComparatorModel() =
        testScope.runTest {
            // Arrange
            val mockTargetInfo = Mockito.mock(TargetInfo::class.java)
            assertThat(fakeResolverComparator.lastUpdateModel).isNull()

            // Act
            resolvedComponentSorting.updateModel(mockTargetInfo)
            runCurrent()

            // Assert
            assertThat(fakeResolverComparator.lastUpdateModel).isSameInstanceAs(mockTargetInfo)
        }

    @Test
    fun updateChooserCounts_updatesResolverComparaterChooserCounts() =
        testScope.runTest {
            // Arrange
            val testPackageName = "TestPackage"
            val testUser = UserHandle(456)
            val testAction = "TestAction"
            assertThat(fakeResolverComparator.lastUpdateChooserCounts).isNull()

            // Act
            resolvedComponentSorting.updateChooserCounts(testPackageName, testUser, testAction)
            runCurrent()

            // Assert
            assertThat(fakeResolverComparator.lastUpdateChooserCounts)
                .isEqualTo(Triple(testPackageName, testUser, testAction))
        }

    @Test
    fun destroy_destroysResolverComparator() {
        // Arrange
        assertThat(fakeResolverComparator.destroyCalled).isFalse()

        // Act
        resolvedComponentSorting.destroy()

        // Assert
        assertThat(fakeResolverComparator.destroyCalled).isTrue()
    }
}
