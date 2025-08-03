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
package com.android.healthconnect.controller.tests.route

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.health.connect.HealthConnectManager.EXTRA_EXERCISE_ROUTE
import android.health.connect.HealthConnectManager.EXTRA_SESSION_ID
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.widget.Button
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.route.ExerciseRouteViewModel
import com.android.healthconnect.controller.route.ExerciseRouteViewModel.SessionWithAttribution
import com.android.healthconnect.controller.route.RouteRequestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.RouteRequestElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@HiltAndroidTest
class RouteRequestActivityTest {

    private val START = Instant.ofEpochMilli(1234567891011)
    private val TEST_SESSION =
        ExerciseSessionRecord.Builder(
                getMetaData(),
                START,
                START.plusMillis(123456),
                ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
            .setTitle("Session title")
            .setRoute(
                ExerciseRoute(
                    listOf(
                        ExerciseRoute.Location.Builder(START.plusSeconds(12), 52.26019, 21.02268)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(40), 52.26000, 21.02360)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(48), 52.25973, 21.02356)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(60), 52.25966, 21.02313)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(78), 52.25993, 21.02309)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(79), 52.25972, 21.02271)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(90), 52.25948, 21.02276)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(93), 52.25945, 21.02335)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(94), 52.25960, 21.02338)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(100), 52.25961, 21.02382)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(102), 52.25954, 21.02370)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(105), 52.25945, 21.02362)
                            .build(),
                        ExerciseRoute.Location.Builder(START.plusSeconds(109), 52.25954, 21.02354)
                            .build(),
                    )))
            .build()

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue
    val viewModel: ExerciseRouteViewModel = Mockito.mock(ExerciseRouteViewModel::class.java)
    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)
    private lateinit var context: Context
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        context = getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        whenever(viewModel.isReadRoutesPermissionDeclared(context.packageName)).thenReturn(true)
        whenever(viewModel.isSessionInaccessible(context.packageName, TEST_SESSION))
            .thenReturn(false)
        // disable animations
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
        // enable animations
        toggleAnimation(true)
    }

    @Test
    fun intentLaunchesRouteRequestActivity_noSessionId() {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isFalse()
    }

    @Test
    fun intentLaunchesRouteRequestActivity_nullSessionId() {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .putExtra(EXTRA_SESSION_ID, null as String?)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isFalse()
    }

    @Test
    fun intentLaunchesRouteRequestActivity_noRoute() {
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(
                SessionWithAttribution(
                    ExerciseSessionRecord.Builder(
                            getMetaData(),
                            START,
                            START.plusMillis(123456),
                            ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                        .setTitle("Session title")
                        .setRoute(ExerciseRoute(listOf()))
                        .build(),
                    TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isFalse()
    }

    @Test
    fun intentLaunchesRouteRequestActivity_emptyRoute() {
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(
                SessionWithAttribution(
                    ExerciseSessionRecord.Builder(
                            getMetaData(),
                            START,
                            START.plusMillis(123456),
                            ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                        .setTitle("Session title")
                        .setRoute(ExerciseRoute(listOf()))
                        .build(),
                    TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isFalse()
    }

    @Test
    fun intentLaunchesRouteRequestActivity() {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .putExtra(EXTRA_SESSION_ID, "sessionID")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        onView(withText("This app will be able to read your past location in the route"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Allow this route")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Allow Health Connect to access this exercise route in Health Connect?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Session title")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("February 13, 2009 • Health Connect test app"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withId(R.id.map_view)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Don\'t allow"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_REQUEST_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_DIALOG_ROUTE_VIEW)
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_DIALOG_INFORMATION_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_DIALOG_ALLOW_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_DIALOG_DONT_ALLOW_BUTTON)
    }

    @Test
    fun intentLaunchesRouteRequestActivity_sessionWithNoTitle() {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .putExtra(EXTRA_SESSION_ID, "sessionID")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(
                SessionWithAttribution(
                    ExerciseSessionRecord.Builder(
                            getMetaData(),
                            START,
                            START.plusMillis(123456),
                            ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                        .setRoute(
                            ExerciseRoute(
                                listOf(
                                    ExerciseRoute.Location.Builder(
                                            START.plusSeconds(12), 52.26019, 21.02268)
                                        .build(),
                                    ExerciseRoute.Location.Builder(
                                            START.plusSeconds(40), 52.26000, 21.02360)
                                        .build())))
                        .build(),
                    TEST_APP))
        }

        launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        onView(withText("Allow this route")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Allow Health Connect to access this exercise route in Health Connect?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("This app will be able to read your past location in the route"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Running")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("February 13, 2009 • Health Connect test app"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withId(R.id.map_view)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Don\'t allow"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun intentLaunchesRouteRequestActivity_infoDialog() {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .putExtra(EXTRA_SESSION_ID, "sessionID")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        scenario.onActivity { activity: RouteRequestActivity ->
            activity.dialog?.findViewById<LinearLayout>(R.id.more_info)?.callOnClick()
        }

        verify(healthConnectLogger)
            .logInteraction(RouteRequestElement.EXERCISE_ROUTE_DIALOG_INFORMATION_BUTTON)

        onView(withText("Exercise routes include location information"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Who can see this data?")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Only apps you allow to access your exercise routes"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("How can I manage access?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("You can manage app access to exercise routes in Health Connect settings"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_EDUCATION_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(RouteRequestElement.EXERCISE_ROUTE_EDUCATION_DIALOG_BACK_BUTTON)
    }

    @Test
    fun intentLaunchesRouteRequestActivity_dontAllow() {
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        scenario.onActivity { activity: RouteRequestActivity ->
            activity.dialog?.findViewById<Button>(R.id.route_dont_allow_button)?.callOnClick()
        }

        verify(healthConnectLogger)
            .logInteraction(RouteRequestElement.EXERCISE_ROUTE_DIALOG_DONT_ALLOW_BUTTON)
        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isFalse()
    }

    @Test
    fun intentLaunchesRouteRequestActivity_allow() {
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        scenario.onActivity { activity: RouteRequestActivity ->
            activity.dialog?.findViewById<Button>(R.id.route_allow_button)?.callOnClick()
        }

        verify(healthConnectLogger)
            .logInteraction(RouteRequestElement.EXERCISE_ROUTE_DIALOG_ALLOW_BUTTON)

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isTrue()
        assertThat(returnedIntent.getParcelableExtra<ExerciseRoute>(EXTRA_EXERCISE_ROUTE))
            .isEqualTo(TEST_SESSION.route)
    }

    @Test
    fun intentLaunchesRouteRequestActivity_alwaysAllow() {
        val startActivityIntent = getRouteActivityIntent()
        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        scenario.onActivity { activity: RouteRequestActivity ->
            activity.dialog?.findViewById<Button>(R.id.route_allow_all_button)?.callOnClick()
        }

        verify(healthConnectLogger)
            .logInteraction(RouteRequestElement.EXERCISE_ROUTE_DIALOG_ALWAYS_ALLOW_BUTTON)
        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.hasExtra(EXTRA_EXERCISE_ROUTE)).isTrue()
        assertThat(returnedIntent.getParcelableExtra<ExerciseRoute>(EXTRA_EXERCISE_ROUTE))
            .isEqualTo(TEST_SESSION.route)
        verify(viewModel).grantReadRoutesPermission(context.packageName)
    }

    @Test
    fun intent_migrationInProgress_shoesMigrationInProgressDialog() = runTest {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IN_PROGRESS,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IN_PROGRESS,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)
        onView(
                withText(
                    "Health Connect is being integrated with the Android system.\n\nYou'll get a notification when the process is complete and you can use Health Connect with Health Connect."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(isDisplayed()))

        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(ViewActions.click())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to make sure activity is destroyed
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun intent_restoreInProgress_showsRestoreInProgressDialog() = runTest {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        val scenario = launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        onView(withText("Health Connect restore in progress"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health Connect is restoring data and permissions. This may take some time to complete."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(ViewActions.click())
        verify(healthConnectLogger)
            .logInteraction(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to make sure activity is destroyed
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun intent_migrationPending_showsMigrationPendingDialog() = runTest {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val startActivityIntent = getRouteActivityIntent()

        whenever(viewModel.exerciseSession).then {
            MutableLiveData(SessionWithAttribution(TEST_SESSION, TEST_APP))
        }

        launchActivityForResult<RouteRequestActivity>(startActivityIntent)

        onView(
                withText(
                    "Health Connect is ready to be integrated with your Android system. If you give Health Connect access now, some features may not work until integration is complete."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        // TODO (b/322495982) check navigation to Migration activity
        onView(withText("Start integration")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Continue")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CANCEL_BUTTON)

        onView(withText("Continue")).inRoot(isDialog()).perform(ViewActions.click())
        onView(withText("Continue")).check(ViewAssertions.doesNotExist())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
    }

    private fun getRouteActivityIntent(): Intent {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, RouteRequestActivity::class.java))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
                .putExtra(EXTRA_SESSION_ID, "sessionID")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return startActivityIntent
    }
}
