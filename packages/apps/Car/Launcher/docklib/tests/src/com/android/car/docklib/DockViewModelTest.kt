/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.docklib

import android.app.ActivityManager
import android.car.content.pm.CarPackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.data.DockAppItem
import com.android.launcher3.icons.IconFactory
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockViewModelTest {
    private companion object {
        private const val CURRENT_USER_ID = 10
        private const val MAX_ITEMS = 4
        private const val TOAST_STR = "TOAST_STR"
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var items: List<DockAppItem>
    private lateinit var model: DockViewModel
    private val resourcesMock = mock<Resources> {}
    private val contextMock = mock<Context> {
        on { getString(eq(R.string.pin_failed_no_spots)) } doReturn TOAST_STR
        on { resources } doReturn resourcesMock
    }
    private val packageManagerMock = mock<PackageManager> {
        on {
            queryIntentServices(any<Intent>(), any<PackageManager.ResolveInfoFlags>())
        } doReturn listOf()
    }
    private var carPackageManagerMock = mock<CarPackageManager> {}
    private var bitmapMock = mock<Bitmap> {}
    private var iconFactoryMock = mock<IconFactory> {
        on { createScaledBitmap(any<Drawable>(), anyInt()) } doReturn bitmapMock
    }

    @Before
    fun setUp() {
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities =
                createTestComponentList(
                        pkgPrefix = "LAUNCHER_PKG", classPrefix = "LAUNCHER_CLASS").toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doNothing().whenever(model).showToast(any())
    }

    @Test
    fun init_defaultPinnedItems_addedToDock() {
        val defaultPinnedItems =
                createTestComponentList(pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS")

        model = DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities =
                createTestComponentList(
                    pkgPrefix = "LAUNCHER_PKG", classPrefix = "LAUNCHER_CLASS").toMutableSet(),
                defaultPinnedItems = defaultPinnedItems,
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        )

        assertThat(items.size).isEqualTo(MAX_ITEMS)
        assertThat(items[0].component).isEqualTo(defaultPinnedItems[0])
        assertThat(items[1].component).isEqualTo(defaultPinnedItems[1])
        assertThat(items[2].component).isEqualTo(defaultPinnedItems[2])
        assertThat(items[3].component).isEqualTo(defaultPinnedItems[3])
    }

    @Test
    fun addDynamicItem_emptyDockList_index0Updated() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")

        model.internalItems.clear()
        model.addDynamicItem(newComponent)

        assertThat(items[0].component).isEqualTo(newComponent)
    }

    @Test
    fun addDynamicItem_allItemsDynamic_leastRecentItemUpdated() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        model.internalItems.clear()
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC)
        }

        model.addDynamicItem(newComponent)

        assertThat(items.size).isEqualTo(MAX_ITEMS)
        assertThat(items[0].component).isEqualTo(newComponent)
    }

    @Test
    fun addDynamicItem_appInDock_itemsNotChanged() {
        val existingComponent = createNewComponent(pkg = "da0", clazz = "da0")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.addDynamicItem(existingComponent)

        assertThat(items.size).isEqualTo(MAX_ITEMS)
        assertThat(items.filter { it.component == existingComponent }.size).isEqualTo(1)
    }

    @Test
    fun addDynamicItem_appInDock_recencyRefreshed() {
        val existingComponent = createNewComponent(pkg = "da0", clazz = "da0")
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.addDynamicItem(existingComponent)
        model.addDynamicItem(newComponent)

        assertThat(items.size).isEqualTo(MAX_ITEMS)
        assertThat(items[1].component).isEqualTo(newComponent)
    }

    @Test
    fun pinItem_itemWithIdNotInDock_itemNotPinned() {
        val idNotInDock = UUID.nameUUIDFromBytes("idNotInDock".toByteArray())
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    id = UUID.nameUUIDFromBytes("id$i".toByteArray()),
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.pinItem(idNotInDock)

        items.forEach { assertThat(it.type).isEqualTo(DockAppItem.Type.DYNAMIC) }
    }

    @Test
    fun pinItem_itemWithIdInDock_itemTypeStatic_itemIdUnchanged() {
        val idInDock = UUID.nameUUIDFromBytes("id0".toByteArray())
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    id = UUID.nameUUIDFromBytes("id$i".toByteArray()),
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.pinItem(idInDock)

        val dockItem = items.firstOrNull { it.id == idInDock }
        assertThat(dockItem).isNotNull()
        assertThat(dockItem?.type).isEqualTo(DockAppItem.Type.STATIC)
    }

    @Test
    fun pinItem_indexLessThanZero_itemNotPinned() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.pinItem(newComponent, indexToPin = -1)

        items.forEach {
            assertThat(it.component).isNotEqualTo(newComponent)
        }
    }

    @Test
    fun pinItem_indexGreaterThanMaxItems_itemNotPinned() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.pinItem(newComponent, indexToPin = MAX_ITEMS + 1)

        items.forEach {
            assertThat(it.component).isNotEqualTo(newComponent)
        }
    }

    @Test
    fun pinItem_indexProvided_itemPinnedToIndex() {
        val indexToPin = 2
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.DYNAMIC
            )
        }

        model.pinItem(newComponent, indexToPin)

        assertThat(items[
                indexToPin].component).isEqualTo(newComponent)
        assertThat(items[indexToPin].type).isEqualTo(DockAppItem.Type.STATIC)
    }

    @Test
    fun pinItem_indexNotProvided_noDynamicItemOrEmptyIndex_itemNotPinned() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.STATIC
            )
        }

        model.pinItem(newComponent, indexToPin = null)

        items.forEach { assertThat(it.component).isNotEqualTo(newComponent) }
    }

    @Test
    fun pinItem_indexNotProvided_noDynamicItemOrEmptyIndex_toastShown() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    app = "da$i",
                    type = DockAppItem.Type.STATIC
            )
        }

        model.pinItem(newComponent, indexToPin = null)

        verify(model).showToast(eq(TOAST_STR))
    }

    @Test
    fun pinItem_indexNotProvided_dynamicItemsPresent_itemPinnedToFirstDynamicItemIndex() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        model.internalItems.compute(0) { _, item -> item?.copy(type = DockAppItem.Type.STATIC) }
        model.internalItems.compute(1) { _, item -> item?.copy(type = DockAppItem.Type.DYNAMIC) }
        model.internalItems.compute(2) { _, item -> item?.copy(type = DockAppItem.Type.DYNAMIC) }
        model.internalItems.compute(3) { _, item -> item?.copy(type = DockAppItem.Type.STATIC) }

        model.pinItem(newComponent, indexToPin = null)

        val index = items.indexOfFirst { it.component == newComponent }
        assertThat(index).isEqualTo(1)
        assertThat(items[index].type).isEqualTo(DockAppItem.Type.STATIC)
    }

    @Test
    fun pinItem_indexNotProvided_emptyIndexesPresent_itemPinnedToFirstEmptyIndex() {
        val newComponent = createNewComponent(pkg = "newpkg", clazz = "newclass")
        model.internalItems.replaceAll { _, item -> item.copy(type = DockAppItem.Type.STATIC) }
        model.internalItems.remove(1)

        model.pinItem(newComponent, indexToPin = null)

        val index = items.indexOfFirst { it.component == newComponent }
        assertThat(index).isEqualTo(1)
        assertThat(items[index].type).isEqualTo(DockAppItem.Type.STATIC)
    }

    @Test
    fun removeItem_itemWithIdNotInDock_itemNotRemoved() {
        val idNotInDock = UUID.nameUUIDFromBytes("idNotInDock".toByteArray())
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    id = UUID.nameUUIDFromBytes("id$i".toByteArray()),
                    app = "da$i",
                    type = DockAppItem.Type.STATIC
            )
        }
        val listBeforeRemove = model.internalItems.values.toList()

        model.removeItem(idNotInDock)

        listBeforeRemove.forEachIndexed { key, item -> assertThat(items[key]).isEqualTo(item) }
    }

    @Test
    fun removeItem_itemWithIdInDock_itemRemoved() {
        val idInDock = UUID.nameUUIDFromBytes("id2".toByteArray())
        for (i in 0..<MAX_ITEMS) {
            model.internalItems[i] = TestUtils.createAppItem(
                    id = UUID.nameUUIDFromBytes("id$i".toByteArray()),
                    app = "da$i",
                    type = DockAppItem.Type.STATIC
            )
        }

        model.removeItem(idInDock)

        items.forEach { item -> assertThat(item.id).isNotEqualTo(idInDock) }
    }

    @Test
    fun createDockList_indexNotFilled_noRecentTasks_noLauncherApp_errorThrown() {
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = mutableSetOf(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(List<ActivityManager.RunningTaskInfo>(0) { return })
                .whenever(model).getRunningTasks()
        model.internalItems.remove(2)

        assertThrows(IllegalStateException::class.java) { model.createDockList() }
    }

    @Test
    fun createDockList_indexNotFilled_noRecentTasks_launcherActivitiesExcluded_errorThrown() {
        val cmpList = createTestComponentList(pkgPrefix = "testPkg", classPrefix = "testClass")
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = cmpList.toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = cmpList.toSet(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(List<ActivityManager.RunningTaskInfo>(0) { return })
                .whenever(model).getRunningTasks()
        model.internalItems.remove(2)

        assertThrows(IllegalStateException::class.java) { model.createDockList() }
    }

    @Test
    fun createDockList_indexNotFilled_noRecentTasks_launcherActivitiesAlreadyInDock_errorThrown() {
        val launcherAppComponent = createNewComponent(pkg = "testPkg", clazz = "testClass")
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = mutableSetOf(launcherAppComponent),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(List<ActivityManager.RunningTaskInfo>(0) { return })
                .whenever(model).getRunningTasks()
        model.internalItems[2] = TestUtils.createAppItem(component = launcherAppComponent)
        model.internalItems.remove(3)

        assertThrows(IllegalStateException::class.java) { model.createDockList() }
    }

    @Test
    fun createDockList_indexNotFilled_noRecentTasks_randomLauncherAppAdded() {
        val launcherActivities = createTestComponentList(
                pkgPrefix = "testPkg",
                classPrefix = "testClass"
        )
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = launcherActivities.toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(List<ActivityManager.RunningTaskInfo>(0) { return })
                .whenever(model).getRunningTasks()
        model.internalItems.remove(2)

        val dockList = model.createDockList()

        assertThat(launcherActivities.contains(dockList[2].component)).isTrue()
    }

    @Test
    fun createDockList_indexNotFilled_noRecentTasksForCurrentUser_randomLauncherAppAdded() {
        val launcherActivities = createTestComponentList(
                pkgPrefix = "testPkg",
                classPrefix = "testClass"
        )
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = launcherActivities.toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(createTestRunningTaskInfoList(userId = CURRENT_USER_ID + 1))
                .whenever(model)
                .getRunningTasks()
        model.internalItems.remove(2)

        val dockList = model.createDockList()

        assertThat(launcherActivities.contains(dockList[2].component)).isTrue()
    }

    @Test
    fun createDockList_indexNotFilled_recentTasksExcluded_randomLauncherAppAdded() {
        val launcherActivities = createTestComponentList(
                pkgPrefix = "testPkg",
                classPrefix = "testClass"
        )
        val excludedComponent = createNewComponent(pkg = "excludedPkg", clazz = "excludedClass")
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = launcherActivities.toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(excludedComponent),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(createTestRunningTaskInfoList(component = excludedComponent))
                .whenever(model)
                .getRunningTasks()
        model.internalItems.remove(2)

        val dockList = model.createDockList()

        assertThat(launcherActivities.contains(dockList[2].component)).isTrue()
    }

    @Test
    fun createDockList_indexNotFilled_recentTasksAlreadyInDock_randomLauncherAppAdded() {
        val launcherActivities = createTestComponentList(
                pkgPrefix = "testPkg",
                classPrefix = "testClass"
        )
        val recentTaskComponent =
                createNewComponent(pkg = "recentTaskPkg", clazz = "recentTaskClass")
        model = spy(DockViewModel(
                maxItemsInDock = MAX_ITEMS,
                context = contextMock,
                packageManager = packageManagerMock,
                carPackageManager = carPackageManagerMock,
                userId = CURRENT_USER_ID,
                launcherActivities = launcherActivities.toMutableSet(),
                defaultPinnedItems =
                createTestComponentList(
                        pkgPrefix = "DEFAULT_PKG", classPrefix = "DEFAULT_CLASS"),
                excludedComponents = setOf(),
                excludedPackages = setOf(),
                iconFactory = iconFactoryMock,
                observer = { items = it },
        ))
        doReturn(createTestRunningTaskInfoList(component = recentTaskComponent))
                .whenever(model)
                .getRunningTasks()
        model.internalItems[2] = TestUtils.createAppItem(component = recentTaskComponent)
        model.internalItems.remove(3)

        val dockList = model.createDockList()

        assertThat(launcherActivities.contains(dockList[3].component)).isTrue()
    }

    @Test
    fun createDockList_indexNotFilled_recentTasksAdded() {
        val recentTaskComponent =
                createNewComponent(pkg = "recentTaskPkg", clazz = "recentTaskClass")
        val recentTaskList = createTestRunningTaskInfoList(component = recentTaskComponent)
        doReturn(recentTaskList).whenever(model).getRunningTasks()
        model.internalItems.remove(2)

        val dockList = model.createDockList()

        assertThat(dockList[2].component).isEqualTo(recentTaskComponent)
    }

    @Test
    fun removeItems_itemsWithPackageNameInDock_itemsRemoved() {
        val pkgToBeRemoved = "pkgToBeRemoved"
        val pkgOther = "pkgOther"
        model.internalItems[0] =
                TestUtils.createAppItem(type = DockAppItem.Type.DYNAMIC, pkg = pkgToBeRemoved)
        model.internalItems[1] =
                TestUtils.createAppItem(type = DockAppItem.Type.STATIC, pkg = pkgOther)
        model.internalItems[2] =
                TestUtils.createAppItem(type = DockAppItem.Type.STATIC, pkg = pkgToBeRemoved)
        model.internalItems[3] =
                TestUtils.createAppItem(type = DockAppItem.Type.DYNAMIC, pkg = pkgOther)

        model.removeItems(pkgToBeRemoved)

        items.forEach { assertThat(it.component.packageName).isNotEqualTo(pkgToBeRemoved) }
    }

    @Test
    fun removeItems_itemsWithPackageNameNotInDock_itemsNotRemoved() {
        val pkgToBeRemoved = "pkgToBeRemoved"
        val pkgOther = "pkgOther"
        model.internalItems[0] =
                TestUtils.createAppItem(type = DockAppItem.Type.DYNAMIC, pkg = pkgOther)
        model.internalItems[1] =
                TestUtils.createAppItem(type = DockAppItem.Type.STATIC, pkg = pkgOther)
        model.internalItems[2] =
                TestUtils.createAppItem(type = DockAppItem.Type.STATIC, pkg = pkgOther)
        model.internalItems[3] =
                TestUtils.createAppItem(type = DockAppItem.Type.DYNAMIC, pkg = pkgOther)

        model.removeItems(pkgToBeRemoved)

        items.forEach { assertThat(it.component.packageName).isNotEqualTo(pkgToBeRemoved) }
    }

    @Test
    fun setCarPackageManager_distractionValuesUpdated() {
        for (i in 0..<4) {
            model.internalItems[i] = TestUtils.createAppItem(
                    pkg = "pkg$i",
                    clazz = "class$i",
                    isDrivingOptimized = false
            )
        }
        whenever(carPackageManagerMock.isActivityDistractionOptimized(
                eq("pkg0"),
                eq("class0")
        )).thenReturn(true)
        whenever(carPackageManagerMock.isActivityDistractionOptimized(
                eq("pkg1"),
                eq("class1")
        )).thenReturn(false)
        whenever(carPackageManagerMock.isActivityDistractionOptimized(
                eq("pkg2"),
                eq("class2")
        )).thenReturn(true)
        whenever(carPackageManagerMock.isActivityDistractionOptimized(
                eq("pkg3"),
                eq("class3")
        )).thenReturn(false)

        model.setCarPackageManager(carPackageManagerMock)

        items.forEach {
            when (it.component.packageName) {
                "pkg0" -> assertThat(it.isDistractionOptimized).isEqualTo(true)
                "pkg1" -> assertThat(it.isDistractionOptimized).isEqualTo(false)
                "pkg2" -> assertThat(it.isDistractionOptimized).isEqualTo(true)
                "pkg3" -> assertThat(it.isDistractionOptimized).isEqualTo(false)
            }
        }
    }

    private fun createTestRunningTaskInfoList(
            pkgPrefix: String = "testRunningTaskInfo_PKG",
            classPrefix: String = "testRunningTaskInfo_CLASS",
            component: ComponentName? = null,
            userId: Int = CURRENT_USER_ID
    ): List<ActivityManager.RunningTaskInfo> {
        val recentTasks = mutableListOf<ActivityManager.RunningTaskInfo>()
        for (i in 1..10) {
            val t = mock<ActivityManager.RunningTaskInfo> {}
            t.userId = userId
            val cmp = component ?: createNewComponent(pkg = "$pkgPrefix$i", "$classPrefix$i")
            t.baseActivity = cmp
            val intent = mock<Intent> {}
            intent.component = cmp
            t.baseIntent = intent
            recentTasks.add(t)
        }
        return recentTasks
    }

    private fun createTestComponentList(
            pkgPrefix: String,
            classPrefix: String,
            isService: Boolean = false,
    ): List<ComponentName> {
        val launcherActivities = mutableListOf<ComponentName>()
        for (i in 1..10) {
            launcherActivities.add(
                createNewComponent(pkg = "$pkgPrefix$i", "$classPrefix$i", isService)
            )
        }
        return launcherActivities
    }

    private fun createNewComponent(
        pkg: String,
        clazz: String,
        isService: Boolean = false
    ): ComponentName {
        val component = ComponentName(pkg, clazz)
        val pi = createMockPackageInfo(component, isService)
        whenever(packageManagerMock.getPackageInfo(eq(pkg), any<PackageManager.PackageInfoFlags>()))
            .thenReturn(pi)
        return component
    }

    private fun createMockPackageInfo(
        component: ComponentName,
        isService: Boolean = false
    ): PackageInfo {
        val pi = mock<PackageInfo> {}
        if (isService) {
            pi.services = arrayOf(createMockServiceInfo(component))
        } else {
            pi.activities = arrayOf(createMockActivityInfo(component))
        }
        return pi
    }

    private fun createMockActivityInfo(component: ComponentName): ActivityInfo {
        val icon = mock<Drawable> {}
        val ai = mock<ActivityInfo> {
            on { loadIcon(any()) } doReturn icon
            on { loadLabel(any()) } doReturn "${component.packageName}-${component.className}"
            on { componentName } doReturn component
        }
        ai.name = "${component.packageName}-${component.className}"
        return ai
    }

    private fun createMockServiceInfo(component: ComponentName): ServiceInfo {
        val icon = mock<Drawable> {}
        val si = mock<ServiceInfo> {
            on { loadIcon(any()) } doReturn icon
            on { componentName } doReturn component
        }
        si.name = "${component.packageName}-${component.className}"
        return si
    }
}
