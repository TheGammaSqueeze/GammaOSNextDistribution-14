#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsAddfbBasic : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_addfb_basic";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsAddfbBasic, TestUnusedHandle) {
    runSubTest(testBinaryName, "unused-handle");
}

TEST_F(KmsAddfbBasic, TestUnusedPitches) {
    runSubTest(testBinaryName, "unused-pitches");
}

TEST_F(KmsAddfbBasic, TestUnusedOffsets) {
    runSubTest(testBinaryName, "unused-offsets");
}

TEST_F(KmsAddfbBasic, TestUnusedModifier) {
    runSubTest(testBinaryName, "unused-modifier");
}

TEST_F(KmsAddfbBasic, TestClobberredModifier) {
    runSubTest(testBinaryName, "clobberred-modifier");
}

TEST_F(KmsAddfbBasic, TestLegacyFormat) {
    runSubTest(testBinaryName, "legacy-format");
}

TEST_F(KmsAddfbBasic, TestNoHandle) {
    runSubTest(testBinaryName, "no-handle");
}

TEST_F(KmsAddfbBasic, TestBasic) {
    runSubTest(testBinaryName, "basic");
}

TEST_F(KmsAddfbBasic, TestBadPitch0) {
    runSubTest(testBinaryName, "bad-pitch-0");
}

TEST_F(KmsAddfbBasic, TestBadPitch32) {
    runSubTest(testBinaryName, "bad-pitch-32");
}

TEST_F(KmsAddfbBasic, TestBadPitch63) {
    runSubTest(testBinaryName, "bad-pitch-63");
}

TEST_F(KmsAddfbBasic, TestBadPitch128) {
    runSubTest(testBinaryName, "bad-pitch-128");
}

TEST_F(KmsAddfbBasic, TestBadPitch256) {
    runSubTest(testBinaryName, "bad-pitch-256");
}

TEST_F(KmsAddfbBasic, TestBadPitch1024) {
    runSubTest(testBinaryName, "bad-pitch-1024");
}

TEST_F(KmsAddfbBasic, TestBadPitch999) {
    runSubTest(testBinaryName, "bad-pitch-999");
}

TEST_F(KmsAddfbBasic, TestBadPitch65536) {
    runSubTest(testBinaryName, "bad-pitch-65536");
}

TEST_F(KmsAddfbBasic, TestSizeMax) {
    runSubTest(testBinaryName, "size-max");
}

TEST_F(KmsAddfbBasic, TestTooWide) {
    runSubTest(testBinaryName, "too-wide");
}

TEST_F(KmsAddfbBasic, TestTooHigh) {
    runSubTest(testBinaryName, "too-high");
}

TEST_F(KmsAddfbBasic, TestBoTooSmall) {
    runSubTest(testBinaryName, "bo-too-small");
}

TEST_F(KmsAddfbBasic, TestSmallBo) {
    runSubTest(testBinaryName, "small-bo");
}

TEST_F(KmsAddfbBasic, TestBoTooSmallDueToTiling) {
    runSubTest(testBinaryName, "bo-too-small-due-to-tiling");
}

TEST_F(KmsAddfbBasic, TestAddfb25ModifierNoFlag) {
    runSubTest(testBinaryName, "addfb25-modifier-no-flag");
}

// TODO: b/254808989
/*
TEST_F(KmsAddfbBasic, TestAddfb25BadModifier) {
    runSubTest(testBinaryName, "addfb25-bad-modifier");
}
*/

TEST_F(KmsAddfbBasic, TestAddfb25XTiledMismatch) {
    runSubTest(testBinaryName, "addfb25-X-tiled-mismatch");
}

TEST_F(KmsAddfbBasic, TestAddfb25XTiled) {
    runSubTest(testBinaryName, "addfb25-X-tiled");
}

TEST_F(KmsAddfbBasic, TestAddfb25FramebufferVsSetTiling) {
    runSubTest(testBinaryName, "addfb25-framebuffer-vs-set-tiling");
}

TEST_F(KmsAddfbBasic, TestAddfb25YTiled) {
    runSubTest(testBinaryName, "addfb25-Y-tiled");
}

TEST_F(KmsAddfbBasic, TestAddfb25YFTiled) {
    runSubTest(testBinaryName, "addfb25-Yf-tiled");
}

TEST_F(KmsAddfbBasic, TestAddfb25YTiledSmall) {
    runSubTest(testBinaryName, "addfb25-Y-tiled-small");
}

TEST_F(KmsAddfbBasic, TestBasicXTiled) {
    runSubTest(testBinaryName, "basic-X-tiled");
}

TEST_F(KmsAddfbBasic, TestFramebufferVsSetTiling) {
    runSubTest(testBinaryName, "framebuffer-vs-set-tiling");
}

TEST_F(KmsAddfbBasic, TestTilePitchMismatch) {
    runSubTest(testBinaryName, "tile-pitch-mismatch");
}

TEST_F(KmsAddfbBasic, TestBasicYTiled) {
    runSubTest(testBinaryName, "basic-Y-tiled");
}

TEST_F(KmsAddfbBasic, TestInvalidGetPropAny) {
    runSubTest(testBinaryName, "invalid-get-prop-any");
}

TEST_F(KmsAddfbBasic, TestInvalidGetProp) {
    runSubTest(testBinaryName, "invalid-get-prop");
}

TEST_F(KmsAddfbBasic, TestInvalidSetPropAny) {
    runSubTest(testBinaryName, "invalid-set-prop-any");
}

TEST_F(KmsAddfbBasic, TestInvalidSetProp) {
    runSubTest(testBinaryName, "invalid-set-prop");
}

TEST_F(KmsAddfbBasic, TestMasterRmfb) {
    runSubTest(testBinaryName, "master-rmfb");
}
