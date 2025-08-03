/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <gtest/gtest.h>

#include <cstdio>
#include <string>

#include "UsbIpUtils.h"

/* No virtual USB devices are attached. */
static const std::string EMPTY_STATUS =
    "hub port sta spd dev      sockfd local_busid\n"
    "hs  0000 004 000 00000000 000000 0-0\n"
    "hs  0001 004 000 00000000 000000 0-0\n"
    "hs  0002 004 000 00000000 000000 0-0\n"
    "hs  0003 004 000 00000000 000000 0-0\n"
    "ss  0004 004 000 00000000 000000 0-0\n"
    "ss  0005 004 000 00000000 000000 0-0\n"
    "ss  0006 004 000 00000000 000000 0-0\n"
    "ss  0007 004 000 00000000 000000 0-0";

/* A single high speed an super speed device are attached. */
static const std::string PORTS_ALLOCATED =
    "hub port sta spd dev      sockfd local_busid\n"
    "hs  0000 006 003 00010003 000003 4-1\n"
    "hs  0001 004 000 00000000 000000 0-0\n"
    "hs  0002 004 000 00000000 000000 0-0\n"
    "hs  0003 004 000 00000000 000000 0-0\n"
    "ss  0004 006 004 00010004 000004 5-1\n"
    "ss  0005 004 000 00000000 000000 0-0\n"
    "ss  0006 004 000 00000000 000000 0-0\n"
    "ss  0007 004 000 00000000 000000 0-0";

/* All USB device ports are allocated. */
static const std::string NONE_AVAILABLE =
    "hub port sta spd dev      sockfd local_busid\n"
    "hs  0000 006 003 00010001 000003 4-1\n"
    "hs  0001 006 003 00010002 000004 4-2\n"
    "hs  0002 006 003 00010003 000005 4-3\n"
    "hs  0003 006 003 00010004 000006 4-4\n"
    "ss  0004 006 004 00010005 000007 5-1\n"
    "ss  0005 006 004 00010006 000008 5-2\n"
    "ss  0006 006 004 00010007 000009 5-3\n"
    "ss  0007 006 004 00010008 000010 5-4";

/*
 * Returns a file pointer associated with a std::string.
 * NOTE: User should call fclose on the pointer when done.
 */
static FILE *get_fp_from_string(const std::string &test_input) {
    return fmemopen((void *)test_input.c_str(), test_input.size(), "r");
}

TEST(UsbIpTest, ReturnsFirstHighSpeedPort) {
    FILE *file = get_fp_from_string(EMPTY_STATUS);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_HIGH), 0);
    fclose(file);
}

TEST(UsbIpTest, ReturnsFirstSuperSpeedPort) {
    FILE *file = get_fp_from_string(EMPTY_STATUS);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_SUPER), 4);
    fclose(file);
}

TEST(UsbIpTest, ReturnsFirstFreeHighSpeedPort) {
    FILE *file = get_fp_from_string(PORTS_ALLOCATED);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_HIGH), 1);
    fclose(file);
}

TEST(UsbIpTest, ReturnsFirstFreeSuperSpeedPort) {
    FILE *file = get_fp_from_string(PORTS_ALLOCATED);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_SUPER), 5);
    fclose(file);
}

TEST(UsbIpTest, AllHighSpeedPortsAllocatted) {
    FILE *file = get_fp_from_string(NONE_AVAILABLE);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_HIGH), -1);
    fclose(file);
}

TEST(UsbIpTest, AllSuperSpeedPortsAllocated) {
    FILE *file = get_fp_from_string(NONE_AVAILABLE);
    ASSERT_EQ(get_free_vhci_port(file, USBIP_SPEED_SUPER), -1);
    fclose(file);
}
