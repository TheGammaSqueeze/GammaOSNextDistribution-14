#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsPlaneLowres : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_plane_lowres";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsPlaneLowres, TestPipeATilingNone) {
    runSubTest(testBinaryName, "pipe-A-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeATilingX) {
    runSubTest(testBinaryName, "pipe-A-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeATilingY) {
    runSubTest(testBinaryName, "pipe-A-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeATilingYf) {
    runSubTest(testBinaryName, "pipe-A-tiling-yf");
}

TEST_F(KmsPlaneLowres, TestPipeBTilingNone) {
    runSubTest(testBinaryName, "pipe-B-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeBTilingX) {
    runSubTest(testBinaryName, "pipe-B-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeBTilingY) {
    runSubTest(testBinaryName, "pipe-B-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeBTilingYf) {
    runSubTest(testBinaryName, "pipe-B-tiling-yf");
}

TEST_F(KmsPlaneLowres, TestPipeCTilingNone) {
    runSubTest(testBinaryName, "pipe-C-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeCTilingX) {
    runSubTest(testBinaryName, "pipe-C-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeCTilingY) {
    runSubTest(testBinaryName, "pipe-C-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeCTilingYf) {
    runSubTest(testBinaryName, "pipe-C-tiling-yf");
}

TEST_F(KmsPlaneLowres, TestPipeDTilingNone) {
    runSubTest(testBinaryName, "pipe-D-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeDTilingX) {
    runSubTest(testBinaryName, "pipe-D-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeDTilingY) {
    runSubTest(testBinaryName, "pipe-D-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeDTilingYf) {
    runSubTest(testBinaryName, "pipe-D-tiling-yf");
}

TEST_F(KmsPlaneLowres, TestPipeETilingNone) {
    runSubTest(testBinaryName, "pipe-E-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeETilingX) {
    runSubTest(testBinaryName, "pipe-E-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeETilingY) {
    runSubTest(testBinaryName, "pipe-E-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeETilingYf) {
    runSubTest(testBinaryName, "pipe-E-tiling-yf");
}

TEST_F(KmsPlaneLowres, TestPipeFTilingNone) {
    runSubTest(testBinaryName, "pipe-F-tiling-none");
}

TEST_F(KmsPlaneLowres, TestPipeFTilingX) {
    runSubTest(testBinaryName, "pipe-F-tiling-x");
}

TEST_F(KmsPlaneLowres, TestPipeFTilingY) {
    runSubTest(testBinaryName, "pipe-F-tiling-y");
}

TEST_F(KmsPlaneLowres, TestPipeFTilingYf) {
    runSubTest(testBinaryName, "pipe-F-tiling-yf");
}
