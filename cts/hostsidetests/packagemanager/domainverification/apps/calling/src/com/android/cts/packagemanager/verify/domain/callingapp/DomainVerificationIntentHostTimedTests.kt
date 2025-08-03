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

package com.android.cts.packagemanager.verify.domain.callingapp

import com.android.compatibility.common.util.ShellUtils
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_2_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainVerificationIntentTestBase
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_2
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DomainVerificationIntentHostTimedTests :
    DomainVerificationIntentTestBase(DOMAIN_1, resetEnable = true) {

    @Test
    fun multipleVerifiedTakeLastFirstInstall() {
        setAppLinks(DECLARING_PKG_NAME_2, true, DOMAIN_1, DOMAIN_2)

        assertResolvesTo(DECLARING_PKG_2_COMPONENT)

        setAppLinks(DECLARING_PKG_NAME_1, true, DOMAIN_1, DOMAIN_2)

        assertResolvesTo(DECLARING_PKG_1_COMPONENT)

        // Re-approve 2 and ensure this doesn't affect anything
        setAppLinks(DECLARING_PKG_NAME_2, true, DOMAIN_1, DOMAIN_2)

        assertResolvesTo(DECLARING_PKG_1_COMPONENT)
    }

    @Test
    fun multipleVerifiedDisableByComponent() {
        setAppLinks(DECLARING_PKG_NAME_2, true, DOMAIN_1, DOMAIN_2)
        setAppLinks(DECLARING_PKG_NAME_1, true, DOMAIN_1, DOMAIN_2)

        assertResolvesTo(DECLARING_PKG_1_COMPONENT)

        assertThat(
            ShellUtils.runShellCommand(
                "pm disable --user ${context.userId} " +
                        DECLARING_PKG_1_COMPONENT.flattenToString()
            ).trim()
        ).endsWith("new state: disabled")

        assertResolvesTo(DECLARING_PKG_2_COMPONENT)

        assertThat(
            ShellUtils.runShellCommand(
                "pm disable --user ${context.userId} " +
                        DECLARING_PKG_2_COMPONENT.flattenToString()
            ).trim()
        ).endsWith("new state: disabled")

        assertResolvesTo(browsers)
    }

    @Test
    fun multipleVerifiedDisableByPackage() {
        setAppLinks(DECLARING_PKG_NAME_2, true, DOMAIN_1, DOMAIN_2)
        setAppLinks(DECLARING_PKG_NAME_1, true, DOMAIN_1, DOMAIN_2)

        assertResolvesTo(DECLARING_PKG_1_COMPONENT)

        assertThat(
            ShellUtils.runShellCommand(
                "pm disable-user --user ${context.userId} $DECLARING_PKG_NAME_1"
            ).trim()
        ).endsWith("new state: disabled-user")

        assertResolvesTo(DECLARING_PKG_2_COMPONENT)

        assertThat(
            ShellUtils.runShellCommand(
                "pm disable-user --user ${context.userId} $DECLARING_PKG_NAME_2"
            ).trim()
        ).endsWith("new state: disabled-user")

        assertResolvesTo(browsers)
    }
}
