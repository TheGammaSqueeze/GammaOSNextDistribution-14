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

#include <lk/list.h>

#include "gtest/gtest.h"

/**
 * CheckListArray - check that a list contains specific items.
 * @list:   The list to check.
 * @items:  Array with expected items.
 * @count:  Number if entries in @items, and expected number of entries in
 *          @list.
 *
 * Check that @list contains the all the entries in @items, in the same order,
 * and no other entries. Also check that head, tail, next and prev functions
 * return the expected entries in @items.
 */
static void CheckListArray(struct list_node *list, struct list_node *items[],
                           int count) {
    struct list_node *node;
    int i = 0;

    struct list_node *head_item = count ? items[0] : NULL;
    EXPECT_EQ(list_peek_head(list), head_item) << "List has wrong head";

    struct list_node *tail_item = count ? items[count - 1] : NULL;
    EXPECT_EQ(list_peek_tail(list), tail_item) << "List has wrong tail";

    list_for_every(list, node) {
        ASSERT_GT(count, i) << "List has more items than expected";
        EXPECT_EQ(node, items[i]) << "Wrong entry as index " << i;

        struct list_node *prev_item = i > 0 ? items[i - 1] : NULL;
        EXPECT_EQ(list_prev(list, node), prev_item) <<
            "List has wrong back pointer at index " << i;

        struct list_node *next_item = i < count - 1 ? items[i + 1] : NULL;
        EXPECT_EQ(list_next(list, node), next_item) <<
            "List has wrong next pointer at index " << i;
        i++;
    }
    EXPECT_EQ(i, count) << "List has fewer items than expected";
}

#define ArraySize(a) (sizeof(a) / sizeof((a)[0]))

/**
 * CheckList - Helper macro to call CheckListArray.
 */
#define CheckList(list, items...) { \
    SCOPED_TRACE("CheckList"); \
    struct list_node *itemarray[] = {items}; \
    CheckListArray(list, itemarray, ArraySize(itemarray)); \
}

/**
 * DefineEmptyList - Helper macro to define and initialize a list.
 */
#define DefineEmptyList(list) \
    struct list_node list = LIST_INITIAL_VALUE(list);

/**
 * DefineAndAddListItem - Helper macro to define and initialize a list entry and
 *                        add it to a list.
 */
#define DefineAndAddListItem(list, item) \
    struct list_node item = LIST_INITIAL_CLEARED_VALUE; \
    list_add_tail(&list, &item);

/**
 * DefineListWith1Item - Helper macro to define and initialize a list with 1
 *                       item.
 */
#define DefineListWith1Item(list, item1) \
    DefineEmptyList(list) \
    DefineAndAddListItem(list, item1) \
    CheckList(&list, &item1)

/**
 * DefineListWith2Items - Helper macro to define and initialize a list with 2
 *                        items.
 */
#define DefineListWith2Items(list, item1, item2) \
    DefineListWith1Item(list, item1) \
    DefineAndAddListItem(list, item2) \
    CheckList(&list, &item1, &item2)

/**
 * DefineListWith3Items - Helper macro to define and initialize a list with 3
 *                        items.
 */
#define DefineListWith3Items(list, item1, item2, item3) \
    DefineListWith2Items(list, item1, item2) \
    DefineAndAddListItem(list, item3) \
    CheckList(&list, &item1, &item2, &item3)

/* Smoke test 3 list init methods */
TEST(ListTest, ListInitialValue) {
    struct list_node list = LIST_INITIAL_VALUE(list);
    EXPECT_TRUE(list_in_list(&list));
    EXPECT_TRUE(list_is_empty(&list));
    CheckList(&list);
}

TEST(ListTest, ListInitialClearedValue) {
    struct list_node list = LIST_INITIAL_CLEARED_VALUE;
    EXPECT_FALSE(list_in_list(&list));
}

TEST(ListTest, ListInitialize) {
    struct list_node list;
    list_initialize(&list);
    EXPECT_TRUE(list_in_list(&list));
    EXPECT_TRUE(list_is_empty(&list));
}

/* Test 4 list add methods */
TEST(ListTest, ListAddHead) {
    struct list_node list = LIST_INITIAL_VALUE(list);
    struct list_node item1 = LIST_INITIAL_CLEARED_VALUE;
    struct list_node item2 = LIST_INITIAL_CLEARED_VALUE;

    list_add_head(&list, &item1);
    CheckList(&list, &item1);

    list_add_head(&list, &item2);
    CheckList(&list, &item2, &item1);
}

TEST(ListTest, ListAddAfterLast) {
    DefineListWith1Item(list, item1);
    struct list_node item2 = LIST_INITIAL_CLEARED_VALUE;

    list_add_after(&item1, &item2);
    CheckList(&list, &item1, &item2);
}

TEST(ListTest, ListAddAfterNotLast) {
    DefineListWith2Items(list, item1, item2);
    struct list_node item3 = LIST_INITIAL_CLEARED_VALUE;

    list_add_after(&item1, &item3);
    CheckList(&list, &item1, &item3, &item2);
}

TEST(ListTest, ListAddTail) {
    struct list_node list = LIST_INITIAL_VALUE(list);
    struct list_node item1 = LIST_INITIAL_CLEARED_VALUE;
    struct list_node item2 = LIST_INITIAL_CLEARED_VALUE;

    list_add_tail(&list, &item1);
    CheckList(&list, &item1);

    list_add_tail(&list, &item2);
    CheckList(&list, &item1, &item2);
}

TEST(ListTest, ListAddBeforeHead) {
    DefineListWith1Item(list, item1);
    struct list_node item2 = LIST_INITIAL_CLEARED_VALUE;

    list_add_before(&item1, &item2);
    CheckList(&list, &item2, &item1);
}

TEST(ListTest, ListAddBeforeNotHead) {
    DefineListWith2Items(list, item1, item2);
    struct list_node item3 = LIST_INITIAL_CLEARED_VALUE;

    list_add_before(&item2, &item3);
    CheckList(&list, &item1, &item3, &item2);
}

/* Test list delete 4 possible configurations of prev and next entries */
TEST(ListTest, ListDeleteOnly) {
    DefineListWith1Item(list, item);
    list_delete(&item);
    EXPECT_FALSE(list_in_list(&item));
    CheckList(&list);
}

TEST(ListTest, ListDeleteFirst) {
    DefineListWith2Items(list, item1, item2);
    list_delete(&item1);
    EXPECT_FALSE(list_in_list(&item1));
    CheckList(&list, &item2);
}

TEST(ListTest, ListDeleteLast) {
    DefineListWith2Items(list, item1, item2);
    list_delete(&item2);
    EXPECT_FALSE(list_in_list(&item2));
    CheckList(&list, &item1);
}

TEST(ListTest, ListDeleteMiddle) {
    DefineListWith3Items(list, item1, item2, item3);
    list_delete(&item2);
    EXPECT_FALSE(list_in_list(&item2));
    CheckList(&list, &item1, &item3);
}

/*
 * Test list_remove_head with 3 possible configurations of empty list, head is
 * last entry, and head is not the last entry.
 */
TEST(ListTest, ListRemoveHead) {
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_remove_head(&list);
    EXPECT_EQ(node, &item1);
    CheckList(&list, &item2);
    node = list_remove_head(&list);
    EXPECT_EQ(node, &item2);
    CheckList(&list);
    node = list_remove_head(&list);
    EXPECT_EQ(node, nullptr);
    CheckList(&list);
}

/*
 * Test list_remove_tail with 3 possible configurations of empty list, tail is
 * first entry, and tail is not the first entry.
 */
TEST(ListTest, ListRemoveTail) {
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_remove_tail(&list);
    EXPECT_EQ(node, &item2);
    CheckList(&list, &item1);
    node = list_remove_tail(&list);
    EXPECT_EQ(node, &item1);
    CheckList(&list);
    node = list_remove_tail(&list);
    EXPECT_EQ(node, nullptr);
    CheckList(&list);
}

/*
 * Test list_peek_head with and without entries in the list.
 * Use 2 entries to separate tail and head in non-empty test.
 */
TEST(ListTest, ListPeekHeadEmpty) {
    struct list_node list = LIST_INITIAL_VALUE(list);
    struct list_node *node = list_peek_head(&list);
    EXPECT_EQ(node, nullptr);
}

TEST(ListTest, ListPeekHeadNotEmpty) {
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_peek_head(&list);
    EXPECT_EQ(node, &item1);
}

/*
 * Test list_peek_tail with and without entries in the list.
 * Use 2 entries to separate tail and head in non-empty test.
 */
TEST(ListTest, ListPeekTailEmpty) {
    struct list_node list = LIST_INITIAL_VALUE(list);
    struct list_node *node = list_peek_tail(&list);
    EXPECT_EQ(node, nullptr);
}

TEST(ListTest, ListPeekTailNotEmpty) {
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_peek_tail(&list);
    EXPECT_EQ(node, &item2);
}

/* Test list navigation functions. */
TEST(ListTest, ListPrevHead) {
    /*
     * Calling list_prev on the head should return NULL.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_prev(&list, &item1);
    EXPECT_EQ(node, nullptr);
}

TEST(ListTest, ListPrevNotHead) {
    /*
     * Calling list_prev on entries other than the head should return the
     * previous entry.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_prev(&list, &item2);
    EXPECT_EQ(node, &item1);
}

TEST(ListTest, ListPrevWrapHead) {
    /*
     * Calling list_prev_wrap on the head should return the tail.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_prev_wrap(&list, &item1);
    EXPECT_EQ(node, &item2);
}

TEST(ListTest, ListPrevWrapNotHead) {
    /*
     * Calling list_prev_wrap on entries other than the head should return the
     * previous entry (same as list_prev).
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_prev_wrap(&list, &item2);
    EXPECT_EQ(node, &item1);
}

TEST(ListTest, ListPrevWrapEmpty) {
    /*
     * Calling list_prev_wrap on the list itself, instead of an entry does not
     * appear to be a useful operation, but the implemention allows it and
     * returns NULL in that case.
     */
    DefineEmptyList(list);
    struct list_node *node = list_prev_wrap(&list, &list);
    EXPECT_EQ(node, nullptr);
}

TEST(ListTest, ListNextHead) {
    /*
     * Calling list_next on the tail should return NULL.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_next(&list, &item2);
    EXPECT_EQ(node, nullptr);
}

TEST(ListTest, ListNextNotTail) {
    /*
     * Calling list_next on entries other than the tail should return the next
     * entry.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_next(&list, &item1);
    EXPECT_EQ(node, &item2);
}

TEST(ListTest, ListNextWrapTail) {
    /*
     * Calling list_next_wrap on the tail should return the head.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_next_wrap(&list, &item2);
    EXPECT_EQ(node, &item1);
}

TEST(ListTest, ListNextWrapNotTail) {
    /*
     * Calling list_next_wrap on entries other than the tail should return the
     * next entry (same as list_next).
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *node = list_next_wrap(&list, &item1);
    EXPECT_EQ(node, &item2);
}

TEST(ListTest, ListNextWrapEmpty) {
    /*
     * Calling list_next_wrap on the list itself, instead of an entry does not
     * appear to be a useful operation, but the implemention allows it and
     * returns NULL in that case.
     */
    DefineEmptyList(list);
    struct list_node *node = list_next_wrap(&list, &list);
    EXPECT_EQ(node, nullptr);
}

/* Test list iterators */
TEST(ListTest, ListForEvery) {
    /*
     * list_for_every should return every entry one by one.
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *itemarray[] = {&item1, &item2};
    size_t i = 0;
    struct list_node *node;
    list_for_every(&list, node) {
        ASSERT_LT(i, countof(itemarray));
        EXPECT_EQ(node, itemarray[i]);
        i++;
    }
    EXPECT_EQ(i, countof(itemarray));
}

TEST(ListTest, ListForEverySafe) {
    /*
     * list_for_every_safe should return every entry one by one even if the
     * current entry is removed (not tested here).
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *itemarray[] = {&item1, &item2};
    size_t i = 0;
    struct list_node *node;
    struct list_node *tmp_node;
    list_for_every_safe(&list, node, tmp_node) {
        ASSERT_LT(i, countof(itemarray));
        EXPECT_EQ(node, itemarray[i]);
        i++;
    }
    EXPECT_EQ(i, countof(itemarray));
}

TEST(ListTest, ListForEverySafeDeleteAll) {
    /*
     * list_for_every_safe should return every entry one by one even if the
     * current entry is removed (tested here).
     */
    DefineListWith2Items(list, item1, item2);
    struct list_node *itemarray[] = {&item1, &item2};
    size_t i = 0;
    struct list_node *node;
    struct list_node *tmp_node;
    list_for_every_safe(&list, node, tmp_node) {
        ASSERT_LT(i, countof(itemarray));
        EXPECT_EQ(node, itemarray[i]);
        list_delete(node);
        i++;
    }
    EXPECT_EQ(i, countof(itemarray));
    CheckList(&list);
}

TEST(ListTest, ListForEverySafeDeleteSome) {
    /*
     * list_for_every_safe should return every entry one by one even if the
     * current entry is removed (tested here).
     */
    DefineListWith3Items(list, item1, item2, item3);
    struct list_node *itemarray[] = {&item1, &item2, &item3};
    size_t i = 0;
    struct list_node *node;
    struct list_node *tmp_node;
    list_for_every_safe(&list, node, tmp_node) {
        ASSERT_LT(i, countof(itemarray));
        EXPECT_EQ(node, itemarray[i]);
        if (i != 1) {
            list_delete(node);
        }
        i++;
    }
    EXPECT_EQ(i, countof(itemarray));
    CheckList(&list, &item2);
}

/* Test list empty and count functions */
TEST(ListTest, ListIsEmptyEmpty) {
    DefineEmptyList(list);
    EXPECT_TRUE(list_is_empty(&list));
}

TEST(ListTest, ListIsEmptyNotEmpty) {
    DefineListWith1Item(list, item);
    EXPECT_FALSE(list_is_empty(&list));
}

TEST(ListTest, ListLengthEmpty) {
    DefineEmptyList(list);
    EXPECT_EQ(list_length(&list), 0U);
}

TEST(ListTest, ListLength1) {
    DefineListWith1Item(list, item);
    EXPECT_EQ(list_length(&list), 1U);
}

TEST(ListTest, ListLength2) {
    DefineListWith2Items(list, item1, item2);
    EXPECT_EQ(list_length(&list), 2U);
}

/* Test list splice operations */
TEST(ListTest, ListSpliceTailBothEmpty) {
    DefineEmptyList(list1);
    DefineEmptyList(list2);
    list_splice_tail(&list1, &list2);
    CheckList(&list1);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceTailSrcEmpty) {
    DefineListWith2Items(list1, item1, item2);
    DefineEmptyList(list2);
    list_splice_tail(&list1, &list2);
    CheckList(&list1, &item1, &item2);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceTailSrc1Item) {
    DefineListWith2Items(list1, item1, item2);
    DefineListWith1Item(list2, item3);
    list_splice_tail(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceTailDestEmpty) {
    DefineEmptyList(list1);
    DefineListWith2Items(list2, item1, item2);
    list_splice_tail(&list1, &list2);
    CheckList(&list1, &item1, &item2);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceTailDest1Item) {
    DefineListWith1Item(list1, item1);
    DefineListWith2Items(list2, item2, item3);
    list_splice_tail(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceTail) {
    DefineListWith2Items(list1, item1, item2);
    DefineListWith2Items(list2, item3, item4);
    list_splice_tail(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3, &item4);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHeadBothEmpty) {
    DefineEmptyList(list1);
    DefineEmptyList(list2);
    list_splice_head(&list1, &list2);
    CheckList(&list1);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHeadSrcEmpty) {
    DefineListWith2Items(list1, item1, item2);
    DefineEmptyList(list2);
    list_splice_head(&list1, &list2);
    CheckList(&list1, &item1, &item2);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHeadSrc1Item) {
    DefineListWith2Items(list1, item2, item3);
    DefineListWith1Item(list2, item1);
    list_splice_head(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHeadDestEmpty) {
    DefineEmptyList(list1);
    DefineListWith2Items(list2, item1, item2);
    list_splice_head(&list1, &list2);
    CheckList(&list1, &item1, &item2);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHeadDest1Item) {
    DefineListWith1Item(list1, item3);
    DefineListWith2Items(list2, item1, item2);
    list_splice_head(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceHead) {
    DefineListWith2Items(list1, item3, item4);
    DefineListWith2Items(list2, item1, item2);
    list_splice_head(&list1, &list2);
    CheckList(&list1, &item1, &item2, &item3, &item4);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceAfter) {
    DefineListWith2Items(list1, item1, item4);
    DefineListWith2Items(list2, item2, item3);
    list_splice_after(&item1, &list2);
    CheckList(&list1, &item1, &item2, &item3, &item4);
    CheckList(&list2);
}

TEST(ListTest, ListSpliceBefore) {
    DefineListWith2Items(list1, item1, item4);
    DefineListWith2Items(list2, item2, item3);
    list_splice_before(&item4, &list2);
    CheckList(&list1, &item1, &item2, &item3, &item4);
    CheckList(&list2);
}
