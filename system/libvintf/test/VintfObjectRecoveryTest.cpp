/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <vintf/VintfObjectRecovery.h>
#include <vintf/parse_string.h>

#include "constants-private.h"
#include "test_constants.h"
#include "utils-fake.h"

using android::base::ConsumePrefix;
using android::base::StringPrintf;
using testing::_;
using testing::Combine;
using testing::Invoke;
using testing::IsEmpty;
using testing::Mock;
using testing::NiceMock;
using testing::StartsWith;
using testing::StrEq;
using testing::TestParamInfo;
using testing::UnorderedElementsAre;
using testing::ValuesIn;

namespace android::vintf::testing {

using details::kSystemManifest;
using details::kSystemManifestFragmentDir;
using details::MockFileSystemWithError;
using details::MockPropertyFetcher;
using details::MockRuntimeInfo;
using details::MockRuntimeInfoFactory;

template <typename T>
using StatusOr = std::variant<status_t, T>;

using DirectoryContent = std::map<std::string, StatusOr<std::string>>;

using OptionalType = std::optional<SchemaType>;
std::vector<OptionalType> OptionalTypes() {
    return {std::nullopt, SchemaType::DEVICE, SchemaType::FRAMEWORK};
}

std::string OptionalTypeToString(const OptionalType& optionalType) {
    if (!optionalType.has_value()) return "broken";
    return to_string(*optionalType);
}

constexpr const char* kMainFmt = R"(<manifest %s type="%s">
    <hal format="aidl">
        <name>android.hardware.main</name>
        <fqname>IMain/default</fqname>
    </hal>
</manifest>
)";

constexpr const char* kFragment1Fmt = R"(<manifest %s type="%s">
    <hal format="aidl">
        <name>android.hardware.fragment1</name>
        <fqname>IFragment/default</fqname>
    </hal>
</manifest>
)";

constexpr const char* kFragment2Fmt = R"(<manifest %s type="%s">
    <hal format="aidl">
        <name>android.hardware.fragment2</name>
        <fqname>IFragment/default</fqname>
    </hal>
</manifest>
)";

std::string formatManifest(const char* fmt, const OptionalType& optionalType) {
    if (!optionalType.has_value()) {
        return "(broken manifest)";
    }
    return StringPrintf(fmt, kMetaVersionStr.c_str(), to_string(*optionalType).c_str());
}

using VintfObjectRecoveryTestParam = std::tuple<OptionalType, OptionalType, OptionalType>;
class VintfObjectRecoveryTest : public ::testing::TestWithParam<VintfObjectRecoveryTestParam> {
   public:
    virtual void SetUp() {
        vintfObject = VintfObjectRecovery::Builder()
                          .setFileSystem(std::make_unique<NiceMock<MockFileSystemWithError>>())
                          .setRuntimeInfoFactory(std::make_unique<NiceMock<MockRuntimeInfoFactory>>(
                              std::make_shared<NiceMock<MockRuntimeInfo>>()))
                          .setPropertyFetcher(std::make_unique<NiceMock<MockPropertyFetcher>>())
                          .build<VintfObjectRecovery>();
        auto [mainType, fragType1, fragType2] = GetParam();
        main = formatManifest(kMainFmt, mainType);
        frag1 = formatManifest(kFragment1Fmt, fragType1);
        frag2 = formatManifest(kFragment2Fmt, fragType2);
    }
    virtual void TearDown() { Mock::VerifyAndClear(&fs()); }

    MockFileSystemWithError& fs() {
        return static_cast<MockFileSystemWithError&>(*vintfObject->getFileSystem());
    }

    void setUpManifests(const StatusOr<std::string>& mainContent,
                        const StatusOr<DirectoryContent>& frags) {
        // By default, no files exist in the file system.
        ON_CALL(fs(), listFiles(_, _, _)).WillByDefault(Return(NAME_NOT_FOUND));
        ON_CALL(fs(), fetch(_, _, _))
            .WillByDefault(Invoke([](const auto& path, auto*, auto* error) {
                if (error != nullptr) {
                    *error = "fetch " + path + ": cannot be found on empty filesystem: " +
                             statusToString(NAME_NOT_FOUND);
                }
                return NAME_NOT_FOUND;
            }));
        ON_CALL(fs(), fetch(StrEq(kSystemManifest), _, _))
            .WillByDefault(Invoke([=](const auto& path, auto* content, auto* error) -> status_t {
                if (std::holds_alternative<status_t>(mainContent)) {
                    if (error != nullptr) {
                        *error = "fetch " + path + ": set to return " +
                                 statusToString(std::get<status_t>(mainContent));
                    }
                    return std::get<status_t>(mainContent);
                }
                *content = std::get<std::string>(mainContent);
                return OK;
            }));
        ON_CALL(fs(), listFiles(StrEq(kSystemManifestFragmentDir), _, _))
            .WillByDefault(Invoke([=](const std::string& path, std::vector<std::string>* out,
                                      auto* error) -> status_t {
                if (std::holds_alternative<status_t>(frags)) {
                    if (error != nullptr) {
                        *error = "list " + path + ": set to return " +
                                 statusToString(std::get<status_t>(frags));
                    }
                    return std::get<status_t>(frags);
                }
                for (const auto& [name, statusOrFile] : std::get<DirectoryContent>(frags)) {
                    out->push_back(name);
                }
                return OK;
            }));
        ON_CALL(fs(), fetch(StartsWith(kSystemManifestFragmentDir), _, _))
            .WillByDefault(Invoke([=](const auto& path, auto* content, auto* error) -> status_t {
                if (std::holds_alternative<status_t>(frags)) {
                    if (error != nullptr) {
                        *error = "fetch " + path + ": for dir, set to return " +
                                 statusToString(std::get<status_t>(frags));
                    }
                    return std::get<status_t>(frags);
                }
                const auto& directoryContent = std::get<DirectoryContent>(frags);
                std::string_view subpath = path;
                bool consumed = ConsumePrefix(&subpath, kSystemManifestFragmentDir);
                EXPECT_TRUE(consumed)
                    << path << " does not start with " << kSystemManifestFragmentDir;
                auto it = directoryContent.find(std::string(subpath));
                if (it == directoryContent.end()) {
                    if (error != nullptr) {
                        *error = "fetch " + path +
                                 ": not in DirectoryContent: " + statusToString(NAME_NOT_FOUND);
                    }
                    return NAME_NOT_FOUND;
                }

                const auto& [name, statusOrFile] = *it;
                if (std::holds_alternative<status_t>(statusOrFile)) {
                    *error = "fetch " + path + ": for file, set to return " +
                             statusToString(std::get<status_t>(statusOrFile));
                    return std::get<status_t>(statusOrFile);
                }
                *content = std::get<std::string>(statusOrFile);
                return OK;
            }));
    }

    static std::string ParamToString(const TestParamInfo<ParamType>& info) {
        auto [mainType, fragType1, fragType2] = info.param;
        auto s = "main_" + OptionalTypeToString(mainType);
        s += "_frag1_" + OptionalTypeToString(fragType1);
        s += "_frag2_" + OptionalTypeToString(fragType2);
        return s;
    }

    std::unique_ptr<VintfObjectRecovery> vintfObject;
    std::string main;
    std::string frag1;
    std::string frag2;
};

TEST_P(VintfObjectRecoveryTest, Empty) {
    setUpManifests(NAME_NOT_FOUND, NAME_NOT_FOUND);
    auto manifest = vintfObject->getRecoveryHalManifest();
    ASSERT_NE(nullptr, manifest);
    auto hals = manifest->getHalNames();
    EXPECT_THAT(hals, IsEmpty());
}

TEST_P(VintfObjectRecoveryTest, InaccessibleMainManifest) {
    setUpManifests(UNKNOWN_ERROR, NAME_NOT_FOUND);
    auto manifest = vintfObject->getRecoveryHalManifest();
    EXPECT_EQ(nullptr, manifest);
}

TEST_P(VintfObjectRecoveryTest, MainManifestOnly) {
    auto [mainType, fragType1, fragType2] = GetParam();
    setUpManifests(main, NAME_NOT_FOUND);
    auto manifest = vintfObject->getRecoveryHalManifest();
    if (!mainType.has_value()) {  // main manifest is broken
        EXPECT_EQ(nullptr, manifest);
        return;
    }
    ASSERT_NE(nullptr, manifest);
    EXPECT_THAT(manifest->getHalNames(), UnorderedElementsAre("android.hardware.main"));
}

TEST_P(VintfObjectRecoveryTest, MainManifestAndDirectoryOnly) {
    auto [mainType, fragType1, fragType2] = GetParam();
    setUpManifests(main, {});
    auto manifest = vintfObject->getRecoveryHalManifest();
    if (!mainType.has_value()) {  // main manifest is broken
        EXPECT_EQ(nullptr, manifest);
        return;
    }
    ASSERT_NE(nullptr, manifest);
    EXPECT_THAT(manifest->getHalNames(), UnorderedElementsAre("android.hardware.main"));
}

TEST_P(VintfObjectRecoveryTest, MainManifestAndInaccessibleFragment) {
    setUpManifests(main, DirectoryContent{{"frag1.xml", UNKNOWN_ERROR}});
    auto manifest = vintfObject->getRecoveryHalManifest();
    EXPECT_EQ(nullptr, manifest);
}

TEST_P(VintfObjectRecoveryTest, MainManifestAndFragments) {
    auto [mainType, fragType1, fragType2] = GetParam();
    setUpManifests(main, DirectoryContent{{"frag1.xml", frag1}, {"frag2.xml", frag2}});
    auto manifest = vintfObject->getRecoveryHalManifest();
    if (!mainType.has_value() || !fragType1.has_value() || !fragType2.has_value()) {
        // some manifest(s) are broken
        EXPECT_EQ(nullptr, manifest);
        return;
    }
    ASSERT_NE(nullptr, manifest);
    EXPECT_THAT(manifest->getHalNames(),
                UnorderedElementsAre("android.hardware.main", "android.hardware.fragment1",
                                     "android.hardware.fragment2"));
}

TEST_P(VintfObjectRecoveryTest, InaccessibleDirectory) {
    setUpManifests(NAME_NOT_FOUND, UNKNOWN_ERROR);
    auto manifest = vintfObject->getRecoveryHalManifest();
    EXPECT_EQ(nullptr, manifest);
}

TEST_P(VintfObjectRecoveryTest, InaccessibleFragment) {
    setUpManifests(NAME_NOT_FOUND, DirectoryContent{{"frag1.xml", UNKNOWN_ERROR}});
    auto manifest = vintfObject->getRecoveryHalManifest();
    EXPECT_EQ(nullptr, manifest);
}

TEST_P(VintfObjectRecoveryTest, SomeInaccessibleFragment) {
    setUpManifests(NAME_NOT_FOUND,
                   DirectoryContent{{"frag1.xml", UNKNOWN_ERROR}, {"frag2.xml", frag2}});
    auto manifest = vintfObject->getRecoveryHalManifest();
    EXPECT_EQ(nullptr, manifest);
}

TEST_P(VintfObjectRecoveryTest, DirectoryOnly) {
    setUpManifests(NAME_NOT_FOUND, {});
    auto manifest = vintfObject->getRecoveryHalManifest();
    ASSERT_NE(nullptr, manifest);
    EXPECT_THAT(manifest->getHalNames(), IsEmpty());
}

TEST_P(VintfObjectRecoveryTest, FragmentsOnly) {
    auto [mainType, fragType1, fragType2] = GetParam();
    setUpManifests(NAME_NOT_FOUND, DirectoryContent{{"frag1.xml", frag1}, {"frag2.xml", frag2}});
    auto manifest = vintfObject->getRecoveryHalManifest();
    if (!fragType1.has_value() || !fragType2.has_value()) {
        // some manifest(s) are broken
        EXPECT_EQ(nullptr, manifest);
        return;
    }
    ASSERT_NE(nullptr, manifest);
    EXPECT_THAT(manifest->getHalNames(),
                UnorderedElementsAre("android.hardware.fragment1", "android.hardware.fragment2"));
}

INSTANTIATE_TEST_CASE_P(VintfObjectRecoveryTest, VintfObjectRecoveryTest,
                        Combine(ValuesIn(OptionalTypes()), ValuesIn(OptionalTypes()),
                                ValuesIn(OptionalTypes())),
                        VintfObjectRecoveryTest::ParamToString);

}  // namespace android::vintf::testing
