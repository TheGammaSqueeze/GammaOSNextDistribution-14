/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-modules-utils/sdk_level.h>
#include <cutils/misc.h>  // FIRST_APPLICATION_UID
#include <dlfcn.h>
#include <gtest/gtest.h>
#include <netinet/in.h>

#include "bpf/BpfUtils.h"

typedef int (*GetPortsBlockedForBind)(in_port_t*, size_t*);
GetPortsBlockedForBind getPortsBlockedForBind;
typedef int (*BlockPortForBind)(in_port_t);
BlockPortForBind blockPortForBind;
typedef int (*UnblockPortForBind)(in_port_t);
UnblockPortForBind unblockPortForBind;
typedef int (*UnblockAllPortsForBind)();
UnblockAllPortsForBind unblockAllPortsForBind;

class ConnectivityNativeBinderTest : public ::testing::Test {
  public:
    in_port_t mActualBlockedPorts[65535];
    size_t mActualBlockedPortsCount = 65535;
    bool restoreBlockedPorts;

    void SetUp() override {
        restoreBlockedPorts = false;

        // Skip test case if not on U.
        if (!android::modules::sdklevel::IsAtLeastU())
            GTEST_SKIP() << "Should be at least U device.";

        // Skip test case if not on 5.4 kernel which is required by bpf prog.
        if (!android::bpf::isAtLeastKernelVersion(5, 4, 0))
            GTEST_SKIP() << "Kernel should be at least 5.4.";

        // Necessary to use dlopen/dlsym since the lib is only available on U and there
        // is no Sdk34ModuleController in tradefed yet.
        // TODO: link against the library directly and add Sdk34ModuleController to
        // AndroidTest.txml when available.
        void* nativeLib = dlopen("libcom.android.tethering.connectivity_native.so", RTLD_NOW);
        ASSERT_NE(nullptr, nativeLib);
        getPortsBlockedForBind = reinterpret_cast<GetPortsBlockedForBind>(
                dlsym(nativeLib, "AConnectivityNative_getPortsBlockedForBind"));
        ASSERT_NE(nullptr, getPortsBlockedForBind);
        blockPortForBind = reinterpret_cast<BlockPortForBind>(
                dlsym(nativeLib, "AConnectivityNative_blockPortForBind"));
        ASSERT_NE(nullptr, blockPortForBind);
        unblockPortForBind = reinterpret_cast<UnblockPortForBind>(
                dlsym(nativeLib, "AConnectivityNative_unblockPortForBind"));
        ASSERT_NE(nullptr, unblockPortForBind);
        unblockAllPortsForBind = reinterpret_cast<UnblockAllPortsForBind>(
                dlsym(nativeLib, "AConnectivityNative_unblockAllPortsForBind"));
        ASSERT_NE(nullptr, unblockAllPortsForBind);

        // If there are already ports being blocked on device unblockAllPortsForBind() store
        // the currently blocked ports and add them back at the end of the test. Do this for
        // every test case so additional test cases do not forget to add ports back.
        int err = getPortsBlockedForBind(mActualBlockedPorts, &mActualBlockedPortsCount);
        EXPECT_EQ(err, 0);
        restoreBlockedPorts = true;
    }

    void TearDown() override {
        int err;
        if (mActualBlockedPortsCount > 0 && restoreBlockedPorts) {
            for (int i=0; i < mActualBlockedPortsCount; i++) {
                err = blockPortForBind(mActualBlockedPorts[i]);
                EXPECT_EQ(err, 0);
            }
        }
    }

  protected:
    void runSocketTest (sa_family_t family, const int type, bool blockPort) {
        int err;
        in_port_t port = 0;
        int sock, sock2;
        // Open two sockets with SO_REUSEADDR and expect they can both bind to port.
        sock = openSocket(&port, family, type, false /* expectBindFail */);
        sock2 = openSocket(&port, family, type, false /* expectBindFail */);

        int blockedPort = 0;
        if (blockPort) {
            blockedPort = ntohs(port);
            err = blockPortForBind(blockedPort);
            EXPECT_EQ(err, 0);
        }

        int sock3 = openSocket(&port, family, type, blockPort /* expectBindFail */);

        if (blockPort) {
            EXPECT_EQ(-1, sock3);
            err = unblockPortForBind(blockedPort);
            EXPECT_EQ(err, 0);
        } else {
            EXPECT_NE(-1, sock3);
        }

        close(sock);
        close(sock2);
        close(sock3);
    }

    /*
    * Open the socket and update the port.
    */
    int openSocket(in_port_t* port, sa_family_t family, const int type, bool expectBindFail) {
        int ret = 0;
        int enable = 1;
        const int sock = socket(family, type, 0);
        ret = setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &enable, sizeof(enable));
        EXPECT_EQ(0, ret);

        if (family == AF_INET) {
            struct sockaddr_in addr4 = { .sin_family = family, .sin_port = htons(*port) };
            ret = bind(sock, (struct sockaddr*) &addr4, sizeof(addr4));
        } else {
            struct sockaddr_in6 addr6 = { .sin6_family = family, .sin6_port = htons(*port) };
            ret = bind(sock, (struct sockaddr*) &addr6, sizeof(addr6));
        }

        if (expectBindFail) {
            EXPECT_NE(0, ret);
            // If port is blocked, return here since the port is not needed
            // for subsequent sockets.
            close(sock);
            return -1;
        }
        EXPECT_EQ(0, ret) << "bind unexpectedly failed, errno: " << errno;

        if (family == AF_INET) {
            struct sockaddr_in sin;
            socklen_t len = sizeof(sin);
            EXPECT_NE(-1, getsockname(sock, (struct sockaddr *)&sin, &len));
            EXPECT_NE(0, ntohs(sin.sin_port));
            if (*port != 0) EXPECT_EQ(*port, ntohs(sin.sin_port));
            *port = ntohs(sin.sin_port);
        } else {
            struct sockaddr_in6 sin;
            socklen_t len = sizeof(sin);
            EXPECT_NE(-1, getsockname(sock, (struct sockaddr *)&sin, &len));
            EXPECT_NE(0, ntohs(sin.sin6_port));
            if (*port != 0) EXPECT_EQ(*port, ntohs(sin.sin6_port));
            *port = ntohs(sin.sin6_port);
        }
        return sock;
    }
};

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV4Udp) {
    runSocketTest(AF_INET, SOCK_DGRAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV4Tcp) {
    runSocketTest(AF_INET, SOCK_STREAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV6Udp) {
    runSocketTest(AF_INET6, SOCK_DGRAM, false);
}

TEST_F(ConnectivityNativeBinderTest, PortUnblockedV6Tcp) {
    runSocketTest(AF_INET6, SOCK_STREAM, false);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort4Udp) {
    runSocketTest(AF_INET, SOCK_DGRAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort4Tcp) {
    runSocketTest(AF_INET, SOCK_STREAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort6Udp) {
    runSocketTest(AF_INET6, SOCK_DGRAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPort6Tcp) {
    runSocketTest(AF_INET6, SOCK_STREAM, true);
}

TEST_F(ConnectivityNativeBinderTest, BlockPortTwice) {
    int err = blockPortForBind(5555);
    EXPECT_EQ(err, 0);
    err = blockPortForBind(5555);
    EXPECT_EQ(err, 0);
    err = unblockPortForBind(5555);
    EXPECT_EQ(err, 0);
}

TEST_F(ConnectivityNativeBinderTest, GetBlockedPorts) {
    int err;
    in_port_t blockedPorts[8] = {1, 100, 1220, 1333, 2700, 5555, 5600, 65000};

    if (mActualBlockedPortsCount > 0) {
        err = unblockAllPortsForBind();
    }

    for (int i : blockedPorts) {
        err = blockPortForBind(i);
        EXPECT_EQ(err, 0);
    }
    size_t actualBlockedPortsCount = 8;
    in_port_t actualBlockedPorts[actualBlockedPortsCount];
    err = getPortsBlockedForBind((in_port_t*) actualBlockedPorts, &actualBlockedPortsCount);
    EXPECT_EQ(err, 0);
    EXPECT_NE(actualBlockedPortsCount, 0);
    for (int i=0; i < actualBlockedPortsCount; i++) {
        EXPECT_EQ(blockedPorts[i], actualBlockedPorts[i]);
    }

    // Remove the ports we added.
    err = unblockAllPortsForBind();
    EXPECT_EQ(err, 0);
    err = getPortsBlockedForBind(actualBlockedPorts, &actualBlockedPortsCount);
    EXPECT_EQ(err, 0);
    EXPECT_EQ(actualBlockedPortsCount, 0);
}

TEST_F(ConnectivityNativeBinderTest, UnblockAllPorts) {
    int err;
    in_port_t blockedPorts[8] = {1, 100, 1220, 1333, 2700, 5555, 5600, 65000};

    if (mActualBlockedPortsCount > 0) {
        err = unblockAllPortsForBind();
    }

    for (int i : blockedPorts) {
        err = blockPortForBind(i);
        EXPECT_EQ(err, 0);
    }

    size_t actualBlockedPortsCount = 8;
    in_port_t actualBlockedPorts[actualBlockedPortsCount];
    err = getPortsBlockedForBind((in_port_t*) actualBlockedPorts, &actualBlockedPortsCount);
    EXPECT_EQ(err, 0);
    EXPECT_EQ(actualBlockedPortsCount, 8);

    err = unblockAllPortsForBind();
    EXPECT_EQ(err, 0);
    err = getPortsBlockedForBind((in_port_t*) actualBlockedPorts, &actualBlockedPortsCount);
    EXPECT_EQ(err, 0);
    EXPECT_EQ(actualBlockedPortsCount, 0);
    // If mActualBlockedPorts is not empty, ports will be added back in teardown.
}

TEST_F(ConnectivityNativeBinderTest, CheckPermission) {
    int curUid = getuid();
    EXPECT_EQ(0, seteuid(FIRST_APPLICATION_UID + 2000)) << "seteuid failed: " << strerror(errno);
    int err = blockPortForBind((in_port_t) 5555);
    EXPECT_EQ(EPERM, err);
    EXPECT_EQ(0, seteuid(curUid)) << "seteuid failed: " << strerror(errno);
}
