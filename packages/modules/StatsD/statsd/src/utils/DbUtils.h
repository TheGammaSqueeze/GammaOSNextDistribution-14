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
#pragma once

#include <sqlite3.h>

#include "config/ConfigKey.h"
#include "logd/LogEvent.h"

using std::string;
using std::vector;

namespace android {
namespace os {
namespace statsd {
namespace dbutils {

#define STATS_RESTRICTED_DATA_DIR "/data/misc/stats-data/restricted-data"

inline int32_t getDbVersion() {
    return SQLITE_VERSION_NUMBER;
};

string getDbName(const ConfigKey& key);

string reformatMetricId(const int64_t metricId);

/* Creates a new data table for a specified metric if one does not yet exist. */
bool createTableIfNeeded(const ConfigKey& key, int64_t metricId, const LogEvent& event);

/* Checks whether the table schema for the given metric matches the event.
 * Returns true if the table has not yet been created.
 */
bool isEventCompatible(const ConfigKey& key, int64_t metricId, const LogEvent& event);

/* Deletes a data table for the specified metric. */
bool deleteTable(const ConfigKey& key, int64_t metricId);

/* Deletes the SQLite db data file. */
void deleteDb(const ConfigKey& key);

/* Gets a handle to the sqlite db. You must call closeDb to free the allocated memory.
 * Returns a nullptr if an error occurs.
 */
sqlite3* getDb(const ConfigKey& key);

/* Closes the handle to the sqlite db. */
void closeDb(sqlite3* db);

/* Inserts new data into the specified metric data table.
 * A temp sqlite handle is created using the ConfigKey.
 */
bool insert(const ConfigKey& key, int64_t metricId, const vector<LogEvent>& events, string& error);

/* Inserts new data into the specified sqlite db handle. */
bool insert(sqlite3* db, int64_t metricId, const vector<LogEvent>& events, string& error);

/* Executes a sql query on the specified SQLite db.
 * A temp sqlite handle is created using the ConfigKey.
 */
bool query(const ConfigKey& key, const string& zSql, vector<vector<string>>& rows,
           vector<int32_t>& columnTypes, vector<string>& columnNames, string& err);

bool flushTtl(sqlite3* db, int64_t metricId, int64_t ttlWallClockNs);

/* Checks for database corruption and deletes the db if it is corrupted. */
void verifyIntegrityAndDeleteIfNecessary(const ConfigKey& key);

/* Creates and updates the device info table for the given configKey. */
bool updateDeviceInfoTable(const ConfigKey& key, string& error);

}  // namespace dbutils
}  // namespace statsd
}  // namespace os
}  // namespace android
