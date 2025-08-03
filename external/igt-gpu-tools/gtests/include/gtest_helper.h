#include <gtest/gtest.h>
#include <cstdlib>
#include <string>

static const char* binary_path = "/data/igt_tests";

/**
 * @brief Run the testBinary for specific subtest and Pass/Fail/Skip depending on the log.
 *
 * @param testBinaryName
 * @param subtestName
 */
void runSubTest(std::string testBinaryName, std::string subtestName);

/**
 * @brief Run the testBinary and Pass/Fail/Skip depending on the log.
 * 
 * @param testBinaryName 
 */
void runTest(std::string testBinaryName);
