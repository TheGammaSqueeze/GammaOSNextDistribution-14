#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsAtomicTransition : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_atomic_transition";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsAtomicTransition, TestPlanePrimaryToggleWithVblankWait) {
    runSubTest(testBinaryName, "plane-primary-toggle-with-vblank-wait");
}

TEST_F(KmsAtomicTransition, TestPlaneAllTransition) {
    runSubTest(testBinaryName, "plane-all-transition");
}

TEST_F(KmsAtomicTransition, TestPlaneAllTransitionFencing) {
    runSubTest(testBinaryName, "plane-all-transition-fencing");
}

TEST_F(KmsAtomicTransition, TestPlaneAllTransitionNonblocking) {
    runSubTest(testBinaryName, "plane-all-transition-nonblocking");
}

TEST_F(KmsAtomicTransition, TestPlaneAllTransitionNonblockingFencing) {
    runSubTest(testBinaryName, "plane-all-transition-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, TestPlaneUseAfterNonblockingUnbind) {
    runSubTest(testBinaryName, "plane-use-after-nonblocking-unbind");
}

TEST_F(KmsAtomicTransition, TestPlaneUseAfterNonblockingUnbindFencing) {
    runSubTest(testBinaryName, "plane-use-after-nonblocking-unbind-fencing");
}

TEST_F(KmsAtomicTransition, TestPlaneAllModesetTransition) {
    runSubTest(testBinaryName, "plane-all-modeset-transition");
}

TEST_F(KmsAtomicTransition, TestPlaneAllModesetTransitionFencing) {
    runSubTest(testBinaryName, "plane-all-modeset-transition-fencing");
}

TEST_F(KmsAtomicTransition, TestPlaneAllModesetTransitionInternalPanels) {
    runSubTest(testBinaryName, "plane-all-modeset-transition-internal-panels");
}

TEST_F(KmsAtomicTransition, TestPlaneAllModesetTransitionFencingInternalPanels) {
    runSubTest(testBinaryName, "plane-all-modeset-transition-fencing-internal-panels");
}

TEST_F(KmsAtomicTransition, TestPlaneToggleModesetTransition) {
    runSubTest(testBinaryName, "plane-toggle-modeset-transition");
}

TEST_F(KmsAtomicTransition, Test1xModesetTransitions) {
    runSubTest(testBinaryName, "1x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test1xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "1x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test1xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "1x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test1xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "1x-modeset-transitions-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, Test2xModesetTransitions) {
    runSubTest(testBinaryName, "2x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test2xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "2x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test2xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "2x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test2xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "2x-modeset-transitions-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, Test3xModesetTransitions) {
    runSubTest(testBinaryName, "3x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test3xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "3x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test3xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "3x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test3xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "3x-modeset-transitions-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, Test4xModesetTransitions) {
    runSubTest(testBinaryName, "4x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test4xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "4x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test4xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "4x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test4xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "4x-modeset-transitions-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, Test5xModesetTransitions) {
    runSubTest(testBinaryName, "5x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test5xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "5x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test5xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "5x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test5xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "5x-modeset-transitions-nonblocking-fencing");
}

TEST_F(KmsAtomicTransition, Test6xModesetTransitions) {
    runSubTest(testBinaryName, "6x-modeset-transitions");
}

TEST_F(KmsAtomicTransition, Test6xModesetTransitionsNonblocking) {
    runSubTest(testBinaryName, "6x-modeset-transitions-nonblocking");
}

TEST_F(KmsAtomicTransition, Test6xModesetTransitionsFencing) {
    runSubTest(testBinaryName, "6x-modeset-transitions-fencing");
}

TEST_F(KmsAtomicTransition, Test6xModesetTransitionsNonblockingFencing) {
    runSubTest(testBinaryName, "6x-modeset-transitions-nonblocking-fencing");
}
