/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * FirewallControllerTest.cpp - unit tests for FirewallController.cpp
 */

#include <string>
#include <vector>
#include <stdio.h>

#include <gtest/gtest.h>

#include "FirewallController.h"
#include "IptablesBaseTest.h"

namespace android {
namespace net {

class FirewallControllerTest : public IptablesBaseTest {
protected:
    FirewallControllerTest() {
        FirewallController::execIptablesRestore = fakeExecIptablesRestore;
    }
    FirewallController mFw;
};

TEST_F(FirewallControllerTest, TestFirewall) {
    std::vector<std::string> enableCommands = {
            "*filter\n"
            "-A fw_INPUT -j DROP\n"
            "-A fw_OUTPUT -j REJECT\n"
            "-A fw_FORWARD -j REJECT\n"
            "COMMIT\n"};
    std::vector<std::string> disableCommands = {
            "*filter\n"
            ":fw_INPUT -\n"
            ":fw_OUTPUT -\n"
            ":fw_FORWARD -\n"
            "-6 -A fw_OUTPUT ! -o lo -s ::1 -j DROP\n"
            "COMMIT\n"};
    std::vector<std::string> noCommands = {};

    EXPECT_EQ(0, mFw.resetFirewall());
    expectIptablesRestoreCommands(disableCommands);

    EXPECT_EQ(0, mFw.resetFirewall());
    expectIptablesRestoreCommands(disableCommands);

    EXPECT_EQ(0, mFw.setFirewallType(DENYLIST));
    expectIptablesRestoreCommands(disableCommands);

    EXPECT_EQ(0, mFw.setFirewallType(DENYLIST));
    expectIptablesRestoreCommands(noCommands);

    std::vector<std::string> disableEnableCommands;
    disableEnableCommands.insert(
            disableEnableCommands.end(), disableCommands.begin(), disableCommands.end());
    disableEnableCommands.insert(
            disableEnableCommands.end(), enableCommands.begin(), enableCommands.end());

    EXPECT_EQ(0, mFw.setFirewallType(ALLOWLIST));
    expectIptablesRestoreCommands(disableEnableCommands);

    std::vector<std::string> ifaceCommands = {
        "*filter\n"
        "-I fw_INPUT -i rmnet_data0 -j RETURN\n"
        "-I fw_OUTPUT -o rmnet_data0 -j RETURN\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mFw.setInterfaceRule("rmnet_data0", ALLOW));
    expectIptablesRestoreCommands(ifaceCommands);

    EXPECT_EQ(0, mFw.setInterfaceRule("rmnet_data0", ALLOW));
    expectIptablesRestoreCommands(noCommands);

    ifaceCommands = {
        "*filter\n"
        "-D fw_INPUT -i rmnet_data0 -j RETURN\n"
        "-D fw_OUTPUT -o rmnet_data0 -j RETURN\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mFw.setInterfaceRule("rmnet_data0", DENY));
    expectIptablesRestoreCommands(ifaceCommands);

    EXPECT_EQ(0, mFw.setInterfaceRule("rmnet_data0", DENY));
    expectIptablesRestoreCommands(noCommands);

    EXPECT_EQ(0, mFw.setFirewallType(ALLOWLIST));
    expectIptablesRestoreCommands(noCommands);

    EXPECT_EQ(0, mFw.resetFirewall());
    expectIptablesRestoreCommands(disableCommands);

    // TODO: calling resetFirewall and then setFirewallType(ALLOWLIST) does
    // nothing. This seems like a clear bug.
    EXPECT_EQ(0, mFw.setFirewallType(ALLOWLIST));
    expectIptablesRestoreCommands(noCommands);
}

}  // namespace net
}  // namespace android
