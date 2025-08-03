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

#define STATSD_DEBUG false  // STOPSHIP if true

#include "Log.h"

#include "utils/DbUtils.h"

#include <android/api-level.h>

#include "FieldValue.h"
#include "android-base/properties.h"
#include "android-base/stringprintf.h"
#include "stats_log_util.h"
#include "storage/StorageManager.h"

namespace android {
namespace os {
namespace statsd {
namespace dbutils {

using ::android::os::statsd::FLOAT;
using ::android::os::statsd::INT;
using ::android::os::statsd::LONG;
using ::android::os::statsd::StorageManager;
using ::android::os::statsd::STRING;
using base::GetProperty;
using base::StringPrintf;

const string TABLE_NAME_PREFIX = "metric_";
const string COLUMN_NAME_ATOM_TAG = "atomId";
const string COLUMN_NAME_EVENT_ELAPSED_CLOCK_NS = "elapsedTimestampNs";
const string COLUMN_NAME_EVENT_WALL_CLOCK_NS = "wallTimestampNs";

const string COLUMN_NAME_SDK_VERSION = "sdkVersion";
const string COLUMN_NAME_MODEL = "model";
const string COLUMN_NAME_PRODUCT = "product";
const string COLUMN_NAME_HARDWARE = "hardware";
const string COLUMN_NAME_DEVICE = "device";
const string COLUMN_NAME_BUILD = "osBuild";
const string COLUMN_NAME_FINGERPRINT = "fingerprint";
const string COLUMN_NAME_BRAND = "brand";
const string COLUMN_NAME_MANUFACTURER = "manufacturer";
const string COLUMN_NAME_BOARD = "board";

static std::vector<std::string> getExpectedTableSchema(const LogEvent& logEvent) {
    vector<std::string> result;
    for (const FieldValue& fieldValue : logEvent.getValues()) {
        if (fieldValue.mField.getDepth() > 0) {
            // Repeated fields are not supported.
            continue;
        }
        switch (fieldValue.mValue.getType()) {
            case INT:
            case LONG:
                result.push_back("INTEGER");
                break;
            case STRING:
                result.push_back("TEXT");
                break;
            case FLOAT:
                result.push_back("REAL");
                break;
            default:
                // Byte array fields are not supported.
                break;
        }
    }
    return result;
}

static int integrityCheckCallback(void*, int colCount, char** queryResults, char**) {
    if (colCount == 0 || strcmp(queryResults[0], "ok") != 0) {
        // Returning 1 is an error code that causes exec to stop and error.
        return 1;
    }
    return 0;
}

string getDbName(const ConfigKey& key) {
    return StringPrintf("%s/%d_%lld.db", STATS_RESTRICTED_DATA_DIR, key.GetUid(),
                        (long long)key.GetId());
}

static string getCreateSqlString(const int64_t metricId, const LogEvent& event) {
    string result = StringPrintf("CREATE TABLE IF NOT EXISTS %s%s", TABLE_NAME_PREFIX.c_str(),
                                 reformatMetricId(metricId).c_str());
    result += StringPrintf("(%s INTEGER,%s INTEGER,%s INTEGER,", COLUMN_NAME_ATOM_TAG.c_str(),
                           COLUMN_NAME_EVENT_ELAPSED_CLOCK_NS.c_str(),
                           COLUMN_NAME_EVENT_WALL_CLOCK_NS.c_str());
    for (size_t fieldId = 1; fieldId <= event.getValues().size(); ++fieldId) {
        const FieldValue& fieldValue = event.getValues()[fieldId - 1];
        if (fieldValue.mField.getDepth() > 0) {
            // Repeated fields are not supported.
            continue;
        }
        switch (fieldValue.mValue.getType()) {
            case INT:
            case LONG:
                result += StringPrintf("field_%d INTEGER,", fieldValue.mField.getPosAtDepth(0));
                break;
            case STRING:
                result += StringPrintf("field_%d TEXT,", fieldValue.mField.getPosAtDepth(0));
                break;
            case FLOAT:
                result += StringPrintf("field_%d REAL,", fieldValue.mField.getPosAtDepth(0));
                break;
            default:
                // Byte array fields are not supported.
                break;
        }
    }
    result.pop_back();
    result += ") STRICT;";
    return result;
}

string reformatMetricId(const int64_t metricId) {
    return metricId < 0 ? StringPrintf("n%lld", (long long)metricId * -1)
                        : StringPrintf("%lld", (long long)metricId);
}

bool createTableIfNeeded(const ConfigKey& key, const int64_t metricId, const LogEvent& event) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        sqlite3_close(db);
        return false;
    }

    char* error = nullptr;
    string zSql = getCreateSqlString(metricId, event);
    sqlite3_exec(db, zSql.c_str(), nullptr, nullptr, &error);
    sqlite3_close(db);
    if (error) {
        ALOGW("Failed to create table to db: %s", error);
        return false;
    }
    return true;
}

bool isEventCompatible(const ConfigKey& key, const int64_t metricId, const LogEvent& event) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        sqlite3_close(db);
        return false;
    }
    string zSql = StringPrintf("PRAGMA table_info(metric_%s);", reformatMetricId(metricId).c_str());
    string err;
    std::vector<int32_t> columnTypes;
    std::vector<string> columnNames;
    std::vector<std::vector<std::string>> rows;
    if (!query(key, zSql, rows, columnTypes, columnNames, err)) {
        ALOGE("Failed to check table schema for metric %lld: %s", (long long)metricId, err.c_str());
        sqlite3_close(db);
        return false;
    }
    // Sample query result
    // cid  name               type     notnull  dflt_value  pk
    // ---  -----------------  -------  -------  ----------  --
    // 0    atomId             INTEGER  0        (null)      0
    // 1    elapsedTimestampNs INTEGER  0        (null)      0
    // 2    wallTimestampNs    INTEGER  0        (null)      0
    // 3    field_1            INTEGER  0        (null)      0
    // 4    field_2            TEXT     0        (null)      0
    std::vector<string> tableSchema;
    for (size_t i = 3; i < rows.size(); ++i) {  // Atom fields start at the third row
        tableSchema.push_back(rows[i][2]);  // The third column stores the data type for the column
    }
    sqlite3_close(db);
    // An empty rows vector implies the table has not yet been created.
    return rows.size() == 0 || getExpectedTableSchema(event) == tableSchema;
}

bool deleteTable(const ConfigKey& key, const int64_t metricId) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        sqlite3_close(db);
        return false;
    }
    string zSql = StringPrintf("DROP TABLE metric_%s", reformatMetricId(metricId).c_str());
    char* error = nullptr;
    sqlite3_exec(db, zSql.c_str(), nullptr, nullptr, &error);
    sqlite3_close(db);
    if (error) {
        ALOGW("Failed to drop table from db: %s", error);
        return false;
    }
    return true;
}

void deleteDb(const ConfigKey& key) {
    const string dbName = getDbName(key);
    StorageManager::deleteFile(dbName.c_str());
}

sqlite3* getDb(const ConfigKey& key) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) == SQLITE_OK) {
        return db;
    }
    return nullptr;
}

void closeDb(sqlite3* db) {
    sqlite3_close(db);
}

static bool getInsertSqlStmt(sqlite3* db, sqlite3_stmt** stmt, const int64_t metricId,
                             const vector<LogEvent>& events, string& err) {
    string result =
            StringPrintf("INSERT INTO metric_%s VALUES", reformatMetricId(metricId).c_str());
    for (auto& logEvent : events) {
        result += StringPrintf("(%d, %lld, %lld,", logEvent.GetTagId(),
                               (long long)logEvent.GetElapsedTimestampNs(),
                               (long long)logEvent.GetLogdTimestampNs());
        for (auto& fieldValue : logEvent.getValues()) {
            if (fieldValue.mField.getDepth() > 0 || fieldValue.mValue.getType() == STORAGE) {
                // Repeated fields and byte fields are not supported.
                continue;
            }
            result += "?,";
        }
        result.pop_back();
        result += "),";
    }
    result.pop_back();
    result += ";";
    if (sqlite3_prepare_v2(db, result.c_str(), -1, stmt, nullptr) != SQLITE_OK) {
        err = sqlite3_errmsg(db);
        return false;
    }
    // ? parameters start with an index of 1 from start of query string to the
    // end.
    int32_t index = 1;
    for (auto& logEvent : events) {
        for (auto& fieldValue : logEvent.getValues()) {
            if (fieldValue.mField.getDepth() > 0 || fieldValue.mValue.getType() == STORAGE) {
                // Repeated fields and byte fields are not supported.
                continue;
            }
            switch (fieldValue.mValue.getType()) {
                case INT:
                    sqlite3_bind_int(*stmt, index, fieldValue.mValue.int_value);
                    break;
                case LONG:
                    sqlite3_bind_int64(*stmt, index, fieldValue.mValue.long_value);
                    break;
                case STRING:
                    sqlite3_bind_text(*stmt, index, fieldValue.mValue.str_value.c_str(), -1,
                                      SQLITE_STATIC);
                    break;
                case FLOAT:
                    sqlite3_bind_double(*stmt, index, fieldValue.mValue.float_value);
                    break;
                default:
                    // Byte array fields are not supported.
                    break;
            }
            ++index;
        }
    }
    return true;
}

bool insert(const ConfigKey& key, const int64_t metricId, const vector<LogEvent>& events,
            string& error) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        error = sqlite3_errmsg(db);
        sqlite3_close(db);
        return false;
    }
    bool success = insert(db, metricId, events, error);
    sqlite3_close(db);
    return success;
}

bool insert(sqlite3* db, const int64_t metricId, const vector<LogEvent>& events, string& error) {
    sqlite3_stmt* stmt = nullptr;
    if (!getInsertSqlStmt(db, &stmt, metricId, events, error)) {
        ALOGW("Failed to generate prepared sql insert query %s", error.c_str());
        sqlite3_finalize(stmt);
        return false;
    }
    if (sqlite3_step(stmt) != SQLITE_DONE) {
        error = sqlite3_errmsg(db);
        ALOGW("Failed to insert data to db: %s", error.c_str());
        sqlite3_finalize(stmt);
        return false;
    }
    sqlite3_finalize(stmt);
    return true;
}

bool query(const ConfigKey& key, const string& zSql, vector<vector<string>>& rows,
           vector<int32_t>& columnTypes, vector<string>& columnNames, string& err) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open_v2(dbName.c_str(), &db, SQLITE_OPEN_READONLY, nullptr) != SQLITE_OK) {
        err = sqlite3_errmsg(db);
        sqlite3_close(db);
        return false;
    }
    sqlite3_stmt* stmt;
    if (sqlite3_prepare_v2(db, zSql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
        err = sqlite3_errmsg(db);
        sqlite3_finalize(stmt);
        sqlite3_close(db);
        return false;
    }
    int result = sqlite3_step(stmt);
    bool firstIter = true;
    while (result == SQLITE_ROW) {
        int colCount = sqlite3_column_count(stmt);
        vector<string> rowData(colCount);
        for (int i = 0; i < colCount; ++i) {
            if (firstIter) {
                int32_t columnType = sqlite3_column_type(stmt, i);
                // Needed to convert to java compatible cursor types. See AbstractCursor#getType()
                if (columnType == 5) {
                    columnType = 0;  // Remap 5 (null type) to 0 for java cursor
                }
                columnTypes.push_back(columnType);
                columnNames.push_back(reinterpret_cast<const char*>(sqlite3_column_name(stmt, i)));
            }
            const unsigned char* textResult = sqlite3_column_text(stmt, i);
            string colData =
                    textResult != nullptr ? string(reinterpret_cast<const char*>(textResult)) : "";
            rowData[i] = std::move(colData);
        }
        rows.push_back(std::move(rowData));
        firstIter = false;
        result = sqlite3_step(stmt);
    }
    sqlite3_finalize(stmt);
    if (result != SQLITE_DONE) {
        err = sqlite3_errmsg(db);
        sqlite3_close(db);
        return false;
    }
    sqlite3_close(db);
    return true;
}

bool flushTtl(sqlite3* db, const int64_t metricId, const int64_t ttlWallClockNs) {
    string zSql = StringPrintf("DELETE FROM %s%s WHERE %s <= %lld", TABLE_NAME_PREFIX.c_str(),
                               reformatMetricId(metricId).c_str(),
                               COLUMN_NAME_EVENT_WALL_CLOCK_NS.c_str(), (long long)ttlWallClockNs);

    char* error = nullptr;
    sqlite3_exec(db, zSql.c_str(), nullptr, nullptr, &error);
    if (error) {
        ALOGW("Failed to enforce ttl: %s", error);
        return false;
    }
    return true;
}

void verifyIntegrityAndDeleteIfNecessary(const ConfigKey& configKey) {
    const string dbName = getDbName(configKey);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        sqlite3_close(db);
        return;
    }
    string zSql = "PRAGMA integrity_check";

    char* error = nullptr;
    sqlite3_exec(db, zSql.c_str(), integrityCheckCallback, nullptr, &error);
    if (error) {
        StatsdStats::getInstance().noteDbCorrupted(configKey);
        ALOGW("Integrity Check failed %s", error);
        sqlite3_close(db);
        deleteDb(configKey);
        return;
    }
    sqlite3_close(db);
}

static bool getDeviceInfoInsertStmt(sqlite3* db, sqlite3_stmt** stmt, string error) {
    string insertSql = StringPrintf("INSERT INTO device_info VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    if (sqlite3_prepare_v2(db, insertSql.c_str(), -1, stmt, nullptr) != SQLITE_OK) {
        error = sqlite3_errmsg(db);
        return false;
    }

    // ? parameters start with an index of 1 from start of query string to the end.
    int32_t index = 1;

    int32_t sdkVersion = android_get_device_api_level();
    sqlite3_bind_int(*stmt, index, sdkVersion);
    ++index;

    string model = GetProperty("ro.product.model", "(unknown)");
    sqlite3_bind_text(*stmt, index, model.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string product = GetProperty("ro.product.name", "(unknown)");
    sqlite3_bind_text(*stmt, index, product.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string hardware = GetProperty("ro.hardware", "(unknown)");
    sqlite3_bind_text(*stmt, index, hardware.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string device = GetProperty("ro.product.device", "(unknown)");
    sqlite3_bind_text(*stmt, index, device.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string osBuild = GetProperty("ro.build.id", "(unknown)");
    sqlite3_bind_text(*stmt, index, osBuild.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string fingerprint = GetProperty("ro.build.fingerprint", "(unknown)");
    sqlite3_bind_text(*stmt, index, fingerprint.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string brand = GetProperty("ro.product.brand", "(unknown)");
    sqlite3_bind_text(*stmt, index, brand.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string manufacturer = GetProperty("ro.product.manufacturer", "(unknown)");
    sqlite3_bind_text(*stmt, index, manufacturer.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    string board = GetProperty("ro.product.board", "(unknown)");
    sqlite3_bind_text(*stmt, index, board.c_str(), -1, SQLITE_TRANSIENT);
    ++index;

    return true;
}

bool updateDeviceInfoTable(const ConfigKey& key, string& error) {
    const string dbName = getDbName(key);
    sqlite3* db;
    if (sqlite3_open(dbName.c_str(), &db) != SQLITE_OK) {
        error = sqlite3_errmsg(db);
        sqlite3_close(db);
        return false;
    }

    string dropTableSql = "DROP TABLE device_info";
    // Ignore possible error result code if table has not yet been created.
    sqlite3_exec(db, dropTableSql.c_str(), nullptr, nullptr, nullptr);

    string createTableSql = StringPrintf(
            "CREATE TABLE device_info(%s INTEGER, %s TEXT, %s TEXT, %s TEXT, %s TEXT, %s TEXT, %s "
            "TEXT, %s TEXT, %s TEXT, %s TEXT) "
            "STRICT",
            COLUMN_NAME_SDK_VERSION.c_str(), COLUMN_NAME_MODEL.c_str(), COLUMN_NAME_PRODUCT.c_str(),
            COLUMN_NAME_HARDWARE.c_str(), COLUMN_NAME_DEVICE.c_str(), COLUMN_NAME_BUILD.c_str(),
            COLUMN_NAME_FINGERPRINT.c_str(), COLUMN_NAME_BRAND.c_str(),
            COLUMN_NAME_MANUFACTURER.c_str(), COLUMN_NAME_BOARD.c_str());
    if (sqlite3_exec(db, createTableSql.c_str(), nullptr, nullptr, nullptr) != SQLITE_OK) {
        error = sqlite3_errmsg(db);
        ALOGW("Failed to create device info table %s", error.c_str());
        sqlite3_close(db);
        return false;
    }

    sqlite3_stmt* stmt = nullptr;
    if (!getDeviceInfoInsertStmt(db, &stmt, error)) {
        ALOGW("Failed to generate device info prepared sql insert query %s", error.c_str());
        sqlite3_finalize(stmt);
        sqlite3_close(db);
        return false;
    }

    if (sqlite3_step(stmt) != SQLITE_DONE) {
        error = sqlite3_errmsg(db);
        ALOGW("Failed to insert data to device info table: %s", error.c_str());
        sqlite3_finalize(stmt);
        sqlite3_close(db);
        return false;
    }
    sqlite3_finalize(stmt);
    sqlite3_close(db);
    return true;
}
}  // namespace dbutils
}  // namespace statsd
}  // namespace os
}  // namespace android
