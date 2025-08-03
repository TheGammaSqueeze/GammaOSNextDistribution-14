#include <gtest/gtest.h>
#include <cstdlib>
#include <cstdio>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <array>
#include <unordered_map>
#include <algorithm>

#include "gtest_helper.h"

enum TestResult {
    PASS,
    FAIL,
    SKIP,
    UNKNOWN
};

/**
 * @brief Run the command and returns the output.
 *
 * @param cmd
 * @return std::string
 */
std::string exec(std::string cmd) {
    std::unique_ptr<FILE, decltype(&pclose) > pipe(popen(cmd.c_str(), "r"), pclose);
    if (!pipe) {
        return "popen() failed! Could not find or run the binary.";
    }

    std::array<char, 128> buffer;
    std::string result;
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
        result += buffer.data();
    }
    return result;
}

/**
 * @brief Get the Subtest Result From Log object
 *
 * @param log
 * @param subtestName
 * @return TestResult
 */
TestResult getSubtestTestResultFromLog(std::string log, std::string subtestName) {
    if (log.find("Subtest " + subtestName + ": FAIL") != std::string::npos) {
        return FAIL;
    }
    else if (log.find("Subtest " + subtestName + ": SKIP") != std::string::npos) {
        return SKIP;
    }
    else if (log.find("Subtest " + subtestName + ": SUCCESS") != std::string::npos) {
        return PASS;
    }
    else {
        return UNKNOWN;
    }
}

/**
 * @brief Get the Test Result From Log object
 *
 * @param log
 * @param subtestName
 * @return TestResult
 */
TestResult getTestResultFromLog(std::string log) {

    std::for_each(log.begin(), log.end(), [](char & c) {
        c = ::tolower(c);
    });

    if (log.find("fail") != std::string::npos) {
        return FAIL;
    }
    else if (log.find("skip") != std::string::npos) {
        return SKIP;
    }
    else if (log.find("success") != std::string::npos) {
        return PASS;
    }
    else {
        return UNKNOWN;
    }
}

void presentTestResult(TestResult result, std::string log) {
    switch (result)
    {
    case PASS:
        SUCCEED();
        break;
    case FAIL:
        ADD_FAILURE() << log;
        break;
    case SKIP:
        GTEST_SKIP() << log;
        break;
    case UNKNOWN:
        ADD_FAILURE() << "Could not determine test result.\n" << log;
        break;
    default:
        ADD_FAILURE() << log;
        break;
    }
}

void runSubTest(std::string testBinaryName, std::string subtestName) {
    std::string log = exec("./" + testBinaryName + " --run-subtest " + subtestName);

    TestResult result = getSubtestTestResultFromLog(log, subtestName);

    presentTestResult(result, log);
}

void runTest(std::string testBinaryName) {    
    std::string log = exec("./" + testBinaryName);

    TestResult result = getTestResultFromLog(log);

    presentTestResult(result, log);
}
