package com.android.healthconnect.controller.tests.data.access

import com.android.healthconnect.controller.data.access.AppAccessState
import com.android.healthconnect.controller.data.access.ILoadAccessUseCase
import com.android.healthconnect.controller.data.access.LoadAccessUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.di.FakeGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadPermissionTypeContributorAppsUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
@HiltAndroidTest
class LoadAccessUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var useCase: ILoadAccessUseCase
    private val fakeLoadPermissionTypeContributorAppsUseCase =
        FakeLoadPermissionTypeContributorAppsUseCase()
    private val fakeFakeGetGrantedHealthPermissionsUseCase =
        FakeGetGrantedHealthPermissionsUseCase()

    @Inject lateinit var appInfoReader: AppInfoReader
    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        useCase =
            LoadAccessUseCase(
                fakeLoadPermissionTypeContributorAppsUseCase,
                fakeFakeGetGrantedHealthPermissionsUseCase,
                healthPermissionReader,
                appInfoReader,
                Dispatchers.Main)
    }

    @Test
    fun invoke_noDataNorPermission_returnsEmptyMap() = runTest {
        val actual = (useCase.invoke(HealthPermissionType.STEPS) as UseCaseResults.Success).data

        assertThat(actual[AppAccessState.Write]!!.size).isEqualTo(0)
        assertThat(actual[AppAccessState.Read]!!.size).isEqualTo(0)
        assertThat(actual[AppAccessState.Inactive]!!.size).isEqualTo(0)
    }

    @Test
    fun invoke_returnsCorrectApps() = runTest {
        fakeLoadPermissionTypeContributorAppsUseCase.updateList(listOf(TEST_APP, TEST_APP_2))
        val writeSteps =
            HealthPermission(HealthPermissionType.STEPS, PermissionsAccessType.WRITE).toString()
        fakeFakeGetGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(writeSteps))

        val actual = (useCase.invoke(HealthPermissionType.STEPS) as UseCaseResults.Success).data

        assertThat(actual[AppAccessState.Write]).isNotNull()
        assertThat(actual[AppAccessState.Write]!!.size).isEqualTo(1)
        assertThat(actual[AppAccessState.Write]!![0].packageName).isEqualTo(TEST_APP_PACKAGE_NAME)
        assertThat(actual[AppAccessState.Write]!![0].appName).isEqualTo(TEST_APP_NAME)
        assertThat(actual[AppAccessState.Read]).isNotNull()
        assertThat(actual[AppAccessState.Read]!!.size).isEqualTo(0)
        assertThat(actual[AppAccessState.Inactive]).isNotNull()
        assertThat(actual[AppAccessState.Inactive]!!.size).isEqualTo(1)
        assertThat(actual[AppAccessState.Inactive]!![0].packageName)
            .isEqualTo(TEST_APP_PACKAGE_NAME_2)
        assertThat(actual[AppAccessState.Inactive]!![0].appName).isEqualTo(TEST_APP_NAME_2)
    }
}
