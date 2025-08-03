/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.searchuiservice.cts;

import static com.google.common.truth.Truth.assertThat;
import static androidx.test.InstrumentationRegistry.getContext;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SearchTarget}
 *
 * atest CtsSearchUiServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SearchTargetTest {
    private static final String ID = "ID";
    private static final String ID2 = "ID2";
    private static final String PARENTID = "PARENTID";
    private static final String TITLE = "TITLE";
    private static final String LAYOUT_TYPE = "hero";
    private static final int RESULT_CORPUS = 1 << 0;
    private static final String PACKAGE_NAME = "package name";
    private static final Bundle EXTRAS = new Bundle();
    private static final float SCORE = 0.5f;
    private static final boolean SHOULD_HIDE = false;
    private static final SearchAction SEARCH_ACTION = new SearchAction.Builder(ID2, TITLE)
            .setIntent(new Intent())
            .build();
    private static final Uri SLICE_URI = Uri.EMPTY;

    SearchTarget.Builder mBuilderAction = new SearchTarget.Builder(RESULT_CORPUS, LAYOUT_TYPE, ID)
            .setPackageName(PACKAGE_NAME)
            .setUserHandle(UserHandle.CURRENT)
            .setSearchAction(SEARCH_ACTION)
            .setScore(SCORE)
            .setHidden(SHOULD_HIDE)
            .setParentId(PARENTID)
            .setExtras(EXTRAS);

    SearchTarget.Builder mBuilderSlice = new SearchTarget.Builder(RESULT_CORPUS, LAYOUT_TYPE, ID)
            .setPackageName(PACKAGE_NAME)
            .setUserHandle(UserHandle.CURRENT)
            .setSliceUri(Uri.EMPTY);

    @Test
    public void testBuilderSearchAction() {
        SearchTarget target = mBuilderAction.build();
        assertEverythingSearchAction(target);
        SearchTarget clone = cloneThroughParcel(target);
        assertEverythingSearchAction(clone);
    }

    private void assertEverythingSearchAction(@NonNull SearchTarget target) {
        assertThat(target.getResultType()).isEqualTo(RESULT_CORPUS);
        assertThat(target.getLayoutType()).isEqualTo(LAYOUT_TYPE);
        assertThat(target.getId()).isEqualTo(ID);
        assertThat(target.getUserHandle()).isEqualTo(UserHandle.CURRENT);
        assertThat(target.getScore()).isEqualTo(SCORE);
        assertThat(target.getParentId()).isEqualTo(PARENTID);
        assertThat(target.getSearchAction()).isEqualTo(SEARCH_ACTION);
        assertThat(target.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(target.getExtras().size()).isEqualTo(EXTRAS.size());
        assertThat(target.isHidden()).isEqualTo(SHOULD_HIDE);
    }

    @Test
    public void testBuilderSliceUri() {
        SearchTarget target = mBuilderSlice.build();
        assertThat(target.getSliceUri()).isEqualTo(Uri.EMPTY);
        SearchTarget clone = cloneThroughParcel(target);
        assertThat(clone.getSliceUri()).isEqualTo(Uri.EMPTY);
    }

    @Test
    public void testBuilderShortcutInfo() {
        ShortcutInfo shortcut = new ShortcutInfo.Builder(getContext(), ID2).build();
        SearchTarget target = new SearchTarget.Builder(RESULT_CORPUS, LAYOUT_TYPE, ID)
                .setPackageName(shortcut.getPackage())
                .setUserHandle(UserHandle.CURRENT)
                .setShortcutInfo(shortcut)
                .build();
        assertThat(target.getShortcutInfo()).isEqualTo(shortcut);
        SearchTarget clone = cloneThroughParcel(target);
        assertThat(clone.getShortcutInfo().getId()).isEqualTo(shortcut.getId());
    }

    @Test
    public void testBuilderAppWidget() {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(PACKAGE_NAME, "classname");
        SearchTarget target = new SearchTarget.Builder(RESULT_CORPUS, LAYOUT_TYPE, ID)
                .setPackageName(PACKAGE_NAME)
                .setUserHandle(UserHandle.CURRENT)
                .setAppWidgetProviderInfo(info)
                .build();
        assertThat(target.getAppWidgetProviderInfo()).isEqualTo(info);
    }

    private SearchTarget cloneThroughParcel(@NonNull SearchTarget target) {
        final Parcel parcel = Parcel.obtain();

        try {
            // Write to parcel
            parcel.setDataPosition(0);
            target.writeToParcel(parcel, 0);

            // Read from parcel
            parcel.setDataPosition(0);
            final SearchTarget clone = SearchTarget.CREATOR
                    .createFromParcel(parcel);
            assertThat(clone).isNotNull();
            return clone;
        } finally {
            parcel.recycle();
        }
    }
}
