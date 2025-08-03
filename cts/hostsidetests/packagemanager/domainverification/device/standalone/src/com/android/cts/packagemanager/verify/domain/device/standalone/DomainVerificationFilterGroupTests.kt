/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.packagemanager.verify.domain.device.standalone

import android.Manifest.permission.DOMAIN_VERIFICATION_AGENT
import android.content.UriRelativeFilter
import android.content.UriRelativeFilterGroup
import android.content.pm.Flags
import android.os.PatternMatcher
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainVerificationIntentTestBase
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_7
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DomainVerificationFilterGroupTests : DomainVerificationIntentTestBase(DOMAIN_1) {
    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    val path = "/path"
    val queryStr = "query=str"
    val uri1 = "$DOMAIN_1$path?$queryStr"
    val uri7 = "$DOMAIN_7$path?$queryStr"
    val groupsMap: MutableMap<String, List<UriRelativeFilterGroup>> = mutableMapOf()
    val blockGroup = UriRelativeFilterGroup(UriRelativeFilterGroup.ACTION_BLOCK).apply {
        this.addUriRelativeFilter(
            UriRelativeFilter(UriRelativeFilter.QUERY, PatternMatcher.PATTERN_LITERAL, queryStr)
        )
    }
    val allowGroup = UriRelativeFilterGroup(UriRelativeFilterGroup.ACTION_ALLOW).apply {
        this.addUriRelativeFilter(
            UriRelativeFilter(UriRelativeFilter.PATH, PatternMatcher.PATTERN_LITERAL, path)
        )
    }
    val emptyGroupsMap: Map<String, List<UriRelativeFilterGroup>> = mapOf(
        DOMAIN_1 to emptyList(),
        DOMAIN_7 to emptyList()
    )

    @Before
    @After
    fun clearGroups() {
        groupsMap.clear()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(DOMAIN_VERIFICATION_AGENT)
        try {
            manager.setUriRelativeFilterGroups(DECLARING_PKG_NAME_1, emptyGroupsMap)
            val map = manager.getUriRelativeFilterGroups(
                DECLARING_PKG_NAME_1,
                listOf(DOMAIN_1, DOMAIN_7)
            )
            assertThat(map.get(DOMAIN_1)).isEmpty()
            assertThat(map.get(DOMAIN_7)).isEmpty()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    @Test
    fun resolveWithUriRelativeFilterGroups_domainNotVerified() {
        setAppLinks(DECLARING_PKG_NAME_1, false, DOMAIN_1, DOMAIN_7)
        assertResolvesTo(browsers, uri1)
        assertResolvesTo(browsers, uri7)

        Assert.assertThrows(SecurityException::class.java) {
            manager.setUriRelativeFilterGroups(DECLARING_PKG_NAME_1, emptyGroupsMap)
        }

        instrumentation.uiAutomation.adoptShellPermissionIdentity(DOMAIN_VERIFICATION_AGENT)
        try {
            groupsMap.put(DOMAIN_7, listOf(allowGroup))
            manager.setUriRelativeFilterGroups(DECLARING_PKG_NAME_1, groupsMap)
            val map = manager.getUriRelativeFilterGroups(DECLARING_PKG_NAME_1, listOf(DOMAIN_7))
            assertThat(map).containsExactlyEntriesIn(groupsMap).inOrder()
            assertResolvesTo(browsers, uri1)
            assertResolvesTo(browsers, uri7)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    @Test
    fun resolveWithUriRelativeFilterGroups_domainVerified() {
        assertResolveFilterGroups(DOMAIN_1, uri1, uri7)
    }

    @RequiresFlagsEnabled(Flags.FLAG_RELATIVE_REFERENCE_INTENT_FILTERS)
    @Test
    fun resolveWithUriRelativeFilterGroups_domainVerifiedWithIntentGroups() {
        assertResolveFilterGroups(DOMAIN_7, uri7, uri1)
    }

    private fun assertResolveFilterGroups(domain: String, targetUri: String, controlUri: String) {
        setAppLinks(DECLARING_PKG_NAME_1, true, DOMAIN_1, DOMAIN_7)
        assertResolvesTo(DECLARING_PKG_1_COMPONENT, uri1)
        assertResolvesTo(DECLARING_PKG_1_COMPONENT, uri7)

        instrumentation.uiAutomation.adoptShellPermissionIdentity(DOMAIN_VERIFICATION_AGENT)
        try {
            groupsMap.put(domain, listOf(allowGroup, blockGroup))
            manager.setUriRelativeFilterGroups(DECLARING_PKG_NAME_1, groupsMap)
            assertThat(manager.getUriRelativeFilterGroups(DECLARING_PKG_NAME_1, listOf(domain)))
                .containsExactlyEntriesIn(groupsMap).inOrder()
            assertResolvesTo(DECLARING_PKG_1_COMPONENT, controlUri)
            assertResolvesTo(DECLARING_PKG_1_COMPONENT, targetUri)

            groupsMap.put(domain, listOf(blockGroup, allowGroup))
            manager.setUriRelativeFilterGroups(DECLARING_PKG_NAME_1, groupsMap)
            assertThat(manager.getUriRelativeFilterGroups(DECLARING_PKG_NAME_1, listOf(domain)))
                .containsExactlyEntriesIn(groupsMap).inOrder()
            assertResolvesTo(browsers, targetUri)
            assertResolvesTo(DECLARING_PKG_1_COMPONENT, controlUri)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
