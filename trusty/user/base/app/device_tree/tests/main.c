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

#define TLOG_TAG "device_tree_user_test"
#define LOCAL_TRACE 0

#include <endian.h>
#include <lib/shared/device_tree/device_tree.h>
#include <lib/unittest/unittest.h>
#include <string.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define TEST_NODE_COMPAT "google,test_node"

typedef struct device_tree_ctxt {
    struct device_tree_idevice_tree* tree;
    /* The root test node is not necessarily the rot of the device tree */
    struct device_tree_inode* root_node;
} DeviceTreeTest_t;

TEST_F_SETUP(DeviceTreeTest) {
    struct device_tree_inode_iter* iter = NULL;

    _state->tree = NULL;
    int rc = device_tree_get_service(&_state->tree);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(_state->tree, NULL);

    /* This is the compatible string of the root test node */
    const char* root_compat = TEST_NODE_COMPAT;
    rc = device_tree_idevice_tree_get_compatible_nodes(_state->tree,
                                                       root_compat, &iter);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(iter, NULL);

    rc = device_tree_inode_iter_get_next_node(iter, &_state->root_node);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(_state->root_node, NULL);

    /*
     * The test context stores the device tree interface and the root node so we
     * shouldn't release those yet
     */
test_abort:
    if (iter) {
        device_tree_inode_iter_release(&iter);
    }
}

TEST_F_TEARDOWN(DeviceTreeTest) {
    /* Test setup ensures these aren't null */
    device_tree_inode_release(&_state->root_node);
    device_tree_idevice_tree_release(&_state->tree);
}

TEST_F(DeviceTreeTest, get_prop) {
    /* Get a property */
    struct device_tree_prop* prop = NULL;
    int rc = device_tree_inode_get_prop(_state->root_node, "compatible", &prop);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(prop, NULL);

    /* Check that the property's name is correct */
    const char* prop_name = NULL;
    size_t len;
    rc = device_tree_prop_get_name(prop, &prop_name, &len);
    ASSERT_NE((char*)prop_name, NULL);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(strcmp(prop_name, "compatible"), 0);
    EXPECT_EQ(len, strlen("compatible"));

    /* Check that the property's value is correct */
    char* prop_value = NULL;
    rc = device_tree_prop_get_value(prop, (uint8_t**)&prop_value, &len);
    ASSERT_NE(prop_value, NULL);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(strcmp(prop_value, TEST_NODE_COMPAT), 0);
    /* Add 1 since string property values include the null-terminator */
    EXPECT_EQ(len, strlen(TEST_NODE_COMPAT) + 1);

test_abort:
    if (prop) {
        device_tree_prop_release(&prop);
    }
}

TEST_F(DeviceTreeTest, get_node) {
    struct device_tree_inode* node = NULL;
    struct device_tree_prop* prop = NULL;

    /* Get a node */
    int rc = device_tree_inode_get_subnode(_state->root_node, "chosen", &node);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(node, NULL);

    /* Get a property in the node */
    rc = device_tree_inode_get_prop(node, "kaslr-seed", &prop);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(prop, NULL);

    /* Check the property's value */
    uint8_t* prop_value = NULL;
    size_t len;
    uint8_t expected_value[] = {0xCA, 0xFE, 0xD0, 0x0D, 0x12, 0x34, 0x56, 0x78};
    rc = device_tree_prop_get_value(prop, &prop_value, &len);
    ASSERT_NE(prop_value, NULL);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(memcmp(prop_value, &expected_value, 8), 0);
    EXPECT_EQ(len, 8);

    /* Check the property's value using the u64 helper */
    uint64_t prop_value_u64 = 0;
    rc = device_tree_prop_get_u64(prop, &prop_value_u64);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(prop_value_u64, 0xCAFED00D12345678);

    /* Check that we can't use the u32 helper for a u64 */
    uint32_t prop_value_u32 = 0;
    rc = device_tree_prop_get_u32(prop, &prop_value_u32);
    EXPECT_EQ(rc, DT_ERROR_INVALID_ARGS);

test_abort:
    if (prop) {
        device_tree_prop_release(&prop);
    }
    if (node) {
        device_tree_inode_release(&node);
    }
}

TEST_F(DeviceTreeTest, iter_props) {
    struct device_tree_inode* node = NULL;
    struct device_tree_iprop_iter* prop_iter = NULL;
    struct device_tree_prop* prop = NULL;

    /* Get a node */
    int rc = device_tree_inode_get_subnode(_state->root_node, "chosen", &node);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(node, NULL);

    /* Get an iterator over the node's properties */
    rc = device_tree_inode_get_props(node, &prop_iter);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(prop_iter, NULL);

    /* Iterate over the properties */
    const char* expected_prop_names[] = {"kaslr-seed", "bootargs", NULL};
    int expected_rc[] = {NO_ERROR, NO_ERROR, DT_ERROR_PROP_NOT_FOUND};
    for (size_t i = 0; i < countof(expected_prop_names); i++) {
        /* Advance the property iterator */
        rc = device_tree_iprop_iter_get_next_prop(prop_iter, &prop);
        ASSERT_EQ(rc, expected_rc[i]);

        if (rc != NO_ERROR) {
            /*
             * prop is only non-null if there is a property, so no need to
             * release here
             */
            continue;
        }
        /* If there was a property, check its name */
        const char* prop_name = NULL;
        size_t len;
        ASSERT_NE(prop, NULL);
        rc = device_tree_prop_get_name(prop, &prop_name, &len);
        ASSERT_EQ(rc, NO_ERROR);
        ASSERT_NE((char*)prop_name, NULL);
        EXPECT_EQ(strcmp(prop_name, expected_prop_names[i]), 0);
        EXPECT_EQ(len, strlen(expected_prop_names[i]));

        device_tree_prop_release(&prop);
    }

test_abort:
    if (prop_iter) {
        device_tree_iprop_iter_release(&prop_iter);
    }
    if (node) {
        device_tree_inode_release(&node);
    }
    if (prop) {
        device_tree_prop_release(&prop);
    }
}

TEST_F(DeviceTreeTest, iter_nodes) {
    struct device_tree_inode_iter* node_iter = NULL;
    struct device_tree_inode* subnode = NULL;
    struct device_tree_prop* subnode_prop = NULL;

    /* Get an iterator over the root node's subnodes */
    int rc = device_tree_inode_get_subnodes(_state->root_node, &node_iter);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(node_iter, NULL);

    /* Iterate over the subnodes */
    int expected_rc[] = {NO_ERROR, NO_ERROR, DT_ERROR_NODE_NOT_FOUND};
    const char* subnode_names[] = {"chosen", "interrupt-controller@DEADBEEF",
                                   NULL};
    const char* subnode_props[] = {"kaslr-seed", "reg", NULL};
    for (size_t i = 0; i < countof(subnode_names); i++) {
        /* Advance the node iterator */
        rc = device_tree_inode_iter_get_next_node(node_iter, &subnode);
        ASSERT_EQ(rc, expected_rc[i]);

        if (rc != NO_ERROR) {
            /*
             * subnode_prop is only non-null if there is a property, so no need
             * to release here
             */
            continue;
        }
        /* If there was a node, check its name */
        ASSERT_NE(subnode, NULL);
        const char* subnode_name = NULL;
        rc = device_tree_inode_get_name(subnode, &subnode_name);
        ASSERT_EQ(rc, NO_ERROR);
        ASSERT_NE((char*)subnode_name, NULL);
        EXPECT_EQ(strcmp(subnode_name, subnode_names[i]), 0);

        /* Check for a property that the node is expected to have */
        rc = device_tree_inode_get_prop(subnode, subnode_props[i],
                                        &subnode_prop);
        ASSERT_EQ(rc, NO_ERROR);
        ASSERT_NE(subnode_prop, NULL);

        /* Check the property's name */
        const char* prop_name = NULL;
        size_t len;
        rc = device_tree_prop_get_name(subnode_prop, &prop_name, &len);
        ASSERT_EQ(rc, NO_ERROR);
        ASSERT_NE((char*)prop_name, NULL);
        EXPECT_EQ(strcmp(prop_name, subnode_props[i]), 0);
        EXPECT_EQ(len, strlen(subnode_props[i]));

        device_tree_prop_release(&subnode_prop);
        device_tree_inode_release(&subnode);
    }

test_abort:
    if (node_iter) {
        device_tree_inode_iter_release(&node_iter);
    }
    if (subnode) {
        device_tree_inode_release(&subnode);
    }
    if (subnode_prop) {
        device_tree_prop_release(&subnode_prop);
    }
}

#if defined(TRUSTY_USERSPACE)
PORT_TEST(DeviceTreeTest, "com.android.trusty.device_tree.test")
#else
PORT_TEST(DeviceTreeTest, "com.android.kernel.device_tree.test")
#endif
