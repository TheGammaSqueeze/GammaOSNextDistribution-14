#include <gtest/gtest.h>
#include <cstdlib>
#include <string>

#include "gtest_helper.h"

class CoreGetClientTests : public ::testing::Test {
    public:
    const char* testBinaryName = "core_getclient";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(CoreGetClientTests, TestGetClient) {
    runTest(testBinaryName);
}
