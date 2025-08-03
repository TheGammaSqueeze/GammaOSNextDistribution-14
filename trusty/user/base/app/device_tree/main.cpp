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

#define TLOG_TAG "device_tree_user_service"
#define LOCAL_TRACE 0

#include <lib/shared/device_tree/service/device_tree_service.h>
#include <lib/unittest/unittest.h>
#include <lib/vmm_obj/vmm_obj.h>
#include <lk/err_ptr.h>
#include <string.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#include <binder/RpcServerTrusty.h>
#include <com/android/trusty/device_tree/IDeviceTree.h>

using com::android::trusty::device_tree::DeviceTree;
using com::android::trusty::device_tree::IDeviceTree;

int main(void) {
    TLOGI("Mapping in device tree blob\n");
    const void* dtb = NULL;
    size_t dtb_size = 0;
    int rc = vmm_obj_map_ro("com.android.trusty.kernel.device_tree.blob", &dtb,
                            &dtb_size);
    if (rc != NO_ERROR) {
        TLOGE("vmm_obj_map failed to map in device tree blob (%d)\n", rc);
        return EXIT_FAILURE;
    }
    if (!dtb || !dtb_size) {
        TLOGE("Failed to map device tree blob into dt server process\n");
        return EXIT_FAILURE;
    }
    TLOGI("Mapped device tree blob (%zu bytes) into dt server process at %p\n",
          dtb_size, dtb);

    TLOGI("Starting service\n");

    tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    const auto port_acl = android::RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };

    /* message size should be large enough to cover any message sent by tests */
    constexpr size_t max_msg_size = 256;
    auto srv = android::RpcServerTrusty::make(
            hset, IDeviceTree::PORT().c_str(),
            std::make_shared<const android::RpcServerTrusty::PortAcl>(port_acl),
            max_msg_size);
    if (srv == nullptr) {
        return EXIT_FAILURE;
    }

    android::sp<DeviceTree> test_srv = android::sp<DeviceTree>::make(
            static_cast<unsigned char*>(const_cast<void*>(dtb)), dtb_size);
    if (!test_srv) {
        TLOGE("Failed to create DeviceTree server\n");
        return EXIT_FAILURE;
    }
    srv->setRootObject(test_srv);

    return tipc_run_event_loop(hset);
}
