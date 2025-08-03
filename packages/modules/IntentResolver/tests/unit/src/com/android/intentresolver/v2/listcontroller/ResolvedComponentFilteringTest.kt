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
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.android.intentresolver.ResolvedComponentInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ResolvedComponentFilteringTest {

    private lateinit var resolvedComponentFiltering: ResolvedComponentFiltering

    private val fakeFilterableComponents =
        object : FilterableComponents {
            override fun isComponentFiltered(name: ComponentName): Boolean {
                return name.packageName == "FilteredPackage"
            }
        }

    private val fakePermissionChecker =
        object : PermissionChecker {
            override suspend fun checkComponentPermission(
                permission: String,
                uid: Int,
                owningUid: Int,
                exported: Boolean
            ): Int {
                return if (permission == "MissingPermission") {
                    PackageManager.PERMISSION_DENIED
                } else {
                    PackageManager.PERMISSION_GRANTED
                }
            }
        }

    @Before
    fun setup() {
        resolvedComponentFiltering =
            ResolvedComponentFilteringImpl(
                launchedFromUid = 123,
                filterableComponents = fakeFilterableComponents,
                permissionChecker = fakePermissionChecker,
            )
    }

    @Test
    fun filterIneligibleActivities_returnsListWithoutFilteredComponents() = runTest {
        // Arrange
        val testIntent = Intent("TestAction")
        val testResolveInfo =
            ResolveInfo().apply {
                activityInfo = ActivityInfo()
                activityInfo.packageName = "TestPackage"
                activityInfo.name = "TestClass"
                activityInfo.permission = "TestPermission"
                activityInfo.applicationInfo = ApplicationInfo()
                activityInfo.applicationInfo.uid = 456
                activityInfo.exported = false
            }
        val filteredResolveInfo =
            ResolveInfo().apply {
                activityInfo = ActivityInfo()
                activityInfo.packageName = "FilteredPackage"
                activityInfo.name = "FilteredClass"
                activityInfo.permission = "TestPermission"
                activityInfo.applicationInfo = ApplicationInfo()
                activityInfo.applicationInfo.uid = 456
                activityInfo.exported = false
            }
        val missingPermissionResolveInfo =
            ResolveInfo().apply {
                activityInfo = ActivityInfo()
                activityInfo.packageName = "NoPermissionPackage"
                activityInfo.name = "NoPermissionClass"
                activityInfo.permission = "MissingPermission"
                activityInfo.applicationInfo = ApplicationInfo()
                activityInfo.applicationInfo.uid = 456
                activityInfo.exported = false
            }
        val testInput =
            listOf(
                ResolvedComponentInfo(
                    ComponentName("TestPackage", "TestClass"),
                    testIntent,
                    testResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("FilteredPackage", "FilteredClass"),
                    testIntent,
                    filteredResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("NoPermissionPackage", "NoPermissionClass"),
                    testIntent,
                    missingPermissionResolveInfo,
                )
            )

        // Act
        val result = resolvedComponentFiltering.filterIneligibleActivities(testInput)

        // Assert
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(name.packageName).isEqualTo("TestPackage")
            assertThat(name.className).isEqualTo("TestClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(testResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
    }

    @Test
    fun filterLowPriority_filtersAfterFirstDifferentPriority() {
        // Arrange
        val testIntent = Intent("TestAction")
        val testResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val equalResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val diffResolveInfo =
            ResolveInfo().apply {
                priority = 2
                isDefault = true
            }
        val testInput =
            listOf(
                ResolvedComponentInfo(
                    ComponentName("TestPackage", "TestClass"),
                    testIntent,
                    testResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("EqualPackage", "EqualClass"),
                    testIntent,
                    equalResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("DiffPackage", "DiffClass"),
                    testIntent,
                    diffResolveInfo,
                ),
            )

        // Act
        val result = resolvedComponentFiltering.filterLowPriority(testInput)

        // Assert
        assertThat(result).hasSize(2)
        with(result.first()) {
            assertThat(name.packageName).isEqualTo("TestPackage")
            assertThat(name.className).isEqualTo("TestClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(testResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
        with(result[1]) {
            assertThat(name.packageName).isEqualTo("EqualPackage")
            assertThat(name.className).isEqualTo("EqualClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(equalResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
    }

    @Test
    fun filterLowPriority_filtersAfterFirstDifferentDefault() {
        // Arrange
        val testIntent = Intent("TestAction")
        val testResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val equalResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val diffResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = false
            }
        val testInput =
            listOf(
                ResolvedComponentInfo(
                    ComponentName("TestPackage", "TestClass"),
                    testIntent,
                    testResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("EqualPackage", "EqualClass"),
                    testIntent,
                    equalResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("DiffPackage", "DiffClass"),
                    testIntent,
                    diffResolveInfo,
                ),
            )

        // Act
        val result = resolvedComponentFiltering.filterLowPriority(testInput)

        // Assert
        assertThat(result).hasSize(2)
        with(result.first()) {
            assertThat(name.packageName).isEqualTo("TestPackage")
            assertThat(name.className).isEqualTo("TestClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(testResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
        with(result[1]) {
            assertThat(name.packageName).isEqualTo("EqualPackage")
            assertThat(name.className).isEqualTo("EqualClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(equalResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
    }

    @Test
    fun filterLowPriority_whenNoDifference_returnsOriginal() {
        // Arrange
        val testIntent = Intent("TestAction")
        val testResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val equalResolveInfo =
            ResolveInfo().apply {
                priority = 1
                isDefault = true
            }
        val testInput =
            listOf(
                ResolvedComponentInfo(
                    ComponentName("TestPackage", "TestClass"),
                    testIntent,
                    testResolveInfo,
                ),
                ResolvedComponentInfo(
                    ComponentName("EqualPackage", "EqualClass"),
                    testIntent,
                    equalResolveInfo,
                ),
            )

        // Act
        val result = resolvedComponentFiltering.filterLowPriority(testInput)

        // Assert
        assertThat(result).hasSize(2)
        with(result.first()) {
            assertThat(name.packageName).isEqualTo("TestPackage")
            assertThat(name.className).isEqualTo("TestClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(testResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
        with(result[1]) {
            assertThat(name.packageName).isEqualTo("EqualPackage")
            assertThat(name.className).isEqualTo("EqualClass")
            assertThat(getIntentAt(0)).isEqualTo(testIntent)
            assertThrows(IndexOutOfBoundsException::class.java) { getIntentAt(1) }
            assertThat(getResolveInfoAt(0)).isEqualTo(equalResolveInfo)
            assertThrows(IndexOutOfBoundsException::class.java) { getResolveInfoAt(1) }
        }
    }
}
