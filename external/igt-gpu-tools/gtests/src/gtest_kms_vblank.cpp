#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsVBlankTests : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_vblank";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsVBlankTests, TestInvalid) {
    runSubTest(testBinaryName, "invalid");
}

TEST_F(KmsVBlankTests, TestCrtcId) {
    runSubTest(testBinaryName, "crtc-id");
}

TEST_F(KmsVBlankTests, TestPipeAAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-A-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeAQueryIdle) {
    runSubTest(testBinaryName, "pipe-A-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeAQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-A-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeAQueryForked) {
    runSubTest(testBinaryName, "pipe-A-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeAQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-A-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeAQueryBusy) {
    runSubTest(testBinaryName, "pipe-A-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeAQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-A-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeAQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-A-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeAQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-A-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeAWaitIdle) {
    runSubTest(testBinaryName, "pipe-A-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeAWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-A-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeAWaitForked) {
    runSubTest(testBinaryName, "pipe-A-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeAWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-A-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeAWaitBusy) {
    runSubTest(testBinaryName, "pipe-A-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeAWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-A-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeAWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-A-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeAWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-A-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeATsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-A-ts-continuation-modeset-rpm");
}

TEST_F(KmsVBlankTests, TestPipeBAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-B-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeBQueryIdle) {
    runSubTest(testBinaryName, "pipe-B-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeBQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-B-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeBQueryForked) {
    runSubTest(testBinaryName, "pipe-B-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeBQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-B-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeBQueryBusy) {
    runSubTest(testBinaryName, "pipe-B-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeBQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-B-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeBQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-B-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeBQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-B-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeBWaitIdle) {
    runSubTest(testBinaryName, "pipe-B-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeBWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-B-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeBWaitForked) {
    runSubTest(testBinaryName, "pipe-B-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeBWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-B-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeBWaitBusy) {
    runSubTest(testBinaryName, "pipe-B-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeBWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-B-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeBWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-B-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeBWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-B-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeBTsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-B-ts-continuation-modeset-rpm");
}

TEST_F(KmsVBlankTests, TestPipeCAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-C-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeCQueryIdle) {
    runSubTest(testBinaryName, "pipe-C-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeCQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-C-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeCQueryForked) {
    runSubTest(testBinaryName, "pipe-C-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeCQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-C-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeCQueryBusy) {
    runSubTest(testBinaryName, "pipe-C-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeCQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-C-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeCQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-C-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeCQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-C-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeCWaitIdle) {
    runSubTest(testBinaryName, "pipe-C-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeCWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-C-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeCWaitForked) {
    runSubTest(testBinaryName, "pipe-C-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeCWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-C-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeCWaitBusy) {
    runSubTest(testBinaryName, "pipe-C-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeCWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-C-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeCWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-C-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeCWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-C-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeCTsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-C-ts-continuation-modeset-rpm");
}

TEST_F(KmsVBlankTests, TestPipeDAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-D-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeDQueryIdle) {
    runSubTest(testBinaryName, "pipe-D-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeDQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-D-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeDQueryForked) {
    runSubTest(testBinaryName, "pipe-D-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeDQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-D-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeDQueryBusy) {
    runSubTest(testBinaryName, "pipe-D-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeDQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-D-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeDQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-D-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeDQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-D-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeDWaitIdle) {
    runSubTest(testBinaryName, "pipe-D-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeDWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-D-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeDWaitForked) {
    runSubTest(testBinaryName, "pipe-D-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeDWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-D-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeDWaitBusy) {
    runSubTest(testBinaryName, "pipe-D-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeDWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-D-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeDWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-D-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeDWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-D-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeDTsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-D-ts-continuation-modeset-rpm");
}

TEST_F(KmsVBlankTests, TestPipeEAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-E-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeEQueryIdle) {
    runSubTest(testBinaryName, "pipe-E-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeEQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-E-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeEQueryForked) {
    runSubTest(testBinaryName, "pipe-E-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeEQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-E-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeEQueryBusy) {
    runSubTest(testBinaryName, "pipe-E-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeEQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-E-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeEQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-E-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeEQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-E-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeEWaitIdle) {
    runSubTest(testBinaryName, "pipe-E-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeEWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-E-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeEWaitForked) {
    runSubTest(testBinaryName, "pipe-E-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeEWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-E-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeEWaitBusy) {
    runSubTest(testBinaryName, "pipe-E-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeEWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-E-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeEWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-E-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeEWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-E-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeETsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-E-ts-continuation-modeset-rpm");
}

TEST_F(KmsVBlankTests, TestPipeFAccuracyIdle) {
    runSubTest(testBinaryName, "pipe-F-accuracy-idle");
}

TEST_F(KmsVBlankTests, TestPipeFQueryIdle) {
    runSubTest(testBinaryName, "pipe-F-query-idle");
}

TEST_F(KmsVBlankTests, TestPipeFQueryIdleHang) {
    runSubTest(testBinaryName, "pipe-F-query-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeFQueryForked) {
    runSubTest(testBinaryName, "pipe-F-query-forked");
}

TEST_F(KmsVBlankTests, TestPipeFQueryForkedHang) {
    runSubTest(testBinaryName, "pipe-F-query-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeFQueryBusy) {
    runSubTest(testBinaryName, "pipe-F-query-busy");
}

TEST_F(KmsVBlankTests, TestPipeFQueryBusyHang) {
    runSubTest(testBinaryName, "pipe-F-query-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeFQueryForkedBusy) {
    runSubTest(testBinaryName, "pipe-F-query-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeFQueryForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-F-query-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeFWaitIdle) {
    runSubTest(testBinaryName, "pipe-F-wait-idle");
}

TEST_F(KmsVBlankTests, TestPipeFWaitIdleHang) {
    runSubTest(testBinaryName, "pipe-F-wait-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeFWaitForked) {
    runSubTest(testBinaryName, "pipe-F-wait-forked");
}

TEST_F(KmsVBlankTests, TestPipeFWaitForkedHang) {
    runSubTest(testBinaryName, "pipe-F-wait-forked-hang");
}

TEST_F(KmsVBlankTests, TestPipeFWaitBusy) {
    runSubTest(testBinaryName, "pipe-F-wait-busy");
}

TEST_F(KmsVBlankTests, TestPipeFWaitBusyHang) {
    runSubTest(testBinaryName, "pipe-F-wait-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeFWaitForkedBusy) {
    runSubTest(testBinaryName, "pipe-F-wait-forked-busy");
}

TEST_F(KmsVBlankTests, TestPipeFWaitForkedBusyHang) {
    runSubTest(testBinaryName, "pipe-F-wait-forked-busy-hang");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationIdle) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-idle");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationIdleHang) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-idle-hang");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationDpmsRpm) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-dpms-rpm");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationDpmsSuspend) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-dpms-suspend");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationSuspend) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-suspend");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationModeset) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-modeset");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationModesetHang) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-modeset-hang");
}

TEST_F(KmsVBlankTests, TestPipeFTsContinuationModesetRpm) {
    runSubTest(testBinaryName, "pipe-F-ts-continuation-modeset-rpm");
}
