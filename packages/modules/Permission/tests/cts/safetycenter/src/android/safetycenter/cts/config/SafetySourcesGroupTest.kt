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

package android.safetycenter.cts.config

import android.content.res.Resources
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.config.SafetySourcesGroup
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.modules.utils.build.SdkLevel
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourcesGroup]. */
@RunWith(AndroidJUnit4::class)
class SafetySourcesGroupTest {

    @Test
    fun getType_returnsType() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
        assertThat(STATEFUL_INFERRED_WITH_ICON.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
        assertThat(STATELESS_INFERRED.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
        assertThat(HIDDEN_INFERRED.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
            assertThat(STATEFUL_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
            assertThat(STATELESS_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
            assertThat(STATELESS_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
            assertThat(HIDDEN_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
            assertThat(HIDDEN_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
        }
    }

    @Test
    fun getId_returnsId() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.id).isEqualTo(STATEFUL_INFERRED_WITH_SUMMARY_ID)
        assertThat(STATEFUL_INFERRED_WITH_ICON.id).isEqualTo(STATEFUL_INFERRED_WITH_ICON_ID)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.id).isEqualTo(STATEFUL_INFERRED_WITH_BOTH_ID)
        assertThat(STATELESS_INFERRED.id).isEqualTo(STATELESS_INFERRED_ID)
        assertThat(HIDDEN_INFERRED.id).isEqualTo(HIDDEN_INFERRED_ID)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.id).isEqualTo(STATEFUL_BAREBONE_ID)
            assertThat(STATEFUL_ALL_OPTIONAL.id).isEqualTo(STATEFUL_ALL_OPTIONAL_ID)
            assertThat(STATELESS_BAREBONE.id).isEqualTo(STATELESS_BAREBONE_ID)
            assertThat(STATELESS_ALL_OPTIONAL.id).isEqualTo(STATELESS_ALL_OPTIONAL_ID)
            assertThat(HIDDEN_BAREBONE.id).isEqualTo(HIDDEN_BAREBONE_ID)
            assertThat(HIDDEN_ALL_OPTIONAL.id).isEqualTo(HIDDEN_ALL_OPTIONAL_ID)
        }
    }

    @Test
    fun getTitleResId_returnsTitleResId() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATEFUL_INFERRED_WITH_ICON.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATELESS_INFERRED.titleResId).isEqualTo(REFERENCE_RES_ID)
        // This is not an enforced invariant, titleResId should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.titleResId).isEqualTo(Resources.ID_NULL)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATEFUL_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATELESS_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATELESS_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(HIDDEN_BAREBONE.titleResId).isEqualTo(Resources.ID_NULL)
            assertThat(HIDDEN_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        }
    }

    @Test
    fun getSummaryResId_returnsSummaryResId() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATEFUL_INFERRED_WITH_ICON.summaryResId).isEqualTo(Resources.ID_NULL)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATELESS_INFERRED.summaryResId).isEqualTo(Resources.ID_NULL)
        // This is not an enforced invariant, summaryResId should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.summaryResId).isEqualTo(Resources.ID_NULL)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATEFUL_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATELESS_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(STATELESS_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(HIDDEN_BAREBONE.titleResId).isEqualTo(Resources.ID_NULL)
            assertThat(HIDDEN_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        }
    }

    @Test
    fun getStatelessIconType_returnsStatelessIconType() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        assertThat(STATEFUL_INFERRED_WITH_ICON.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        assertThat(STATELESS_INFERRED.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        // This is not an enforced invariant
        // statelessIconType should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(STATEFUL_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
            assertThat(STATELESS_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(STATELESS_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
            assertThat(HIDDEN_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(HIDDEN_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        }
    }

    @Test
    fun getSafetySources_returnsSafetySources() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.safetySources)
            .containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
        assertThat(STATEFUL_INFERRED_WITH_ICON.safetySources)
            .containsExactly(SafetySourceTest.STATIC_BAREBONE)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.safetySources)
            .containsExactly(
                SafetySourceTest.DYNAMIC_BAREBONE,
                SafetySourceTest.STATIC_BAREBONE,
                SafetySourceTest.ISSUE_ONLY_BAREBONE
            )
            .inOrder()
        assertThat(STATELESS_INFERRED.safetySources)
            .containsExactly(SafetySourceTest.STATIC_BAREBONE)
        assertThat(HIDDEN_INFERRED.safetySources)
            .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
            assertThat(STATEFUL_ALL_OPTIONAL.safetySources)
                .containsExactly(
                    SafetySourceTest.DYNAMIC_BAREBONE,
                    SafetySourceTest.STATIC_BAREBONE,
                    SafetySourceTest.ISSUE_ONLY_BAREBONE
                )
            assertThat(STATELESS_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.STATIC_BAREBONE)
            assertThat(STATELESS_ALL_OPTIONAL.safetySources)
                .containsExactly(
                    SafetySourceTest.DYNAMIC_BAREBONE,
                    SafetySourceTest.STATIC_BAREBONE,
                    SafetySourceTest.ISSUE_ONLY_BAREBONE
                )
            assertThat(HIDDEN_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
            assertThat(HIDDEN_ALL_OPTIONAL.safetySources)
                .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
        }
    }

    @Test
    fun getSafetySources_mutationsAreNotAllowed() {
        val sources = STATEFUL_INFERRED_WITH_SUMMARY.safetySources

        assertFailsWith(UnsupportedOperationException::class) {
            sources.add(SafetySourceTest.DYNAMIC_BAREBONE)
        }
    }

    @Test
    fun builder_addSafetySource_doesNotMutatePreviouslyBuiltInstance() {
        val safetySourcesGroupBuilder =
            SafetySourcesGroup.Builder()
                .setId(STATEFUL_INFERRED_WITH_SUMMARY_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
        val sources = safetySourcesGroupBuilder.build().safetySources

        safetySourcesGroupBuilder.addSafetySource(SafetySourceTest.STATIC_BAREBONE)

        assertThat(sources).containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_hiddenGroupWithDynamicSource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                .setId(HIDDEN_BAREBONE_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups of type hidden can only contain sources of type issue-only"
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_hiddenGroupWithStaticSource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                .setId(HIDDEN_BAREBONE_ID)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups of type hidden can only contain sources of type issue-only"
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_statefulGroupWithIssueOnlySource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
                .setId(STATEFUL_BAREBONE_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups containing only sources of type issue-only must be of " +
                    "type hidden"
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun build_statelessGroupWithIssueOnlySource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                .setId(STATELESS_BAREBONE_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups containing only sources of type issue-only must be of " +
                    "type hidden"
            )
    }

    @Test
    fun describeContents_returns0() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY.describeContents()).isEqualTo(0)
        assertThat(STATEFUL_INFERRED_WITH_ICON.describeContents()).isEqualTo(0)
        assertThat(STATEFUL_INFERRED_WITH_BOTH.describeContents()).isEqualTo(0)
        assertThat(STATELESS_INFERRED.describeContents()).isEqualTo(0)
        assertThat(HIDDEN_INFERRED.describeContents()).isEqualTo(0)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(STATEFUL_ALL_OPTIONAL.describeContents()).isEqualTo(0)
            assertThat(STATELESS_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(STATELESS_ALL_OPTIONAL.describeContents()).isEqualTo(0)
            assertThat(HIDDEN_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(HIDDEN_ALL_OPTIONAL.describeContents()).isEqualTo(0)
        }
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(STATEFUL_INFERRED_WITH_SUMMARY).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(STATEFUL_INFERRED_WITH_ICON).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(STATEFUL_INFERRED_WITH_BOTH).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(STATELESS_INFERRED).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(HIDDEN_INFERRED).recreatesEqual(SafetySourcesGroup.CREATOR)
        if (SdkLevel.isAtLeastU()) {
            assertThat(STATEFUL_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(STATEFUL_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(STATELESS_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(STATELESS_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(HIDDEN_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(HIDDEN_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
        }
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetySourcesGroup.CREATOR,
                createCopy =
                    if (SdkLevel.isAtLeastU()) {
                        { SafetySourcesGroup.Builder(it).build() }
                    } else {
                        null
                    }
            )
            .addEqualityGroup(STATEFUL_INFERRED_WITH_SUMMARY)
            .addEqualityGroup(STATEFUL_INFERRED_WITH_ICON)
            .addEqualityGroup(
                *mutableListOf(
                        STATEFUL_INFERRED_WITH_BOTH,
                        SafetySourcesGroup.Builder()
                            .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                            .setTitleResId(REFERENCE_RES_ID)
                            .setSummaryResId(REFERENCE_RES_ID)
                            .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                            .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                            .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                            .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                            .build()
                    )
                    .apply { if (SdkLevel.isAtLeastU()) add(STATEFUL_ALL_OPTIONAL) }
                    .toTypedArray()
            )
            .addEqualityGroup(
                *mutableListOf(STATELESS_INFERRED)
                    .apply { if (SdkLevel.isAtLeastU()) add(STATELESS_BAREBONE) }
                    .toTypedArray()
            )
            .addEqualityGroup(
                *mutableListOf(HIDDEN_INFERRED)
                    .apply { if (SdkLevel.isAtLeastU()) add(HIDDEN_BAREBONE) }
                    .toTypedArray()
            )
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId("other")
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()
            )
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(-1)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()
            )
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(-1)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()
            )
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()
            )
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .build()
            )
            .apply {
                if (SdkLevel.isAtLeastU()) {
                    addEqualityGroup(STATEFUL_BAREBONE)
                    addEqualityGroup(STATELESS_ALL_OPTIONAL)
                    addEqualityGroup(HIDDEN_ALL_OPTIONAL)
                }
            }
            .test()
    }

    companion object {
        private const val REFERENCE_RES_ID = 9999

        private const val STATEFUL_BAREBONE_ID = "stateful_barebone"
        private const val STATEFUL_ALL_OPTIONAL_ID = "stateful_all_optional"
        private const val STATELESS_BAREBONE_ID = "stateless_barebone"
        private const val STATELESS_ALL_OPTIONAL_ID = "stateless_all_optional"
        private const val HIDDEN_BAREBONE_ID = "hidden_barebone"
        private const val HIDDEN_ALL_OPTIONAL_ID = "hidden_all_optional"
        private const val STATEFUL_INFERRED_WITH_SUMMARY_ID = "stateful_inferred_with_summary"
        private const val STATEFUL_INFERRED_WITH_ICON_ID = "stateful_inferred_with_icon"
        private const val STATEFUL_INFERRED_WITH_BOTH_ID = STATEFUL_ALL_OPTIONAL_ID
        private const val STATELESS_INFERRED_ID = STATELESS_BAREBONE_ID
        private const val HIDDEN_INFERRED_ID = HIDDEN_BAREBONE_ID

        // TODO(b/230078826): Consider extracting shared constants to a separate file.
        internal val STATEFUL_INFERRED_WITH_SUMMARY =
            SafetySourcesGroup.Builder()
                .setId(STATEFUL_INFERRED_WITH_SUMMARY_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                .build()

        private val STATEFUL_INFERRED_WITH_ICON =
            SafetySourcesGroup.Builder()
                .setId(STATEFUL_INFERRED_WITH_ICON_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .build()

        private val STATEFUL_INFERRED_WITH_BOTH =
            SafetySourcesGroup.Builder()
                .setId(STATEFUL_INFERRED_WITH_BOTH_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                .build()

        private val STATEFUL_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
                    .setId(STATEFUL_BAREBONE_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()

        private val STATEFUL_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL)
                    .setId(STATEFUL_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        internal val STATELESS_INFERRED =
            SafetySourcesGroup.Builder()
                .setId(STATELESS_INFERRED_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .build()

        private val STATELESS_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                    .setId(STATELESS_BAREBONE_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .build()

        private val STATELESS_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATELESS)
                    .setId(STATELESS_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        internal val HIDDEN_INFERRED =
            SafetySourcesGroup.Builder()
                .setId(HIDDEN_INFERRED_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                .build()

        private val HIDDEN_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                    .setId(HIDDEN_BAREBONE_ID)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        private val HIDDEN_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                    .setId(HIDDEN_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()
    }
}
