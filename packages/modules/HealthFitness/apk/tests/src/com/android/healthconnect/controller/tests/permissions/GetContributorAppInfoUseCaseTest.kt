/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions

import android.content.Context
import android.graphics.Bitmap
import android.health.connect.ApplicationInfoResponse
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.AppInfo
import android.os.OutcomeReceiver
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.app.GetContributorAppInfoUseCase
import com.android.healthconnect.controller.tests.utils.CoroutineTestRule
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers.*
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class GetContributorAppInfoUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private var manager: HealthConnectManager = Mockito.mock(HealthConnectManager::class.java)
    private lateinit var usecase: GetContributorAppInfoUseCase
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        usecase = GetContributorAppInfoUseCase(manager, context, Dispatchers.Main)
    }

    @Test
    fun invoke_noData_returnsEmptyMap() = runTest {
        Mockito.doAnswer(prepareAnswer(emptyList()))
            .`when`(manager)
            .getContributorApplicationsInfo(any(), any())

        assertThat(usecase.invoke()).isEmpty()
    }

    @Test
    fun invoke_withContributorApps_returnsAppsMap() = runTest {
        val bitmap =
            AppCompatResources.getDrawable(context, R.drawable.health_connect_logo)!!.toBitmap()
        val appInfo =
            listOf(
                AppInfo.Builder(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, bitmap).build(),
                AppInfo.Builder(TEST_APP_PACKAGE_NAME_2, TEST_APP_NAME_2, bitmap).build(),
            )

        Mockito.doAnswer(prepareAnswer(appInfo))
            .`when`(manager)
            .getContributorApplicationsInfo(any(), any())

        val result = usecase.invoke()
        assertThat(result.size).isEqualTo(2)
        assertThat(result).containsKey(TEST_APP_PACKAGE_NAME)
        assertThat(result).containsKey(TEST_APP_PACKAGE_NAME_2)
    }

    private fun prepareAnswer(apps: List<AppInfo>): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<ApplicationInfoResponse, *>
            receiver.onResult(ApplicationInfoResponse(apps))
            null
        }
        return answer
    }

    private fun getIconAsByteArray(): ByteArray {
        return try {
            val bitmap =
                AppCompatResources.getDrawable(context, R.drawable.health_connect_logo)!!.toBitmap()
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                stream.toByteArray()
            }
        } catch (exception: IOException) {
            throw IllegalArgumentException(exception)
        }
    }
}
