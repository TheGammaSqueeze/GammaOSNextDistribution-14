#include <gtest/gtest.h>
#include <cstdlib>
#include <string>

#include "gtest_helper.h"

class KMSThroughputTests : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_throughput";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KMSThroughputTests, TestKMSThroughput) {
    runTest(testBinaryName);
}
