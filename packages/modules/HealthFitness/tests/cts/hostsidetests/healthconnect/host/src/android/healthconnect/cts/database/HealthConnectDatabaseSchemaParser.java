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

package android.healthconnect.cts.database;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HealthConnectDatabaseSchemaParser takes the sql file as input in string format and stores all
 * information about tables,corresponding columns,foreign keys and indexes of the HealthConnect
 * database.
 */
class HealthConnectDatabaseSchemaParser {

    /**
     * It matches one word(table name) followed by opening parenthesis followed by anything(table
     * definitions including column definitions and foreign key definitions) followed by closing
     * parenthesis(end of table definition).
     */
    public static final String CREATE_TABLE_REGEX = "(\\w+)\\s*\\((.*)\\)";

    public static final String CHECK_CONSTRAINT_REGEX = "(?i)CHECK\\s*\\(([^)]+)\\)";
    public static final String DEFAULT_VALUE_REGEX = "(?i)DEFAULT\\s+(\\S+)";

    public static final String COMPOSITE_PRIMARY_KEY_REGEX =
            "(?i)PRIMARY (?i)KEY\\s*\\(\\s*([^\\)]+)\\)";

    /**
     * It matches FOREIGN KEY followed by opening parenthesis followed by word (foreign key name)
     * followed by REFERENCES followed by word(table name) followed by column names of that table
     * and then finally flags which are optional.
     */
    public static final String FOREIGN_KEY_REGEX =
            "(?i)FOREIGN (?i)KEY\\s*\\(\\s*([^\\)]+)\\)(?:\\s*(?i)REFERENCES\\s*(\\w+)\\s*\\"
                    + "(\\s*([^\\)]+)\\))?((?: \\w+)*)?";

    /**
     * It matches CREATE UNIQUE(optionally) INDEX followed by word (index name) ON followed by
     * word(table name) followed by column bounded by opening and closing parenthesis.
     */
    public static final String INDEX_REGEX =
            "(?i)CREATE\\s+(?:\\s*UNIQUE\\s+)?(?i)INDEX\\s+(\\w+)\\s+(?i)ON\\s+(\\w+)\\s*\\((.*)"
                    + "\\)";

    public static final String WHITE_SPACE_REGEX = "\\s+";
    public static final String CREATE_REGEX = "(?i)\\bcreate\\b";
    public static final String INDEX_KEY_REGEX = "(?i)\\bINDEX\\b";
    public static final String FOREIGN_REGEX = "(?i)\\bFOREIGN\\b";
    public static final String KEY_REGEX = "(?i)\\bKEY\\b";
    public static final String PRIMARY_REGEX = "(?i)\\bPRIMARY\\b";
    public static final String NOT_NULL_REGEX = "(?i)\\bNOT NULL\\b";
    public static final String AUTO_INCREMENT_REGEX = "(?i)\\bAUTOINCREMENT\\b";
    public static final String UNIQUE_REGEX = "(?i)\\bUNIQUE\\b";
    public static final String CHECK_REGEX = "(?i)\\bCHECK\\b";

    /** Stores all the tables . */
    static void getTableInfo(@NonNull String inputSchema, HashMap<String, TableInfo> mTableMap) {
        Objects.requireNonNull(inputSchema);
        String sqlStatement = inputSchema;
        /**
         * sqlStatement string has been processed so that it can work correctly with our regex . For
         * that we have replaced more than one whitespace with one whitespace and broken the line so
         * that string after 'CREATE' can start from a new line.
         */
        sqlStatement = sqlStatement.replaceAll(WHITE_SPACE_REGEX, " ");
        sqlStatement = sqlStatement.replaceAll(CREATE_REGEX, "\nCREATE");
        String[] createTableStatements = sqlStatement.split("(?i)CREATE");
        for (String statement : createTableStatements) {

            if (statement.contains("INDEX")) continue;
            statement = statement.replaceAll("\\bTABLE\\b", "");
            String trimmedStatement = statement.trim();

            if (!trimmedStatement.isEmpty()) {

                Pattern tablePattern = Pattern.compile(CREATE_TABLE_REGEX);
                Matcher tableMatcher = tablePattern.matcher(trimmedStatement);

                if (tableMatcher.find()) {
                    String tableName = tableMatcher.group(1);
                    TableInfo.Builder tableInfo = new TableInfo.Builder(tableName);
                    String tableDefinition = tableMatcher.group(2);
                    String[] columns = tableDefinition.split(",\\s*");
                    for (String column : columns) {
                        /**
                         * Since we are splitting the column definitions from each other with the
                         * help of comma, we will need to take care of the case when composite
                         * foreign key is created as the columns will also be separated with
                         * comma.So in order to identify only the valid column definitions ,we are
                         * adding a small check to ensure that we consider only that part in which
                         * either both opening and closing parenthesis are present or none of them
                         * are present. e.g.: Suppose we have a composite foreign key as FOREIGN
                         * KEY(col1,col2,col3) REFERENCES table(pk1,ok2,pk3) So in this case, when
                         * we will split with comma ,the parts will be: FOREIGN KEY (col1 {ignored
                         * as we have only opening parenthesis} col2 col3 ) REFERENCES table(pk1
                         * {ignored as we have closing parenthesis before opening}
                         */
                        int first = column.indexOf('('), second = column.indexOf(')');
                        if ((first == -1 && second != -1)
                                || (first != -1 && second == -1)
                                || (first > second)) {
                            continue;
                        } else {
                            String[] parts = column.trim().split("\\s+");

                            if (parts.length > 0
                                    && ((parts[0].equals("FOREIGN"))
                                            || parts[0].equals("PRIMARY"))) {
                                continue;
                            } else if (parts.length >= 2) {
                                String columnName = parts[0];
                                String dataType = parts[1];
                                String defaultValue = null;
                                Pattern defaultPattern = Pattern.compile(DEFAULT_VALUE_REGEX);
                                Matcher defaultMatcher = defaultPattern.matcher(column);
                                if (defaultMatcher.find()) {
                                    defaultValue = defaultMatcher.group(1);
                                }
                                ColumnInfo.Builder col =
                                        new ColumnInfo.Builder(columnName, dataType);
                                col.setDefaultValue(defaultValue);

                                boolean isUnique = column.contains("UNIQUE");
                                if (isUnique) {
                                    col.addConstraint(ColumnInfo.UNIQUE_CONSTRAINT);
                                }

                                boolean isNotNull = column.contains("NOT NULL");
                                if (isNotNull) {
                                    col.addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT);
                                }

                                boolean hasAutoIncrement = column.contains("AUTOINCREMENT");
                                if (hasAutoIncrement) {
                                    col.addConstraint(ColumnInfo.AUTO_INCREMENT_CONSTRAINT);
                                }
                                if (column.contains("PRIMARY KEY")) {
                                    tableInfo.addPrimaryKeyColumn(columnName);
                                }
                                if (column.contains("CHECK")) {
                                    Pattern checkPattern = Pattern.compile(CHECK_CONSTRAINT_REGEX);
                                    Matcher checkMatcher = checkPattern.matcher(column);
                                    if (checkMatcher.find()) {
                                        String checkConstraint = checkMatcher.group(1);
                                        col.addCheckConstraint(checkConstraint);
                                    }
                                }
                                tableInfo.addColumnInfoMapping(columnName, col.build());
                            }
                        }
                    }
                    /** Regular expression for composite Primary Key */
                    Pattern primaryKeyPattern = Pattern.compile(COMPOSITE_PRIMARY_KEY_REGEX);
                    Matcher primaryKeyMatcher = primaryKeyPattern.matcher(trimmedStatement);
                    while (primaryKeyMatcher.find()) {
                        String pkColumns = primaryKeyMatcher.group(1);
                        String[] pkColumnList = pkColumns.split(",");
                        for (String primaryKeyColumn : pkColumnList) {
                            tableInfo.addPrimaryKeyColumn(primaryKeyColumn);
                        }
                    }

                    Pattern foreignKeyPattern = Pattern.compile(FOREIGN_KEY_REGEX);
                    Matcher foreignKeyMatcher = foreignKeyPattern.matcher(trimmedStatement);
                    while (foreignKeyMatcher.find()) {
                        String columnNames = foreignKeyMatcher.group(1);
                        String referencedTable = foreignKeyMatcher.group(2);
                        String referencedPrimaryKey = foreignKeyMatcher.group(3);
                        String sqlForeignKeyStatement = foreignKeyMatcher.group();
                        String[] foreignColumn = columnNames.split(",");

                        if (referencedTable != null) {
                            String[] referencedPk = referencedPrimaryKey.split(",");
                            for (int i = 0; i < foreignColumn.length; i++) {
                                addForeignKeyInfo(
                                        tableInfo,
                                        foreignColumn[i],
                                        referencedTable,
                                        referencedPk[i],
                                        sqlForeignKeyStatement);
                            }
                        } else {
                            for (String foreignKey : foreignColumn) {
                                addForeignKeyInfo(tableInfo, foreignKey, null, null, null);
                            }
                        }
                    }
                    mTableMap.put(tableName, tableInfo.build());
                }
            }
        }
    }

    /** Stores all the corresponding indexes of a table. */
    static void getIndexInfo(@NonNull String inputSchema, HashMap<String, TableInfo> mTableMap) {
        String sqlStatement = inputSchema;
        sqlStatement = sqlStatement.replaceAll(WHITE_SPACE_REGEX, " ");
        sqlStatement = sqlStatement.replaceAll(CREATE_REGEX, "\nCREATE");

        Pattern indexPattern = Pattern.compile(INDEX_REGEX);
        Matcher indexMatcher = indexPattern.matcher(sqlStatement);

        while (indexMatcher.find()) {
            boolean uniqueFlag = (indexMatcher.group().contains("UNIQUE"));
            String indexName = indexMatcher.group(1);
            String tableName = indexMatcher.group(2);
            String columnList = indexMatcher.group(3);
            IndexInfo.Builder indexInfo = new IndexInfo.Builder(indexName, tableName);
            indexInfo.setUniqueFlag(uniqueFlag);
            String[] columns = columnList.split("\\s*,\\s*");

            for (String column : columns) {
                indexInfo.addIndexCols(column);
            }
            TableInfo table1 = mTableMap.get(tableName);
            table1.getIndexInfoMapping().put(indexName, indexInfo.build());
        }
    }

    /** Populate the HashMap of HealthConnect database Schema. */
    public static HashMap<String, TableInfo> getSchemaMap(@NonNull String inputSchema) {
        Objects.requireNonNull(inputSchema);
        HashMap<String, TableInfo> mTableMap = new HashMap<>();
        inputSchema = convertToUpperCase(inputSchema);
        getTableInfo(inputSchema, mTableMap);
        getIndexInfo(inputSchema, mTableMap);
        return mTableMap;
    }

    /**
     * Checks the flags that are being used in the table while creating the foreign key and adds
     * those flags to the flag list of the ForeignKeyInfo.
     */
    static void setForeignKeyFlags(
            String sqlForeignKeyStatement, ForeignKeyInfo.Builder foreignKeyInfo) {
        if (sqlForeignKeyStatement == null) {
            return;
        }
        sqlForeignKeyStatement = sqlForeignKeyStatement.toUpperCase();

        if (sqlForeignKeyStatement.contains("ON DELETE CASCADE")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_DELETE_CASCADE);
        }
        if (sqlForeignKeyStatement.contains("ON DELETE SET NULL")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_DELETE_SET_NULL);
        }
        if (sqlForeignKeyStatement.contains("ON DELETE SET DEFAULT")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_DELETE_SET_DEFAULT);
        }
        if (sqlForeignKeyStatement.contains("ON DELETE RESTRICT")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_DELETE_RESTRICT);
        }
        if (sqlForeignKeyStatement.contains("ON UPDATE CASCADE")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_UPDATE_CASCADE);
        }
        if (sqlForeignKeyStatement.contains("ON UPDATE SET NULL")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_UPDATE_SET_NULL);
        }
        if (sqlForeignKeyStatement.contains("ON UPDATE SET DEFAULT")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_UPDATE_SET_DEFAULT);
        }
        if (sqlForeignKeyStatement.contains("ON UPDATE RESTRICT")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.ON_UPDATE_RESTRICT);
        }
        if (sqlForeignKeyStatement.contains("DEFERRABLE")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.DEFERRABLE_FLAG);
        }
        if (sqlForeignKeyStatement.contains("INITIALLY DEFERRED")) {
            foreignKeyInfo.addFlag(ForeignKeyInfo.INITIALLY_DEFERRED);
        }
    }

    public static String convertToUpperCase(String inputSchema) {

        inputSchema = inputSchema.replaceAll(INDEX_KEY_REGEX, "INDEX");
        inputSchema = inputSchema.replaceAll(FOREIGN_REGEX, "FOREIGN");
        inputSchema = inputSchema.replaceAll(KEY_REGEX, "KEY");
        inputSchema = inputSchema.replaceAll(PRIMARY_REGEX, "PRIMARY");
        inputSchema = inputSchema.replaceAll(UNIQUE_REGEX, "UNIQUE");
        inputSchema = inputSchema.replaceAll(NOT_NULL_REGEX, "NOT NULL");
        inputSchema = inputSchema.replaceAll(AUTO_INCREMENT_REGEX, "AUTOINCREMENT");
        inputSchema = inputSchema.replaceAll(CHECK_REGEX, "CHECK");
        return inputSchema;
    }

    public static void addForeignKeyInfo(
            TableInfo.Builder tableInfo,
            String foreignKeyColumn,
            String referencedTable,
            String referencedPrimaryKey,
            String sqlForeignKeyStatement) {
        ForeignKeyInfo.Builder foreignKeyInfo =
                new ForeignKeyInfo.Builder(foreignKeyColumn, referencedTable, referencedPrimaryKey);
        if (sqlForeignKeyStatement != null) {
            setForeignKeyFlags(sqlForeignKeyStatement, foreignKeyInfo);
        }

        tableInfo.addForeignKeyInfoMapping(foreignKeyColumn, foreignKeyInfo.build());
    }
}
