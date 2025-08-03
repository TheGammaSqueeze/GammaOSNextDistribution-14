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

package android.net

import android.os.Build
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner

import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet6Address
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import android.net.MulticastRoutingConfig.Builder
import android.net.MulticastRoutingConfig.FORWARD_NONE
import android.net.MulticastRoutingConfig.FORWARD_SELECTED
import android.net.MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class MulticastRoutingConfigTest {

    val address1 = Inet6Address.getByName("2000::8888") as Inet6Address
    val address2 = Inet6Address.getByName("2000::9999") as Inet6Address

    private fun configNone() = Builder(FORWARD_NONE).build()
    private fun configMinScope(scope: Int) = Builder(FORWARD_WITH_MIN_SCOPE, scope).build()
    private fun configSelected() = Builder(FORWARD_SELECTED).build()
    private fun configSelectedWithAddress1AndAddress2() =
            Builder(FORWARD_SELECTED).addListeningAddress(address1)
            .addListeningAddress(address2).build()
    private fun configSelectedWithAddress2AndAddress1() =
            Builder(FORWARD_SELECTED).addListeningAddress(address2)
            .addListeningAddress(address1).build()

    @Test
    fun equalityTests() {

        assertTrue(configNone().equals(configNone()))

        assertTrue(configSelected().equals(configSelected()))

        assertTrue(configMinScope(4).equals(configMinScope(4)))

        assertTrue(configSelectedWithAddress1AndAddress2()
                .equals(configSelectedWithAddress2AndAddress1()))
    }

    @Test
    fun inequalityTests() {

        assertFalse(configNone().equals(configSelected()))

        assertFalse(configNone().equals(configMinScope(4)))

        assertFalse(configSelected().equals(configMinScope(4)))

        assertFalse(configMinScope(4).equals(configMinScope(5)))

        assertFalse(configSelected().equals(configSelectedWithAddress1AndAddress2()))
    }

    @Test
    fun toString_equalObjects_returnsEqualStrings() {
        val config1 = configSelectedWithAddress1AndAddress2()
        val config2 = configSelectedWithAddress2AndAddress1()

        val str1 = config1.toString()
        val str2 = config2.toString()

        assertTrue(str1.equals(str2))
    }

    @Test
    fun toString_unequalObjects_returnsUnequalStrings() {
        val config1 = configSelected()
        val config2 = configSelectedWithAddress1AndAddress2()

        val str1 = config1.toString()
        val str2 = config2.toString()

        assertFalse(str1.equals(str2))
    }
}
