#include <gtest/gtest.h>
#include <cstdlib>
#include <string>

#include "gtest_helper.h"

class KmsAtomicTests : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_atomic";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsAtomicTests, TestPlaneOverlayLegacy) {
    runSubTest(testBinaryName, "plane_overlay_legacy");
}

TEST_F(KmsAtomicTests, TestPlanePrimaryLegacy) {
    runSubTest(testBinaryName, "plane_primary_legacy");
}

TEST_F(KmsAtomicTests, TestPlanePrimaryOverlayZpos) {
    runSubTest(testBinaryName, "plane_primary_overlay_zpos");
}

TEST_F(KmsAtomicTests, TestOnly) {
    runSubTest(testBinaryName, "test_only");
}

TEST_F(KmsAtomicTests, TestPlaneCursorLegacy) {
    runSubTest(testBinaryName, "plane_cursor_legacy");
}

TEST_F(KmsAtomicTests, TestPlaneInvalidParams) {
    runSubTest(testBinaryName, "plane_invalid_params");
}

TEST_F(KmsAtomicTests, TestPlaneInvalidParamsFence) {
    runSubTest(testBinaryName, "plane_invalid_params_fence");
}

TEST_F(KmsAtomicTests, TestCrtcInvalidParams) {
    runSubTest(testBinaryName, "crtc_invalid_params");
}

TEST_F(KmsAtomicTests, TestCrtcInvalidParamsFence) {
    runSubTest(testBinaryName, "crtc_invalid_params_fence");
}

TEST_F(KmsAtomicTests, TestAtomicInvalidParams) {
    runSubTest(testBinaryName, "atomic_invalid_params");
}
