/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/result-gmock.h>
#include <android-base/scopeguard.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/apex/ApexInfo.h>
#include <android/apex/IApexService.h>
#include <android/os/IVold.h>
#include <binder/IServiceManager.h>
#include <fs_mgr_overlayfs.h>
#include <fstab/fstab.h>
#include <gmock/gmock.h>
#include <grp.h>
#include <gtest/gtest.h>
#include <libdm/dm.h>
#include <linux/loop.h>
#include <selinux/selinux.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/xattr.h>

#include <algorithm>
#include <filesystem>
#include <fstream>
#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <unordered_set>
#include <vector>

#include "apex_constants.h"
#include "apex_database.h"
#include "apex_file.h"
#include "apex_manifest.h"
#include "apexd.h"
#include "apexd_private.h"
#include "apexd_session.h"
#include "apexd_test_utils.h"
#include "apexd_utils.h"
#include "session_state.pb.h"
#include "string_log.h"

using apex::proto::SessionState;

namespace android {
namespace apex {

using android::sp;
using android::String16;
using android::apex::testing::CreateSessionInfo;
using android::apex::testing::IsOk;
using android::apex::testing::SessionInfoEq;
using android::base::EndsWith;
using android::base::Error;
using android::base::Join;
using android::base::Result;
using android::base::SetProperty;
using android::base::StartsWith;
using android::base::StringPrintf;
using android::base::unique_fd;
using android::base::testing::Ok;
using android::dm::DeviceMapper;
using ::apex::proto::ApexManifest;
using ::apex::proto::SessionState;
using ::testing::EndsWith;
using ::testing::Not;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

using MountedApexData = MountedApexDatabase::MountedApexData;

namespace fs = std::filesystem;

class ApexServiceTest : public ::testing::Test {
 public:
  ApexServiceTest() {}

 protected:
  void SetUp() override {
    // Enable VERBOSE logging to simplifying debugging
    SetProperty("log.tag.apexd", "VERBOSE");

    using android::IBinder;
    using android::IServiceManager;

    sp<IServiceManager> sm = android::defaultServiceManager();
    sp<IBinder> binder = sm->waitForService(String16("apexservice"));
    if (binder != nullptr) {
      service_ = android::interface_cast<IApexService>(binder);
    }
    binder = sm->getService(String16("vold"));
    if (binder != nullptr) {
      vold_service_ = android::interface_cast<android::os::IVold>(binder);
    }

    ASSERT_NE(nullptr, service_.get());
    ASSERT_NE(nullptr, vold_service_.get());
    android::binder::Status status =
        vold_service_->supportsCheckpoint(&supports_fs_checkpointing_);
    ASSERT_TRUE(IsOk(status));
    CleanUp();
    service_->recollectPreinstalledData(kApexPackageBuiltinDirs);
  }

  void TearDown() override { CleanUp(); }

  static std::string GetTestDataDir() {
    return android::base::GetExecutableDirectory();
  }
  static std::string GetTestFile(const std::string& name) {
    return GetTestDataDir() + "/" + name;
  }

  static bool HaveSelinux() { return 1 == is_selinux_enabled(); }

  static bool IsSelinuxEnforced() { return 0 != security_getenforce(); }

  Result<std::vector<ApexInfo>> GetAllPackages() {
    std::vector<ApexInfo> list;
    android::binder::Status status = service_->getAllPackages(&list);
    if (status.isOk()) {
      return list;
    }

    return Error() << status.toString8().c_str();
  }

  Result<std::vector<ApexInfo>> GetActivePackages() {
    std::vector<ApexInfo> list;
    android::binder::Status status = service_->getActivePackages(&list);
    if (status.isOk()) {
      return list;
    }

    return Error() << status.exceptionMessage().c_str();
  }

  Result<std::vector<ApexInfo>> GetInactivePackages() {
    std::vector<ApexInfo> list;
    android::binder::Status status = service_->getAllPackages(&list);
    list.erase(std::remove_if(
                   list.begin(), list.end(),
                   [](const ApexInfo& apexInfo) { return apexInfo.isActive; }),
               list.end());
    if (status.isOk()) {
      return list;
    }

    return Error() << status.toString8().c_str();
  }

  std::string GetPackageString(const ApexInfo& p) {
    return p.moduleName + "@" + std::to_string(p.versionCode) +
           " [path=" + p.moduleName + "]";
  }

  std::vector<std::string> GetPackagesStrings(
      const std::vector<ApexInfo>& list) {
    std::vector<std::string> ret;
    ret.reserve(list.size());
    for (const ApexInfo& p : list) {
      ret.push_back(GetPackageString(p));
    }
    return ret;
  }

  std::vector<std::string> GetActivePackagesStrings() {
    std::vector<ApexInfo> list;
    android::binder::Status status = service_->getActivePackages(&list);
    if (status.isOk()) {
      std::vector<std::string> ret(list.size());
      for (const ApexInfo& p : list) {
        ret.push_back(GetPackageString(p));
      }
      return ret;
    }

    std::vector<std::string> error;
    error.push_back("ERROR");
    return error;
  }

  Result<std::vector<ApexInfo>> GetFactoryPackages() {
    std::vector<ApexInfo> list;
    android::binder::Status status = service_->getAllPackages(&list);
    list.erase(
        std::remove_if(list.begin(), list.end(),
                       [](ApexInfo& apexInfo) { return !apexInfo.isFactory; }),
        list.end());
    if (status.isOk()) {
      return list;
    }

    return Error() << status.toString8().c_str();
  }

  static std::vector<std::string> ListDir(const std::string& path) {
    std::vector<std::string> ret;
    std::error_code ec;
    if (!fs::is_directory(path, ec)) {
      return ret;
    }
    auto status = WalkDir(path, [&](const fs::directory_entry& entry) {
      std::string tmp;
      switch (entry.symlink_status(ec).type()) {
        case fs::file_type::directory:
          tmp = "[dir]";
          break;
        case fs::file_type::symlink:
          tmp = "[lnk]";
          break;
        case fs::file_type::regular:
          tmp = "[reg]";
          break;
        default:
          tmp = "[other]";
      }
      ret.push_back(tmp.append(entry.path().filename()));
    });
    CHECK(status.has_value())
        << "Failed to list " << path << " : " << status.error();
    std::sort(ret.begin(), ret.end());
    return ret;
  }

  static void DeleteIfExists(const std::string& path) {
    if (fs::exists(path)) {
      std::error_code ec;
      fs::remove_all(path, ec);
      ASSERT_FALSE(ec) << "Failed to delete dir " << path << " : "
                       << ec.message();
    }
  }

  struct PrepareTestApexForInstall {
    static constexpr const char* kTestDir = "/data/app-staging/apexservice_tmp";

    // This is given to the constructor.
    std::string test_input;           // Original test file.
    std::string selinux_label_input;  // SELinux label to apply.
    std::string test_dir_input;

    // This is derived from the input.
    std::string test_file;            // Prepared path. Under test_dir_input.
    std::string test_installed_file;  // Where apexd will store it.

    std::string package;  // APEX package name.
    uint64_t version;     // APEX version

    explicit PrepareTestApexForInstall(
        const std::string& test,
        const std::string& test_dir = std::string(kTestDir),
        const std::string& selinux_label = "staging_data_file") {
      test_input = test;
      selinux_label_input = selinux_label;
      test_dir_input = test_dir;

      test_file = test_dir_input + "/" + android::base::Basename(test);

      package = "";  // Explicitly mark as not initialized.

      Result<ApexFile> apex_file = ApexFile::Open(test);
      if (!apex_file.ok()) {
        return;
      }

      const ApexManifest& manifest = apex_file->GetManifest();
      package = manifest.name();
      version = manifest.version();

      test_installed_file = std::string(kActiveApexPackagesDataDir) + "/" +
                            package + "@" + std::to_string(version) + ".apex";
    }

    bool Prepare() {
      if (package.empty()) {
        // Failure in constructor. Redo work to get error message.
        auto fail_fn = [&]() {
          Result<ApexFile> apex_file = ApexFile::Open(test_input);
          ASSERT_THAT(apex_file, Not(Ok()));
          ASSERT_TRUE(apex_file.ok())
              << test_input << " failed to load: " << apex_file.error();
        };
        fail_fn();
        return false;
      }

      auto prepare = [](const std::string& src, const std::string& trg,
                        const std::string& selinux_label) {
        ASSERT_EQ(0, access(src.c_str(), F_OK))
            << src << ": " << strerror(errno);
        const std::string trg_dir = android::base::Dirname(trg);
        if (0 != mkdir(trg_dir.c_str(), 0777)) {
          int saved_errno = errno;
          ASSERT_EQ(saved_errno, EEXIST) << trg << ":" << strerror(saved_errno);
        }

        // Do not use a hardlink, even though it's the simplest solution.
        // b/119569101.
        {
          std::ifstream src_stream(src, std::ios::binary);
          ASSERT_TRUE(src_stream.good());
          std::ofstream trg_stream(trg, std::ios::binary);
          ASSERT_TRUE(trg_stream.good());

          trg_stream << src_stream.rdbuf();
        }

        ASSERT_EQ(0, chmod(trg.c_str(), 0666)) << strerror(errno);
        struct group* g = getgrnam("system");
        ASSERT_NE(nullptr, g);
        ASSERT_EQ(0, chown(trg.c_str(), /* root uid */ 0, g->gr_gid))
            << strerror(errno);

        int rc = setfilecon(
            trg_dir.c_str(),
            std::string("u:object_r:" + selinux_label + ":s0").c_str());
        ASSERT_TRUE(0 == rc || !HaveSelinux()) << strerror(errno);
        rc = setfilecon(
            trg.c_str(),
            std::string("u:object_r:" + selinux_label + ":s0").c_str());
        ASSERT_TRUE(0 == rc || !HaveSelinux()) << strerror(errno);
      };
      prepare(test_input, test_file, selinux_label_input);
      return !HasFatalFailure();
    }

    ~PrepareTestApexForInstall() {
      LOG(INFO) << "Deleting file " << test_file;
      if (unlink(test_file.c_str()) != 0) {
        PLOG(ERROR) << "Unable to unlink " << test_file;
      }
      LOG(INFO) << "Deleting directory " << test_dir_input;
      if (rmdir(test_dir_input.c_str()) != 0) {
        PLOG(ERROR) << "Unable to rmdir " << test_dir_input;
      }
    }
  };

  std::string GetDebugStr(PrepareTestApexForInstall* installer) {
    StringLog log;

    if (installer != nullptr) {
      log << "test_input=" << installer->test_input << " ";
      log << "test_file=" << installer->test_file << " ";
      log << "test_installed_file=" << installer->test_installed_file << " ";
      log << "package=" << installer->package << " ";
      log << "version=" << installer->version << " ";
    }

    log << "active=[" << Join(GetActivePackagesStrings(), ',') << "] ";
    log << kActiveApexPackagesDataDir << "=["
        << Join(ListDir(kActiveApexPackagesDataDir), ',') << "] ";
    log << kApexRoot << "=[" << Join(ListDir(kApexRoot), ',') << "]";

    return log;
  }

  sp<IApexService> service_;
  sp<android::os::IVold> vold_service_;
  bool supports_fs_checkpointing_;

 private:
  void CleanUp() {
    DeleteDirContent(kActiveApexPackagesDataDir);
    DeleteDirContent(kApexBackupDir);
    DeleteDirContent(kApexHashTreeDir);
    DeleteDirContent(GetSessionsDir());

    DeleteIfExists("/data/misc_ce/0/apexdata/apex.apexd_test");
    DeleteIfExists("/data/misc_ce/0/apexrollback/123456");
    DeleteIfExists("/data/misc_ce/0/apexrollback/77777");
    DeleteIfExists("/data/misc_ce/0/apexrollback/98765");
    DeleteIfExists("/data/misc_de/0/apexrollback/123456");
    DeleteIfExists("/data/misc/apexrollback/123456");
  }
};

namespace {

bool RegularFileExists(const std::string& path) {
  struct stat buf;
  if (0 != stat(path.c_str(), &buf)) {
    return false;
  }
  return S_ISREG(buf.st_mode);
}

bool DirExists(const std::string& path) {
  struct stat buf;
  if (0 != stat(path.c_str(), &buf)) {
    return false;
  }
  return S_ISDIR(buf.st_mode);
}

void CreateDir(const std::string& path) {
  std::error_code ec;
  fs::create_directory(path, ec);
  ASSERT_FALSE(ec) << "Failed to create rollback dir "
                   << " : " << ec.message();
}

void CreateFile(const std::string& path) {
  std::ofstream ofs(path);
  ASSERT_TRUE(ofs.good());
  ofs.close();
}

void CreateFileWithExpectedProperties(const std::string& path) {
  CreateFile(path);
  std::error_code ec;
  fs::permissions(
      path,
      fs::perms::owner_read | fs::perms::group_write | fs::perms::others_exec,
      fs::perm_options::replace, ec);
  ASSERT_FALSE(ec) << "Failed to set permissions: " << ec.message();
  ASSERT_EQ(0, chown(path.c_str(), 1007 /* log */, 3001 /* net_bt_admin */))
      << "chown failed: " << strerror(errno);
  ASSERT_TRUE(RegularFileExists(path));
  char buf[65536];  // 64kB is max possible xattr list size. See "man 7 xattr".
  ASSERT_EQ(0, setxattr(path.c_str(), "user.foo", "bar", 4, 0));
  ASSERT_GE(listxattr(path.c_str(), buf, sizeof(buf)), 9);
  ASSERT_TRUE(memmem(buf, sizeof(buf), "user.foo", 9) != nullptr);
  ASSERT_EQ(4, getxattr(path.c_str(), "user.foo", buf, sizeof(buf)));
  ASSERT_STREQ("bar", buf);
}

void ExpectFileWithExpectedProperties(const std::string& path) {
  EXPECT_TRUE(RegularFileExists(path));
  EXPECT_EQ(fs::status(path).permissions(), fs::perms::owner_read |
                                                fs::perms::group_write |
                                                fs::perms::others_exec);
  struct stat sd;
  ASSERT_EQ(0, stat(path.c_str(), &sd));
  EXPECT_EQ(1007u, sd.st_uid);
  EXPECT_EQ(3001u, sd.st_gid);
  char buf[65536];  // 64kB is max possible xattr list size. See "man 7 xattr".
  EXPECT_GE(listxattr(path.c_str(), buf, sizeof(buf)), 9);
  EXPECT_TRUE(memmem(buf, sizeof(buf), "user.foo", 9) != nullptr);
  EXPECT_EQ(4, getxattr(path.c_str(), "user.foo", buf, sizeof(buf)));
  EXPECT_STREQ("bar", buf);
}

Result<std::vector<std::string>> ReadEntireDir(const std::string& path) {
  static const auto kAcceptAll = [](auto /*entry*/) { return true; };
  return ReadDir(path, kAcceptAll);
}

}  // namespace

TEST_F(ApexServiceTest, HaveSelinux) {
  // We want to test under selinux.
  EXPECT_TRUE(HaveSelinux());
}

// Skip for b/119032200.
TEST_F(ApexServiceTest, DISABLED_EnforceSelinux) {
  // Crude cutout for virtual devices.
#if !defined(__i386__) && !defined(__x86_64__)
  constexpr bool kIsX86 = false;
#else
  constexpr bool kIsX86 = true;
#endif
  EXPECT_TRUE(IsSelinuxEnforced() || kIsX86);
}

TEST_F(ApexServiceTest,
       SubmitStagegSessionSuccessDoesNotLeakTempVerityDevices) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_1543",
                                      "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 1543;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  std::vector<DeviceMapper::DmBlockDevice> devices;
  DeviceMapper& dm = DeviceMapper::Instance();
  ASSERT_TRUE(dm.GetAvailableDevices(&devices));

  for (const auto& device : devices) {
    ASSERT_THAT(device.name(), Not(EndsWith(".tmp")));
  }
}

TEST_F(ApexServiceTest, SubmitStagedSessionStoresBuildFingerprint) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_1547",
                                      "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }
  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 1547;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  auto session = ApexSession::GetSession(1547);
  ASSERT_FALSE(session->GetBuildFingerprint().empty());
}

TEST_F(ApexServiceTest, SubmitStagedSessionFailDoesNotLeakTempVerityDevices) {
  PrepareTestApexForInstall installer(
      GetTestFile("apex.apexd_test_manifest_mismatch.apex"),
      "/data/app-staging/session_239", "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 239;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));

  std::vector<DeviceMapper::DmBlockDevice> devices;
  DeviceMapper& dm = DeviceMapper::Instance();
  ASSERT_TRUE(dm.GetAvailableDevices(&devices));

  for (const auto& device : devices) {
    ASSERT_THAT(device.name(), Not(EndsWith(".tmp")));
  }
}

TEST_F(ApexServiceTest, CannotBeRollbackAndHaveRollbackEnabled) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_1543",
                                      "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 1543;
  params.isRollback = true;
  params.hasRollbackEnabled = true;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexServiceTest, SessionParamDefaults) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_1547",
                                      "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }
  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 1547;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  auto session = ApexSession::GetSession(1547);
  ASSERT_TRUE(session->GetChildSessionIds().empty());
  ASSERT_FALSE(session->IsRollback());
  ASSERT_FALSE(session->HasRollbackEnabled());
  ASSERT_EQ(0, session->GetRollbackId());
}

TEST_F(ApexServiceTest, SnapshotCeData) {
  CreateDir("/data/misc_ce/0/apexdata/apex.apexd_test");
  CreateFileWithExpectedProperties(
      "/data/misc_ce/0/apexdata/apex.apexd_test/hello.txt");

  service_->snapshotCeData(0, 123456, "apex.apexd_test");

  ExpectFileWithExpectedProperties(
      "/data/misc_ce/0/apexrollback/123456/apex.apexd_test/hello.txt");
}

TEST_F(ApexServiceTest, RestoreCeData) {
  CreateDir("/data/misc_ce/0/apexdata/apex.apexd_test");
  CreateDir("/data/misc_ce/0/apexrollback/123456");
  CreateDir("/data/misc_ce/0/apexrollback/123456/apex.apexd_test");

  CreateFile("/data/misc_ce/0/apexdata/apex.apexd_test/newfile.txt");
  CreateFileWithExpectedProperties(
      "/data/misc_ce/0/apexrollback/123456/apex.apexd_test/oldfile.txt");

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexdata/apex.apexd_test/newfile.txt"));
  ExpectFileWithExpectedProperties(
      "/data/misc_ce/0/apexrollback/123456/apex.apexd_test/oldfile.txt");

  service_->restoreCeData(0, 123456, "apex.apexd_test");

  ExpectFileWithExpectedProperties(
      "/data/misc_ce/0/apexdata/apex.apexd_test/oldfile.txt");
  EXPECT_FALSE(RegularFileExists(
      "/data/misc_ce/0/apexdata/apex.apexd_test/newfile.txt"));
  // The snapshot should be deleted after restoration.
  EXPECT_FALSE(
      DirExists("/data/misc_ce/0/apexrollback/123456/apex.apexd_test"));
}

TEST_F(ApexServiceTest, DestroyDeSnapshotsDeSys) {
  CreateDir("/data/misc/apexrollback/123456");
  CreateDir("/data/misc/apexrollback/123456/my.apex");
  CreateFile("/data/misc/apexrollback/123456/my.apex/hello.txt");

  ASSERT_TRUE(
      RegularFileExists("/data/misc/apexrollback/123456/my.apex/hello.txt"));

  service_->destroyDeSnapshots(8975);
  ASSERT_TRUE(
      RegularFileExists("/data/misc/apexrollback/123456/my.apex/hello.txt"));

  service_->destroyDeSnapshots(123456);
  ASSERT_FALSE(
      RegularFileExists("/data/misc/apexrollback/123456/my.apex/hello.txt"));
  ASSERT_FALSE(DirExists("/data/misc/apexrollback/123456"));
}

TEST_F(ApexServiceTest, DestroyDeSnapshotsDeUser) {
  CreateDir("/data/misc_de/0/apexrollback/123456");
  CreateDir("/data/misc_de/0/apexrollback/123456/my.apex");
  CreateFile("/data/misc_de/0/apexrollback/123456/my.apex/hello.txt");

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_de/0/apexrollback/123456/my.apex/hello.txt"));

  service_->destroyDeSnapshots(8975);
  ASSERT_TRUE(RegularFileExists(
      "/data/misc_de/0/apexrollback/123456/my.apex/hello.txt"));

  service_->destroyDeSnapshots(123456);
  ASSERT_FALSE(RegularFileExists(
      "/data/misc_de/0/apexrollback/123456/my.apex/hello.txt"));
  ASSERT_FALSE(DirExists("/data/misc_de/0/apexrollback/123456"));
}

TEST_F(ApexServiceTest, DestroyCeSnapshots) {
  CreateDir("/data/misc_ce/0/apexrollback/123456");
  CreateDir("/data/misc_ce/0/apexrollback/123456/apex.apexd_test");
  CreateFile("/data/misc_ce/0/apexrollback/123456/apex.apexd_test/file.txt");

  CreateDir("/data/misc_ce/0/apexrollback/77777");
  CreateDir("/data/misc_ce/0/apexrollback/77777/apex.apexd_test");
  CreateFile("/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt");

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/123456/apex.apexd_test/file.txt"));
  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt"));

  android::binder::Status st = service_->destroyCeSnapshots(0, 123456);
  ASSERT_TRUE(IsOk(st));
  // Should be OK if the directory doesn't exist.
  st = service_->destroyCeSnapshots(1, 123456);
  ASSERT_TRUE(IsOk(st));

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt"));
  ASSERT_FALSE(DirExists("/data/misc_ce/0/apexrollback/123456"));
}

TEST_F(ApexServiceTest, DestroyCeSnapshotsNotSpecified) {
  CreateDir("/data/misc_ce/0/apexrollback/123456");
  CreateDir("/data/misc_ce/0/apexrollback/123456/apex.apexd_test");
  CreateFile("/data/misc_ce/0/apexrollback/123456/apex.apexd_test/file.txt");

  CreateDir("/data/misc_ce/0/apexrollback/77777");
  CreateDir("/data/misc_ce/0/apexrollback/77777/apex.apexd_test");
  CreateFile("/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt");

  CreateDir("/data/misc_ce/0/apexrollback/98765");
  CreateDir("/data/misc_ce/0/apexrollback/98765/apex.apexd_test");
  CreateFile("/data/misc_ce/0/apexrollback/98765/apex.apexd_test/test.txt");

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/123456/apex.apexd_test/file.txt"));
  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt"));
  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/98765/apex.apexd_test/test.txt"));

  std::vector<int> retain{123, 77777, 987654};
  android::binder::Status st =
      service_->destroyCeSnapshotsNotSpecified(0, retain);
  ASSERT_TRUE(IsOk(st));

  ASSERT_TRUE(RegularFileExists(
      "/data/misc_ce/0/apexrollback/77777/apex.apexd_test/thing.txt"));
  ASSERT_FALSE(DirExists("/data/misc_ce/0/apexrollback/123456"));
  ASSERT_FALSE(DirExists("/data/misc_ce/0/apexrollback/98765"));
}

TEST_F(ApexServiceTest, SubmitStagedSessionCleanupsTempMountOnFailure) {
  // Parent session id: 23
  // Children session ids: 37 73
  PrepareTestApexForInstall installer(
      GetTestFile("apex.apexd_test_different_app.apex"),
      "/data/app-staging/session_37", "staging_data_file");
  PrepareTestApexForInstall installer2(
      GetTestFile("apex.apexd_test_manifest_mismatch.apex"),
      "/data/app-staging/session_73", "staging_data_file");
  if (!installer.Prepare() || !installer2.Prepare()) {
    FAIL() << GetDebugStr(&installer) << GetDebugStr(&installer2);
  }
  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 23;
  params.childSessionIds = {37, 73};
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)))
      << GetDebugStr(&installer);

  // Check that temp mounts were cleanded up.
  for (const auto& mount : GetApexMounts()) {
    EXPECT_FALSE(EndsWith(mount, ".tmp")) << "Found temp mount " << mount;
  }
}

TEST_F(ApexServiceTest, GetFactoryPackages) {
  Result<std::vector<ApexInfo>> factory_packages = GetFactoryPackages();
  ASSERT_RESULT_OK(factory_packages);
  ASSERT_TRUE(factory_packages->size() > 0);

  std::vector<std::string> builtin_dirs;
  for (const auto& d : kApexPackageBuiltinDirs) {
    std::string realpath;
    if (android::base::Realpath(d, &realpath)) {
      builtin_dirs.push_back(realpath);
    }
    // realpath might fail in case when dir is a non-existing path. We can
    // ignore non-existing paths.
  }

  // Decompressed APEX is also considred factory package
  builtin_dirs.push_back(kApexDecompressedDir);

  for (const ApexInfo& package : *factory_packages) {
    bool is_builtin = false;
    for (const auto& dir : builtin_dirs) {
      if (StartsWith(package.modulePath, dir)) {
        is_builtin = true;
      }
    }
    ASSERT_TRUE(is_builtin);
  }
}

TEST_F(ApexServiceTest, NoPackagesAreBothActiveAndInactive) {
  Result<std::vector<ApexInfo>> active_packages = GetActivePackages();
  ASSERT_RESULT_OK(active_packages);
  ASSERT_TRUE(active_packages->size() > 0);
  Result<std::vector<ApexInfo>> inactive_packages = GetInactivePackages();
  ASSERT_RESULT_OK(inactive_packages);
  std::vector<std::string> active_packages_strings =
      GetPackagesStrings(*active_packages);
  std::vector<std::string> inactive_packages_strings =
      GetPackagesStrings(*inactive_packages);
  std::sort(active_packages_strings.begin(), active_packages_strings.end());
  std::sort(inactive_packages_strings.begin(), inactive_packages_strings.end());
  std::vector<std::string> intersection;
  std::set_intersection(
      active_packages_strings.begin(), active_packages_strings.end(),
      inactive_packages_strings.begin(), inactive_packages_strings.end(),
      std::back_inserter(intersection));
  ASSERT_THAT(intersection, SizeIs(0));
}

TEST_F(ApexServiceTest, GetAllPackages) {
  Result<std::vector<ApexInfo>> all_packages = GetAllPackages();
  ASSERT_RESULT_OK(all_packages);
  ASSERT_TRUE(all_packages->size() > 0);
  Result<std::vector<ApexInfo>> active_packages = GetActivePackages();
  ASSERT_RESULT_OK(active_packages);
  std::vector<std::string> active_strings =
      GetPackagesStrings(*active_packages);
  Result<std::vector<ApexInfo>> factory_packages = GetFactoryPackages();
  ASSERT_RESULT_OK(factory_packages);
  std::vector<std::string> factory_strings =
      GetPackagesStrings(*factory_packages);
  for (ApexInfo& apexInfo : *all_packages) {
    std::string package_string = GetPackageString(apexInfo);
    bool should_be_active =
        std::find(active_strings.begin(), active_strings.end(),
                  package_string) != active_strings.end();
    bool should_be_factory =
        std::find(factory_strings.begin(), factory_strings.end(),
                  package_string) != factory_strings.end();
    ASSERT_EQ(should_be_active, apexInfo.isActive)
        << package_string << " should " << (should_be_active ? "" : "not ")
        << "be active";
    ASSERT_EQ(should_be_factory, apexInfo.isFactory)
        << package_string << " should " << (should_be_factory ? "" : "not ")
        << "be factory";
  }
}

TEST_F(ApexServiceTest, SubmitSingleSessionTestSuccess) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_123",
                                      "staging_data_file");
  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 123;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)))
      << GetDebugStr(&installer);
  EXPECT_EQ(1u, list.apexInfos.size());
  ApexInfo match;
  for (const ApexInfo& info : list.apexInfos) {
    if (info.moduleName == installer.package) {
      match = info;
      break;
    }
  }

  ASSERT_EQ(installer.package, match.moduleName);
  ASSERT_EQ(installer.version, static_cast<uint64_t>(match.versionCode));
  ASSERT_EQ(installer.test_file, match.modulePath);

  ApexSessionInfo session;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(123, &session)))
      << GetDebugStr(&installer);
  ApexSessionInfo expected = CreateSessionInfo(123);
  expected.isVerified = true;
  EXPECT_THAT(session, SessionInfoEq(expected));

  ASSERT_TRUE(IsOk(service_->markStagedSessionReady(123)));
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(123, &session)))
      << GetDebugStr(&installer);
  expected.isVerified = false;
  expected.isStaged = true;
  EXPECT_THAT(session, SessionInfoEq(expected));

  // Call markStagedSessionReady again. Should be a no-op.
  ASSERT_TRUE(IsOk(service_->markStagedSessionReady(123)))
      << GetDebugStr(&installer);

  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(123, &session)))
      << GetDebugStr(&installer);
  EXPECT_THAT(session, SessionInfoEq(expected));

  // See if the session is reported with getSessions() as well
  std::vector<ApexSessionInfo> sessions;
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)))
      << GetDebugStr(&installer);
  ASSERT_THAT(sessions, UnorderedElementsAre(SessionInfoEq(expected)));
}

TEST_F(ApexServiceTest, SubmitSingleSessionTestFail) {
  PrepareTestApexForInstall installer(
      GetTestFile("apex.apexd_test_corrupt_apex.apex"),
      "/data/app-staging/session_456", "staging_data_file");
  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 456;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)))
      << GetDebugStr(&installer);

  ApexSessionInfo session;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(456, &session)))
      << GetDebugStr(&installer);
  ApexSessionInfo expected = CreateSessionInfo(-1);
  expected.isUnknown = true;
  EXPECT_THAT(session, SessionInfoEq(expected));
}

TEST_F(ApexServiceTest, SubmitMultiSessionTestSuccess) {
  // Parent session id: 10
  // Children session ids: 20 30
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_20",
                                      "staging_data_file");
  PrepareTestApexForInstall installer2(
      GetTestFile("apex.apexd_test_different_app.apex"),
      "/data/app-staging/session_30", "staging_data_file");
  if (!installer.Prepare() || !installer2.Prepare()) {
    FAIL() << GetDebugStr(&installer) << GetDebugStr(&installer2);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 10;
  params.childSessionIds = {20, 30};
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)))
      << GetDebugStr(&installer);
  EXPECT_EQ(2u, list.apexInfos.size());
  ApexInfo match;
  bool package1_found = false;
  bool package2_found = false;
  for (const ApexInfo& info : list.apexInfos) {
    if (info.moduleName == installer.package) {
      ASSERT_EQ(installer.package, info.moduleName);
      ASSERT_EQ(installer.version, static_cast<uint64_t>(info.versionCode));
      ASSERT_EQ(installer.test_file, info.modulePath);
      package1_found = true;
    } else if (info.moduleName == installer2.package) {
      ASSERT_EQ(installer2.package, info.moduleName);
      ASSERT_EQ(installer2.version, static_cast<uint64_t>(info.versionCode));
      ASSERT_EQ(installer2.test_file, info.modulePath);
      package2_found = true;
    } else {
      FAIL() << "Unexpected package found " << info.moduleName
             << GetDebugStr(&installer) << GetDebugStr(&installer2);
    }
  }
  ASSERT_TRUE(package1_found);
  ASSERT_TRUE(package2_found);

  ApexSessionInfo session;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(10, &session)))
      << GetDebugStr(&installer);
  ApexSessionInfo expected = CreateSessionInfo(10);
  expected.isVerified = true;
  ASSERT_THAT(session, SessionInfoEq(expected));

  ASSERT_TRUE(IsOk(service_->markStagedSessionReady(10)))
      << GetDebugStr(&installer);

  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(10, &session)))
      << GetDebugStr(&installer);
  expected.isVerified = false;
  expected.isStaged = true;
  ASSERT_THAT(session, SessionInfoEq(expected));

  // Check that temp mounts were cleanded up.
  for (const auto& mount : GetApexMounts()) {
    EXPECT_FALSE(EndsWith(mount, ".tmp")) << "Found temp mount " << mount;
  }
}

TEST_F(ApexServiceTest, SubmitMultiSessionTestFail) {
  // Parent session id: 11
  // Children session ids: 21 31
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"),
                                      "/data/app-staging/session_21",
                                      "staging_data_file");
  PrepareTestApexForInstall installer2(
      GetTestFile("apex.apexd_test_corrupt_apex.apex"),
      "/data/app-staging/session_31", "staging_data_file");
  if (!installer.Prepare() || !installer2.Prepare()) {
    FAIL() << GetDebugStr(&installer) << GetDebugStr(&installer2);
  }
  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 11;
  params.childSessionIds = {21, 31};
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)))
      << GetDebugStr(&installer);
}

TEST_F(ApexServiceTest, MarkStagedSessionReadyFail) {
  // We should fail if we ask information about a session we don't know.
  ASSERT_FALSE(IsOk(service_->markStagedSessionReady(666)));

  ApexSessionInfo session;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(666, &session)));
  ApexSessionInfo expected = CreateSessionInfo(-1);
  expected.isUnknown = true;
  ASSERT_THAT(session, SessionInfoEq(expected));
}

TEST_F(ApexServiceTest, MarkStagedSessionSuccessfulFailsNoSession) {
  ASSERT_FALSE(IsOk(service_->markStagedSessionSuccessful(37)));

  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(37, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(-1);
  expected.isUnknown = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

TEST_F(ApexServiceTest, MarkStagedSessionSuccessfulFailsSessionInWrongState) {
  auto session = ApexSession::CreateSession(73);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(
      session->UpdateStateAndCommit(::apex::proto::SessionState::STAGED));

  ASSERT_FALSE(IsOk(service_->markStagedSessionSuccessful(73)));

  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(73, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(73);
  expected.isStaged = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

TEST_F(ApexServiceTest, MarkStagedSessionSuccessfulActivatedSession) {
  auto session = ApexSession::CreateSession(239);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(
      session->UpdateStateAndCommit(::apex::proto::SessionState::ACTIVATED));

  ASSERT_TRUE(IsOk(service_->markStagedSessionSuccessful(239)));

  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(239, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(239);
  expected.isSuccess = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

TEST_F(ApexServiceTest, MarkStagedSessionSuccessfulNoOp) {
  auto session = ApexSession::CreateSession(1543);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(
      session->UpdateStateAndCommit(::apex::proto::SessionState::SUCCESS));

  ASSERT_TRUE(IsOk(service_->markStagedSessionSuccessful(1543)));

  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(1543, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(1543);
  expected.isSuccess = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

// Should be able to abort individual staged session
TEST_F(ApexServiceTest, AbortStagedSession) {
  auto session1 = ApexSession::CreateSession(239);
  ASSERT_RESULT_OK(session1->UpdateStateAndCommit(SessionState::VERIFIED));
  auto session2 = ApexSession::CreateSession(240);
  ASSERT_RESULT_OK(session2->UpdateStateAndCommit(SessionState::STAGED));

  std::vector<ApexSessionInfo> sessions;
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ASSERT_EQ(2u, sessions.size());

  ASSERT_TRUE(IsOk(service_->abortStagedSession(239)));

  sessions.clear();
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ApexSessionInfo expected = CreateSessionInfo(240);
  expected.isStaged = true;
  ASSERT_THAT(sessions, UnorderedElementsAre(SessionInfoEq(expected)));
}

// abortStagedSession should not abort activated session
TEST_F(ApexServiceTest, AbortStagedSessionActivatedFail) {
  auto session1 = ApexSession::CreateSession(239);
  ASSERT_RESULT_OK(session1->UpdateStateAndCommit(SessionState::ACTIVATED));
  auto session2 = ApexSession::CreateSession(240);
  ASSERT_RESULT_OK(session2->UpdateStateAndCommit(SessionState::STAGED));

  std::vector<ApexSessionInfo> sessions;
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ASSERT_EQ(2u, sessions.size());

  ASSERT_FALSE(IsOk(service_->abortStagedSession(239)));

  sessions.clear();
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ApexSessionInfo expected1 = CreateSessionInfo(239);
  expected1.isActivated = true;
  ApexSessionInfo expected2 = CreateSessionInfo(240);
  expected2.isStaged = true;
  ASSERT_THAT(sessions, UnorderedElementsAre(SessionInfoEq(expected1),
                                             SessionInfoEq(expected2)));
}

// Only finalized sessions should be deleted on DeleteFinalizedSessions()
TEST_F(ApexServiceTest, DeleteFinalizedSessions) {
  // Fetch list of all session state
  std::vector<SessionState::State> states;
  for (int i = SessionState::State_MIN; i < SessionState::State_MAX; i++) {
    if (!SessionState::State_IsValid(i)) {
      continue;
    }
    states.push_back(SessionState::State(i));
  }

  // For every session state, create a new session. This is to verify we only
  // delete sessions in final state.
  auto nonFinalSessions = 0u;
  for (auto i = 0u; i < states.size(); i++) {
    auto session = ApexSession::CreateSession(230 + i);
    SessionState::State state = states[i];
    ASSERT_RESULT_OK(session->UpdateStateAndCommit(state));
    if (!session->IsFinalized()) {
      nonFinalSessions++;
    }
  }
  std::vector<ApexSession> sessions = ApexSession::GetSessions();
  ASSERT_EQ(states.size(), sessions.size());

  // Now try cleaning up all finalized sessions
  ApexSession::DeleteFinalizedSessions();
  sessions = ApexSession::GetSessions();
  ASSERT_EQ(nonFinalSessions, sessions.size());

  // Verify only finalized sessions have been deleted
  for (auto& session : sessions) {
    ASSERT_FALSE(session.IsFinalized());
  }
}

TEST_F(ApexServiceTest, BackupActivePackages) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }
  PrepareTestApexForInstall installer1(GetTestFile("apex.apexd_test.apex"));
  PrepareTestApexForInstall installer2(
      GetTestFile("apex.apexd_test_different_app.apex"));
  PrepareTestApexForInstall installer3(GetTestFile("apex.apexd_test_v2.apex"),
                                       "/data/app-staging/session_23",
                                       "staging_data_file");

  if (!installer1.Prepare() || !installer2.Prepare() || !installer3.Prepare()) {
    return;
  }

  // Activate some packages, in order to backup them later.
  std::vector<std::string> pkgs = {installer1.test_file, installer2.test_file};
  ASSERT_TRUE(IsOk(service_->stagePackages(pkgs)));

  // Make sure that /data/apex/active has activated packages.
  auto active_pkgs = ReadEntireDir(kActiveApexPackagesDataDir);
  ASSERT_RESULT_OK(active_pkgs);
  ASSERT_THAT(*active_pkgs,
              UnorderedElementsAre(installer1.test_installed_file,
                                   installer2.test_installed_file));

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 23;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  auto backups = ReadEntireDir(kApexBackupDir);
  ASSERT_RESULT_OK(backups);
  auto backup1 =
      StringPrintf("%s/com.android.apex.test_package@1.apex", kApexBackupDir);
  auto backup2 =
      StringPrintf("%s/com.android.apex.test_package_2@1.apex", kApexBackupDir);
  ASSERT_THAT(*backups, UnorderedElementsAre(backup1, backup2));
}

TEST_F(ApexServiceTest, BackupActivePackagesClearsPreviousBackup) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }
  PrepareTestApexForInstall installer1(GetTestFile("apex.apexd_test.apex"));
  PrepareTestApexForInstall installer2(
      GetTestFile("apex.apexd_test_different_app.apex"));
  PrepareTestApexForInstall installer3(GetTestFile("apex.apexd_test_v2.apex"),
                                       "/data/app-staging/session_43",
                                       "staging_data_file");

  if (!installer1.Prepare() || !installer2.Prepare() || !installer3.Prepare()) {
    return;
  }

  // Make sure /data/apex/backups exists.
  ASSERT_RESULT_OK(CreateDirIfNeeded(std::string(kApexBackupDir), 0700));
  // Create some bogus files in /data/apex/backups.
  std::ofstream old_backup(StringPrintf("%s/file1", kApexBackupDir));
  ASSERT_TRUE(old_backup.good());
  old_backup.close();

  std::vector<std::string> pkgs = {installer1.test_file, installer2.test_file};
  ASSERT_TRUE(IsOk(service_->stagePackages(pkgs)));

  // Make sure that /data/apex/active has activated packages.
  auto active_pkgs = ReadEntireDir(kActiveApexPackagesDataDir);
  ASSERT_RESULT_OK(active_pkgs);
  ASSERT_THAT(*active_pkgs,
              UnorderedElementsAre(installer1.test_installed_file,
                                   installer2.test_installed_file));

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 43;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  auto backups = ReadEntireDir(kApexBackupDir);
  ASSERT_RESULT_OK(backups);
  auto backup1 =
      StringPrintf("%s/com.android.apex.test_package@1.apex", kApexBackupDir);
  auto backup2 =
      StringPrintf("%s/com.android.apex.test_package_2@1.apex", kApexBackupDir);
  ASSERT_THAT(*backups, UnorderedElementsAre(backup1, backup2));
}

TEST_F(ApexServiceTest, BackupActivePackagesZeroActivePackages) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"),
                                      "/data/app-staging/session_41",
                                      "staging_data_file");

  if (!installer.Prepare()) {
    return;
  }

  // Make sure that /data/apex/active exists and is empty
  ASSERT_RESULT_OK(
      CreateDirIfNeeded(std::string(kActiveApexPackagesDataDir), 0755));
  auto active_pkgs = ReadEntireDir(kActiveApexPackagesDataDir);
  ASSERT_RESULT_OK(active_pkgs);
  ASSERT_EQ(0u, active_pkgs->size());

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 41;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  auto backups = ReadEntireDir(kApexBackupDir);
  ASSERT_RESULT_OK(backups);
  ASSERT_EQ(0u, backups->size());
}

TEST_F(ApexServiceTest, ActivePackagesDirEmpty) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"),
                                      "/data/app-staging/session_41",
                                      "staging_data_file");

  if (!installer.Prepare()) {
    return;
  }

  // Make sure that /data/apex/active is empty
  DeleteDirContent(kActiveApexPackagesDataDir);

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 41;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));

  if (!supports_fs_checkpointing_) {
    auto backups = ReadEntireDir(kApexBackupDir);
    ASSERT_RESULT_OK(backups);
    ASSERT_EQ(0u, backups->size());
  }
}

class ApexServiceRevertTest : public ApexServiceTest {
 protected:
  void SetUp() override { ApexServiceTest::SetUp(); }

  void PrepareBackup(const std::vector<std::string>& pkgs) {
    ASSERT_RESULT_OK(CreateDirIfNeeded(std::string(kApexBackupDir), 0700));
    for (const auto& pkg : pkgs) {
      PrepareTestApexForInstall installer(pkg);
      ASSERT_TRUE(installer.Prepare()) << " failed to prepare " << pkg;
      const std::string& from = installer.test_file;
      std::string to = std::string(kApexBackupDir) + "/" + installer.package +
                       "@" + std::to_string(installer.version) + ".apex";
      std::error_code ec;
      fs::copy(fs::path(from), fs::path(to),
               fs::copy_options::create_hard_links, ec);
      ASSERT_FALSE(ec) << "Failed to copy " << from << " to " << to << " : "
                       << ec;
    }
  }

  void CheckActiveApexContents(const std::vector<std::string>& expected_pkgs) {
    // First check that /data/apex/active exists and has correct permissions.
    struct stat sd;
    ASSERT_EQ(0, stat(kActiveApexPackagesDataDir, &sd));
    ASSERT_EQ(0755u, sd.st_mode & ALLPERMS);

    // Now read content and check it contains expected values.
    auto active_pkgs = ReadEntireDir(kActiveApexPackagesDataDir);
    ASSERT_RESULT_OK(active_pkgs);
    ASSERT_THAT(*active_pkgs, UnorderedElementsAreArray(expected_pkgs));
  }
};

// Should be able to revert activated sessions
TEST_F(ApexServiceRevertTest, RevertActiveSessionsSuccessful) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }

  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"));
  if (!installer.Prepare()) {
    return;
  }

  auto session = ApexSession::CreateSession(1543);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::ACTIVATED));

  // Make sure /data/apex/active is non-empty.
  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));

  PrepareBackup({GetTestFile("apex.apexd_test.apex")});

  ASSERT_TRUE(IsOk(service_->revertActiveSessions()));

  auto pkg = StringPrintf("%s/com.android.apex.test_package@1.apex",
                          kActiveApexPackagesDataDir);
  SCOPED_TRACE("");
  CheckActiveApexContents({pkg});
}

// Calling revertActiveSessions should not restore backup on checkpointing
// devices
TEST_F(ApexServiceRevertTest,
       RevertActiveSessionsDoesNotRestoreBackupIfCheckpointingSupported) {
  if (!supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is not supported";
  }

  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"));
  if (!installer.Prepare()) {
    return;
  }

  auto session = ApexSession::CreateSession(1543);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::ACTIVATED));

  // Make sure /data/apex/active is non-empty.
  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));

  PrepareBackup({GetTestFile("apex.apexd_test.apex")});

  ASSERT_TRUE(IsOk(service_->revertActiveSessions()));

  // Check that active apexes were not reverted.
  auto pkg = StringPrintf("%s/com.android.apex.test_package@2.apex",
                          kActiveApexPackagesDataDir);
  SCOPED_TRACE("");
  CheckActiveApexContents({pkg});
}

// Should fail to revert active sessions when there are none
TEST_F(ApexServiceRevertTest, RevertActiveSessionsWithoutActiveSessions) {
  // This test simulates a situation that should never happen on user builds:
  // revertActiveSessions was called, but there were no active sessions.
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"));
  if (!installer.Prepare()) {
    return;
  }

  // Make sure /data/apex/active is non-empty.
  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));

  PrepareBackup({GetTestFile("apex.apexd_test.apex")});

  // Even though backup is there, no sessions are active, hence revert request
  // should fail.
  ASSERT_FALSE(IsOk(service_->revertActiveSessions()));
}

TEST_F(ApexServiceRevertTest, RevertFailsNoBackupFolder) {
  ASSERT_FALSE(IsOk(service_->revertActiveSessions()));
}

TEST_F(ApexServiceRevertTest, RevertFailsNoActivePackagesFolder) {
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test.apex"));
  ASSERT_FALSE(IsOk(service_->revertActiveSessions()));
}

TEST_F(ApexServiceRevertTest, MarkStagedSessionSuccessfulCleanupBackup) {
  PrepareBackup({GetTestFile("apex.apexd_test.apex"),
                 GetTestFile("apex.apexd_test_different_app.apex")});

  auto session = ApexSession::CreateSession(101);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::ACTIVATED));

  ASSERT_TRUE(IsOk(service_->markStagedSessionSuccessful(101)));

  ASSERT_TRUE(fs::is_empty(fs::path(kApexBackupDir)));
}

TEST_F(ApexServiceRevertTest, ResumesRevert) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }
  PrepareBackup({GetTestFile("apex.apexd_test.apex"),
                 GetTestFile("apex.apexd_test_different_app.apex")});

  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"));
  if (!installer.Prepare()) {
    return;
  }

  // Make sure /data/apex/active is non-empty.
  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));

  auto session = ApexSession::CreateSession(17239);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(
      session->UpdateStateAndCommit(SessionState::REVERT_IN_PROGRESS));

  ASSERT_TRUE(IsOk(service_->resumeRevertIfNeeded()));

  auto pkg1 = StringPrintf("%s/com.android.apex.test_package@1.apex",
                           kActiveApexPackagesDataDir);
  auto pkg2 = StringPrintf("%s/com.android.apex.test_package_2@1.apex",
                           kActiveApexPackagesDataDir);
  SCOPED_TRACE("");
  CheckActiveApexContents({pkg1, pkg2});

  std::vector<ApexSessionInfo> sessions;
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ApexSessionInfo expected = CreateSessionInfo(17239);
  expected.isReverted = true;
  ASSERT_THAT(sessions, UnorderedElementsAre(SessionInfoEq(expected)));
}

TEST_F(ApexServiceRevertTest, DoesNotResumeRevert) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }
  PrepareTestApexForInstall installer(GetTestFile("apex.apexd_test_v2.apex"));
  if (!installer.Prepare()) {
    return;
  }

  // Make sure /data/apex/active is non-empty.
  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));

  auto session = ApexSession::CreateSession(53);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::SUCCESS));

  ASSERT_TRUE(IsOk(service_->resumeRevertIfNeeded()));

  // Check that revert wasn't resumed.
  auto active_pkgs = ReadEntireDir(kActiveApexPackagesDataDir);
  ASSERT_RESULT_OK(active_pkgs);
  ASSERT_THAT(*active_pkgs,
              UnorderedElementsAre(installer.test_installed_file));

  std::vector<ApexSessionInfo> sessions;
  ASSERT_TRUE(IsOk(service_->getSessions(&sessions)));
  ApexSessionInfo expected = CreateSessionInfo(53);
  expected.isSuccess = true;
  ASSERT_THAT(sessions, UnorderedElementsAre(SessionInfoEq(expected)));
}

// Should mark sessions as REVERT_FAILED on failed revert
TEST_F(ApexServiceRevertTest, SessionsMarkedAsRevertFailed) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }

  auto session = ApexSession::CreateSession(53);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::ACTIVATED));

  ASSERT_FALSE(IsOk(service_->revertActiveSessions()));
  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(53, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(53);
  expected.isRevertFailed = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

TEST_F(ApexServiceRevertTest, RevertFailedStateRevertAttemptFails) {
  if (supports_fs_checkpointing_) {
    GTEST_SKIP() << "Can't run if filesystem checkpointing is enabled";
  }

  auto session = ApexSession::CreateSession(17239);
  ASSERT_RESULT_OK(session);
  ASSERT_RESULT_OK(session->UpdateStateAndCommit(SessionState::REVERT_FAILED));

  ASSERT_FALSE(IsOk(service_->revertActiveSessions()));
  ApexSessionInfo session_info;
  ASSERT_TRUE(IsOk(service_->getStagedSessionInfo(17239, &session_info)));
  ApexSessionInfo expected = CreateSessionInfo(17239);
  expected.isRevertFailed = true;
  ASSERT_THAT(session_info, SessionInfoEq(expected));
}

static pid_t GetPidOf(const std::string& name) {
  char buf[1024];
  const std::string cmd = std::string("pidof -s ") + name;
  FILE* cmd_pipe = popen(cmd.c_str(), "r");  // NOLINT(cert-env33-c): test code
  if (cmd_pipe == nullptr) {
    PLOG(ERROR) << "Cannot open pipe for " << cmd;
    return 0;
  }
  if (fgets(buf, 1024, cmd_pipe) == nullptr) {
    PLOG(ERROR) << "Cannot read pipe for " << cmd;
    pclose(cmd_pipe);
    return 0;
  }

  pclose(cmd_pipe);
  return strtoul(buf, nullptr, 10);
}

static void ExecInMountNamespaceOf(pid_t pid,
                                   const std::function<void(pid_t)>& func) {
  const std::string my_path = "/proc/self/ns/mnt";
  android::base::unique_fd my_fd(open(my_path.c_str(), O_RDONLY | O_CLOEXEC));
  ASSERT_TRUE(my_fd.get() >= 0);

  const std::string target_path =
      std::string("/proc/") + std::to_string(pid) + "/ns/mnt";
  android::base::unique_fd target_fd(
      open(target_path.c_str(), O_RDONLY | O_CLOEXEC));
  ASSERT_TRUE(target_fd.get() >= 0);

  int res = setns(target_fd.get(), CLONE_NEWNS);
  ASSERT_NE(-1, res);

  func(pid);

  res = setns(my_fd.get(), CLONE_NEWNS);
  ASSERT_NE(-1, res);
}

// This test case is part of the ApexServiceTest suite to ensure that apexd is
// running when this test is executed.
TEST_F(ApexServiceTest, ApexdIsInSameMountNamespaceAsInit) {
  std::string ns_apexd;
  std::string ns_init;

  ExecInMountNamespaceOf(GetPidOf("apexd"), [&](pid_t /*pid*/) {
    bool res = android::base::Readlink("/proc/self/ns/mnt", &ns_apexd);
    ASSERT_TRUE(res);
  });

  ExecInMountNamespaceOf(1, [&](pid_t /*pid*/) {
    bool res = android::base::Readlink("/proc/self/ns/mnt", &ns_init);
    ASSERT_TRUE(res);
  });

  ASSERT_EQ(ns_apexd, ns_init);
}

// These are NOT exhaustive list of early processes be should be enough
static const std::vector<const std::string> kEarlyProcesses = {
    "vold",
    "logd",
};

// This test case is part of the ApexServiceTest suite to ensure that apexd is
// running when this test is executed.
TEST_F(ApexServiceTest, EarlyProcessesAreInDifferentMountNamespace) {
  std::string ns_apexd;

  ExecInMountNamespaceOf(GetPidOf("apexd"), [&](pid_t /*pid*/) {
    bool res = android::base::Readlink("/proc/self/ns/mnt", &ns_apexd);
    ASSERT_TRUE(res);
  });

  for (const auto& name : kEarlyProcesses) {
    std::string ns_early_process;
    ExecInMountNamespaceOf(GetPidOf(name), [&](pid_t /*pid*/) {
      bool res =
          android::base::Readlink("/proc/self/ns/mnt", &ns_early_process);
      ASSERT_TRUE(res);
    });
    ASSERT_NE(ns_apexd, ns_early_process);
  }
}

TEST(ApexdTest, ApexIsAPrivateMountPoint) {
  std::string mountinfo;
  ASSERT_TRUE(
      android::base::ReadFileToString("/proc/self/mountinfo", &mountinfo));
  bool found_apex_mountpoint = false;
  for (const auto& line : android::base::Split(mountinfo, "\n")) {
    std::vector<std::string> tokens = android::base::Split(line, " ");
    // line format:
    // mnt_id parent_mnt_id major:minor source target option propagation_type
    // ex) 33 260:19 / /apex rw,nosuid,nodev -
    if (tokens.size() >= 7 && tokens[4] == "/apex") {
      found_apex_mountpoint = true;
      // Make sure that propagation type is set to - which means private
      ASSERT_EQ("-", tokens[6]);
    }
  }
  ASSERT_TRUE(found_apex_mountpoint);
}

static const std::vector<const std::string> kEarlyApexes = {
    "/apex/com.android.runtime",
    "/apex/com.android.tzdata",
};

TEST(ApexdTest, ApexesAreActivatedForEarlyProcesses) {
  for (const auto& name : kEarlyProcesses) {
    pid_t pid = GetPidOf(name);
    const std::string path =
        std::string("/proc/") + std::to_string(pid) + "/mountinfo";
    std::string mountinfo;
    ASSERT_TRUE(android::base::ReadFileToString(path.c_str(), &mountinfo));

    std::unordered_set<std::string> mountpoints;
    for (const auto& line : android::base::Split(mountinfo, "\n")) {
      std::vector<std::string> tokens = android::base::Split(line, " ");
      // line format:
      // mnt_id parent_mnt_id major:minor source target option propagation_type
      // ex) 69 33 7:40 / /apex/com.android.conscrypt ro,nodev,noatime -
      if (tokens.size() >= 5) {
        // token[4] is the target mount point
        mountpoints.emplace(tokens[4]);
      }
    }
    for (const auto& apex_name : kEarlyApexes) {
      ASSERT_NE(mountpoints.end(), mountpoints.find(apex_name));
    }
  }
}

class ApexShimUpdateTest : public ApexServiceTest {
 protected:
  void SetUp() override {
    ApexServiceTest::SetUp();

    // Skip test if for some reason shim APEX is missing.
    std::vector<ApexInfo> list;
    ASSERT_TRUE(IsOk(service_->getAllPackages(&list)));
    bool found = std::any_of(list.begin(), list.end(), [](const auto& apex) {
      return apex.moduleName == "com.android.apex.cts.shim";
    });
    if (!found) {
      GTEST_SKIP() << "Can't find com.android.apex.cts.shim";
    }
  }
};

TEST_F(ApexShimUpdateTest, UpdateToV2Success) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.v2.apex"));

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));
}

TEST_F(ApexShimUpdateTest, SubmitStagedSessionFailureHasPreInstallHook) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.v2_with_pre_install_hook.apex"),
      "/data/app-staging/session_23", "staging_data_file");

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 23;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexShimUpdateTest, SubmitStagedSessionFailureHasPostInstallHook) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.v2_with_post_install_hook.apex"),
      "/data/app-staging/session_43", "staging_data_file");

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 43;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexShimUpdateTest, SubmitStagedSessionFailureAdditionalFile) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.v2_additional_file.apex"),
      "/data/app-staging/session_41", "staging_data_file");
  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 41;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexShimUpdateTest, SubmitStagedSessionFailureAdditionalFolder) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.v2_additional_folder.apex"),
      "/data/app-staging/session_42", "staging_data_file");
  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 42;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexShimUpdateTest, UpdateToV1Success) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.apex"));

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ASSERT_TRUE(IsOk(service_->stagePackages({installer.test_file})));
}

TEST_F(ApexShimUpdateTest, SubmitStagedSessionV1ShimApexSuccess) {
  PrepareTestApexForInstall installer(
      GetTestFile("com.android.apex.cts.shim.apex"),
      "/data/app-staging/session_97", "staging_data_file");
  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 97;
  ASSERT_TRUE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexServiceTest, SubmitStagedSessionCorruptApexFails) {
  PrepareTestApexForInstall installer(
      GetTestFile("apex.apexd_test_corrupt_apex.apex"),
      "/data/app-staging/session_57", "staging_data_file");

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 57;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexServiceTest, SubmitStagedSessionCorruptApexFailsB146895998) {
  PrepareTestApexForInstall installer(GetTestFile("corrupted_b146895998.apex"),
                                      "/data/app-staging/session_71",
                                      "staging_data_file");

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 71;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
}

TEST_F(ApexServiceTest, StageCorruptApexFailsB146895998) {
  PrepareTestApexForInstall installer(GetTestFile("corrupted_b146895998.apex"));

  if (!installer.Prepare()) {
    FAIL() << GetDebugStr(&installer);
  }

  ASSERT_FALSE(IsOk(service_->stagePackages({installer.test_file})));
}

TEST_F(ApexServiceTest,
       SubmitStagedSessionFailsManifestMismatchCleansUpHashtree) {
  PrepareTestApexForInstall installer(
      GetTestFile("apex.apexd_test_no_hashtree_manifest_mismatch.apex"),
      "/data/app-staging/session_83", "staging_data_file");
  if (!installer.Prepare()) {
    return;
  }

  ApexInfoList list;
  ApexSessionParams params;
  params.sessionId = 83;
  ASSERT_FALSE(IsOk(service_->submitStagedSession(params, &list)));
  std::string hashtree_file = std::string(kApexHashTreeDir) + "/" +
                              installer.package + "@" +
                              std::to_string(installer.version) + ".new";
  ASSERT_FALSE(RegularFileExists(hashtree_file));
}

class LogTestToLogcat : public ::testing::EmptyTestEventListener {
  void OnTestStart(const ::testing::TestInfo& test_info) override {
#ifdef __ANDROID__
    using base::LogId;
    using base::LogSeverity;
    using base::StringPrintf;
    base::LogdLogger l;
    std::string msg =
        StringPrintf("=== %s::%s (%s:%d)", test_info.test_suite_name(),
                     test_info.name(), test_info.file(), test_info.line());
    l(LogId::MAIN, LogSeverity::INFO, "ApexServiceTestCases", __FILE__,
      __LINE__, msg.c_str());
#else
    UNUSED(test_info);
#endif
  }
};

}  // namespace apex
}  // namespace android

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  android::base::InitLogging(argv, &android::base::StderrLogger);
  android::base::SetMinimumLogSeverity(android::base::VERBOSE);
  ::testing::UnitTest::GetInstance()->listeners().Append(
      new android::apex::LogTestToLogcat());
  return RUN_ALL_TESTS();
}
