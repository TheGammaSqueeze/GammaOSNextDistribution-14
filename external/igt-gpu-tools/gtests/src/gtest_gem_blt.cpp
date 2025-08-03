#include <gtest/gtest.h>
#include <cstdlib>
#include <string>

#include "gtest_helper.h"

class GemBltTests : public ::testing::Test {
    public:
    const char* testBinaryName = "gem_blt";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(GemBltTests, TestGemBlt) {
    runTest(testBinaryName);
}
