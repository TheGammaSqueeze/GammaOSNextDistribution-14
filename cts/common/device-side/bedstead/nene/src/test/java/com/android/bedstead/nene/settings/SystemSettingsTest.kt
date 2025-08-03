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
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class SystemSettingsTest {
    @Test
    @Throws(Exception::class)
    fun putInt_putsIntIntoSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putInt(KEY, INT_VALUE)

        assertThat(Settings.System.getInt(sContext.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putIntWithContentResolver_putsIntIntoSystemSettings() {
        TestApis.settings().system().putInt(sContext.contentResolver, KEY, INT_VALUE)

        assertThat(Settings.System.getInt(sContext.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putIntWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().system().putInt(
                    sContext.contentResolver, KEY, INT_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putIntWithUser_instrumentedUser_putsIntIntoSystemSettings() {
        TestApis.settings().system().putInt(TestApis.users().instrumented(), KEY, INT_VALUE)

        assertThat(Settings.System.getInt(sContext.contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putIntWithUser_differentUser_putsIntIntoSystemSettings() {
        TestApis.settings().system().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)

        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use { p ->
            assertThat(Settings.System.getInt(
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
            TestApis.settings().system().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        }
    }

    @Test
    fun int_getsIntFromSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putInt(KEY, INT_VALUE)

        assertThat(TestApis.settings().system().getInt(KEY)).isEqualTo(INT_VALUE)
    }

    @Test
    fun int_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().system().getInt(INVALID_KEY)
        }
    }

    @Test
    fun int_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(INVALID_KEY, INT_VALUE))
                .isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(
                TestApis.context().instrumentedContext().contentResolver, KEY, INT_VALUE)
        assertThat(TestApis.settings().system().getInt(
                TestApis.context().instrumentedContext().contentResolver, KEY))
                .isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().system().getInt(
                    TestApis.context().instrumentedContext().contentResolver, INVALID_KEY)
        }
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(
                TestApis.context().instrumentedContext().contentResolver, INVALID_KEY, INT_VALUE))
                .isEqualTo(INT_VALUE)
    }

    @Test
    fun intWithUser_instrumentedUser_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(KEY, INT_VALUE)
        assertThat(TestApis.settings().system().getInt(TestApis.users().instrumented(), KEY))
                .isEqualTo(INT_VALUE)
    }

    @Test
    fun intWithUser_invalidKey_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.settings().system().getInt(TestApis.users().instrumented(), INVALID_KEY)
        }
    }

    @Test
    fun intWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(
                TestApis.users().instrumented(), INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @EnsureHasSecondaryUser
    @Test
    fun intWithUser_differentUser_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        assertThat(TestApis.settings().system().getInt(deviceState.secondaryUser(), KEY))
                .isEqualTo(INT_VALUE)
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @EnsureHasSecondaryUser
    @Test
    fun intWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().system().putInt(deviceState.secondaryUser(), KEY, INT_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putString_putsStringIntoSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putString(KEY, STRING_VALUE)
        assertThat(Settings.System.getString(sContext.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putStringWithContentResolver_putsStringIntoSystemSettings() {
        TestApis.settings().system().putString(sContext.contentResolver, KEY, STRING_VALUE)
        assertThat(Settings.System.getString(sContext.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putStringWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().system().putString(
                    sContext.contentResolver, KEY, STRING_VALUE)
        }
    }

    @Test
    @Throws(Exception::class)
    fun putStringWithUser_instrumentedUser_putsStringIntoSystemSettings() {
        TestApis.settings().system().putString(
                TestApis.users().instrumented(), KEY, STRING_VALUE)
        assertThat(Settings.System.getString(sContext.contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Throws(Exception::class)
    fun putStringWithUser_differentUser_putsStringIntoSystemSettings() {
        TestApis.settings().system().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use { p ->
            assertThat(Settings.System.getString(
                    TestApis.context().androidContextAsUser(deviceState.secondaryUser())
                            .contentResolver, KEY)).isEqualTo(STRING_VALUE)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @Throws(Exception::class)
    fun putStringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java
        ) {
            TestApis.settings().system().putString(deviceState.secondaryUser(), KEY,
                    STRING_VALUE)
        }
    }

    @Test
    fun string_getsStringFromSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putString(KEY, STRING_VALUE)
        assertThat(TestApis.settings().system().getString(KEY)).isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Test
    fun intWithContentResolver_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(
                TestApis.context().instrumentedContext().contentResolver, KEY, STRING_VALUE)
        assertThat(TestApis.settings().system().getString(
                TestApis.context().instrumentedContext().contentResolver, KEY))
                .isEqualTo(STRING_VALUE)
    }

    @Test
    fun stringWithUser_instrumentedUser_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(KEY, STRING_VALUE)
        assertThat(TestApis.settings().system().getString(
                TestApis.users().instrumented(), KEY))
                .isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @EnsureHasSecondaryUser
    @Test
    fun stringWithUser_differentUser_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)
        assertThat(TestApis.settings().system().getString(
                deviceState.secondaryUser(), KEY)).isEqualTo(STRING_VALUE)
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    @EnsureHasSecondaryUser
    @Test
    fun stringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException::class.java) {
            TestApis.settings().system().putString(deviceState.secondaryUser(), KEY, STRING_VALUE)
        }
    }

    companion object {
        @JvmField @ClassRule @Rule
        val deviceState = DeviceState()

        private val sContext = TestApis.context().instrumentedContext()

        private const val KEY = "screen_brightness" // must be a public system setting
        private const val INVALID_KEY = "noKey"
        private const val INT_VALUE = 123
        private const val STRING_VALUE = "testValue"
    }
}
