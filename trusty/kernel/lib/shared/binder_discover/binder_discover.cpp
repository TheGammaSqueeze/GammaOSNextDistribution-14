/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <binder/IBinder.h>
#include <lib/binary_search_tree.h>
#include <lib/shared/binder_discover/binder_discover.h>

#if defined(TRUSTY_USERSPACE)
#include <binder/RpcSession.h>
#include <binder/RpcTransportTipcTrusty.h>
#include <lib/tipc/tipc_srv.h>
#else
#include <kernel/mutex.h>
#endif

struct DiscoveryTreeNode {
    struct bst_node node;
    std::string port;
    android::sp<android::IBinder> binder;

    DiscoveryTreeNode() = delete;
    DiscoveryTreeNode(std::string&& port)
            : node(BST_NODE_INITIAL_VALUE), port(std::move(port)) {}
    DiscoveryTreeNode(std::string&& port,
                      const android::sp<android::IBinder>& ib)
            : node(BST_NODE_INITIAL_VALUE), port(std::move(port)), binder(ib) {}

    static int compare_by_port(struct bst_node* a, struct bst_node* b) {
        auto nodea = containerof(a, DiscoveryTreeNode, node);
        auto nodeb = containerof(b, DiscoveryTreeNode, node);
        return nodea->port.compare(nodeb->port);
    }
};

static struct bst_root discovery_tree = BST_ROOT_INITIAL_VALUE;

#if defined(TRUSTY_USERSPACE)
static inline void lock_discovery_tree(void) {}
static inline void unlock_discovery_tree(void) {}
#else
static mutex_t discovery_tree_lock = MUTEX_INITIAL_VALUE(discovery_tree_lock);

static inline void lock_discovery_tree(void) {
    mutex_acquire(&discovery_tree_lock);
}

static inline void unlock_discovery_tree(void) {
    mutex_release(&discovery_tree_lock);
}
#endif

int binder_discover_get_service(const char* port,
                                android::sp<android::IBinder>& ib) {
    // Search the discovery tree to determine whether an in-process binder
    // exists for this port; if found, return it.
    DiscoveryTreeNode key{port};
    lock_discovery_tree();
    auto node = bst_search(&discovery_tree, &key.node,
                           DiscoveryTreeNode::compare_by_port);
    if (node != nullptr) {
        ib = containerof(node, DiscoveryTreeNode, node)->binder;
        unlock_discovery_tree();
        return android::OK;
    }
    unlock_discovery_tree();

#if defined(TRUSTY_USERSPACE)
    android::sp<android::RpcSession> sess = android::RpcSession::make(
            android::RpcTransportCtxFactoryTipcTrusty::make());
    android::status_t status = sess->setupPreconnectedClient({}, [=]() {
        int srv_fd = connect(port, IPC_CONNECT_WAIT_FOR_PORT);
        return srv_fd >= 0 ? android::binder::unique_fd(srv_fd)
                           : android::binder::unique_fd();
    });
    if (status != android::OK) {
        return status;
    }
    ib = sess->getRootObject();
#else
    panic("out-of-process services are currently unsupported in the kernel\n");
#endif
    if (!ib) {
        return android::BAD_VALUE;
    }
    return android::OK;
}

int binder_discover_add_service(const char* port,
                                const android::sp<android::IBinder>& ib) {
    auto node = new (std::nothrow) DiscoveryTreeNode{port, ib};
    if (node == nullptr) {
        return android::NO_MEMORY;
    }

    lock_discovery_tree();
    auto inserted = bst_insert(&discovery_tree, &node->node,
                               DiscoveryTreeNode::compare_by_port);
    unlock_discovery_tree();

    if (!inserted) {
        delete node;
        return android::ALREADY_EXISTS;
    }

    return android::OK;
}

int binder_discover_remove_service(const char* port) {
    DiscoveryTreeNode key{port};
    lock_discovery_tree();
    auto node = bst_search(&discovery_tree, &key.node,
                           DiscoveryTreeNode::compare_by_port);
    if (node != nullptr) {
        bst_delete(&discovery_tree, node);
    }
    unlock_discovery_tree();

    if (node == nullptr) {
        return android::NAME_NOT_FOUND;
    }

    // Destruct and free the underlying DiscoveryTreeNode
    auto full_node = containerof(node, DiscoveryTreeNode, node);
    delete full_node;

    return android::OK;
}
