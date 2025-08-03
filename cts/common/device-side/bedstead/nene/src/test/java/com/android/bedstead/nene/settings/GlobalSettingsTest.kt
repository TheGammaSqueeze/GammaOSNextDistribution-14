/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.nene.settings

import android.os.Build
import android.provider.Settings
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class GlobalSettingsTest {
    @After
    fun resetGlobalSettings() {
        TestApis.settings().global().reset()
    }

    @Test
    @Throws(Exception::class)
    fun putInt_putsIntIntoGlobalSettingsOnInstrumentedUser() {
        TestApis.settings().global().putInt(KEY, INT_VALUE)
        assertThat(Settings.Global.getInt(context.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putIntWithContentResolver_putsIntIntoGlobalSettings() {
        TestApis.settings().global().putInt(context.contentResolver, KEY, INT_VALUE)
        assertThat(Settings.Global.getInt(context.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putIntWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putInt(
                    context.contentResolver, KEY, INT_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putIntWithUser_instrumentedUser_putsIntIntoGlobalSettings() {
        TestApis.settings().global().putInt(TestApis.users().instrumented(), KEY, INT_VALUE)
        assertThat(Settings.Global.getInt(context.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putIntWithUser_differentUser_putsIntIntoGlobalSettings() {
        TestApis.settings().global().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            assertThat(Settings.Global.getInt(
                    TestApis.context().androidContextAsUser(deviceState.secondaryUser())
                            .contentResolver, KEY)).isEqualTo(INT_VALUE)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putIntWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        }
    }

    @Test
    fun int_getsIntFromGlobalSettingsOnInstrumentedUser() {
        TestApis.settings().global().putInt(KEY, INT_VALUE)

        assertThat(TestApis.settings().global().getInt(KEY)).isEqualTo(INT_VALUE)
    }

    @Test
    fun int_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(INVALID_KEY)
        }
    }

    @Test
    fun int_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().global().getInt(INVALID_KEY, INT_VALUE))
                .isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_getsIntFromGlobalSettings() {
        TestApis.settings().global().putInt(
                TestApis.context().instrumentedContext().contentResolver, KEY, INT_VALUE)
        assertThat(TestApis.settings().global().getInt(
                TestApis.context().instrumentedContext().contentResolver, KEY)).isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(
                    TestApis.context().instrumentedContext().contentResolver, INVALID_KEY)
        }
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().global().getInt(
                TestApis.context().instrumentedContext().contentResolver,
                INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE)
    }

    @Test
    fun intWithUser_instrumentedUser_getsIntFromGlobalSettings() {
        TestApis.settings().global().putInt(KEY, INT_VALUE)

        assertThat(TestApis.settings().global().getInt(TestApis.users().instrumented(), KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    fun intWithUser_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(TestApis.users().instrumented(), INVALID_KEY)
        }
    }

    @Test
    fun intWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().global().getInt(
                TestApis.users().instrumented(), INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @EnsureHasSecondaryUser
    @Test
    fun intWithUser_differentUser_getsIntFromGlobalSettings() {
        TestApis.settings().global().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        assertThat(TestApis.settings().global().getInt(
                deviceState.secondaryUser(), KEY)).isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @EnsureHasSecondaryUser
    @Test
    fun intWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putString_putsStringIntoGlobalSettingsOnInstrumentedUser() {
        TestApis.settings().global().putString(KEY, STRING_VALUE)
        assertThat(Settings.Global.getString(context.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putStringWithContentResolver_putsStringIntoGlobalSettings() {
        TestApis.settings().global().putString(context.contentResolver, KEY, STRING_VALUE)
        assertThat(Settings.Global.getString(context.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putStringWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putString(
                    context.contentResolver, KEY, STRING_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putStringWithUser_instrumentedUser_putsStringIntoGlobalSettings() {
        TestApis.settings().global().putString(
                TestApis.users().instrumented(), KEY, STRING_VALUE)
        assertThat(Settings.Global.getString(context.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putStringWithUser_differentUser_putsStringIntoGlobalSettings() {
        TestApis.settings().global().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use { p ->
            assertThat(Settings.Global.getString(
                    TestApis.context().androidContextAsUser(deviceState.secondaryUser())
                            .contentResolver, KEY)).isEqualTo(STRING_VALUE)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putStringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putString(deviceState.secondaryUser(), KEY,
                    STRING_VALUE)
        }
    }

    @Test
    fun string_getsStringFromGlobalSettingsOnInstrumentedUser() {
        TestApis.settings().global().putString(KEY, STRING_VALUE)

        assertThat(TestApis.settings().global().getString(KEY)).isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_getsStringFromGlobalSettings() {
        TestApis.settings().global().putString(
                TestApis.context().instrumentedContext().contentResolver, KEY, STRING_VALUE)

        assertThat(TestApis.settings().global().getString(
                TestApis.context().instrumentedContext().contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    fun stringWithUser_instrumentedUser_getsStringFromGlobalSettings() {
        TestApis.settings().global().putString(KEY, STRING_VALUE)

        assertThat(TestApis.settings().global().getString(TestApis.users().instrumented(), KEY))
                .isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @EnsureHasSecondaryUser
    @Test
    fun stringWithUser_differentUser_getsStringFromGlobalSettings() {
        TestApis.settings().global().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)

        assertThat(TestApis.settings().global().getString(deviceState.secondaryUser(), KEY))
                .isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @EnsureHasSecondaryUser
    @Test
    fun stringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)
        }
    }

    @Test
    fun reset_resetsGlobalSettings() {
        TestApis.settings().global().putInt(KEY, INT_VALUE)
        TestApis.settings().global().putString(KEY, STRING_VALUE)

        TestApis.settings().global().reset()

        assertThrows(NeneException::class.java) { TestApis.settings().global().getInt(KEY) }
        assertThat(TestApis.settings().global().getString(KEY)).isNotEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    fun resetWithContentResolver_resetsGlobalSettings() {
        val contentResolver = TestApis.context().instrumentedContext().contentResolver
        TestApis.settings().global().putInt(contentResolver, KEY, INT_VALUE)
        TestApis.settings().global().putString(contentResolver, KEY, STRING_VALUE)

        TestApis.settings().global().reset(contentResolver)

        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(
                    contentResolver,
                    KEY)
        }
        assertThat(TestApis.settings().global().getString(contentResolver, KEY))
                .isNotEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    fun resetWithContentResolver_preS_throwsException() {
        val contentResolver = TestApis.context().instrumentedContext().contentResolver

        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().reset(contentResolver)
        }
    }

    @Test
    fun resetWithUser_instrumentedUser_resetsGlobalSettings() {
        TestApis.settings().global().putInt(TestApis.users().instrumented(), KEY, INT_VALUE)
        TestApis.settings().global().putString(TestApis.users().instrumented(), KEY,
                STRING_VALUE)

        TestApis.settings().global().reset(TestApis.users().instrumented())

        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(
                    TestApis.users().instrumented(), KEY)
        }
        assertThat(TestApis.settings().global().getString(TestApis.users().instrumented(),
                KEY)).isNotEqualTo(STRING_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Ignore("b/194669450")
    fun resetWithUser_differentUser_resetsGlobalSettings() {
        TestApis.settings().global().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        TestApis.settings().global().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)

        TestApis.settings().global().reset(deviceState.secondaryUser())

        assertThrows(NeneException::class.java) {
            TestApis.settings().global().getInt(
                    deviceState.secondaryUser(), KEY)
        }
        assertThat(TestApis.settings().global().getString(TestApis.settings().global()
                .getString(deviceState.secondaryUser(), KEY)!!)).isNotEqualTo(STRING_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    fun resetWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().global().reset(deviceState.secondaryUser())
        }
    }

    companion object {
        @JvmField @ClassRule @Rule
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()

        private const val KEY = "key"
        private const val INVALID_KEY = "noKey"
        private const val INT_VALUE = 123
        private const val STRING_VALUE = "testValue"
    }
}
