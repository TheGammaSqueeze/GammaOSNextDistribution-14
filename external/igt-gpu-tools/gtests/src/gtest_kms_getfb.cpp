#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsGetfb : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_getfb";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsGetfb, TestGetfbHandleZero) {
    runSubTest(testBinaryName, "getfb-handle-zero");
}

TEST_F(KmsGetfb, TestGetfbHandleValid) {
    runSubTest(testBinaryName, "getfb-handle-valid");
}

TEST_F(KmsGetfb, TestGetfbHandleClosed) {
    runSubTest(testBinaryName, "getfb-handle-closed");
}

TEST_F(KmsGetfb, TestGetfbHandleNotFb) {
    runSubTest(testBinaryName, "getfb-handle-not-fb");
}

TEST_F(KmsGetfb, TestGetfbAddfbDifferentHandles) {
    runSubTest(testBinaryName, "getfb-addfb-different-handles");
}

TEST_F(KmsGetfb, TestGetfbRepeatedDifferentHandles) {
    runSubTest(testBinaryName, "getfb-repeated-different-handles");
}

TEST_F(KmsGetfb, TestGetfbRejectCcs) {
    runSubTest(testBinaryName, "getfb-reject-ccs");
}
