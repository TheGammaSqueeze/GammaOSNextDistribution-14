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

#define LOG_TAG "pixelstats: BrownoutDetected"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/BrownoutDetectedReporter.h>
#include <time.h>
#include <utils/Log.h>

#include <map>
#include <regex>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::hardware::google::pixel::PixelAtoms::BrownoutDetected;

#define READING_IDX 2
#define KEY_IDX 0
#define DEFAULT_BATTERY_TEMP 9999999
#define DEFAULT_BATTERY_SOC 100
#define DEFAULT_BATTERY_VOLT 5000000
#define ONE_SECOND_IN_US 1000000

const std::regex kTimestampPattern("^\\S+\\s[0-9]+:[0-9]+:[0-9]+\\S+$");
const std::regex kIrqPattern("^(\\S+)\\striggered\\sat\\s\\S+$");
const std::regex kOdpmPattern("^CH\\d+\\[(\\S+)\\],\\s(\\d+)$");
const std::regex kDvfsPattern("^([A-Z1-9]+):(\\d+)$");
const std::regex kFgPattern("^(voltage_now):(\\d+)$");
const std::regex kBatteryTempPattern("^(battery):(\\d+)$");
const std::regex kBatteryCyclePattern("^(battery_cycle):(\\d+)$");
const std::regex kBatterySocPattern("^(soc):(\\d+)$");
const std::regex kAlreadyUpdatedPattern("^(LASTMEAL_UPDATED)$");

const std::map<std::string, int> kBrownoutReason = {{"uvlo,pmic,if", BrownoutDetected::UVLO_IF},
                                                    {"ocp,pmic,if", BrownoutDetected::OCP_IF},
                                                    {"ocp2,pmic,if", BrownoutDetected::OCP2_IF},
                                                    {"uvlo,pmic,main", BrownoutDetected::UVLO_MAIN},
                                                    {"uvlo,pmic,sub", BrownoutDetected::UVLO_SUB},
                                                    {"ocp,buck1m", BrownoutDetected::OCP_B1M},
                                                    {"ocp,buck2m", BrownoutDetected::OCP_B2M},
                                                    {"ocp,buck3m", BrownoutDetected::OCP_B3M},
                                                    {"ocp,buck4m", BrownoutDetected::OCP_B4M},
                                                    {"ocp,buck5m", BrownoutDetected::OCP_B5M},
                                                    {"ocp,buck6m", BrownoutDetected::OCP_B6M},
                                                    {"ocp,buck7m", BrownoutDetected::OCP_B7M},
                                                    {"ocp,buck8m", BrownoutDetected::OCP_B8M},
                                                    {"ocp,buck9m", BrownoutDetected::OCP_B9M},
                                                    {"ocp,buck10m", BrownoutDetected::OCP_B10M},
                                                    {"ocp,buck1s", BrownoutDetected::OCP_B1S},
                                                    {"ocp,buck2s", BrownoutDetected::OCP_B2S},
                                                    {"ocp,buck3s", BrownoutDetected::OCP_B3S},
                                                    {"ocp,buck4s", BrownoutDetected::OCP_B4S},
                                                    {"ocp,buck5s", BrownoutDetected::OCP_B5S},
                                                    {"ocp,buck6s", BrownoutDetected::OCP_B6S},
                                                    {"ocp,buck7s", BrownoutDetected::OCP_B7S},
                                                    {"ocp,buck8s", BrownoutDetected::OCP_B8S},
                                                    {"ocp,buck9s", BrownoutDetected::OCP_B9S},
                                                    {"ocp,buck10s", BrownoutDetected::OCP_B10S},
                                                    {"ocp,buckas", BrownoutDetected::OCP_BAS},
                                                    {"ocp,buckbs", BrownoutDetected::OCP_BBS},
                                                    {"ocp,buckcs", BrownoutDetected::OCP_BCS},
                                                    {"ocp,buckds", BrownoutDetected::OCP_BDS}};

bool BrownoutDetectedReporter::updateIfFound(std::string line, std::regex pattern,
                                             int *current_value, Update flag) {
    bool found = false;
    std::smatch pattern_match;
    if (std::regex_match(line, pattern_match, pattern)) {
        if (pattern_match.size() < (READING_IDX + 1)) {
            return found;
        }
        found = true;
        int reading = std::stoi(pattern_match[READING_IDX].str());
        if (flag == kUpdateMax) {
            if (*current_value < reading) {
                *current_value = reading;
            }
        } else {
            if (*current_value > reading) {
                *current_value = reading;
            }
        }
    }
    return found;
}

void BrownoutDetectedReporter::setAtomFieldValue(std::vector<VendorAtomValue> *values, int offset,
                                                 int content) {
    std::vector<VendorAtomValue> &val = *values;
    if (offset - kVendorAtomOffset < val.size()) {
        val[offset - kVendorAtomOffset].set<VendorAtomValue::intValue>(content);
    }
}

void BrownoutDetectedReporter::uploadData(const std::shared_ptr<IStats> &stats_client,
                                          const struct BrownoutDetectedInfo max_value) {
    // Load values array
    VendorAtomValue tmp;
    std::vector<VendorAtomValue> values(37);
    setAtomFieldValue(&values, BrownoutDetected::kTriggeredIrqFieldNumber,
                      max_value.triggered_irq_);
    setAtomFieldValue(&values, BrownoutDetected::kTriggeredTimestampFieldNumber,
                      max_value.triggered_timestamp_);
    setAtomFieldValue(&values, BrownoutDetected::kBatteryTempFieldNumber, max_value.battery_temp_);
    setAtomFieldValue(&values, BrownoutDetected::kBatterySocFieldNumber,
                      100 - max_value.battery_soc_);
    setAtomFieldValue(&values, BrownoutDetected::kBatteryCycleFieldNumber,
                      max_value.battery_cycle_);
    setAtomFieldValue(&values, BrownoutDetected::kVoltageNowFieldNumber, max_value.voltage_now_);

    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel01FieldNumber,
                      max_value.odpm_value_[0]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel02FieldNumber,
                      max_value.odpm_value_[1]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel03FieldNumber,
                      max_value.odpm_value_[2]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel04FieldNumber,
                      max_value.odpm_value_[3]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel05FieldNumber,
                      max_value.odpm_value_[4]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel06FieldNumber,
                      max_value.odpm_value_[5]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel07FieldNumber,
                      max_value.odpm_value_[6]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel08FieldNumber,
                      max_value.odpm_value_[7]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel09FieldNumber,
                      max_value.odpm_value_[8]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel10FieldNumber,
                      max_value.odpm_value_[9]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel11FieldNumber,
                      max_value.odpm_value_[10]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel12FieldNumber,
                      max_value.odpm_value_[11]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel13FieldNumber,
                      max_value.odpm_value_[12]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel14FieldNumber,
                      max_value.odpm_value_[13]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel15FieldNumber,
                      max_value.odpm_value_[14]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel16FieldNumber,
                      max_value.odpm_value_[15]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel17FieldNumber,
                      max_value.odpm_value_[16]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel18FieldNumber,
                      max_value.odpm_value_[17]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel19FieldNumber,
                      max_value.odpm_value_[18]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel20FieldNumber,
                      max_value.odpm_value_[19]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel21FieldNumber,
                      max_value.odpm_value_[20]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel22FieldNumber,
                      max_value.odpm_value_[21]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel23FieldNumber,
                      max_value.odpm_value_[22]);
    setAtomFieldValue(&values, BrownoutDetected::kOdpmChannel24FieldNumber,
                      max_value.odpm_value_[23]);

    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel1FieldNumber,
                      max_value.dvfs_value_[0]);
    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel2FieldNumber,
                      max_value.dvfs_value_[1]);
    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel3FieldNumber,
                      max_value.dvfs_value_[2]);
    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel4FieldNumber,
                      max_value.dvfs_value_[3]);
    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel5FieldNumber,
                      max_value.dvfs_value_[4]);
    setAtomFieldValue(&values, BrownoutDetected::kDvfsChannel6FieldNumber,
                      max_value.dvfs_value_[5]);
    setAtomFieldValue(&values, BrownoutDetected::kBrownoutReasonFieldNumber,
                      max_value.brownout_reason_);

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kBrownoutDetected,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report ChargeStats to Stats service");
}

long BrownoutDetectedReporter::parseTimestamp(std::string timestamp) {
    struct tm triggeredTimestamp = {};
    std::string timestampFormat = "%Y-%m-%d %H:%M:%S";
    if (strptime(timestamp.substr(0, 19).c_str(), timestampFormat.c_str(), &triggeredTimestamp)) {
        auto logFileTime = std::chrono::system_clock::from_time_t(mktime(&triggeredTimestamp));
        return logFileTime.time_since_epoch().count() / ONE_SECOND_IN_US;
    }
    return 0;
}

int BrownoutDetectedReporter::brownoutReasonCheck(const std::string &brownoutReasonProp) {
    std::string reason = android::base::GetProperty(brownoutReasonProp.c_str(), "");
    if (reason.empty()) {
        // Brownout not found
        return -1;
    }
    auto key = kBrownoutReason.find(reason);
    if (key == kBrownoutReason.end()) {
        return -1;
    }
    return key->second;
}

int parseIRQ(const std::string &element) {
    int idx = atoi(element.c_str());
    if (idx == SMPL_WARN) {
        return BrownoutDetected::SMPL_WARN;
    } else if (idx == UVLO1) {
        return BrownoutDetected::UVLO1;
    } else if (idx == UVLO2) {
        return BrownoutDetected::UVLO2;
    } else if (idx == BATOILO) {
        return BrownoutDetected::BATOILO;
    } else if (idx == BATOILO2) {
        return BrownoutDetected::BATOILO2;
    }
    return -1;
}

void BrownoutDetectedReporter::logBrownoutCsv(const std::shared_ptr<IStats> &stats_client,
                                              const std::string &CsvFilePath,
                                              const std::string &brownoutReasonProp) {
    std::string csvFile;
    if (!android::base::ReadFileToString(CsvFilePath, &csvFile)) {
        return;
    }
    std::istringstream content(csvFile);
    std::string line;
    struct BrownoutDetectedInfo max_value = {};
    max_value.voltage_now_ = DEFAULT_BATTERY_VOLT;
    max_value.battery_soc_ = DEFAULT_BATTERY_SOC;
    max_value.battery_temp_ = DEFAULT_BATTERY_TEMP;
    std::smatch pattern_match;
    max_value.brownout_reason_ = brownoutReasonCheck(brownoutReasonProp);
    if (max_value.brownout_reason_ < 0) {
        return;
    }
    bool isAlreadyUpdated = false;
    std::vector<std::vector<std::string>> rows;
    int row_num = 0;
    while (std::getline(content, line)) {
        if (std::regex_match(line, pattern_match, kAlreadyUpdatedPattern)) {
            isAlreadyUpdated = true;
            break;
        }
        row_num++;
        if (row_num == 1) {
            continue;
        }
        std::vector<std::string> row;
        std::stringstream ss(line);
        std::string field;
        while (getline(ss, field, ',')) {
            row.push_back(field);
        }

        max_value.triggered_timestamp_ = parseTimestamp(row[TIMESTAMP_IDX].c_str());
        max_value.triggered_irq_ = parseIRQ(row[IRQ_IDX]);
        max_value.battery_soc_ = atoi(row[SOC_IDX].c_str());
        max_value.battery_temp_ = atoi(row[TEMP_IDX].c_str());
        max_value.battery_cycle_ = atoi(row[CYCLE_IDX].c_str());
        max_value.voltage_now_ = atoi(row[VOLTAGE_IDX].c_str());
        for (int i = 0; i < DVFS_MAX_IDX; i++) {
            max_value.dvfs_value_[i] = atoi(row[i + DVFS_CHANNEL_0].c_str());
        }
        for (int i = 0; i < ODPM_MAX_IDX; i++) {
            max_value.odpm_value_[i] = atoi(row[i + ODPM_CHANNEL_0].c_str());
        }
    }
    if (!isAlreadyUpdated && max_value.battery_temp_ != DEFAULT_BATTERY_TEMP) {
        std::string file_content = "LASTMEAL_UPDATED\n" + csvFile;
        android::base::WriteStringToFile(file_content, CsvFilePath);
        uploadData(stats_client, max_value);
    }
}

void BrownoutDetectedReporter::logBrownout(const std::shared_ptr<IStats> &stats_client,
                                           const std::string &logFilePath,
                                           const std::string &brownoutReasonProp) {
    std::string logFile;
    if (!android::base::ReadFileToString(logFilePath, &logFile)) {
        return;
    }
    std::istringstream content(logFile);
    std::string line;
    struct BrownoutDetectedInfo max_value = {};
    max_value.voltage_now_ = DEFAULT_BATTERY_VOLT;
    max_value.battery_soc_ = DEFAULT_BATTERY_SOC;
    max_value.battery_temp_ = DEFAULT_BATTERY_TEMP;
    std::smatch pattern_match;
    int odpm_index = 0, dvfs_index = 0;
    max_value.brownout_reason_ = brownoutReasonCheck(brownoutReasonProp);
    if (max_value.brownout_reason_ < 0) {
        return;
    }
    bool isAlreadyUpdated = false;
    while (std::getline(content, line)) {
        if (std::regex_match(line, pattern_match, kAlreadyUpdatedPattern)) {
            isAlreadyUpdated = true;
            break;
        }
        if (std::regex_match(line, pattern_match, kIrqPattern)) {
            if (pattern_match.size() < (KEY_IDX + 1)) {
                return;
            }
            std::ssub_match irq = pattern_match[KEY_IDX];
            if (irq.str().find("batoilo") != std::string::npos) {
                max_value.triggered_irq_ = BrownoutDetected::BATOILO;
                continue;
            }
            if (irq.str().find("vdroop1") != std::string::npos) {
                max_value.triggered_irq_ = BrownoutDetected::UVLO1;
                continue;
            }
            if (irq.str().find("vdroop2") != std::string::npos) {
                max_value.triggered_irq_ = BrownoutDetected::UVLO2;
                continue;
            }
            if (irq.str().find("smpl_gm") != std::string::npos) {
                max_value.triggered_irq_ = BrownoutDetected::SMPL_WARN;
                continue;
            }
            continue;
        }
        if (std::regex_match(line, pattern_match, kTimestampPattern)) {
            max_value.triggered_timestamp_ = parseTimestamp(line.c_str());
            continue;
        }
        if (updateIfFound(line, kBatterySocPattern, &max_value.battery_soc_, kUpdateMin)) {
            continue;
        }
        if (updateIfFound(line, kBatteryTempPattern, &max_value.battery_temp_, kUpdateMin)) {
            continue;
        }
        if (updateIfFound(line, kBatteryCyclePattern, &max_value.battery_cycle_, kUpdateMax)) {
            continue;
        }
        if (updateIfFound(line, kFgPattern, &max_value.voltage_now_, kUpdateMin)) {
            continue;
        }
        if (updateIfFound(line, kDvfsPattern, &max_value.dvfs_value_[dvfs_index], kUpdateMax)) {
            dvfs_index++;
            // Discarding previous value and update with new DVFS value
            if (dvfs_index == DVFS_MAX_IDX) {
                dvfs_index = 0;
            }
            continue;
        }
        if (updateIfFound(line, kOdpmPattern, &max_value.odpm_value_[odpm_index], kUpdateMax)) {
            odpm_index++;
            // Discarding previous value and update with new ODPM value
            if (odpm_index == ODPM_MAX_IDX) {
                odpm_index = 0;
            }
            continue;
        }
    }
    if (!isAlreadyUpdated && max_value.battery_temp_ != DEFAULT_BATTERY_TEMP) {
        std::string file_content = "LASTMEAL_UPDATED\n" + logFile;
        android::base::WriteStringToFile(file_content, logFilePath);
        uploadData(stats_client, max_value);
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
