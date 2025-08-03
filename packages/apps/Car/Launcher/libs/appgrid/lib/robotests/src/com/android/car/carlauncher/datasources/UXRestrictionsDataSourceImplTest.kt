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
package com.android.car.carlauncher.datasources

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.testapi.FakeCar
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Field
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class UXRestrictionsDataSourceImplTest {

    private val scope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")
    private val fakeCar: FakeCar =
        FakeCar.createFakeCar(ApplicationProvider.getApplicationContext())
    private val carUxRestrictionsManager =
        fakeCar.car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
    private val carUxRestrictionsController = fakeCar.carUxRestrictionController

    /**
     * Updates the CarUxRestrictions and notifies any active listeners.
     *
     * TODO: b/319266967 - FakeCarUxRestrictionsService always sets requiresDistractionOptimization
     *  to 'false' when creating CarUxRestrictions.
     *  Use reflection to set CarUxRestrictions with mRequiresDistractionOptimization as true.
     */
    private fun updateCarUxRestrictions(isDistractionOptimized: Boolean) {
        // Set Restrictions to trigger callback.
        carUxRestrictionsController.setUxRestrictions(CarUxRestrictions.UX_RESTRICTIONS_BASELINE)

        val carUxRestrictionsField: Field =
            carUxRestrictionsController.javaClass.getDeclaredField("mCarUxRestrictions")
        carUxRestrictionsField.isAccessible = true
        val currentCarUxRestrictions =
            carUxRestrictionsField[carUxRestrictionsController] as CarUxRestrictions
        val requiresDistractionOptimizationField: Field =
            currentCarUxRestrictions.javaClass.getDeclaredField("mRequiresDistractionOptimization")
        requiresDistractionOptimizationField.isAccessible = true

        requiresDistractionOptimizationField[currentCarUxRestrictions] = isDistractionOptimized
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requiresDistractionOptimization_sendsRequired() =
        scope.runTest {
            val uxRestrictionDataSource =
                UXRestrictionDataSourceImpl(carUxRestrictionsManager, bgDispatcher)
            val outputFlows = mutableListOf<Boolean>()

            launch(StandardTestDispatcher(testScheduler)) {
                uxRestrictionDataSource.requiresDistractionOptimization().toList(outputFlows)
            }
            advanceUntilIdle()
            updateCarUxRestrictions(isDistractionOptimized = true)
            ShadowLooper.runUiThreadTasks()
            advanceUntilIdle()
            coroutineContext.cancelChildren()

            // Asserts initial value of the DO(Distraction Optimization) as false and updated
            // callback as true.
            assertEquals(listOf(false, true), outputFlows)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requiresDistractionOptimization_sendsNotRequired() = scope.runTest {
        val uxRestrictionDataSource =
            UXRestrictionDataSourceImpl(carUxRestrictionsManager, bgDispatcher)
        val outputFlows = mutableListOf<Boolean>()

        launch(StandardTestDispatcher(testScheduler)) {
            uxRestrictionDataSource.requiresDistractionOptimization().toList(outputFlows)
        }
        advanceUntilIdle()
        updateCarUxRestrictions(isDistractionOptimized = false)
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        // Asserts initial value of the DO(Distraction Optimization) as false and updated
        // callback as false.
        assertEquals(listOf(false, false), outputFlows)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requiresDistractionOptimization_scopeClosed_shouldCleanUp() = scope.runTest {
        val uxRestrictionDataSource =
            UXRestrictionDataSourceImpl(carUxRestrictionsManager, bgDispatcher)
        val outputFlows = mutableListOf<Boolean>()

        launch(StandardTestDispatcher(testScheduler)) {
            uxRestrictionDataSource.requiresDistractionOptimization().toList(outputFlows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()
        advanceUntilIdle()

        assertFalse(carUxRestrictionsController.isListenerRegistered)
    }
}
