/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.server.healthconnect.storage.utils.StorageUtils.SELECT_ALL;

import android.annotation.NonNull;
import android.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents SQL join. Default join type is INNER join.
 *
 * @hide
 */
public final class SqlJoin {
    public static final String SQL_JOIN_INNER = "INNER";
    public static final String SQL_JOIN_LEFT = "LEFT";

    /** @hide */
    @StringDef(
            value = {
                SQL_JOIN_INNER,
                SQL_JOIN_LEFT,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JoinType {}

    private final String mSelfTableName;
    private final String mTableNameToJoinOn;
    private final String mSelfColumnNameToMatch;
    private final String mJoiningColumnNameToMatch;

    private List<SqlJoin> mAttachedJoins;
    private String mJoinType = SQL_JOIN_INNER;

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private WhereClauses mTableToJoinWhereClause = null;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public SqlJoin(
            String selfTableName,
            String tableNameToJoinOn,
            String selfColumnNameToMatch,
            String joiningColumnNameToMatch) {
        mSelfTableName = selfTableName;
        mTableNameToJoinOn = tableNameToJoinOn;
        mSelfColumnNameToMatch = selfColumnNameToMatch;
        mJoiningColumnNameToMatch = joiningColumnNameToMatch;
    }

    /**
     * Sets join type to the current joint, default value is inner join. Returns class with join
     * type set.
     */
    public SqlJoin setJoinType(@NonNull @JoinType String joinType) {
        Objects.requireNonNull(joinType);
        mJoinType = joinType;
        return this;
    }

    /**
     * Returns query by applying JOIN condition on the innerQuery
     *
     * @param innerQuery An inner query to be used for the JOIN
     * @return Final query with JOIN condition
     */
    public String getJoinWithQueryCommand(String innerQuery) {
        if (innerQuery == null) {
            throw new IllegalArgumentException("Inner query cannot be null");
        }
        return SELECT_ALL
                + "( "
                + innerQuery
                + " ) "
                + getJoinCommand(/* withSelfTableNamePrefix= */ false);
    }

    /** Returns join command. */
    public String getJoinCommand() {
        return getJoinCommand(/* withSelfTableNamePrefix= */ true);
    }

    /** Attaches another join to this join. Returns this class with another join attached. */
    public SqlJoin attachJoin(@NonNull SqlJoin join) {
        Objects.requireNonNull(join);
        if (!Objects.equals(mSelfTableName, join.mSelfTableName)) {
            throw new IllegalArgumentException(
                    "Sequenced joins must have the same self table name");
        }

        if (mAttachedJoins == null) {
            mAttachedJoins = new ArrayList<>();
        }

        mAttachedJoins.add(join);
        return this;
    }

    public void setSecondTableWhereClause(WhereClauses whereClause) {
        mTableToJoinWhereClause = whereClause;
    }

    private String getJoinCommand(boolean withSelfTableNamePrefix) {
        String selfColumnPrefix = withSelfTableNamePrefix ? mSelfTableName + "." : "";
        return " "
                + mJoinType
                + " JOIN "
                + (mTableToJoinWhereClause == null ? "" : "( " + buildFilterQuery() + ") ")
                + mTableNameToJoinOn
                + " ON "
                + selfColumnPrefix
                + mSelfColumnNameToMatch
                + " = "
                + mTableNameToJoinOn
                + "."
                + mJoiningColumnNameToMatch
                + buildAttachedJoinsCommand(withSelfTableNamePrefix);
    }

    private String buildFilterQuery() {
        return SELECT_ALL + mTableNameToJoinOn + mTableToJoinWhereClause.get(true);
    }

    private String buildAttachedJoinsCommand(boolean withSelfTableNamePrefix) {
        if (mAttachedJoins == null) {
            return "";
        }

        StringBuilder command = new StringBuilder();
        for (SqlJoin join : mAttachedJoins) {
            command.append(" ").append(join.getJoinCommand(withSelfTableNamePrefix));
        }

        return command.toString();
    }
}
