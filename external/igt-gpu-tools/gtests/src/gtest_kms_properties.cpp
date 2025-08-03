#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsPropertiesTests : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_properties";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsPropertiesTests, TestPlanePropertiesLegacy) {
    runSubTest(testBinaryName, "plane-properties-legacy");
}

TEST_F(KmsPropertiesTests, TestPlanePropertiesAtomic) {
    runSubTest(testBinaryName, "plane-properties-atomic");
}

TEST_F(KmsPropertiesTests, TestCrtcPropertiesLegacy) {
    runSubTest(testBinaryName, "crtc-properties-legacy");
}

TEST_F(KmsPropertiesTests, TestCrtcPropertiesAtomic) {
    runSubTest(testBinaryName, "crtc-properties-atomic");
}

TEST_F(KmsPropertiesTests, TestConnectorPropertiesLegacy) {
    runSubTest(testBinaryName, "connector-properties-legacy");
}

TEST_F(KmsPropertiesTests, TestConnectorPropertiesAtomic) {
    runSubTest(testBinaryName, "connector-properties-atomic");
}

TEST_F(KmsPropertiesTests, TestInvalidPropertiesLegacy) {
    runSubTest(testBinaryName, "invalid-properties-legacy");
}

TEST_F(KmsPropertiesTests, TestInvalidPropertiesAtomic) {
    runSubTest(testBinaryName, "invalid-properties-atomic");
}

TEST_F(KmsPropertiesTests, TestGetPropertiesSanityAtomic) {
    runSubTest(testBinaryName, "get_properties-sanity-atomic");
}

TEST_F(KmsPropertiesTests, TestGetPropertiesSanityNonAtomic) {
    runSubTest(testBinaryName, "get_properties-sanity-non-atomic");
}
