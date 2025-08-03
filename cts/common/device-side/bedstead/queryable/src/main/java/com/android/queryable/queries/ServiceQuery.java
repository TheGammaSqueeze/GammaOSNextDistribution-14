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

import android.app.Service;
import android.content.IntentFilter;

import com.android.queryable.Queryable;
import com.android.queryable.info.ServiceInfo;

/**
 * Query for an {@link Service}.
 *
 * @param <E> Type of query.
 */
public interface ServiceQuery<E extends Queryable> extends Query<ServiceInfo>  {

    /** Queries a {@link Service}. */
    static ServiceQueryHelper.ServiceQueryBase service() {
        return new ServiceQueryHelper.ServiceQueryBase();
    }

    /** Used to query the class name of a service. */
    ClassQuery<E> serviceClass();

    /** Query the intent-filters on an activity. */
    SetQuery<E, IntentFilter> intentFilters();
}
