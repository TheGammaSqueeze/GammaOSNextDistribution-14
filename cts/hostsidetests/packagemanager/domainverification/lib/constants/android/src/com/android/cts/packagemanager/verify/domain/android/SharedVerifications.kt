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

package com.android.cts.packagemanager.verify.domain.android

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import com.android.compatibility.common.util.ShellUtils
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_2_COMPONENT
import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_3
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_7
import com.google.common.truth.Truth

object SharedVerifications {

    fun reset(context: Context, resetEnable: Boolean = false) {
        DomainUtils.ALL_PACKAGES.forEach {
            ShellUtils.runShellCommand(DomainUtils.setAppLinks(it, "STATE_NO_RESPONSE", "all"))
            ShellUtils.runShellCommand(DomainUtils.resetAppLinks(it))
            ShellUtils.runShellCommand(DomainUtils.setAppLinksAllowed(it, context.userId, true))
            ShellUtils.runShellCommand(
                DomainUtils.setAppLinksUserSelection(it, context.userId, false, "all")
            )
        }

        // Ignore if these fail, since not all tests will make use of both packages
        if (resetEnable) {
            val enablePrefix = "pm enable --user ${context.userId}"
            ShellUtils.runShellCommand("$enablePrefix $DECLARING_PKG_NAME_1")
            ShellUtils.runShellCommand("$enablePrefix $DECLARING_PKG_NAME_2")
            ShellUtils.runShellCommand(
                "$enablePrefix ${DECLARING_PKG_1_COMPONENT.flattenToString()}"
            )
            ShellUtils.runShellCommand(
                "$enablePrefix ${DECLARING_PKG_2_COMPONENT.flattenToString()}"
            )
        }
    }

    fun verifyDomains(context: Context) {
        val packageName = DECLARING_PKG_NAME_1
        val user = context.user
        val userId = context.userId
        val manager = context.getSystemService(DomainVerificationManager::class.java)!!
        manager.getDomainVerificationUserState(packageName)!!.also {
            Truth.assertThat(it.packageName).isEqualTo(packageName)
            Truth.assertThat(it.isLinkHandlingAllowed).isTrue()
            Truth.assertThat(it.user).isEqualTo(user)
            Truth.assertThat(it.hostToStateMap).containsExactlyEntriesIn(
                mapOf(
                    DOMAIN_1 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_2 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_3 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_7 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                )
            )
        }

        // Try to approve both 1 and 2, but only 1 is marked autoVerify
        ShellUtils.runShellCommand(
            DomainUtils.setAppLinks(packageName, "STATE_APPROVED", DOMAIN_1, DOMAIN_2)
        )

        manager.getDomainVerificationUserState(packageName)!!.also {
            Truth.assertThat(it.packageName).isEqualTo(packageName)
            Truth.assertThat(it.isLinkHandlingAllowed).isTrue()
            Truth.assertThat(it.user).isEqualTo(user)
            Truth.assertThat(it.hostToStateMap).containsExactlyEntriesIn(
                mapOf(
                    DOMAIN_1 to DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
                    DOMAIN_2 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_3 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_7 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                )
            )
        }

        ShellUtils.runShellCommand(
            DomainUtils.setAppLinksUserSelection(packageName, userId, true, DOMAIN_1, DOMAIN_2)
        )

        manager.getDomainVerificationUserState(packageName)!!.also {
            Truth.assertThat(it.packageName).isEqualTo(packageName)
            Truth.assertThat(it.isLinkHandlingAllowed).isTrue()
            Truth.assertThat(it.user).isEqualTo(user)
            Truth.assertThat(it.hostToStateMap).containsExactlyEntriesIn(
                mapOf(
                    DOMAIN_1 to DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
                    DOMAIN_2 to DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                    DOMAIN_3 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_7 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                )
            )
        }

        ShellUtils.runShellCommand(DomainUtils.setAppLinksAllowed(packageName, userId, false))

        manager.getDomainVerificationUserState(packageName)!!.also {
            Truth.assertThat(it.packageName).isEqualTo(packageName)
            Truth.assertThat(it.isLinkHandlingAllowed).isFalse()
            Truth.assertThat(it.user).isEqualTo(user)
            Truth.assertThat(it.hostToStateMap).containsExactlyEntriesIn(
                mapOf(
                    DOMAIN_1 to DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
                    DOMAIN_2 to DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                    DOMAIN_3 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                    DOMAIN_7 to DomainVerificationUserState.DOMAIN_STATE_NONE,
                )
            )
        }
    }
}
