/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.NotificationListenerPregrants
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationListenerPregrantsTest {
    private lateinit var mockitoSession: MockitoSession
    private lateinit var context: Context

    private lateinit var notificationListenerPregrants: NotificationListenerPregrants

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        notificationListenerPregrants = NotificationListenerPregrants(context)
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun getPregrantedPackages() {
        val pregrants: Set<String> = notificationListenerPregrants.pregrantedPackages
        assertTrue(pregrants.isNotEmpty())
    }

    @Test
    fun pregrantedPackagesNotInitializedUntilUsed() {
        assertFalse(notificationListenerPregrants.pregrantedPackagesDelegate.isInitialized())
        notificationListenerPregrants.pregrantedPackages
        assertTrue(notificationListenerPregrants.pregrantedPackagesDelegate.isInitialized())
    }
}
