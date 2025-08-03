#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsAtomicInterruptible : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_atomic_interruptible";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsAtomicInterruptible, TestLegacySetmode) {
    runSubTest(testBinaryName, "legacy-setmode");
}

TEST_F(KmsAtomicInterruptible, TestAtomicSetmode) {
    runSubTest(testBinaryName, "atomic-setmode");
}

TEST_F(KmsAtomicInterruptible, TestLegacyDpms) {
    runSubTest(testBinaryName, "legacy-dpms");
}

TEST_F(KmsAtomicInterruptible, TestLegacyPageflip) {
    runSubTest(testBinaryName, "legacy-pageflip");
}

TEST_F(KmsAtomicInterruptible, TestLegacyCursor) {
    runSubTest(testBinaryName, "legacy-cursor");
}

TEST_F(KmsAtomicInterruptible, TestUniversalSetplanePrimary) {
    runSubTest(testBinaryName, "universal-setplane-primary");
}

TEST_F(KmsAtomicInterruptible, TestUniversalSetplaneCursor) {
    runSubTest(testBinaryName, "universal-setplane-cursor");
}
