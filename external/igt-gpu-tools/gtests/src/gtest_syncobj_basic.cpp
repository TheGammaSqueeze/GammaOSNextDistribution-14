#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class SyncobjBasic : public ::testing::Test {
    public:
    const char* testBinaryName = "syncobj_basic";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(SyncobjBasic, TestBadDestroy) {
    runSubTest(testBinaryName, "bad-destroy");
}

TEST_F(SyncobjBasic, TestBadCreateFlags) {
    runSubTest(testBinaryName, "bad-create-flags");
}

TEST_F(SyncobjBasic, TestBadHandleToFd) {
    runSubTest(testBinaryName, "bad-handle-to-fd");
}

TEST_F(SyncobjBasic, TestBadFdToHandle) {
    runSubTest(testBinaryName, "bad-fd-to-handle");
}

TEST_F(SyncobjBasic, TestBadFlagsHandleToFd) {
    runSubTest(testBinaryName, "bad-flags-handle-to-fd");
}

TEST_F(SyncobjBasic, TestBadFlagsFdToHandle) {
    runSubTest(testBinaryName, "bad-flags-fd-to-handle");
}

TEST_F(SyncobjBasic, TestBadPadHandleToFd) {
    runSubTest(testBinaryName, "bad-pad-handle-to-fd");
}

TEST_F(SyncobjBasic, TestBadPadFdToHandle) {
    runSubTest(testBinaryName, "bad-pad-fd-to-handle");
}

TEST_F(SyncobjBasic, TestIllegalFdToHandle) {
    runSubTest(testBinaryName, "illegal-fd-to-handle");
}

TEST_F(SyncobjBasic, TestBadDestroyPad) {
    runSubTest(testBinaryName, "bad-destroy-pad");
}

TEST_F(SyncobjBasic, TestCreateSignaled) {
    runSubTest(testBinaryName, "create-signaled");
}

TEST_F(SyncobjBasic, TestTestValidCycle) {
    runSubTest(testBinaryName, "test-valid-cycle");
}
