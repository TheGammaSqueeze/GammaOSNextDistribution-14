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

#define TLOG_TAG "device_tree_client_lib"

#include <binder/RpcSession.h>
#include <binder/RpcTransportTipcTrusty.h>
#include <endian.h>
#include <lib/shared/binder_discover/binder_discover.h>
#include <lib/shared/device_tree/device_tree.h>
#include <lib/shared/ibinder/macros.h>
#include <stdio.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <com/android/trusty/device_tree/IDeviceTree.h>

#define LOCAL_TRACE (0)

using android::IBinder;
using com::android::trusty::device_tree::IDeviceTree;
using com::android::trusty::device_tree::INode;
using com::android::trusty::device_tree::INodeIterator;
using com::android::trusty::device_tree::IPropIterator;
using com::android::trusty::device_tree::Property;

struct device_tree_idevice_tree {};
struct device_tree_iprop_iter {};
struct device_tree_inode_iter {};
struct device_tree_inode {};
struct device_tree_prop {};

IBINDER_DEFINE_IFACE(IDeviceTree, device_tree_idevice_tree);
IBINDER_DEFINE_IFACE(IPropIterator, device_tree_iprop_iter);
IBINDER_DEFINE_IFACE(INodeIterator, device_tree_inode_iter);
IBINDER_DEFINE_IFACE(INode, device_tree_inode);
IBINDER_DEFINE_PARCELABLE(Property, device_tree_prop);

int device_tree_get_service(device_tree_idevice_tree** tree) {
    assert(tree != nullptr);

    android::sp<IBinder> cbinder;
    const char* port =
#if defined(TRUSTY_USERSPACE)
            IDeviceTree::PORT().c_str();
#else
            IDeviceTree::KERNEL_PORT().c_str();
#endif
    if (int rc = binder_discover_get_service(port, cbinder) != 0) {
        return rc;
    }
    auto bdr = new (std::nothrow) device_tree_idevice_tree_container{
            IDeviceTree::asInterface(cbinder), {}, 1};
    if (bdr == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *tree = &bdr->cbinder;

    return android::OK;
}

static int from_binder_status(android::binder::Status status) {
    if (status.serviceSpecificErrorCode() != 0) {
        return status.serviceSpecificErrorCode();
    }

    // TODO: ensure no overlapping error codes
    if (!status.isOk()) {
        return -status.transactionError();
    }

    return android::OK;
}

int device_tree_idevice_tree_get_compatible_nodes(
        struct device_tree_idevice_tree* self,
        const char* compat_str,
        struct device_tree_inode_iter** iter) {
    return device_tree_idevice_tree_get_compatible_nodes_from_list(
            self, &compat_str, 1, iter);
}

int device_tree_idevice_tree_get_compatible_nodes_from_list(
        struct device_tree_idevice_tree* self,
        const char** compat_str_list,
        size_t num_str,
        struct device_tree_inode_iter** iter) {
    assert(self != nullptr);

    std::vector<std::string> compat(compat_str_list, compat_str_list + num_str);
    android::sp<INodeIterator> node_iter;

    auto pidevice_tree = device_tree_idevice_tree_to_IDeviceTree(self);
    auto rc = pidevice_tree->get_compatible_nodes_from_list(compat, &node_iter);

    if (auto err = from_binder_status(rc); err != android::OK) {
        return err;
    }

    auto pinode_iter_container = new (std::nothrow)
            device_tree_inode_iter_container{node_iter, {}, 1};
    if (pinode_iter_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *iter = &pinode_iter_container->cbinder;

    return android::OK;
}

int device_tree_inode_iter_get_next_node(struct device_tree_inode_iter* iter,
                                         struct device_tree_inode** node) {
    assert(iter != nullptr);
    assert(node != nullptr);

    android::sp<INode> node_ptr;
    const auto pinode_iter = device_tree_inode_iter_to_INodeIterator(iter);
    auto rc = pinode_iter->get_next_node(&node_ptr);

    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto pinode_container =
            new (std::nothrow) device_tree_inode_container{node_ptr, {}, 1};
    if (pinode_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *node = &pinode_container->cbinder;

    return android::OK;
}

int device_tree_inode_get_name(struct device_tree_inode* node,
                               const char** name) {
    assert(node != nullptr);
    assert(name != nullptr);
    auto pnode = device_tree_inode_to_INode(node);
    std::string* node_name = new (std::nothrow) std::string();
    if (node_name == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    auto rc = pnode->get_name(node_name);
    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    *name = node_name->c_str();
    return android::OK;
}

int device_tree_inode_get_subnode(struct device_tree_inode* parent,
                                  const char* subnode_name,
                                  struct device_tree_inode** subnode) {
    assert(parent != nullptr);
    assert(subnode != nullptr);

    android::sp<INode> node_ptr;
    std::string node_name(subnode_name);

    const auto pinode = device_tree_inode_to_INode(parent);
    auto rc = pinode->get_subnode(node_name, &node_ptr);

    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto pinode_container =
            new (std::nothrow) device_tree_inode_container{node_ptr, {}, 1};
    if (pinode_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *subnode = &pinode_container->cbinder;

    return android::OK;
}

int device_tree_inode_get_subnodes(struct device_tree_inode* parent,
                                   struct device_tree_inode_iter** iter) {
    assert(parent != nullptr);
    assert(iter != nullptr);

    android::sp<INodeIterator> node_iter;

    const auto pinode = device_tree_inode_to_INode(parent);
    auto rc = pinode->get_subnodes(&node_iter);
    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto pinode_iter_container = new (std::nothrow)
            device_tree_inode_iter_container{node_iter, {}, 1};
    if (pinode_iter_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *iter = &pinode_iter_container->cbinder;

    return android::OK;
}

int device_tree_inode_get_prop(struct device_tree_inode* node,
                               const char* name,
                               struct device_tree_prop** prop) {
    assert(node != nullptr);
    assert(name != nullptr);
    assert(prop != nullptr);

    std::string property_name(name);

    auto property = new (std::nothrow) Property;
    if (property == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    const auto pinode = device_tree_inode_to_INode(node);
    auto rc = pinode->get_prop(property_name, property);

    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto pproperty_container =
            new (std::nothrow) device_tree_prop_container{property, {}, 1};
    if (pproperty_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *prop = &pproperty_container->cparcel;

    return android::OK;
}

int device_tree_inode_get_props(struct device_tree_inode* node,
                                struct device_tree_iprop_iter** prop) {
    assert(node != nullptr);
    assert(prop != nullptr);

    android::sp<IPropIterator> prop_iter;
    const auto pinode = device_tree_inode_to_INode(node);
    auto rc = pinode->get_props(&prop_iter);

    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto piprop_iter_container = new (std::nothrow)
            device_tree_iprop_iter_container{prop_iter, {}, 1};
    if (piprop_iter_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *prop = &piprop_iter_container->cbinder;

    return android::OK;
}

int device_tree_iprop_iter_get_next_prop(struct device_tree_iprop_iter* iter,
                                         struct device_tree_prop** prop) {
    assert(iter != nullptr);
    assert(prop != nullptr);

    auto property = new (std::nothrow) Property;
    if (property == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }

    const auto piprop_iter = device_tree_iprop_iter_to_IPropIterator(iter);
    auto rc = piprop_iter->get_next_prop(property);

    if (auto err = from_binder_status(rc); err != android::OK)
        return err;

    auto pproperty_container =
            new (std::nothrow) device_tree_prop_container{property, {}, 1};
    if (pproperty_container == nullptr) {
        return DT_ERROR_NO_MEMORY;
    }
    *prop = &pproperty_container->cparcel;
    return android::OK;
}

int device_tree_prop_get_name(struct device_tree_prop* prop,
                              const char** name,
                              size_t* name_len) {
    assert(prop != nullptr);
    assert(name != nullptr);
    assert(name_len != nullptr);

    auto pprop = device_tree_prop_to_Property(prop);
    *name = pprop->name.c_str();
    *name_len = pprop->name.size();
    return android::OK;
}

int device_tree_prop_get_value(struct device_tree_prop* prop,
                               uint8_t** value,
                               size_t* size) {
    assert(prop != nullptr);
    assert(value != nullptr);
    assert(size != nullptr);

    auto pprop = device_tree_prop_to_Property(prop);
    *size = pprop->value.size();
    *value = pprop->value.data();
    return android::OK;
}

int device_tree_prop_get_u32(struct device_tree_prop* prop, uint32_t* value) {
    assert(value != nullptr);
    uint32_t* tmp_ptr = NULL;
    size_t prop_size;
    int rc = device_tree_prop_get_value(prop, (uint8_t**)&tmp_ptr, &prop_size);
    if (rc != android::OK) {
        return rc;
    }
    if (prop_size != sizeof(uint32_t)) {
        TLOGI("Property is not a u32\n");
        return DT_ERROR_INVALID_ARGS;
    }
    /* Convert from big-endian to little endian and write to output pointer */
    *value = be32toh(*tmp_ptr);
    return android::OK;
}

int device_tree_prop_get_u64(struct device_tree_prop* prop, uint64_t* value) {
    assert(value != nullptr);
    uint64_t* tmp_ptr = NULL;
    size_t prop_size;
    int rc = device_tree_prop_get_value(prop, (uint8_t**)&tmp_ptr, &prop_size);
    if (rc != android::OK) {
        return rc;
    }
    if (prop_size != sizeof(uint64_t)) {
        TLOGI("Property is not a u64\n");
        return DT_ERROR_INVALID_ARGS;
    }
    /* Convert from big-endian to little endian and write to output pointer */
    *value = be64toh(*tmp_ptr);
    return android::OK;
}
