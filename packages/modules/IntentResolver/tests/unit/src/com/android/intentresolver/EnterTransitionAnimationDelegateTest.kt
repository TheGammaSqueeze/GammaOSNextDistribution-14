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

package com.android.intentresolver

import android.content.res.Resources
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val TIMEOUT_MS = 200

@OptIn(ExperimentalCoroutinesApi::class)
class EnterTransitionAnimationDelegateTest {
    private val elementName = "shared-element"
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val lifecycleOwner = TestLifecycleOwner()

    private val transitionTargetView =
        mock<View> {
            // avoid the request-layout path in the delegate
            whenever(isInLayout).thenReturn(true)
        }

    private val windowMock = mock<Window>()
    private val resourcesMock =
        mock<Resources> { whenever(getInteger(anyInt())).thenReturn(TIMEOUT_MS) }
    private val activity =
        mock<ComponentActivity> {
            whenever(lifecycle).thenReturn(lifecycleOwner.lifecycle)
            whenever(resources).thenReturn(resourcesMock)
            whenever(isActivityTransitionRunning).thenReturn(true)
            whenever(window).thenReturn(windowMock)
        }

    private val testSubject = EnterTransitionAnimationDelegate(activity) { transitionTargetView }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @After
    fun cleanup() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Dispatchers.resetMain()
    }

    @Test
    fun test_postponeTransition_timeout() {
        testSubject.postponeTransition()
        testSubject.markOffsetCalculated()

        scheduler.advanceTimeBy(TIMEOUT_MS + 1L)
        verify(activity, times(1)).startPostponedEnterTransition()
        verify(windowMock, never()).setWindowAnimations(anyInt())
    }

    @Test
    fun test_postponeTransition_animation_resumes_only_once() {
        testSubject.postponeTransition()
        testSubject.markOffsetCalculated()
        testSubject.onTransitionElementReady(elementName)
        testSubject.markOffsetCalculated()
        testSubject.onTransitionElementReady(elementName)

        scheduler.advanceTimeBy(TIMEOUT_MS + 1L)
        verify(activity, times(1)).startPostponedEnterTransition()
    }

    @Test
    fun test_postponeTransition_resume_animation_conditions() {
        testSubject.postponeTransition()
        verify(activity, never()).startPostponedEnterTransition()

        testSubject.markOffsetCalculated()
        verify(activity, never()).startPostponedEnterTransition()

        testSubject.onAllTransitionElementsReady()
        verify(activity, times(1)).startPostponedEnterTransition()
    }
}
