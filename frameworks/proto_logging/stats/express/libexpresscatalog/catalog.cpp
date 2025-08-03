/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "catalog.h"

#include <expresscatalog-utils.h>
#include <google/protobuf/text_format.h>
#include <inttypes.h>
#include <utils/hash/farmhash.h>

#include <filesystem>
#include <fstream>
#include <iostream>
#include <regex>
#include <sstream>
#include <unordered_set>

#define DEBUG true

using std::map;
using std::string;

namespace fs = std::filesystem;
namespace pb = google::protobuf;

namespace android {
namespace express {

namespace {

bool validateMetricId(const string& metricId) {
    // validation is done according to regEx
    static const char* regExStr = "[a-z]+[a-z_0-9]*[.]value_[a-z]+[a-z_0-9]*";
    static const std::regex expr(regExStr);

    if (!std::regex_match(metricId, expr)) {
        LOGE("Metric Id \"%s\"does not follow naming convention which is \"%s\"\n",
             metricId.c_str(), regExStr);
        return false;
    }

    return true;
}

bool readMetrics(const fs::path& cfgFile, map<string, ExpressMetric>& metrics) {
    std::ifstream fileStream(cfgFile.c_str());
    std::stringstream buffer;
    buffer << fileStream.rdbuf();

    LOGD("Metrics config content:\n %s\n", buffer.str().c_str());

    ExpressMetricConfigFile cfMessage;
    if (!pb::TextFormat::ParseFromString(buffer.str(), &cfMessage)) {
        LOGE("Can not process config file %s\n", cfgFile.c_str());
        return false;
    }

    LOGD("Metrics amount in the file %d\n", cfMessage.express_metric_size());

    for (int i = 0; i < cfMessage.express_metric_size(); i++) {
        const ExpressMetric& metric = cfMessage.express_metric(i);

        if (!metric.has_id()) {
            LOGE("No id is defined for metric index %d. Skip\n", i);
            return false;
        }

        LOGD("Metric: %s\n", metric.id().c_str());

        if (!validateMetricId(metric.id())) {
            return false;
        }

        if (metrics.find(metric.id()) != metrics.end()) {
            LOGE("Metric id redefinition error, broken uniqueness rule. Skip\n");
            return false;
        }

        metrics[metric.id()] = metric;
    }

    return true;
}

}  // namespace

bool readCatalog(const char* configDir, map<string, ExpressMetric>& metrics) {
    MEASURE_FUNC();
    auto configDirPath = configDir;

    LOGD("Config dir %s\n", configDirPath.c_str());

    for (const auto& entry : fs::directory_iterator(configDirPath)) {
        LOGD("Checking %s\n", entry.path().c_str());
        LOGD("  is_regular_file %d\n", fs::is_regular_file(entry.path()));
        LOGD("  extension %s\n", entry.path().extension().c_str());

        if (fs::is_regular_file(entry.path()) &&
            strcmp(entry.path().extension().c_str(), ".cfg") == 0) {
            LOGD("Located config: %s\n", entry.path().c_str());
            if (!readMetrics(entry, metrics)) {
                LOGE("Error parsing config: %s\n", entry.path().c_str());
                return false;
            }
        }
    }
    LOGD("Catalog dir %s processed\n", configDirPath.c_str());

    return true;
}

bool generateMetricsIds(const map<string, ExpressMetric>& metrics, MetricInfoMap& metricsIds) {
    MEASURE_FUNC();
    std::unordered_set<int64_t> currentHashes;

    for (const auto& [metricId, expressMetric] : metrics) {
        const int64_t hashId = farmhash::Fingerprint64(metricId.c_str(), metricId.size());

        // check if there is a collision
        if (currentHashes.find(hashId) != currentHashes.end()) {
            LOGE("Detected hash name collision for a metric %s\n", metricId.c_str());
            return false;
        }
        currentHashes.insert(hashId);
        metricsIds[metricId] = {hashId, expressMetric.type()};
    }

    return true;
}

}  // namespace express
}  // namespace android
