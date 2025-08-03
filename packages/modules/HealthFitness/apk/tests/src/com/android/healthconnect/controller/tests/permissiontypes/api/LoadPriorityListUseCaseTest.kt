package com.android.healthconnect.controller.tests.permissiontypes.api

import android.content.Context
import android.health.connect.FetchDataOriginsPriorityOrderResponse
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissiontypes.api.LoadPriorityListUseCase
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.CoroutineTestRule
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class LoadPriorityListUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val manager: HealthConnectManager = Mockito.mock(HealthConnectManager::class.java)
    @Inject lateinit var appInfoReader: AppInfoReader
    private lateinit var usecase: LoadPriorityListUseCase
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        usecase = LoadPriorityListUseCase(manager, appInfoReader, Dispatchers.Main)
    }

    @Test
    fun loadPriorityList_listOfAppsInPriorityListReturnedCorrectly() = runTest {
        val dataOriginsPriorityOrderResponse =
            FetchDataOriginsPriorityOrderResponse(
                mutableListOf(
                    getDataOrigin(TEST_APP_PACKAGE_NAME), getDataOrigin(TEST_APP_PACKAGE_NAME_2)))

        Mockito.doAnswer(prepareAnswer(dataOriginsPriorityOrderResponse))
            .`when`(manager)
            .fetchDataOriginsPriorityOrder(
                Matchers.eq(HealthDataCategory.ACTIVITY), Matchers.any(), Matchers.any())

        val loadedAppsPriorityList = usecase.execute(HealthDataCategory.ACTIVITY)

        Truth.assertThat(loadedAppsPriorityList.size).isEqualTo(2)

        Truth.assertThat(loadedAppsPriorityList)
            .contains(appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME))

        Truth.assertThat(loadedAppsPriorityList)
            .contains(appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_2))
    }

    private fun prepareAnswer(
        fetchDataOriginsPriorityOrderResponse: FetchDataOriginsPriorityOrderResponse
    ): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<FetchDataOriginsPriorityOrderResponse, *>
            receiver.onResult(fetchDataOriginsPriorityOrderResponse)
            null
        }
        return answer
    }
}
