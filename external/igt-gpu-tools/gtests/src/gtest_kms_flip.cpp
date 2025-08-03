#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsFlipTests : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_flip";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsFlipTests, TestNonblockingRead) {
    runSubTest(testBinaryName, "nonblocking-read");
}

TEST_F(KmsFlipTests, TestWfVblankTsCheck) {
    runSubTest(testBinaryName, "wf_vblank-ts-check");
}

TEST_F(KmsFlipTests, Test2xWfVblankTsCheck) {
    runSubTest(testBinaryName, "2x-wf_vblank-ts-check");
}

TEST_F(KmsFlipTests, TestBlockingWfVblank) {
    runSubTest(testBinaryName, "blocking-wf_vblank");
}

TEST_F(KmsFlipTests, Test2xBlockingWfVblank) {
    runSubTest(testBinaryName, "2x-blocking-wf_vblank");
}

TEST_F(KmsFlipTests, TestAbsoluteWfVblank) {
    runSubTest(testBinaryName, "absolute-wf_vblank");
}

TEST_F(KmsFlipTests, Test2xAbsoluteWfVblank) {
    runSubTest(testBinaryName, "2x-absolute-wf_vblank");
}

TEST_F(KmsFlipTests, TestBlockingAbsoluteWfVblank) {
    runSubTest(testBinaryName, "blocking-absolute-wf_vblank");
}

TEST_F(KmsFlipTests, Test2xBlockingAbsoluteWfVblank) {
    runSubTest(testBinaryName, "2x-blocking-absolute-wf_vblank");
}

TEST_F(KmsFlipTests, TestBasicPlainFlip) {
    runSubTest(testBinaryName, "basic-plain-flip");
}

TEST_F(KmsFlipTests, Test2xPlainFlip) {
    runSubTest(testBinaryName, "2x-plain-flip");
}

TEST_F(KmsFlipTests, TestBusyFlip) {
    runSubTest(testBinaryName, "busy-flip");
}

TEST_F(KmsFlipTests, Test2xBusyFlip) {
    runSubTest(testBinaryName, "2x-busy-flip");
}

TEST_F(KmsFlipTests, TestFlipVsFences) {
    runSubTest(testBinaryName, "flip-vs-fences");
}

TEST_F(KmsFlipTests, Test2xFlipVsFences) {
    runSubTest(testBinaryName, "2x-flip-vs-fences");
}

TEST_F(KmsFlipTests, TestPlainFlipTsCheck) {
    runSubTest(testBinaryName, "plain-flip-ts-check");
}

TEST_F(KmsFlipTests, Test2xPlainFlipTsCheck) {
    runSubTest(testBinaryName, "2x-plain-flip-ts-check");
}

TEST_F(KmsFlipTests, TestPlainFlipFbRecreate) {
    runSubTest(testBinaryName, "plain-flip-fb-recreate");
}

TEST_F(KmsFlipTests, Test2xPlainFlipFbRecreate) {
    runSubTest(testBinaryName, "2x-plain-flip-fb-recreate");
}

TEST_F(KmsFlipTests, TestFlipVsRmfb) {
    runSubTest(testBinaryName, "flip-vs-rmfb");
}

TEST_F(KmsFlipTests, Test2xFlipVsRmfb) {
    runSubTest(testBinaryName, "2x-flip-vs-rmfb");
}

TEST_F(KmsFlipTests, TestBasicFlipVsDpms) {
    runSubTest(testBinaryName, "basic-flip-vs-dpms");
}

TEST_F(KmsFlipTests, Test2xFlipVsDpms) {
    runSubTest(testBinaryName, "2x-flip-vs-dpms");
}

TEST_F(KmsFlipTests, TestFlipVsPanning) {
    runSubTest(testBinaryName, "flip-vs-panning");
}

TEST_F(KmsFlipTests, Test2xFlipVsPanning) {
    runSubTest(testBinaryName, "2x-flip-vs-panning");
}

TEST_F(KmsFlipTests, TestBasicFlipVsModeset) {
    runSubTest(testBinaryName, "basic-flip-vs-modeset");
}

TEST_F(KmsFlipTests, Test2xFlipVsModeset) {
    runSubTest(testBinaryName, "2x-flip-vs-modeset");
}

TEST_F(KmsFlipTests, TestFlipVsExpiredVblank) {
    runSubTest(testBinaryName, "flip-vs-expired-vblank");
}

TEST_F(KmsFlipTests, Test2xFlipVsExpiredVblank) {
    runSubTest(testBinaryName, "2x-flip-vs-expired-vblank");
}

TEST_F(KmsFlipTests, TestFlipVsAbsoluteWfVblank) {
    runSubTest(testBinaryName, "flip-vs-absolute-wf_vblank");
}

TEST_F(KmsFlipTests, Test2xFlipVsAbsoluteWfVblank) {
    runSubTest(testBinaryName, "2x-flip-vs-absolute-wf_vblank");
}

TEST_F(KmsFlipTests, TestBasicFlipVsWfVblank) {
    runSubTest(testBinaryName, "basic-flip-vs-wf_vblank");
}

TEST_F(KmsFlipTests, Test2xFlipVsWfVblank) {
    runSubTest(testBinaryName, "2x-flip-vs-wf_vblank");
}

TEST_F(KmsFlipTests, TestFlipVsBlockingWfVblank) {
    runSubTest(testBinaryName, "flip-vs-blocking-wf-vblank");
}

TEST_F(KmsFlipTests, Test2xFlipVsBlockingWfVblank) {
    runSubTest(testBinaryName, "2x-flip-vs-blocking-wf-vblank");
}

TEST_F(KmsFlipTests, TestFlipVsModesetVsHang) {
    runSubTest(testBinaryName, "flip-vs-modeset-vs-hang");
}

TEST_F(KmsFlipTests, Test2xFlipVsModesetVsHang) {
    runSubTest(testBinaryName, "2x-flip-vs-modeset-vs-hang");
}

TEST_F(KmsFlipTests, TestFlipVsPanningVsHang) {
    runSubTest(testBinaryName, "flip-vs-panning-vs-hang");
}

TEST_F(KmsFlipTests, Test2xFlipVsPanningVsHang) {
    runSubTest(testBinaryName, "2x-flip-vs-panning-vs-hang");
}

TEST_F(KmsFlipTests, TestFlipVsDpmsOffVsModeset) {
    runSubTest(testBinaryName, "flip-vs-dpms-off-vs-modeset");
}

TEST_F(KmsFlipTests, Test2xFlipVsDpmsOffVsModeset) {
    runSubTest(testBinaryName, "2x-flip-vs-dpms-off-vs-modeset");
}

TEST_F(KmsFlipTests, TestSingleBufferFlipVsDpmsOffVsModeset) {
    runSubTest(testBinaryName, "single-buffer-flip-vs-dpms-off-vs-modeset");
}

TEST_F(KmsFlipTests, Test2xSingleBufferFlipVsDpmsOffVsModeset) {
    runSubTest(testBinaryName, "2x-single-buffer-flip-vs-dpms-off-vs-modeset");
}

TEST_F(KmsFlipTests, TestDpmsOffConfusion) {
    runSubTest(testBinaryName, "dpms-off-confusion");
}

TEST_F(KmsFlipTests, TestNonexistingFb) {
    runSubTest(testBinaryName, "nonexisting-fb");
}

TEST_F(KmsFlipTests, Test2xNonexistingFb) {
    runSubTest(testBinaryName, "2x-nonexisting-fb");
}

TEST_F(KmsFlipTests, TestDpmsVsVblankRace) {
    runSubTest(testBinaryName, "dpms-vs-vblank-race");
}

TEST_F(KmsFlipTests, Test2xDpmsVsVblankRace) {
    runSubTest(testBinaryName, "2x-dpms-vs-vblank-race");
}

TEST_F(KmsFlipTests, TestModesetVsVblankRace) {
    runSubTest(testBinaryName, "modeset-vs-vblank-race");
}

TEST_F(KmsFlipTests, Test2xModesetVsVblankRace) {
    runSubTest(testBinaryName, "2x-modeset-vs-vblank-race");
}

TEST_F(KmsFlipTests, TestBoTooBig) {
    runSubTest(testBinaryName, "bo-too-big");
}

TEST_F(KmsFlipTests, TestFlipVsSuspend) {
    runSubTest(testBinaryName, "flip-vs-suspend");
}

TEST_F(KmsFlipTests, Test2xFlipVsSuspend) {
    runSubTest(testBinaryName, "2x-flip-vs-suspend");
}

TEST_F(KmsFlipTests, TestWfVblankTsCheckInterruptible) {
    runSubTest(testBinaryName, "wf_vblank-ts-check-interruptible");
}

TEST_F(KmsFlipTests, Test2xWfVblankTsCheckInterruptible) {
    runSubTest(testBinaryName, "2x-wf_vblank-ts-check-interruptible");
}

TEST_F(KmsFlipTests, TestAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, Test2xAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "2x-absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, TestBlockingAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "blocking-absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, Test2xBlockingAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "2x-blocking-absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, TestPlainFlipInterruptible) {
    runSubTest(testBinaryName, "plain-flip-interruptible");
}

TEST_F(KmsFlipTests, Test2xPlainFlipInterruptible) {
    runSubTest(testBinaryName, "2x-plain-flip-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsFencesInterruptible) {
    runSubTest(testBinaryName, "flip-vs-fences-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsFencesInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-fences-interruptible");
}

TEST_F(KmsFlipTests, TestPlainFlipTsCheckInterruptible) {
    runSubTest(testBinaryName, "plain-flip-ts-check-interruptible");
}

TEST_F(KmsFlipTests, Test2xPlainFlipTsCheckInterruptible) {
    runSubTest(testBinaryName, "2x-plain-flip-ts-check-interruptible");
}

TEST_F(KmsFlipTests, TestPlainFlipFbRecreateInterruptible) {
    runSubTest(testBinaryName, "plain-flip-fb-recreate-interruptible");
}

TEST_F(KmsFlipTests, Test2xPlainFlipFbRecreateInterruptible) {
    runSubTest(testBinaryName, "2x-plain-flip-fb-recreate-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsRmfbInterruptible) {
    runSubTest(testBinaryName, "flip-vs-rmfb-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsRmfbInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-rmfb-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsDpmsInterruptible) {
    runSubTest(testBinaryName, "flip-vs-dpms-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsDpmsInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-dpms-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsPanningInterruptible) {
    runSubTest(testBinaryName, "flip-vs-panning-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsPanningInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-panning-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsModesetInterruptible) {
    runSubTest(testBinaryName, "flip-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsModesetInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsExpiredVblankInterruptible) {
    runSubTest(testBinaryName, "flip-vs-expired-vblank-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsExpiredVblankInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-expired-vblank-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "flip-vs-absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsAbsoluteWfVblankInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-absolute-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsWfVblankInterruptible) {
    runSubTest(testBinaryName, "flip-vs-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsWfVblankInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-wf_vblank-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsModesetVsHangInterruptible) {
    runSubTest(testBinaryName, "flip-vs-modeset-vs-hang-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsModesetVsHangInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-modeset-vs-hang-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsPanningVsHangInterruptible) {
    runSubTest(testBinaryName, "flip-vs-panning-vs-hang-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsPanningVsHangInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-panning-vs-hang-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsDpmsOffVsModesetInterruptible) {
    runSubTest(testBinaryName, "flip-vs-dpms-off-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsDpmsOffVsModesetInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-dpms-off-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, TestSingleBufferFlipVsDpmsOffVsModesetInterruptible) {
    runSubTest(testBinaryName, "single-buffer-flip-vs-dpms-off-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, Test2xSingleBufferFlipVsDpmsOffVsModesetInterruptible) {
    runSubTest(testBinaryName, "2x-single-buffer-flip-vs-dpms-off-vs-modeset-interruptible");
}

TEST_F(KmsFlipTests, TestDpmsOffConfusionInterruptible) {
    runSubTest(testBinaryName, "dpms-off-confusion-interruptible");
}

TEST_F(KmsFlipTests, TestNonexistingFbInterruptible) {
    runSubTest(testBinaryName, "nonexisting-fb-interruptible");
}

TEST_F(KmsFlipTests, Test2xNonexistingFbInterruptible) {
    runSubTest(testBinaryName, "2x-nonexisting-fb-interruptible");
}

TEST_F(KmsFlipTests, TestDpmsVsVblankRaceInterruptible) {
    runSubTest(testBinaryName, "dpms-vs-vblank-race-interruptible");
}

TEST_F(KmsFlipTests, Test2xDpmsVsVblankRaceInterruptible) {
    runSubTest(testBinaryName, "2x-dpms-vs-vblank-race-interruptible");
}

TEST_F(KmsFlipTests, TestModesetVsVblankRaceInterruptible) {
    runSubTest(testBinaryName, "modeset-vs-vblank-race-interruptible");
}

TEST_F(KmsFlipTests, Test2xModesetVsVblankRaceInterruptible) {
    runSubTest(testBinaryName, "2x-modeset-vs-vblank-race-interruptible");
}

TEST_F(KmsFlipTests, TestBoTooBigInterruptible) {
    runSubTest(testBinaryName, "bo-too-big-interruptible");
}

TEST_F(KmsFlipTests, TestFlipVsSuspendInterruptible) {
    runSubTest(testBinaryName, "flip-vs-suspend-interruptible");
}

TEST_F(KmsFlipTests, Test2xFlipVsSuspendInterruptible) {
    runSubTest(testBinaryName, "2x-flip-vs-suspend-interruptible");
}
