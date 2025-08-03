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

package com.android.server.connectivity

import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import com.android.frameworks.tests.net.R
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class ApplicationSelfCertifiedNetworkCapabilitiesTest {
    private val mResource = InstrumentationRegistry.getContext().getResources()
    private val bandwidthCapability = NetworkCapabilities.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
    }.build()
    private val latencyCapability = NetworkCapabilities.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
    }.build()
    private val emptyCapability = NetworkCapabilities.Builder().build()
    private val bothCapabilities = NetworkCapabilities.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
        addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
    }.build()

    @Test
    fun parseXmlWithWrongTag_shouldIgnoreWrongTag() {
        val parser = mResource.getXml(
            R.xml.self_certified_capabilities_wrong_tag
        )
        val selfDeclaredCaps = ApplicationSelfCertifiedNetworkCapabilities.createFromXml(parser)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(latencyCapability)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(bandwidthCapability)
    }

    @Test
    fun parseXmlWithWrongDeclaration_shouldThrowException() {
        val parser = mResource.getXml(
            R.xml.self_certified_capabilities_wrong_declaration
        )
        val exception = assertFailsWith<InvalidTagException> {
            ApplicationSelfCertifiedNetworkCapabilities.createFromXml(parser)
        }
        assertThat(exception.message).contains("network-capabilities-declaration1")
    }

    @Test
    fun checkIfSelfCertifiedNetworkCapabilitiesDeclared_shouldThrowExceptionWhenNoDeclaration() {
        val parser = mResource.getXml(R.xml.self_certified_capabilities_other)
        val selfDeclaredCaps = ApplicationSelfCertifiedNetworkCapabilities.createFromXml(parser)
        val exception1 = assertFailsWith<SecurityException> {
            selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(latencyCapability)
        }
        assertThat(exception1.message).contains(
            ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_LATENCY
        )
        val exception2 = assertFailsWith<SecurityException> {
            selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(bandwidthCapability)
        }
        assertThat(exception2.message).contains(
            ApplicationSelfCertifiedNetworkCapabilities.PRIORITIZE_BANDWIDTH
        )
    }

    @Test
    fun checkIfSelfCertifiedNetworkCapabilitiesDeclared_shouldPassIfDeclarationExist() {
        val parser = mResource.getXml(R.xml.self_certified_capabilities_both)
        val selfDeclaredCaps = ApplicationSelfCertifiedNetworkCapabilities.createFromXml(parser)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(latencyCapability)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(bandwidthCapability)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(bothCapabilities)
        selfDeclaredCaps.enforceSelfCertifiedNetworkCapabilitiesDeclared(emptyCapability)
    }
}
