#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class CoreAuthTests : public ::testing::Test {
    public:
    const char* testBinaryName = "core_auth";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(CoreAuthTests, TestGetclientSimple) {
    runSubTest(testBinaryName, "getclient-simple");
}

TEST_F(CoreAuthTests, TestGetclientMasterDrop) {
    runSubTest(testBinaryName, "getclient-master-drop");
}

TEST_F(CoreAuthTests, TestBasicAuth) {
    runSubTest(testBinaryName, "basic-auth");
}

TEST_F(CoreAuthTests, TestManyMagics) {
    runSubTest(testBinaryName, "many-magics");
}
