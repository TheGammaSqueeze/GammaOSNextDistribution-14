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

package com.android.intentresolver

import com.android.intentresolver.ResolverDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the various implementations of {@link TargetPresentationGetter}.
 * TODO: consider expanding to cover icon logic (not just labels/sublabels).
 * TODO: these are conceptually "acceptance tests" that provide comprehensive coverage of the
 * apparent variations in the legacy implementation. The tests probably don't have to be so
 * exhaustive if we're able to impose a simpler design on the implementation.
 */
class TargetPresentationGetterTest {
  fun makeResolveInfoPresentationGetter(
          withSubstitutePermission: Boolean,
          appLabel: String,
          activityLabel: String,
          resolveInfoLabel: String): TargetPresentationGetter {
      val testPackageInfo = ResolverDataProvider.createPackageManagerMockedInfo(
              withSubstitutePermission, appLabel, activityLabel, resolveInfoLabel)
      val factory = TargetPresentationGetter.Factory(testPackageInfo.ctx, 100)
      return factory.makePresentationGetter(testPackageInfo.resolveInfo)
  }

  fun makeActivityInfoPresentationGetter(
          withSubstitutePermission: Boolean,
          appLabel: String?,
          activityLabel: String?): TargetPresentationGetter {
      val testPackageInfo = ResolverDataProvider.createPackageManagerMockedInfo(
              withSubstitutePermission, appLabel, activityLabel, "")
      val factory = TargetPresentationGetter.Factory(testPackageInfo.ctx, 100)
      return factory.makePresentationGetter(testPackageInfo.activityInfo)
  }

  @Test
  fun testActivityInfoLabels_noSubstitutePermission_distinctRequestedLabelAndSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(
              false, "app_label", "activity_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      assertThat(presentationGetter.getSubLabel()).isEqualTo("activity_label")
  }

  @Test
  fun testActivityInfoLabels_noSubstitutePermission_sameRequestedLabelAndSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(
              false, "app_label", "app_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // Without the substitute permission, there's no logic to dedupe the labels.
      // TODO: this matches our observations in the legacy code, but is it the right behavior? It
      // seems like {@link ResolverListAdapter.ViewHolder#bindLabel()} has some logic to dedupe in
      // the UI at least, but maybe that logic should be pulled back to the "presentation"?
      assertThat(presentationGetter.getSubLabel()).isEqualTo("app_label")
  }

  @Test
  fun testActivityInfoLabels_noSubstitutePermission_nullRequestedLabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(false, null, "activity_label")
      assertThat(presentationGetter.getLabel()).isNull()
      assertThat(presentationGetter.getSubLabel()).isEqualTo("activity_label")
  }

  @Test
  fun testActivityInfoLabels_noSubstitutePermission_emptyRequestedLabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(false, "", "activity_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("")
      assertThat(presentationGetter.getSubLabel()).isEqualTo("activity_label")
  }

  @Test
  fun testActivityInfoLabels_noSubstitutePermission_emptyRequestedSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(false, "app_label", "")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // Without the substitute permission, empty sublabels are passed through as-is.
      assertThat(presentationGetter.getSubLabel()).isEqualTo("")
  }

  @Test
  fun testActivityInfoLabels_withSubstitutePermission_distinctRequestedLabelAndSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(
              true, "app_label", "activity_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("activity_label")
      // With the substitute permission, the same ("activity") label is requested as both the label
      // and sublabel, even though the other value ("app_label") was distinct. Thus this behaves the
      // same as a dupe.
      assertThat(presentationGetter.getSubLabel()).isEqualTo(null)
  }

  @Test
  fun testActivityInfoLabels_withSubstitutePermission_sameRequestedLabelAndSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(
              true, "app_label", "app_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // With the substitute permission, duped sublabels get converted to nulls.
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testActivityInfoLabels_withSubstitutePermission_nullRequestedLabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(true, "app_label", null)
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // With the substitute permission, null inputs are a special case that produces null outputs
      // (i.e., they're not simply passed-through from the inputs).
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testActivityInfoLabels_withSubstitutePermission_emptyRequestedLabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(true, "app_label", "")
      // Empty "labels" are taken as-is and (unlike nulls) don't prompt a fallback to the sublabel.
      // Thus (as in the previous case with substitute permission & "distinct" labels), this is
      // treated as a dupe.
      assertThat(presentationGetter.getLabel()).isEqualTo("")
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testActivityInfoLabels_withSubstitutePermission_emptyRequestedSublabel() {
      val presentationGetter = makeActivityInfoPresentationGetter(true, "", "activity_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("activity_label")
      // With the substitute permission, empty sublabels get converted to nulls.
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testResolveInfoLabels_noSubstitutePermission_distinctRequestedLabelAndSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              false, "app_label", "activity_label", "resolve_info_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      assertThat(presentationGetter.getSubLabel()).isEqualTo("resolve_info_label")
  }

  @Test
  fun testResolveInfoLabels_noSubstitutePermission_sameRequestedLabelAndSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              false, "app_label", "activity_label", "app_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // Without the substitute permission, there's no logic to dedupe the labels.
      // TODO: this matches our observations in the legacy code, but is it the right behavior? It
      // seems like {@link ResolverListAdapter.ViewHolder#bindLabel()} has some logic to dedupe in
      // the UI at least, but maybe that logic should be pulled back to the "presentation"?
      assertThat(presentationGetter.getSubLabel()).isEqualTo("app_label")
  }

  @Test
  fun testResolveInfoLabels_noSubstitutePermission_emptyRequestedSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              false, "app_label", "activity_label", "")
      assertThat(presentationGetter.getLabel()).isEqualTo("app_label")
      // Without the substitute permission, empty sublabels are passed through as-is.
      assertThat(presentationGetter.getSubLabel()).isEqualTo("")
  }

  @Test
  fun testResolveInfoLabels_withSubstitutePermission_distinctRequestedLabelAndSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              true, "app_label", "activity_label", "resolve_info_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("activity_label")
      assertThat(presentationGetter.getSubLabel()).isEqualTo("resolve_info_label")
  }

  @Test
  fun testResolveInfoLabels_withSubstitutePermission_sameRequestedLabelAndSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              true, "app_label", "activity_label", "activity_label")
      assertThat(presentationGetter.getLabel()).isEqualTo("activity_label")
      // With the substitute permission, duped sublabels get converted to nulls.
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testResolveInfoLabels_withSubstitutePermission_emptyRequestedSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              true, "app_label", "activity_label", "")
      assertThat(presentationGetter.getLabel()).isEqualTo("activity_label")
      // With the substitute permission, empty sublabels get converted to nulls.
      assertThat(presentationGetter.getSubLabel()).isNull()
  }

  @Test
  fun testResolveInfoLabels_withSubstitutePermission_emptyRequestedLabelAndSublabel() {
      val presentationGetter = makeResolveInfoPresentationGetter(
              true, "app_label", "", "")
      assertThat(presentationGetter.getLabel()).isEqualTo("")
      // With the substitute permission, empty sublabels get converted to nulls.
      assertThat(presentationGetter.getSubLabel()).isNull()
  }
}
