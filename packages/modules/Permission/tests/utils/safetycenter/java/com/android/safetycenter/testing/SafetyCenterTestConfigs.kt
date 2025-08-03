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

package com.android.safetycenter.testing

import android.content.Context
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.res.Resources
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.config.SafetySourcesGroup
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permission.flags.Flags
import com.android.safetycenter.testing.SettingsPackage.getSettingsPackageName
import java.security.MessageDigest

/**
 * A class that provides [SafetyCenterConfig] objects and associated constants to facilitate setting
 * up safety sources for testing.
 */
@RequiresApi(TIRAMISU)
class SafetyCenterTestConfigs(private val context: Context) {
    /** The certificate hash signing the current package. */
    val packageCertHash =
        MessageDigest.getInstance("SHA256")
            .digest(
                context.packageManager
                    .getPackageInfo(
                        context.packageName,
                        PackageInfoFlags.of(GET_SIGNING_CERTIFICATES.toLong())
                    )
                    .signingInfo!!
                    .apkContentsSigners[0]
                    .toByteArray()
            )
            .joinToString("") { "%02x".format(it) }

    /** A simple [SafetyCenterConfig] for tests with a single source of id [SINGLE_SOURCE_ID]. */
    val singleSourceConfig = singleSourceConfig(dynamicSafetySource(SINGLE_SOURCE_ID))

    /**
     * A simple [SafetyCenterConfig] with an invalid intent action for tests with a single source of
     * id [SINGLE_SOURCE_ID].
     */
    val singleSourceInvalidIntentConfig =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID)
                .setIntentAction(INTENT_ACTION_NOT_RESOLVING)
                .build()
        )

    /**
     * Same as [singleSourceConfig] but with an `intentAction` that will resolve implicitly; i.e.
     * the source's `packageName` does not own the activity resolved by the `intentAction`.
     */
    val implicitIntentSingleSourceConfig =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID)
                // A valid package name that is *not* CTS.
                .setPackageName(context.packageManager.permissionControllerPackageName)
                // Exported activity that lives in the CTS package. The PC package does
                // implement this intent action so the activity has to resolve
                // implicitly.
                .setIntentAction(ACTION_TEST_ACTIVITY_EXPORTED)
                .build()
        )

    /** A simple [SafetyCenterConfig] for tests with a source max severity level of 0. */
    val severityZeroConfig =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setMaxSeverityLevel(0).build()
        )

    /** A simple [SafetyCenterConfig] for tests with a fake/incorrect package cert hash. */
    val singleSourceWithFakeCert: SafetyCenterConfig
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            singleSourceConfig(
                dynamicSafetySourceBuilder(SINGLE_SOURCE_ID)
                    .addPackageCertificateHash(PACKAGE_CERT_HASH_FAKE)
                    .build()
            )

    /**
     * A simple [SafetyCenterConfig] for tests with a invalid package cert hash (not a hex-formatted
     * byte string).
     */
    val singleSourceWithInvalidCert: SafetyCenterConfig
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            singleSourceConfig(
                dynamicSafetySourceBuilder(SINGLE_SOURCE_ID)
                    .addPackageCertificateHash(PACKAGE_CERT_HASH_INVALID)
                    .build()
            )

    /**
     * A simple [SafetyCenterConfig] for tests with a source that does not support refresh on page
     * open.
     */
    val noPageOpenConfig =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setRefreshOnPageOpenAllowed(false).build()
        )

    /** A Simple [SafetyCenterConfig] with an issue only source. */
    val issueOnlySourceConfig =
        singleSourceConfig(issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID).build())

    /** A Simple [SafetyCenterConfig] with an issue only source supporting all profiles. */
    val issueOnlySourceAllProfileConfig =
        singleSourceConfig(
            issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID).build()
        )

    /**
     * A Simple [SafetyCenterConfig] with an issue only source inside a [SafetySourcesGroup] with
     * null title.
     */
    val issueOnlySourceNoGroupTitleConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(SINGLE_SOURCE_GROUP_ID)
                    .setTitleResId(Resources.ID_NULL)
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID).build()
                    )
                    .build()
            )
            .build()

    /** A dynamic source with [OTHER_PACKAGE_NAME] */
    val dynamicOtherPackageSafetySource =
        dynamicSafetySourceBuilder(DYNAMIC_OTHER_PACKAGE_ID)
            .setRefreshOnPageOpenAllowed(false)
            .setPackageName(OTHER_PACKAGE_NAME)
            .build()

    /** A [SafetyCenterConfig] with a dynamic source in a different, missing package. */
    val singleSourceOtherPackageConfig = singleSourceConfig(dynamicOtherPackageSafetySource)

    /** A [SafetyCenterConfig] with a dynamic hidden-by-default source. */
    val hiddenSourceConfig =
        singleSourceConfig(
            dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                .build()
        )

    /** A simple [SafetyCenterConfig] with a source supporting all profiles. */
    val singleSourceAllProfileConfig =
        singleSourceConfig(
            dynamicAllProfileSafetySourceBuilder(SINGLE_SOURCE_ALL_PROFILE_ID).build()
        )

    /** A simple [SafetyCenterConfig] for tests with multiple sources. */
    val multipleSourcesConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
                    .setTitleResId(android.R.string.copy)
                    .setSummaryResId(android.R.string.cancel)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(SOURCE_ID_3)
                            .setTitleResId(android.R.string.copy)
                            .setSummaryResId(android.R.string.cancel)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .build()
            )
            .build()

    /** A simple [SafetyCenterConfig] with multiple sources in a single [SafetySourcesGroup]. */
    val multipleSourcesInSingleGroupConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_3))
                    .build()
            )
            .build()

    /** A simple [SafetyCenterConfig] for tests with multiple sources with deduplication info. */
    val multipleSourcesWithDeduplicationInfoConfig: SafetyCenterConfig
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(
                    safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_1,
                                DEDUPLICATION_GROUP_1
                            )
                        )
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_2,
                                DEDUPLICATION_GROUP_1
                            )
                        )
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_3,
                                DEDUPLICATION_GROUP_2
                            )
                        )
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_4,
                                DEDUPLICATION_GROUP_3
                            )
                        )
                        .build()
                )
                .addSafetySourcesGroup(
                    safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_5,
                                DEDUPLICATION_GROUP_1
                            )
                        )
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_6,
                                DEDUPLICATION_GROUP_3
                            )
                        )
                        .build()
                )
                .addSafetySourcesGroup(
                    safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_3)
                        .addSafetySource(
                            issueOnlySafetySourceWithDuplicationInfo(
                                SOURCE_ID_7,
                                DEDUPLICATION_GROUP_3
                            )
                        )
                        .build()
                )
                .build()

    /**
     * A simple [SafetyCenterConfig] for testing the Privacy subpage. Note that this config contains
     * the [PRIVACY_SOURCE_ID_1] source that is part of the generic category, and the [SOURCE_ID_1]
     * that is part of the data category.
     */
    val privacySubpageConfig: SafetyCenterConfig
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(
                    safetySourcesGroupBuilder(ANDROID_PRIVACY_SOURCES_GROUP_ID)
                        .addSafetySource(dynamicSafetySource(PRIVACY_SOURCE_ID_1))
                        .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                        .build()
                )
                .build()

    /**
     * A simple [SafetyCenterConfig] without data sources for testing the Privacy subpage. Note that
     * this config contains only [PRIVACY_SOURCE_ID_1] source that is part of the generic category.
     * Hence it doesn't have any data category sources.
     */
    val privacySubpageWithoutDataSourcesConfig: SafetyCenterConfig
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(
                    safetySourcesGroupBuilder(ANDROID_PRIVACY_SOURCES_GROUP_ID)
                        .addSafetySource(dynamicSafetySource(PRIVACY_SOURCE_ID_1))
                        .build()
                )
                .build()

    /** Source included in [dynamicSourceGroup1]. */
    val dynamicSource1 = dynamicSafetySource(SOURCE_ID_1)

    /** Source included in [dynamicSourceGroup1]. */
    val dynamicSource2 = dynamicSafetySource(SOURCE_ID_2)

    /** Source included in [dynamicSourceGroup2]. */
    val dynamicSource3 = dynamicSafetySource(SOURCE_ID_3)

    private val dynamicSource4 = dynamicSafetySource(SOURCE_ID_4)
    private val dynamicSource5 = dynamicSafetySource(SOURCE_ID_5)

    /** Source group provided by [multipleSourceGroupsConfig]. */
    val dynamicSourceGroup1 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
            .addSafetySource(dynamicSource1)
            .addSafetySource(dynamicSource2)
            .setTitleResId(android.R.string.copy)
            .setSummaryResId(android.R.string.cut)
            .build()

    /** Source group provided by [multipleSourceGroupsConfig]. */
    val dynamicSourceGroup2 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
            .addSafetySource(dynamicSource3)
            .setTitleResId(android.R.string.paste)
            .setSummaryResId(android.R.string.cancel)
            .build()

    /** Source group provided by [multipleSourceGroupsConfig]. */
    val dynamicSourceGroup3 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_3)
            .addSafetySource(dynamicSource4)
            .addSafetySource(dynamicSource5)
            .setTitleResId(android.R.string.dialog_alert_title)
            .setSummaryResId(android.R.string.selectAll)
            .build()

    /** A simple [SafetyCenterConfig] for tests with multiple groups of multiple sources. */
    val multipleSourceGroupsConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(dynamicSourceGroup1)
            .addSafetySourcesGroup(dynamicSourceGroup2)
            .addSafetySourcesGroup(dynamicSourceGroup3)
            .build()

    /**
     * A simple [SafetyCenterConfig] for tests with multiple sources with one source having an
     * invalid default intent.
     */
    val multipleSourcesConfigWithSourceWithInvalidIntent =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(SOURCE_ID_1)
                            .setIntentAction(INTENT_ACTION_NOT_RESOLVING)
                            .build()
                    )
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .build()
            )
            .build()

    /** Source provided by [staticSourcesConfig]. */
    val staticSource1 =
        staticSafetySourceBuilder("test_static_source_id_1")
            .setTitleResId(android.R.string.dialog_alert_title)
            .setSummaryResId(android.R.string.autofill)
            .build()

    /** Source provided by [staticSourcesConfig]. */
    val staticSource2 =
        staticSafetySourceBuilder("test_static_source_id_2")
            .setTitleResId(android.R.string.copyUrl)
            .setSummaryResId(android.R.string.cut)
            .build()

    /**
     * Source group provided by [staticSourcesConfig] containing a single source [staticSource1].
     */
    val staticSourceGroup1 =
        staticSafetySourcesGroupBuilder("test_static_sources_group_id_1")
            .addSafetySource(staticSource1)
            .build()

    /**
     * Source group provided by [staticSourcesConfig] containing a single source [staticSource2].
     */
    val staticSourceGroup2 =
        staticSafetySourcesGroupBuilder("test_static_sources_group_id_2")
            .setTitleResId(android.R.string.copy)
            .addSafetySource(staticSource2)
            .build()

    /** A simple [SafetyCenterConfig] for tests with static sources. */
    val staticSourcesConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(staticSourceGroup1)
            .addSafetySourcesGroup(staticSourceGroup2)
            .build()

    /**
     * A [SafetyCenterConfig] with a single static source
     *
     * The particular source ID is configured in the same way as sources hosted by the Settings app,
     * to launch as if it is part of the Settings app UI.
     */
    val singleStaticSettingsSourceConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                staticSafetySourcesGroupBuilder("single_static_source_group")
                    .addSafetySource(staticSafetySource("TestSource"))
                    .build()
            )
            .build()

    /** A [SafetyCenterConfig] with a single static source and an intent that doesn't resolve */
    val singleStaticInvalidIntentConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                staticSafetySourcesGroupBuilder("single_static_source_group")
                    .addSafetySource(
                        staticSafetySourceBuilder(SINGLE_SOURCE_ID)
                            .setIntentAction(INTENT_ACTION_NOT_RESOLVING)
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * A [SafetyCenterConfig] with a single static source and an implicit intent that isn't exported
     */
    val singleStaticImplicitIntentNotExportedConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                staticSafetySourcesGroupBuilder("single_static_source_group")
                    .addSafetySource(
                        staticSafetySourceBuilder(SINGLE_SOURCE_ID)
                            .setIntentAction(ACTION_TEST_ACTIVITY)
                            .build()
                    )
                    .build()
            )
            .build()

    /** [SafetyCenterConfig] used in tests for Your Work Policy Info source. */
    val workPolicyInfoConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId("AndroidAdvancedSources")
                    .setTitleResId(android.R.string.paste)
                    .addSafetySource(
                        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
                            .setId("AndroidWorkPolicyInfo")
                            .setPackageName(context.packageManager.permissionControllerPackageName)
                            .setProfile(SafetySource.PROFILE_PRIMARY)
                            .setRefreshOnPageOpenAllowed(true)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .build()

    /** [SafetyCenterConfig] used in tests to replicate the lock screen source. */
    val settingsLockScreenSourceConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)
                    .addSafetySource(
                        dynamicSafetySourceBuilder("AndroidLockScreen")
                            .setPackageName(context.getSettingsPackageName())
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .build()
            )
            .build()

    /** [SafetyCenterConfig] used in tests to replicate the lock screen sources group. */
    val androidLockScreenSourcesConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)
                    // This is needed to have a stateful group with an empty summary
                    .setSummaryResId(Resources.ID_NULL)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .build()
            )
            .build()

    /** A simple [SafetyCenterConfig] used in tests that stress the group summary logic. */
    val summaryTestConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(SUMMARY_TEST_GROUP_ID)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_3))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_4))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_5))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_6))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_7))
                    .addSafetySource(staticSafetySource(STATIC_IN_STATEFUL_ID))
                    .build()
            )
            .build()

    /**
     * A complex [SafetyCenterConfig] exploring different combinations of valid sources and groups.
     */
    val complexConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(DYNAMIC_GROUP_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setSearchTermsResId(android.R.string.ok)
                            .setLoggingAllowed(false)
                            .apply {
                                if (SdkLevel.isAtLeastU()) {
                                    setNotificationsAllowed(true)
                                    setDeduplicationGroup("group")
                                    addPackageCertificateHash(PACKAGE_CERT_HASH_FAKE)
                                    addPackageCertificateHash(packageCertHash)
                                }
                            }
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_WITH_SEARCH_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .setSearchTermsResId(android.R.string.ok)
                            .build()
                    )
                    .addSafetySource(dynamicOtherPackageSafetySource)
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(STATIC_GROUP_ID)
                    .apply {
                        if (SdkLevel.isAtLeastU()) {
                            setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                            setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                        } else {
                            setSummaryResId(Resources.ID_NULL)
                        }
                    }
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_BAREBONE_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .build()
                    )
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_ALL_OPTIONAL_ID)
                            .setSearchTermsResId(android.R.string.ok)
                            .apply {
                                if (SdkLevel.isAtLeastU()) setPackageName(context.packageName)
                            }
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId(ISSUE_ONLY_GROUP_ID)
                    .apply {
                        if (SdkLevel.isAtLeastU()) {
                            setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                            setTitleResId(android.R.string.ok)
                            setSummaryResId(android.R.string.ok)
                            setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                        }
                    }
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setLoggingAllowed(false)
                            .apply {
                                if (SdkLevel.isAtLeastU()) {
                                    setNotificationsAllowed(true)
                                    setDeduplicationGroup("group")
                                    addPackageCertificateHash(PACKAGE_CERT_HASH_FAKE)
                                    addPackageCertificateHash(packageCertHash)
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_STATEFUL_GROUP_ID)
                    .addSafetySource(dynamicSafetySource(DYNAMIC_IN_STATEFUL_ID))
                    .addSafetySource(staticSafetySource(STATIC_IN_STATEFUL_ID))
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_STATELESS_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(dynamicSafetySource(DYNAMIC_IN_STATELESS_ID))
                    .addSafetySource(staticSafetySource(STATIC_IN_STATELESS_ID))
                    .addSafetySource(issueOnlySafetySource(ISSUE_ONLY_IN_STATELESS_ID))
                    .build()
            )
            .build()

    /**
     * A complex [SafetyCenterConfig] exploring different combinations of valid sources and groups.
     * Including sources that support all profiles.
     */
    val complexAllProfileConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(DYNAMIC_GROUP_ID)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setTitleForWorkResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .apply {
                                if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                                    setTitleForPrivateProfileResId(Resources.ID_NULL)
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(STATIC_GROUP_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_BAREBONE_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .build()
                    )
                    .addSafetySource(
                        staticAllProfileSafetySourceBuilder(STATIC_ALL_OPTIONAL_ID)
                            .setSearchTermsResId(android.R.string.ok)
                            .apply {
                                if (SdkLevel.isAtLeastU()) setPackageName(context.packageName)
                            }
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId(ISSUE_ONLY_GROUP_ID)
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setLoggingAllowed(false)
                            .apply {
                                if (SdkLevel.isAtLeastU()) {
                                    setNotificationsAllowed(true)
                                    setDeduplicationGroup("group")
                                    addPackageCertificateHash(PACKAGE_CERT_HASH_FAKE)
                                    addPackageCertificateHash(packageCertHash)
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_STATELESS_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_IN_STATELESS_ID).build()
                    )
                    .addSafetySource(
                        staticAllProfileSafetySourceBuilder(STATIC_IN_STATELESS_ID).build()
                    )
                    .addSafetySource(
                        issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_IN_STATELESS_ID).build()
                    )
                    .build()
            )
            .build()

    /** A [SafetyCenterConfig] containing only hidden sources. */
    val hiddenOnlyConfig =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder("some_collapsible_group")
                    .addSafetySource(
                        dynamicSafetySourceBuilder("some_hidden_source")
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder("some_rigid_group")
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        dynamicSafetySourceBuilder("another_hidden_source")
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .build()

    private fun dynamicSafetySource(id: String) = dynamicSafetySourceBuilder(id).build()

    fun dynamicSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
            .setId(id)
            .setPackageName(context.packageName)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_TEST_ACTIVITY)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

    private fun dynamicAllProfileSafetySourceBuilder(id: String) =
        dynamicSafetySourceBuilder(id)
            .setProfile(SafetySource.PROFILE_ALL)
            .setTitleForWorkResId(android.R.string.paste)
            .apply {
                if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                    setTitleForPrivateProfileResId(android.R.string.paste)
                }
            }

    private fun staticSafetySource(id: String) = staticSafetySourceBuilder(id).build()

    private fun staticSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_STATIC)
            .setId(id)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_TEST_ACTIVITY_EXPORTED)
            .setProfile(SafetySource.PROFILE_PRIMARY)

    private fun staticAllProfileSafetySourceBuilder(id: String) =
        staticSafetySourceBuilder(id)
            .setProfile(SafetySource.PROFILE_ALL)
            .setTitleForWorkResId(android.R.string.paste)
            .apply {
                if (SdkLevel.isAtLeastV() && Flags.privateProfileTitleApi()) {
                    setTitleForPrivateProfileResId(android.R.string.unknownName)
                }
            }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun issueOnlySafetySourceWithDuplicationInfo(id: String, deduplicationGroup: String) =
        issueOnlySafetySourceBuilder(id).setDeduplicationGroup(deduplicationGroup).build()

    private fun issueOnlySafetySource(id: String) = issueOnlySafetySourceBuilder(id).build()

    private fun issueOnlySafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_ISSUE_ONLY)
            .setId(id)
            .setPackageName(context.packageName)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

    private fun issueOnlyAllProfileSafetySourceBuilder(id: String) =
        issueOnlySafetySourceBuilder(id).setProfile(SafetySource.PROFILE_ALL)

    private fun safetySourcesGroupBuilder(id: String) =
        SafetySourcesGroup.Builder()
            .setId(id)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)

    private fun staticSafetySourcesGroupBuilder(id: String) =
        SafetySourcesGroup.Builder().setId(id).setTitleResId(android.R.string.paste)

    fun singleSourceConfig(safetySource: SafetySource) =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(SINGLE_SOURCE_GROUP_ID)
                    .addSafetySource(safetySource)
                    .build()
            )
            .build()

    companion object {
        /** ID of a source not used in any config. */
        const val SAMPLE_SOURCE_ID = "test_sample_source_id"

        /** Activity action: Launches the [TestActivity] used to check redirects in tests. */
        const val ACTION_TEST_ACTIVITY = "com.android.safetycenter.testing.action.TEST_ACTIVITY"

        /**
         * Activity action: Launches the [TestActivity] used to check redirects in tests, but with
         * an exported activity alias.
         */
        const val ACTION_TEST_ACTIVITY_EXPORTED =
            "com.android.safetycenter.testing.action.TEST_ACTIVITY_EXPORTED"

        /**
         * ID of the only source provided in [singleSourceConfig], [severityZeroConfig] and
         * [noPageOpenConfig].
         */
        // LINT.IfChange(single_source_id)
        const val SINGLE_SOURCE_ID = "test_single_source_id"
        // LINT.ThenChange(/tests/hostside/safetycenter/src/android/safetycenter/hostside/SafetyCenterInteractionLoggingHostTest.kt:single_source_id)

        /** ID of the only source provided in [singleSourceAllProfileConfig]. */
        const val SINGLE_SOURCE_ALL_PROFILE_ID = "test_single_source_all_profile_id"

        /** ID of the only source provided in [issueOnlySourceAllProfileConfig]. */
        const val ISSUE_ONLY_ALL_PROFILE_SOURCE_ID = "test_issue_only_all_profile_id"

        /**
         * ID of the only [SafetySourcesGroup] provided by [singleSourceConfig],
         * [severityZeroConfig] and [noPageOpenConfig].
         */
        const val SINGLE_SOURCE_GROUP_ID = "test_single_source_group_id"

        /**
         * SHA256 hash of a package certificate.
         *
         * <p>This is a fake certificate, and can be used to test failure cases, or to test a list
         * of certificates when only one match is required.
         */
        const val PACKAGE_CERT_HASH_FAKE = "feed12"

        /** An invalid SHA256 hash (not a byte string, not even number of chars). */
        const val PACKAGE_CERT_HASH_INVALID = "0124ppl"

        /** ID of a source provided by [multipleSourcesConfig] and [summaryTestConfig]. */
        const val SOURCE_ID_1 = "test_source_id_1"

        /** ID of a source provided by [multipleSourcesConfig] and [summaryTestConfig]. */
        const val SOURCE_ID_2 = "test_source_id_2"

        /** ID of a source provided by [multipleSourcesConfig] and [summaryTestConfig]. */
        const val SOURCE_ID_3 = "test_source_id_3"

        /** ID of a source provided by [summaryTestConfig]. */
        const val SOURCE_ID_4 = "test_source_id_4"

        /** ID of a source provided by [summaryTestConfig]. */
        const val SOURCE_ID_5 = "test_source_id_5"

        /** ID of a source provided by [summaryTestConfig]. */
        const val SOURCE_ID_6 = "test_source_id_6"

        /** ID of a source provided by [summaryTestConfig]. */
        const val SOURCE_ID_7 = "test_source_id_7"

        /**
         * ID of a source provided by [privacySubpageConfig] and
         * [privacySubpageWithoutDataSourcesConfig].
         */
        const val PRIVACY_SOURCE_ID_1 = "AndroidPermissionUsage"

        /**
         * ID of a [SafetySourcesGroup] provided by [multipleSourcesConfig], containing two sources
         * of ids [SOURCE_ID_1] and [SOURCE_ID_2].
         */
        const val MULTIPLE_SOURCES_GROUP_ID_1 = "test_multiple_sources_group_id_1"

        /**
         * ID of a [SafetySourcesGroup] provided by [multipleSourcesConfig], containing a single
         * source of id [SOURCE_ID_3].
         */
        const val MULTIPLE_SOURCES_GROUP_ID_2 = "test_multiple_sources_group_id_2"

        /**
         * ID of a [SafetySourcesGroup] provided by [multipleSourcesConfig], containing two sources
         * of ids [SOURCE_ID_4] and [SOURCE_ID_5].
         */
        const val MULTIPLE_SOURCES_GROUP_ID_3 = "test_multiple_sources_group_id_3"

        /**
         * ID of a [SafetySourcesGroup] provided by [summaryTestGroupConfig], containing sources:
         * [SOURCE_ID_1], [SOURCE_ID_2], [SOURCE_ID_3], [SOURCE_ID_4], [SOURCE_ID_5], [SOURCE_ID_6],
         * [SOURCE_ID_7], [STATIC_IN_STATEFUL_ID].
         */
        const val SUMMARY_TEST_GROUP_ID = "summary_test_group_id"

        /**
         * ID of a [SafetySourcesGroup] provided by [complexConfig], containing sources:
         * [DYNAMIC_BAREBONE_ID], [DYNAMIC_ALL_OPTIONAL_ID], [DYNAMIC_DISABLED_ID],
         * [DYNAMIC_HIDDEN_ID], [DYNAMIC_HIDDEN_WITH_SEARCH_ID], [DYNAMIC_OTHER_PACKAGE_ID]. And
         * provided by [complexAllProfileConfig], containing sources: [DYNAMIC_BAREBONE_ID],
         * [DYNAMIC_DISABLED_ID], [DYNAMIC_HIDDEN_ID].
         */
        const val DYNAMIC_GROUP_ID = "dynamic"

        /**
         * ID of a [SafetySourcesGroup] provided by [complexConfig] and [complexAllProfileConfig],
         * containing sources: [STATIC_BAREBONE_ID], [STATIC_ALL_OPTIONAL_ID].
         */
        const val STATIC_GROUP_ID = "static"

        /**
         * ID of a [SafetySourcesGroup] provided by [complexConfig] and [complexAllProfileConfig],
         * containing sources: [ISSUE_ONLY_BAREBONE_ID], [ISSUE_ONLY_ALL_OPTIONAL_ID].
         */
        const val ISSUE_ONLY_GROUP_ID = "issue_only"

        /**
         * ID of a [SafetySourcesGroup] provided by [complexConfig], containing sources:
         * [DYNAMIC_IN_STATEFUL_ID], [STATIC_IN_STATEFUL_ID].
         */
        const val MIXED_STATEFUL_GROUP_ID = "mixed_stateful"

        /**
         * ID of a [SafetySourcesGroup] provided by [complexConfig] and [complexAllProfileConfig],
         * containing sources: [DYNAMIC_IN_STATELESS_ID], [STATIC_IN_STATELESS_ID],
         * [ISSUE_ONLY_IN_STATELESS_ID].
         */
        const val MIXED_STATELESS_GROUP_ID = "mixed_stateless"

        /**
         * ID of a source provided by [complexConfig], [complexAllProfileConfig], and
         * [androidLockScreenSourcesConfig], this is a dynamic, primary profile only, visible source
         * for which only the required fields are set.
         */
        const val DYNAMIC_BAREBONE_ID = "dynamic_barebone"

        /**
         * ID of a source provided by [complexConfig] and [singleSourceOtherPackageConfig], this is
         * a dynamic, primary profile only, visible source belonging to the [OTHER_PACKAGE_NAME]
         * package for which only the required fields are set.
         */
        const val DYNAMIC_OTHER_PACKAGE_ID = "dynamic_other_package"

        /**
         * ID of a source provided by [complexConfig], this is a dynamic, primary profile only,
         * disabled by default source for which all the required and optional fields are set.
         * Notably, this includes the refresh on page open flag and a max severity level of
         * recommendation.
         */
        const val DYNAMIC_ALL_OPTIONAL_ID = "dynamic_all_optional"

        /**
         * ID of a source provided by [complexConfig], [complexAllProfileConfig], and
         * [androidLockScreenSourcesConfig], this is a dynamic, disabled by default source for which
         * only the required fields are set.
         */
        const val DYNAMIC_DISABLED_ID = "dynamic_disabled"

        /**
         * ID of a source provided by [complexConfig], [complexAllProfileConfig], and
         * [androidLockScreenSourcesConfig], this ism a dynamic, hidden by default source for which
         * only the required fields are set.
         */
        const val DYNAMIC_HIDDEN_ID = "dynamic_hidden"

        /**
         * ID of a source provided by [complexConfig], this is a dynamic, primary profile only,
         * hidden by default source for which all the required and optional fields are set.
         */
        const val DYNAMIC_HIDDEN_WITH_SEARCH_ID = "dynamic_hidden_with_search"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is a
         * static, primary profile only source for which only the required fields are set.
         */
        const val STATIC_BAREBONE_ID = "static_barebone"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is a
         * static source for which all the required and optional fields are set.
         */
        const val STATIC_ALL_OPTIONAL_ID = "static_all_optional"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is an
         * issue-only, primary profile only source for which only the required fields are set.
         */
        const val ISSUE_ONLY_BAREBONE_ID = "issue_only_barebone"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is an
         * issue-only source for which all the required and optional fields are set. Notably, this
         * includes the refresh on page open flag and a max severity level of recommendation.
         */
        const val ISSUE_ONLY_ALL_OPTIONAL_ID = "issue_only_all_optional"

        /**
         * ID of a source provided by [complexConfig], this is a generic, dynamic, primary profile
         * only, visible source.
         */
        const val DYNAMIC_IN_STATEFUL_ID = "dynamic_in_stateful"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is a
         * generic, dynamic, visible source.
         */
        const val DYNAMIC_IN_STATELESS_ID = "dynamic_in_stateless"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is an
         * issue-only source.
         */
        const val ISSUE_ONLY_IN_STATELESS_ID = "issue_only_in_stateless"

        /**
         * ID of a source provided by [complexConfig] and [summaryTestConfig], this is a generic,
         * static, primary profile only source.
         */
        const val STATIC_IN_STATEFUL_ID = "static_in_stateful"

        /**
         * ID of a source provided by [complexConfig] and [complexAllProfileConfig], this is a
         * generic, static source.
         */
        const val STATIC_IN_STATELESS_ID = "static_in_stateless"

        /** Package name for the [DYNAMIC_OTHER_PACKAGE_ID] source. */
        const val OTHER_PACKAGE_NAME = "other_package_name"

        private const val DEDUPLICATION_GROUP_1 = "deduplication_group_1"
        private const val DEDUPLICATION_GROUP_2 = "deduplication_group_2"
        private const val DEDUPLICATION_GROUP_3 = "deduplication_group_3"

        /**
         * ID of a [SafetySourcesGroup] provided by [settingsLockScreenSourceConfig] and
         * [androidLockScreenSourcesConfig], to replicate the lock screen sources group.
         */
        const val ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID = "AndroidLockScreenSources"

        /**
         * ID of a [SafetySourcesGroup] provided by [privacySubpageConfig] and
         * [privacySubpageWithoutDataSourcesConfig], to replicate the privacy sources group.
         */
        const val ANDROID_PRIVACY_SOURCES_GROUP_ID = "AndroidPrivacySources"

        private const val INTENT_ACTION_NOT_RESOLVING = "there.is.no.way.this.resolves"
    }
}
