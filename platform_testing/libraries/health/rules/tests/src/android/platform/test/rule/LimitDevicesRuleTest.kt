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
package android.platform.test.rule

import android.platform.test.rule.DeviceProduct.CF_PHONE
import android.platform.test.rule.DeviceProduct.CF_TABLET
import org.junit.Assert.assertEquals
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.model.Statement
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class LimitDevicesRuleTest {

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun allowOnPhone_withPhone_succeeds() {
        val rule = LimitDevicesRule(thisDevice = CF_PHONE.product)

        val description = createDescription(AllowedOnPhonesOnly::class.java)

        assertEquals(rule.apply(statement, description), statement)
    }

    @Test(expected = AssumptionViolatedException::class)
    fun allowOnPhone_withTablet_fails() {
        val rule = LimitDevicesRule(thisDevice = CF_TABLET.product)

        val description = createDescription(AllowedOnPhonesOnly::class.java)

        rule.apply(statement, description).evaluate()
    }

    @Test
    fun denyOnPhone_withTablet_succeeds() {
        val rule = LimitDevicesRule(thisDevice = CF_TABLET.product)

        val description = createDescription(DeniedOnPhonesOnly::class.java)

        assertEquals(rule.apply(statement, description), statement)
    }

    @Test(expected = AssumptionViolatedException::class)
    fun denyOnPhone_withPhone_fails() {
        val rule = LimitDevicesRule(thisDevice = CF_PHONE.product)

        val description = createDescription(DeniedOnPhonesOnly::class.java)

        rule.apply(statement, description).evaluate()
    }

    @Test
    fun allowedOnBothPhonesAndTablets_withTabletAndPhone_succeeds() {
        val ruleOnTablet = LimitDevicesRule(thisDevice = CF_TABLET.product)
        val ruleOnPhone = LimitDevicesRule(thisDevice = CF_PHONE.product)

        val description = createDescription(AllowedOnPhonesAndTablets::class.java)

        assertEquals(ruleOnPhone.apply(statement, description), statement)
        assertEquals(ruleOnTablet.apply(statement, description), statement)
    }

    private fun <T> createDescription(clazz: Class<T>) =
        Description.createSuiteDescription(this.javaClass, *clazz.annotations)!!

    private val statement: Statement =
        object : Statement() {
            override fun evaluate() {}
        }
}

@AllowedDevices(CF_PHONE) private class AllowedOnPhonesOnly

@DeniedDevices(CF_PHONE) private class DeniedOnPhonesOnly

@AllowedDevices(CF_PHONE, CF_TABLET) private class AllowedOnPhonesAndTablets
