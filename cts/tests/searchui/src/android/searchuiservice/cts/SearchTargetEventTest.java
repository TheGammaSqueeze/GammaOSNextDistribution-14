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

package android.searchuiservice.cts;

import static android.app.search.SearchTargetEvent.ACTION_TAP;
import static android.app.search.SearchTargetEvent.Builder;
import static android.app.search.SearchTargetEvent.FLAG_IME_SHOWN;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;

import android.app.search.SearchAction;
import android.app.search.SearchTargetEvent;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;

@RunWith(JUnit4.class)
public class SearchTargetEventTest {
    private final String ID = "id";
    private final String LOCATION = "location";
    private final Builder mBuilder = new Builder(ID, ACTION_TAP).setLaunchLocation(LOCATION);
    private final Builder mBuilderList = new Builder(new ArrayList(), ACTION_TAP);

    @Test
    public void testBuilder() {
        SearchTargetEvent event = mBuilder
                .setLaunchLocation(LOCATION).setFlags(FLAG_IME_SHOWN).build();
        assertEverything(event);
        mBuilderList.build();
    }

    @Test
    public void testParcel() {
        SearchTargetEvent event = mBuilder
                .setLaunchLocation(LOCATION).setFlags(FLAG_IME_SHOWN).build();
        assertEverything(event);
        final SearchTargetEvent clone = cloneThroughParcel(event);
        assertEverything(clone);

    }

    private void assertEverything(@NonNull SearchTargetEvent event) {
        assertThat(event.getTargetId()).isEqualTo(ID);
        assertThat(event.getAction()).isEqualTo(ACTION_TAP);
        assertThat(event.getFlags()).isEqualTo(FLAG_IME_SHOWN);
        assertThat(event.getLaunchLocation()).isEqualTo(LOCATION);
    }

    private SearchTargetEvent cloneThroughParcel(@NonNull SearchTargetEvent event) {
        final Parcel parcel = Parcel.obtain();

        try {
            // Write to parcel
            parcel.setDataPosition(0);
            event.writeToParcel(parcel, 0);

            // Read from parcel
            parcel.setDataPosition(0);
            final SearchTargetEvent clone = SearchTargetEvent.CREATOR
                    .createFromParcel(parcel);
            assertThat(clone).isNotNull();
            return clone;
        } finally {
            parcel.recycle();
        }
    }
}
