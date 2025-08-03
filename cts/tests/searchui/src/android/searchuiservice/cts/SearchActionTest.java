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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import static androidx.test.InstrumentationRegistry.getContext;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.search.SearchAction;
import android.app.search.SearchAction.Builder;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SearchAction}
 *
 * atest CtsSearchUiServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SearchActionTest {
    private static final String ID = "ID";
    private static final String TITLE = "TITLE";
    private static final String SUBTITLE = "SUBTITLE";
    private static final String CONTENTDESCRIPTION = "CONTENTDESCRIPTION";
    private static final Intent INTENT = new Intent();

    private final SearchAction.Builder mBuilder = new SearchAction.Builder(ID, TITLE);

    private final Bundle mExtras = new Bundle();

    @Before
    public void setIntentExtras() {
        mExtras.putString("SEARCH", "AWESOME");
        mBuilder.setExtras(mExtras);
    }

    @Test
    public void testBuilder_invalidId() {
        assertThrows(NullPointerException.class, () -> new Builder (null, TITLE));
    }

    @Test
    public void testBuilder_invalidTitle() {
        assertThrows(NullPointerException.class, () -> new Builder (ID, null));
    }

    @Test
    public void testBuilder_zeroIntent() {
        assertThrows(IllegalStateException.class, () -> new Builder(ID, TITLE).build());
    }

    @Test
    public void testParcel_nullIcon() {
        final SearchAction originalSearchAction = mBuilder
                .setUserHandle(UserHandle.CURRENT)
                .setSubtitle(SUBTITLE)
                .setIntent(INTENT)
                .setContentDescription(CONTENTDESCRIPTION)
                .build();
        assertEverything(originalSearchAction);
        final SearchAction clone = cloneThroughParcel(originalSearchAction);
        assertEverything(clone);
    }

    @Test
    public void testParcel_bitmapIcon() {
        final SearchAction originalSearchAction = mBuilder
                .setIcon(Icon.createWithBitmap(
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)))
                .setSubtitle(SUBTITLE)
                .setUserHandle(UserHandle.CURRENT)
                .setIntent(INTENT)
                .setContentDescription(CONTENTDESCRIPTION)
                .build();
        assertEverything(originalSearchAction);
        assertNotNull(originalSearchAction.getIcon());
        final SearchAction clone = cloneThroughParcel(originalSearchAction);
        assertEverything(clone);
    }

    @Test
    public void testParcel_filePathIcon() {
        final SearchAction originalSearchAction = mBuilder
                .setIcon(Icon.createWithFilePath("file path"))
                .setSubtitle(SUBTITLE)
                .setUserHandle(UserHandle.CURRENT)
                .setIntent(INTENT)
                .setContentDescription(CONTENTDESCRIPTION)
                .build();
        assertEverything(originalSearchAction);
        final SearchAction clone = cloneThroughParcel(originalSearchAction);
        assertEverything(clone);
    }

    private void assertEverything(@NonNull SearchAction searchAction) {
        assertThat(searchAction).isNotNull();
        assertThat(searchAction.getId()).isEqualTo(ID);
        assertThat(searchAction.getUserHandle()).isEqualTo(UserHandle.CURRENT);
        assertThat(searchAction.getTitle()).isEqualTo(TITLE);
        assertThat(searchAction.getContentDescription()).isEqualTo(CONTENTDESCRIPTION);
        assertThat(searchAction.getIntent().toUri(0)).isEqualTo(INTENT.toUri(0));
        assertThat(searchAction.getSubtitle()).isEqualTo(SUBTITLE);
        assertExtras(searchAction.getExtras());
    }


    @Test
    public void test_twoIntents() {
        mBuilder.setIntent(INTENT);
        PendingIntent dummyPendingIntent = TaskStackBuilder.create(getContext())
                .addNextIntent(INTENT)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setPendingIntent(dummyPendingIntent);
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }

    @Test
    public void test_PendingIntent() {
        PendingIntent dummyPendingIntent = TaskStackBuilder.create(getContext())
                .addNextIntent(INTENT)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setPendingIntent(dummyPendingIntent);
        SearchAction searchAction = mBuilder.build();
        assertNotNull(searchAction.getPendingIntent());
    }

    private void assertExtras(@NonNull Bundle bundle) {
        assertThat(bundle).isNotNull();
        assertThat(bundle.keySet()).hasSize(1);
        assertThat(bundle.getString("SEARCH")).isEqualTo("AWESOME");
    }

    private SearchAction cloneThroughParcel(@NonNull SearchAction searchAction) {
        final Parcel parcel = Parcel.obtain();

        try {
            // Write to parcel
            parcel.setDataPosition(0);
            searchAction.writeToParcel(parcel, 0);

            // Read from parcel
            parcel.setDataPosition(0);
            final SearchAction clone = SearchAction.CREATOR
                    .createFromParcel(parcel);
            assertThat(clone).isNotNull();
            return clone;
        } finally {
            parcel.recycle();
        }
    }
}
