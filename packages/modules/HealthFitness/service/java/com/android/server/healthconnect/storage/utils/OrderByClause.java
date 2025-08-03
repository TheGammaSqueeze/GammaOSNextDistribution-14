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

package com.android.server.healthconnect.storage.utils;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/** @hide */
public final class OrderByClause {
    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    List<Pair<String, Boolean>> mOrderList;

    /**
     * Adds Order By condition for the read query.
     *
     * @param columnName the column name on which sorting to be done
     * @param isAscending to specify the sorting order
     */
    public OrderByClause addOrderByClause(String columnName, boolean isAscending) {
        if (mOrderList == null) {
            mOrderList = new ArrayList<>();
        }
        mOrderList.add(new Pair<>(columnName, isAscending));
        return this;
    }

    /**
     * Returns the Order By clause for the read query
     *
     * @return ordery by clause containing all the order by column conditions in order
     */
    public String getOrderBy() {
        if (mOrderList == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(" ORDER BY ");
        String prefix = "";
        for (Pair<String, Boolean> column : mOrderList) {
            builder.append(prefix);
            prefix = " , ";
            builder.append(column.first);
            if (!column.second) {
                builder.append(" DESC ");
            }
        }
        return builder.toString();
    }
}
