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

#pragma once
#include <com/android/trusty/device_tree/BnDeviceTree.h>

using android::sp;
using android::binder::Status;

namespace com {
namespace android {
namespace trusty {
namespace device_tree {

class PropIterator : public BnPropIterator {
public:
    PropIterator(int prop_offset, const unsigned char* dtb)
            : BnPropIterator(),
              mInitialPropOffset(prop_offset),
              mCurrentPropOffset(-1),
              mDtb(dtb) {}

    Status get_next_prop(Property* prop);

private:
    int mInitialPropOffset;
    int mCurrentPropOffset;
    const unsigned char* mDtb;
};

class Node : public BnNode {
public:
    Node(int offset, const unsigned char* dtb)
            : BnNode(), mNodeOffset(offset), mDtb(dtb) {}

    Status get_name(std::string* node_name);

    Status get_subnode(const std::string& node_name, sp<INode>* node);

    Status get_subnodes(sp<INodeIterator>* node_iter);

    Status get_props(sp<IPropIterator>* prop_iter);

    Status get_prop(const std::string& prop_name, Property* prop);

private:
    int mNodeOffset;
    const unsigned char* mDtb;
};

class NodeIterator : public BnNodeIterator {
public:
    // Creates an iterator over the subnodes of the node at the initial
    // offset
    NodeIterator(int initial_offset, const unsigned char* dtb)
            : BnNodeIterator(),
              mCompatibleStrs(std::nullopt),
              mInitialNodeOffset(initial_offset),
              mCurrentNodeOffset(-1),
              mDtb(dtb) {}

    // Creates an iterator over the nodes matching one of the compatible
    // strings
    NodeIterator(const std::vector<std::string>& compatible,
                 int initial_offset,
                 const unsigned char* dtb)
            : BnNodeIterator(),
              mCompatibleStrs(std::move(compatible)),
              mInitialNodeOffset(initial_offset),
              mCurrentNodeOffset(-1),
              mDtb(dtb) {}

    Status get_next_node(sp<INode>* node);

private:
    std::optional<std::vector<std::string>> mCompatibleStrs;
    int mInitialNodeOffset;
    int mCurrentNodeOffset;
    const unsigned char* mDtb;
};

class DeviceTree : public BnDeviceTree {
public:
    DeviceTree(const unsigned char* dtb, size_t dtb_size);

    // Get an iterator over nodes with one of the given compatible strings.
    Status get_compatible_nodes_from_list(
            const std::vector<std::string>& compatible_strs,
            sp<INodeIterator>* node_iter);

private:
    const unsigned char* mDtb;
};
}  // namespace device_tree
}  // namespace trusty
}  // namespace android
}  // namespace com
