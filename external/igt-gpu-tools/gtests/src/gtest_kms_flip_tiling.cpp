#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsFlipTiling : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_flip_tiling";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsFlipTiling, TestFlipChangesTiling) {
    runSubTest(testBinaryName, "flip-changes-tiling");
}

TEST_F(KmsFlipTiling, TestFlipChangesTilingY) {
    runSubTest(testBinaryName, "flip-changes-tiling-Y");
}

TEST_F(KmsFlipTiling, TestFlipChangesTilingYF) {
    runSubTest(testBinaryName, "flip-changes-tiling-Yf");
}

TEST_F(KmsFlipTiling, TestFlipXTiled) {
    runSubTest(testBinaryName, "flip-X-tiled");
}

TEST_F(KmsFlipTiling, TestFlipYTiled) {
    runSubTest(testBinaryName, "flip-Y-tiled");
}

TEST_F(KmsFlipTiling, TestFlipYFTiled) {
    runSubTest(testBinaryName, "flip-Yf-tiled");
}

TEST_F(KmsFlipTiling, TestFlipToXTiled) {
    runSubTest(testBinaryName, "flip-to-X-tiled");
}

TEST_F(KmsFlipTiling, TestFlipToYTiled) {
    runSubTest(testBinaryName, "flip-to-Y-tiled");
}

TEST_F(KmsFlipTiling, TestFlipToYFTiled) {
    runSubTest(testBinaryName, "flip-to-Yf-tiled");
}
