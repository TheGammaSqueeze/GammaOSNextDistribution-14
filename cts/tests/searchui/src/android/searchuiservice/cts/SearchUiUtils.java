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

import static androidx.test.InstrumentationRegistry.getContext;

import android.app.search.Query;
import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.List;

public class SearchUiUtils {
    static final String LAYOUT_TYPE_HERO = "hero";

    static final int RESULT_CORPUS1 = 1 << 0;
    static final int RESULT_CORPUS2 = 1 << 1;
    static final int RESULT_CORPUS3 = 1 << 2;

    static final String QUERY_INPUT = "b";
    static final int QUERY_TIMESTAMP = 314;

    /**
     * Generate total {@param num} of {@link SearchTarget}s.
     */
    static List<SearchTarget> generateSearchTargetList(int num) {
        return generateSearchTargetList(num, false, false, false, false);
    }

    /**
     * Generate total {@param num} of {@link SearchTarget}s.
     */
    static List<SearchTarget> generateSearchTargetList(int num,
            boolean includeSearchAction,
            boolean includeShortcutInfo,
            boolean includeAppWidgetProviderInfo,
            boolean includeSliceUri) {
        List<SearchTarget> targets = new ArrayList<>();
        for (int seed = 0; seed < num; seed++) {
            targets.add(generateSearchTarget(seed,
                    includeSearchAction,
                    includeShortcutInfo,
                    includeAppWidgetProviderInfo,
                    includeSliceUri));
        }
        return targets;
    }

    /**
     * Generate sample search target using the {@param seed}.
     */
    static SearchTarget generateSearchTarget(int seed,
            boolean includeSearchAction,
            boolean includeShortcutInfo,
            boolean includeAppWidgetProviderInfo,
            boolean includeSliceUri) {

        SearchTarget.Builder builder = new SearchTarget.Builder(RESULT_CORPUS1, LAYOUT_TYPE_HERO, String.valueOf(seed))
                .setPackageName("package name")
                .setUserHandle(UserHandle.CURRENT);

        if (includeSearchAction) {
            builder.setSearchAction(new SearchAction.Builder("id" + seed, "title" + seed)
                    .setIntent(new Intent())
                    .build());
        }

        if (includeShortcutInfo) {
            builder.setShortcutInfo(new ShortcutInfo.Builder(getContext(), "id" + seed)
                    .build());
        }

        if (includeAppWidgetProviderInfo) {
            builder.setAppWidgetProviderInfo(new AppWidgetProviderInfo());
        }

        if (includeSliceUri) {
            builder.setSliceUri(new Uri.Builder().build());
        }

        return builder.build();
    }

    static Query generateQuery(Bundle extras) {
        return new Query(QUERY_INPUT, QUERY_TIMESTAMP, extras);
    }
}
