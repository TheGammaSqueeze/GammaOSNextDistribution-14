/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define _GNU_SOURCE /* for asprintf */

#include <inttypes.h>
#include <libgen.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

#include <trusty_unittest.h>

#include "block_cache.h"
#include "block_device_tipc.h"
#include "crypt.h"
#include "error_reporting_mock.h"
#include "file.h"
#include "rpmb.h"
#include "storageproxy_shim.h"
#include "transaction.h"

/* For BLOCK_CACHE_SIZE */
#include "block_cache_priv.h"

static struct key storage_test_key;
static struct block_device_tipc test_block_device;

static bool print_test_verbose = false;

static inline void transaction_complete(struct transaction* tr) {
    return transaction_complete_etc(tr, false);
}

static inline void transaction_complete_update_checkpoint(
        struct transaction* tr) {
    return transaction_complete_etc(tr, true);
}

static void open_test_file_etc(struct transaction* tr,
                               struct file_handle* file,
                               const char* path,
                               enum file_create_mode create,
                               enum file_op_result expected_result,
                               bool allow_repaired) {
    enum file_op_result result;
    /* TODO: parameterize the allow_repaired argument if needed */
    result = file_open(tr, path, file, create, allow_repaired);
    if (print_test_verbose) {
        printf("%s: lookup file %s, create %d, got %" PRIu64 ":\n", __func__,
               path, create, block_mac_to_block(tr, &file->block_mac));
    }

    ASSERT_EQ(result, expected_result);
    ASSERT_EQ(true, result != FILE_OP_SUCCESS ||
                            block_mac_valid(tr, &file->block_mac));

test_abort:;
}

static void open_test_file(struct transaction* tr,
                           struct file_handle* file,
                           const char* path,
                           enum file_create_mode create) {
    open_test_file_etc(tr, file, path, create, FILE_OP_SUCCESS, false);
}

/* run tests on already open file */
static void file_test_open(struct transaction* tr,
                           struct file_handle* file,
                           int allocate,
                           int read,
                           int free,
                           int id) {
    int i;
    int* block_data_rw;
    struct obj_ref ref = OBJ_REF_INITIAL_VALUE(ref);
    const int* block_data_ro;
    size_t file_block_size = tr->fs->dev->block_size - sizeof(struct iv);

    if (allocate) {
        for (i = 0; i < allocate; i++) {
            block_data_rw = file_get_block_write(tr, file, i, true, &ref);
            if (!block_data_rw) {
                ASSERT_EQ(true, tr->failed);
                goto test_abort;
            }
            if (print_test_verbose) {
                printf("%s: allocate file block %d, %" PRIu64 ":\n", __func__,
                       i, data_to_block_num(block_data_rw));
            }
            /* TODO: store iv in file block map */
            block_data_rw = (void*)block_data_rw + sizeof(struct iv);
            // block_data_rw = block_get_cleared(block)+ sizeof(struct iv);
            block_data_rw[0] = i;
            block_data_rw[1] = ~i;
            block_data_rw[2] = id;
            block_data_rw[3] = ~id;
            file_block_put_dirty(tr, file, i,
                                 (void*)block_data_rw - sizeof(struct iv),
                                 &ref);
        }
        if (file->size < i * file_block_size) {
            file_set_size(tr, file, i * file_block_size);
        }
        ASSERT_GE(file->size, i * file_block_size);
        if (print_test_verbose) {
            printf("%s: allocated %d file blocks\n", __func__, i);
            file_print(tr, file);
        }
    }

    if (read) {
        for (i = 0;; i++) {
            block_data_ro = file_get_block(tr, file, i, &ref);
            if (!block_data_ro) {
                break;
            }
            if (print_test_verbose) {
                printf("%s: found file block %d, %" PRIu64 ":\n", __func__, i,
                       data_to_block_num(block_data_ro));
            }
            block_data_ro = (void*)block_data_ro + sizeof(struct iv);
            ASSERT_EQ(block_data_ro[0], i);
            ASSERT_EQ(block_data_ro[1], ~i);
            ASSERT_EQ(block_data_ro[2], id);
            ASSERT_EQ(block_data_ro[3], ~id);
            file_block_put((void*)block_data_ro - sizeof(struct iv), &ref);
        }
        ASSERT_EQ(i, read);
        ASSERT_GE(file->size, i * file_block_size);
    }

    if (free) {
        file_set_size(tr, file, 0);
        for (i = 0; i < free; i++) {
            block_data_ro = file_get_block(tr, file, i, &ref);
            if (block_data_ro) {
                file_block_put(block_data_ro, &ref);
                printf("%s: file block %d, %" PRIu64 " not deleted\n", __func__,
                       i, data_to_block_num(block_data_ro));
                break;
            }
        }
        if (print_test_verbose) {
            printf("%s: deleted %d file blocks\n", __func__, i);
            file_print(tr, file);
        }
        ASSERT_EQ(i, free);
    }

test_abort:;
}

static void file_test_commit(struct transaction* tr, bool commit) {
    if (commit) {
        transaction_complete(tr);

        ASSERT_EQ(false, tr->failed);
        transaction_activate(tr);
    }

test_abort:;
}

static void file_test_etc(struct transaction* tr,
                          bool commit,
                          bool allow_repaired,
                          const char* path,
                          enum file_create_mode create,
                          const char* move_path,
                          enum file_create_mode move_create,
                          int allocate,
                          int read,
                          int free,
                          bool delete,
                          int id) {
    enum file_op_result delete_res;
    struct file_handle file;

    open_test_file_etc(tr, &file, path, create, FILE_OP_SUCCESS,
                       allow_repaired);
    ASSERT_EQ(false, HasFailure());
    if (tr->failed) {
        goto test_abort;
    }

    file_test_commit(tr, commit);

    if (move_path) {
        file_move(tr, &file, move_path, move_create, allow_repaired);
        file_test_commit(tr, commit);
        path = move_path;
    }
    file_test_open(tr, &file, allocate, read, free, id);
    file_test_commit(tr, commit);

    if (delete) {
        if (print_test_verbose) {
            printf("%s: delete file %s, at %" PRIu64 ":\n", __func__, path,
                   block_mac_to_block(tr, &file.block_mac));
        }
        delete_res = file_delete(tr, path, allow_repaired);
        file_test_commit(tr, commit);
        ASSERT_EQ(FILE_OP_SUCCESS, delete_res);
    }

test_abort:;
    file_close(&file);
}

static void file_test(struct transaction* tr,
                      const char* path,
                      enum file_create_mode create,
                      int allocate,
                      int read,
                      int free,
                      bool delete,
                      int id) {
    file_test_etc(tr, false, false, path, create, NULL, FILE_OPEN_NO_CREATE,
                  allocate, read, free, delete, id);
}

static void clear_all_pending_superblock_writes() {
    fail_next_rpmb_writes(0, false);

    struct fs* fs = NULL;

    fs = &test_block_device.tr_state_rpmb;
    if (fs->initial_super_block_tr) {
        transaction_initial_super_block_complete(fs->initial_super_block_tr);
        EXPECT_EQ(NULL, fs->initial_super_block_tr);
    }

#if HAS_FS_TDP
    fs = &test_block_device.tr_state_ns_tdp;
    if (fs->initial_super_block_tr) {
        transaction_initial_super_block_complete(fs->initial_super_block_tr);
        EXPECT_EQ(NULL, fs->initial_super_block_tr);
    }
#endif

    fs = &test_block_device.tr_state_ns;
    if (fs->initial_super_block_tr) {
        transaction_initial_super_block_complete(fs->initial_super_block_tr);
        EXPECT_EQ(NULL, fs->initial_super_block_tr);
    }
}

static void reset_repaired_flag(struct transaction* tr) {
    tr->fs->main_repaired = false;
    tr->fs->needs_full_scan = false;
    write_current_super_block(tr->fs, false);
    transaction_initial_super_block_complete(tr->fs->initial_super_block_tr);
}

typedef struct transaction_test {
    struct transaction tr;
    int initial_super_block_version;
} StorageTest_t;

#define IS_TP() (_state->tr.fs == &test_block_device.tr_state_rpmb)

#if HAS_FS_TDP
#define IS_TDP() (_state->tr.fs == &test_block_device.tr_state_ns_tdp)
#else
#define IS_TDP() (false)
#endif

TEST_F_SETUP(StorageTest) {
    fail_next_rpmb_writes(0, false);
    mock_error_report_clear();
    transaction_init(&_state->tr, *((struct fs**)GetParam()), true);
    _state->initial_super_block_version = _state->tr.fs->super_block_version;
}

TEST_F_TEARDOWN(StorageTest) {
    transaction_free(&_state->tr);
    clear_all_pending_superblock_writes();
}

TEST_P(StorageTest, FileCreate) {
    const char* filename = "FileCreate";
    struct file_handle file;
    open_test_file(&_state->tr, &file, filename, FILE_OPEN_CREATE_EXCLUSIVE);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    file_close(&file);

    transaction_activate(&_state->tr);
    open_test_file(&_state->tr, &file, filename, FILE_OPEN_NO_CREATE);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    file_close(&file);

test_abort:;
}

TEST_P(StorageTest, FailDataWrite) {
    fail_next_rpmb_writes(1, false);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    /* force data block to be written */
    block_cache_clean_transaction(&_state->tr);
    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
        ASSERT_NE(NULL, _state->tr.fs->initial_super_block_tr);
        ASSERT_NE(_state->tr.fs->super_block_version,
                  _state->tr.fs->written_super_block_version);
    }
    ASSERT_EQ(true, _state->tr.failed);
    transaction_activate(&_state->tr);

    /* did we recover? */
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

    if (!IS_TP()) {
        /* assert that we have overwritten the superblock */
        ASSERT_NE(_state->initial_super_block_version,
                  _state->tr.fs->super_block_version);
    }

test_abort:;
}

TEST_P(StorageTest, FailDataWriteFullCache) {
    fail_next_rpmb_writes(1, false);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE,
              BLOCK_CACHE_SIZE + 1, 0, 0, false, 0);
    /*
     * We have queued more data than can fit in cache, we have to start flushing
     * now. For TP this will immediately fail.
     */

    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
        ASSERT_NE(NULL, _state->tr.fs->initial_super_block_tr);
        ASSERT_NE(_state->tr.fs->super_block_version,
                  _state->tr.fs->written_super_block_version);
    }
    ASSERT_EQ(true, _state->tr.failed);
    transaction_activate(&_state->tr);

    /* did we recover? */
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE,
              BLOCK_CACHE_SIZE + 1, 0, 0, false, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

    if (!IS_TP()) {
        /* assert that we have overwritten the superblock */
        ASSERT_NE(_state->initial_super_block_version,
                  _state->tr.fs->super_block_version);
    }

test_abort:;
}

TEST_P(StorageTest, FailDataWriteWithCounterIncrement) {
    fail_next_rpmb_writes(1, true);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    /* force data block to be written */
    block_cache_clean_transaction(&_state->tr);
    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
    }
    ASSERT_EQ(true, _state->tr.failed);
    ASSERT_NE(NULL, _state->tr.fs->initial_super_block_tr);
    ASSERT_NE(_state->tr.fs->super_block_version,
              _state->tr.fs->written_super_block_version);
    expect_errors(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED, 1);
    transaction_activate(&_state->tr);

    /* did we recover? */
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    block_cache_clean_transaction(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    /* assert that we have overwritten the superblock */
    ASSERT_NE(_state->initial_super_block_version,
              _state->tr.fs->super_block_version);

    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

test_abort:;
}

TEST_P(StorageTest, FailDataWriteFullCacheWithIncrement) {
    fail_next_rpmb_writes(1, true);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE,
              BLOCK_CACHE_SIZE + 1, 0, 0, false, 0);
    /*
     * We have queued more data than can fit in cache, we have to start flushing
     * now. For TP this will immediately fail.
     */

    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
    }
    ASSERT_EQ(true, _state->tr.failed);
    ASSERT_NE(NULL, _state->tr.fs->initial_super_block_tr);
    ASSERT_NE(_state->tr.fs->super_block_version,
              _state->tr.fs->written_super_block_version);
    expect_errors(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED, 1);
    transaction_activate(&_state->tr);

    /* did we recover? */
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE,
              BLOCK_CACHE_SIZE + 1, 0, 0, false, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

    /* assert that we have overwritten the superblock */
    ASSERT_NE(_state->initial_super_block_version,
              _state->tr.fs->super_block_version);

test_abort:;
}

/*
 * Test that we don't crash the storage process if we fail to verify an RPMB
 * write. This verify failure can occur if the storageproxy has shut down
 * because a reboot is in progress, so we don't want to take down the entire
 * device just because this happens.
 */
TEST_P(StorageTest, FailRpmbVerify) {
    handle_t null_handle = 0;
    int rc;

    file_test(&_state->tr, "FailRpmbVerifyValidFile",
              FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);

    fail_next_rpmb_writes(1, true);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    /* force data block to be written */
    block_cache_clean_transaction(&_state->tr);
    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
    }
    ASSERT_EQ(true, _state->tr.failed);
    ASSERT_NE(NULL, _state->tr.fs->initial_super_block_tr);
    ASSERT_NE(_state->tr.fs->super_block_version,
              _state->tr.fs->written_super_block_version);
    expect_errors(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED, 1);
    transaction_activate(&_state->tr);

    /* Fail the verification that we actually performed the RPMB write */
    fail_next_rpmb_reads(1);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    /* Write will fail; first_write_complete is false and verification fails */
    block_cache_clean_transaction(&_state->tr);
    if (!IS_TP()) {
        /* only superblock write should fail for TD */
        transaction_complete(&_state->tr);
    }
    ASSERT_EQ(true, _state->tr.failed);
    transaction_activate(&_state->tr);

    /* All RPMB access should now fail */
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(true, _state->tr.failed);

    /*
     * de-initialize and re-initialize the block device to clear the
     * verify_failed flag from the rpmb state.
     */
    transaction_free(&_state->tr);
    block_device_tipc_uninit(&test_block_device);
    rc = block_device_tipc_init(&test_block_device, null_handle,
                                &storage_test_key, NULL, null_handle);
    ASSERT_EQ(rc, 0);
    transaction_init(&_state->tr, *((struct fs**)GetParam()), true);

    /* Everything should work now */
    file_test(&_state->tr, "FailRpmbVerifyValidFile", FILE_OPEN_NO_CREATE, 1, 0,
              0, true, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

test_abort:;
}

TEST(StorageTest, FlushFailingSpecialTransaction) {
    struct transaction td_tr;
    struct transaction tp_tr;
    transaction_init(&tp_tr, &test_block_device.tr_state_rpmb, true);
    int tp_initial_super_block_version = tp_tr.fs->super_block_version;

    mock_error_report_clear();

    fail_next_rpmb_writes(1, true);
    file_test(&tp_tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false, 0);

    /* force data block to be written */
    block_cache_clean_transaction(&tp_tr);
    ASSERT_EQ(true, tp_tr.failed);
    ASSERT_NE(NULL, tp_tr.fs->initial_super_block_tr);
    ASSERT_NE(tp_tr.fs->super_block_version,
              tp_tr.fs->written_super_block_version);
    expect_errors(TRUSTY_STORAGE_ERROR_RPMB_COUNTER_MISMATCH_RECOVERED, 1);
    transaction_activate(&tp_tr);

    /*
     * At this point there is a resync superblock write queueud up for each
     * FS
     */

    fail_next_rpmb_writes(1, false);
    transaction_init(&td_tr, &test_block_device.tr_state_ns, true);
    file_test(&td_tr, "FlushFailingSpecialTransaction_td",
              FILE_OPEN_CREATE_EXCLUSIVE, BLOCK_CACHE_SIZE, 0, 0, false, 0);
    block_cache_clean_transaction(&td_tr);
    /*
     * This transaction will fail because we couldn't write the superblock to
     * disk
     */
    ASSERT_EQ(true, td_tr.failed);
    transaction_activate(&td_tr);

    file_test(&td_tr, "FlushFailingSpecialTransaction_td",
              FILE_OPEN_CREATE_EXCLUSIVE, BLOCK_CACHE_SIZE, 0, 0, false, 0);
    block_cache_clean_transaction(&td_tr);
    ASSERT_EQ(false, td_tr.failed);
    transaction_complete(&td_tr);
    ASSERT_EQ(false, td_tr.failed);
    transaction_activate(&td_tr);

    /* TD resync is done, TP is pending */
    ASSERT_EQ(NULL, td_tr.fs->initial_super_block_tr);
    ASSERT_NE(NULL, tp_tr.fs->initial_super_block_tr);

    /* Fill the cache up */
    file_test(&td_tr, "FlushFailingSpecialTransaction_td", FILE_OPEN_CREATE,
              BLOCK_CACHE_SIZE + 1, 0, 0, false, 0);
    ASSERT_EQ(false, td_tr.failed);
    block_cache_clean_transaction(&td_tr);
    ASSERT_EQ(false, td_tr.failed);
    ASSERT_NE(NULL, tp_tr.fs->initial_super_block_tr);

    /* did we recover? */
    file_test(&tp_tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false, 0);

    block_cache_clean_transaction(&tp_tr);
    ASSERT_EQ(false, tp_tr.failed);
    /* assert that we have overwritten the superblock */
    ASSERT_NE(tp_initial_super_block_version, tp_tr.fs->super_block_version);

    transaction_complete(&tp_tr);
    ASSERT_EQ(false, tp_tr.failed);

test_abort:;
    if (!tp_tr.failed) {
        transaction_fail(&tp_tr);
    }
    if (!td_tr.failed) {
        transaction_fail(&td_tr);
    }
    transaction_free(&tp_tr);
    transaction_free(&td_tr);
    clear_all_pending_superblock_writes();
}

/* Storage tests for filesystems with non-RPMB backing file */

TEST_P(StorageTest, DesyncBackingFile) {
    int rc;
    handle_t null_handle = 0;
    struct file_handle file;
    bool allow_repaired = false;
    struct fs* fs = _state->tr.fs;

    if (IS_TP()) {
        transaction_fail(&_state->tr);
        trusty_unittest_printf(
                "[  SKIPPED ] - FS does not have separate backing storage\n");
        return;
    }

#if !STORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED
    if (IS_TDP()) {
        transaction_fail(&_state->tr);
        trusty_unittest_printf(
                "[  SKIPPED ] - FS is not configured to auto-repair\n");
        return;
    }
#endif

    /* ensure a file is only in the checkpoint */
    file_test(&_state->tr, "checkpoint_only", FILE_OPEN_CREATE, 1, 0, 0, false,
              1);
    transaction_complete_update_checkpoint(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);
    file_delete(&_state->tr, "checkpoint_only", false);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);

    ignore_next_ns_writes(INT_MAX);
    file_test(&_state->tr, __func__, FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);

    /* force data block to be written */
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    ignore_next_ns_writes(0);
    transaction_free(&_state->tr);

    block_device_tipc_uninit(&test_block_device);
    rc = block_device_tipc_init(&test_block_device, null_handle,
                                &storage_test_key, NULL, null_handle);
    ASSERT_EQ(rc, 0);
    transaction_init(&_state->tr, fs, true);
    _state->initial_super_block_version = _state->tr.fs->super_block_version;

    if (IS_TDP()) {
        /*
         * FS has been repaired, we shouldn't be able to create a file without
         * acknowledging this
         */
        open_test_file_etc(&_state->tr, &file, __func__,
                           FILE_OPEN_CREATE_EXCLUSIVE, FILE_OP_ERR_FS_REPAIRED,
                           allow_repaired);

        allow_repaired = true;

        /* check the checkpoint file (must allow repair) */
        file_test_etc(&_state->tr, false, allow_repaired, "checkpoint_only",
                      FILE_OPEN_NO_CREATE, NULL, FILE_OPEN_NO_CREATE, 0, 1, 0,
                      true, 1);
    }

    file_test_etc(&_state->tr, false, allow_repaired, __func__,
                  FILE_OPEN_CREATE_EXCLUSIVE, NULL, FILE_OPEN_NO_CREATE, 1, 0,
                  0, true, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

    /* assert that we have overwritten the superblock */
    ASSERT_NE(_state->initial_super_block_version,
              _state->tr.fs->super_block_version);

test_abort:;
    reset_repaired_flag(&_state->tr);
}

TEST_P(StorageTest, CorruptFileInfo) {
    int rc;
    handle_t null_handle = 0;
    struct file_handle file;
    bool allow_repaired = false;
    struct fs* fs = _state->tr.fs;

    if (IS_TP()) {
        transaction_fail(&_state->tr);
        trusty_unittest_printf(
                "[  SKIPPED ] - FS does not have separate backing storage\n");
        return;
    }

#if !STORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED
    if (IS_TDP()) {
        transaction_fail(&_state->tr);
        trusty_unittest_printf(
                "[  SKIPPED ] - FS is not configured to auto-repair\n");
        return;
    }
#endif

    /* ensure a file is only in the checkpoint */
    file_test(&_state->tr, "checkpoint_only", FILE_OPEN_CREATE, 1, 0, 0, false,
              1);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_complete_update_checkpoint(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);
    file_delete(&_state->tr, "checkpoint_only", false);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);

    ASSERT_EQ(false, _state->tr.fs->main_repaired);

    file_test(&_state->tr, "source", FILE_OPEN_CREATE_EXCLUSIVE, 1, 0, 0, false,
              0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    transaction_activate(&_state->tr);

    open_test_file(&_state->tr, &file, "source", FILE_OPEN_NO_CREATE);
    file_move(&_state->tr, &file, "corrupted_dest", FILE_OPEN_CREATE_EXCLUSIVE,
              false);
    ignore_next_ns_writes(1);
    block_cache_clean_transaction(&_state->tr);
    file_close(&file);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);
    ASSERT_EQ(false, _state->tr.fs->main_repaired);
    transaction_free(&_state->tr);

    /* remount the filesystem to clear the block cache */
    block_device_tipc_uninit(&test_block_device);
    rc = block_device_tipc_init(&test_block_device, null_handle,
                                &storage_test_key, NULL, null_handle);
    ASSERT_EQ(rc, 0);
    transaction_init(&_state->tr, fs, true);
    _state->initial_super_block_version = _state->tr.fs->super_block_version;

    open_test_file_etc(&_state->tr, &file, "corrupted_dest",
                       FILE_OPEN_NO_CREATE, FILE_OP_ERR_FAILED, false);
    ASSERT_EQ(true, _state->tr.failed);
    ASSERT_EQ(true, fs->needs_full_scan);
    transaction_free(&_state->tr);

    block_device_tipc_uninit(&test_block_device);
    rc = block_device_tipc_init(&test_block_device, null_handle,
                                &storage_test_key, NULL, null_handle);
    ASSERT_EQ(rc, 0);
    transaction_init(&_state->tr, fs, true);
    _state->initial_super_block_version = _state->tr.fs->super_block_version;

    /* TDP should restore to checkpoint and recover */
    ASSERT_EQ(IS_TDP(), !fs->needs_full_scan);

    if (IS_TDP()) {
        /*
         * FS has been repaired, we shouldn't be able to create a file without
         * acknowledging this
         */
        open_test_file_etc(&_state->tr, &file, __func__,
                           FILE_OPEN_CREATE_EXCLUSIVE, FILE_OP_ERR_FS_REPAIRED,
                           allow_repaired);

        allow_repaired = true;

        /* check the checkpoint file (must allow repair) */
        file_test_etc(&_state->tr, false, allow_repaired, "checkpoint_only",
                      FILE_OPEN_NO_CREATE, NULL, FILE_OPEN_NO_CREATE, 0, 1, 0,
                      true, 1);
    }

    file_test_etc(&_state->tr, false, allow_repaired, __func__,
                  FILE_OPEN_CREATE_EXCLUSIVE, NULL, FILE_OPEN_NO_CREATE, 1, 0,
                  0, false, 0);
    transaction_complete(&_state->tr);
    ASSERT_EQ(false, _state->tr.failed);

test_abort:;
    reset_repaired_flag(&_state->tr);
}

#if HAS_FS_TDP
#define TEST_FILESYSTEMS                             \
    testing_Values(&test_block_device.tr_state_rpmb, \
                   &test_block_device.tr_state_ns,   \
                   &test_block_device.tr_state_ns_tdp)
#else
#define TEST_FILESYSTEMS                             \
    testing_Values(&test_block_device.tr_state_rpmb, \
                   &test_block_device.tr_state_ns)
#endif /* HAS_FS_TDP */

INSTANTIATE_TEST_SUITE_P(Filesystem, StorageTest, TEST_FILESYSTEMS);

int main(int argc, const char* argv[]) {
    int rc = 1;
    handle_t null_handle = 0;
    char* exec_filename = NULL;

    crypt_init();
    block_cache_init();

    exec_filename = strdup(argv[0]);
    if (!exec_filename) {
        goto err;
    }
    const char* dir = dirname(exec_filename);
    if (!init_rpmb_state(dir)) {
        goto err;
    }
    rc = block_device_tipc_init(&test_block_device, null_handle,
                                &storage_test_key, NULL, null_handle);
    if (rc < 0) {
        fprintf(stderr, "%s: block_device_tipc_init failed (%d)\n", __func__,
                rc);
        goto err;
    }

    rc = RUN_ALL_TESTS() ? 0 : 1;

    block_device_tipc_uninit(&test_block_device);

err:
    crypt_shutdown();
    destroy_rpmb_state();
    if (exec_filename)
        free(exec_filename);

    (void)file_test;

    return rc;
}
