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

package com.android.cts.appcloning.contacts;

import java.util.List;

public class ContactsShellCommandHelper {

    private static final String CONTACTS_AUTHORITY_ENDPOINT = "content://com.android.contacts/";
    private static final String CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";
    private static final String URI_TAG = "--uri";
    private static final String BIND_TAG = "--bind";
    private static final String USER_TAG = "--user";
    private static final String WHERE_CLAUSE_TAG = "--where";

    private enum ContentOperationType {
        INSERT("insert"),
        DELETE("delete"),
        QUERY("query"),
        UPDATE("update");

        public final String operation;
        ContentOperationType(String s) {
            this.operation = s;
        }
    }

    public static class ColumnBindings {
        public final String columnName;
        public final String columnValue;
        public final Type columnType;

        enum Type {
            STRING("s"),
            INT("i"),
            FLOAT("f"),
            LONG("l"),
            DOUBLE("d");

            public final String charValue;

            Type(String s) {
                this.charValue = s;
            }
        }

        public ColumnBindings(String name, String value, Type type) {
            this.columnType = type;
            this.columnName = name;
            this.columnValue = value;
        }
    }

    /**
     * Helper method that builds shell command for the content insert operation corresponding to the
     * input values
     * @param endpoint Contacts endpoint that the uri would access
     * @param bindings Content values and the column name to which they should be bound
     * @param user user identifier to redirect to the correct content provider
     * @return String corresponding to the content insert shell command
     */
    public static String getInsertTestContactCommand(String endpoint, List<ColumnBindings> bindings,
            String user) {
        return buildContentOperationCommand(ContentOperationType.INSERT.operation, endpoint,
                /* callerIsSyncAdapter */false, bindings, /* whereOperation */ null, user);
    }

    /**
     * Helper method that builds shell command for the content delete operation corresponding to the
     * input values
     * @param endpoint Contacts endpoint that the uri would access
     * @param whereClause String that specifies the where clause to identify the rows that the
     *                    query will be run on
     * @param user user identifier to redirect to the correct content provider
     * @return String corresponding to the content delete shell command
     */
    public static String getDeleteContactCommand(String endpoint, String whereClause, String user) {
        return buildContentOperationCommand(ContentOperationType.DELETE.operation, endpoint,
                /* callerIsSyncAdapter */ true, /* bindValues */ null, whereClause, user);
    }

    /**
     *
     * @param endpoint Contacts endpoint that the uri would access
     * @param whereClause String that specifies the where clause to identify the rows that the
     *                    query will be run on
     * @param user user identifier to redirect to the correct content provider
     * @return String corresponding to the content query shell command
     * @return
     */
    public static String getQueryTestContactsCommand(String endpoint, String whereClause,
            String user) {
        return buildContentOperationCommand(ContentOperationType.QUERY.operation, endpoint,
                /* callerIsSyncAdapter */ false, /* bindValues */ null, whereClause, user);
    }

    /**
     * A helper method that builds the shell command for a content operation
     * @param operationType content operation type - insert/delete/query/update
     * @param endpoint Contacts endpoint that the uri would access
     * @param callerIsSyncAdapter boolean value indicating if caller is a sync adapter
     * @param bindValues Content values and the column name to which they should be bound
     * @param whereOperations String that specifies the where clause to identify the rows that the
     *                        query will be run on
     * @param user user identifier to redirect to the correct content provider
     */
    private static String buildContentOperationCommand(String operationType,
            String endpoint, boolean callerIsSyncAdapter,
            List<ColumnBindings> bindValues, String whereOperations,
            String user) {
        if (operationType == null || endpoint == null) {
            return null;
        }

        String contentOperation = "content " + operationType;

        // Build the uri part of the command
        StringBuilder uriPart = new StringBuilder();
        uriPart.append(URI_TAG).append(" ")
                .append(CONTACTS_AUTHORITY_ENDPOINT)
                .append(endpoint);
        if (callerIsSyncAdapter) {
            uriPart.append("?")
                    .append(CALLER_IS_SYNCADAPTER)
                    .append("=true");
        }

        // Build the column to value bindings part of the command
        StringBuilder bindingsString = new StringBuilder();
        if (bindValues != null && !bindValues.isEmpty()) {
            for (ColumnBindings binding: bindValues) {
                bindingsString.append(BIND_TAG).append(" ")
                        .append(binding.columnName)
                        .append(":")
                        .append(binding.columnType.charValue)
                        .append(":")
                        .append(binding.columnValue)
                        .append(" ");
            }
        }

        // Build the where clause of the command
        StringBuilder where = new StringBuilder();
        if (whereOperations != null) {
            where.append(WHERE_CLAUSE_TAG).append(" ")
                    .append(whereOperations);
        }

        // Build user part of the command if user id is provided
        StringBuilder userPart = new StringBuilder();
        if (user != null) {
            userPart.append(USER_TAG).append(" ")
                    .append(user);
        }
        return contentOperation + " " + uriPart + " " + bindingsString + " " + where + " "
                + userPart;
    }
}
