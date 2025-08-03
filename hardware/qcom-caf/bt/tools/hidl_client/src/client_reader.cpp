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

#include <android/hardware/bluetooth/1.0/IBluetoothHci.h>

#include <com/qualcomm/qti/ant/1.0/IAntHci.h>
#include <com/qualcomm/qti/ant/1.0/IAntHciCallbacks.h>
#include <com/qualcomm/qti/ant/1.0/types.h>

#include <vendor/qti/hardware/fm/1.0/IFmHci.h>
#include <vendor/qti/hardware/fm/1.0/IFmHciCallbacks.h>
#include <vendor/qti/hardware/fm/1.0/types.h>

#include <sys/socket.h>
#include <cutils/sockets.h>
#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <sys/un.h>
#include <errno.h>
#include <utils/Log.h>
#include <sys/select.h>
#include "hci_internals.h"

using android::hardware::bluetooth::V1_0::IBluetoothHci;
using ::android::hardware::hidl_vec;
using com::qualcomm::qti::ant::V1_0::IAntHci;
using vendor::qti::hardware::fm::V1_0::IFmHci;

extern android::sp<IBluetoothHci> btHci;
extern android::sp<IAntHci> antHci;
extern android::sp<IFmHci> fmHci;

extern int server_fd;

static int get_preamble_length(char packet_type);
static int get_pkt_len_offset(char packet_type);
static int get_payload_len(char packet_type, unsigned char *preamble);
static int safe_read(int server_fd, unsigned char* buf, int read_len);

void *process_tool_data(void *arg) {

    int retval;
    int preamble_len, payload_len;
    unsigned char preamble[PREAMBLE_SIZE_MAX];
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(server_fd, &readfds);
    unsigned char packet_type;

    hidl_vec<uint8_t> *data;
    data = new hidl_vec<uint8_t>;

    do {
        retval = select(server_fd + 1, &readfds, NULL, NULL, NULL);
        if (retval == -1) {
            ALOGE("select failed, Error: %s (%d)\n", strerror(errno),
                                errno);
            break;
        }

        if (safe_read(server_fd, &packet_type, 1) == -1) {
            ALOGE("%s: failed to read the packet from tools", __func__);
            break;
        }
        ALOGI("%s: Packet type :%d", __func__,packet_type);
        if ((preamble_len = get_preamble_length(packet_type))== -1) {
            ALOGE("%s: Unsupported preamble_len packet type %d",__func__, packet_type);
            break;
        }
        if (safe_read(server_fd, preamble, preamble_len) == -1) {
            ALOGE("%s: failed to read the packet from tools", __func__);
            break;
        }
        if ((payload_len = get_payload_len(packet_type, preamble)) == -1) {
            ALOGE("%s: Invalid payload length ", __func__);
            break;
        }
        data->resize(preamble_len + payload_len);
        memcpy(data->data(), preamble, preamble_len);
        if (safe_read(server_fd, data->data() + preamble_len, payload_len) == -1) {
            ALOGE("%s: failed to read the packet from tool", __func__);
            break;
        }
        switch (packet_type) {
            case BT_PACKET_TYPE_COMMAND:
                btHci->sendHciCommand(*data);
                break;

            case BT_PACKET_TYPE_ACL_DATA:
                btHci->sendAclData(*data);
                break;

            case BT_PACKET_TYPE_SCO_DATA:
                btHci->sendScoData(*data);
                break;

            case ANT_PACKET_TYPE_CTRL:
                antHci->sendAntControl(*data);
                break;

            case ANT_PACKET_TYPE_DATA:
                antHci->sendAntData(*data);
                break;

            case FM_PACKET_TYPE_CMD:
                ALOGI("%s: Send FM Cmd ", __func__);
                fmHci->sendHciCommand(*data);
                break;
            default:
                ALOGE("%s: Unsupported packet type: %d", __func__, packet_type);
        }
    } while (1);
    free(data);
    return NULL;
}

static int get_preamble_length(char packet_type) {

    int preamble_len = -1;
    switch (packet_type) {
        case BT_PACKET_TYPE_COMMAND:
            preamble_len = BT_COMMAND_PREAMBLE_SIZE;
            break;

        case BT_PACKET_TYPE_ACL_DATA:
            preamble_len = BT_ACL_PREAMBLE_SIZE;
            break;

        case BT_PACKET_TYPE_SCO_DATA:
            preamble_len = BT_SCO_PREAMBLE_SIZE;
            break;

        case BT_PACKET_TYPE_EVENT:
            preamble_len = BT_EVENT_PREAMBLE_SIZE;
            break;

        case ANT_PACKET_TYPE_CTRL:
            preamble_len = ANT_COMMAND_PREAMBLE_SIZE;
            break;

        case ANT_PACKET_TYPE_DATA:
            preamble_len = ANT_COMMAND_PREAMBLE_SIZE;
            break;

        case FM_PACKET_TYPE_CMD:
            preamble_len = FM_COMMAND_PREAMBLE_SIZE;
            break;

        case FM_PACKET_TYPE_EVENT:
            preamble_len = FM_EVENT_PREAMBLE_SIZE;
            break;

        default:
            break;
    }
    return preamble_len;
};

static int get_pkt_len_offset(char packet_type) {

    int len_offset = -1;
    switch (packet_type) {
        case BT_PACKET_TYPE_COMMAND:
            len_offset = BT_LENGTH_OFFSET_CMD;
            break;

        case BT_PACKET_TYPE_ACL_DATA:
            len_offset = BT_LENGTH_OFFSET_ACL;
            break;

        case BT_PACKET_TYPE_SCO_DATA:
            len_offset = BT_LENGTH_OFFSET_SCO;
            break;

        case BT_PACKET_TYPE_EVENT:
            len_offset = BT_LENGTH_OFFSET_EVT;
            break;

        case ANT_PACKET_TYPE_CTRL:
            len_offset = ANT_LENGTH_OFFSET_CMD;
            break;

        case ANT_PACKET_TYPE_DATA:
            len_offset = ANT_LENGTH_OFFSET_CMD;
            break;

        case FM_PACKET_TYPE_CMD:
            len_offset = FM_LENGTH_OFFSET_CMD;
            break;

        case FM_PACKET_TYPE_EVENT:
            len_offset = FM_LENGTH_OFFSET_EVT;
            break;

        default:
            break;
    }
    return len_offset;
}

static int get_payload_len(char packet_type, unsigned char *preamble) {

    int len_offset;
    if ((len_offset = get_pkt_len_offset(packet_type)) == -1) {
        ALOGE("%s: Unsupported packet type, failed to get length", __func__);
        return len_offset;
    }

    if (packet_type != BT_PACKET_TYPE_ACL_DATA) return preamble[len_offset];
    return (((preamble[len_offset + 1]) << 8) | preamble[len_offset]);
}

static int safe_read(int server_fd, unsigned char* buf, int read_len) {

    int bytes_read = 0;
    int ret = 0;

    if (buf == NULL) {
        ALOGE("%s: The buffer is NULL", __func__);
        return -1;
    }

    if (!read_len) {
        ALOGE("%s: No data to read", __func__);
        return 0;
    }

    while (read_len > 0) {
        CLIENT_NO_INTR(ret = read(server_fd, buf+bytes_read, read_len));
        if (ret < 0) {
            ALOGE("%s: Read error: (%s)", __func__, strerror(errno));
            return -1;
        } else if (ret == 0) {
            ALOGE("%s: read returned 0, err = %s, read bytes: %d",
                              __func__, strerror(errno), (unsigned int)(bytes_read));
            return -1;
        } else {
            read_len -= ret;
            bytes_read += ret;
        }
    }
    return bytes_read;
}
