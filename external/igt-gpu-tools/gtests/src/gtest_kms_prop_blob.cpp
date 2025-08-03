#include <gtest/gtest.h>
#include <cstdlib>
#include <string>
#include "gtest_helper.h"

class KmsPropBlob : public ::testing::Test {
    public:
    const char* testBinaryName = "kms_prop_blob";
    void SetUp() override { chdir(binary_path); }
    void TearDown() override { chdir("/"); }
};

TEST_F(KmsPropBlob, TestBasic) {
    runSubTest(testBinaryName, "basic");
}

TEST_F(KmsPropBlob, TestBlobPropCore) {
    runSubTest(testBinaryName, "blob-prop-core");
}

TEST_F(KmsPropBlob, TestBlobPropValidate) {
    runSubTest(testBinaryName, "blob-prop-validate");
}

TEST_F(KmsPropBlob, TestBlobPropLifetime) {
    runSubTest(testBinaryName, "blob-prop-lifetime");
}

TEST_F(KmsPropBlob, TestBlobMultiple) {
    runSubTest(testBinaryName, "blob-multiple");
}

TEST_F(KmsPropBlob, TestInvalidGetPropAny) {
    runSubTest(testBinaryName, "invalid-get-prop-any");
}

TEST_F(KmsPropBlob, TestInvalidGetProp) {
    runSubTest(testBinaryName, "invalid-get-prop");
}

TEST_F(KmsPropBlob, TestInvalidSetPropAny) {
    runSubTest(testBinaryName, "invalid-set-prop-any");
}

TEST_F(KmsPropBlob, TestInvalidSetProp) {
    runSubTest(testBinaryName, "invalid-set-prop");
}
