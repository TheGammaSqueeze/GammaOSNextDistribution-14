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

import com.android.server.healthconnect.storage.request.ReadTableRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** @hide */
public final class WhereClauses {
    public enum LogicalOperator {
        AND(" AND "),
        OR(" OR ");

        private final String opKeyword;

        LogicalOperator(String opKeyword) {
            this.opKeyword = opKeyword;
        }
    }

    private final List<String> mClauses = new ArrayList<>();
    private final LogicalOperator mLogicalOperator;

    public WhereClauses(LogicalOperator logicalOperator) {
        mLogicalOperator = logicalOperator;
    }

    public WhereClauses addWhereBetweenClause(String columnName, long start, long end) {
        mClauses.add(columnName + " BETWEEN " + start + " AND " + end);

        return this;
    }

    public WhereClauses addWhereBetweenTimeClause(String columnName, long startTime, long endTime) {
        if (endTime < 0 || endTime < startTime) {
            // Below method has check for startTime less than 0.
            // If both startTime and endTime are less than 0 then no new time clause will be added
            // and just return current object.
            return addWhereLaterThanTimeClause(columnName, startTime);
        }

        mClauses.add(columnName + " BETWEEN " + startTime + " AND " + endTime);

        return this;
    }

    public WhereClauses addWhereLaterThanTimeClause(String columnName, long startTime) {
        if (startTime < 0) {
            return this;
        }

        mClauses.add(columnName + " > " + startTime);

        return this;
    }

    public WhereClauses addWhereInClause(String columnName, List<String> values) {
        if (values == null || values.isEmpty()) return this;

        mClauses.add(columnName + " IN " + "('" + String.join("', '", values) + "')");

        return this;
    }

    public WhereClauses addWhereInClauseWithoutQuotes(String columnName, List<String> values) {
        if (values == null || values.isEmpty()) return this;

        mClauses.add(columnName + " IN " + "(" + String.join(", ", values) + ")");

        return this;
    }

    public WhereClauses addWhereEqualsClause(String columnName, String value) {
        if (columnName == null || value == null || value.isEmpty() || columnName.isEmpty()) {
            return this;
        }

        mClauses.add(columnName + " = " + StorageUtils.getNormalisedString(value));
        return this;
    }

    public WhereClauses addWhereGreaterThanClause(String columnName, String value) {
        mClauses.add(columnName + " > '" + value + "'");

        return this;
    }

    /** Add clause columnName > value */
    public WhereClauses addWhereGreaterThanClause(String columnName, long value) {
        mClauses.add(columnName + " > " + value);

        return this;
    }

    public WhereClauses addWhereGreaterThanOrEqualClause(String columnName, long value) {
        mClauses.add(columnName + " >= " + value);

        return this;
    }

    public WhereClauses addWhereLessThanOrEqualClause(String columnName, long value) {
        mClauses.add(columnName + " <= " + value);

        return this;
    }

    /** Add clause columnName < value */
    public WhereClauses addWhereLessThanClause(String columnName, long value) {
        mClauses.add(columnName + " < " + value);

        return this;
    }

    public WhereClauses addWhereInIntsClause(String columnName, List<Integer> values) {
        if (values == null || values.isEmpty()) return this;

        mClauses.add(
                columnName
                        + " IN ("
                        + values.stream().map(String::valueOf).collect(Collectors.joining(", "))
                        + ")");

        return this;
    }

    /**
     * Adds where in condition for the column.
     *
     * @param columnName Column name on which where condition to be applied
     * @param values to check in the where condition
     */
    public WhereClauses addWhereInLongsClause(String columnName, Collection<Long> values) {
        if (values == null || values.isEmpty()) return this;

        mClauses.add(
                columnName
                        + " IN ("
                        + values.stream()
                                .distinct()
                                .map(String::valueOf)
                                .collect(Collectors.joining(", "))
                        + ")");

        return this;
    }

    /**
     * Creates IN clause, where in range is another SQL request. Returns instance with extra clauses
     * set.
     */
    public WhereClauses addWhereInSQLRequestClause(String columnName, ReadTableRequest inRequest) {
        mClauses.add(columnName + " IN (" + inRequest.getReadCommand() + ") ");

        return this;
    }

    /** Adds other {@link WhereClauses} as conditions of this where clause. */
    public WhereClauses addNestedWhereClauses(WhereClauses... otherWhereClauses) {
        for (WhereClauses whereClauses : otherWhereClauses) {
            if (whereClauses.mClauses.isEmpty()) {
                // skip empty WhereClauses so we don't add empty pairs of parentheses "()" to the
                // final SQL statement
                continue;
            }
            mClauses.add("(" + whereClauses.get(/* withWhereKeyword= */ false) + ")");
        }

        return this;
    }

    /**
     * Returns where clauses joined by 'AND', if the input parameter isIncludeWHEREinClauses is true
     * then the clauses are preceded by 'WHERE'.
     */
    public String get(boolean withWhereKeyword) {
        if (mClauses.isEmpty()) {
            return "";
        }

        return (withWhereKeyword ? " WHERE " : "")
                + String.join(mLogicalOperator.opKeyword, mClauses);
    }
}
