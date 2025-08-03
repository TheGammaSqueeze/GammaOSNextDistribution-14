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

package android.safetycenter.cts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.os.Parcelables.forceParcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.internaldata.SafetyCenterBundles
import com.android.safetycenter.internaldata.SafetyCenterBundles.ISSUES_TO_GROUPS_BUNDLE_KEY
import com.android.safetycenter.internaldata.SafetyCenterBundles.STATIC_ENTRIES_TO_IDS_BUNDLE_KEY
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withDismissedIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withExtrasIfAtLeastU
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterData]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)

    private val status1 =
        SafetyCenterStatus.Builder("This is my title", "This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()
    private val status2 =
        SafetyCenterStatus.Builder("This is also my title", "This is also my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val issue1 =
        SafetyCenterIssue.Builder("iSsUe_iD_oNe", "An issue title", "An issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()
    private val issue2 =
        SafetyCenterIssue.Builder("iSsUe_iD_tWo", "Another issue title", "Another issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entry1 =
        SafetyCenterEntry.Builder("eNtRy_iD_OnE", "An entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .build()
    private val entry2 =
        SafetyCenterEntry.Builder("eNtRy_iD_TwO", "Another entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entryGroup1 =
        SafetyCenterEntryGroup.Builder("eNtRy_gRoUp_iD", "An entry group title")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    private val entryOrGroup1 = SafetyCenterEntryOrGroup(entry1)
    private val entryOrGroup2 = SafetyCenterEntryOrGroup(entryGroup1)

    private val staticEntry1 =
        SafetyCenterStaticEntry.Builder("A static entry title")
            .setSummary("A static entry summary")
            .setPendingIntent(pendingIntent)
            .build()
    private val staticEntry2 =
        SafetyCenterStaticEntry.Builder("Another static entry title")
            .setSummary("Another static entry summary")
            .setPendingIntent(pendingIntent)
            .build()

    private val staticEntryGroup1 =
        SafetyCenterStaticEntryGroup("A static entry group title", listOf(staticEntry1))
    private val staticEntryGroup2 =
        SafetyCenterStaticEntryGroup("Another static entry group title", listOf(staticEntry2))

    private val issueToGroupExtra1 =
        Bundle().apply { putStringArrayList(issue1.id, arrayListOf(entryGroup1.id)) }

    private val filledExtrasIssuesToGroups1 =
        Bundle().apply { putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, issueToGroupExtra1) }

    private val issueToGroupExtra2 =
        Bundle().apply { putStringArrayList(issue2.id, arrayListOf(entryGroup1.id)) }

    private val filledExtrasIssuesToGroups2 =
        Bundle().apply { putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, issueToGroupExtra2) }

    private val staticEntryToId1 =
        Bundle().apply {
            putString(SafetyCenterBundles.toBundleKey(staticEntry1), "StaticEntryId1")
        }

    private val filledExtrasStaticEntriesToIds1 =
        Bundle().apply { putBundle(STATIC_ENTRIES_TO_IDS_BUNDLE_KEY, staticEntryToId1) }

    private val staticEntryToId2 =
        Bundle().apply {
            putString(SafetyCenterBundles.toBundleKey(staticEntry2), "StaticEntryId2")
        }

    private val filledExtrasStaticEntriesToIds2 =
        Bundle().apply { putBundle(STATIC_ENTRIES_TO_IDS_BUNDLE_KEY, staticEntryToId2) }

    private val filledAllExtras =
        Bundle().apply {
            val allIssuesToGroups = Bundle()
            allIssuesToGroups.putAll(issueToGroupExtra1)
            allIssuesToGroups.putAll(issueToGroupExtra2)
            putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, allIssuesToGroups)
            val allStaticEntriesToIds = Bundle()
            allStaticEntriesToIds.putAll(staticEntryToId1)
            allStaticEntriesToIds.putAll(staticEntryToId2)
            putBundle(STATIC_ENTRIES_TO_IDS_BUNDLE_KEY, allStaticEntriesToIds)
        }

    private val filledOneKnownOneUnknown =
        Bundle().apply {
            val allIssuesToGroups = Bundle()
            allIssuesToGroups.putAll(issueToGroupExtra1)
            allIssuesToGroups.putAll(issueToGroupExtra2)
            putBundle(ISSUES_TO_GROUPS_BUNDLE_KEY, allIssuesToGroups)
            putInt("unknown_key", 1)
        }

    private val unknownExtras = Bundle().apply { putString("key", "value") }

    private val data1 =
        SafetyCenterData(status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
    private val data2 =
        SafetyCenterData(status2, listOf(issue2), listOf(entryOrGroup2), listOf(staticEntryGroup2))

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getStatus_withDefaultBuilder_returnsStatus() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.status).isEqualTo(status1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getIssues_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.issues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getIssues_whenSetExplicitly_returnsIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1).addIssue(issue1).addIssue(issue2).build()

        assertThat(safetyCenterData.issues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getEntriesOrGroups_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.entriesOrGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getEntriesOrGroups_whenSetExplicitly_returnsEntriesOrGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addEntryOrGroup(entryOrGroup1)
                .addEntryOrGroup(entryOrGroup2)
                .build()

        assertThat(safetyCenterData.entriesOrGroups)
            .containsExactly(entryOrGroup1, entryOrGroup2)
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getStaticGroups_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.staticEntryGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getStaticEntryGroups_whenSetExplicitly_returnsStaticEntryGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addStaticEntryGroup(staticEntryGroup1)
                .addStaticEntryGroup(staticEntryGroup2)
                .build()

        assertThat(safetyCenterData.staticEntryGroups)
            .containsExactly(staticEntryGroup1, staticEntryGroup2)
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDismissedIssues_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.dismissedIssues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDismissedIssues_whenSetExplicitly_returnsIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addDismissedIssue(issue1)
                .addDismissedIssue(issue2)
                .build()

        assertThat(safetyCenterData.dismissedIssues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_withDefaultBuilder_returnsEmptyBundle() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.extras.keySet()).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_whenSetExplicitly_returnsExtras() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1).setExtras(filledExtrasIssuesToGroups1).build()

        val extras = safetyCenterData.extras
        val issuesToGroups = extras.getBundle(ISSUES_TO_GROUPS_BUNDLE_KEY)
        val groups = issuesToGroups!!.getStringArrayList(issue1.id)

        assertThat(issuesToGroups.keySet().size).isEqualTo(1)
        assertThat(groups).isEqualTo(arrayListOf(entryGroup1.id))
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getExtras_whenCleared_returnsEmptyBundle() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .setExtras(filledExtrasIssuesToGroups1)
                .clearExtras()
                .build()

        assertThat(safetyCenterData.extras.keySet()).isEmpty()
    }

    @Test
    fun getStatus_returnsStatus() {
        assertThat(data1.status).isEqualTo(status1)
        assertThat(data2.status).isEqualTo(status2)
    }

    @Test
    fun getIssues_returnsIssues() {
        assertThat(data1.issues).containsExactly(issue1)
        assertThat(data2.issues).containsExactly(issue2)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDismissedIssues_returnsDismissedIssues() {
        val data3 = data1.withDismissedIssuesIfAtLeastU(listOf(issue2))

        assertThat(data1.dismissedIssues).isEmpty()
        assertThat(data3.dismissedIssues).containsExactly(issue2)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getDismissedIssues_mutationsAreNotAllowed() {
        val mutatedDismissedIssues = data1.dismissedIssues

        assertFailsWith(UnsupportedOperationException::class) { mutatedDismissedIssues.add(issue2) }
    }

    @Test
    fun getIssues_mutationsAreNotAllowed() {
        val mutatedIssues = data1.issues

        assertFailsWith(UnsupportedOperationException::class) { mutatedIssues.add(issue2) }
    }

    @Test
    fun getEntriesOrGroups_returnsEntriesOrGroups() {
        assertThat(data1.entriesOrGroups).containsExactly(entryOrGroup1)
        assertThat(data2.entriesOrGroups).containsExactly(entryOrGroup2)
    }

    @Test
    fun getEntriesOrGroups_mutationsAreNotAllowed() {
        val mutatedEntriesOrGroups = data1.entriesOrGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedEntriesOrGroups.add(entryOrGroup2)
        }
    }

    @Test
    fun getStaticEntryGroups_returnsStaticEntryGroups() {
        assertThat(data1.staticEntryGroups).containsExactly(staticEntryGroup1)
        assertThat(data2.staticEntryGroups).containsExactly(staticEntryGroup2)
    }

    @Test
    fun getStaticEntryGroups_mutationsAreNotAllowed() {
        val mutatedStaticEntryGroups = data1.staticEntryGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedStaticEntryGroups.add(staticEntryGroup2)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_addIssue_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder = SafetyCenterData.Builder(status1).addIssue(issue1)
        val issues = safetyCenterDataBuilder.build().issues

        safetyCenterDataBuilder.addIssue(issue2)

        assertThat(issues).containsExactly(issue1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_clearIssues_removesAllIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .clearIssues()
                .build()

        assertThat(safetyCenterData.issues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_addEntryOrGroup_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder =
            SafetyCenterData.Builder(status1).addEntryOrGroup(entryOrGroup1)
        val entriesOrGroups = safetyCenterDataBuilder.build().entriesOrGroups

        safetyCenterDataBuilder.addEntryOrGroup(entryOrGroup2)

        assertThat(entriesOrGroups).containsExactly(entryOrGroup1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_clearEntriesOrGroups_removesAllEntriesOrGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addEntryOrGroup(entryOrGroup1)
                .addEntryOrGroup(entryOrGroup2)
                .clearEntriesOrGroups()
                .build()

        assertThat(safetyCenterData.entriesOrGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_addStaticEntryGroup_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder =
            SafetyCenterData.Builder(status1).addStaticEntryGroup(staticEntryGroup1)
        val staticEntryGroups = safetyCenterDataBuilder.build().staticEntryGroups

        safetyCenterDataBuilder.addStaticEntryGroup(staticEntryGroup2)

        assertThat(staticEntryGroups).containsExactly(staticEntryGroup1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_clearStaticEntryGroups_removesAllStaticEntryGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addStaticEntryGroup(staticEntryGroup1)
                .addStaticEntryGroup(staticEntryGroup2)
                .clearStaticEntryGroups()
                .build()

        assertThat(safetyCenterData.staticEntryGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_addDismissedIssue_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder = SafetyCenterData.Builder(status1).addDismissedIssue(issue1)
        val dismissedIssues = safetyCenterDataBuilder.build().dismissedIssues

        safetyCenterDataBuilder.addDismissedIssue(issue2)

        assertThat(dismissedIssues).containsExactly(issue1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun builder_clearDismissedIssues_removesAllDismissedIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addDismissedIssue(issue1)
                .addDismissedIssue(issue2)
                .clearDismissedIssues()
                .build()

        assertThat(safetyCenterData.dismissedIssues).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(data1.describeContents()).isEqualTo(0)
        assertThat(data2.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(data1).recreatesEqual(SafetyCenterData.CREATOR)
        assertThat(data2).recreatesEqual(SafetyCenterData.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_withDismissedIssues_recreatesEqual() {
        val data3 = data1.withDismissedIssuesIfAtLeastU(listOf(issue2))
        val data4 = data2.withDismissedIssuesIfAtLeastU(listOf(issue1))

        assertThat(data3).recreatesEqual(SafetyCenterData.CREATOR)
        assertThat(data4).recreatesEqual(SafetyCenterData.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun parcelRoundTrip_withExtras_recreatesEqual() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(filledAllExtras)
        val safetyCenterDatafromParcel =
            forceParcel(safetyCenterDataWithExtras, SafetyCenterData.CREATOR)

        assertThat(safetyCenterDatafromParcel).isEqualTo(safetyCenterDataWithExtras)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        val equalsHashCodeToStringTester =
            EqualsHashCodeToStringTester.ofParcelable(parcelableCreator = SafetyCenterData.CREATOR)
                .addEqualityGroup(
                    data1,
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    data2,
                    SafetyCenterData(
                        status2,
                        listOf(issue2),
                        listOf(entryOrGroup2),
                        listOf(staticEntryGroup2)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(status1, listOf(), listOf(), listOf()),
                    SafetyCenterData(status1, listOf(), listOf(), listOf())
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status2,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue2),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup2),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup2)
                    )
                )

        equalsHashCodeToStringTester.test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun equalsHashCode_atLeastU_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterData.CREATOR,
                ignoreToString = true,
                createCopy = { SafetyCenterData.Builder(it).build() }
            )
            .addEqualityGroup(
                data1,
                SafetyCenterData(
                    status1,
                    listOf(issue1),
                    listOf(entryOrGroup1),
                    listOf(staticEntryGroup1)
                ),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(unknownExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledExtrasIssuesToGroups1)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledOneKnownOneUnknown)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledAllExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledExtrasStaticEntriesToIds1)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledExtrasStaticEntriesToIds2)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addIssue(issue1)
                    .setExtras(filledExtrasIssuesToGroups1)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addIssue(issue1)
                    .setExtras(filledExtrasIssuesToGroups1)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addIssue(issue1)
                    .setExtras(filledExtrasIssuesToGroups2)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup2)
                    .addIssue(issue1)
                    .setExtras(filledExtrasStaticEntriesToIds2)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .addDismissedIssue(issue2)
                    .build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withSingleKnownExtra_containsKnownExtra() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(filledExtrasIssuesToGroups1)

        val stringRepresentation = safetyCenterDataWithExtras.toString()

        assertThat(stringRepresentation).contains("IssuesToGroups")
        assertThat(stringRepresentation).doesNotContain("StaticEntriesToIds")
        assertThat(stringRepresentation).doesNotContain("has unknown extras")
        assertThat(stringRepresentation).doesNotContain("no extras")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withAllKnownExtras_containsKnownExtras() {
        val safetyCenterDataWithAllExtras = data1.withExtrasIfAtLeastU(filledAllExtras)

        val stringRepresentation = safetyCenterDataWithAllExtras.toString()

        assertThat(stringRepresentation).contains("IssuesToGroups")
        assertThat(stringRepresentation).contains("StaticEntriesToIds")
        assertThat(stringRepresentation).doesNotContain("has unknown extras")
        assertThat(stringRepresentation).doesNotContain("no extras")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withOneKnowAndOneUnknownExtra_containsKnownAndUnknownExtras() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(filledOneKnownOneUnknown)

        val stringRepresentation = safetyCenterDataWithExtras.toString()

        assertThat(stringRepresentation).contains("IssuesToGroups")
        assertThat(stringRepresentation).contains("has unknown extras")
        assertThat(stringRepresentation).doesNotContain("StaticEntriesToIds")
        assertThat(stringRepresentation).doesNotContain("no extras")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withSingleUnknownExtra_containsUnknownExtras() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(unknownExtras)

        val stringRepresentation = safetyCenterDataWithExtras.toString()

        assertThat(stringRepresentation).contains("has unknown extras")
        assertThat(stringRepresentation).doesNotContain("IssuesToGroups")
        assertThat(stringRepresentation).doesNotContain("StaticEntriesToIds")
        assertThat(stringRepresentation).doesNotContain("no extras")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun toString_withoutExtras_containsNoExtras() {
        val safetyCenterDataWithoutExtras = data1

        val stringRepresentation = safetyCenterDataWithoutExtras.toString()

        assertThat(stringRepresentation).contains("no extras")
        assertThat(stringRepresentation).doesNotContain("IssuesToGroups")
        assertThat(stringRepresentation).doesNotContain("StaticEntriesToIds")
        assertThat(stringRepresentation).doesNotContain("has unknown extras")
    }
}
