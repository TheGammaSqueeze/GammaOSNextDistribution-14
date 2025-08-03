/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <gtest/gtest_prod.h>
#include <src/uid_data.pb.h>
#include <stdio.h>
#include <utils/RefBase.h>
#include <utils/String16.h>

#include <list>
#include <mutex>
#include <set>
#include <string>
#include <unordered_map>

#include "config/ConfigKey.h"
#include "packages/PackageInfoListener.h"
#include "stats_util.h"

using namespace android;
using namespace std;

using android::util::ProtoOutputStream;

namespace android {
namespace os {
namespace statsd {

struct AppData {
    int64_t versionCode;
    string versionString;
    string installer;
    bool deleted;
    string certificateHash;

    // Empty constructor needed for unordered map.
    AppData() {
    }

    AppData(const int64_t v, const string& versionString, const string& installer,
            const string& certificateHash)
        : versionCode(v),
          versionString(versionString),
          installer(installer),
          deleted(false),
          certificateHash(certificateHash){};
};

// When calling appendUidMap, we retrieve all the ChangeRecords since the last
// timestamp we called appendUidMap for this configuration key.
struct ChangeRecord {
    const bool deletion;
    const int64_t timestampNs;
    const string package;
    const int32_t uid;
    const int64_t version;
    const int64_t prevVersion;
    const string versionString;
    const string prevVersionString;

    ChangeRecord(const bool isDeletion, int64_t timestampNs, const string& package,
                 const int32_t uid, int64_t version, const string& versionString,
                 const int64_t prevVersion, const string& prevVersionString)
        : deletion(isDeletion),
          timestampNs(timestampNs),
          package(package),
          uid(uid),
          version(version),
          prevVersion(prevVersion),
          versionString(versionString),
          prevVersionString(prevVersionString) {
    }
};

const unsigned int kBytesChangeRecord = sizeof(struct ChangeRecord);

// UidMap keeps track of what the corresponding app name (APK name) and version code for every uid
// at any given moment. This map must be updated by StatsCompanionService.
class UidMap : public virtual RefBase {
public:
    UidMap();
    ~UidMap();
    static const std::map<std::string, uint32_t> sAidToUidMapping;

    static sp<UidMap> getInstance();

    void updateMap(const int64_t timestamp, const UidData& uidData);

    void updateApp(const int64_t timestamp, const string& appName, const int32_t uid,
                   const int64_t versionCode, const string& versionString, const string& installer,
                   const vector<uint8_t>& certificateHash);
    void removeApp(const int64_t timestamp, const string& app, const int32_t uid);

    // Returns true if the given uid contains the specified app (eg. com.google.android.gms).
    bool hasApp(int uid, const string& packageName) const;

    // Returns the app names from uid.
    std::set<string> getAppNamesFromUid(int32_t uid, bool returnNormalized) const;

    int64_t getAppVersion(int uid, const string& packageName) const;

    // Helper for debugging contents of this uid map. Can be triggered with:
    // adb shell cmd stats print-uid-map [--with_certificate_hash]
    void printUidMap(int outFd, bool includeCertificateHash) const;

    // Command for indicating to the map that StatsLogProcessor should be notified if an app is
    // updated. This allows metric producers and managers to distinguish when the same uid or app
    // represents a different version of an app.
    void setListener(const wp<PackageInfoListener>& listener);

    // Informs uid map that a config is added/updated. Used for keeping mConfigKeys up to date.
    void OnConfigUpdated(const ConfigKey& key);

    // Informs uid map that a config is removed. Used for keeping mConfigKeys up to date.
    void OnConfigRemoved(const ConfigKey& key);

    void assignIsolatedUid(int isolatedUid, int parentUid);
    void removeIsolatedUid(int isolatedUid);

    // Returns the host uid if it exists. Otherwise, returns the same uid that was passed-in.
    virtual int getHostUidOrSelf(int uid) const;

    // Gets all snapshots and changes that have occurred since the last output.
    // If every config key has received a change or snapshot record, then this
    // record is deleted.
    void appendUidMap(int64_t timestamp, const ConfigKey& key, const bool includeVersionStrings,
                      const bool includeInstaller, const uint8_t truncatedCertificateHashSize,
                      std::set<string>* str_set, ProtoOutputStream* proto);

    // Forces the output to be cleared. We still generate a snapshot based on the current state.
    // This results in extra data uploaded but helps us reconstruct the uid mapping on the server
    // in case we lose a previous upload.
    void clearOutput();

    // Get currently cached value of memory used by UID map.
    size_t getBytesUsed() const;

    virtual std::set<int32_t> getAppUid(const string& package) const;

    // Write current PackageInfoSnapshot to ProtoOutputStream.
    // interestingUids: If not empty, only write the package info for these uids. If empty, write
    //                  package info for all uids.
    // str_set: if not null, add new string to the set and write str_hash to proto
    //          if null, write string to proto.
    void writeUidMapSnapshot(int64_t timestamp, bool includeVersionStrings, bool includeInstaller,
                             const uint8_t truncatedCertificateHashSize,
                             const std::set<int32_t>& interestingUids,
                             std::map<string, int>* installerIndices, std::set<string>* str_set,
                             ProtoOutputStream* proto) const;

private:
    std::set<string> getAppNamesFromUidLocked(int32_t uid, bool returnNormalized) const;
    string normalizeAppName(const string& appName) const;

    void writeUidMapSnapshotLocked(const int64_t timestamp, const bool includeVersionStrings,
                                   const bool includeInstaller,
                                   const uint8_t truncatedCertificateHashSize,
                                   const std::set<int32_t>& interestingUids,
                                   std::map<string, int>* installerIndices,
                                   std::set<string>* str_set, ProtoOutputStream* proto) const;

    mutable mutex mMutex;
    mutable mutex mIsolatedMutex;

    struct PairHash {
        size_t operator()(const std::pair<int, string>& p) const noexcept {
            std::hash<std::string> hash_fn;
            return hash_fn(std::to_string(p.first) + p.second);
        }
    };
    // Maps uid and package name to application data.
    std::unordered_map<std::pair<int, string>, AppData, PairHash> mMap;

    // Maps isolated uid to the parent uid. Any metrics for an isolated uid will instead contribute
    // to the parent uid.
    std::unordered_map<int, int> mIsolatedUidMap;

    // Record the changes that can be provided with the uploads.
    std::list<ChangeRecord> mChanges;

    // Store which uid and apps represent deleted ones.
    std::list<std::pair<int, string>> mDeletedApps;

    // Notify StatsLogProcessor if there's an upgrade/removal in any app.
    wp<PackageInfoListener> mSubscriber;

    // Mapping of config keys we're aware of to the epoch time they last received an update. This
    // lets us know it's safe to delete events older than the oldest update. The value is nanosec.
    // Value of -1 denotes this config key has never received an upload.
    std::unordered_map<ConfigKey, int64_t> mLastUpdatePerConfigKey;

    // Returns the minimum value from mConfigKeys.
    int64_t getMinimumTimestampNs();

    // If our current used bytes is above the limit, then we clear out the earliest snapshot. If
    // there are no more snapshots, then we clear out the earliest delta. We repeat the deletions
    // until the memory consumed by mOutput is below the specified limit.
    void ensureBytesUsedBelowLimit();

    // Override used for testing the max memory allowed by uid map. 0 means we use the value
    // specified in StatsdStats.h with the rest of the guardrails.
    size_t maxBytesOverride = 0;

    // Cache the size of mOutput;
    size_t mBytesUsed;

    // Allows unit-test to access private methods.
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestRestrictedConfigUpdateDoesNotUpdateUidMap);
    FRIEND_TEST(RestrictedEventMetricE2eTest,
                TestRestrictedConfigUpdateAddsDelegateRemovesUidMapEntry);
    FRIEND_TEST(UidMapTest, TestClearingOutput);
    FRIEND_TEST(UidMapTest, TestRemovedAppRetained);
    FRIEND_TEST(UidMapTest, TestRemovedAppOverGuardrail);
    FRIEND_TEST(UidMapTest, TestOutputIncludesAtLeastOneSnapshot);
    FRIEND_TEST(UidMapTest, TestMemoryComputed);
    FRIEND_TEST(UidMapTest, TestMemoryGuardrail);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
