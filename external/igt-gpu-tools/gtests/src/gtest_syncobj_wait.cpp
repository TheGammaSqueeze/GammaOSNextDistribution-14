#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class SyncobjWait : public ::testing::Test {
    public:
    const char* testBinaryName = "syncobj_wait";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(SyncobjWait, TestInvalidWaitBadFlags) {
    runSubTest(testBinaryName, "invalid-wait-bad-flags");
}

TEST_F(SyncobjWait, TestInvalidWaitZeroHandles) {
    runSubTest(testBinaryName, "invalid-wait-zero-handles");
}

TEST_F(SyncobjWait, TestInvalidWaitIllegalHandle) {
    runSubTest(testBinaryName, "invalid-wait-illegal-handle");
}

TEST_F(SyncobjWait, TestInvalidResetZeroHandles) {
    runSubTest(testBinaryName, "invalid-reset-zero-handles");
}

TEST_F(SyncobjWait, TestInvalidResetIllegalHandle) {
    runSubTest(testBinaryName, "invalid-reset-illegal-handle");
}

TEST_F(SyncobjWait, TestInvalidResetOneIllegalHandle) {
    runSubTest(testBinaryName, "invalid-reset-one-illegal-handle");
}

TEST_F(SyncobjWait, TestInvalidResetBadPad) {
    runSubTest(testBinaryName, "invalid-reset-bad-pad");
}

TEST_F(SyncobjWait, TestInvalidSignalZeroHandles) {
    runSubTest(testBinaryName, "invalid-signal-zero-handles");
}

TEST_F(SyncobjWait, TestInvalidSignalIllegalHandle) {
    runSubTest(testBinaryName, "invalid-signal-illegal-handle");
}

TEST_F(SyncobjWait, TestInvalidSignalOneIllegalHandle) {
    runSubTest(testBinaryName, "invalid-signal-one-illegal-handle");
}

TEST_F(SyncobjWait, TestInvalidSignalBadPad) {
    runSubTest(testBinaryName, "invalid-signal-bad-pad");
}

TEST_F(SyncobjWait, TestInvalidSingleWaitUnsubmitted) {
    runSubTest(testBinaryName, "invalid-single-wait-unsubmitted");
}

TEST_F(SyncobjWait, TestSingleWaitForSubmitUnsubmitted) {
    runSubTest(testBinaryName, "single-wait-for-submit-unsubmitted");
}

TEST_F(SyncobjWait, TestInvalidSingleWaitAllUnsubmitted) {
    runSubTest(testBinaryName, "invalid-single-wait-all-unsubmitted");
}

TEST_F(SyncobjWait, TestSingleWaitAllForSubmitUnsubmitted) {
    runSubTest(testBinaryName, "single-wait-all-for-submit-unsubmitted");
}

TEST_F(SyncobjWait, TestSingleWaitSubmitted) {
    runSubTest(testBinaryName, "single-wait-submitted");
}

TEST_F(SyncobjWait, TestSingleWaitForSubmitSubmitted) {
    runSubTest(testBinaryName, "single-wait-for-submit-submitted");
}

TEST_F(SyncobjWait, TestSingleWaitAllSubmitted) {
    runSubTest(testBinaryName, "single-wait-all-submitted");
}

TEST_F(SyncobjWait, TestSingleWaitAllForSubmitSubmitted) {
    runSubTest(testBinaryName, "single-wait-all-for-submit-submitted");
}

TEST_F(SyncobjWait, TestSingleWaitSignaled) {
    runSubTest(testBinaryName, "single-wait-signaled");
}

TEST_F(SyncobjWait, TestSingleWaitForSubmitSignaled) {
    runSubTest(testBinaryName, "single-wait-for-submit-signaled");
}

TEST_F(SyncobjWait, TestSingleWaitAllSignaled) {
    runSubTest(testBinaryName, "single-wait-all-signaled");
}

TEST_F(SyncobjWait, TestSingleWaitAllForSubmitSignaled) {
    runSubTest(testBinaryName, "single-wait-all-for-submit-signaled");
}

TEST_F(SyncobjWait, TestWaitDelayedSignal) {
    runSubTest(testBinaryName, "wait-delayed-signal");
}

TEST_F(SyncobjWait, TestWaitForSubmitDelayedSubmit) {
    runSubTest(testBinaryName, "wait-for-submit-delayed-submit");
}

TEST_F(SyncobjWait, TestWaitAllDelayedSignal) {
    runSubTest(testBinaryName, "wait-all-delayed-signal");
}

TEST_F(SyncobjWait, TestWaitAllForSubmitDelayedSubmit) {
    runSubTest(testBinaryName, "wait-all-for-submit-delayed-submit");
}

TEST_F(SyncobjWait, TestResetUnsignaled) {
    runSubTest(testBinaryName, "reset-unsignaled");
}

TEST_F(SyncobjWait, TestResetSignaled) {
    runSubTest(testBinaryName, "reset-signaled");
}

TEST_F(SyncobjWait, TestResetMultipleSignaled) {
    runSubTest(testBinaryName, "reset-multiple-signaled");
}

TEST_F(SyncobjWait, TestResetDuringWaitForSubmit) {
    runSubTest(testBinaryName, "reset-during-wait-for-submit");
}

TEST_F(SyncobjWait, TestSignal) {
    runSubTest(testBinaryName, "signal");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitUnsubmitted) {
    runSubTest(testBinaryName, "invalid-multi-wait-unsubmitted");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitUnsubmitted) {
    runSubTest(testBinaryName, "multi-wait-for-submit-unsubmitted");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitAllUnsubmitted) {
    runSubTest(testBinaryName, "invalid-multi-wait-all-unsubmitted");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitUnsubmitted) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-unsubmitted");
}

TEST_F(SyncobjWait, TestMultiWaitSubmitted) {
    runSubTest(testBinaryName, "multi-wait-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitSubmitted) {
    runSubTest(testBinaryName, "multi-wait-for-submit-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitAllSubmitted) {
    runSubTest(testBinaryName, "multi-wait-all-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitSubmitted) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-submitted");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitUnsubmittedSubmitted) {
    runSubTest(testBinaryName, "invalid-multi-wait-unsubmitted-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitUnsubmittedSubmitted) {
    runSubTest(testBinaryName, "multi-wait-for-submit-unsubmitted-submitted");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitAllUnsubmittedSubmitted) {
    runSubTest(testBinaryName, "invalid-multi-wait-all-unsubmitted-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitUnsubmittedSubmitted) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-unsubmitted-submitted");
}

TEST_F(SyncobjWait, TestMultiWaitSignaled) {
    runSubTest(testBinaryName, "multi-wait-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitSignaled) {
    runSubTest(testBinaryName, "multi-wait-for-submit-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitAllSignaled) {
    runSubTest(testBinaryName, "multi-wait-all-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitSignaled) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-signaled");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitUnsubmittedSignaled) {
    runSubTest(testBinaryName, "invalid-multi-wait-unsubmitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitUnsubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-for-submit-unsubmitted-signaled");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitAllUnsubmittedSignaled) {
    runSubTest(testBinaryName, "invalid-multi-wait-all-unsubmitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitUnsubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-unsubmitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitSubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-submitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitSubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-for-submit-submitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitAllSubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-all-submitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitAllForSubmitSubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-all-for-submit-submitted-signaled");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitUnsubmittedSubmittedSignaled) {
    runSubTest(testBinaryName, "invalid-multi-wait-unsubmitted-submitted-signaled");
}

TEST_F(SyncobjWait, TestMultiWaitForSubmitUnsubmittedSubmittedSignaled) {
    runSubTest(testBinaryName, "multi-wait-for-submit-unsubmitted-submitted-signaled");
}

TEST_F(SyncobjWait, TestInvalidMultiWaitAllUnsubmittedSubmittedSignaled) {
    runSubTest(testBinaryName, "invalid-multi-wait-all-unsubmitted-submitted-signaled");
}

TEST_F(SyncobjWait, TestWaitAnySnapshot) {
    runSubTest(testBinaryName, "wait-any-snapshot");
}

TEST_F(SyncobjWait, TestWaitAllSnapshot) {
    runSubTest(testBinaryName, "wait-all-snapshot");
}

TEST_F(SyncobjWait, TestWaitForSubmitSnapshot) {
    runSubTest(testBinaryName, "wait-for-submit-snapshot");
}

TEST_F(SyncobjWait, TestWaitAllForSubmitSnapshot) {
    runSubTest(testBinaryName, "wait-all-for-submit-snapshot");
}

TEST_F(SyncobjWait, TestWaitAnyComplex) {
    runSubTest(testBinaryName, "wait-any-complex");
}

TEST_F(SyncobjWait, TestWaitAllComplex) {
    runSubTest(testBinaryName, "wait-all-complex");
}

TEST_F(SyncobjWait, TestWaitForSubmitComplex) {
    runSubTest(testBinaryName, "wait-for-submit-complex");
}

TEST_F(SyncobjWait, TestWaitAllForSubmitComplex) {
    runSubTest(testBinaryName, "wait-all-for-submit-complex");
}

TEST_F(SyncobjWait, TestWaitAnyInterrupted) {
    runSubTest(testBinaryName, "wait-any-interrupted");
}

TEST_F(SyncobjWait, TestWaitAllInterrupted) {
    runSubTest(testBinaryName, "wait-all-interrupted");
}
