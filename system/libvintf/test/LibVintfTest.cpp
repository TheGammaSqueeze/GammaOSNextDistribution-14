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

#define LOG_TAG "LibVintfTest"

#include <algorithm>
#include <functional>
#include <vector>

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <vintf/CompatibilityMatrix.h>
#include <vintf/KernelConfigParser.h>
#include <vintf/VintfObject.h>
#include <vintf/parse_string.h>
#include <vintf/parse_xml.h>
#include "constants-private.h"
#include "parse_xml_for_test.h"
#include "parse_xml_internal.h"
#include "test_constants.h"
#include "utils.h"

using android::base::StringPrintf;
using ::testing::Combine;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::Optional;
using ::testing::Property;
using ::testing::Range;
using ::testing::SizeIs;
using ::testing::TestParamInfo;

using std::string_literals::operator""s;

namespace android {
namespace vintf {

#define EXPECT_IN(sub, str) EXPECT_THAT(str, HasSubstr(sub))

struct LibVintfTest : public ::testing::Test {
public:
    virtual void SetUp() override {
    }
    virtual void TearDown() override {
    }
    bool add(CompatibilityMatrix &cm, MatrixHal &&hal) {
        return cm.add(std::move(hal));
    }
    bool add(CompatibilityMatrix &cm, MatrixKernel &&kernel) {
        std::string error;
        bool success = cm.addKernel(std::move(kernel), &error);
        EXPECT_EQ(success, error == "") << "success: " << success << ", error: " << error;
        return success;
    }
    bool add(HalManifest& vm, ManifestHal&& hal) { return vm.add(std::move(hal), nullptr); }
    void addXmlFile(CompatibilityMatrix& cm, std::string name, VersionRange range) {
        MatrixXmlFile f;
        f.mName = name;
        f.mVersionRange = range;
        f.mFormat = XmlSchemaFormat::DTD;
        f.mOptional = true;
        cm.addXmlFile(std::move(f));
    }
    void set(CompatibilityMatrix &cm, Sepolicy &&sepolicy) {
        cm.framework.mSepolicy = sepolicy;
    }
    void set(CompatibilityMatrix &cm, SchemaType type) {
        cm.mType = type;
    }
    void set(CompatibilityMatrix &cm, VndkVersionRange &&range, std::set<std::string> &&libs) {
        cm.device.mVndk.mVersionRange = range;
        cm.device.mVndk.mLibraries = libs;
    }
    void setAvb(RuntimeInfo &ki, Version vbmeta, Version boot) {
        ki.mBootVbmetaAvbVersion = vbmeta;
        ki.mBootAvbVersion = boot;
    }
    void setAvb(CompatibilityMatrix &cm, Version &&avbVersion) {
        cm.framework.mAvbMetaVersion = avbVersion;
    }
    Version getAvb(CompatibilityMatrix &cm) {
        return cm.framework.mAvbMetaVersion;
    }
    const ManifestHal *getAnyHal(HalManifest &vm, const std::string &name) {
        return vm.getAnyHal(name);
    }
    MatrixHal *getAnyHal(CompatibilityMatrix &cm, const std::string &name) {
        return cm.getAnyHal(name);
    }
    ConstMultiMapValueIterable<std::string, ManifestHal> getHals(const HalManifest& vm) {
        return vm.getHals();
    }
    std::vector<const ManifestHal*> getHals(const HalManifest& vm, const std::string& name) {
        return vm.getHals(name);
    }
    std::vector<const MatrixHal*> getHals(const CompatibilityMatrix& cm, const std::string& name) {
        return cm.getHals(name);
    }
    bool isValid(const ManifestHal &mh) {
        return mh.isValid();
    }
    std::vector<MatrixKernel>& getKernels(CompatibilityMatrix& cm) { return cm.framework.mKernels; }
    bool addAllHalsAsOptional(CompatibilityMatrix* cm1, CompatibilityMatrix* cm2, std::string* e) {
        return cm1->addAllHalsAsOptional(cm2, e);
    }
    bool addAllXmlFilesAsOptional(CompatibilityMatrix* cm1, CompatibilityMatrix* cm2,
                                  std::string* e) {
        return cm1->addAllXmlFilesAsOptional(cm2, e);
    }
    std::set<std::string> checkUnusedHals(const HalManifest& m, const CompatibilityMatrix& cm) {
        return m.checkUnusedHals(cm, {});
    }
    Level getLevel(const KernelInfo& ki) { return ki.level(); }
    static status_t parseGkiKernelRelease(RuntimeInfo::FetchFlags flags,
                                          const std::string& kernelRelease, KernelVersion* version,
                                          Level* kernelLevel) {
        return RuntimeInfo::parseGkiKernelRelease(flags, kernelRelease, version, kernelLevel);
    }

    std::map<std::string, HalInterface> testHalInterfaces() {
        HalInterface intf("IFoo", {"default"});
        std::map<std::string, HalInterface> map;
        map[intf.name()] = intf;
        return map;
    }

    ManifestHal createManifestHal(HalFormat format, std::string name, TransportArch ta,
                                  const std::set<FqInstance>& fqInstances) {
        ManifestHal ret;
        ret.format = format;
        ret.name = std::move(name);
        ret.transportArch = ta;
        std::string error;
        EXPECT_TRUE(ret.insertInstances(fqInstances, false, &error)) << error;
        return ret;
    }

    HalManifest testDeviceManifestWithSepolicy(SepolicyVersion sepolicyVersion) {
        HalManifest vm;
        vm.mType = SchemaType::DEVICE;
        vm.device.mSepolicyVersion = sepolicyVersion;
        vm.add(createManifestHal(HalFormat::HIDL, "android.hardware.camera",
                                 {Transport::HWBINDER, Arch::ARCH_EMPTY},
                                 {
                                     *FqInstance::from(2, 0, "ICamera", "legacy/0"),
                                     *FqInstance::from(2, 0, "ICamera", "default"),
                                     *FqInstance::from(2, 0, "IBetterCamera", "camera"),
                                 }));
        vm.add(createManifestHal(HalFormat::HIDL, "android.hardware.nfc",
                                 {Transport::PASSTHROUGH, Arch::ARCH_32_64},
                                 std::set({*FqInstance::from(1, 0, "INfc", "default")})));

        return vm;
    }
    HalManifest testDeviceManifest() { return testDeviceManifestWithSepolicy({25, 0}); }
    HalManifest testDeviceManifestWithXmlFile() {
        HalManifest vm = testDeviceManifest();
        ManifestXmlFile xmlFile;
        xmlFile.mName = "media_profile";
        xmlFile.mVersion = {1, 0};
        vm.addXmlFile(std::move(xmlFile));
        return vm;
    }
    HalManifest testFrameworkManfiest() {
        HalManifest vm;
        vm.mType = SchemaType::FRAMEWORK;
        vm.add(createManifestHal(
            HalFormat::HIDL, "android.hidl.manager", {Transport::HWBINDER, Arch::ARCH_EMPTY},
            std::set({*FqInstance::from(1, 0, "IServiceManager", "default")})));
        Vndk vndk2505;
        vndk2505.mVersionRange = {25, 0, 5};
        vndk2505.mLibraries = {"libjpeg.so", "libbase.so"};
        Vndk vndk2513;
        vndk2513.mVersionRange = {25, 1, 3};
        vndk2513.mLibraries = {"libjpeg.so", "libbase.so", "libtinyxml2.so"};
        vm.framework.mVndks = { std::move(vndk2505), std::move(vndk2513) };

        return vm;
    }
    RuntimeInfo testRuntimeInfo() {
        RuntimeInfo info;
        info.mOsName = "Linux";
        info.mNodeName = "localhost";
        info.mOsRelease = "3.18.31-g936f9a479d0f";
        info.mOsVersion = "#4 SMP PREEMPT Wed Feb 1 18:10:52 PST 2017";
        info.mHardwareId = "aarch64";
        info.mKernelSepolicyVersion = 30;
        info.mKernel = testKernelInfo();
        setAvb(info, {2, 1}, {2, 1});
        return info;
    }
    KernelInfo testKernelInfo() {
        KernelInfo info;
        info.mVersion = {3, 18, 31};
        info.mConfigs = {{"CONFIG_64BIT", "y"},
                         {"CONFIG_ANDROID_BINDER_DEVICES", "\"binder,hwbinder\""},
                         {"CONFIG_ARCH_MMAP_RND_BITS", "24"},
                         {"CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES", "\"\""},
                         {"CONFIG_ILLEGAL_POINTER_VALUE", "0xdead000000000000"}};
        return info;
    }
    status_t fetchManifest(HalManifest& manifest, FileSystem* files, const std::string& path,
                           std::string* error) {
        return manifest.fetchAllInformation(files, path, error);
    }
};

// clang-format off

TEST_F(LibVintfTest, ArchOperatorOr) {
    Arch a = Arch::ARCH_EMPTY;
    a |= Arch::ARCH_32;
    EXPECT_EQ(Arch::ARCH_32, a);

    a |= Arch::ARCH_64;
    EXPECT_EQ(Arch::ARCH_32_64, a);

    a = Arch::ARCH_EMPTY;
    a |= Arch::ARCH_64;
    EXPECT_EQ(Arch::ARCH_64, a);
}

TEST_F(LibVintfTest, Stringify) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(dump(vm), "hidl/android.hardware.camera/hwbinder/:"
                        "hidl/android.hardware.nfc/passthrough32+64/");

    EXPECT_EQ(to_string(HalFormat::HIDL), "hidl");
    EXPECT_EQ(to_string(HalFormat::NATIVE), "native");

    VersionRange v(1, 2, 3);
    EXPECT_EQ(to_string(v), "1.2-3");
    VersionRange v2;
    EXPECT_TRUE(parse("1.2-3", &v2));
    EXPECT_EQ(v, v2);
    SepolicyVersionRange v3(4, std::nullopt);
    EXPECT_EQ(to_string(v3), "4");
    SepolicyVersionRange v4;
    EXPECT_TRUE(parse("4", &v4));
    EXPECT_EQ(v3, v4);
    SepolicyVersion v5(5, std::nullopt);
    EXPECT_EQ(to_string(v5), "5");
    SepolicyVersion v6;
    EXPECT_TRUE(parse("5", &v6));
    EXPECT_EQ(v5, v6);
}

TEST_F(LibVintfTest, GetTransport) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(Transport::HWBINDER, vm.getHidlTransport("android.hardware.camera",
            {2, 0}, "ICamera", "default"));
}

TEST_F(LibVintfTest, FutureManifestCompatible) {
    HalManifest expectedManifest;
    expectedManifest.add(createManifestHal(HalFormat::HIDL,
                                     "android.hardware.foo",
                                     {Transport::HWBINDER, Arch::ARCH_EMPTY},
                                     {*FqInstance::from(1, 0, "IFoo", "default")}));
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\" might_add=\"true\">\n"
        "    <hal format=\"hidl\" attribuet_might_be_added=\"value\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <tag_might_be_added/>\n"
        "</manifest>\n";
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_EQ(expectedManifest, manifest);
}

TEST_F(LibVintfTest, HalManifestConverter) {
    HalManifest vm = testDeviceManifest();
    std::string xml =
        toXml(vm, SerializeFlags::HALS_ONLY.enableSepolicy());
    EXPECT_EQ(xml,
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@2.0::IBetterCamera/camera</fqname>\n"
        "        <fqname>@2.0::ICamera/default</fqname>\n"
        "        <fqname>@2.0::ICamera/legacy/0</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport arch=\"32+64\">passthrough</transport>\n"
        "        <fqname>@1.0::INfc/default</fqname>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.0</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterWithVfrcSepolicy) {
    HalManifest vm = testDeviceManifestWithSepolicy({202404, std::nullopt});
    std::string xml =
        toXml(vm, SerializeFlags::HALS_ONLY.enableSepolicy());
    EXPECT_EQ(xml,
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@2.0::IBetterCamera/camera</fqname>\n"
        "        <fqname>@2.0::ICamera/default</fqname>\n"
        "        <fqname>@2.0::ICamera/legacy/0</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport arch=\"32+64\">passthrough</transport>\n"
        "        <fqname>@1.0::INfc/default</fqname>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>202404</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterWithInterface) {
    HalManifest vm = testDeviceManifest();
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport arch=\"32+64\">passthrough</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.0</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterFramework) {
    HalManifest vm = testFrameworkManfiest();
    std::string xml = toXml(vm, SerializeFlags::HALS_ONLY.enableVndk());
    EXPECT_EQ(xml,
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hidl.manager</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.0::IServiceManager/default</fqname>\n"
        "    </hal>\n"
        "    <vndk>\n"
        "        <version>25.0.5</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vndk>\n"
        "    <vndk>\n"
        "        <version>25.1.3</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "        <library>libtinyxml2.so</library>\n"
        "    </vndk>\n"
        "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterFrameworkWithInterface) {
    HalManifest vm = testFrameworkManfiest();
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hidl.manager</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IServiceManager</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <vndk>\n"
        "        <version>25.0.5</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vndk>\n"
        "    <vndk>\n"
        "        <version>25.1.3</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "        <library>libtinyxml2.so</library>\n"
        "    </vndk>\n"
        "</manifest>\n";
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestOptional) {
    HalManifest vm;
    EXPECT_TRUE(fromXml(&vm,
            "<manifest " + kMetaVersionStr + " type=\"device\"></manifest>"));
    EXPECT_TRUE(fromXml(&vm,
            "<manifest version=\"5.0\" type=\"device\">"
            "    <hal>"
            "        <name>android.hidl.manager</name>"
            "        <transport>hwbinder</transport>"
            "        <version>1.0</version>"
            "    </hal>"
            "</manifest>"));
    EXPECT_FALSE(fromXml(&vm,
            "<manifest version=\"5.0\" type=\"device\">"
            "    <hal>"
            "        <name>android.hidl.manager</name>"
            "        <version>1.0</version>"
            "    </hal>"
            "</manifest>"));
}

TEST_F(LibVintfTest, HalManifestNativeNoInstance) {
    std::string error;
    HalManifest vm;
    EXPECT_TRUE(fromXml(&vm,
                                      "<manifest " + kMetaVersionStr + " type=\"device\">"
                                      "    <hal format=\"native\">"
                                      "        <name>foo</name>"
                                      "        <version>1.0</version>"
                                      "    </hal>"
                                      "</manifest>", &error)) << error;
}

TEST_F(LibVintfTest, HalManifestNativeWithTransport) {
    std::string error;
    HalManifest vm;
    EXPECT_FALSE(fromXml(&vm,
                                       "<manifest " + kMetaVersionStr + " type=\"device\">"
                                       "    <hal format=\"native\">"
                                       "        <name>foo</name>"
                                       "        <version>1.0</version>"
                                       "        <transport>hwbinder</transport>"
                                       "    </hal>"
                                       "</manifest>", &error));
    EXPECT_THAT(error, HasSubstr("Native HAL 'foo' should not have <transport> defined"));
}

// clang-format on

TEST_F(LibVintfTest, HalManifestNativeInstancesWithInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <version>1.0</version>
                <interface>
                    <name>IFoo</name>
                    <instance>inst</instance>
                </interface>
           </hal>
        </manifest>
    )";

    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    manifest.forEachInstance([](const auto& manifestInstance) {
        EXPECT_EQ(manifestInstance.package(), "foo");
        EXPECT_EQ(manifestInstance.version(), Version(1, 0));
        EXPECT_EQ(manifestInstance.interface(), "IFoo");
        EXPECT_EQ(manifestInstance.instance(), "inst");
        return true;  // continue
    });
}

TEST_F(LibVintfTest, HalManifestNativeFqInstancesWithInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <fqname>@1.0::IFoo/inst</fqname>
           </hal>
        </manifest>
    )";

    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    manifest.forEachInstance([](const auto& manifestInstance) {
        EXPECT_EQ(manifestInstance.package(), "foo");
        EXPECT_EQ(manifestInstance.version(), Version(1, 0));
        EXPECT_EQ(manifestInstance.interface(), "IFoo");
        EXPECT_EQ(manifestInstance.instance(), "inst");
        return true;  // continue
    });
}

TEST_F(LibVintfTest, HalManifestNativeInstancesNoInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <version>1.0</version>
                <interface>
                    <instance>inst</instance>
                </interface>
           </hal>
        </manifest>
    )";

    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    manifest.forEachInstance([](const auto& manifestInstance) {
        EXPECT_EQ(manifestInstance.package(), "foo");
        EXPECT_EQ(manifestInstance.version(), Version(1, 0));
        EXPECT_EQ(manifestInstance.interface(), "");
        EXPECT_EQ(manifestInstance.instance(), "inst");
        return true;  // continue
    });
}

TEST_F(LibVintfTest, HalManifestNativeFqInstancesNoInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <fqname>@1.0/inst</fqname>
           </hal>
        </manifest>
    )";

    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    manifest.forEachInstance([](const auto& manifestInstance) {
        EXPECT_EQ(manifestInstance.package(), "foo");
        EXPECT_EQ(manifestInstance.version(), Version(1, 0));
        EXPECT_EQ(manifestInstance.interface(), "");
        EXPECT_EQ(manifestInstance.instance(), "inst");
        return true;  // continue
    });
}

TEST_F(LibVintfTest, QueryNativeInstances) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <version>1.0</version>
                <interface>
                    <instance>fooinst</instance>
                </interface>
           </hal>
            <hal format="native">
                <name>bar</name>
                <fqname>@1.0::I/barinst</fqname>
           </hal>
        </manifest>
    )";
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    EXPECT_EQ(manifest.getNativeInstances("foo"), std::set<std::string>{"fooinst"});
    EXPECT_TRUE(manifest.hasNativeInstance("foo", "fooinst"));
    EXPECT_EQ(manifest.getNativeInstances("bar"), std::set<std::string>{"barinst"});
    EXPECT_TRUE(manifest.hasNativeInstance("bar", "barinst"));

    EXPECT_EQ(manifest.getNativeInstances("baz"), std::set<std::string>{});
    EXPECT_FALSE(manifest.hasNativeInstance("baz", "bazinst"));
}

// clang-format off

TEST_F(LibVintfTest, HalManifestDuplicate) {
    HalManifest vm;
    EXPECT_FALSE(fromXml(&vm,
                                       "<manifest " + kMetaVersionStr + " type=\"device\">"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport>hwbinder</transport>"
                                       "        <version>1.0</version>"
                                       "        <version>1.1</version>"
                                       "    </hal>"
                                       "</manifest>"))
        << "Should not allow duplicated major version in <hal>";
    EXPECT_FALSE(fromXml(&vm,
                                       "<manifest " + kMetaVersionStr + " type=\"device\">"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport>hwbinder</transport>"
                                       "        <version>1.0</version>"
                                       "    </hal>"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport arch=\"32+64\">passthrough</transport>"
                                       "        <version>1.1</version>"
                                       "    </hal>"
                                       "</manifest>"))
        << "Should not allow duplicated major version across <hal>";
}

TEST_F(LibVintfTest, HalManifestGetTransport) {
    HalManifest vm;
    EXPECT_TRUE(fromXml(&vm,
                                      "<manifest " + kMetaVersionStr + " type=\"device\">"
                                      "    <hal>"
                                      "        <name>android.hidl.manager</name>"
                                      "        <transport>hwbinder</transport>"
                                      "        <version>1.0</version>"
                                      "        <interface>"
                                      "            <name>IServiceManager</name>"
                                      "            <instance>default</instance>"
                                      "        </interface>"
                                      "    </hal>"
                                      "    <hal>"
                                      "        <name>android.hidl.manager</name>"
                                      "        <transport arch=\"32+64\">passthrough</transport>"
                                      "        <version>2.1</version>"
                                      "        <interface>"
                                      "            <name>IServiceManager</name>"
                                      "            <instance>default</instance>"
                                      "        </interface>"
                                      "    </hal>"
                                      "</manifest>"));
    EXPECT_EQ(Transport::PASSTHROUGH,
              vm.getHidlTransport("android.hidl.manager", {2, 1}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::PASSTHROUGH,
              vm.getHidlTransport("android.hidl.manager", {2, 0}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::EMPTY,
              vm.getHidlTransport("android.hidl.manager", {2, 2}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::HWBINDER,
              vm.getHidlTransport("android.hidl.manager", {1, 0}, "IServiceManager", "default"));
}

TEST_F(LibVintfTest, HalManifestInstances) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getHidlInstances("android.hardware.camera", {2, 0}, "ICamera"),
              std::set<std::string>({"default", "legacy/0"}));
    EXPECT_EQ(vm.getHidlInstances("android.hardware.camera", {2, 0}, "IBetterCamera"),
              std::set<std::string>({"camera"}));
    EXPECT_EQ(vm.getHidlInstances("android.hardware.camera", {2, 0}, "INotExist"),
              std::set<std::string>({}));
    EXPECT_EQ(vm.getHidlInstances("android.hardware.nfc", {1, 0}, "INfc"),
              std::set<std::string>({"default"}));

    EXPECT_TRUE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "ICamera", "default"));
    EXPECT_TRUE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "ICamera", "legacy/0"));
    EXPECT_TRUE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "IBetterCamera", "camera"));
    EXPECT_TRUE(vm.hasHidlInstance("android.hardware.nfc", {1, 0}, "INfc", "default"));

    EXPECT_FALSE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "INotExist", "default"));
    EXPECT_FALSE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "ICamera", "notexist"));
    EXPECT_FALSE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "IBetterCamera", "default"));
    EXPECT_FALSE(vm.hasHidlInstance("android.hardware.camera", {2, 0}, "INotExist", "notexist"));
    EXPECT_FALSE(vm.hasHidlInstance("android.hardware.nfc", {1, 0}, "INfc", "notexist"));
}

TEST_F(LibVintfTest, VersionConverter) {
    Version v(3, 6);
    std::string xml = toXml(v);
    EXPECT_EQ(xml, "<version>3.6</version>\n");
    Version v2;
    EXPECT_TRUE(fromXml(&v2, xml));
    EXPECT_EQ(v, v2);

    SepolicyVersion v3(202404, std::nullopt);
    std::string xml2 = toXml(v3);
    EXPECT_EQ(xml2, "<version>202404</version>\n");
    SepolicyVersion v4;
    EXPECT_TRUE(fromXml(&v4, xml2));
    EXPECT_EQ(v3, v4);
}

static bool insert(std::map<std::string, HalInterface>* map, HalInterface&& intf) {
    std::string name{intf.name()};
    return map->emplace(std::move(name), std::move(intf)).second;
}

TEST_F(LibVintfTest, MatrixHalConverter) {
    MatrixHal mh{HalFormat::NATIVE, "android.hardware.camera",
            {{VersionRange(1,2,3), VersionRange(4,5,6)}},
            false /* optional */, false /* updatableViaApex */, {}};
    EXPECT_TRUE(insert(&mh.interfaces, {"IBetterCamera", {"default", "great"}}));
    EXPECT_TRUE(insert(&mh.interfaces, {"ICamera", {"default"}}));
    std::string xml = toXml(mh);
    EXPECT_EQ(xml,
        "<hal format=\"native\" optional=\"false\">\n"
        "    <name>android.hardware.camera</name>\n"
        "    <version>1.2-3</version>\n"
        "    <version>4.5-6</version>\n"
        "    <interface>\n"
        "        <name>IBetterCamera</name>\n"
        "        <instance>default</instance>\n"
        "        <instance>great</instance>\n"
        "    </interface>\n"
        "    <interface>\n"
        "        <name>ICamera</name>\n"
        "        <instance>default</instance>\n"
        "    </interface>\n"
        "</hal>\n");
    MatrixHal mh2;
    EXPECT_TRUE(fromXml(&mh2, xml));
    EXPECT_EQ(mh, mh2);
}

TEST_F(LibVintfTest, KernelConfigTypedValueConverter) {

    KernelConfigTypedValue converted;

    auto testOne = [] (const KernelConfigTypedValue &original,
                    const std::string &expectXml) {
        std::string xml;
        KernelConfigTypedValue converted;
        xml = toXml(original);
        EXPECT_EQ(xml, expectXml);
        EXPECT_TRUE(fromXml(&converted, xml));
        EXPECT_EQ(original, converted);
    };

    auto testParse = [] (const KernelConfigTypedValue &original,
                    const std::string &xml) {
        KernelConfigTypedValue converted;
        EXPECT_TRUE(fromXml(&converted, xml));
        EXPECT_EQ(original, converted);
    };

    testOne(KernelConfigTypedValue("stringvalue"),
        "<value type=\"string\">stringvalue</value>\n");
    testOne(KernelConfigTypedValue(""),
        "<value type=\"string\"></value>\n");

    testOne(KernelConfigTypedValue(Tristate::YES),
        "<value type=\"tristate\">y</value>\n");
    testOne(KernelConfigTypedValue(Tristate::NO),
        "<value type=\"tristate\">n</value>\n");
    testOne(KernelConfigTypedValue(Tristate::MODULE),
        "<value type=\"tristate\">m</value>\n");
    EXPECT_FALSE(fromXml(&converted,
        "<value type=\"tristate\">q</value>\n"));

    testOne(KernelConfigTypedValue(KernelConfigRangeValue{4, 20}),
        "<value type=\"range\">4-20</value>\n");
    testOne(KernelConfigTypedValue(KernelConfigRangeValue{0, UINT64_MAX}),
        "<value type=\"range\">0-18446744073709551615</value>\n");
    testParse(KernelConfigTypedValue(KernelConfigRangeValue{0, UINT64_MAX}),
            "<value type=\"range\">0x0-0xffffffffffffffff</value>\n");

    EXPECT_FALSE(fromXml(&converted,
            "<value type=\"int\">-18446744073709551616</value>\n"));

    testOne(KernelConfigTypedValue(INT64_MIN),
         "<value type=\"int\">-9223372036854775808</value>\n");
    testParse(KernelConfigTypedValue(INT64_MIN),
            "<value type=\"int\">0x8000000000000000</value>\n");
    testParse(KernelConfigTypedValue(INT64_MIN),
            "<value type=\"int\">-0X8000000000000000</value>\n");

    testParse(KernelConfigTypedValue(INT64_MIN + 1),
            "<value type=\"int\">-0X7FFFFFFFFFFFFFFF</value>\n");

    testParse(KernelConfigTypedValue(-0x50),
            "<value type=\"int\">-0x50</value>\n");

    testOne(KernelConfigTypedValue(0),
         "<value type=\"int\">0</value>\n");

    // Truncation for underflow.
    testParse(KernelConfigTypedValue(1),
            "<value type=\"int\">-0xffffffffffffffff</value>\n");
    testParse(KernelConfigTypedValue(1),
            "<value type=\"int\">-18446744073709551615</value>\n");

    testOne(KernelConfigTypedValue(INT64_MAX),
         "<value type=\"int\">9223372036854775807</value>\n");
    testParse(KernelConfigTypedValue(INT64_MAX),
            "<value type=\"int\">0x7FFFFFFFFFFFFFFF</value>\n");
    // Truncation for underflow.
    testParse(KernelConfigTypedValue(INT64_MAX),
            "<value type=\"int\">-9223372036854775809</value>\n");

    testParse(KernelConfigTypedValue(-1),
            "<value type=\"int\">18446744073709551615</value>\n");
    testParse(KernelConfigTypedValue(-1),
            "<value type=\"int\">0xffffffffffffffff</value>\n");

    EXPECT_FALSE(fromXml(&converted,
            "<value type=\"int\">18446744073709551616</value>\n"));
}

TEST_F(LibVintfTest, CompatibilityMatrixConverter) {
    CompatibilityMatrix cm;
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hardware.camera",
            {{VersionRange(1,2,3), VersionRange(4,5,6)}},
            false /* optional */,  false /* updatableViaApex */, testHalInterfaces()}));
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hardware.nfc",
            {{VersionRange(4,5,6), VersionRange(10,11,12)}},
            true /* optional */,  false /* updatableViaApex */, testHalInterfaces()}));
    EXPECT_TRUE(add(cm, MatrixKernel{KernelVersion(3, 18, 22),
            {KernelConfig{"CONFIG_FOO", Tristate::YES}, KernelConfig{"CONFIG_BAR", "stringvalue"}}}));
    EXPECT_TRUE(add(cm, MatrixKernel{KernelVersion(4, 4, 1),
            {KernelConfig{"CONFIG_BAZ", 20}, KernelConfig{"CONFIG_BAR", KernelConfigRangeValue{3, 5} }}}));
    set(cm, Sepolicy(30, {{25, 0}, {26, 0, 3}, {202404, std::nullopt}}));
    setAvb(cm, Version{2, 1});
    std::string xml = toXml(cm);
    EXPECT_EQ(xml,
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"native\" optional=\"false\">\n"
            "        <name>android.hardware.camera</name>\n"
            "        <version>1.2-3</version>\n"
            "        <version>4.5-6</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <hal format=\"native\" optional=\"true\">\n"
            "        <name>android.hardware.nfc</name>\n"
            "        <version>4.5-6</version>\n"
            "        <version>10.11-12</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <kernel version=\"3.18.22\">\n"
            "        <config>\n"
            "            <key>CONFIG_FOO</key>\n"
            "            <value type=\"tristate\">y</value>\n"
            "        </config>\n"
            "        <config>\n"
            "            <key>CONFIG_BAR</key>\n"
            "            <value type=\"string\">stringvalue</value>\n"
            "        </config>\n"
            "    </kernel>\n"
            "    <kernel version=\"4.4.1\">\n"
            "        <config>\n"
            "            <key>CONFIG_BAZ</key>\n"
            "            <value type=\"int\">20</value>\n"
            "        </config>\n"
            "        <config>\n"
            "            <key>CONFIG_BAR</key>\n"
            "            <value type=\"range\">3-5</value>\n"
            "        </config>\n"
            "    </kernel>\n"
            "    <sepolicy>\n"
            "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
            "        <sepolicy-version>25.0</sepolicy-version>\n"
            "        <sepolicy-version>26.0-3</sepolicy-version>\n"
            "        <sepolicy-version>202404</sepolicy-version>\n"
            "    </sepolicy>\n"
            "    <avb>\n"
            "        <vbmeta-version>2.1</vbmeta-version>\n"
            "    </avb>\n"
            "</compatibility-matrix>\n");
    CompatibilityMatrix cm2;
    EXPECT_TRUE(fromXml(&cm2, xml));
    EXPECT_EQ(cm, cm2);
}

TEST_F(LibVintfTest, DeviceCompatibilityMatrixCoverter) {
    CompatibilityMatrix cm;
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hidl.manager",
            {{VersionRange(1,0)}},
            false /* optional */,  false /* updatableViaApex */, testHalInterfaces()}));
    set(cm, SchemaType::DEVICE);
    set(cm, VndkVersionRange{25,0,1,5}, {"libjpeg.so", "libbase.so"});
    std::string xml = toXml(cm);
    EXPECT_EQ(xml,
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"native\" optional=\"false\">\n"
        "        <name>android.hidl.manager</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <vndk>\n"
        "        <version>25.0.1-5</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vndk>\n"
        "</compatibility-matrix>\n");
    CompatibilityMatrix cm2;
    EXPECT_TRUE(fromXml(&cm2, xml));
    EXPECT_EQ(cm, cm2);
}

// clang-format on

TEST_F(LibVintfTest, CompatibilityMatrixDefaultOptionalTrue) {
    auto xml = "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
            <hal format="aidl">
                <name>android.foo.bar</name>
                <version>1</version>
                <interface>
                    <name>IFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </compatibility-matrix>)";
    CompatibilityMatrix cm;
    EXPECT_TRUE(fromXml(&cm, xml));
    auto hal = getAnyHal(cm, "android.foo.bar");
    ASSERT_NE(nullptr, hal);
    EXPECT_TRUE(hal->optional) << "If optional is not specified, it should be true by default";
}

TEST_F(LibVintfTest, IsValid) {
    EXPECT_TRUE(isValid(ManifestHal()));

    auto invalidHal = createManifestHal(HalFormat::HIDL, "android.hardware.camera",
                                        {Transport::PASSTHROUGH, Arch::ARCH_32_64}, {});
    invalidHal.versions = {{Version(2, 0), Version(2, 1)}};

    EXPECT_FALSE(isValid(invalidHal));
    HalManifest vm2;
    EXPECT_FALSE(add(vm2, std::move(invalidHal)));
}
// clang-format off

TEST_F(LibVintfTest, HalManifestGetHalNames) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getHalNames(), std::set<std::string>(
                  {"android.hardware.camera", "android.hardware.nfc"}));
}

TEST_F(LibVintfTest, HalManifestGetAllHals) {
    HalManifest vm = testDeviceManifest();
    EXPECT_NE(getAnyHal(vm, "android.hardware.camera"), nullptr);
    EXPECT_EQ(getAnyHal(vm, "non-existent"), nullptr);

    std::vector<std::string> arr{"android.hardware.camera", "android.hardware.nfc"};
    size_t i = 0;
    for (const auto &hal : getHals(vm)) {
        EXPECT_EQ(hal.name, arr[i++]);
    }
}

// clang-format on
TEST_F(LibVintfTest, HalManifestGetHals) {
    HalManifest vm;

    EXPECT_TRUE(add(vm, createManifestHal(HalFormat::HIDL, "android.hardware.camera",
                                          {Transport::HWBINDER, Arch::ARCH_EMPTY},
                                          {
                                              *FqInstance::from(1, 2, "ICamera", "legacy/0"),
                                              *FqInstance::from(1, 2, "ICamera", "default"),
                                              *FqInstance::from(1, 2, "IBetterCamera", "camera"),
                                          })));
    EXPECT_TRUE(add(vm, createManifestHal(HalFormat::HIDL, "android.hardware.camera",
                                          {Transport::HWBINDER, Arch::ARCH_EMPTY},
                                          {
                                              *FqInstance::from(2, 0, "ICamera", "legacy/0"),
                                              *FqInstance::from(2, 0, "ICamera", "default"),
                                              *FqInstance::from(2, 0, "IBetterCamera", "camera"),
                                          })));

    EXPECT_TRUE(add(vm, createManifestHal(HalFormat::HIDL, "android.hardware.nfc",
                                          {Transport::PASSTHROUGH, Arch::ARCH_32_64},
                                          {*FqInstance::from(1, 0, "INfc", "default"),
                                           *FqInstance::from(2, 1, "INfc", "default")})));

    ManifestHal expectedCameraHalV1_2 = createManifestHal(
        HalFormat::HIDL, "android.hardware.camera", {Transport::HWBINDER, Arch::ARCH_EMPTY},
        {
            *FqInstance::from(1, 2, "ICamera", "legacy/0"),
            *FqInstance::from(1, 2, "ICamera", "default"),
            *FqInstance::from(1, 2, "IBetterCamera", "camera"),
        });
    ManifestHal expectedCameraHalV2_0 = createManifestHal(
        HalFormat::HIDL, "android.hardware.camera", {Transport::HWBINDER, Arch::ARCH_EMPTY},
        {
            *FqInstance::from(2, 0, "ICamera", "legacy/0"),
            *FqInstance::from(2, 0, "ICamera", "default"),
            *FqInstance::from(2, 0, "IBetterCamera", "camera"),
        });
    ManifestHal expectedNfcHal = createManifestHal(
        HalFormat::HIDL, "android.hardware.nfc", {Transport::PASSTHROUGH, Arch::ARCH_32_64},
        {*FqInstance::from(1, 0, "INfc", "default"), *FqInstance::from(2, 1, "INfc", "default")});

    auto cameraHals = getHals(vm, "android.hardware.camera");
    EXPECT_EQ((int)cameraHals.size(), 2);
    EXPECT_EQ(*cameraHals[0], expectedCameraHalV1_2);
    EXPECT_EQ(*cameraHals[1], expectedCameraHalV2_0);
    auto nfcHals = getHals(vm, "android.hardware.nfc");
    EXPECT_EQ((int)nfcHals.size(), 1);
    EXPECT_EQ(*nfcHals[0], expectedNfcHal);
}
// clang-format off

TEST_F(LibVintfTest, CompatibilityMatrixGetHals) {
    CompatibilityMatrix cm;
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE,
                                  "android.hardware.camera",
                                  {{VersionRange(1, 2, 3), VersionRange(4, 5, 6)}},
                                  false /* optional */,
                                  false /* updatableViaApex */,
                                  testHalInterfaces()}));
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE,
                                  "android.hardware.nfc",
                                  {{VersionRange(4, 5, 6), VersionRange(10, 11, 12)}},
                                  true /* optional */,
                                  false /* updatableViaApex */,
                                  testHalInterfaces()}));

    MatrixHal expectedCameraHal = MatrixHal{
        HalFormat::NATIVE,
        "android.hardware.camera",
        {{VersionRange(1, 2, 3), VersionRange(4, 5, 6)}},
        false /* optional */,
        false /* updatableViaApex */,
        testHalInterfaces(),
    };
    MatrixHal expectedNfcHal = MatrixHal{HalFormat::NATIVE,
                                         "android.hardware.nfc",
                                         {{VersionRange(4, 5, 6), VersionRange(10, 11, 12)}},
                                         true /* optional */,
                                         false /* updatableViaApex */,
                                         testHalInterfaces()};
    auto cameraHals = getHals(cm, "android.hardware.camera");
    EXPECT_EQ((int)cameraHals.size(), 1);
    EXPECT_EQ(*cameraHals[0], expectedCameraHal);
    auto nfcHals = getHals(cm, "android.hardware.nfc");
    EXPECT_EQ((int)nfcHals.size(), 1);
    EXPECT_EQ(*nfcHals[0], expectedNfcHal);
}

TEST_F(LibVintfTest, RuntimeInfo) {
    RuntimeInfo ki = testRuntimeInfo();
    using KernelConfigs = std::vector<KernelConfig>;
    const KernelConfigs configs {
            KernelConfig{"CONFIG_64BIT", Tristate::YES},
            KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", "binder,hwbinder"},
            KernelConfig{"CONFIG_ARCH_MMAP_RND_BITS", 24},
            KernelConfig{"CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES", ""},
            KernelConfig{"CONFIG_ILLEGAL_POINTER_VALUE", 0xdead000000000000},
            KernelConfig{"CONFIG_NOTEXIST", Tristate::NO},
    };

    auto testMatrix = [&] (MatrixKernel &&kernel) {
        CompatibilityMatrix cm;
        add(cm, std::move(kernel));
        set(cm, {30, {{25, 0}}});
        setAvb(cm, {2, 1});
        return cm;
    };

    std::string error;

    {
        MatrixKernel kernel(KernelVersion{4, 4, 1}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Kernel version shouldn't match";
    }

    {
        MatrixKernel kernel(KernelVersion{3, 18, 60}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Kernel version shouldn't match";
    }

    {
        MatrixKernel kernel(KernelVersion{3, 18, 22}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_TRUE(ki.checkCompatibility(cm, &error)) << error;
    }

    {
        MatrixKernel kernel(KernelVersion{3, 18, 22}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        set(cm, Sepolicy{22, {{25, 0}}});
        EXPECT_TRUE(ki.checkCompatibility(cm, &error)) << error;
        set(cm, Sepolicy{40, {{25, 0}}});
        EXPECT_FALSE(ki.checkCompatibility(cm, &error))
            << "kernel-sepolicy-version shouldn't match";
        EXPECT_IN("kernelSepolicyVersion = 30 but required >= 40", error);
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[0] = KernelConfig{"CONFIG_64BIT", Tristate::NO};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for tristate";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[0] = KernelConfig{"CONFIG_64BIT", 20};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Type shouldn't match";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[1] = KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", "binder"};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for string";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[1] = KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", Tristate::YES};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Type shouldn't match";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[2] = KernelConfig{"CONFIG_ARCH_MMAP_RND_BITS", 30};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for integer";
    }

    RuntimeInfo badAvb = testRuntimeInfo();
    CompatibilityMatrix cm = testMatrix(MatrixKernel(KernelVersion{3, 18, 31}, {}));
    {
        setAvb(badAvb, {1, 0}, {2, 1});
        EXPECT_FALSE(badAvb.checkCompatibility(cm, &error, CheckFlags::ENABLE_ALL_CHECKS));
        EXPECT_STREQ(error.c_str(), "Vbmeta version 1.0 does not match framework matrix 2.1");
    }
    {
        setAvb(badAvb, {2, 1}, {3, 0});
        EXPECT_FALSE(badAvb.checkCompatibility(cm, &error, CheckFlags::ENABLE_ALL_CHECKS));
    }
    {
        setAvb(badAvb, {2, 1}, {2, 3});
        EXPECT_TRUE(badAvb.checkCompatibility(cm, &error, CheckFlags::ENABLE_ALL_CHECKS));
    }
    {
        setAvb(badAvb, {2, 3}, {2, 1});
        EXPECT_TRUE(badAvb.checkCompatibility(cm, &error, CheckFlags::ENABLE_ALL_CHECKS));
    }
}

TEST_F(LibVintfTest, MissingAvb) {
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.31\"></kernel>"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "    </sepolicy>\n"
        "</compatibility-matrix>\n";
    CompatibilityMatrix cm;
    EXPECT_TRUE(fromXml(&cm, xml));
    EXPECT_EQ(getAvb(cm), Version(0, 0));
}

TEST_F(LibVintfTest, DisableAvb) {
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.31\"></kernel>"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb>\n"
        "        <vbmeta-version>1.0</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n";
    CompatibilityMatrix cm;
    EXPECT_TRUE(fromXml(&cm, xml));
    RuntimeInfo ki = testRuntimeInfo();
    std::string error;
    EXPECT_FALSE(ki.checkCompatibility(cm, &error, CheckFlags::ENABLE_ALL_CHECKS));
    EXPECT_STREQ(error.c_str(), "AVB version 2.1 does not match framework matrix 1.0");
    EXPECT_TRUE(ki.checkCompatibility(cm, &error, CheckFlags::DISABLE_AVB_CHECK)) << error;
}

// This is the test extracted from VINTF Object doc
TEST_F(LibVintfTest, HalCompat) {
    CompatibilityMatrix matrix;
    std::string error;

    std::string matrixXml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>1.0</version>\n"
            "        <version>3.1-2</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>specific</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>2.0</version>\n"
            "        <interface>\n"
            "            <name>IBar</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <sepolicy>\n"
            "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
            "        <sepolicy-version>25.5</sepolicy-version>\n"
            "    </sepolicy>\n"
            "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&matrix, matrixXml, &error)) << error;

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";

        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
                << "should not be compatible because IBar is missing";
    }

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "should not be compatible because IFoo/specific is missing";
    }

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>3.3</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>3.2</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
                << "should not be compatible even though @1.0::IFoo/default "
                << "and @3.2::IFoo/specific present";
    }

    {
        std::string manifestXml =
                "<manifest " + kMetaVersionStr + " type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }
}

TEST_F(LibVintfTest, FullCompat) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>3.5</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.5</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";

    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <version>2.0-5</version>\n"
        "        <version>3.4-16</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <version>1.0</version>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "        <sepolicy-version>26.0-3</sepolicy-version>\n"
        "        <sepolicy-version>202404</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb>\n"
        "        <vbmeta-version>2.1</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n";

    HalManifest manifest;
    CompatibilityMatrix matrix;
    std::string error;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;

    // some smaller test cases
    matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <version>3.4</version>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";
    matrix = {};
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    MatrixHal *camera = getAnyHal(matrix, "android.hardware.camera");
    EXPECT_NE(camera, nullptr);
    camera->versionRanges[0] = {3, 5};
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    camera->versionRanges[0] = {3, 6};
    EXPECT_FALSE(manifest.checkCompatibility(matrix));

    // reset it
    matrix = {};
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    set(matrix, Sepolicy{30, {{26, 0}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));
    set(matrix, Sepolicy{30, {{25, 6}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));
    set(matrix, Sepolicy{30, {{25, 4}}});
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    set(matrix, Sepolicy{30, {{202404, std::nullopt}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));

    // vFRC sepolicy test cases
    manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <sepolicy>\n"
        "        <version>202404</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    set(matrix, Sepolicy{30, {{202404, std::nullopt}}});
    EXPECT_TRUE(manifest.checkCompatibility(matrix)) << error;
    set(matrix, Sepolicy{30, {{202404, 0}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix)) << error;
    set(matrix, Sepolicy{30, {{202504, std::nullopt}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));
}

// clang-format on

TEST_F(LibVintfTest, ApexInterfaceShouldBeOkayWithoutApexInfoList) {
    details::FileSystemNoOp fs;
    details::PropertyFetcherNoOp pf;
    details::Apex apex;
    ASSERT_FALSE(apex.HasUpdate(&fs, &pf));
    std::vector<std::string> dirs;
    ASSERT_EQ(OK, apex.DeviceVintfDirs(&fs, &pf, &dirs, nullptr));
}

struct NativeHalCompatTestParam {
    std::string matrixXml;
    std::string manifestXml;
    bool compatible;
    std::string expectedError;
};

class NativeHalCompatTest : public LibVintfTest,
                            public ::testing::WithParamInterface<NativeHalCompatTestParam> {
   public:
    static std::vector<NativeHalCompatTestParam> createParams() {
        std::string matrixIntf = "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
                <hal format="native" optional="false">
                    <name>foo</name>
                    <version>1.0</version>
                    <interface>
                        <name>IFoo</name>
                        <instance>default</instance>
                    </interface>
               </hal>
            </compatibility-matrix>
        )";
        std::string matrixNoIntf = "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
                <hal format="native" optional="false">
                    <name>foo</name>
                    <version>1.0</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
               </hal>
            </compatibility-matrix>
        )";
        std::string matrixNoInst = "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
                <hal format="native" optional="false">
                    <name>foo</name>
                    <version>1.0</version>
               </hal>
            </compatibility-matrix>
        )";
        std::string manifestFqnameIntf = "<manifest " + kMetaVersionStr + R"( type="framework">
                <hal format="native">
                    <name>foo</name>
                    <fqname>@1.0::IFoo/default</fqname>
               </hal>
            </manifest>
        )";
        std::string manifestLegacyIntf = "<manifest " + kMetaVersionStr + R"( type="framework">
                <hal format="native">
                    <name>foo</name>
                    <version>1.0</version>
                    <interface>
                        <name>IFoo</name>
                        <instance>default</instance>
                    </interface>
               </hal>
            </manifest>
        )";
        std::string manifestFqnameNoIntf = "<manifest " + kMetaVersionStr + R"( type="framework">
                <hal format="native">
                    <name>foo</name>
                    <fqname>@1.0/default</fqname>
               </hal>
            </manifest>
        )";
        std::string manifestLegacyNoIntf = "<manifest " + kMetaVersionStr + R"( type="framework">
                <hal format="native">
                    <name>foo</name>
                    <version>1.0</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
               </hal>
            </manifest>
        )";
        std::string manifestNoInst = "<manifest " + kMetaVersionStr + R"( type="framework">
                <hal format="native">
                    <name>foo</name>
                    <version>1.0</version>
               </hal>
            </manifest>
        )";

        std::vector<NativeHalCompatTestParam> ret;

        // If the matrix specifies interface name, the manifest must also do.
        ret.emplace_back(NativeHalCompatTestParam{matrixIntf, manifestFqnameIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixIntf, manifestLegacyIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixIntf, manifestFqnameNoIntf, false,
                                                  "required: @1.0::IFoo/default"});
        ret.emplace_back(NativeHalCompatTestParam{matrixIntf, manifestLegacyNoIntf, false,
                                                  "required: @1.0::IFoo/default"});
        ret.emplace_back(NativeHalCompatTestParam{matrixIntf, manifestNoInst, false,
                                                  "required: @1.0::IFoo/default"});

        // If the matrix does not specify an interface name, the manifest must not do that either.
        ret.emplace_back(NativeHalCompatTestParam{matrixNoIntf, manifestFqnameIntf, false,
                                                  "required: @1.0/default"});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoIntf, manifestLegacyIntf, false,
                                                  "required: @1.0/default"});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoIntf, manifestFqnameNoIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoIntf, manifestLegacyNoIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoIntf, manifestNoInst, false,
                                                  "required: @1.0/default"});

        // If the matrix does not specify interface name nor instances, the manifest may either
        // provide instances of that version, or just a version number with no instances.
        ret.emplace_back(NativeHalCompatTestParam{matrixNoInst, manifestFqnameIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoInst, manifestLegacyIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoInst, manifestFqnameNoIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoInst, manifestLegacyNoIntf, true, ""});
        ret.emplace_back(NativeHalCompatTestParam{matrixNoInst, manifestNoInst, true, ""});

        return ret;
    }
};

TEST_P(NativeHalCompatTest, Compat) {
    auto params = GetParam();
    std::string error;
    HalManifest manifest;
    ASSERT_TRUE(fromXml(&manifest, params.manifestXml, &error)) << error;
    CompatibilityMatrix matrix;
    ASSERT_TRUE(fromXml(&matrix, params.matrixXml, &error)) << error;
    EXPECT_EQ(params.compatible, manifest.checkCompatibility(matrix, &error)) << error;
    if (!params.expectedError.empty()) {
        EXPECT_THAT(error, HasSubstr(params.expectedError));
    } else {
        EXPECT_THAT(error, IsEmpty());
    }
}

INSTANTIATE_TEST_CASE_P(LibVintfTest, NativeHalCompatTest,
                        ::testing::ValuesIn(NativeHalCompatTest::createParams()));

// clang-format off

/////////////////// xmlfile tests

TEST_F(LibVintfTest, HalManifestConverterXmlFile) {
    HalManifest vm = testDeviceManifestWithXmlFile();
    std::string xml = toXml(
        vm, SerializeFlags::HALS_ONLY.enableSepolicy().enableXmlFiles());
    EXPECT_EQ(xml,
              "<manifest " + kMetaVersionStr + " type=\"device\">\n"
              "    <hal format=\"hidl\">\n"
              "        <name>android.hardware.camera</name>\n"
              "        <transport>hwbinder</transport>\n"
              "        <fqname>@2.0::IBetterCamera/camera</fqname>\n"
              "        <fqname>@2.0::ICamera/default</fqname>\n"
              "        <fqname>@2.0::ICamera/legacy/0</fqname>\n"
              "    </hal>\n"
              "    <hal format=\"hidl\">\n"
              "        <name>android.hardware.nfc</name>\n"
              "        <transport arch=\"32+64\">passthrough</transport>\n"
              "        <fqname>@1.0::INfc/default</fqname>\n"
              "    </hal>\n"
              "    <sepolicy>\n"
              "        <version>25.0</version>\n"
              "    </sepolicy>\n"
              "    <xmlfile>\n"
              "        <name>media_profile</name>\n"
              "        <version>1.0</version>\n"
              "    </xmlfile>\n"
              "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterXmlFileWithInterface) {
    HalManifest vm = testDeviceManifestWithXmlFile();
    std::string xml =
              "<manifest " + kMetaVersionStr + " type=\"device\">\n"
              "    <hal format=\"hidl\">\n"
              "        <name>android.hardware.camera</name>\n"
              "        <transport>hwbinder</transport>\n"
              "        <version>2.0</version>\n"
              "        <interface>\n"
              "            <name>IBetterCamera</name>\n"
              "            <instance>camera</instance>\n"
              "        </interface>\n"
              "        <interface>\n"
              "            <name>ICamera</name>\n"
              "            <instance>default</instance>\n"
              "            <instance>legacy/0</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "    <hal format=\"hidl\">\n"
              "        <name>android.hardware.nfc</name>\n"
              "        <transport arch=\"32+64\">passthrough</transport>\n"
              "        <version>1.0</version>\n"
              "        <interface>\n"
              "            <name>INfc</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "    <sepolicy>\n"
              "        <version>25.0</version>\n"
              "    </sepolicy>\n"
              "    <xmlfile>\n"
              "        <name>media_profile</name>\n"
              "        <version>1.0</version>\n"
              "    </xmlfile>\n"
              "</manifest>\n";
    HalManifest vm2;
    EXPECT_TRUE(fromXml(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, CompatibilityMatrixConverterXmlFile) {
    CompatibilityMatrix cm;
    addXmlFile(cm, "media_profile", {1, 0});
    std::string xml = toXml(cm, SerializeFlags::XMLFILES_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
              "    <xmlfile format=\"dtd\" optional=\"true\">\n"
              "        <name>media_profile</name>\n"
              "        <version>1.0</version>\n"
              "    </xmlfile>\n"
              "</compatibility-matrix>\n");
    CompatibilityMatrix cm2;
    EXPECT_TRUE(fromXml(&cm2, xml));
    EXPECT_EQ(cm, cm2);
}

TEST_F(LibVintfTest, CompatibilityMatrixConverterXmlFile2) {
    std::string error;
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <xmlfile format=\"dtd\" optional=\"false\">\n"
        "        <name>media_profile</name>\n"
        "        <version>1.0</version>\n"
        "    </xmlfile>\n"
        "</compatibility-matrix>\n";
    CompatibilityMatrix cm;
    EXPECT_FALSE(fromXml(&cm, xml, &error));
    EXPECT_EQ("compatibility-matrix.xmlfile entry media_profile has to be optional for "
              "compatibility matrix version 1.0", error);
}

TEST_F(LibVintfTest, ManifestXmlFilePathDevice) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">"
        "    <xmlfile>"
        "        <name>media_profile</name>"
        "        <version>1.0</version>"
        "    </xmlfile>"
        "</manifest>";
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_EQ(manifest.getXmlFilePath("media_profile", {1, 0}),
              "/vendor/etc/media_profile_V1_0.xml");
}

TEST_F(LibVintfTest, ManifestXmlFilePathFramework) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">"
        "    <xmlfile>"
        "        <name>media_profile</name>"
        "        <version>1.0</version>"
        "    </xmlfile>"
        "</manifest>";
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_EQ(manifest.getXmlFilePath("media_profile", {1, 0}),
              "/system/etc/media_profile_V1_0.xml");
}

TEST_F(LibVintfTest, ManifestXmlFilePathOverride) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">"
        "    <xmlfile>"
        "        <name>media_profile</name>"
        "        <version>1.0</version>"
        "        <path>/vendor/etc/foo.xml</path>"
        "    </xmlfile>"
        "</manifest>";
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_EQ(manifest.getXmlFilePath("media_profile", {1, 0}), "/vendor/etc/foo.xml");
}

TEST_F(LibVintfTest, ManifestXmlFilePathMissing) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">"
        "    <xmlfile>"
        "        <name>media_profile</name>"
        "        <version>1.1</version>"
        "    </xmlfile>"
        "</manifest>";
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, manifestXml));
    EXPECT_EQ(manifest.getXmlFilePath("media_profile", {1, 0}), "");
}

TEST_F(LibVintfTest, MatrixXmlFilePathFramework) {
    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">"
        "    <xmlfile format=\"dtd\" optional=\"true\">"
        "        <name>media_profile</name>"
        "        <version>2.0-1</version>"
        "    </xmlfile>"
        "</compatibility-matrix>";
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_EQ(matrix.getXmlSchemaPath("media_profile", {2, 1}),
              "/system/etc/media_profile_V2_1.dtd");
}

TEST_F(LibVintfTest, MatrixXmlFilePathDevice) {
    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">"
        "    <xmlfile format=\"xsd\" optional=\"true\">"
        "        <name>media_profile</name>"
        "        <version>2.0-1</version>"
        "    </xmlfile>"
        "</compatibility-matrix>";
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_EQ(matrix.getXmlSchemaPath("media_profile", {2, 0}),
              "/vendor/etc/media_profile_V2_1.xsd");
}

TEST_F(LibVintfTest, MatrixXmlFilePathOverride) {
    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">"
        "    <xmlfile format=\"xsd\" optional=\"true\">"
        "        <name>media_profile</name>"
        "        <version>2.0-1</version>"
        "        <path>/system/etc/foo.xsd</path>"
        "    </xmlfile>"
        "</compatibility-matrix>";
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_EQ(matrix.getXmlSchemaPath("media_profile", {2, 0}), "/system/etc/foo.xsd");
}

TEST_F(LibVintfTest, MatrixXmlFilePathMissing) {
    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">"
        "    <xmlfile format=\"dtd\" optional=\"true\">"
        "        <name>media_profile</name>"
        "        <version>2.1</version>"
        "    </xmlfile>"
        "</compatibility-matrix>";
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, matrixXml));
    EXPECT_EQ(matrix.getXmlSchemaPath("media_profile", {2, 0}), "");
}

std::pair<KernelConfigParser, status_t> processData(const std::string& data, bool processComments,
                                                    bool relaxedFormat = false) {
    KernelConfigParser parser(processComments, relaxedFormat);
    const char* p = data.c_str();
    size_t n = 0;
    size_t chunkSize;
    status_t status = OK;
    for (; n < data.size(); p += chunkSize, n += chunkSize) {
        chunkSize = std::min<size_t>(5, data.size() - n);
        if ((status = parser.process(p, chunkSize)) != OK) {
            break;
        }
    }
    return {std::move(parser), status};
}

TEST_F(LibVintfTest, KernelConfigParser) {
    // usage in /proc/config.gz
    const std::string data =
        "# CONFIG_NOT_SET is not set\n"
        "CONFIG_ONE=1\n"
        "CONFIG_Y=y\n"
        "CONFIG_STR=\"string\"\n";
    auto pair = processData(data, false /* processComments */);
    ASSERT_EQ(OK, pair.second) << pair.first.error();
    const auto& configs = pair.first.configs();

    EXPECT_EQ(configs.find("CONFIG_ONE")->second, "1");
    EXPECT_EQ(configs.find("CONFIG_Y")->second, "y");
    EXPECT_EQ(configs.find("CONFIG_STR")->second, "\"string\"");
    EXPECT_EQ(configs.find("CONFIG_NOT_SET"), configs.end());
}

TEST_F(LibVintfTest, KernelConfigParser2) {
    // usage in android-base.config
    const std::string data =
        "# CONFIG_NOT_SET is not set\n"
        "CONFIG_ONE=1\n"
        "CONFIG_Y=y\n"
        "CONFIG_STR=string\n"
        "# ignore_thiscomment\n"
        "# CONFIG_NOT_SET2 is not set\n";
    auto pair = processData(data, true /* processComments */);
    ASSERT_EQ(OK, pair.second) << pair.first.error();
    const auto& configs = pair.first.configs();

    EXPECT_EQ(configs.find("CONFIG_ONE")->second, "1");
    EXPECT_EQ(configs.find("CONFIG_Y")->second, "y");
    EXPECT_EQ(configs.find("CONFIG_STR")->second, "string");
    EXPECT_EQ(configs.find("CONFIG_NOT_SET")->second, "n");
    EXPECT_EQ(configs.find("CONFIG_NOT_SET2")->second, "n");
}

TEST_F(LibVintfTest, KernelConfigParserSpace) {
    // usage in android-base.config
    const std::string data =
        "   #   CONFIG_NOT_SET is not set   \n"
        "  CONFIG_ONE=1   # 'tis a one!\n"
        " CONFIG_TWO=2 #'tis a two!   \n"
        " CONFIG_THREE=3#'tis a three!   \n"
        " CONFIG_233=233#'tis a three!   \n"
        "#yey! random comments\n"
        "CONFIG_Y=y   \n"
        " CONFIG_YES=y#YES!   \n"
        "CONFIG_STR=string\n"
        "CONFIG_HELLO=hello world!  #still works\n"
        "CONFIG_WORLD=hello world!       \n"
        "CONFIG_GOOD   =   good morning!  #comments here\n"
        "    CONFIG_MORNING   =   good morning!  \n";
    auto pair = processData(data, true /* processComments */, true /* relaxedFormat */);
    ASSERT_EQ(OK, pair.second) << pair.first.error();
    const auto& configs = pair.first.configs();

    EXPECT_EQ(configs.find("CONFIG_ONE")->second, "1");
    EXPECT_EQ(configs.find("CONFIG_TWO")->second, "2");
    EXPECT_EQ(configs.find("CONFIG_THREE")->second, "3");
    EXPECT_EQ(configs.find("CONFIG_Y")->second, "y");
    EXPECT_EQ(configs.find("CONFIG_STR")->second, "string");
    EXPECT_EQ(configs.find("CONFIG_HELLO")->second, "hello world!")
        << "Value should be \"hello world!\" without trailing spaces";
    EXPECT_EQ(configs.find("CONFIG_WORLD")->second, "hello world!")
        << "Value should be \"hello world!\" without trailing spaces";
    EXPECT_EQ(configs.find("CONFIG_GOOD")->second, "good morning!")
        << "Value should be \"good morning!\" without leading or trailing spaces";
    EXPECT_EQ(configs.find("CONFIG_MORNING")->second, "good morning!")
        << "Value should be \"good morning!\" without leading or trailing spaces";
    EXPECT_EQ(configs.find("CONFIG_NOT_SET")->second, "n");
}

TEST_F(LibVintfTest, NetutilsWrapperMatrix) {
    std::string matrixXml;
    CompatibilityMatrix matrix;
    std::string error;

    matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">"
        "    <hal format=\"native\" optional=\"false\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.0</version>"
        "    </hal>"
        "</compatibility-matrix>";
    EXPECT_TRUE(fromXml(&matrix, matrixXml, &error)) << error;

// only host libvintf hardcodes netutils-wrapper version requirements
#ifndef LIBVINTF_TARGET

    matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">"
        "    <hal format=\"native\" optional=\"false\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.0-1</version>"
        "    </hal>"
        "</compatibility-matrix>";
    EXPECT_FALSE(fromXml(&matrix, matrixXml, &error));
    EXPECT_THAT(error, HasSubstr(
        "netutils-wrapper HAL must specify exactly one version x.0, but a range is provided. "
        "Perhaps you mean '1.0'?"));

    matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">"
        "    <hal format=\"native\" optional=\"false\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.1</version>"
        "    </hal>"
        "</compatibility-matrix>";
    EXPECT_FALSE(fromXml(&matrix, matrixXml, &error));
    EXPECT_THAT(error, HasSubstr(
        "netutils-wrapper HAL must specify exactly one version x.0, but minor version is not 0. "
        "Perhaps you mean '1.0'?"));

    matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">"
        "    <hal format=\"native\" optional=\"false\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.0</version>"
        "        <version>2.0</version>"
        "    </hal>"
        "</compatibility-matrix>";
    EXPECT_FALSE(fromXml(&matrix, matrixXml, &error));
    EXPECT_THAT(error, HasSubstr(
        "netutils-wrapper HAL must specify exactly one version x.0, but multiple <version> element "
        "is specified."));

#endif  // LIBVINTF_TARGET
}

TEST_F(LibVintfTest, NetutilsWrapperManifest) {
    std::string manifestXml;
    HalManifest manifest;
    std::string error;

    manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">"
        "    <hal format=\"native\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.0</version>"
        "        <version>2.0</version>"
        "    </hal>"
        "</manifest>";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;

// only host libvintf hardcodes netutils-wrapper version requirements
#ifndef LIBVINTF_TARGET

    manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">"
        "    <hal format=\"native\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.1</version>"
        "    </hal>"
        "</manifest>";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_THAT(error, HasSubstr(
        "netutils-wrapper HAL must specify exactly one version x.0, but minor version is not 0."));

    manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">"
        "    <hal format=\"native\">"
        "        <name>netutils-wrapper</name>"
        "        <version>1.0</version>"
        "        <version>2.1</version>"
        "    </hal>"
        "</manifest>";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_THAT(error, HasSubstr(
        "netutils-wrapper HAL must specify exactly one version x.0, but minor version is not 0."));

#endif  // LIBVINTF_TARGET
}

TEST_F(LibVintfTest, KernelConfigConditionTest) {
    std::string error;
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARM</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_FOO</key>\n"
        "            <value type=\"tristate\">y</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.0</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb>\n"
        "        <vbmeta-version>2.1</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n";

    CompatibilityMatrix cm;
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    const auto& kernels = getKernels(cm);
    ASSERT_GE(kernels.size(), 2u);
    ASSERT_TRUE(kernels[0].conditions().empty());
    const auto& kernel = kernels[1];
    const auto& cond = kernel.conditions();
    ASSERT_FALSE(cond.empty());
    EXPECT_EQ("CONFIG_ARM", cond.begin()->first);
    EXPECT_EQ(KernelConfigTypedValue(Tristate::YES), cond.begin()->second);
    EXPECT_FALSE(kernel.configs().empty());

    EXPECT_EQ(xml, toXml(cm));
}

TEST_F(LibVintfTest, KernelConfigConditionEmptyTest) {
    std::string error;
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"4.4.0\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARM</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "    </kernel>\n"
        "</compatibility-matrix>\n";

    CompatibilityMatrix cm;
    EXPECT_FALSE(fromXml(&cm, xml, &error))
        << "Should not accept first kernel version with non-empty conditions";
    EXPECT_EQ(
        "First <kernel> for version 3.18 must have empty <conditions> "
        "for backwards compatibility.", error);
}

TEST_F(LibVintfTest, KernelConfigConditionMatch) {
    RuntimeInfo runtime = testRuntimeInfo();
    std::string error;
    std::string xml;
    CompatibilityMatrix cm;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "            <value type=\"int\">24</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_TRUE(runtime.checkCompatibility(cm, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "            <value type=\"int\">26</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_FALSE(runtime.checkCompatibility(cm, &error))
        << "conditions met, so CONFIG_ARCH_MMAP_RND_BITS should not match";

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">n</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "            <value type=\"int\">26</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_TRUE(runtime.checkCompatibility(cm, &error)) << error;
    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">24</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xdead000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_TRUE(runtime.checkCompatibility(cm, &error));

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">24</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xbeaf000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_FALSE(runtime.checkCompatibility(cm, &error))
        << "conditions have 'and' relationship, so CONFIG_ILLEGAL_POINTER_VALUE should not match";

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\"/>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">26</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xbeaf000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_TRUE(runtime.checkCompatibility(cm, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <config>\n"
        "            <key>CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES</key>\n"
        "            <value type=\"string\"/>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xdead000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">24</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ANDROID_BINDER_DEVICES</key>\n"
        "            <value type=\"string\">binder,hwbinder</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_TRUE(runtime.checkCompatibility(cm, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <config>\n"
        "            <key>CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES</key>\n"
        "            <value type=\"string\"/>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xbeaf000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">24</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ANDROID_BINDER_DEVICES</key>\n"
        "            <value type=\"string\">binder,hwbinder</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_FALSE(runtime.checkCompatibility(cm, &error)) << "all fragments should be used.";

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <config>\n"
        "            <key>CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES</key>\n"
        "            <value type=\"string\"/>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_64BIT</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "            <value type=\"int\">0xdead000000000000</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <kernel version=\"3.18.22\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "                <value type=\"int\">24</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_ANDROID_BINDER_DEVICES</key>\n"
        "            <value type=\"string\">binder</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";

    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_FALSE(runtime.checkCompatibility(cm, &error)) << "all fragments should be used";
}

// Run KernelConfigParserInvalidTest on processComments = {true, false}
class KernelConfigParserInvalidTest : public ::testing::TestWithParam<bool> {};

TEST_P(KernelConfigParserInvalidTest, NonSet1) {
    const std::string data = "# CONFIG_NOT_EXIST is not sat\n";
    auto pair = processData(data, GetParam() /* processComments */, true /* relaxedFormat */);
    ASSERT_EQ(OK, pair.second) << pair.first.error();
    const auto& configs = pair.first.configs();
    EXPECT_EQ(configs.find("CONFIG_NOT_EXIST"), configs.end())
        << "CONFIG_NOT_EXIST should not exist because of typo";
}

TEST_P(KernelConfigParserInvalidTest, InvalidLine1) {
    const std::string data = "FOO_CONFIG=foo\n";
    ASSERT_NE(OK,
              processData(data, GetParam() /* processComments */, true /* relaxedFormat */).second);
}

TEST_P(KernelConfigParserInvalidTest, InvalidLine2) {
    const std::string data = "CONFIG_BAR-BAZ=foo\n";
    ASSERT_NE(OK,
              processData(data, GetParam() /* processComments */, true /* relaxedFormat */).second);
}

INSTANTIATE_TEST_CASE_P(KernelConfigParser, KernelConfigParserInvalidTest, ::testing::Bool());

TEST_F(LibVintfTest, MatrixLevel) {
    std::string error;
    CompatibilityMatrix cm;
    std::string xml;

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\"/>";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_EQ(Level::UNSPECIFIED, cm.level());

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"legacy\"/>";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_EQ(Level::LEGACY, cm.level());

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\"/>";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_EQ(Level{1}, cm.level());
}

TEST_F(LibVintfTest, ManifestLevel) {
    std::string error;
    HalManifest manifest;
    std::string xml;

    xml = "<manifest " + kMetaVersionStr + " type=\"device\"/>";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(Level::UNSPECIFIED, manifest.level());

    xml = "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"legacy\"/>";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(Level::LEGACY, manifest.level());

    xml = "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"1\"/>";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(Level{1}, manifest.level());
}

TEST_F(LibVintfTest, AddOptionalHal) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\"/>";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0-1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <hal format=\"hidl\" optional=\"true\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <version>1.0-1</version>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, AddOptionalHalMinorVersion) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.2-3</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0-4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <hal format=\"hidl\" optional=\"false\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <version>1.0-4</version>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, AddOptionalHalMajorVersion) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.2-3</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.2-3</version>\n"
        "        <version>2.0-4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <hal format=\"hidl\" optional=\"false\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <version>1.2-3</version>\n"
              "        <version>2.0-4</version>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, AddOptionalHalMinorVersionDiffInstance) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0-1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.1-2</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>custom</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <hal format=\"hidl\" optional=\"false\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <version>1.0-1</version>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "    <hal format=\"hidl\" optional=\"true\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <version>1.1-2</version>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>custom</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, AddRequiredHalOverlapInstance) {
    CompatibilityMatrix cm1;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>custom</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    {
        // Test that 2.0 should be added to IFoo/default, so 1.0::IFoo/custom
        // should be in a new <hal> tag
        CompatibilityMatrix cm2;
        xml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>2.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</compatibility-matrix>\n";
        EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

        EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;

        xml = toXml(cm1, SerializeFlags::HALS_ONLY);
        EXPECT_EQ(xml,
                  "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
                  "    <hal format=\"hidl\" optional=\"false\">\n"
                  "        <name>android.hardware.foo</name>\n"
                  "        <version>1.0</version>\n"
                  "        <interface>\n"
                  "            <name>IFoo</name>\n"
                  "            <instance>custom</instance>\n"
                  "        </interface>\n"
                  "    </hal>\n"
                  "    <hal format=\"hidl\" optional=\"false\">\n"
                  "        <name>android.hardware.foo</name>\n"
                  "        <version>1.0</version>\n"
                  "        <version>2.0</version>\n"
                  "        <interface>\n"
                  "            <name>IFoo</name>\n"
                  "            <instance>default</instance>\n"
                  "        </interface>\n"
                  "    </hal>\n"
                  "</compatibility-matrix>\n");
    }

    {
        // Test that 2.0::IFoo/strong should be added as an optional <hal> tag.
        CompatibilityMatrix cm2;
        xml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>2.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>strong</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</compatibility-matrix>\n";
        EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

        EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;

        xml = toXml(cm1, SerializeFlags::HALS_ONLY);
        EXPECT_EQ(xml,
                  "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
                  "    <hal format=\"hidl\" optional=\"false\">\n"
                  "        <name>android.hardware.foo</name>\n"
                  "        <version>1.0</version>\n"
                  "        <interface>\n"
                  "            <name>IFoo</name>\n"
                  "            <instance>custom</instance>\n"
                  "        </interface>\n"
                  "    </hal>\n"
                  "    <hal format=\"hidl\" optional=\"false\">\n"
                  "        <name>android.hardware.foo</name>\n"
                  "        <version>1.0</version>\n"
                  "        <version>2.0</version>\n"
                  "        <interface>\n"
                  "            <name>IFoo</name>\n"
                  "            <instance>default</instance>\n"
                  "        </interface>\n"
                  "    </hal>\n"
                  "    <hal format=\"hidl\" optional=\"true\">\n"
                  "        <name>android.hardware.foo</name>\n"
                  "        <version>2.0</version>\n"
                  "        <interface>\n"
                  "            <name>IFoo</name>\n"
                  "            <instance>strong</instance>\n"
                  "        </interface>\n"
                  "    </hal>\n"
                  "</compatibility-matrix>\n");
    }
}

TEST_F(LibVintfTest, AddRequiredHalOverlapInstanceSplit) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>custom</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>strong</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>custom</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>strong</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n",
        xml);
}

TEST_F(LibVintfTest, AddOptionalHalUpdatableViaApex) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <hal format=\"aidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <hal format=\"aidl\" optional=\"false\" updatable-via-apex=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllHalsAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::HALS_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <hal format=\"aidl\" optional=\"false\" updatable-via-apex=\"true\">\n"
              "        <name>android.hardware.foo</name>\n"
              "        <interface>\n"
              "            <name>IFoo</name>\n"
              "            <instance>default</instance>\n"
              "        </interface>\n"
              "    </hal>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, AddOptionalXmlFile) {
    CompatibilityMatrix cm1;
    CompatibilityMatrix cm2;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <xmlfile format=\"xsd\" optional=\"true\">\n"
        "        <name>foo</name>\n"
        "        <version>1.0-2</version>\n"
        "        <path>/foo/bar/baz.xsd</path>\n"
        "    </xmlfile>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm1, xml, &error)) << error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"
        "    <xmlfile format=\"xsd\" optional=\"true\">\n"
        "        <name>foo</name>\n"
        "        <version>1.1-3</version>\n"
        "        <path>/foo/bar/quux.xsd</path>\n"
        "    </xmlfile>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm2, xml, &error)) << error;

    EXPECT_TRUE(addAllXmlFilesAsOptional(&cm1, &cm2, &error)) << error;
    xml = toXml(cm1, SerializeFlags::XMLFILES_ONLY);
    EXPECT_EQ(xml,
              "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
              "    <xmlfile format=\"xsd\" optional=\"true\">\n"
              "        <name>foo</name>\n"
              "        <version>1.0-2</version>\n"
              "        <path>/foo/bar/baz.xsd</path>\n"
              "    </xmlfile>\n"
              "    <xmlfile format=\"xsd\" optional=\"true\">\n"
              "        <name>foo</name>\n"
              "        <version>1.1-3</version>\n"
              "        <path>/foo/bar/quux.xsd</path>\n"
              "    </xmlfile>\n"
              "</compatibility-matrix>\n");
}

TEST_F(LibVintfTest, VendorNdk) {
    CompatibilityMatrix cm;
    std::string error;
    std::string xml;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <vendor-ndk>\n"
        "        <version>P</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vendor-ndk>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(cm));

    EXPECT_EQ("P", cm.getVendorNdkVersion());

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <vendor-ndk>\n"
            "        <version>27</version>\n"
            "        <library>libbase.so</library>\n"
            "        <library>libjpeg.so</library>\n"
            "    </vendor-ndk>\n"
            "    <vendor-ndk>\n"
            "        <version>P</version>\n"
            "        <library>libbase.so</library>\n"
            "        <library>libjpeg.so</library>\n"
            "        <library>libtinyxml2.so</library>\n"
            "    </vendor-ndk>\n"
            "</manifest>\n";

        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_EQ(xml, toXml(manifest));
        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <vendor-ndk>\n"
            "        <version>27</version>\n"
            "        <library>libbase.so</library>\n"
            "        <library>libjpeg.so</library>\n"
            "    </vendor-ndk>\n"
            "</manifest>\n";

        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_EQ(xml, toXml(manifest));
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN("Vndk version P is not supported.", error);
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <vendor-ndk>\n"
            "        <version>P</version>\n"
            "        <library>libbase.so</library>\n"
            "    </vendor-ndk>\n"
            "</manifest>\n";

        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_EQ(xml, toXml(manifest));
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN("Vndk libs incompatible for version P.", error);
        EXPECT_IN("libjpeg.so", error);
    }
}

TEST_F(LibVintfTest, MissingVendorNdkInMatrix) {
    CompatibilityMatrix cm;
    std::string xml;
    std::string error;

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"device\"/>\n";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;

    {
        HalManifest manifest;
        xml = "<manifest " + kMetaVersionStr + " type=\"framework\"/>\n";
        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <vendor-ndk>\n"
            "        <version>P</version>\n"
            "        <library>libbase.so</library>\n"
            "    </vendor-ndk>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
    }
}

TEST_F(LibVintfTest, DuplicatedVendorNdkVersion) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <vendor-ndk>\n"
        "        <version>27</version>\n"
        "    </vendor-ndk>\n"
        "    <vendor-ndk>\n"
        "        <version>27</version>\n"
        "    </vendor-ndk>\n"
        "</manifest>\n";

    EXPECT_FALSE(fromXml(&manifest, xml, &error));
    EXPECT_EQ("Duplicated manifest.vendor-ndk.version 27", error);
}

TEST_F(LibVintfTest, ManifestHalOverride) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest version=\"5.0\" type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    const auto& foo = getHals(manifest, "android.hardware.foo");
    ASSERT_FALSE(foo.empty());
    EXPECT_TRUE(foo.front()->isOverride());
    const auto& bar = getHals(manifest, "android.hardware.bar");
    ASSERT_FALSE(bar.empty());
    EXPECT_FALSE(bar.front()->isOverride());
}

TEST_F(LibVintfTest, ManifestHalOverrideLatest) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    const auto& foo = getHals(manifest, "android.hardware.foo");
    ASSERT_FALSE(foo.empty());
    EXPECT_TRUE(foo.front()->isOverride());
}

// Test functionality of override="true" tag
TEST_F(LibVintfTest, ManifestAddOverrideHalSimple) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + " type=\"device\"/>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(xml, toXml(manifest, SerializeFlags::HALS_ONLY));
}

// Test functionality of override="true" tag
TEST_F(LibVintfTest, ManifestAddOverrideHalSimpleWithInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + " type=\"device\"/>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, ManifestAddOverrideHalSimpleOverride) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest version=\"5.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(xml, toXml(manifest, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, ManifestAddOverrideHalSimpleOverrideWithInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest version=\"5.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));
}

// Existing major versions should be removed.
TEST_F(LibVintfTest, ManifestAddOverrideHalMultiVersion) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest version=\"5.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "        <version>2.4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot1</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.1</version>\n"
        "        <version>3.1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot2</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@2.4::IFoo/slot1</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/slot2</fqname>\n"
        "        <fqname>@3.1::IFoo/slot2</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, ManifestAddOverrideHalMultiVersion2) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "        <version>2.4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot1</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/slot2</fqname>\n"
        "        <fqname>@2.1::IFoo/slot2</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";

    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(xml, toXml(manifest, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, ManifestAddOverrideHalMultiVersion2WithInterface) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "        <version>2.4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot1</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.1</version>\n"
        "        <version>2.1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot2</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";

    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/slot2</fqname>\n"
        "        <fqname>@2.1::IFoo/slot2</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));
}

// if no <versions>, remove all existing <hal> with given <name>.
TEST_F(LibVintfTest, ManifestAddOverrideHalRemoveAll) {
    std::string error;
    HalManifest manifest;
    std::string xml =
        "<manifest version=\"5.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "        <version>2.4</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>slot1</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>3.1</version>\n"
        "        <version>4.3</version>\n"
        "        <interface>\n"
        "            <name>IBar</name>\n"
        "            <instance>slot2</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    HalManifest newManifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "    </hal>\n"
        "</manifest>\n";

    EXPECT_TRUE(fromXml(&newManifest, xml, &error)) << error;

    manifest.addAllHals(&newManifest);
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.3</version>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));
}

// Make sure missing tags in old VINTF files does not cause incompatibilities.
TEST_F(LibVintfTest, Empty) {
    CompatibilityMatrix cm;
    HalManifest manifest;
    std::string xml;
    std::string error;

    xml = "<compatibility-matrix " + kMetaVersionStr + " type=\"device\"/>\n";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;

    xml = "<manifest " + kMetaVersionStr + " type=\"framework\"/>\n";
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;

    EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
}

TEST_F(LibVintfTest, ParsingUpdatableHals) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"com.android.foo\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));

    // check by calling the API: updatableViaApex()
    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_THAT(foo.front()->updatableViaApex(), Optional(Eq("com.android.foo")));
}

TEST_F(LibVintfTest, ParsingUpdatableViaApex_EmptyIsValidForNonUpdatableHal) {
    std::string error;

    HalManifest manifest;
    manifest.setFileName("/apex/com.foo/etc/vintf/manifest.xml");
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));

    // check by calling the API: updatableViaApex()
    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_THAT(foo.front()->updatableViaApex(), Optional(Eq("")));
}

TEST_F(LibVintfTest, ParsingUpdatableViaApex_UpdatableHalCanExplicitlySet) {
    std::string error;

    HalManifest manifest;
    manifest.setFileName("/apex/com.foo/etc/vintf/manifest.xml");
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"com.foo\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));

    // check by calling the API: updatableViaApex()
    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_THAT(foo.front()->updatableViaApex(), Optional(Eq("com.foo")));
}

TEST_F(LibVintfTest, ParsingUpdatableViaApex_ErrorIfExplicitValueMismatch) {
    std::string error;

    HalManifest manifest;
    manifest.setFileName("/apex/com.bar/etc/vintf/manifest.xml");
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"com.foo\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("updatable-via-apex com.foo doesn't match", error);
}

TEST_F(LibVintfTest, ParsingUpdatableViaApex_SetToCurrentApex) {
    std::string error;

    HalManifest manifest;
    manifest.setFileName("/apex/com.foo/etc/vintf/manifest.xml");
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("updatable-via-apex=\"com.foo\"", toXml(manifest, SerializeFlags::HALS_ONLY));

    // check by calling the API: updatableViaApex()
    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_THAT(foo.front()->updatableViaApex(), Optional(Eq("com.foo")));
}

TEST_F(LibVintfTest, ParsingUpdatableHalsWithInterface) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"com.android.foo\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" updatable-via-apex=\"com.android.foo\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));

    // check by calling the API: updatableViaApex()
    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_THAT(foo.front()->updatableViaApex(), Optional(Eq("com.android.foo")));
}

TEST_F(LibVintfTest, ParsingHalsInetTransport) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\">inet</transport>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));

    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    ASSERT_TRUE(foo.front()->ip().has_value());
    ASSERT_TRUE(foo.front()->port().has_value());
    EXPECT_EQ("1.2.3.4", *foo.front()->ip());
    EXPECT_EQ(12, *foo.front()->port());
}

TEST_F(LibVintfTest, ParsingHalsInetTransportWithInterface) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\">inet</transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_EQ(
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\">inet</transport>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n",
        toXml(manifest, SerializeFlags::HALS_ONLY));

    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    ASSERT_TRUE(foo.front()->ip().has_value());
    ASSERT_TRUE(foo.front()->port().has_value());
    EXPECT_EQ("1.2.3.4", *foo.front()->ip());
    EXPECT_EQ(12, *foo.front()->port());
}

TEST_F(LibVintfTest, RejectHalsInetTransportNoAttrs) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>inet</transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("Transport inet requires ip and port attributes", error);
}

TEST_F(LibVintfTest, RejectHalsInetTransportMissingAttrs) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\">inet</transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("Transport inet requires ip and port", error);
}

TEST_F(LibVintfTest, RejectHalsEmptyTransportWithInetAttrs) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\"></transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("Transport  requires empty ip and port attributes", error);
}

TEST_F(LibVintfTest, RejectHidlHalsInetTransport) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\">inet</transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN(
            "HIDL HAL 'android.hardware.foo' should not have <transport> \"inet\" or ip or port",
            error);
}

TEST_F(LibVintfTest, RejectHidlHalsHwbinderInetAttrs) {
    std::string error;

    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport ip=\"1.2.3.4\" port=\"12\">hwbinder</transport>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("Transport hwbinder requires empty ip and port attributes", error);
}

TEST_F(LibVintfTest, SystemSdk) {
    CompatibilityMatrix cm;
    std::string xml;
    std::string error;

    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <system-sdk>\n"
        "        <version>1</version>\n"
        "        <version>P</version>\n"
        "    </system-sdk>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(cm, SerializeFlags::SSDK_ONLY));

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <system-sdk>\n"
            "        <version>1</version>\n"
            "        <version>P</version>\n"
            "    </system-sdk>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_EQ(xml, toXml(manifest, SerializeFlags::SSDK_ONLY));

        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <system-sdk>\n"
            "        <version>1</version>\n"
            "        <version>3</version>\n"
            "        <version>P</version>\n"
            "    </system-sdk>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(cm, &error));
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <system-sdk>\n"
            "        <version>1</version>\n"
            "    </system-sdk>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_TRUE(error.find("System SDK") != std::string::npos) << error;
    }
}

TEST_F(LibVintfTest, ManifestEmpty) {
    std::string error;
    HalManifest e;
    EXPECT_FALSE(fromXml(&e, "<manifest/>", &error));
    EXPECT_NE("Not a valid XML", error);

    std::string prevError = error;
    EXPECT_FALSE(fromXml(&e, "", &error));
    EXPECT_EQ("Not a valid XML", error);
}

TEST_F(LibVintfTest, MatrixEmpty) {
    std::string error;
    CompatibilityMatrix e;
    EXPECT_FALSE(fromXml(&e, "<compatibility-matrix/>", &error));
    EXPECT_NE("Not a valid XML", error);

    std::string prevError = error;
    EXPECT_FALSE(fromXml(&e, "", &error));
    EXPECT_EQ("Not a valid XML", error);
}

TEST_F(LibVintfTest, MatrixDetailErrorMsg) {
    std::string error;
    std::string xml;

    HalManifest manifest;
    xml =
        "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"8\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</manifest>\n";
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    {
        CompatibilityMatrix cm;
        xml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"7\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>1.2-3</version>\n"
            "        <version>4.5</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>slot1</instance>\n"
            "        </interface>\n"
            "        <interface>\n"
            "            <name>IBar</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</compatibility-matrix>\n";
        EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN("Manifest level = 8", error);
        EXPECT_IN("Matrix level = 7", error);
        EXPECT_IN(
            "android.hardware.foo:\n"
            "    required: \n"
            "        (@1.2-3::IBar/default AND @1.2-3::IFoo/default AND @1.2-3::IFoo/slot1) OR\n"
            "        (@4.5::IBar/default AND @4.5::IFoo/default AND @4.5::IFoo/slot1)\n"
            "    provided: @1.0::IFoo/default",
            error);
    }

    {
        CompatibilityMatrix cm;
        xml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>1.2-3</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>slot1</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</compatibility-matrix>\n";
        EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN(
            "android.hardware.foo:\n"
            "    required: (@1.2-3::IFoo/default AND @1.2-3::IFoo/slot1)\n"
            "    provided: @1.0::IFoo/default",
            error);
    }

    // the most frequent use case.
    {
        CompatibilityMatrix cm;
        xml =
            "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>1.2-3</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</compatibility-matrix>\n";
        EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN(
            "android.hardware.foo:\n"
            "    required: @1.2-3::IFoo/default\n"
            "    provided: @1.0::IFoo/default",
            error);
    }
}

TEST_F(LibVintfTest, DisabledHal) {
    std::string error;
    std::string xml;
    HalManifest manifest;
    xml =
        "<manifest version=\"5.0\" type=\"framework\">\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <transport>hwbinder</transport>\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" override=\"true\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.1::IFoo/custom</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.baz</name>\n"
        "        <transport>hwbinder</transport>\n"
        "    </hal>\n"
        "</manifest>\n";
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    auto foo = getHals(manifest, "android.hardware.foo");
    ASSERT_EQ(1u, foo.size());
    EXPECT_TRUE(foo.front()->isDisabledHal());
    auto bar = getHals(manifest, "android.hardware.bar");
    ASSERT_EQ(1u, bar.size());
    EXPECT_FALSE(bar.front()->isDisabledHal());
    auto baz = getHals(manifest, "android.hardware.baz");
    ASSERT_EQ(1u, baz.size());
    EXPECT_FALSE(baz.front()->isDisabledHal());
}

TEST_F(LibVintfTest, FqNameValid) {
    std::string error;
    std::string xml;

    CompatibilityMatrix cm;
    xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.1</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>custom</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&cm, xml, &error)) << error;

    {
        HalManifest manifest;
        xml =
            "<manifest version=\"5.0\" type=\"framework\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <version>1.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>custom</instance>\n"
            "        </interface>\n"
            "        <fqname>@1.1::IFoo/custom</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;

        EXPECT_EQ(Transport::HWBINDER,
                  manifest.getHidlTransport("android.hardware.foo", {1, 1}, "IFoo", "custom"));
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <fqname>@1.0::IFoo/default</fqname>\n"
            "        <fqname>@1.1::IFoo/custom</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_TRUE(manifest.checkCompatibility(cm, &error)) << error;
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <version>1.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>custom</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;
        EXPECT_FALSE(manifest.checkCompatibility(cm, &error));
        EXPECT_IN(
            "android.hardware.foo:\n"
            "    required: @1.1::IFoo/custom\n"
            "    provided: \n"
            "        @1.0::IFoo/custom\n"
            "        @1.0::IFoo/default",
            error);
    }

    {
        HalManifest manifest;
        xml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <fqname>@1.0::IFoo/default</fqname>\n"
            "        <fqname>@1.0::IFoo/custom</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;
    }
}

TEST_F(LibVintfTest, FqNameInvalid) {
    std::string error;
    std::string xml;
    {
        ManifestHal hal;
        xml =
            "<hal format=\"hidl\">\n"
            "    <name>android.hardware.foo</name>\n"
            "    <transport>hwbinder</transport>\n"
            "    <fqname>@1.1::IFoo/custom</fqname>\n"
            "</hal>\n";
        EXPECT_TRUE(fromXml(&hal, xml, &error)) << error;
    }
    ManifestHal hal;
    xml =
        "<hal format=\"hidl\">\n"
        "    <name>android.hardware.foo</name>\n"
        "    <transport>hwbinder</transport>\n"
        "    <fqname>1.1::IFoo/custom</fqname>\n"
        "</hal>\n";
    ASSERT_FALSE(fromXml(&hal, xml, &error));
    EXPECT_IN("Could not parse text \"1.1::IFoo/custom\" in element <fqname>", error);
    xml =
        "<hal format=\"hidl\">\n"
        "    <name>android.hardware.foo</name>\n"
        "    <transport>hwbinder</transport>\n"
        "    <fqname>android.hardware.foo@1.1::IFoo/custom</fqname>\n"
        "</hal>\n";
    ASSERT_FALSE(fromXml(&hal, xml, &error));
    EXPECT_IN("Should not specify package", error);
    xml =
        "<hal format=\"hidl\">\n"
        "    <name>android.hardware.foo</name>\n"
        "    <transport>hwbinder</transport>\n"
        "    <fqname>IFoo/custom</fqname>\n"
        "</hal>\n";
    ASSERT_FALSE(fromXml(&hal, xml, &error));
    EXPECT_IN("Should specify version", error);
    xml =
        "<hal format=\"hidl\">\n"
        "    <name>android.hardware.foo</name>\n"
        "    <transport>hwbinder</transport>\n"
        "    <fqname>@1.0::IFoo</fqname>\n"
        "</hal>\n";
    ASSERT_FALSE(fromXml(&hal, xml, &error));
    EXPECT_IN("Could not parse text \"@1.0::IFoo\" in element <fqname>", error);
    xml =
        "<hal format=\"hidl\">\n"
        "    <name>n07 4 v4l1d 1n73rf4c3</name>\n"
        "    <transport>hwbinder</transport>\n"
        "    <fqname>@1.0::IFoo/custom</fqname>\n"
        "</hal>\n";
    ASSERT_FALSE(fromXml(&hal, xml, &error));
    EXPECT_IN("Cannot create FqInstance", error);
    EXPECT_IN("n07 4 v4l1d 1n73rf4c3", error);
}

TEST_F(LibVintfTest, RegexInstanceValid) {
    CompatibilityMatrix matrix;
    std::string error;

    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <regex-instance>legacy/[0-9]+</regex-instance>\n"
        "            <regex-instance>slot[0-9]+</regex-instance>\n"
        "            <regex-instance>.*</regex-instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
}

TEST_F(LibVintfTest, RegexInstanceInvalid) {
    CompatibilityMatrix matrix;
    std::string error;
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <regex-instance>e{1,2,3}</regex-instance>\n"
        "            <regex-instance>*</regex-instance>\n"
        "            <regex-instance>+</regex-instance>\n"
        "            <regex-instance>[0-9]+</regex-instance>\n"
        "            <regex-instance>[0-9]+</regex-instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    EXPECT_FALSE(fromXml(&matrix, xml, &error));
    EXPECT_IN("Invalid regular expression 'e{1,2,3}'", error);
    EXPECT_IN("Invalid regular expression '*'", error);
    EXPECT_IN("Invalid regular expression '+'", error);
    EXPECT_IN("Duplicated regex-instance '[0-9]+'", error);
}

TEST_F(LibVintfTest, RegexInstanceCompat) {
    CompatibilityMatrix matrix;
    std::string error;

    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <version>3.1-2</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "            <regex-instance>legacy/[0-9]+</regex-instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>0</kernel-sepolicy-version>\n"
        "        <sepolicy-version>0</sepolicy-version>\n"
        "    </sepolicy>\n"
        "</compatibility-matrix>\n";
    EXPECT_TRUE(fromXml(&matrix, matrixXml, &error)) << error;

    {
        std::string xml =
            "<manifest " + kMetaVersionStr + " type=\"device\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <version>1.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>legacy/0</instance>\n"
            "            <instance>legacy/1</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";

        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, xml));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;

        auto unused = checkUnusedHals(manifest, matrix);
        EXPECT_TRUE(unused.empty())
            << "Contains unused HALs: " << android::base::Join(unused, "\n");
    }

    {
        std::string xml =
            "<manifest " + kMetaVersionStr + " type=\"device\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <version>1.0</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>legacy0</instance>\n"
            "            <instance>nonmatch/legacy/0</instance>\n"
            "            <instance>legacy/0/nonmatch</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";

        HalManifest manifest;
        EXPECT_TRUE(fromXml(&manifest, xml));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because no legacy/[0-9]+ is provided.";

        auto unused = checkUnusedHals(manifest, matrix);
        EXPECT_EQ((std::set<std::string>{"android.hardware.foo@1.0::IFoo/nonmatch/legacy/0",
                                         "android.hardware.foo@1.0::IFoo/legacy/0/nonmatch",
                                         "android.hardware.foo@1.0::IFoo/legacy0"}),
                  unused);
    }
}

TEST_F(LibVintfTest, Regex) {
    details::Regex regex;

    EXPECT_FALSE(regex.compile("+"));
    EXPECT_FALSE(regex.compile("*"));

    ASSERT_TRUE(regex.compile("legacy/[0-9]+"));
    EXPECT_TRUE(regex.matches("legacy/0"));
    EXPECT_TRUE(regex.matches("legacy/000"));
    EXPECT_FALSE(regex.matches("legacy/"));
    EXPECT_FALSE(regex.matches("ssslegacy/0"));
    EXPECT_FALSE(regex.matches("legacy/0sss"));
}

TEST_F(LibVintfTest, ManifestGetHalNamesAndVersions) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getHalNamesAndVersions(),
              std::set<std::string>({"android.hardware.camera@2.0", "android.hardware.nfc@1.0"}));
}

TEST_F(LibVintfTest, KernelInfo) {
    KernelInfo ki = testKernelInfo();

    EXPECT_EQ(
        "<kernel version=\"3.18.31\">\n"
        "    <config>\n"
        "        <key>CONFIG_64BIT</key>\n"
        "        <value>y</value>\n"
        "    </config>\n"
        "    <config>\n"
        "        <key>CONFIG_ANDROID_BINDER_DEVICES</key>\n"
        "        <value>\"binder,hwbinder\"</value>\n"
        "    </config>\n"
        "    <config>\n"
        "        <key>CONFIG_ARCH_MMAP_RND_BITS</key>\n"
        "        <value>24</value>\n"
        "    </config>\n"
        "    <config>\n"
        "        <key>CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES</key>\n"
        "        <value>\"\"</value>\n"
        "    </config>\n"
        "    <config>\n"
        "        <key>CONFIG_ILLEGAL_POINTER_VALUE</key>\n"
        "        <value>0xdead000000000000</value>\n"
        "    </config>\n"
        "</kernel>\n",
        toXml(ki, SerializeFlags::NO_TAGS.enableKernelConfigs()));
}

TEST_F(LibVintfTest, ManifestAddAllDeviceManifest) {
    std::string xml1 = "<manifest " + kMetaVersionStr + " type=\"device\" />\n";
    std::string xml2 =
        "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"3\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.0::IFoo/default</fqname>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.5</version>\n"
        "    </sepolicy>\n"
        "    <kernel version=\"3.18.31\">\n"
        "        <config>\n"
        "            <key>CONFIG_64BIT</key>\n"
        "            <value>y</value>\n"
        "        </config>\n"
        "    </kernel>\n"
        "    <xmlfile>\n"
        "        <name>media_profile</name>\n"
        "        <version>1.0</version>\n"
        "    </xmlfile>\n"
        "</manifest>\n";

    std::string error;
    HalManifest manifest1;
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_TRUE(manifest1.addAll(&manifest2, &error)) << error;

    EXPECT_EQ(xml2, toXml(manifest1));
}

TEST_F(LibVintfTest, ManifestAddAllFrameworkManifest) {
    std::string xml1 = "<manifest " + kMetaVersionStr + " type=\"framework\" />\n";
    std::string xml2 =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.0::IFoo/default</fqname>\n"
        "    </hal>\n"
        "    <vendor-ndk>\n"
        "        <version>P</version>\n"
        "        <library>libbase.so</library>\n"
        "    </vendor-ndk>\n"
        "    <system-sdk>\n"
        "        <version>1</version>\n"
        "    </system-sdk>\n"
        "    <xmlfile>\n"
        "        <name>media_profile</name>\n"
        "        <version>1.0</version>\n"
        "    </xmlfile>\n"
        "</manifest>\n";

    std::string error;
    HalManifest manifest1;
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_TRUE(manifest1.addAll(&manifest2, &error)) << error;

    EXPECT_EQ(xml2, toXml(manifest1));
}

TEST_F(LibVintfTest, ManifestAddAllConflictMajorVersion) {
    std::string head =
            "<manifest " + kMetaVersionStr + " type=\"device\">\n"
            "    <hal format=\"hidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <transport>hwbinder</transport>\n"
            "        <version>";
    std::string tail =
            "</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";

    std::string xml1 = head + "1.0" + tail;
    std::string xml2 = head + "1.1" + tail;

    std::string error;
    HalManifest manifest1;
    manifest1.setFileName("1.xml");
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    manifest2.setFileName("2.xml");
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_FALSE(manifest1.addAll(&manifest2, &error));

    EXPECT_IN("android.hardware.foo", error);
    EXPECT_IN("@1.0::IFoo/default (from 1.xml)", error);
    EXPECT_IN("@1.1::IFoo/default (from 2.xml)", error);
}

TEST_F(LibVintfTest, ManifestAddAllConflictLevel) {
    std::string xml1 = "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"2\" />\n";
    std::string xml2 = "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"3\" />\n";

    std::string error;
    HalManifest manifest1;
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_FALSE(manifest1.addAll(&manifest2, &error));
    EXPECT_IN("Conflicting target-level", error);
}

TEST_F(LibVintfTest, ManifestAddAllConflictSepolicy) {
    std::string xml1 =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <sepolicy>\n"
        "        <version>25.5</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";
    std::string xml2 =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <sepolicy>\n"
        "        <version>30.0</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";

    std::string error;
    HalManifest manifest1;
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_FALSE(manifest1.addAll(&manifest2, &error));
    EXPECT_IN("Conflicting sepolicy version", error);
}

TEST_F(LibVintfTest, ManifestAddAllConflictKernel) {
    std::string xml1 =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <kernel version=\"3.18.0\" />\n"
        "</manifest>\n";
    std::string xml2 =
        "<manifest " + kMetaVersionStr + " type=\"device\">\n"
        "    <kernel version=\"3.18.1\" />\n"
        "</manifest>\n";

    std::string error;
    HalManifest manifest1;
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_FALSE(manifest1.addAll(&manifest2, &error));
    EXPECT_IN("Conflicting kernel", error);
}

TEST_F(LibVintfTest, ManifestMetaVersionCompat) {
    std::string xml = "<manifest version=\"2.0\" type=\"device\" />";
    std::string error;
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
}

TEST_F(LibVintfTest, ManifestMetaVersionIncompat) {
    std::string xml = "<manifest version=\"10000.0\" type=\"device\" />";
    std::string error;
    HalManifest manifest;
    EXPECT_FALSE(fromXml(&manifest, xml, &error))
        << "Should not parse metaversion 10000.0";
}

TEST_F(LibVintfTest, ManifestMetaVersionWriteLatest) {
    std::string xml = "<manifest version=\"1.0\" type=\"device\" />";
    std::string error;
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_IN(kMetaVersionStr, toXml(manifest, SerializeFlags::NO_TAGS));
}

TEST_F(LibVintfTest, MatrixMetaVersionCompat) {
    std::string xml = "<compatibility-matrix version=\"2.0\" type=\"framework\" />";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
}

TEST_F(LibVintfTest, MatrixMetaVersionIncompat) {
    std::string xml = "<compatibility-matrix version=\"10000.0\" type=\"framework\" />";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_FALSE(fromXml(&matrix, xml, &error))
        << "Should not parse metaversion 10000.0";
}

TEST_F(LibVintfTest, MatrixMetaVersionWriteLatest) {
    std::string xml = "<compatibility-matrix version=\"1.0\" type=\"framework\" />";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
    EXPECT_IN(kMetaVersionStr, toXml(matrix, SerializeFlags::NO_TAGS));
}

// clang-format on

struct InMemoryFileSystem : FileSystem {
    std::map<std::string, std::string> files;
    InMemoryFileSystem(std::map<std::string, std::string> files) : files(std::move(files)) {}
    status_t fetch(const std::string& path, std::string* fetched,
                   std::string* error) const override {
        (void)error;
        if (auto it = files.find(path); it != files.end()) {
            *fetched = it->second;
            return OK;
        }
        return NAME_NOT_FOUND;
    }
    status_t listFiles(const std::string& path, std::vector<std::string>* out,
                       std::string* error) const override {
        (void)error;
        std::set<std::string> entries;
        for (const auto& pair : files) {
            std::string_view entry{pair.first};
            if (android::base::ConsumePrefix(&entry, path)) {
                android::base::ConsumePrefix(&entry, "/");
                entries.emplace(entry.substr(0, entry.find('/')));
            }
        }
        *out = std::vector<std::string>{begin(entries), end(entries)};
        return OK;
    }
    status_t modifiedTime(const std::string& path, TimeSpec* mtime, std::string* error) const {
        (void)error;
        if (auto it = files.find(path); it != files.end()) {
            *mtime = TimeSpec{};
            return OK;
        }
        return NAME_NOT_FOUND;
    }
};

TEST_F(LibVintfTest, HalManifestWithMultipleFiles) {
    std::string vendorXmlPath = "/vendor/etc/vintf/manifest.xml";
    std::string vendorXml = "<manifest " + kMetaVersionStr +
                            " type=\"device\">\n"
                            "    <hal format=\"aidl\">\n"
                            "        <name>android.hardware.foo</name>\n"
                            "        <fqname>IFoo/default</fqname>\n"
                            "    </hal>\n"
                            "</manifest>";
    std::string apexXmlPath = "/apex/com.android.bar/etc/vintf/manifest.xml";
    std::string apexXml = "<manifest " + kMetaVersionStr +
                          " type=\"device\">\n"
                          "    <hal format=\"aidl\">\n"
                          "        <name>android.hardware.bar</name>\n"
                          "        <fqname>IBar/default</fqname>\n"
                          "    </hal>\n"
                          "</manifest>";
    InMemoryFileSystem files{{
        {vendorXmlPath, vendorXml},
        {apexXmlPath, apexXml},
    }};
    // Read apexXml later. This shouldn't affect the result except HalManifest::fileName.
    {
        std::string error;
        HalManifest manifest;
        EXPECT_EQ(OK, fetchManifest(manifest, &files, vendorXmlPath, &error)) << error;
        EXPECT_EQ(OK, fetchManifest(manifest, &files, apexXmlPath, &error)) << error;
        EXPECT_EQ(vendorXmlPath + ":" + apexXmlPath, manifest.fileName());
        EXPECT_EQ(std::nullopt, getAnyHal(manifest, "android.hardware.foo")->updatableViaApex());
        EXPECT_EQ(std::make_optional("com.android.bar"s),
                  getAnyHal(manifest, "android.hardware.bar")->updatableViaApex());
    }
    // Read apexXml first. This shouldn't affect the result except HalManifest::fileName.
    {
        std::string error;
        HalManifest manifest;
        EXPECT_EQ(OK, fetchManifest(manifest, &files, apexXmlPath, &error)) << error;
        EXPECT_EQ(OK, fetchManifest(manifest, &files, vendorXmlPath, &error)) << error;
        EXPECT_EQ(apexXmlPath + ":" + vendorXmlPath, manifest.fileName());
        EXPECT_EQ(std::nullopt, getAnyHal(manifest, "android.hardware.foo")->updatableViaApex());
        EXPECT_EQ(std::make_optional("com.android.bar"s),
                  getAnyHal(manifest, "android.hardware.bar")->updatableViaApex());
    }
}

// clang-format off

TEST_F(LibVintfTest, Aidl) {
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" optional=\"false\">\n"
        "        <name>android.system.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "            <regex-instance>test.*</regex-instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(matrix, SerializeFlags::HALS_NO_FQNAME));

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>test0</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "test0"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IFoo", "does_not_exist"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IDoesNotExist", "default"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.does_not_exist", "IFoo", "default"));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.does_not_exist", "IFoo"),
                  std::set<std::string>({}));
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "test0"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IFoo", "does_not_exist"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IDoesNotExist", "default"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.does_not_exist", "IFoo", "default"));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.does_not_exist", "IFoo"),
                  std::set<std::string>({}));
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because default instance is missing";
        EXPECT_IN("required: (IFoo/default (@1) AND IFoo/test.* (@1))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/incompat_instance (@1)\n"
                  "        IFoo/test0 (@1)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>incompat_instance</instance>\n"
            "            <instance>test0</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because default instance is missing";
        EXPECT_IN("required: (IFoo/default (@1) AND IFoo/test.* (@1))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/incompat_instance (@1)\n"
                  "        IFoo/test0 (@1)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because test.* instance is missing";
        EXPECT_IN("required: (IFoo/default (@1) AND IFoo/test.* (@1))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@1)\n"
                  "        IFoo/incompat_instance (@1)\n",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>incompat_instance</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because test.* instance is missing";
        EXPECT_IN("required: (IFoo/default (@1) AND IFoo/test.* (@1))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@1)\n"
                  "        IFoo/incompat_instance (@1)\n",
                  error);
    }
}

TEST_F(LibVintfTest, AidlAndHidlNamesMatrix) {
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(matrix, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, AidlAndHidlNamesManifest) {
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.0::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    std::string error;
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(manifest, SerializeFlags::HALS_ONLY));
}

TEST_F(LibVintfTest, AidlAndHidlCheckUnused) {
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@1.0::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    std::string matrixXml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    std::string error;
    HalManifest manifest;
    CompatibilityMatrix matrix;

    EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_TRUE(fromXml(&matrix, matrixXml, &error)) << error;
    auto unused = checkUnusedHals(manifest, matrix);
    EXPECT_TRUE(unused.empty()) << android::base::Join(unused, "\n");
}

TEST_F(LibVintfTest, AidlVersion) {
    std::string xml =
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <hal format=\"aidl\" optional=\"false\">\n"
        "        <name>android.system.foo</name>\n"
        "        <version>4-100</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "            <regex-instance>test.*</regex-instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "</compatibility-matrix>\n";
    std::string error;
    CompatibilityMatrix matrix;
    EXPECT_TRUE(fromXml(&matrix, xml, &error)) << error;
    EXPECT_EQ(xml, toXml(matrix, SerializeFlags::HALS_NO_FQNAME));

    {
        std::vector<std::string> matrixInstances;
        (void)matrix.forEachInstance([&](const MatrixInstance& matrixInstance) {
            EXPECT_EQ(matrixInstance.versionRange(),
                      VersionRange(details::kFakeAidlMajorVersion, 4, 100));
            matrixInstances.push_back(matrixInstance.description(
                matrixInstance.versionRange().minVer()));
            return true;
        });
        EXPECT_THAT(matrixInstances, SizeIs(2)) << android::base::Join(matrixInstances, ", ");
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>test0</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "test0"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", 5, "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", 5, "IFoo", "test0"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IFoo", "does_not_exist"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IDoesNotExist", "default"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.does_not_exist", "IFoo", "default"));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", 5, "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.does_not_exist", "IFoo"),
                  std::set<std::string>({}));
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", "IFoo", "test0"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", 5, "IFoo", "default"));
        EXPECT_TRUE(manifest.hasAidlInstance("android.system.foo", 5, "IFoo", "test0"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IFoo", "does_not_exist"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.foo", "IDoesNotExist", "default"));
        EXPECT_FALSE(manifest.hasAidlInstance("android.system.does_not_exist", "IFoo", "default"));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.foo", 5, "IFoo"),
                  std::set<std::string>({"default", "test0"}));
        EXPECT_EQ(manifest.getAidlInstances("android.system.does_not_exist", "IFoo"),
                  std::set<std::string>({}));
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because default instance is missing";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/incompat_instance (@5)\n"
                  "        IFoo/test0 (@5)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>incompat_instance</instance>\n"
            "            <instance>test0</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because default instance is missing";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/incompat_instance (@5)\n"
                  "        IFoo/test0 (@5)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because test.* instance is missing";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@5)\n"
                  "        IFoo/incompat_instance (@5)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>incompat_instance</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>5</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/incompat_instance</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because test.* instance is missing";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@5)\n"
                  "        IFoo/incompat_instance (@5)",
                  error);
    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>3</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(manifestXml, toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because version 3 cannot satisfy version 4-100";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@3)\n"
                  "        IFoo/test0 (@3)",
                  error);

    }

    {
        HalManifest manifest;
        std::string manifestXml =
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>3</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>test0</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "</manifest>\n";
        EXPECT_TRUE(fromXml(&manifest, manifestXml, &error)) << error;
        EXPECT_EQ(
            "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.system.foo</name>\n"
            "        <version>3</version>\n"
            "        <fqname>IFoo/default</fqname>\n"
            "        <fqname>IFoo/test0</fqname>\n"
            "    </hal>\n"
            "</manifest>\n",
            toXml(manifest, SerializeFlags::HALS_ONLY));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
            << "Should not be compatible because version 3 cannot satisfy version 4-100";
        EXPECT_IN("required: (IFoo/default (@4-100) AND IFoo/test.* (@4-100))", error);
        EXPECT_IN("provided: \n"
                  "        IFoo/default (@3)\n"
                  "        IFoo/test0 (@3)",
                  error);
    }
}

TEST_F(LibVintfTest, AidlFqnameNoVersion) {
    std::string error;
    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <fqname>@1.0::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error)) << error;
    EXPECT_IN("Should not specify version in <fqname> for AIDL HAL: \"@1.0::IFoo/default\"", error);
}

TEST_F(LibVintfTest, GetTransportHidlHalWithFakeAidlVersion) {
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>@" + to_string(details::kDefaultAidlVersion) + "::IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    std::string error;
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(Transport::HWBINDER,
              manifest.getHidlTransport("android.system.foo", details::kDefaultAidlVersion, "IFoo",
                                        "default"));
}

TEST_F(LibVintfTest, RejectAidlHalsWithUnsupportedTransport) {
    std::string error;
    HalManifest manifest;
    std::string manifestXml =
        "<manifest " + kMetaVersionStr + R"( type="framework">"
             <hal format="aidl">
                 <name>android.system.foo</name>
                 <transport>hwbinder</transport>
                 <fqname>IFoo/default</fqname>
             </hal>
         </manifest>)";
    EXPECT_FALSE(fromXml(&manifest, manifestXml, &error));
    EXPECT_IN("android.system.foo", error);
    EXPECT_IN("hwbinder", error);
}

TEST_F(LibVintfTest, GetTransportAidlHalWithDummyTransport) {
    // Check that even if <transport> is specified for AIDL, it is ignored and getHidlTransport
    // will return EMPTY.
    // This is only supported for libvintf 4.0 and below.
    constexpr Version kLegacyMetaVersion{4, 0};
    ASSERT_GE(kMetaVersionAidlInet, kLegacyMetaVersion);
    std::string xml =
        "<manifest version=\"" + to_string(kLegacyMetaVersion) + "\" type=\"framework\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    std::string error;
    HalManifest manifest;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    EXPECT_EQ(Transport::EMPTY,
              manifest.getHidlTransport("android.system.foo", details::kDefaultAidlVersion, "IFoo",
                                        "default"));
}

TEST_F(LibVintfTest, AidlGetHalNamesAndVersions) {
    HalManifest manifest;
    std::string xml =
        "<manifest " + kMetaVersionStr + " type=\"framework\">\n"
        "    <hal format=\"aidl\">\n"
        "        <name>android.system.foo</name>\n"
        "        <fqname>IFoo/default</fqname>\n"
        "    </hal>\n"
        "</manifest>\n";
    std::string error;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    auto names = manifest.getHalNamesAndVersions();
    ASSERT_EQ(1u, names.size());
    EXPECT_EQ("android.system.foo@1", *names.begin());
}

TEST_F(LibVintfTest, ManifestAddAidl) {
    std::string head =
            "<manifest " + kMetaVersionStr + " type=\"device\">\n"
            "    <hal format=\"aidl\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <fqname>";
    std::string tail =
            "</fqname>\n"
            "    </hal>\n"
            "</manifest>\n";

    std::string xml1 = head + "IFoo/default" + tail;
    std::string xml2 = head + "IFoo/another" + tail;

    std::string error;
    HalManifest manifest1;
    manifest1.setFileName("1.xml");
    ASSERT_TRUE(fromXml(&manifest1, xml1, &error)) << error;
    HalManifest manifest2;
    manifest2.setFileName("2.xml");
    ASSERT_TRUE(fromXml(&manifest2, xml2, &error)) << error;

    ASSERT_TRUE(manifest1.addAll(&manifest2, &error)) << error;
}

// clang-format on

TEST_F(LibVintfTest, NativeGetHalNamesAndVersions) {
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <version>1.0</version>
                <interface>
                    <instance>inst</instance>
                </interface>
           </hal>
        </manifest>
    )";
    std::string error;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    auto names = manifest.getHalNamesAndVersions();
    ASSERT_EQ(1u, names.size());
    EXPECT_EQ("foo@1.0", *names.begin());
}

TEST_F(LibVintfTest, NativeGetHalNamesAndVersionsFqName) {
    HalManifest manifest;
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>foo</name>
                <fqname>@1.0/inst</fqname>
           </hal>
        </manifest>
    )";
    std::string error;
    EXPECT_TRUE(fromXml(&manifest, xml, &error)) << error;
    auto names = manifest.getHalNamesAndVersions();
    ASSERT_EQ(1u, names.size());
    EXPECT_EQ("foo@1.0", *names.begin());
}

// clang-format off

TEST_F(LibVintfTest, KernelInfoLevel) {
    std::string error;
    std::string xml = "<kernel version=\"3.18.31\" target-level=\"1\"/>\n";
    KernelInfo ki;
    ASSERT_TRUE(fromXml(&ki, xml, &error)) << error;
    EXPECT_EQ(Level{1}, getLevel(ki));
    EXPECT_EQ(xml, toXml(ki));
}

// Test merge of <kernel target-level=""> with autogenerated <kernel> by parsing
// kernel prebuilt.
TEST_F(LibVintfTest, HalManifestMergeKernel) {
    std::string head =
        "<manifest " + kMetaVersionStr + " type=\"device\" target-level=\"1\">\n";
    std::string tail =
        "</manifest>\n";
    std::string xml1 =
        "    <kernel target-level=\"2\"/>\n";
    std::string xml2 =
        "    <kernel version=\"3.18.31\">\n"
        "        <config>\n"
        "            <key>CONFIG_64BIT</key>\n"
        "            <value>y</value>\n"
        "        </config>\n"
        "    </kernel>\n";

    std::string error;
    HalManifest manifest1;
    HalManifest manifest2;
    ASSERT_TRUE(fromXml(&manifest1, head + xml1 + tail, &error)) << error;
    ASSERT_TRUE(fromXml(&manifest2, head + xml2 + tail, &error)) << error;
    ASSERT_TRUE(manifest1.addAll(&manifest2, &error)) << error;
    std::string merged_xml = toXml(manifest1);
    EXPECT_IN(head, merged_xml);
    EXPECT_IN("target-level=\"2\"", merged_xml);
    EXPECT_IN("version=\"3.18.31\"", merged_xml);
    EXPECT_IN("CONFIG_64BIT", merged_xml);
}

// clang-format on

TEST_F(LibVintfTest, FrameworkManifestHalMaxLevel) {
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="framework">
                           <hal max-level="3">
                               <name>android.frameworks.schedulerservice</name>
                               <transport>hwbinder</transport>
                               <fqname>@1.0::ISchedulingPolicyService/default</fqname>
                           </hal>
                           <hal format="aidl" max-level="4">
                               <name>android.frameworks.myaidl</name>
                               <fqname>IAidl/default</fqname>
                           </hal>
                           <hal format="native" max-level="5">
                               <name>some-native-hal</name>
                               <version>1.0</version>
                           </hal>
                       </manifest>)";

    std::string error;
    HalManifest manifest;
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    auto hals = getHals(manifest, "android.frameworks.schedulerservice");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(3)))));

    hals = getHals(manifest, "android.frameworks.myaidl");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(4)))));

    hals = getHals(manifest, "some-native-hal");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(5)))));
}

TEST_F(LibVintfTest, FrameworkManifestHalMinLevel) {
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="framework">
                           <hal min-level="3">
                               <name>android.frameworks.schedulerservice</name>
                               <transport>hwbinder</transport>
                               <fqname>@1.0::ISchedulingPolicyService/default</fqname>
                           </hal>
                           <hal format="aidl" min-level="4">
                               <name>android.frameworks.myaidl</name>
                               <fqname>IAidl/default</fqname>
                           </hal>
                           <hal format="native" min-level="5">
                               <name>some-native-hal</name>
                               <version>1.0</version>
                           </hal>
                       </manifest>)";

    std::string error;
    HalManifest manifest;
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    auto hals = getHals(manifest, "android.frameworks.schedulerservice");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(3)))));

    hals = getHals(manifest, "android.frameworks.myaidl");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(4)))));

    hals = getHals(manifest, "some-native-hal");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(5)))));
}

TEST_F(LibVintfTest, FrameworkManifestHalMinMaxLevel) {
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="framework">
                           <hal min-level="2" max-level="5">
                               <name>android.frameworks.schedulerservice</name>
                               <transport>hwbinder</transport>
                               <fqname>@1.0::ISchedulingPolicyService/default</fqname>
                           </hal>
                           <hal format="aidl" min-level="3" max-level="6">
                               <name>android.frameworks.myaidl</name>
                               <fqname>IAidl/default</fqname>
                           </hal>
                           <hal format="native" min-level="4" max-level="7">
                               <name>some-native-hal</name>
                               <version>1.0</version>
                           </hal>
                       </manifest>)";

    std::string error;
    HalManifest manifest;
    ASSERT_TRUE(fromXml(&manifest, xml, &error)) << error;

    auto hals = getHals(manifest, "android.frameworks.schedulerservice");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(2)))));
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(5)))));

    hals = getHals(manifest, "android.frameworks.myaidl");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(3)))));
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(6)))));

    hals = getHals(manifest, "some-native-hal");
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMinLevel, Eq(static_cast<Level>(4)))));
    EXPECT_THAT(hals, ElementsAre(Property(&ManifestHal::getMaxLevel, Eq(static_cast<Level>(7)))));
}

TEST_F(LibVintfTest, RuntimeInfoParseGkiKernelReleaseOk) {
    KernelVersion version;
    Level level = Level::UNSPECIFIED;
    EXPECT_EQ(OK, parseGkiKernelRelease(RuntimeInfo::FetchFlag::ALL, "5.4.42-android12-0-something",
                                        &version, &level));
    EXPECT_EQ(KernelVersion(5, 4, 42), version);
    EXPECT_EQ(Level::S, level);
}

TEST_F(LibVintfTest, RuntimeInfoParseGkiKernelReleaseVersionOnly) {
    KernelVersion version;
    EXPECT_EQ(OK, parseGkiKernelRelease(RuntimeInfo::FetchFlag::CPU_VERSION,
                                        "5.4.42-android12-0-something", &version, nullptr));
    EXPECT_EQ(KernelVersion(5, 4, 42), version);
}

TEST_F(LibVintfTest, RuntimeInfoParseGkiKernelReleaseLevelOnly) {
    Level level = Level::UNSPECIFIED;
    EXPECT_EQ(OK, parseGkiKernelRelease(RuntimeInfo::FetchFlag::KERNEL_FCM,
                                        "5.4.42-android12-0-something", nullptr, &level));
    EXPECT_EQ(Level::S, level);
}

TEST_F(LibVintfTest, RuntimeInfoParseGkiKernelReleaseLevelConsistent) {
    Level level = Level::S;
    EXPECT_EQ(OK, parseGkiKernelRelease(RuntimeInfo::FetchFlag::KERNEL_FCM,
                                        "5.4.42-android12-0-something", nullptr, &level));
    EXPECT_EQ(Level::S, level);
}

TEST_F(LibVintfTest, RuntimeInfoParseGkiKernelReleaseLevelInconsistent) {
    Level level = Level::R;
    EXPECT_EQ(UNKNOWN_ERROR,
              parseGkiKernelRelease(RuntimeInfo::FetchFlag::KERNEL_FCM,
                                    "5.4.42-android12-0-something", nullptr, &level));
}

// We bump level numbers for V, so check for consistency
TEST_F(LibVintfTest, RuntimeInfoGkiReleaseV) {
    Level level = Level::UNSPECIFIED;
    EXPECT_EQ(OK, parseGkiKernelRelease(RuntimeInfo::FetchFlag::KERNEL_FCM, "6.1.0-android15-0",
                                        nullptr, &level));
    EXPECT_EQ(Level::V, level);
}

class ManifestMissingITest : public LibVintfTest,
                             public ::testing::WithParamInterface<std::string> {
   public:
    static std::vector<std::string> createParams() {
        std::vector<std::string> ret;

        ret.push_back("<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="aidl">
                <name>android.hardware.foo</name>
                <version>1</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </manifest>)");

        ret.push_back("<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="hidl">
                <name>android.hardware.foo</name>
                <transport>hwbinder</transport>
                <version>1.0</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </manifest>)");

        ret.push_back("<manifest " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>android.hardware.foo</name>
                <version>1.0</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </manifest>)");

        return ret;
    }
};

TEST_P(ManifestMissingITest, CheckErrorMsg) {
    std::string xml = GetParam();
    HalManifest manifest;
    std::string error;
    ASSERT_FALSE(fromXml(&manifest, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr("Interface 'MyFoo' should have the format I[a-zA-Z0-9_]*")) << "\n"
                                                                                             << xml;
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, ManifestMissingITest,
                         ::testing::ValuesIn(ManifestMissingITest::createParams()));

struct ManifestMissingInterfaceTestParam {
    std::string xml;
    std::string expectedError;
};

class ManifestMissingInterfaceTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<ManifestMissingInterfaceTestParam> {
   public:
    static std::vector<ManifestMissingInterfaceTestParam> createParams() {
        std::vector<ManifestMissingInterfaceTestParam> ret;

        ret.emplace_back(ManifestMissingInterfaceTestParam{
            "<manifest " + kMetaVersionStr + R"( type="device">
                <hal format="aidl">
                    <name>android.hardware.foo</name>
                    <version>1</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
                </hal>
            </manifest>)",
            "Interface '' should have the format I[a-zA-Z0-9_]*",
        });

        ret.emplace_back(ManifestMissingInterfaceTestParam{
            "<manifest " + kMetaVersionStr + R"( type="device">
                <hal format="aidl">
                    <name>android.hardware.foo</name>
                    <version>1</version>
                    <fqname>/default</fqname>
                </hal>
            </manifest>)",
            "Could not parse text \"/default\" in element <fqname>",
        });

        ret.emplace_back(ManifestMissingInterfaceTestParam{
            "<manifest " + kMetaVersionStr + R"( type="device">
                <hal format="hidl">
                    <name>android.hardware.foo</name>
                    <transport>hwbinder</transport>
                    <version>1.0</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
                </hal>
            </manifest>)",
            "Interface '' should have the format I[a-zA-Z0-9_]*",
        });

        ret.emplace_back(ManifestMissingInterfaceTestParam{
            "<manifest " + kMetaVersionStr + R"( type="device">
                <hal format="hidl">
                    <name>android.hardware.foo</name>
                    <transport>hwbinder</transport>
                    <fqname>@1.0/default</fqname>
                </hal>
            </manifest>)",
            "Should specify interface: \"@1.0/default\"",
        });

        return ret;
    }
};

TEST_P(ManifestMissingInterfaceTest, CheckErrorMsg) {
    auto&& [xml, expectedError] = GetParam();
    HalManifest manifest;
    std::string error;
    ASSERT_FALSE(fromXml(&manifest, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr(expectedError)) << "\n" << xml;
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, ManifestMissingInterfaceTest,
                         ::testing::ValuesIn(ManifestMissingInterfaceTest::createParams()));

TEST_F(LibVintfTest, HalManifestInvalidPackage) {
    // If package name, interface or instance contains characters invalid to FqInstance,
    // it must be rejected because forEachInstance requires them to fit into FqInstance.
    std::string xml = "<manifest " + kMetaVersionStr + R"( type="framework">
                           <hal format="aidl">
                               <name>not_a_valid_package!</name>
                               <version>1</version>
                               <interface>
                                   <name>MyFoo</name>
                                   <instance>default</instance>
                               </interface>
                           </hal>
                       </manifest>)";
    HalManifest manifest;
    std::string error;
    ASSERT_FALSE(fromXml(&manifest, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr("not_a_valid_package!"));
}

class MatrixMissingITest : public LibVintfTest, public ::testing::WithParamInterface<std::string> {
   public:
    static std::vector<std::string> createParams() {
        std::vector<std::string> ret;

        ret.push_back("<compatibility-matrix " + kMetaVersionStr + R"( type="device">
            <hal format="aidl">
                <name>android.hardware.foo</name>
                <version>1</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </compatibility-matrix>)");

        ret.push_back("<compatibility-matrix " + kMetaVersionStr + R"( type="device">
            <hal format="hidl">
                <name>android.hardware.foo</name>
                <version>1.0</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </compatibility-matrix>)");

        ret.push_back("<compatibility-matrix " + kMetaVersionStr + R"( type="device">
            <hal format="native">
                <name>android.hardware.foo</name>
                <version>1.0</version>
                <interface>
                    <name>MyFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
        </compatibility-matrix>)");

        return ret;
    }
};

TEST_P(MatrixMissingITest, CheckErrorMsg) {
    std::string xml = GetParam();
    CompatibilityMatrix matrix;
    std::string error;
    ASSERT_FALSE(fromXml(&matrix, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr("Interface 'MyFoo' should have the format I[a-zA-Z0-9_]*"));
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, MatrixMissingITest,
                         ::testing::ValuesIn(MatrixMissingITest::createParams()));

struct MatrixMissingInterfaceTestParam {
    std::string xml;
    std::string expectedError;
};

class MatrixMissingInterfaceTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<MatrixMissingInterfaceTestParam> {
   public:
    static std::vector<MatrixMissingInterfaceTestParam> createParams() {
        std::vector<MatrixMissingInterfaceTestParam> ret;

        ret.emplace_back(MatrixMissingInterfaceTestParam{
            "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
                <hal format="aidl">
                    <name>android.hardware.foo</name>
                    <version>1</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
                </hal>
            </compatibility-matrix>)",
            "Interface '' should have the format I[a-zA-Z0-9_]*",
        });

        ret.emplace_back(MatrixMissingInterfaceTestParam{
            "<compatibility-matrix " + kMetaVersionStr + R"( type="device">
                <hal format="hidl">
                    <name>android.hardware.foo</name>
                    <version>1.0</version>
                    <interface>
                        <instance>default</instance>
                    </interface>
                </hal>
            </compatibility-matrix>)",
            "Interface '' should have the format I[a-zA-Z0-9_]*",
        });

        return ret;
    }
};

TEST_P(MatrixMissingInterfaceTest, CheckErrorMsg) {
    auto&& [xml, expectedError] = GetParam();
    CompatibilityMatrix matrix;
    std::string error;
    ASSERT_FALSE(fromXml(&matrix, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr(expectedError)) << "\n" << xml;
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, MatrixMissingInterfaceTest,
                         ::testing::ValuesIn(MatrixMissingInterfaceTest::createParams()));

TEST_F(LibVintfTest, CompatibilityMatrixInvalidPackage) {
    // If package name, interface or instance contains characters invalid to FqInstance,
    // it must be rejected because forEachInstance requires them to fit into FqInstance.
    std::string xml = "<compatibility-matrix " + kMetaVersionStr + R"( type="framework">
                           <hal format="aidl">
                               <name>not_a_valid_package!</name>
                               <version>1-2</version>
                               <interface>
                                   <name>MyFoo</name>
                                   <instance>default</instance>
                               </interface>
                           </hal>
                       </compatibility-matrix>)";
    CompatibilityMatrix matrix;
    std::string error;
    ASSERT_FALSE(fromXml(&matrix, xml, &error)) << "Should not be valid:\n" << xml;
    EXPECT_THAT(error, HasSubstr("not_a_valid_package!"));
}

struct DupInterfaceAndFqnameTestParam {
    HalFormat format;
    std::string footer;
    std::string halName;
};

class DupInterfaceAndFqnameTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<DupInterfaceAndFqnameTestParam> {
   public:
    static std::vector<DupInterfaceAndFqnameTestParam> createParams() {
        std::vector<DupInterfaceAndFqnameTestParam> ret;

        std::string hidlFooter = R"(
    <hal>
        <name>android.hardware.nfc</name>
        <transport>hwbinder</transport>
        <version>1.0</version>
        <interface>
            <name>INfc</name>
            <instance>default</instance>
        </interface>
        <fqname>@1.0::INfc/default</fqname>
    </hal>
</manifest>
)";

        std::string aidlFooter = R"(
    <hal format="aidl">
        <name>android.hardware.nfc</name>
        <interface>
            <name>INfc</name>
            <instance>default</instance>
        </interface>
        <fqname>INfc/default</fqname>
    </hal>
</manifest>
)";

        return {
            {HalFormat::HIDL, hidlFooter, "android.hardware.nfc@1.0::INfc/default"},
            {HalFormat::AIDL, aidlFooter, "android.hardware.nfc.INfc/default"},
        };
    }
    static std::string getTestSuffix(const TestParamInfo<ParamType>& info) {
        return to_string(info.param.format);
    }
};

TEST_P(DupInterfaceAndFqnameTest, Test5_0) {
    auto&& [_, footer, halName] = GetParam();
    std::string xml = R"(<manifest version="5.0" type="device">)" + footer;
    HalManifest vm;
    std::string error;
    EXPECT_TRUE(fromXml(&vm, xml, &error))
        << "<fqname> and <interface> are allowed to exist "
           "together for the same instance for libvintf 5.0, but error is: "
        << error;
}

TEST_P(DupInterfaceAndFqnameTest, Test6_0) {
    auto&& [_, footer, halName] = GetParam();
    std::string xml = R"(<manifest version=")" + to_string(kMetaVersionNoHalInterfaceInstance) +
                      R"(" type="device">)" + footer;
    HalManifest vm;
    std::string error;
    EXPECT_FALSE(fromXml(&vm, xml, &error));
    EXPECT_THAT(error,
                HasSubstr("Duplicated " + halName + " in <interface><instance> and <fqname>."))
        << "<fqname> and <interface> are not allowed to exist "
           "together for the same instance for libvintf "
        << kMetaVersionNoHalInterfaceInstance << ".";
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, DupInterfaceAndFqnameTest,
                         ::testing::ValuesIn(DupInterfaceAndFqnameTest::createParams()),
                         &DupInterfaceAndFqnameTest::getTestSuffix);

struct AllowDupMajorVersionTestParam {
    std::string testName;
    std::string expectedError;
    std::string footer;
};

class AllowDupMajorVersionTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<AllowDupMajorVersionTestParam> {
   public:
    static std::vector<AllowDupMajorVersionTestParam> createParams() {
        std::vector<AllowDupMajorVersionTestParam> ret;
        ret.push_back({"HidlInterfaceAndFqName", "Duplicated major version", R"(
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <version>1.0</version>
                    <interface>
                        <name>INfc</name>
                        <instance>default</instance>
                    </interface>
                    <fqname>@1.1::INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        ret.push_back({"HidlFqNameInTheSameHal", "Duplicated major version", R"(
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <fqname>@1.0::INfc/default</fqname>
                    <fqname>@1.1::INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        ret.push_back({"HidlFqNameInDifferentHals", "Conflicting FqInstance", R"(
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <fqname>@1.0::INfc/default</fqname>
                </hal>
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <fqname>@1.1::INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        ret.push_back({"HidlInterfaceAndFqNameInDifferentHals", "Conflicting FqInstance", R"(
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <version>1.0</version>
                    <interface>
                        <name>INfc</name>
                        <instance>default</instance>
                    </interface>
                </hal>
                <hal>
                    <name>android.hardware.nfc</name>
                    <transport>hwbinder</transport>
                    <fqname>@1.1::INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        ret.push_back({"AidlInterfaceInDifferentHals", "Conflicting FqInstance", R"(
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>1</version>
                    <interface>
                        <name>INfc</name>
                        <instance>default</instance>
                    </interface>
                </hal>
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>2</version>
                    <interface>
                        <name>INfc</name>
                        <instance>default</instance>
                    </interface>
                </hal>
            </manifest>
            )"});
        ret.push_back({"AidlFqNameInDifferentHals", "Conflicting FqInstance", R"(
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>1</version>
                    <fqname>INfc/default</fqname>
                </hal>
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>2</version>
                    <fqname>INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        ret.push_back({"AidlInterfaceAndFqNameInDifferentHals", "Conflicting FqInstance", R"(
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>1</version>
                    <interface>
                        <name>INfc</name>
                        <instance>default</instance>
                    </interface>
                </hal>
                <hal format="aidl">
                    <name>android.hardware.nfc</name>
                    <version>2</version>
                    <fqname>INfc/default</fqname>
                </hal>
            </manifest>
            )"});
        return ret;
    }
    static std::string getTestSuffix(const TestParamInfo<ParamType>& info) {
        return info.param.testName;
    }
};

TEST_P(AllowDupMajorVersionTest, Allow5_0) {
    auto&& [_, expectedError, footer] = GetParam();
    std::string xml = R"(<manifest version="5.0" type="device">)" + footer;
    HalManifest vm;
    std::string error;
    EXPECT_TRUE(fromXml(&vm, xml, &error))
        << "Conflicting major version in <fqname> is allowed in libvintf 5.0. However, error is: "
        << error;
}

TEST_P(AllowDupMajorVersionTest, DoNotAllow6_0) {
    auto&& [_, expectedError, footer] = GetParam();
    std::string xml = R"(<manifest version=")" + to_string(kMetaVersionNoHalInterfaceInstance) +
                      R"(" type="device">)" + footer;
    HalManifest vm;
    std::string error;
    EXPECT_FALSE(fromXml(&vm, xml, &error));
    EXPECT_THAT(error, HasSubstr(expectedError));
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, AllowDupMajorVersionTest,
                         ::testing::ValuesIn(AllowDupMajorVersionTest::createParams()),
                         &AllowDupMajorVersionTest::getTestSuffix);

struct InterfaceMissingInstanceTestParam {
    HalFormat format;
    std::string footer;
};

class InterfaceMissingInstanceTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<InterfaceMissingInstanceTestParam> {
   public:
    static std::vector<InterfaceMissingInstanceTestParam> createParams() {
        std::vector<InterfaceMissingInstanceTestParam> ret;

        std::string hidlFooter = R"(
    <hal>
        <name>android.hardware.nfc</name>
        <transport>hwbinder</transport>
        <version>1.0</version>
        <interface>
            <name>INfc</name>
        </interface>
    </hal>
</manifest>
)";
        std::string aidlFooter = R"(
    <hal format="aidl">
        <name>android.hardware.nfc</name>
        <interface>
            <name>INfc</name>
        </interface>
    </hal>
</manifest>
)";

        return {{HalFormat::HIDL, hidlFooter}, {HalFormat::AIDL, aidlFooter}};
    }
    static std::string getTestSuffix(const TestParamInfo<ParamType>& info) {
        return to_string(info.param.format);
    }
};

TEST_P(InterfaceMissingInstanceTest, Test5_0) {
    auto&& [testName, footer] = GetParam();
    std::string header = R"(<manifest version="5.0" type="device">)";
    std::string xml = header + footer;
    HalManifest vm;
    std::string error;
    EXPECT_TRUE(fromXml(&vm, xml, &error)) << error;
}

TEST_P(InterfaceMissingInstanceTest, Test6_0) {
    auto&& [testName, footer] = GetParam();
    std::string header = R"(<manifest version=")" + to_string(kMetaVersionNoHalInterfaceInstance) +
                         R"(" type="device">)";
    std::string xml = header + footer;
    HalManifest vm;
    std::string error;
    EXPECT_FALSE(fromXml(&vm, xml, &error));
    EXPECT_THAT(error, HasSubstr("<hal> android.hardware.nfc <interface> INfc has no <instance>."));
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, InterfaceMissingInstanceTest,
                         ::testing::ValuesIn(InterfaceMissingInstanceTest::createParams()),
                         &InterfaceMissingInstanceTest::getTestSuffix);

struct ManifestHalNoInstanceTestParam {
    HalFormat format;
    std::string footer;
};

class ManifestHalNoInstanceTest
    : public LibVintfTest,
      public ::testing::WithParamInterface<ManifestHalNoInstanceTestParam> {
   public:
    static std::vector<ManifestHalNoInstanceTestParam> createParams() {
        std::vector<ManifestHalNoInstanceTestParam> ret;

        std::string hidlFooter = R"(
    <hal>
        <name>android.hardware.nfc</name>
        <transport>hwbinder</transport>
        <version>1.0</version>
    </hal>
</manifest>
)";
        std::string aidlFooter = R"(
    <hal format="aidl">
        <name>android.hardware.nfc</name>
    </hal>
</manifest>
)";

        return {{HalFormat::HIDL, hidlFooter}, {HalFormat::AIDL, aidlFooter}};
    }
    static std::string getTestSuffix(const TestParamInfo<ParamType>& info) {
        return to_string(info.param.format);
    }
};

TEST_P(ManifestHalNoInstanceTest, Test5_0) {
    auto&& [testName, footer] = GetParam();
    std::string header = R"(<manifest version="5.0" type="device">)";
    std::string xml = header + footer;
    HalManifest vm;
    std::string error;
    EXPECT_TRUE(fromXml(&vm, xml, &error)) << error;
}

TEST_P(ManifestHalNoInstanceTest, Test6_0) {
    auto&& [testName, footer] = GetParam();
    std::string header = R"(<manifest version=")" + to_string(kMetaVersionNoHalInterfaceInstance) +
                         R"(" type="device">)";
    std::string xml = header + footer;
    HalManifest vm;
    std::string error;
    EXPECT_FALSE(fromXml(&vm, xml, &error));
    EXPECT_THAT(error,
                HasSubstr("<hal> android.hardware.nfc has no instance. Fix by adding <fqname>."));
}

INSTANTIATE_TEST_SUITE_P(LibVintfTest, ManifestHalNoInstanceTest,
                         ::testing::ValuesIn(ManifestHalNoInstanceTest::createParams()),
                         &ManifestHalNoInstanceTest::getTestSuffix);

// clang-format off

struct FrameworkCompatibilityMatrixCombineTest : public LibVintfTest {
    virtual void SetUp() override {
        matrices.resize(2);
        matrices[0].setFileName("compatibility_matrix.1_1.xml");
        matrices[1].setFileName("compatibility_matrix.1_2.xml");
    }
    // Access to private methods.
    std::unique_ptr<CompatibilityMatrix> combine(Level deviceLevel,
                                                 std::vector<CompatibilityMatrix>* theMatrices,
                                                 std::string* errorPtr) {
        return CompatibilityMatrix::combine(deviceLevel, Level::UNSPECIFIED, theMatrices, errorPtr);
    }
    std::unique_ptr<CompatibilityMatrix> combine(Level deviceLevel, Level kernellevel,
                                                 std::vector<CompatibilityMatrix>* theMatrices,
                                                 std::string* errorPtr) {
        return CompatibilityMatrix::combine(deviceLevel, kernellevel, theMatrices, errorPtr);
    }

    std::vector<CompatibilityMatrix> matrices;
    std::string error;
};

// Combining framework compatibility matrix with conflicting minlts fails
TEST_F(FrameworkCompatibilityMatrixCombineTest, ConflictMinlts) {
    ASSERT_TRUE(fromXml(
        &matrices[0],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <kernel version=\"3.18.5\" />\n"
        "</compatibility-matrix>\n",
        &error))
        << error;
    ASSERT_TRUE(fromXml(
        &matrices[1],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <kernel version=\"3.18.6\" />\n"
        "</compatibility-matrix>\n",
        &error))
        << error;

    auto combined = combine(Level{1}, &matrices, &error);
    ASSERT_EQ(nullptr, combined) << toXml(*combined);
    EXPECT_IN("Kernel version mismatch", error);
}

// <kernel> without <conditions> always comes first
TEST_F(FrameworkCompatibilityMatrixCombineTest, KernelNoConditions) {
    std::string conditionedKernel =
        "    <kernel version=\"3.18.5\" level=\"1\">\n"
        "        <conditions>\n"
        "            <config>\n"
        "                <key>CONFIG_ARM</key>\n"
        "                <value type=\"tristate\">y</value>\n"
        "            </config>\n"
        "        </conditions>\n"
        "        <config>\n"
        "            <key>CONFIG_FOO</key>\n"
        "            <value type=\"tristate\">y</value>\n"
        "        </config>\n"
        "    </kernel>\n";
    std::string simpleKernel =
        "    <kernel version=\"3.18.5\" level=\"1\">\n"
        "        <config>\n"
        "            <key>CONFIG_BAR</key>\n"
        "            <value type=\"tristate\">y</value>\n"
        "        </config>\n"
        "    </kernel>\n";

    ASSERT_TRUE(fromXml(
        &matrices[0],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <kernel version=\"3.18.5\" />\n" +
            conditionedKernel + "</compatibility-matrix>\n",
        &error))
        << error;
    ASSERT_TRUE(fromXml(
        &matrices[1],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n" + simpleKernel +
            "</compatibility-matrix>\n",
        &error))
        << error;

    auto combined = combine(Level{1}, &matrices, &error);
    ASSERT_NE(nullptr, combined);
    EXPECT_EQ("", error);
    EXPECT_EQ("<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n" +
                  simpleKernel + conditionedKernel + "</compatibility-matrix>\n",
              toXml(*combined));
}

// Combining framework compatibility matrix with conflicting sepolicy fails
TEST_F(FrameworkCompatibilityMatrixCombineTest, ConflictSepolicy) {
    ASSERT_TRUE(fromXml(
        &matrices[0],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "</compatibility-matrix>\n",
        &error))
        << error;
    ASSERT_TRUE(fromXml(
        &matrices[1],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>29</kernel-sepolicy-version>\n"
        "    </sepolicy>\n"
        "</compatibility-matrix>\n",
        &error))
        << error;

    auto combined = combine(Level{1}, &matrices, &error);
    ASSERT_EQ(nullptr, combined) << toXml(*combined);
    EXPECT_IN("<sepolicy> is already defined", error);
}

// Combining framework compatibility matrix with conflicting avb fails
TEST_F(FrameworkCompatibilityMatrixCombineTest, ConflictAvb) {
    ASSERT_TRUE(fromXml(
        &matrices[0],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <avb>\n"
        "        <vbmeta-version>1.1</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n",
        &error))
        << error;
    ASSERT_TRUE(fromXml(
        &matrices[1],
        "<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"
        "    <avb>\n"
        "        <vbmeta-version>1.0</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n",
        &error))
        << error;

    auto combined = combine(Level{1}, &matrices, &error);
    ASSERT_EQ(nullptr, combined) << toXml(*combined);
    EXPECT_IN("<avb><vbmeta-version> is already defined", error);
}

TEST_F(FrameworkCompatibilityMatrixCombineTest, AidlAndHidlNames) {
    std::string head1{"<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"1\">\n"};
    std::string head2{"<compatibility-matrix " + kMetaVersionStr + " type=\"framework\" level=\"2\">\n"};
    std::string tail{"</compatibility-matrix>\n"};
    std::string aidl =
        "    <hal format=\"aidl\" optional=\"false\">\n"
        "        <name>android.system.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n";
    std::string hidl =
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.system.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n";
    std::string aidlOptional = std::string(aidl).replace(hidl.find("false"), 5, "true");
    std::string hidlOptional = std::string(hidl).replace(hidl.find("false"), 5, "true");
    std::string error;
    {
        ASSERT_TRUE(fromXml(&matrices[0], head1 + aidl + tail, &error))
            << error;
        ASSERT_TRUE(fromXml(&matrices[1], head1 + hidl + tail, &error))
            << error;

        auto combined = combine(Level{1}, &matrices, &error);
        ASSERT_NE(nullptr, combined) << error;

        auto combinedXml = toXml(*combined);
        EXPECT_IN(aidl, combinedXml);
        EXPECT_IN(hidl, combinedXml);
    }
    {
        ASSERT_TRUE(fromXml(&matrices[0], head1 + aidl + tail, &error))
            << error;
        ASSERT_TRUE(fromXml(&matrices[1], head2 + hidl + tail, &error))
            << error;

        auto combined = combine(Level{1}, &matrices, &error);
        ASSERT_NE(nullptr, combined) << error;

        auto combinedXml = toXml(*combined);
        EXPECT_IN(aidl, combinedXml);
        EXPECT_IN(hidlOptional, combinedXml);
    }
    {
        ASSERT_TRUE(fromXml(&matrices[0], head2 + aidl + tail, &error))
            << error;
        ASSERT_TRUE(fromXml(&matrices[1], head1 + hidl + tail, &error))
            << error;

        auto combined = combine(Level{1}, &matrices, &error);
        ASSERT_NE(nullptr, combined) << error;

        auto combinedXml = toXml(*combined);
        EXPECT_IN(aidlOptional, combinedXml);
        EXPECT_IN(hidl, combinedXml);
    }
}

// clang-format on

class FcmCombineKernelTest : public FrameworkCompatibilityMatrixCombineTest,
                             public ::testing::WithParamInterface<std::tuple<size_t, size_t>> {
   public:
    static std::string PrintTestParams(const TestParamInfo<FcmCombineKernelTest::ParamType>& info) {
        auto [deviceLevelNum, kernelLevelNum] = info.param;
        return "device_" + std::to_string(deviceLevelNum) + "_kernel_" +
               std::to_string(kernelLevelNum);
    }
    static constexpr size_t kMinLevel = 1;
    static constexpr size_t kMaxLevel = 5;
};

TEST_P(FcmCombineKernelTest, OlderKernel) {
    auto [deviceLevelNum, kernelLevelNum] = GetParam();

    std::vector<size_t> levelNums;
    for (size_t i = kMinLevel; i <= kMaxLevel; ++i) levelNums.push_back(i);

    constexpr auto fmt = R"(
        <compatibility-matrix %s type="framework" level="%s">
            <hal format="hidl" optional="false">
                <name>android.system.foo</name>
                <version>%zu.0</version>
                <interface>
                    <name>IFoo</name>
                    <instance>default</instance>
                </interface>
            </hal>
            <kernel version="%zu.0.0">
                <config>
                    <key>CONFIG_%zu</key>
                    <value type="tristate">y</value>
                </config>
            </kernel>
        </compatibility-matrix>
    )";
    std::string error;
    std::vector<CompatibilityMatrix> matrices;
    for (size_t levelNum : levelNums) {
        auto levelStr = android::vintf::to_string((Level)levelNum);
        auto xml = StringPrintf(fmt, kMetaVersionStr.c_str(), levelStr.c_str(), levelNum, levelNum,
                                levelNum);
        CompatibilityMatrix& matrix = matrices.emplace_back();
        ASSERT_TRUE(fromXml(&matrix, xml, &error)) << error;
    }
    ASSERT_FALSE(matrices.empty());

    auto combined = combine(Level(deviceLevelNum), Level(kernelLevelNum), &matrices, &error);
    ASSERT_NE(nullptr, combined);
    auto combinedXml = toXml(*combined);

    // Check that HALs are combined correctly.
    for (size_t i = kMinLevel; i < deviceLevelNum; ++i)
        EXPECT_THAT(combinedXml, Not(HasSubstr(StringPrintf("<version>%zu.0</version>", i))));

    for (size_t i = deviceLevelNum; i <= kMaxLevel; ++i)
        EXPECT_THAT(combinedXml, HasSubstr(StringPrintf("<version>%zu.0</version>", i)));

    // Check that kernels are combined correctly. <kernel> tags from
    // matrices with level >= min(kernelLevel, deviceLevel) are added.
    // The "level" tag on <kernel> must also be set properly so that old kernel requirements from
    // deviceLevel <= x < kernelLevel won't be used.
    auto hasKernelFrom = std::min(kernelLevelNum, deviceLevelNum);
    for (size_t i = kMinLevel; i < hasKernelFrom; ++i) {
        EXPECT_THAT(combinedXml,
                    Not(HasSubstr(StringPrintf(R"(<kernel version="%zu.0.0" level="%zu")", i, i))));
        EXPECT_THAT(combinedXml, Not(HasSubstr(StringPrintf("CONFIG_%zu", i))));
    }

    for (size_t i = hasKernelFrom; i <= kMaxLevel; ++i) {
        EXPECT_THAT(combinedXml,
                    HasSubstr(StringPrintf(R"(<kernel version="%zu.0.0" level="%zu")", i, i)));
        EXPECT_THAT(combinedXml, HasSubstr(StringPrintf("CONFIG_%zu", i)));
    }

    if (::testing::Test::HasFailure()) ADD_FAILURE() << "Resulting matrix is \n" << combinedXml;
}

INSTANTIATE_TEST_CASE_P(
    FrameworkCompatibilityMatrixCombineTest, FcmCombineKernelTest,
    Combine(Range(FcmCombineKernelTest::kMinLevel, FcmCombineKernelTest::kMaxLevel + 1),
            Range(FcmCombineKernelTest::kMinLevel, FcmCombineKernelTest::kMaxLevel + 1)),
    FcmCombineKernelTest::PrintTestParams);

// clang-format off

struct DeviceCompatibilityMatrixCombineTest : public LibVintfTest {
    virtual void SetUp() override {
        matrices.resize(2);
        matrices[0].setFileName("compatibility_matrix.1.xml");
        matrices[1].setFileName("compatibility_matrix.2.xml");
    }
    // Access to private methods.
    std::unique_ptr<CompatibilityMatrix> combine(std::vector<CompatibilityMatrix>* theMatrices,
                                                 std::string* errorPtr) {
        return CompatibilityMatrix::combineDeviceMatrices(theMatrices, errorPtr);
    }

    std::vector<CompatibilityMatrix> matrices;
    std::string error;
};

TEST_F(DeviceCompatibilityMatrixCombineTest, Success) {
    std::string head{"<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"};
    std::string tail{"</compatibility-matrix>\n"};
    std::string halFoo{
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"};
    std::string halBar{
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.bar</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IBar</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"};
    ASSERT_TRUE(fromXml(&matrices[0], head + halFoo + tail, &error))
        << error;
    ASSERT_TRUE(fromXml(&matrices[1], head + halBar + tail, &error))
        << error;

    auto combined = combine(&matrices, &error);
    ASSERT_NE(nullptr, combined) << error;
    EXPECT_EQ("", error);
    auto combinedXml = toXml(*combined);
    EXPECT_IN(halFoo, combinedXml);
    EXPECT_IN(halBar, combinedXml);
}

TEST_F(DeviceCompatibilityMatrixCombineTest, ConflictVendorNdk) {
    std::string vendorNdkP{
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <vendor-ndk>\n"
        "        <version>P</version>\n"
        "    </vendor-ndk>\n"
        "</compatibility-matrix>\n"};
    std::string vendorNdkQ{
        "<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"
        "    <vendor-ndk>\n"
        "        <version>Q</version>\n"
        "    </vendor-ndk>\n"
        "</compatibility-matrix>\n"};
    ASSERT_TRUE(fromXml(&matrices[0], vendorNdkP, &error)) << error;
    ASSERT_TRUE(fromXml(&matrices[1], vendorNdkQ, &error)) << error;

    auto combined = combine(&matrices, &error);
    ASSERT_EQ(nullptr, combined) << toXml(*combined);
    EXPECT_IN("<vendor-ndk> is already defined", error);
}

TEST_F(DeviceCompatibilityMatrixCombineTest, AidlAndHidlNames) {
    std::string head{"<compatibility-matrix " + kMetaVersionStr + " type=\"device\">\n"};
    std::string tail{"</compatibility-matrix>\n"};
    std::string aidl =
        "    <hal format=\"aidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n";
    std::string hidl =
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.system.foo</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n";
    ASSERT_TRUE(fromXml(&matrices[0], head + aidl + tail, &error))
        << error;
    ASSERT_TRUE(fromXml(&matrices[1], head + hidl + tail, &error))
        << error;

    auto combined = combine(&matrices, &error);
    ASSERT_NE(nullptr, combined) << error;

    auto combinedXml = toXml(*combined);
    EXPECT_IN(aidl, combinedXml);
    EXPECT_IN(hidl, combinedXml);
}

// clang-format on

} // namespace vintf
} // namespace android

int main(int argc, char **argv) {
    ::testing::InitGoogleMock(&argc, argv);
    return RUN_ALL_TESTS();
}
