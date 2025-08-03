/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

#include <lib/binary_search_tree.h>
#include <lk/compiler.h>
#include <stdlib.h>

#include "gtest/gtest.h"

class BstTest : public testing::TestWithParam<bool> {
};

struct bst_test_entry {
    struct bst_node node;
};

INSTANTIATE_TEST_SUITE_P(BstTestMirror, BstTest, testing::Bool());

static int bst_test_compare(struct bst_node *a, struct bst_node *b) {
    if (BstTest::GetParam()) {
        return a > b ? 1 : a < b ? -1 : 0;
    } else {
        return a < b ? 1 : a > b ? -1 : 0;
    }
}

static struct bst_node *bst_test_search(struct bst_root *root,
                                        struct bst_node *node) {
    return bst_search(root, node, bst_test_compare);
}

static struct bst_node *bst_node(struct bst_node nodes[], size_t index[],
                                 size_t i, size_t i_size) {
    if (BstTest::GetParam()) {
        i = i_size - 1 - i;
    }
    if (index) {
        i = index[i];
    }
    return &nodes[i];
}

std::ostream& operator<<(std::ostream& os, const struct bst_node* node) {
  return os <<
    "Node (" << (void*)node << "):\n" <<
    "  Parent:" << (void*)node->parent << "\n" <<
    "  Rank: " << node->rank << "\n" <<
    "  Left child: " << (void*)node->child[0] << "\n" <<
    "  Right child: " << (void*)node->child[1] << "\n";
}

std::ostream& operator<<(std::ostream& os, const struct bst_root* root) {
  return os << "Root (" << (void*)root << ")\n";
}

/**
 * bst_subtree_depth - Internal helper function
 * @node:   Root of a subtree.
 *
 * Return: Depth of subtree at @node, or 0 if @node is %NULL.
 */
static size_t bst_subtree_depth(struct bst_node *node) {
    if (!node) {
        return 0;
    } else {
        size_t child_depth[2];
        for (size_t i = 0; i < 2; i++) {
            child_depth[i] = bst_subtree_depth(node->child[i]);
        }
        return 1 + MAX(child_depth[0], child_depth[1]);
    }
}

/**
 * bst_depth - Debug function - return depth of tree
 * @root:   Tree.
 *
 * Return: Depth of @root.
 */
static size_t bst_depth(struct bst_root *root) {
    return bst_subtree_depth(root->root);
}

static bool bst_is_right_child(struct bst_node *node) {
    DEBUG_ASSERT(node);
    DEBUG_ASSERT(!node->parent || node->parent->child[0] == node ||
                 node->parent->child[1] == node);
    return node->parent && node->parent->child[1] == node;
}

static void bst_test_check_node(struct bst_root *root, struct bst_node *node) {
    if (node->parent) {
        ASSERT_NE(root->root, node) << node << root;
        ASSERT_EQ(node->parent->child[bst_is_right_child(node)], node) << node << root;
        ASSERT_GE(node->parent->rank, node->rank + 1) << node << root;
        ASSERT_LE(node->parent->rank, node->rank + 2) << node << root;
    } else {
        ASSERT_EQ(root->root, node) << node << root;;
    }
    if (!node->child[0] && !node->child[1]) {
        ASSERT_EQ(node->rank, 1U) << node << root;;
    }
}

static void print_rep(char ch, size_t count) {
    for (size_t i = 0; i < count; i++) {
        printf("%c", ch);
    }
}

static size_t bst_test_node_num(struct bst_node *node,
                                struct bst_node nodes[]) {
    return nodes ? node - nodes : 0;
}

static void bst_test_print_node_at(struct bst_root *root,
                                   struct bst_node nodes[], size_t row,
                                   size_t col, size_t depth) {
    size_t space = (1 << (depth - row)) - 2;
    struct bst_node *node = root->root;
    for (size_t mask = 1 << row >> 1; node && mask; mask >>= 1) {
        node = node->child[!!(col & mask)];
    }
    if (col) {
        printf("    ");
    }
    print_rep(' ', space);
    print_rep(node && node->child[0] ? '_' : ' ', space);
    if (node) {
        printf("%02zur%01zu", bst_test_node_num(node, nodes), node->rank);
    } else {
        printf("    ");
    }
    print_rep(node && node->child[1] ? '_' : ' ', space);
    print_rep(' ', space);
    /*
     *               ______________0004______________
     *       ______0003______                ______0003______
     *   __0002__        __0002__        __0002__        __0002__
     * 0001    0001    0001    0001    0001    0001    0001    0001
     */
}

static void bst_test_print_tree(struct bst_root *root, struct bst_node nodes[]) {
    size_t depth = bst_depth(root);
    printf("Tree depth %zu\n", depth);
    for (size_t row = 0; row < depth; row++) {
        for (size_t col = 0; col < 1 << row; col++) {
            bst_test_print_node_at(root, nodes, row, col, depth);
        }
        printf("\n");
    }
}

static void bst_test_check_tree_valid(struct bst_root *root) {
    struct bst_node *node = 0;
    while (true) {
        node = bst_next(root, node);
        if (!node) {
            break;
        }
        bst_test_check_node(root, node);
    }
    if(::testing::Test::HasFailure()) {
        bst_test_print_tree(root, NULL);
    }
}

static void bst_test_check_sub_tree(struct bst_node *subtree,
                                    struct bst_node nodes[],
                                    struct bst_node *parent,
                                    const char **expected_tree,
                                    int is_right_child);

static void bst_test_check_child(struct bst_node *subtree,
                                 struct bst_node nodes[],
                                 const char **expected_tree,
                                 int child) {
    if (BstTest::GetParam()) {
        child = !child;
    }
    while (isspace(**expected_tree)) {
        (*expected_tree)++;
    }

    if (**expected_tree == '(') {
        (*expected_tree)++;
        bst_test_check_sub_tree(subtree->child[child], nodes, subtree,
                                expected_tree, child);
        if (::testing::Test::HasFatalFailure()) {
            return;
        }
        ASSERT_EQ(**expected_tree, ')');
        (*expected_tree)++;
    } else {
        EXPECT_EQ(subtree->child[child], nullptr);
    }

    while (isspace(**expected_tree)) {
        (*expected_tree)++;
    }
}

static void bst_test_check_sub_tree(struct bst_node *subtree,
                                    struct bst_node nodes[],
                                    struct bst_node *parent,
                                    const char **expected_tree,
                                    int is_right_child) {
    size_t index;
    size_t rank;

    if (!parent && **expected_tree == '\0') {
        return;
    }
    ASSERT_NE(subtree, nullptr);

    bst_test_check_child(subtree, nodes, expected_tree, 0);
    if (::testing::Test::HasFatalFailure()) {
        return;
    }

    index = strtoul(*expected_tree, (char **)expected_tree, 0);
    ASSERT_EQ(**expected_tree, 'r');
    (*expected_tree)++;
    rank = strtoul(*expected_tree, (char **)expected_tree, 0);

    EXPECT_EQ(subtree, &nodes[index]);
    EXPECT_EQ(subtree->rank, rank);
    EXPECT_EQ(subtree->parent, parent);
    EXPECT_EQ(bst_is_right_child(subtree), is_right_child);

    bst_test_check_child(subtree, nodes, expected_tree, 1);
    if (::testing::Test::HasFatalFailure()) {
        return;
    }
}

/*
 * Check if tree has expected structure and ranks. The expected tree is
 * described by a string, "(left child) <node index>r<rank> (right child)". For
 * example: "((0r1) 1r2 (2r1)) 3r3 ((4r1) 5r2)".
 */
static void _bst_test_check_tree(struct bst_root *root, struct bst_node nodes[],
                                 const char *expected_tree) {
    bst_test_check_sub_tree(root->root, nodes, NULL, &expected_tree, 0);
    EXPECT_EQ(*expected_tree, '\0');
    if(::testing::Test::HasFailure()) {
        bst_test_print_tree(root, nodes);
    }
}

static void bst_test_check_array(struct bst_root *root,
                                     struct bst_node nodes[],
                                     size_t index[],
                                     size_t count,
                                     struct bst_node *left,
                                     struct bst_node *right) {
    for (size_t i = 0; i < count; i++) {
        struct bst_node *node = bst_node(nodes, index, i, count);
        EXPECT_EQ(bst_test_search(root, node), node) << "Node: " << (node - nodes) << "\n";
    }

    if (!count) {
        EXPECT_EQ(bst_next(root, left), right);
        EXPECT_EQ(bst_prev(root, right), left);
        return;
    }

    EXPECT_EQ(bst_next(root, left), bst_node(nodes, index, 0, count));
    EXPECT_EQ(bst_prev(root, bst_node(nodes, index, 0, count)), left);
    for (size_t i = 0; i < count - 1; i++) {
        struct bst_node *node = bst_node(nodes, index, i, count);
        struct bst_node *next = bst_node(nodes, index, i + 1, count);
        EXPECT_EQ(bst_next(root, node), next) << "node: " << (node - nodes) << ", next: " << (next - nodes);
        EXPECT_EQ(bst_prev(root, next), node) << "next: " << (next - nodes) << ", node: " << (node - nodes);
    }
    EXPECT_EQ(bst_next(root, bst_node(nodes, index, count - 1, count)), right);
    EXPECT_EQ(bst_prev(root, right), bst_node(nodes, index, count - 1, count));}

#define bst_countof(a) (sizeof(a) / sizeof((a))[0])

#define bst_test_check(root, nodes, items...) do { \
    SCOPED_TRACE("bst_test_check"); \
    size_t index[] = {items}; \
    bst_test_check_array(root, nodes, index, bst_countof(index), NULL, NULL); \
    if (HasFatalFailure()) return; \
} while(0)

#define bst_test_check_tree(root, node, treestr) do { \
    SCOPED_TRACE("bst_test_check_tree"); \
    _bst_test_check_tree(root, nodes, treestr); \
    if (HasFatalFailure()) return; \
} while(0)

static void bst_test_insert_func(struct bst_root *root, struct bst_node *node) {
    ASSERT_EQ(node->rank, 0U);
    bst_insert(root, node, bst_test_compare);
    bst_test_check_tree_valid(root);
    EXPECT_EQ(bst_test_search(root, node), node);
}

#define bst_test_insert(root, node) do { \
    SCOPED_TRACE(testing::Message() << "bst_insert" << node); \
    bst_test_insert_func(root, node); \
    if (HasFatalFailure()) return; \
} while(0)

#define bst_test_insert_check(root, nodes, insert, items...) do { \
    bst_test_insert(root, &nodes[insert]); \
    bst_test_check(root, nodes, items); \
} while(0)

#define bst_test_delete_check(root, nodes, insert, items...) do { \
    bst_test_delete(root, &nodes[insert]); \
    bst_test_check(root, nodes, items); \
} while(0)

#define bst_test_insert_check_tree(root, nodes, insert, treestr) do { \
    bst_test_insert(root, &nodes[insert]); \
    bst_test_check_tree(root, nodes, treestr); \
} while(0)

#define bst_test_delete_check_tree(root, nodes, insert, treestr) do { \
    bst_test_delete(root, &nodes[insert]); \
    bst_test_check_tree(root, nodes, treestr); \
} while(0)

static void bst_test_delete_func(struct bst_root *root, struct bst_node *node) {
    bst_delete(root, node);
    bst_test_check_tree_valid(root);
    EXPECT_EQ(bst_test_search(root, node), nullptr);
}

#define bst_test_delete(root, node) do { \
    SCOPED_TRACE(testing::Message() << "bst_delete" << node); \
    bst_test_delete_func(root, node); \
    if (HasFatalFailure()) return; \
} while(0)

/*
 * Test init api
 */
TEST(BstTest, InitRootValue) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    EXPECT_EQ(root.root, nullptr);
}

TEST(BstTest, InitRootFunction) {
    struct bst_root root;
    memset(&root, 0xff, sizeof(root));
    bst_root_initialize(&root);
    EXPECT_EQ(root.root, nullptr);
}

TEST(BstTest, InitNodeValue) {
    struct bst_node node = BST_NODE_INITIAL_VALUE;
    EXPECT_EQ(node.rank, 0U);
}

TEST(BstTest, InitNodeFnction) {
    struct bst_node node;
    memset(&node, 0xff, sizeof(node));
    bst_node_initialize(&node);
    EXPECT_EQ(node.rank, 0U);
}

/*
 * Simple tests to check that api return expected results.
 */
TEST_P(BstTest, InsertAscending) {
    /* Insert nodes in ascending order (or decending for mirrored test) */
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 14] = BST_NODE_INITIAL_VALUE};

    bst_test_check(&root, nodes);
    for (size_t i = 0; i < countof(nodes); i++) {
        bst_test_insert(&root, &nodes[i]);
        if (GetParam()) {
            EXPECT_EQ(bst_prev(&root, &nodes[i]), nullptr);
        } else {
            EXPECT_EQ(bst_next(&root, &nodes[i]), nullptr);
        }
    }
    bst_test_check_array(&root, nodes, NULL, countof(nodes), NULL, NULL);
    EXPECT_GE(bst_depth(&root), 4U); /* Minimum depth for a binary tree */
    EXPECT_LT(bst_depth(&root), 15U); /* We should have a tree, not a list */
    EXPECT_LE(bst_depth(&root), 8U); /* RB tree should have depth <= 8 */
    EXPECT_LE(bst_depth(&root), 6U); /* WAVL tree should have depth <= 6 */
}

TEST_P(BstTest, InsertBalanced) {
    /*
     *         ______7_____
     *        /            \
     *     __3__           _11_
     *    /     \         /    \
     *   1       5       9      13
     *  / \     / \     / \    /  \
     * 0   2   4   6   8  10  12  14
     */
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 14] = BST_NODE_INITIAL_VALUE};
    size_t index[] = { 7, 3, 11, 1, 5, 9, 13, 0, 2, 4, 6, 8, 10, 12, 14 };
    for (size_t i = 0; i < countof(index); i++) { \
        bst_test_insert(&root, &nodes[index[i]]);
    }
    bst_test_check_array(&root, nodes, NULL, countof(nodes), NULL, NULL);
    EXPECT_EQ(bst_depth(&root), 4U);
}

TEST_P(BstTest, DeleteOnlyEntry) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 0] = BST_NODE_INITIAL_VALUE};
    bst_test_insert_check(&root, nodes, 0, 0);
    /*
     * 0
     */
    bst_test_delete_check(&root, nodes, 0);
}

TEST_P(BstTest, DeleteRootOneChild) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 1] = BST_NODE_INITIAL_VALUE};
    bst_test_insert_check(&root, nodes, 1, 1);
    bst_test_insert_check(&root, nodes, 0, 0, 1);
    /*
     *   1
     *  /
     * 0
     */
    bst_test_delete_check(&root, nodes, 1, 0);
}

TEST_P(BstTest, DeleteRootTwoChildren) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 2] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check(&root, nodes, 1, 1);
    bst_test_insert_check(&root, nodes, 0, 0, 1);
    bst_test_insert_check(&root, nodes, 2, 0, 1, 2);
    /*
     *   1
     *  / \
     * 0   2
     */
    bst_test_delete_check(&root, nodes, 1, 0, 2);
}

TEST_P(BstTest, DeleteRootManyChildrenOneSide) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 4] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check(&root, nodes, 3, 3);
    bst_test_insert_check(&root, nodes, 1, 1, 3);
    bst_test_insert_check(&root, nodes, 4, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 0, 0, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 2, 0, 1, 2, 3, 4);
    /*
     *     __3__
     *    /     \
     *   1       4
     *  / \
     * 0   2
     */
    bst_test_delete_check(&root, nodes, 3, 0, 1, 2, 4);
}

TEST_P(BstTest, DeleteRootManyChildrenBothSides) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 6] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check(&root, nodes, 3, 3);
    bst_test_insert_check(&root, nodes, 1, 1, 3);
    bst_test_insert_check(&root, nodes, 5, 1, 3, 5);
    bst_test_insert_check(&root, nodes, 0, 0, 1, 3, 5);
    bst_test_insert_check(&root, nodes, 2, 0, 1, 2, 3, 5);
    bst_test_insert_check(&root, nodes, 4, 0, 1, 2, 3, 4, 5);
    bst_test_insert_check(&root, nodes, 6, 0, 1, 2, 3, 4, 5, 6);
    /*
     *     __3__
     *    /     \
     *   1       5
     *  / \     / \
     * 0   2   4   6
     */
    bst_test_delete_check(&root, nodes, 3, 0, 1, 2, 4, 5, 6);
}

TEST_P(BstTest, DeleteEdge1) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 4] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check(&root, nodes, 3, 3);
    bst_test_insert_check(&root, nodes, 1, 1, 3);
    bst_test_insert_check(&root, nodes, 4, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 0, 0, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 2, 0, 1, 2, 3, 4);
    /*
     *     __3__
     *    /     \
     *   1       4
     *  / \
     * 0   2
     */

    bst_test_delete_check(&root, nodes, 4, 0, 1, 2, 3);
}

TEST_P(BstTest, DeleteInternal) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 4] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check(&root, nodes, 3, 3);
    bst_test_insert_check(&root, nodes, 1, 1, 3);
    bst_test_insert_check(&root, nodes, 4, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 0, 0, 1, 3, 4);
    bst_test_insert_check(&root, nodes, 2, 0, 1, 2, 3, 4);
    /*
     *     __3__
     *    /     \
     *   1       4
     *  / \
     * 0   2
     */
    bst_test_delete_check(&root, nodes, 1, 0, 2, 3, 4);
}

TEST_P(BstTest, ForEveryEntry) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 254] = BST_NODE_INITIAL_VALUE};
    struct bst_test_entry *entry;

    for (size_t i = 0; i < countof(nodes); i++) {
        bst_test_insert(&root, &nodes[i]);
    }

    size_t i = 0;
    bst_for_every_entry(&root, entry, struct bst_test_entry, node) {
        EXPECT_EQ(&entry->node, bst_node(nodes, NULL, i, countof(nodes)));
        i++;
    }
    EXPECT_EQ(i, countof(nodes));
}

TEST_P(BstTest, ForEveryEntryExplicitDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 254] = BST_NODE_INITIAL_VALUE};
    struct bst_test_entry *entry;

    for (size_t i = 0; i < countof(nodes); i++) {
        bst_test_insert(&root, &nodes[i]);
    }

    size_t i = 0;
    bst_for_every_entry(&root, entry, struct bst_test_entry, node) {
        EXPECT_EQ(&entry->node, bst_node(nodes, NULL, i, countof(nodes)));
        i++;
        bst_delete(&root, &entry->node);
    }
    EXPECT_EQ(i, countof(nodes));

    EXPECT_EQ(bst_next(&root, NULL), nullptr);
}

TEST_P(BstTest, ForEveryEntryDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 254] = BST_NODE_INITIAL_VALUE};
    struct bst_test_entry *entry;

    for (size_t i = 0; i < countof(nodes); i++) {
        bst_test_insert(&root, &nodes[i]);
    }

    size_t i = 0;
    bst_for_every_entry_delete(&root, entry, struct bst_test_entry, node) {
        EXPECT_EQ(&entry->node, bst_node(nodes, NULL, i, countof(nodes)));
        i++;
    }
    EXPECT_EQ(i, countof(nodes));

    EXPECT_EQ(bst_next(&root, NULL), nullptr);
}

/*
 * WAVL specific tests.
 * Name describe non-mirrored test (/0).
 * Swap left/right for mirrrored test run (/1).
 */
TEST_P(BstTest, WAVLPromoteRootInsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 1] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 1, "1r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2");
}

TEST_P(BstTest, WAVLPromote2InsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 3] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");
    bst_test_insert_check_tree(&root, nodes, 3, "(1r1) 2r2 (3r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 (3r1)");
}

TEST_P(BstTest, WAVLRootRotateRightNoChildNodesInsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 2] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");

    /*
     *       2r2*         1r2
     *      /            /   \
     *    1r2      =>  0r1   2r1
     *   /
     * 0r1
     */
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2 (2r1)");
}

TEST_P(BstTest, WAVLRootRotateRightNoChildNodesInsertRight) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 2] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 2r2");

    /*
     *       ___2r2*         1r2
     *      /               /   \
     *    0r2         =>  0r1   2r1
     *       \
     *       1r1
     */
    bst_test_insert_check_tree(&root, nodes, 1, "(0r1) 1r2 (2r1)");
}


TEST_P(BstTest, WAVLRootRotateRightWithChildNodesInsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 5] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 4, "4r1");
    bst_test_insert_check_tree(&root, nodes, 2, "(2r1) 4r2");
    bst_test_insert_check_tree(&root, nodes, 5, "(2r1) 4r2 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 1, "((1r1) 2r2) 4r3 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "((1r1) 2r2 (3r1)) 4r3 (5r1)");

    /*
     *          ___4r3*              2r3___
     *         /      \             /      \
     *       2r3      5r1  =>     1r2      4r2
     *      /   \                /        /   \
     *    1r2   3r1            0r1      3r1   5r1
     *   /
     * 0r1
     */
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 ((3r1) 4r2 (5r1))");
}

TEST_P(BstTest, WAVLNonRootRotateRightWithChildNodesInsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 8] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 6, "6r1");
    bst_test_insert_check_tree(&root, nodes, 4, "(4r1) 6r2");
    bst_test_insert_check_tree(&root, nodes, 7, "(4r1) 6r2 (7r1)");
    bst_test_insert_check_tree(&root, nodes, 2, "((2r1) 4r2) 6r3 (7r1)");
    bst_test_insert_check_tree(&root, nodes, 8, "((2r1) 4r2) 6r3 (7r2 (8r1))");
    bst_test_insert_check_tree(&root, nodes, 5, "((2r1) 4r2 (5r1)) 6r3 (7r2 (8r1))");
    bst_test_insert_check_tree(&root, nodes, 1, "(((1r1) 2r2) 4r3 (5r1)) 6r4 (7r2 (8r1))");
    bst_test_insert_check_tree(&root, nodes, 3, "(((1r1) 2r2 (3r1)) 4r3 (5r1)) 6r4 (7r2 (8r1))");

    /*
     *                ___6r4...             _________6r4...
     *               /                     /
     *          ___4r3*                  2r3___
     *         /      \                 /      \
     *       2r3      5r1      =>     1r2      4r1
     *      /   \                    /        /   \
     *    1r2   3r1                0r1      3r1   5r1
     *   /
     * 0r1
     */
    bst_test_insert_check_tree(&root, nodes, 0, "(((0r1) 1r2) 2r3 ((3r1) 4r2 (5r1))) 6r4 (7r2 (8r1))");
}

TEST_P(BstTest, WAVLRotateLeftRightSimpleInsertRight) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 5] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 4, "4r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 4r2");
    bst_test_insert_check_tree(&root, nodes, 5, "(1r1) 4r2 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 4r3 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 2, "((0r1) 1r2 (2r1)) 4r3 (5r1)");

    /*
     *       ______4r3                 ___4r3               2r3___
     *      /         \               /      \             /      \
     *    1r3         5r1           2r3      5r1         1r2      4r2
     *   /   \             =>      /   \           =>   /        /   \
     * 0r1   2r2                 1r2   3r1            0r1      3r1   5r1
     *          \               /
     *          3r1           0r1
     */
    bst_test_insert_check_tree(&root, nodes, 3, "((0r1) 1r2) 2r3 ((3r1) 4r2 (5r1))");
}

TEST_P(BstTest, WAVLRotateRightLeftSimpleInsertLeft) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 5] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 1, "1r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2");
    bst_test_insert_check_tree(&root, nodes, 4, "(0r1) 1r2 (4r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "(0r1) 1r3 ((3r1) 4r2)");
    bst_test_insert_check_tree(&root, nodes, 5, "(0r1) 1r3 ((3r1) 4r2 (5r1))");

    /*
     *    1r3______               1r3___                     ___3r3
     *   /         \             /      \                   /      \
     * 0r1         4r3         0r1      3r3               1r2      4r2
     *            /   \    =>          /   \       =>    /   \        \
     *          3r2   5r1            2r1   4r2         0r1   2r1      5r1
     *         /                              \
     *       2r1                              5r1
     */
    bst_test_insert_check_tree(&root, nodes, 2, "((0r1) 1r2 (2r1)) 3r3 (4r2 (5r1))");
}

TEST_P(BstTest, WAVLDemoteLeaf) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 1] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 1, "1r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2");
    /*
     *    1r2      1r2*      1r1
     *   /     =>        =>
     * 0r1
     */
    bst_test_delete_check_tree(&root, nodes, 0, "1r1");
}

TEST_P(BstTest, WAVLDemoteNonLeaf) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 3] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 1, "1r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2");
    bst_test_insert_check_tree(&root, nodes, 2, "(0r1) 1r2 (2r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "(0r1) 1r3 (2r2 (3r1))");
    bst_test_delete_check_tree(&root, nodes, 3, "(0r1) 1r3 (2r1)");
    /*
     *    1r3         1r3         1r2
     *   /   \    =>     \    =>     \
     * 0r1   2r1         2r1         2r1
     */
    bst_test_delete_check_tree(&root, nodes, 0, "1r2 (2r1)");
}

TEST_P(BstTest, WAVLDoubleDemote) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 6] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");
    bst_test_insert_check_tree(&root, nodes, 4, "(1r1) 2r2 (4r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 (4r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "((0r1) 1r2) 2r3 ((3r1) 4r2)");
    bst_test_insert_check_tree(&root, nodes, 5, "((0r1) 1r2) 2r3 ((3r1) 4r2 (5r1))");
    bst_test_insert_check_tree(&root, nodes, 6, "((0r1) 1r2) 2r4 ((3r1) 4r3 (5r2 (6r1)))");
    bst_test_delete_check_tree(&root, nodes, 6, "((0r1) 1r2) 2r4 ((3r1) 4r3 (5r1))");
    /*
     *       2r4___            2r4___               2r3___
     *      /      \    =>    /      \       =>    /      \
     *    1r2      4r3      1r1      4r3         1r1      4r2
     *   /        /   \             /   \                /   \
     * 0r1      3r1   5r1         3r1   5r1            3r1   5r1
     */
    bst_test_delete_check_tree(&root, nodes, 0, "(1r1) 2r3 ((3r1) 4r2 (5r1))");
}

TEST_P(BstTest, WAVLRotateNoChildrenAfterDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 3] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 1, "1r1");
    bst_test_insert_check_tree(&root, nodes, 0, "(0r1) 1r2");
    bst_test_insert_check_tree(&root, nodes, 2, "(0r1) 1r2 (2r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "(0r1) 1r3 (2r2 (3r1))");
    /*
     *    1r3            1r3               2r3
     *   /   \              \             /   \
     * 0r1   2r2     =>     2r2     =>  1r1   3r1
     *          \              \
     *          3r1            3r1
     *
     * (2r2 would also be a valid wavl tree after this rotate, but that does
     * not work in the general case where 2 could have started with a left
     * child)
     */
    bst_test_delete_check_tree(&root, nodes, 0, "(1r1) 2r3 (3r1)");
}

TEST_P(BstTest, WAVLRotateWithChildren1AfterDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 6] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");
    bst_test_insert_check_tree(&root, nodes, 4, "(1r1) 2r2 (4r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 (4r1)");
    bst_test_insert_check_tree(&root, nodes, 3, "((0r1) 1r2) 2r3 ((3r1) 4r2)");
    bst_test_insert_check_tree(&root, nodes, 5, "((0r1) 1r2) 2r3 ((3r1) 4r2 (5r1))");
    bst_test_insert_check_tree(&root, nodes, 6, "((0r1) 1r2) 2r4 ((3r1) 4r3 (5r2 (6r1)))");
    /*
     *       2r4___                 2r4___                     ___4r4
     *      /      \               /      \                   /      \
     *    1r2      4r3           1r1      4r3               2r2      5r2
     *   /        /   \      =>          /   \       =>    /   \        \
     * 0r1      3r1   5r2              3r1   5r2         1r1   3r1      6r1
     *                   \                      \
     *                   6r1                    6r1
     *
     * (4r3/2r2 would also be a valid wavl tree after this rotate, but that does
     * not work in the general case where 3r1 could be 3r2. 2r3 would also be
     * valid, but since we have to demote it when it is a leaf node, the current
     * implementation demotes it when possible)
     */
    bst_test_delete_check_tree(&root, nodes, 0, "((1r1) 2r2 (3r1)) 4r4 (5r2 (6r1))");
}

TEST_P(BstTest, WAVLRotateWithChildren2AfterDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 7] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");
    bst_test_insert_check_tree(&root, nodes, 5, "(1r1) 2r2 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 (5r1)");
    bst_test_insert_check_tree(&root, nodes, 4, "((0r1) 1r2) 2r3 ((4r1) 5r2)");
    bst_test_insert_check_tree(&root, nodes, 6, "((0r1) 1r2) 2r3 ((4r1) 5r2 (6r1))");
    bst_test_insert_check_tree(&root, nodes, 3, "((0r1) 1r2) 2r4 (((3r1) 4r2) 5r3 (6r1))");
    bst_test_insert_check_tree(&root, nodes, 7, "((0r1) 1r2) 2r4 (((3r1) 4r2) 5r3 (6r2 (7r1)))");
    /*
     *       2r4_____               2r4______                   ______5r4
     *      /        \             /         \                 /         \
     *    1r2         5r3         1r1         5r3             2r3___      6r2
     *   /           /   \     =>            /   \      =>   /      \        \
     * 0r1         4r2   6r2               4r2   6r2       1r1      4r2      7r1
     *            /         \             /         \              /
     *          3r1         7r1         3r1         7r1          3r1
     */
    bst_test_delete_check_tree(&root, nodes, 0, "((1r1) 2r3 ((3r1) 4r2)) 5r4 (6r2 (7r1))");
}

TEST_P(BstTest, WAVLDoubleRotateWithChildren2AfterDelete) {
        /*
         * Swap nodes @up2 and @up1 then @up2 and @down
         * (pictured for up_was_right_child==false):
         *
         *         down(0)              down            up2(0)
         *        /       \            /    \          /    \
         *       up1(-1)   D(-3)     up2     D      up1(-2)  down(-2)
         *      /       \            /   \          /  \     /   \
         *     A(-3)    up2(-2)    up1    C      A(-3)  B   C     D(-3)
         *              /   \     /   \
         *             B     C   A(-3) B
         */
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 7] = BST_NODE_INITIAL_VALUE};

    bst_test_insert_check_tree(&root, nodes, 2, "2r1");
    bst_test_insert_check_tree(&root, nodes, 1, "(1r1) 2r2");
    bst_test_insert_check_tree(&root, nodes, 6, "(1r1) 2r2 (6r1)");
    bst_test_insert_check_tree(&root, nodes, 0, "((0r1) 1r2) 2r3 (6r1)");
    bst_test_insert_check_tree(&root, nodes, 4, "((0r1) 1r2) 2r3 ((4r1) 6r2)");
    bst_test_insert_check_tree(&root, nodes, 7, "((0r1) 1r2) 2r3 ((4r1) 6r2 (7r1))");
    bst_test_insert_check_tree(&root, nodes, 3, "((0r1) 1r2) 2r4 (((3r1) 4r2) 6r3 (7r1))");
    bst_test_insert_check_tree(&root, nodes, 5, "((0r1) 1r2) 2r4 (((3r1) 4r2 (5r1)) 6r3 (7r1))");
    /*
     *    2r4_________             2r4___                     ___4r4___
     *   /            \           /      \                   /         \
     * 1r1         ___6r3       1r1      4r3___            2r2         6r2
     *            /      \   =>         /      \     =>   /   \       /   \
     *          4r2      7r1          3r1      6r2      1r1   3r1   5r1   7r1
     *         /   \                          /   \
     *       3r1   5r1                      5r1   7r1
     */
    bst_test_delete_check_tree(&root, nodes, 0, "((1r1) 2r2 (3r1)) 4r4 ((5r1) 6r2 (7r1))");
}

TEST_P(BstTest, RandomInsert) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 999] = BST_NODE_INITIAL_VALUE};
    for (size_t i = 0; i < countof(nodes);) {
        struct bst_node *node = &nodes[lrand48() % countof(nodes)];
        if (!node->rank) {
            bst_test_insert(&root, node);
            ASSERT_GE(node->rank, 1U);
            EXPECT_EQ(bst_test_search(&root, node), node);
            i++;
        }
    }
}

TEST_P(BstTest, RandomInsertRandomDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 999] = BST_NODE_INITIAL_VALUE};
    for (size_t i = 0; i < countof(nodes);) {
        struct bst_node *node = &nodes[lrand48() % countof(nodes)];
        if (!node->rank) {
            bst_test_insert(&root, node);
            ASSERT_GE(node->rank, 1U);
            EXPECT_EQ(bst_test_search(&root, node), node);
            i++;
        }
    }
    for (size_t i = 0; i < countof(nodes);) {
        struct bst_node *node = &nodes[lrand48() % countof(nodes)];
        if (node->rank) {
            bst_test_delete(&root, node);
            EXPECT_EQ(node->rank, 0U);
            EXPECT_EQ(bst_test_search(&root, node), nullptr) << node;
            i++;
        }
    }
}

TEST_P(BstTest, RandomInsertDelete) {
    struct bst_root root = BST_ROOT_INITIAL_VALUE;
    struct bst_node nodes[] = {[0 ... 499] = BST_NODE_INITIAL_VALUE};
    for (size_t i = 0; i < countof(nodes) * 100; i++) {
        struct bst_node *node = &nodes[lrand48() % countof(nodes)];
        if (node->rank) {
            bst_test_delete(&root, node);
            ASSERT_EQ(node->rank, 0U);
            EXPECT_EQ(bst_test_search(&root, node), nullptr);
        } else {
            bst_test_insert(&root, node);
            EXPECT_GE(node->rank, 1U);
            EXPECT_EQ(bst_test_search(&root, node), node);
        }
    }
}
