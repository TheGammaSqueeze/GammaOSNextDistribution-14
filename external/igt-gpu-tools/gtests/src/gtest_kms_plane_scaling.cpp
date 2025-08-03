#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsPlaneScaling : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_plane_scaling";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsPlaneScaling, TestPipeAPlaneScaling) {
    runSubTest(testBinaryName, "pipe-A-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeAScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-A-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeAScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-A-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeAScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-A-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, TestPipeBPlaneScaling) {
    runSubTest(testBinaryName, "pipe-B-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeBScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-B-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeBScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-B-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeBScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-B-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, TestPipeCPlaneScaling) {
    runSubTest(testBinaryName, "pipe-C-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeCScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-C-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeCScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-C-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeCScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-C-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, TestPipeDPlaneScaling) {
    runSubTest(testBinaryName, "pipe-D-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeDScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-D-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeDScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-D-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeDScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-D-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, TestPipeEPlaneScaling) {
    runSubTest(testBinaryName, "pipe-E-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeEScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-E-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeEScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-E-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeEScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-E-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, TestPipeFPlaneScaling) {
    runSubTest(testBinaryName, "pipe-F-plane-scaling");
}

TEST_F(KmsPlaneScaling, TestPipeFScalerWithPixelFormat) {
    runSubTest(testBinaryName, "pipe-F-scaler-with-pixel-format");
}

TEST_F(KmsPlaneScaling, TestPipeFScalerWithRotation) {
    runSubTest(testBinaryName, "pipe-F-scaler-with-rotation");
}

TEST_F(KmsPlaneScaling, TestPipeFScalerWithClippingClamping) {
    runSubTest(testBinaryName, "pipe-F-scaler-with-clipping-clamping");
}

TEST_F(KmsPlaneScaling, Test2xScalerMultiPipe) {
    runSubTest(testBinaryName, "2x-scaler-multi-pipe");
}
