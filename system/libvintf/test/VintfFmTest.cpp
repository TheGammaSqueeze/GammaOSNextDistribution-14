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

// This needs to be on top of the file to work.
#include "gmock-logging-compat.h"

#include <sysexits.h>

#include <filesystem>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <gtest/gtest.h>
#include <vintf/VintfFm.h>
#include <vintf/parse_xml.h>

#include "parse_xml_for_test.h"
#include "test_constants.h"

namespace android::vintf {

namespace {

using ::testing::_;
using ::testing::Eq;
using ::testing::Invoke;
using ::testing::MakeMatcher;
using ::testing::Matcher;
using ::testing::MatcherInterface;
using ::testing::MatchResultListener;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::StartsWith;
using ::testing::Test;
using ::testing::WithParamInterface;

using std::string_literals::operator""s;

static constexpr const char* gFakeRoot = "fake_root";
static constexpr const char* gFakeSystemArg = "/system:fake_root/system";
static constexpr const char* gFrameworkManifestPath = "fake_root/system/etc/vintf/manifest.xml";
static constexpr const char* gFrozenDir = "frozen";
static constexpr const char* gFrameworkManifest = R"(
<manifest version="2.0" type="framework">
  <hal>
    <name>android.frameworks.no_level</name>
    <transport>hwbinder</transport>
    <fqname>@1.0::IHidl/default</fqname>
  </hal>
  <hal max-level="1">
    <name>android.frameworks.level1</name>
    <transport>hwbinder</transport>
    <fqname>@1.0::IHidl/default</fqname>
  </hal>
  <hal max-level="2">
    <name>android.frameworks.level2</name>
    <transport>hwbinder</transport>
    <fqname>@1.0::IHidl/default</fqname>
  </hal>
  <hal format="aidl">
    <name>android.frameworks.no_level</name>
    <fqname>IAidl/default</fqname>
  </hal>
  <hal format="aidl" max-level="1">
    <name>android.frameworks.level1</name>
    <fqname>IAidl/default</fqname>
  </hal>
  <hal format="aidl" max-level="2">
    <name>android.frameworks.level2</name>
    <fqname>IAidl/default</fqname>
  </hal>
</manifest>)";

// clang-format off
static std::set<std::string> gInstances1 = {
    "android.frameworks.level1@1.0::IHidl/default",
    "android.frameworks.level1.IAidl/default (@1)",
    "android.frameworks.level2@1.0::IHidl/default",
    "android.frameworks.level2.IAidl/default (@1)",
    "android.frameworks.no_level@1.0::IHidl/default",
    "android.frameworks.no_level.IAidl/default (@1)",
};
static std::set<std::string> gInstances2 = {
    "android.frameworks.level2@1.0::IHidl/default",
    "android.frameworks.level2.IAidl/default (@1)",
    "android.frameworks.no_level@1.0::IHidl/default",
    "android.frameworks.no_level.IAidl/default (@1)",
};
static std::set<std::string> gInstances3 = {
    "android.frameworks.no_level@1.0::IHidl/default",
    "android.frameworks.no_level.IAidl/default (@1)",
};
// clang-format on

class MockWritableFileSystem : public WritableFileSystem {
   public:
    MOCK_METHOD(status_t, fetch, (const std::string&, std::string*, std::string*),
                (const override));
    MOCK_METHOD(status_t, listFiles, (const std::string&, std::vector<std::string>*, std::string*),
                (const override));
    MOCK_METHOD(status_t, modifiedTime, (const std::string&, TimeSpec*, std::string*),
                (const override));
    MOCK_METHOD(status_t, write, (const std::string&, const std::string&, std::string*),
                (const override));
    MOCK_METHOD(status_t, deleteFile, (const std::string&, std::string*), (const override));
};

// Helper to convert const char* array to char* array.
class Args {
   public:
    Args(const std::initializer_list<const char*>& list) {
        mStrings.reserve(list.size());
        mBuf.reserve(list.size());
        for (const char* item : list) {
            auto& str = mStrings.emplace_back(item);
            mBuf.push_back(str.data());
        }
    }
    int size() { return static_cast<int>(mBuf.size()); }
    char** get() { return mBuf.data(); }

   private:
    std::vector<std::string> mStrings;
    std::vector<char*> mBuf;
};

// Returns true if two paths are equivalent. Repeated '/' are omitted.
MATCHER_P(PathEq, expected, "") {
    return std::filesystem::path(arg) == std::filesystem::path(expected);
}

// CHeck if arg contains only the listed instances.
class MatrixInstanceMatcher : public MatcherInterface<const std::string&> {
   public:
    MatrixInstanceMatcher(std::set<std::string> expected) : mExpected(std::move(expected)) {}
    bool MatchAndExplain(const std::string& actual, MatchResultListener* listener) const override {
        CompatibilityMatrix actualMatrix;
        std::string error;
        if (!fromXml(&actualMatrix, actual, &error)) {
            *listener << "is not a valid compatibility matrix: " << error;
            return false;
        }
        std::set<std::string> actualInstances;
        actualMatrix.forEachInstance([&](const auto& matrixInstance) {
            actualInstances.emplace(
                matrixInstance.description(matrixInstance.versionRange().minVer()));
            return true;
        });
        if (actualInstances != mExpected) {
            *listener << "contains instances " << android::base::Join(actualInstances, ",\n");
            return false;
        }
        return true;
    }
    void DescribeTo(std::ostream* os) const override {
        *os << "contains only the following instances " << android::base::Join(mExpected, ",\n");
    }
    void DescribeNegationTo(std::ostream* os) const override {
        *os << "does not contain only the following instances "
            << android::base::Join(mExpected, ",\n");
    }

   private:
    std::set<std::string> mExpected;
};

Matcher<const std::string&> MatrixContains(std::set<std::string> expected) {
    return MakeMatcher(new MatrixInstanceMatcher(expected));
}

}  // namespace

class VintfFmTest : public Test {
   protected:
    void SetUp() override {
        auto unique_fs = std::make_unique<NiceMock<MockWritableFileSystem>>();
        fs = unique_fs.get();
        vintffm = std::make_unique<VintfFm>(std::move(unique_fs));

        ON_CALL(*fs, fetch(StartsWith(gFakeRoot), _, _)).WillByDefault(Return(NAME_NOT_FOUND));
        ON_CALL(*fs, fetch(PathEq(gFrameworkManifestPath), _, _))
            .WillByDefault(Invoke([](const auto&, auto* fetched, auto*) {
                *fetched = gFrameworkManifest;
                return OK;
            }));
    }

    void expectWriteMatrix(const std::string& path, std::set<std::string> instances) {
        EXPECT_CALL(*fs, write(PathEq(path), MatrixContains(std::move(instances)), _))
            .WillOnce(Return(OK));
    }

    MockWritableFileSystem* fs;
    std::unique_ptr<VintfFm> vintffm;
};

TEST_F(VintfFmTest, Update1) {
    expectWriteMatrix(gFrozenDir + "/1.xml"s, gInstances1);

    Args args({"vintffm", "--update", "--dirmap", gFakeSystemArg, "--level=1", gFrozenDir});
    EXPECT_EQ(EX_OK, vintffm->main(args.size(), args.get()));
}

TEST_F(VintfFmTest, Update2) {
    expectWriteMatrix(gFrozenDir + "/2.xml"s, gInstances2);

    Args args({"vintffm", "--update", "--dirmap", gFakeSystemArg, "--level=2", gFrozenDir});
    EXPECT_EQ(EX_OK, vintffm->main(args.size(), args.get()));
}

TEST_F(VintfFmTest, Update3) {
    expectWriteMatrix(gFrozenDir + "/3.xml"s, gInstances3);

    Args args({"vintffm", "--update", "--dirmap", gFakeSystemArg, "--level=3", gFrozenDir});
    EXPECT_EQ(EX_OK, vintffm->main(args.size(), args.get()));
}

std::string createMatrixHal(HalFormat format, const std::string& package) {
    std::vector<VersionRange> versionRanges;
    if (format != HalFormat::AIDL) {
        versionRanges.emplace_back(1, 0);
    }
    auto interface = format == HalFormat::AIDL ? "IAidl" : "IHidl";
    MatrixHal matrixHal{.format = format,
                        .name = package,
                        .versionRanges = versionRanges,
                        .optional = false,
                        .interfaces = {{interface, HalInterface{interface, {"default"}}}}};
    return toXml(matrixHal);
}

class VintfFmCheckTest : public VintfFmTest, public WithParamInterface<Level> {
   protected:
    void SetUp() override {
        VintfFmTest::SetUp();
        SetUpFiles();
        ON_CALL(*fs, listFiles(gFrozenDir, _, _))
            .WillByDefault(Invoke([this](const auto&, auto* out, auto*) {
                for (const auto& [path, content] : files) {
                    out->push_back(path);
                }
                return OK;
            }));

        ON_CALL(*fs, fetch(StartsWith(gFrozenDir + "/"s), _, _))
            .WillByDefault(Invoke([this](const auto& path, auto* fetched, auto*) {
                auto it = files.find(android::base::Basename(path));
                if (it == files.end()) {
                    return NAME_NOT_FOUND;
                }
                *fetched = it->second;
                return OK;
            }));
    }

    std::map<std::string, std::string> files;

   private:
    // Set up the following files:
    //   1.xml -> gXml1
    //   ...
    //   |current|.json -> gJson|current|.
    // |current| is the value of the variable |current|.
    // |level|.xml contains instances listed in gInstances|level|.
    void SetUpFiles() {
        auto current = GetParam();
        auto head = android::base::StringPrintf(R"(<compatibility-matrix %s type="device">)",
                                                kMetaVersionStr.c_str());
        auto tail = R"(</compatibility-matrix>)";

        auto xml3 = createMatrixHal(HalFormat::HIDL, "android.frameworks.no_level") +
                    createMatrixHal(HalFormat::AIDL, "android.frameworks.no_level");
        auto xml2 = xml3 + createMatrixHal(HalFormat::HIDL, "android.frameworks.level2") +
                    createMatrixHal(HalFormat::AIDL, "android.frameworks.level2");
        auto xml1 = xml2 + createMatrixHal(HalFormat::HIDL, "android.frameworks.level1") +
                    createMatrixHal(HalFormat::AIDL, "android.frameworks.level1");
        xml3 = head + xml3 + tail;
        xml2 = head + xml2 + tail;
        xml1 = head + xml1 + tail;

        std::map<Level, std::string> allFiles{
            {static_cast<Level>(1), xml1},
            {static_cast<Level>(2), xml2},
            {static_cast<Level>(3), xml3},
        };

        for (Level level = static_cast<Level>(1); level <= current;
             level = static_cast<Level>(static_cast<size_t>(level) + 1)) {
            files.emplace(to_string(level) + ".xml", allFiles[level]);
        }
    }
};

TEST_P(VintfFmCheckTest, Check) {
    Args args({"vintffm", "--check", "--dirmap", gFakeSystemArg, gFrozenDir});
    EXPECT_EQ(EX_OK, vintffm->main(args.size(), args.get()));
}

INSTANTIATE_TEST_SUITE_P(VintfFmTest, VintfFmCheckTest,
                         ::testing::Values(static_cast<Level>(1), static_cast<Level>(2),
                                           static_cast<Level>(3)));

}  // namespace android::vintf

int main(int argc, char** argv) {
    // Silence logs on host because they pollute the gtest output. VintfObject writes a lot
    // of INFO logs.
    android::base::SetMinimumLogSeverity(android::base::LogSeverity::WARNING);
    ::testing::InitGoogleMock(&argc, argv);
    return RUN_ALL_TESTS();
}
