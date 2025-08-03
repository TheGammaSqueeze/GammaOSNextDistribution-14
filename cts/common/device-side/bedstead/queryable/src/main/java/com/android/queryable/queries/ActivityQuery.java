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

package com.android.queryable.queries;

import android.app.Activity;
import android.content.IntentFilter;

import com.android.queryable.Queryable;
import com.android.queryable.info.ActivityInfo;

/** Query for an {@link Activity}. */
public interface ActivityQuery<E extends Queryable> extends Query<ActivityInfo>  {

    /** Queries a {@link Activity}*/
    static ActivityQueryHelper.ActivityQueryBase activity() {
        return new ActivityQueryHelper.ActivityQueryBase();
    }

    ClassQuery<E> activityClass();
    BooleanQuery<E> exported();

    /** Query the permission to launch an activity. */
    StringQuery<E> permission();

    /** Query the intent-filters on an activity. */
    SetQuery<E, IntentFilter> intentFilters();
}
