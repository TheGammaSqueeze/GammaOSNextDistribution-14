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

package com.android.safetycenter.config

import android.content.Context
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySourcesGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel
import com.android.permission.flags.Flags
import com.android.safetycenter.config.tests.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParserConfigValidTest {
    private val context: Context = getApplicationContext()

    @Test
    fun validConfig_matchesExpected() {
        val inputStream = context.resources.openRawResource(R.raw.config_valid)

        val actual = SafetyCenterConfigParser.parseXmlResource(inputStream, context.resources)

        val expected =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(
                    SafetySourcesGroup.Builder()
                        .setId("dynamic")
                        .setTitleResId(R.string.reference)
                        .setSummaryResId(R.string.reference)
                        .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_barebone")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_all_optional")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setTitleForWorkResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                                .setMaxSeverityLevel(300)
                                .setSearchTermsResId(R.string.reference)
                                .setLoggingAllowed(false)
                                .setRefreshOnPageOpenAllowed(true)
                                .apply {
                                    if (SdkLevel.isAtLeastU()) {
                                        setNotificationsAllowed(true)
                                        setDeduplicationGroup("group")
                                        addPackageCertificateHash("feed1")
                                        addPackageCertificateHash("feed2")
                                    }
                                    if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                                        setTitleForPrivateProfileResId(R.string.reference)
                                    }
                                }
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_all_references")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setTitleForWorkResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                                .setMaxSeverityLevel(300)
                                .setSearchTermsResId(R.string.reference)
                                .setLoggingAllowed(false)
                                .setRefreshOnPageOpenAllowed(true)
                                .apply {
                                    if (SdkLevel.isAtLeastU()) {
                                        setNotificationsAllowed(true)
                                        setDeduplicationGroup("group")
                                        addPackageCertificateHash("feed1")
                                        addPackageCertificateHash("feed2")
                                    }
                                    if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                                        setTitleForPrivateProfileResId(R.string.reference)
                                    }
                                }
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_disabled")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_hidden")
                                .setPackageName("package")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("dynamic_hidden_with_search")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setTitleForWorkResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                                .setSearchTermsResId(R.string.reference)
                                .apply {
                                    if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                                        setTitleForPrivateProfileResId(R.string.reference)
                                    }
                                }
                                .build()
                        )
                        .build()
                )
                .addSafetySourcesGroup(
                    SafetySourcesGroup.Builder()
                        .setId("static")
                        .setTitleResId(R.string.reference)
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                .setId("static_barebone")
                                .setTitleResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                .setId("static_all_optional")
                                .apply { if (SdkLevel.isAtLeastU()) setPackageName("package") }
                                .setTitleResId(R.string.reference)
                                .setTitleForWorkResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setSearchTermsResId(R.string.reference)
                                .apply {
                                    if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                                        setTitleForPrivateProfileResId(R.string.reference)
                                    }
                                }
                                .build()
                        )
                        .build()
                )
                .addSafetySourcesGroup(
                    SafetySourcesGroup.Builder()
                        .setId("issue_only")
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                .setId("issue_only_barebone")
                                .setPackageName("package")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                .setId("issue_only_all_optional")
                                .setPackageName("package")
                                .setProfile(SafetySource.PROFILE_ALL)
                                .setMaxSeverityLevel(300)
                                .setLoggingAllowed(false)
                                .setRefreshOnPageOpenAllowed(true)
                                .apply {
                                    if (SdkLevel.isAtLeastU()) {
                                        setNotificationsAllowed(true)
                                        setDeduplicationGroup("group")
                                        addPackageCertificateHash("feed1")
                                        addPackageCertificateHash("feed2")
                                    }
                                }
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                .setId("id_test_abcxyz_ABCXYZ_012789")
                                .setPackageName("package")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .build()
                )
                .addSafetySourcesGroup(
                    SafetySourcesGroup.Builder()
                        .setId("mixed")
                        .setTitleResId(R.string.reference)
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                                .setId("mixed_dynamic_barebone")
                                .setPackageName("package")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                .setId("mixed_issue_only_barebone")
                                .setPackageName("package")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .addSafetySource(
                            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                .setId("mixed_static_barebone")
                                .setTitleResId(R.string.reference)
                                .setIntentAction("intent")
                                .setProfile(SafetySource.PROFILE_PRIMARY)
                                .build()
                        )
                        .build()
                )
                .apply {
                    if (SdkLevel.isAtLeastU()) {
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
                                .setId("stateful_barebone")
                                .setTitleResId(R.string.reference)
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                        .setId("stateful_barebone_source")
                                        .setTitleResId(R.string.reference)
                                        .setIntentAction("intent")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
                                .setId("stateful_all_optional")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setStatelessIconType(
                                    SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY
                                )
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                        .setId("stateful_all_optional_source")
                                        .setTitleResId(R.string.reference)
                                        .setIntentAction("intent")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                                .setId("stateless_barebone")
                                .setTitleResId(R.string.reference)
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                        .setId("stateless_barebone_source")
                                        .setTitleResId(R.string.reference)
                                        .setIntentAction("intent")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                                .setId("stateless_all_optional")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setStatelessIconType(
                                    SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY
                                )
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                                        .setId("stateless_all_optional_source")
                                        .setTitleResId(R.string.reference)
                                        .setIntentAction("intent")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                                .setId("hidden_barebone")
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                        .setId("hidden_barebone_source")
                                        .setPackageName("package")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                        addSafetySourcesGroup(
                            SafetySourcesGroup.Builder()
                                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                                .setId("hidden_all_optional")
                                .setTitleResId(R.string.reference)
                                .setSummaryResId(R.string.reference)
                                .setStatelessIconType(
                                    SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY
                                )
                                .addSafetySource(
                                    SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                                        .setId("hidden_all_optional_source")
                                        .setPackageName("package")
                                        .setProfile(SafetySource.PROFILE_PRIMARY)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
                .build()
        assertThat(actual).isEqualTo(expected)
    }
}
