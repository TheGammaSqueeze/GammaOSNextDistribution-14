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

package com.android.healthconnect.controller.tests

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.migration.MigrationActivity
import com.android.healthconnect.controller.onboarding.OnboardingActivity
import com.android.healthconnect.controller.route.RouteRequestActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManifestTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun onboardingActivity_shouldNotBeExported() {
        val intent = Intent(context, OnboardingActivity::class.java)
        val info = intent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_ALL)
        assertThat(info.exported).isFalse()
    }

    @Test
    fun migrationActivity_shouldBeExported() {
        val intent = Intent(context, MigrationActivity::class.java)
        val info = intent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_ALL)
        assertThat(info.exported).isTrue()
    }

    @Test
    fun routeActivity_shouldBeProtectedByPermissionReadExercise() {
        val intent = Intent(context, RouteRequestActivity::class.java)
        val info = intent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_ALL)
        assertThat(info.permission).isEqualTo("android.permission.health.READ_EXERCISE")
    }


    @Test
    fun routeRequestActivity_shouldBeExported() {
        val intent = Intent(context, RouteRequestActivity::class.java)
        val info = intent.resolveActivityInfo(context.packageManager, PackageManager.MATCH_ALL)
        assertThat(info.exported).isTrue()
    }
}
