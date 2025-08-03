/*
 * Copyright (c) 2017 The Linux Foundation. All rights reserved.
 *
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdlib.h>

// HCI UART transport packet types (Volume 4, Part A, 2)

#define BT_PACKET_TYPE_UNKNOWN  0
#define BT_PACKET_TYPE_COMMAND  1
#define BT_PACKET_TYPE_ACL_DATA 2
#define BT_PACKET_TYPE_SCO_DATA 3
#define BT_PACKET_TYPE_EVENT    4
#define ANT_PACKET_TYPE_CTRL    0x0c
#define ANT_PACKET_TYPE_DATA    0x0e
#define FM_PACKET_TYPE_CMD      0x11
#define FM_PACKET_TYPE_EVENT    0x14

// Re-run |fn| system call until the system call doesn't cause EINTR.
#define CLIENT_NO_INTR(fn)  do {} while ((fn) == -1 && errno == EINTR)

#define LOG_TAG "bt_hidl_client"

// 2 bytes for opcode, 1 byte for parameter length (Volume 2, Part E, 5.4.1)
const int BT_COMMAND_PREAMBLE_SIZE = 3;
const int BT_LENGTH_OFFSET_CMD = 2;

// 2 bytes for handle, 2 bytes for data length (Volume 2, Part E, 5.4.2)
const int BT_ACL_PREAMBLE_SIZE = 4;
const int BT_LENGTH_OFFSET_ACL = 2;

// 2 bytes for handle, 1 byte for data length (Volume 2, Part E, 5.4.3)
const int BT_SCO_PREAMBLE_SIZE = 3;
const int BT_LENGTH_OFFSET_SCO = 2;

// 1 byte for event code, 1 byte for parameter length (Volume 2, Part E, 5.4.4)
const int BT_EVENT_PREAMBLE_SIZE = 2;
const int BT_LENGTH_OFFSET_EVT = 1;

const int ANT_COMMAND_PREAMBLE_SIZE = 1;
const int ANT_LENGTH_OFFSET_CMD = 0;

const int FM_COMMAND_PREAMBLE_SIZE = 3;
const int FM_LENGTH_OFFSET_CMD = 2;

const int FM_EVENT_PREAMBLE_SIZE = 2;
const int FM_LENGTH_OFFSET_EVT = 1;

const int PREAMBLE_SIZE_MAX = BT_ACL_PREAMBLE_SIZE;

