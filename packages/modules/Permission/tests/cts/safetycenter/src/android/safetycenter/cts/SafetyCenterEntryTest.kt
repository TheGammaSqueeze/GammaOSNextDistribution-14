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

package android.safetycenter.cts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.safetycenter.SafetyCenterEntry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterEntry]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterEntryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent1 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent2 =
        PendingIntent.getActivity(
            context,
            0,
            Intent("Fake Different Data"),
            PendingIntent.FLAG_IMMUTABLE
        )

    private val iconAction1 =
        SafetyCenterEntry.IconAction(
            SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
            pendingIntent1
        )
    private val iconAction2 =
        SafetyCenterEntry.IconAction(
            SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
            pendingIntent2
        )

    private val entry1 =
        SafetyCenterEntry.Builder("eNtRy_iD", "a title")
            .setSummary("a summary")
            .setPendingIntent(pendingIntent1)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setIconAction(iconAction1)
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(SafetyCenterEntry.Builder(entry1).setId("id_one").build().id).isEqualTo("id_one")
        assertThat(SafetyCenterEntry.Builder(entry1).setId("id_two").build().id).isEqualTo("id_two")
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterEntry.Builder(entry1).setTitle("a title").build().title)
            .isEqualTo("a title")
        assertThat(SafetyCenterEntry.Builder(entry1).setTitle("another title").build().title)
            .isEqualTo("another title")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary("a summary").build().summary)
            .isEqualTo("a summary")
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary("another summary").build().summary)
            .isEqualTo("another summary")
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary(null).build().summary).isNull()
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                    .build()
                    .severityLevel
            )
            .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSeverityUnspecifiedIconType_returnsSeverityUnspecifiedIconType() {
        assertThat(entry1.severityUnspecifiedIconType)
            .isEqualTo(SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .build()
                    .severityUnspecifiedIconType
            )
            .isEqualTo(SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
    }

    @Test
    fun isEnabled_returnsIsEnabled() {
        assertThat(SafetyCenterEntry.Builder(entry1).setEnabled(true).build().isEnabled).isTrue()
        assertThat(SafetyCenterEntry.Builder(entry1).setEnabled(false).build().isEnabled).isFalse()
    }

    @Test
    fun isEnabled_defaultTrue() {
        assertThat(
                SafetyCenterEntry.Builder("eNtRy_iD", "a title")
                    .setPendingIntent(pendingIntent1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
                    .build()
                    .isEnabled
            )
            .isTrue()
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setPendingIntent(pendingIntent1)
                    .build()
                    .pendingIntent
            )
            .isEqualTo(pendingIntent1)
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setPendingIntent(pendingIntent2)
                    .build()
                    .pendingIntent
            )
            .isEqualTo(pendingIntent2)
        assertThat(SafetyCenterEntry.Builder(entry1).setPendingIntent(null).build().pendingIntent)
            .isNull()
    }

    @Test
    fun getIconAction_returnsIconAction() {
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(iconAction1).build().iconAction)
            .isEqualTo(iconAction1)
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(iconAction2).build().iconAction)
            .isEqualTo(iconAction2)
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(null).build().iconAction)
            .isNull()
    }

    @Test
    fun build_withInvalidEntrySeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterEntry.Builder(entry1).setSeverityLevel(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected EntrySeverityLevel for SafetyCenterEntry: -1")
    }

    @Test
    fun build_withInvalidSeverityUnspecifiedIconType_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterEntry.Builder(entry1).setSeverityUnspecifiedIconType(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected SeverityUnspecifiedIconType for SafetyCenterEntry: -1")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entry1.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(entry1).recreatesEqual(SafetyCenterEntry.CREATOR)
        assertThat(
                SafetyCenterEntry.Builder(entry1)
                    .setSummary(null)
                    .setPendingIntent(null)
                    .setIconAction(null)
                    .build()
            )
            .recreatesEqual(SafetyCenterEntry.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterEntry.CREATOR,
                createCopy = { SafetyCenterEntry.Builder(it).build() }
            )
            .addEqualityGroup(entry1)
            .addEqualityGroup(
                SafetyCenterEntry.Builder("id", "a title")
                    .setSummary("a summary")
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .setPendingIntent(pendingIntent1)
                    .setIconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
                        pendingIntent2
                    )
                    .build(),
                SafetyCenterEntry.Builder("id", "a title")
                    .setSummary("a summary")
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .setPendingIntent(pendingIntent1)
                    .setIconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
                        pendingIntent2
                    )
                    .build()
            )
            .addEqualityGroup(SafetyCenterEntry.Builder(entry1).setId("a different id").build())
            .addEqualityGroup(
                SafetyCenterEntry.Builder(entry1).setTitle("a different title").build()
            )
            .addEqualityGroup(
                SafetyCenterEntry.Builder(entry1).setSummary("a different summary").build()
            )
            .addEqualityGroup(
                SafetyCenterEntry.Builder(entry1)
                    .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterEntry.Builder(entry1)
                    .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
                    )
                    .build()
            )
            .addEqualityGroup(SafetyCenterEntry.Builder(entry1).setEnabled(false).build())
            .addEqualityGroup(
                SafetyCenterEntry.Builder(entry1).setPendingIntent(pendingIntent2).build()
            )
            .addEqualityGroup(SafetyCenterEntry.Builder(entry1).setIconAction(iconAction2).build())
            .test()
    }

    @Test
    fun iconAction_getType_returnsType() {
        assertThat(
                SafetyCenterEntry.IconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
                        pendingIntent1
                    )
                    .type
            )
            .isEqualTo(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR)
        assertThat(
                SafetyCenterEntry.IconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
                        pendingIntent1
                    )
                    .type
            )
            .isEqualTo(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO)
    }

    @Test
    fun iconAction_getPendingIntent_returnsPendingIntent() {
        assertThat(
                SafetyCenterEntry.IconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
                        pendingIntent1
                    )
                    .pendingIntent
            )
            .isEqualTo(pendingIntent1)
        assertThat(
                SafetyCenterEntry.IconAction(
                        SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
                        pendingIntent2
                    )
                    .pendingIntent
            )
            .isEqualTo(pendingIntent2)
    }

    @Test
    fun iconAction_withInvalidIconActionType_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterEntry.IconAction(-1, pendingIntent1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IconActionType for IconAction: -1")
    }

    @Test
    fun iconAction_describeContents_returns0() {
        assertThat(iconAction1.describeContents()).isEqualTo(0)
    }

    @Test
    fun iconAction_parcelRoundTrip_recreatesEqual() {
        assertThat(iconAction1).recreatesEqual(SafetyCenterEntry.IconAction.CREATOR)
    }

    @Test
    fun iconAction_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterEntry.IconAction.CREATOR
            )
            .addEqualityGroup(
                iconAction1,
                SafetyCenterEntry.IconAction(
                    SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
                    pendingIntent1
                )
            )
            .addEqualityGroup(
                iconAction2,
                SafetyCenterEntry.IconAction(
                    SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
                    pendingIntent2
                )
            )
            .addEqualityGroup(
                SafetyCenterEntry.IconAction(
                    SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO,
                    pendingIntent1
                )
            )
            .addEqualityGroup(
                SafetyCenterEntry.IconAction(
                    SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR,
                    pendingIntent2
                )
            )
            .test()
    }
}
