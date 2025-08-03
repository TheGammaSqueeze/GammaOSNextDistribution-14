/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <fcntl.h>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <iterator>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/scopeguard.h>
#include <logwrap/logwrap.h>
#include <odrefresh/odrefresh.h>

#include "CertUtils.h"
#include "KeystoreKey.h"
#include "StatsReporter.h"
#include "VerityUtils.h"
#include "statslog_odsign.h"

#include "odsign_info.pb.h"

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using android::base::SetProperty;

using OdsignInfo = ::odsign::proto::OdsignInfo;

// Keystore boot level that the odsign key uses
const int kKeyBootLevel = 30;
const std::string kPublicKeySignature = "/data/misc/odsign/publickey.signature";
const android::String16 kKeyAlias{"ondevice-signing"};
constexpr int kKeyNspace = 101;  // odsign_key

const std::string kSigningKeyCert = "/data/misc/odsign/key.cert";
const std::string kOdsignInfo = "/data/misc/odsign/odsign.info";
const std::string kOdsignInfoSignature = "/data/misc/odsign/odsign.info.signature";

const std::string kArtArtifactsDir = "/data/misc/apexdata/com.android.art/dalvik-cache";

constexpr const char* kOdrefreshPath = "/apex/com.android.art/bin/odrefresh";
constexpr const char* kCompOsVerifyPath = "/apex/com.android.compos/bin/compos_verify";

constexpr bool kForceCompilation = false;
constexpr bool kUseCompOs = true;

const std::string kCompOsPendingArtifactsDir = "/data/misc/apexdata/com.android.art/compos-pending";
const std::string kCompOsInfo = kArtArtifactsDir + "/compos.info";
const std::string kCompOsInfoSignature = kCompOsInfo + ".signature";

constexpr const char* kCompOsPendingInfoPath =
    "/data/misc/apexdata/com.android.art/compos-pending/compos.info";
constexpr const char* kCompOsPendingInfoSignaturePath =
    "/data/misc/apexdata/com.android.art/compos-pending/compos.info.signature";

constexpr const char* kOdsignVerificationDoneProp = "odsign.verification.done";
constexpr const char* kOdsignKeyDoneProp = "odsign.key.done";

constexpr const char* kOdsignVerificationStatusProp = "odsign.verification.success";
constexpr const char* kOdsignVerificationStatusValid = "1";
constexpr const char* kOdsignVerificationStatusError = "0";

constexpr const char* kStopServiceProp = "ctl.stop";

enum class CompOsInstance { kCurrent, kPending };

namespace {

bool rename(const std::string& from, const std::string& to) {
    std::error_code ec;
    std::filesystem::rename(from, to, ec);
    if (ec) {
        LOG(ERROR) << "Can't rename " << from << " to " << to << ": " << ec.message();
        return false;
    }
    return true;
}

int removeDirectory(const std::string& directory) {
    std::error_code ec;
    auto num_removed = std::filesystem::remove_all(directory, ec);
    if (ec) {
        LOG(ERROR) << "Can't remove " << directory << ": " << ec.message();
        return 0;
    } else {
        if (num_removed > 0) {
            LOG(INFO) << "Removed " << num_removed << " entries from " << directory;
        }
        return num_removed;
    }
}

bool directoryHasContent(const std::string& directory) {
    std::error_code ec;
    return std::filesystem::is_directory(directory, ec) &&
           !std::filesystem::is_empty(directory, ec);
}

art::odrefresh::ExitCode compileArtifacts(bool force) {
    const char* const argv[] = {kOdrefreshPath, force ? "--force-compile" : "--compile"};
    const int exit_code =
        logwrap_fork_execvp(arraysize(argv), argv, nullptr, false, LOG_ALOG, false, nullptr);
    return static_cast<art::odrefresh::ExitCode>(exit_code);
}

art::odrefresh::ExitCode checkArtifacts() {
    const char* const argv[] = {kOdrefreshPath, "--check"};
    const int exit_code =
        logwrap_fork_execvp(arraysize(argv), argv, nullptr, false, LOG_ALOG, false, nullptr);
    return static_cast<art::odrefresh::ExitCode>(exit_code);
}

std::string toHex(const std::vector<uint8_t>& digest) {
    std::stringstream ss;
    for (auto it = digest.begin(); it != digest.end(); ++it) {
        ss << std::setfill('0') << std::setw(2) << std::hex << static_cast<unsigned>(*it);
    }
    return ss.str();
}

bool compOsPresent() {
    // We must have the CompOS APEX
    return access(kCompOsVerifyPath, X_OK) == 0;
}

Result<void> verifyExistingRootCert(const SigningKey& key) {
    if (access(kSigningKeyCert.c_str(), F_OK) < 0) {
        return ErrnoError() << "Key certificate not found: " << kSigningKeyCert;
    }
    auto trustedPublicKey = key.getPublicKey();
    if (!trustedPublicKey.ok()) {
        return Error() << "Failed to retrieve signing public key: " << trustedPublicKey.error();
    }

    auto publicKeyFromExistingCert = extractPublicKeyFromX509(kSigningKeyCert);
    if (!publicKeyFromExistingCert.ok()) {
        return publicKeyFromExistingCert.error();
    }
    if (publicKeyFromExistingCert.value() != trustedPublicKey.value()) {
        return Error() << "Public key of existing certificate at " << kSigningKeyCert
                       << " does not match signing public key.";
    }

    // At this point, we know the cert is for our key; it's unimportant whether it's
    // actually self-signed.
    return {};
}

Result<void> createX509RootCert(const SigningKey& key, const std::string& outPath) {
    auto publicKey = key.getPublicKey();

    if (!publicKey.ok()) {
        return publicKey.error();
    }

    auto keySignFunction = [&](const std::string& to_be_signed) { return key.sign(to_be_signed); };
    return createSelfSignedCertificate(*publicKey, keySignFunction, outPath);
}

Result<std::map<std::string, std::string>> computeDigests(const std::string& path) {
    std::error_code ec;
    std::map<std::string, std::string> digests;

    auto it = std::filesystem::recursive_directory_iterator(path, ec);
    auto end = std::filesystem::recursive_directory_iterator();

    while (!ec && it != end) {
        if (it->is_regular_file()) {
            auto digest = createDigest(it->path());
            if (!digest.ok()) {
                return Error() << "Failed to compute digest for " << it->path() << ": "
                               << digest.error();
            }
            digests[it->path()] = toHex(*digest);
        }
        ++it;
    }
    if (ec) {
        return Error() << "Failed to iterate " << path << ": " << ec;
    }

    return digests;
}

Result<void> verifyDigests(const std::map<std::string, std::string>& digests,
                           const std::map<std::string, std::string>& trusted_digests) {
    for (const auto& path_digest : digests) {
        auto path = path_digest.first;
        auto digest = path_digest.second;
        if (trusted_digests.count(path) == 0) {
            return Error() << "Couldn't find digest for " << path;
        }
        if (trusted_digests.at(path) != digest) {
            return Error() << "Digest mismatch for " << path;
        }
    }

    // All digests matched!
    if (digests.size() > 0) {
        LOG(INFO) << "All root hashes match.";
    }
    return {};
}

Result<void> verifyIntegrityFsVerity(const std::map<std::string, std::string>& trusted_digests) {
    // Just verify that the files are in verity, and get their digests
    auto result = verifyAllFilesInVerity(kArtArtifactsDir);
    if (!result.ok()) {
        return result.error();
    }

    return verifyDigests(*result, trusted_digests);
}

Result<void> verifyIntegrityNoFsVerity(const std::map<std::string, std::string>& trusted_digests) {
    // On these devices, just compute the digests, and verify they match the ones we trust
    auto result = computeDigests(kArtArtifactsDir);
    if (!result.ok()) {
        return result.error();
    }

    return verifyDigests(*result, trusted_digests);
}

Result<OdsignInfo> getAndVerifyOdsignInfo(const SigningKey& key) {
    std::string persistedSignature;
    OdsignInfo odsignInfo;

    if (!android::base::ReadFileToString(kOdsignInfoSignature, &persistedSignature)) {
        return ErrnoError() << "Failed to read " << kOdsignInfoSignature;
    }

    std::fstream odsign_info(kOdsignInfo, std::ios::in | std::ios::binary);
    if (!odsign_info) {
        return Error() << "Failed to open " << kOdsignInfo;
    }
    odsign_info.seekg(0);
    // Verify the hash
    std::string odsign_info_str((std::istreambuf_iterator<char>(odsign_info)),
                                std::istreambuf_iterator<char>());

    auto publicKey = key.getPublicKey();
    auto signResult = verifySignature(odsign_info_str, persistedSignature, *publicKey);
    if (!signResult.ok()) {
        return Error() << kOdsignInfoSignature << " does not match.";
    } else {
        LOG(INFO) << kOdsignInfoSignature << " matches.";
    }

    odsign_info.seekg(0);
    if (!odsignInfo.ParseFromIstream(&odsign_info)) {
        return Error() << "Failed to parse " << kOdsignInfo;
    }

    LOG(INFO) << "Loaded " << kOdsignInfo;
    return odsignInfo;
}

std::map<std::string, std::string> getTrustedDigests(const SigningKey& key) {
    std::map<std::string, std::string> trusted_digests;

    if (access(kOdsignInfo.c_str(), F_OK) != 0) {
        // no odsign info file, which is not necessarily an error - just return
        // an empty list of digests.
        LOG(INFO) << kOdsignInfo << " not found.";
        return trusted_digests;
    }
    auto signInfo = getAndVerifyOdsignInfo(key);

    if (signInfo.ok()) {
        trusted_digests.insert(signInfo->file_hashes().begin(), signInfo->file_hashes().end());
    } else {
        // This is not expected, since the file did exist. Log an error and
        // return an empty list of digests.
        LOG(ERROR) << "Couldn't load trusted digests: " << signInfo.error();
    }

    return trusted_digests;
}

Result<void> persistDigests(const std::map<std::string, std::string>& digests,
                            const SigningKey& key) {
    OdsignInfo signInfo;
    google::protobuf::Map<std::string, std::string> proto_hashes(digests.begin(), digests.end());
    auto map = signInfo.mutable_file_hashes();
    *map = proto_hashes;

    std::fstream odsign_info(kOdsignInfo,
                             std::ios::in | std::ios::out | std::ios::trunc | std::ios::binary);
    if (!signInfo.SerializeToOstream(&odsign_info)) {
        return Error() << "Failed to persist root hashes in " << kOdsignInfo;
    }

    // Sign the signatures with our key itself, and write that to storage
    odsign_info.seekg(0, std::ios::beg);
    std::string odsign_info_str((std::istreambuf_iterator<char>(odsign_info)),
                                std::istreambuf_iterator<char>());
    auto signResult = key.sign(odsign_info_str);
    if (!signResult.ok()) {
        return Error() << "Failed to sign " << kOdsignInfo;
    }
    android::base::WriteStringToFile(*signResult, kOdsignInfoSignature);
    return {};
}

Result<void> verifyArtifactsIntegrity(const std::map<std::string, std::string>& trusted_digests,
                                      bool supportsFsVerity) {
    Result<void> integrityStatus;

    if (supportsFsVerity) {
        integrityStatus = verifyIntegrityFsVerity(trusted_digests);
    } else {
        integrityStatus = verifyIntegrityNoFsVerity(trusted_digests);
    }
    if (!integrityStatus.ok()) {
        return integrityStatus.error();
    }

    return {};
}

Result<OdsignInfo> getComposInfo() {
    const char* const argv[] = {kCompOsVerifyPath, "--instance", "current"};
    int result =
        logwrap_fork_execvp(arraysize(argv), argv, nullptr, false, LOG_ALOG, false, nullptr);
    if (result != 0) {
        return Error() << kCompOsVerifyPath << " returned " << result;
    }

    std::string compos_info_str;
    if (!android::base::ReadFileToString(kCompOsInfo, &compos_info_str)) {
        return ErrnoError() << "Failed to read " << kCompOsInfo;
    }

    // Delete the files - we don't need them any more, and they'd confuse
    // artifact verification
    if (unlink(kCompOsInfo.c_str()) != 0 || unlink(kCompOsInfoSignature.c_str()) != 0) {
        return ErrnoError() << "Unable to delete CompOS info/signature file";
    }

    OdsignInfo compos_info;
    if (!compos_info.ParseFromString(compos_info_str)) {
        return Error() << "Failed to parse " << kCompOsInfo;
    }

    LOG(INFO) << "Loaded " << kCompOsInfo;
    return compos_info;
}

art::odrefresh::ExitCode CheckCompOsPendingArtifacts(const SigningKey& signing_key,
                                                     bool* digests_verified,
                                                     StatsReporter* stats_reporter) {
    StatsReporter::CompOsArtifactsCheckRecord* compos_check_record =
        stats_reporter->GetOrCreateComposArtifactsCheckRecord();

    if (!directoryHasContent(kCompOsPendingArtifactsDir)) {
        // No pending CompOS artifacts, all that matters is the current ones.
        art::odrefresh::ExitCode odrefresh_status = checkArtifacts();
        if (odrefresh_status == art::odrefresh::ExitCode::kOkay) {
            compos_check_record->current_artifacts_ok = true;
        }
        return odrefresh_status;
    }

    compos_check_record->comp_os_pending_artifacts_exists = true;

    // CompOS has generated some artifacts that may, or may not, match the
    // current state.  But if there are already valid artifacts present the
    // CompOS ones are redundant.
    art::odrefresh::ExitCode odrefresh_status = checkArtifacts();
    if (odrefresh_status != art::odrefresh::ExitCode::kCompilationRequired) {
        if (odrefresh_status == art::odrefresh::ExitCode::kOkay) {
            compos_check_record->current_artifacts_ok = true;
            LOG(INFO) << "Current artifacts are OK, deleting pending artifacts";
            removeDirectory(kCompOsPendingArtifactsDir);
        }
        return odrefresh_status;
    }

    // No useful current artifacts, lets see if the CompOS ones are ok
    if (access(kCompOsPendingInfoPath, R_OK) != 0 ||
        access(kCompOsPendingInfoSignaturePath, R_OK) != 0) {
        LOG(INFO) << "Missing CompOS info/signature, deleting pending artifacts";
        removeDirectory(kCompOsPendingArtifactsDir);
        return art::odrefresh::ExitCode::kCompilationRequired;
    }

    LOG(INFO) << "Current artifacts are out of date, switching to pending artifacts";
    removeDirectory(kArtArtifactsDir);
    if (!rename(kCompOsPendingArtifactsDir, kArtArtifactsDir)) {
        removeDirectory(kCompOsPendingArtifactsDir);
        return art::odrefresh::ExitCode::kCompilationRequired;
    }

    // Make sure the artifacts we have are genuinely produced by the current
    // instance of CompOS.
    auto compos_info = getComposInfo();
    if (!compos_info.ok()) {
        LOG(WARNING) << compos_info.error();
    } else {
        std::map<std::string, std::string> compos_digests(compos_info->file_hashes().begin(),
                                                          compos_info->file_hashes().end());

        auto status = verifyAllFilesUsingCompOs(kArtArtifactsDir, compos_digests);
        if (!status.ok()) {
            LOG(WARNING) << "Faild to verify CompOS artifacts: " << status.error();
        } else {
            LOG(INFO) << "CompOS artifacts successfully verified.";
            odrefresh_status = checkArtifacts();
            switch (odrefresh_status) {
            case art::odrefresh::ExitCode::kCompilationRequired:
                // We have verified all the files, and we need to make sure
                // we don't check them against odsign.info which will be out
                // of date.
                *digests_verified = true;
                return odrefresh_status;
            case art::odrefresh::ExitCode::kOkay: {
                // We have digests of all the files, so we can just sign them & save them now.
                // We need to make sure we don't check them against odsign.info which will
                // be out of date.
                auto persisted = persistDigests(compos_digests, signing_key);
                if (!persisted.ok()) {
                    LOG(ERROR) << persisted.error();
                    // Don't try to compile again - if we can't write the digests, things
                    // are pretty bad.
                    return art::odrefresh::ExitCode::kCleanupFailed;
                }
                compos_check_record->use_comp_os_generated_artifacts = true;
                LOG(INFO) << "Persisted CompOS digests.";
                *digests_verified = true;
                return odrefresh_status;
            }
            default:
                return odrefresh_status;
            }
        }
    }

    // We can't use the existing artifacts, so we will need to generate new
    // ones.
    if (removeDirectory(kArtArtifactsDir) == 0) {
        // We have unsigned artifacts that we can't delete, so it's not safe to continue.
        LOG(ERROR) << "Unable to delete invalid CompOS artifacts";
        return art::odrefresh::ExitCode::kCleanupFailed;
    }

    return art::odrefresh::ExitCode::kCompilationRequired;
}
}  // namespace

int main(int /* argc */, char** argv) {
    android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));

    auto scope_guard = android::base::make_scope_guard([]() {
        // In case we hit any error, remove the artifacts and tell Zygote not to use
        // anything
        removeDirectory(kArtArtifactsDir);
        removeDirectory(kCompOsPendingArtifactsDir);
        // Tell init we don't need to use our key anymore
        SetProperty(kOdsignKeyDoneProp, "1");
        // Tell init we're done with verification, and that it was an error
        SetProperty(kOdsignVerificationStatusProp, kOdsignVerificationStatusError);
        SetProperty(kOdsignVerificationDoneProp, "1");
        // Tell init it shouldn't try to restart us - see odsign.rc
        SetProperty(kStopServiceProp, "odsign");
    });

    // `stats_reporter` must come after `scope_guard` so that its destructor is called before
    // `scope_guard`.
    auto stats_reporter = std::make_unique<StatsReporter>();
    StatsReporter::OdsignRecord* odsign_record = stats_reporter->GetOdsignRecord();

    if (!android::base::GetBoolProperty("ro.apex.updatable", false)) {
        LOG(INFO) << "Device doesn't support updatable APEX, exiting.";
        stats_reporter->SetOdsignRecordEnabled(false);
        return 0;
    }
    auto keystoreResult =
        KeystoreKey::getInstance(kPublicKeySignature, kKeyAlias, kKeyNspace, kKeyBootLevel);
    if (!keystoreResult.ok()) {
        LOG(ERROR) << "Could not create keystore key: " << keystoreResult.error();
        odsign_record->status =
            art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_KEYSTORE_FAILED;
        return -1;
    }
    SigningKey* key = keystoreResult.value();

    bool supportsFsVerity = SupportsFsVerity();
    if (!supportsFsVerity) {
        LOG(INFO) << "Device doesn't support fsverity. Falling back to full verification.";
    }

    bool useCompOs = kUseCompOs && supportsFsVerity && compOsPresent();

    if (supportsFsVerity) {
        auto existing_cert = verifyExistingRootCert(*key);
        if (!existing_cert.ok()) {
            LOG(WARNING) << existing_cert.error();

            // Try to create a new cert
            auto new_cert = createX509RootCert(*key, kSigningKeyCert);
            if (!new_cert.ok()) {
                LOG(ERROR) << "Failed to create X509 certificate: " << new_cert.error();
                // TODO apparently the key become invalid - delete the blob / cert
                odsign_record->status =
                    art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_CERT_FAILED;
                return -1;
            }
        } else {
            LOG(INFO) << "Found and verified existing public key certificate: " << kSigningKeyCert;
        }
    }

    bool digests_verified = false;
    art::odrefresh::ExitCode odrefresh_status =
        useCompOs ? CheckCompOsPendingArtifacts(*key, &digests_verified, stats_reporter.get())
                  : checkArtifacts();

    // The artifacts dir doesn't necessarily need to exist; if the existing
    // artifacts on the system partition are valid, those can be used.
    int err = access(kArtArtifactsDir.c_str(), F_OK);
    // If we receive any error other than ENOENT, be suspicious
    bool artifactsPresent = (err == 0) || (err < 0 && errno != ENOENT);

    if (artifactsPresent && !digests_verified &&
        (odrefresh_status == art::odrefresh::ExitCode::kOkay ||
         odrefresh_status == art::odrefresh::ExitCode::kCompilationRequired)) {
        // If we haven't verified the digests yet, we need to validate them. We
        // need to do this both in case the existing artifacts are okay, but
        // also if odrefresh said that a recompile is required. In the latter
        // case, odrefresh may use partial compilation, and leave some
        // artifacts unchanged.
        auto trusted_digests = getTrustedDigests(*key);

        if (odrefresh_status == art::odrefresh::ExitCode::kOkay) {
            // Tell init we're done with the key; this is a boot time optimization
            // in particular for the no fs-verity case, where we need to do a
            // costly verification. If the files haven't been tampered with, which
            // should be the common path, the verification will succeed, and we won't
            // need the key anymore. If it turns out the artifacts are invalid (eg not
            // in fs-verity) or the hash doesn't match, we won't be able to generate
            // new artifacts without the key, so in those cases, remove the artifacts,
            // and use JIT zygote for the current boot. We should recover automatically
            // by the next boot.
            SetProperty(kOdsignKeyDoneProp, "1");
        }

        auto verificationResult = verifyArtifactsIntegrity(trusted_digests, supportsFsVerity);
        if (!verificationResult.ok()) {
            int num_removed = removeDirectory(kArtArtifactsDir);
            if (num_removed == 0) {
                // If we can't remove the bad artifacts, we shouldn't continue, and
                // instead prevent Zygote from using them (which is taken care of
                // in the exit handler).
                LOG(ERROR) << "Failed to remove unknown artifacts.";
                odsign_record->status =
                    art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_CLEANUP_FAILED;
                return -1;
            }
        }
    }

    // Now that we verified existing artifacts, compile if we need to.
    if (odrefresh_status == art::odrefresh::ExitCode::kCompilationRequired) {
        odrefresh_status = compileArtifacts(kForceCompilation);
    }

    if (odrefresh_status == art::odrefresh::ExitCode::kOkay) {
        // No new artifacts generated, and we verified existing ones above, nothing left to do.
        LOG(INFO) << "odrefresh said artifacts are VALID";
        stats_reporter->SetOdsignRecordEnabled(false);
    } else if (odrefresh_status == art::odrefresh::ExitCode::kCompilationSuccess ||
               odrefresh_status == art::odrefresh::ExitCode::kCompilationFailed) {
        const bool compiled_all = odrefresh_status == art::odrefresh::ExitCode::kCompilationSuccess;
        LOG(INFO) << "odrefresh compiled " << (compiled_all ? "all" : "partial")
                  << " artifacts, returned " << odrefresh_status;
        // This value may be overwritten later.
        odsign_record->status =
            compiled_all ? art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_ALL_OK
                         : art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_PARTIAL_OK;
        Result<std::map<std::string, std::string>> digests;
        if (supportsFsVerity) {
            digests = addFilesToVerityRecursive(kArtArtifactsDir);
        } else {
            // If we can't use verity, just compute the root hashes and store
            // those, so we can reverify them at the next boot.
            digests = computeDigests(kArtArtifactsDir);
        }
        if (!digests.ok()) {
            LOG(ERROR) << digests.error();
            odsign_record->status =
                art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_SIGNING_FAILED;
            return -1;
        }
        auto persistStatus = persistDigests(*digests, *key);
        if (!persistStatus.ok()) {
            LOG(ERROR) << persistStatus.error();
            odsign_record->status =
                art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_SIGNING_FAILED;
            return -1;
        }
    } else if (odrefresh_status == art::odrefresh::ExitCode::kCleanupFailed) {
        LOG(ERROR) << "odrefresh failed cleaning up existing artifacts";
        odsign_record->status =
            art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_ODREFRESH_FAILED;
        return -1;
    } else {
        LOG(ERROR) << "odrefresh exited unexpectedly, returned " << odrefresh_status;
        odsign_record->status =
            art::metrics::statsd::ODSIGN_REPORTED__STATUS__STATUS_ODREFRESH_FAILED;
        return -1;
    }

    LOG(INFO) << "On-device signing done.";

    scope_guard.Disable();

    // Explicitly reset the pointer - We rely on stats_reporter's
    // destructor for actually writing the buffered metrics. This will otherwise not be called
    // if the program doesn't exit normally (for ex, killed by init, which actually happens
    // because odsign (after it finishes) sets kStopServiceProp instructing init to kill it).
    stats_reporter.reset();

    // At this point, we're done with the key for sure
    SetProperty(kOdsignKeyDoneProp, "1");
    // And we did a successful verification
    SetProperty(kOdsignVerificationStatusProp, kOdsignVerificationStatusValid);
    SetProperty(kOdsignVerificationDoneProp, "1");

    // Tell init it shouldn't try to restart us - see odsign.rc
    SetProperty(kStopServiceProp, "odsign");
    return 0;
}
