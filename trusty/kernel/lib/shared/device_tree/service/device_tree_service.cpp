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

#define TLOG_TAG "device_tree_service_lib"

#include <lk/compiler.h>
#include <lk/trace.h>
#include <trusty_log.h>

#include <libfdt.h>

#include <lib/shared/device_tree/service/device_tree_service.h>

#define LOCAL_TRACE 0

using com::android::trusty::device_tree::DeviceTree;
using com::android::trusty::device_tree::IDeviceTree;

namespace com {
namespace android {
namespace trusty {
namespace device_tree {

// Returns the offset of the first node with one of the given `compatible`
// strings or `nullopt` if no matching nodes were found. This is analogous to
// fdt_node_offset_by_compatible in libfdt.
static std::optional<int> NodeOffsetByCompatibleList(
        const void* dtb,
        int startoffset,
        const std::vector<std::string>& compatible_strs) {
    // For each compatible string, find the first matching node's offset and
    // keep the minimum of all node offsets
    int min_node_offset = INT_MAX;
    for (const auto& compat : compatible_strs) {
        int node_offset = ::fdt_node_offset_by_compatible(dtb, startoffset,
                                                          compat.c_str());
        if (node_offset < 0) {
            // Other return codes indicate a dtb in an invalid state
            assert(node_offset == -FDT_ERR_NOTFOUND);

            // No nodes matching `compat` were found
            continue;
        }

        min_node_offset = std::min(min_node_offset, node_offset);
    }

    // If min_node_offset is unchanged, no matching nodes were found
    if (min_node_offset == INT_MAX) {
        return std::nullopt;
    }

    return std::optional<int>{min_node_offset};
}

DeviceTree::DeviceTree(const unsigned char* dtb, size_t dtb_size)
        : BnDeviceTree(), mDtb(dtb) {
    int rc = ::fdt_check_full(dtb, dtb_size);
    assert(rc == 0);
}

Status DeviceTree::get_compatible_nodes_from_list(
        const std::vector<std::string>& compatible_strs,
        sp<INodeIterator>* node_iter) {
    if (node_iter == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    auto min_node_offset = NodeOffsetByCompatibleList(
            mDtb, -1 /* search from start */, compatible_strs);
    if (!min_node_offset.has_value()) {
        TLOGI("No nodes matching the compatible strings found\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_NODE_NOT_FOUND);
    }

    sp<NodeIterator> iter = sp<NodeIterator>::make(std::move(compatible_strs),
                                                   *min_node_offset, mDtb);
    if (iter == nullptr) {
        TLOGE("Failed to allocate memory for NodeIterator\n");
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_NO_MEMORY);
    }
    *node_iter = iter;

    return Status::ok();
}

Status NodeIterator::get_next_node(sp<INode>* node) {
    if (node == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    // If the current node offset is -1, use the node offset that was
    // passed in to the constructor
    if (mCurrentNodeOffset == -1) {
        mCurrentNodeOffset = mInitialNodeOffset;
    } else {
        // Advance current node offset to the next node
        if (mCompatibleStrs.has_value()) {
            // Search for next node with matching compatible strings
            auto min_node_offset = NodeOffsetByCompatibleList(
                    mDtb, mCurrentNodeOffset, *mCompatibleStrs);

            if (!min_node_offset.has_value()) {
                TLOGI("Reached the end of the node iterator\n");
                return Status::fromServiceSpecificError(
                        IDeviceTree::ERROR_NODE_NOT_FOUND);
            }

            mCurrentNodeOffset = *min_node_offset;

        } else {
            // Search for next subnode
            int next_subnode = ::fdt_next_subnode(mDtb, mCurrentNodeOffset);

            if (next_subnode < 0) {
                // Other return codes indicate a dtb in an invalid state
                assert(next_subnode == -FDT_ERR_NOTFOUND);

                TLOGI("Reached the end of the node iterator\n");
                return Status::fromServiceSpecificError(
                        IDeviceTree::ERROR_NODE_NOT_FOUND);
            }

            mCurrentNodeOffset = next_subnode;
        }
    }

    // mCurrentNodeOffset was either initialized to a valid node offset
    // by the constructor or advanced to another valid node offset by a
    // previous call to `get_next_node`
    sp<Node> next_node = sp<Node>::make(mCurrentNodeOffset, mDtb);
    if (next_node == nullptr) {
        TLOGE("Failed to allocate memory for Node\n");
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_NO_MEMORY);
    }
    *node = next_node;

    return Status::ok();
}

Status Node::get_name(std::string* node_name) {
    if (node_name == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    const char* fdt_node_name = ::fdt_get_name(mDtb, mNodeOffset, NULL);
    if (fdt_node_name == nullptr) {
        TLOGE("No name found for node with offset %d\n", mNodeOffset);
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_NODE_NOT_FOUND);
    }
    *node_name = fdt_node_name;
    return Status::ok();
}

Status Node::get_subnode(const std::string& node_name, sp<INode>* node) {
    if (node == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    int subnode_offset =
            ::fdt_subnode_offset(mDtb, mNodeOffset, node_name.c_str());
    if (subnode_offset < 0) {
        TLOGI("No subnode named %s was found\n", node_name.c_str());

        // Other return codes indicate a dtb in an invalid state
        assert(subnode_offset == -FDT_ERR_NOTFOUND);

        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_NODE_NOT_FOUND);
    }
    sp<Node> subnode = sp<Node>::make(subnode_offset, mDtb);
    if (subnode == nullptr) {
        TLOGE("Failed to allocate memory for Node\n");
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_NO_MEMORY);
    }
    *node = subnode;
    return Status::ok();
}

Status Node::get_subnodes(sp<INodeIterator>* node_iter) {
    if (node_iter == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    int subnode_offset = ::fdt_first_subnode(mDtb, mNodeOffset);
    if (subnode_offset < 0) {
        TLOGI("No matching subnodes were found\n");

        // Other return codes indicate a dtb in an invalid state
        assert(subnode_offset == -FDT_ERR_NOTFOUND);

        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_NODE_NOT_FOUND);
    }

    sp<NodeIterator> iter = sp<NodeIterator>::make(subnode_offset, mDtb);
    if (iter == nullptr) {
        TLOGE("Failed to allocate memory for NodeIterator\n");
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_NO_MEMORY);
    }
    *node_iter = iter;

    return Status::ok();
}

Status Node::get_props(sp<IPropIterator>* prop_iter) {
    if (prop_iter == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    int prop_offset = ::fdt_first_property_offset(mDtb, mNodeOffset);
    if (prop_offset < 0) {
        TLOGI("Node has no properties\n");

        // Other return codes indicate a dtb in an invalid state
        assert(prop_offset == -FDT_ERR_NOTFOUND);

        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_PROP_NOT_FOUND);
    }

    sp<PropIterator> iter = sp<PropIterator>::make(prop_offset, mDtb);
    if (iter == nullptr) {
        TLOGE("Failed to allocate memory for PropIterator\n");
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_NO_MEMORY);
    }
    *prop_iter = iter;

    return Status::ok();
}

Status Node::get_prop(const std::string& prop_name, Property* prop) {
    if (prop == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    int len = -1;
    const struct fdt_property* fdt_prop =
            ::fdt_get_property(mDtb, mNodeOffset, prop_name.c_str(), &len);

    if (len < 0) {
        TLOGE("Node has no property named %s\n", prop_name.c_str());

        // Other return codes indicate a dtb in an invalid state or an
        // internal error in the device tree service
        assert(len == -FDT_ERR_NOTFOUND);

        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_PROP_NOT_FOUND);
    }

    // This indicates an internal error in libfdt
    if (fdt_prop == nullptr) {
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_GENERIC);
    }

    prop->value.resize(len);
    prop->value.assign(fdt_prop->data, fdt_prop->data + len);

    prop->name = prop_name;

    return Status::ok();
}

Status PropIterator::get_next_prop(Property* prop) {
    if (prop == nullptr) {
        TLOGE("Invalid arguments\n");
        return Status::fromServiceSpecificError(
                IDeviceTree::ERROR_INVALID_ARGS);
    }

    // If the current prop offset is -1, use the prop offset that was
    // passed in to the constructor
    if (mCurrentPropOffset == -1) {
        mCurrentPropOffset = mInitialPropOffset;
    } else {
        // Advance current prop offset to the next prop
        int next_prop = ::fdt_next_property_offset(mDtb, mCurrentPropOffset);
        if (next_prop < 0) {
            TLOGI("Reached the end of the property iterator\n");

            // Other return codes indicate a dtb in an invalid state
            assert(next_prop == -FDT_ERR_NOTFOUND);

            return Status::fromServiceSpecificError(
                    IDeviceTree::ERROR_PROP_NOT_FOUND);
        }
        mCurrentPropOffset = next_prop;
    }

    int len = -1;
    const struct fdt_property* fdt_prop =
            ::fdt_get_property_by_offset(mDtb, mCurrentPropOffset, &len);

    if (len < 0) {
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_GENERIC);
    }

    prop->value.resize(len);
    prop->value.assign(fdt_prop->data, fdt_prop->data + len);

    int prop_len;
    const char* prop_name =
            fdt_get_string(mDtb, fdt32_ld(&fdt_prop->nameoff), &prop_len);
    if (prop_len < 0) {
        return Status::fromServiceSpecificError(IDeviceTree::ERROR_GENERIC);
    }
    prop->name = std::string(prop_name, (size_t)prop_len);

    return Status::ok();
}

}  // namespace device_tree
}  // namespace trusty
}  // namespace android
}  // namespace com
