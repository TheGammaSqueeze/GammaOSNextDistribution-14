/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <assert.h>
#include <lk/macros.h>
#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <uapi/err.h>

#include <lib/storage/storage.h>

#ifndef STORAGE_FAKE
#include <lib/system_state/system_state.h>
#include <lib/unittest/unittest.h>
#include <trusty/time.h>
#endif

#include <trusty_unittest.h>

#define TLOG_TAG "ss_unittest"

#if STORAGE_UNITTEST_ON_EMULATOR
#define ENABLED_ON_EMULATOR_ONLY(name) name
#else
#define ENABLED_ON_EMULATOR_ONLY(name) DISABLED_##name
#endif

typedef void (*test_body)(storage_session_t ss, storage_session_t ss_aux);

static const char* storage_test_client_port;
static storage_session_t storage_test_ss_persist = STORAGE_INVALID_SESSION;

typedef struct {
    storage_session_t ss;
    storage_session_t ss_aux;
} StorageTest_t;

void StorageTest_SetUp(StorageTest_t* state) {
    state->ss = STORAGE_INVALID_SESSION;
    state->ss_aux = STORAGE_INVALID_SESSION;

    if (storage_test_ss_persist != STORAGE_INVALID_SESSION) {
        storage_close_session(storage_test_ss_persist);
        storage_test_ss_persist = STORAGE_INVALID_SESSION;
    }

    int rc = storage_open_session(&state->ss, storage_test_client_port);
    if (rc < 0) {
        TLOGE("failed (%d) to open session\n", rc);
        return;
    }

#ifndef STORAGE_FAKE
    rc = storage_open_session(&state->ss_aux, storage_test_client_port);
    if (rc < 0) {
        TLOGE("failed (%d) to open session\n", rc);
        storage_close_session(state->ss);
        return;
    }
#endif
};

void StorageTest_TearDown(StorageTest_t* state) {
    storage_close_session(state->ss);

#ifndef STORAGE_FAKE
    storage_close_session(state->ss_aux);
#endif
}

#undef TEST_F
#define TEST_F(suite_name, test_name)                                   \
    TEST_F_CUSTOM_ARGS(suite_name, test_name, (state.ss, state.ss_aux), \
                       (storage_session_t ss, storage_session_t ss_aux))

#ifndef STORAGE_FAKE
TEST_FIXTURE_ALIAS(StorageInitNoCommitSmallTest, StorageTest)
TEST_FIXTURE_ALIAS(StorageInitNoCommitLargeTest, StorageTest)
TEST_FIXTURE_ALIAS(StorageInitNoCommitCleanupTest, StorageTest)
#endif
TEST_FIXTURE_ALIAS(StorageInitTest, StorageTest)
TEST_FIXTURE_ALIAS(StorageCheckTest, StorageTest)
TEST_FIXTURE_ALIAS(StorageCleanTest, StorageTest)

static inline bool is_32bit_aligned(size_t sz) {
    return ((sz & 0x3) == 0);
}

static inline bool is_valid_size(size_t sz) {
    return (sz > 0) && is_32bit_aligned(sz);
}

static bool is_valid_offset(storage_off_t off) {
    return (off & 0x3) == 0ULL;
}

static void fill_pattern32(uint32_t* buf, size_t len, storage_off_t off) {
    size_t cnt = len / sizeof(uint32_t);
    uint32_t pattern = (uint32_t)(off / sizeof(uint32_t));
    for (; cnt > 0; cnt--) {
        *buf++ = pattern++;
    }
}

static bool check_pattern32(const uint32_t* buf,
                            size_t len,
                            storage_off_t off) {
    size_t cnt = len / sizeof(uint32_t);
    uint32_t pattern = (uint32_t)(off / sizeof(uint32_t));
    for (; cnt > 0; cnt--) {
        if (*buf != pattern)
            return false;
        buf++;
        pattern++;
    }
    return true;
}

static bool check_value32(const uint32_t* buf, size_t len, uint32_t val) {
    size_t cnt = len / sizeof(uint32_t);
    for (; cnt > 0; cnt--) {
        if (*buf != val)
            return false;
        buf++;
    }
    return true;
}

static int WriteZeroChunk(file_handle_t handle,
                          storage_off_t off,
                          size_t chunk_len,
                          uint32_t opflags) {
    uint32_t data_buf[chunk_len / sizeof(uint32_t)];

    assert(is_valid_size(chunk_len));
    assert(is_valid_offset(off));

    memset(data_buf, 0, chunk_len);

    return storage_write(handle, off, data_buf, sizeof(data_buf), opflags);
}

static int WritePatternChunk(file_handle_t handle,
                             storage_off_t off,
                             size_t chunk_len,
                             uint32_t opflags) {
    uint32_t data_buf[chunk_len / sizeof(uint32_t)];

    assert(is_valid_size(chunk_len));
    assert(is_valid_offset(off));

    fill_pattern32(data_buf, chunk_len, off);

    return storage_write(handle, off, data_buf, sizeof(data_buf), opflags);
}

static int WritePatternExt(file_handle_t handle,
                           storage_off_t off,
                           size_t data_len,
                           size_t chunk_len,
                           uint32_t extra_opflags) {
    size_t written = 0;

    assert(is_valid_size(data_len));
    assert(is_valid_size(chunk_len));

    while (data_len) {
        if (data_len < chunk_len)
            chunk_len = data_len;
        int rc = WritePatternChunk(handle, off, chunk_len,
                                   (chunk_len == data_len) ? extra_opflags : 0);
        if (rc < 0)
            return rc;
        if ((size_t)rc != chunk_len)
            return written + rc;
        off += chunk_len;
        data_len -= chunk_len;
        written += chunk_len;
    }
    return (int)written;
}

static int WritePattern(file_handle_t handle,
                        storage_off_t off,
                        size_t data_len,
                        size_t chunk_len,
                        bool complete) {
    return WritePatternExt(handle, off, data_len, chunk_len,
                           complete ? STORAGE_OP_COMPLETE : 0);
}

static int ReadChunk(file_handle_t handle,
                     storage_off_t off,
                     size_t chunk_len,
                     size_t head_len,
                     size_t pattern_len,
                     size_t tail_len) {
    int rc;
    uint32_t data_buf[chunk_len / sizeof(uint32_t)];
    uint8_t* data_ptr = (uint8_t*)data_buf;

    assert(is_valid_size(chunk_len));
    assert(is_valid_offset(off));
    assert((head_len + pattern_len + tail_len) == chunk_len);

    rc = storage_read(handle, off, data_buf, chunk_len);
    if ((size_t)rc != chunk_len)
        return rc;

    if (head_len) {
        if (!check_value32((const uint32_t*)data_ptr, head_len, 0))
            return ERR_CHECKSUM_FAIL;
        data_ptr += head_len;
        off += head_len;
    }

    if (pattern_len) {
        if (!check_pattern32((const uint32_t*)data_ptr, pattern_len, off))
            return ERR_CHECKSUM_FAIL;
        data_ptr += pattern_len;
    }

    if (tail_len) {
        if (!check_value32((const uint32_t*)data_ptr, tail_len, 0))
            return ERR_CHECKSUM_FAIL;
    }

    return chunk_len;
}

static int ReadPattern(file_handle_t handle,
                       storage_off_t off,
                       size_t data_len,
                       size_t chunk_len) {
    int rc;
    size_t bytes_read = 0;
    uint32_t data_buf[chunk_len / sizeof(uint32_t)];

    assert(is_valid_size(chunk_len));
    assert(is_valid_size(data_len));
    assert(is_valid_offset(off));

    while (data_len) {
        if (chunk_len > data_len)
            chunk_len = data_len;
        rc = storage_read(handle, off, data_buf, sizeof(data_buf));
        if (rc < 0)
            return rc;
        if ((size_t)rc != chunk_len)
            return bytes_read + rc;
        if (!check_pattern32(data_buf, chunk_len, off))
            return ERR_CHECKSUM_FAIL;
        off += chunk_len;
        data_len -= chunk_len;
        bytes_read += chunk_len;
    }
    return bytes_read;
}

static int ReadPatternEOF(file_handle_t handle,
                          storage_off_t off,
                          size_t chunk_len) {
    int rc;
    size_t bytes_read = 0;
    uint32_t data_buf[chunk_len / sizeof(uint32_t)];

    assert(is_valid_size(chunk_len));

    while (true) {
        rc = storage_read(handle, off, data_buf, sizeof(data_buf));
        if (rc < 0)
            return rc;
        if (rc == 0)
            break;  // end of file reached
        if (!is_valid_size((size_t)rc))
            return ERR_BAD_LEN;
        if (!check_pattern32(data_buf, rc, off))
            return ERR_CHECKSUM_FAIL;
        off += rc;
        bytes_read += rc;
    }
    return bytes_read;
}

TEST_F(StorageTest, CreateDelete) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_create_delete_file";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc, "delete test file");

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc, "delete again");

    // create file (expect 0)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "create test file");

    // try to create it again while it is still opened (expect
    // ERR_ALREADY_EXISTS)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_ALREADY_EXISTS, rc, "create again");

    // close it
    storage_close_file(handle);

    // try to create it again while it is closed (expect ERR_ALREADY_EXISTS)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_ALREADY_EXISTS, rc, "create again");

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "delete test file");

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc, "delete again");

test_abort:;
}

#ifndef STORAGE_FAKE
// Moving file and opening directory is not supported in fake secure
// storage implementation.

TEST_F(StorageTest, CreateMoveDelete) {
    int rc;
    file_handle_t handle;
    const char* fname1 = "test_create_move_delete_1_file";
    const char* fname2 = "test_create_move_delete_2_file";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc, "delete test file1");
    rc = storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc, "delete test file2");

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc, "delete 1 again");
    rc = storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc, "delete 2 again");

    // create file (expect 0)
    rc = storage_open_file(
            ss, &handle, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "create test file");

    // move file
    rc = storage_move_file(ss, handle, fname1, fname2,
                           STORAGE_FILE_MOVE_CREATE |
                                   STORAGE_FILE_MOVE_CREATE_EXCLUSIVE |
                                   STORAGE_FILE_MOVE_OPEN_FILE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "move test file");

    // try to create it again while it is still opened (expect
    // ERR_ALREADY_EXISTS)
    rc = storage_open_file(
            ss, &handle, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_ALREADY_EXISTS, rc, "create again");

    // close it
    storage_close_file(handle);

    // try to create it again while it is closed (expect ERR_ALREADY_EXISTS)
    rc = storage_open_file(
            ss, &handle, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_ALREADY_EXISTS, rc, "create again");

    // create file1 (expect 0)
    rc = storage_open_file(
            ss, &handle, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "create test file");

    // move file
    rc = storage_move_file(ss, handle, fname1, fname2,
                           STORAGE_FILE_MOVE_CREATE |
                                   STORAGE_FILE_MOVE_CREATE_EXCLUSIVE |
                                   STORAGE_FILE_MOVE_OPEN_FILE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_ALREADY_EXISTS, rc, "move test file");

    // close it
    storage_close_file(handle);

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "delete test file");

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc, "delete again");

test_abort:
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);
}

TEST_F(StorageTest, FileList) {
    int rc;
    file_handle_t handle;
    struct storage_open_dir_state* dir;
    const char* fname_pat = "test_file_list_%d_file";
    char file_name[64];
    char file_name_dir[64];
    uint8_t read_dir_flags;
    int i;
    int file_count = 100;

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    for (i = 0; i < file_count; i++) {
        snprintf(file_name, sizeof(file_name), fname_pat, i);
        rc = storage_delete_file(ss, file_name, STORAGE_OP_COMPLETE);
        rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
        ASSERT_EQ(0, rc, "delete test file1");
    }

    // one more time (expect ERR_NOT_FOUND)
    for (i = 0; i < file_count; i++) {
        snprintf(file_name, sizeof(file_name), fname_pat, i);
        rc = storage_delete_file(ss, file_name, STORAGE_OP_COMPLETE);
        ASSERT_EQ(ERR_NOT_FOUND, rc, "delete 1 again");
    }

    // test empty dir
    rc = storage_open_dir(ss, "", &dir);
    ASSERT_EQ(0, rc, "open_dir");

    rc = storage_read_dir(ss, dir, &read_dir_flags, file_name_dir,
                          sizeof(file_name_dir));
    ASSERT_EQ(0, rc, "read_dir");
    ASSERT_EQ(STORAGE_FILE_LIST_END,
              read_dir_flags & STORAGE_FILE_LIST_STATE_MASK, "dir flags");

    storage_close_dir(ss, dir);

    // create file (expect 0)
    snprintf(file_name, sizeof(file_name), fname_pat, 0);
    rc = storage_open_file(
            ss, &handle, file_name,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc, "create test file 1");

    // close it
    storage_close_file(handle);

    for (i = 1; i < file_count; i++) {
        snprintf(file_name, sizeof(file_name), fname_pat, i);
        rc = storage_open_file(
                ss, &handle, file_name,
                STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
                0);
        ASSERT_EQ(0, rc, "create test file 2");

        // close it
        storage_close_file(handle);
    }

    // test read_dir fname1 committed, fname2 added
    rc = storage_open_dir(ss, "", &dir);
    ASSERT_EQ(0, rc, "open_dir");

    snprintf(file_name, sizeof(file_name), fname_pat, 0);
    rc = storage_read_dir(ss, dir, &read_dir_flags, file_name_dir,
                          sizeof(file_name_dir));
    ASSERT_EQ(strlen(file_name) + 1, rc, "read_dir");
    ASSERT_EQ(STORAGE_FILE_LIST_COMMITTED,
              read_dir_flags & STORAGE_FILE_LIST_STATE_MASK, "dir flags");
    ASSERT_EQ(0, strcmp(file_name, file_name_dir), "file name");

    for (i = 1; i < file_count; i++) {
        rc = storage_read_dir(ss, dir, &read_dir_flags, file_name_dir,
                              sizeof(file_name_dir));
        ASSERT_EQ(strlen(file_name_dir) + 1, rc, "read_dir");
        ASSERT_EQ(STORAGE_FILE_LIST_ADDED,
                  read_dir_flags & STORAGE_FILE_LIST_STATE_MASK, "dir flags");
        ASSERT_NE(0, strcmp(file_name, file_name_dir), "file name");
    }

    rc = storage_read_dir(ss, dir, &read_dir_flags, file_name_dir,
                          sizeof(file_name_dir));
    ASSERT_EQ(0, rc, "read_dir");
    ASSERT_EQ(STORAGE_FILE_LIST_END,
              read_dir_flags & STORAGE_FILE_LIST_STATE_MASK, "end dir flag");

    storage_close_dir(ss, dir);

    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc, "commit");

test_abort:
    for (i = 0; i < file_count; i++) {
        snprintf(file_name, sizeof(file_name), fname_pat, i);
        rc = storage_delete_file(ss, file_name, STORAGE_OP_COMPLETE);
    }
}
#endif

TEST_F(StorageTest, DeleteOpened) {
    int rc;
    file_handle_t handle;
    const char* fname = "delete_opened_test_file";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // open/create file (expect 0)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // delete opened file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // close file
    storage_close_file(handle);

    // one more time (expect ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

test_abort:;
}

TEST_F(StorageTest, OpenNoCreate) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_open_no_create_file";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

    // open non-existing file (expect ERR_NOT_FOUND)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // create file (expect 0)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);
    storage_close_file(handle);

    // open existing file (expect 0)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // close it
    storage_close_file(handle);

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest, OpenOrCreate) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_open_create_file";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

    // open/create a non-existing file (expect 0)
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_CREATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);
    storage_close_file(handle);

    // open/create an existing file (expect 0)
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_CREATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);
    storage_close_file(handle);

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest, OpenCreateDeleteCharset) {
    int rc;
    file_handle_t handle;
    const char* fname =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdefghijklmnopqrstuvwxyz_01234.56789";

    // open/create file (expect 0)
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_CREATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);
    storage_close_file(handle);

    // open/create an existing file (expect 0)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);
    storage_close_file(handle);

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open again
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

test_abort:;
}

TEST_F(StorageTest, WriteReadSequential) {
    int rc;
    size_t blk = 2048;
    file_handle_t handle;
    const char* fname = "test_write_read_sequential";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

    // create file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write a bunch of blocks (sequentially)
    rc = WritePattern(handle, 0, 32 * blk, blk, true);
    ASSERT_EQ((int)(32 * blk), rc);

    rc = ReadPattern(handle, 0, 32 * blk, blk);
    ASSERT_EQ((int)(32 * blk), rc);

    // close file
    storage_close_file(handle);

    // open the same file again
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // read data back (sequentially) and check pattern again
    rc = ReadPattern(handle, 0, 32 * blk, blk);
    ASSERT_EQ((int)(32 * blk), rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, OpenTruncate) {
    int rc;
    uint32_t val;
    size_t blk = 2048;
    file_handle_t handle;
    const char* fname = "test_open_truncate";

    // make sure test file does not exist (expect success or ERR_NOT_FOUND)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

    // create file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write some data and read it back
    rc = WritePatternChunk(handle, 0, blk, true);
    ASSERT_EQ((int)blk, rc);

    rc = ReadPattern(handle, 0, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // close file
    storage_close_file(handle);

    // reopen with truncate
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_TRUNCATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    /* try to read data back (expect no data) */
    rc = storage_read(handle, 0LL, &val, sizeof(val));
    ASSERT_EQ(0, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, OpenSame) {
    int rc;
    file_handle_t handle1;
    file_handle_t handle2;
    file_handle_t handle3;
    const char* fname = "test_open_same_file";

    // open/create file (expect 0)
    rc = storage_open_file(ss, &handle1, fname, STORAGE_FILE_OPEN_CREATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);
    storage_close_file(handle1);

    // open an existing file first time (expect 0)
    rc = storage_open_file(ss, &handle1, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // open the same file second time (is not allowed)
    rc = storage_open_file(ss, &handle2, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_ALLOWED, rc);

    // delete file (expect 0)
    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open deleted file (expect ERR_NOT_FOUND)
    rc = storage_open_file(ss, &handle3, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // close file
    storage_close_file(handle1);

test_abort:;
}

TEST_F(StorageTest, OpenMany) {
    int rc;
    file_handle_t handles[10];
    char filename[10];
    const char* fname_fmt = "mf%d";

    // open or create a bunch of files (expect 0)
    for (uint32_t i = 0; i < countof(handles); ++i) {
        snprintf(filename, sizeof(filename), fname_fmt, i);
        rc = storage_open_file(ss, &handles[i], filename,
                               STORAGE_FILE_OPEN_CREATE, STORAGE_OP_COMPLETE);
        ASSERT_EQ(0, rc);
    }

    // check that all handles are different
    for (uint32_t i = 0; i < countof(handles) - 1; i++) {
        for (uint32_t j = i + 1; j < countof(handles); j++) {
            ASSERT_NE(handles[i], handles[j]);
        }
    }

    // close them all
    for (uint32_t i = 0; i < countof(handles); ++i) {
        storage_close_file(handles[i]);
    }

    // open all files without CREATE flags (expect 0)
    for (uint32_t i = 0; i < countof(handles); ++i) {
        snprintf(filename, sizeof(filename), fname_fmt, i);
        rc = storage_open_file(ss, &handles[i], filename, 0, 0);
        ASSERT_EQ(0, rc);
    }

    // check that all handles are different
    for (uint32_t i = 0; i < countof(handles) - 1; i++) {
        for (uint32_t j = i + 1; j < countof(handles); j++) {
            ASSERT_NE(handles[i], handles[j]);
        }
    }

    // close and remove all test files
    for (uint32_t i = 0; i < countof(handles); ++i) {
        storage_close_file(handles[i]);
        snprintf(filename, sizeof(filename), fname_fmt, i);
        rc = storage_delete_file(ss, filename, STORAGE_OP_COMPLETE);
        ASSERT_EQ(0, rc);
    }

test_abort:;
}

TEST_F(StorageTest, ReadAtEOF) {
    int rc;
    uint32_t val;
    size_t blk = 2048;
    file_handle_t handle;
    const char* fname = "test_read_eof";

    // open/create/truncate file
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write block at offset 0
    rc = WritePatternChunk(handle, 0, blk, true);
    ASSERT_EQ((int)blk, rc);

    // close file
    storage_close_file(handle);

    // open same file again
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // read the whole block back and check pattern again
    rc = ReadPattern(handle, 0, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // read at end of file (expected 0 bytes)
    rc = storage_read(handle, blk, &val, sizeof(val));
    ASSERT_EQ(0, rc);

    // partial read at end of the file (expected partial data)
    rc = ReadPatternEOF(handle, blk / 2, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // read past end of file
    rc = storage_read(handle, blk + 2, &val, sizeof(val));
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, GetFileSize) {
    int rc;
    size_t blk = 2048;
    storage_off_t size;
    file_handle_t handle;
    const char* fname = "test_get_file_size";

    // open/create/truncate file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check file size (expect success and size == 0)
    size = 1;
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, size);

    // write block
    rc = WritePatternChunk(handle, 0, blk, true);
    ASSERT_EQ((int)blk, rc);

    // check size
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ(blk, size);

    // write another block
    rc = WritePatternChunk(handle, blk, blk, true);
    ASSERT_EQ((int)blk, rc);

    // check size again
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ(blk * 2, size);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, SetFileSize) {
    int rc;
    size_t blk = 2048;
    storage_off_t size;
    file_handle_t handle;
    const char* fname = "test_set_file_size";

    // open/create/truncate file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check file size (expect success and size == 0)
    size = 1;
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, size);

    // write block
    rc = WritePatternChunk(handle, 0, blk, true);
    ASSERT_EQ((int)blk, rc);

    // check size
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ(blk, size);

    storage_close_file(handle);

    // reopen normally
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // check size again
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ(blk, size);

    // set file size to half
    rc = storage_set_file_size(handle, blk / 2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check size again (should be half of original size)
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ(blk / 2, size);

    // read data back
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // set file size to 0
    rc = storage_set_file_size(handle, 0, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check size again (should be 0)
    rc = storage_get_file_size(handle, &size);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0LL, size);

    // try to read again
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ(0, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, WriteReadAtOffset) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t blk_cnt = 32;
    const char* fname = "test_write_at_offset";

    // create/truncate file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write a bunch of blocks filled with zeroes
    for (size_t i = 0; i < blk_cnt; i++) {
        rc = WriteZeroChunk(handle, i * blk, blk, true);
        ASSERT_EQ((int)blk, rc);
    }

    storage_off_t off1 = blk;
    storage_off_t off2 = blk * (blk_cnt - 1);

    // write known pattern data at non-zero offset1
    rc = WritePatternChunk(handle, off1, blk, true);
    ASSERT_EQ((int)blk, rc);

    // write known pattern data at non-zero offset2
    rc = WritePatternChunk(handle, off2, blk, true);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset1
    rc = ReadPattern(handle, off1, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset2
    rc = ReadPattern(handle, off2, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // read partially written data at end of file(expect to get data only, no
    // padding)
    rc = ReadPatternEOF(handle, off2 + blk / 2, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // read data at offset 0 (expect success and zero data)
    rc = ReadChunk(handle, 0, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // read data from gap (expect success and zero data)
    rc = ReadChunk(handle, off1 + blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // read partially written data (start pointing within written data)
    // (expect to get written data back and zeroes at the end)
    rc = ReadChunk(handle, off1 + blk / 2, blk, 0, blk / 2, blk / 2);
    ASSERT_EQ((int)blk, rc);

    // read partially written data (start pointing within unwritten data)
    // expect to get zeroes at the beginning and proper data at the end
    rc = ReadChunk(handle, off1 - blk / 2, blk, blk / 2, blk / 2, 0);
    ASSERT_EQ((int)blk, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, WriteReadAtOffsetSparse) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t blk_cnt = 32;
    const char* fname = "test_write_at_offset";

    // create/truncate file.
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    storage_off_t off1 = blk;
    storage_off_t off2 = blk * (blk_cnt - 1);

    // write known pattern data at non-zero offset1
    rc = WritePatternChunk(handle, off1, blk, true);
    ASSERT_EQ((int)blk, rc);

    // write known pattern data at non-zero offset2
    rc = WritePatternChunk(handle, off2, blk, true);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset1
    rc = ReadPattern(handle, off1, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset2
    rc = ReadPattern(handle, off2, blk, blk);
    ASSERT_EQ((int)blk, rc);

    // read partially written data at end of file(expect to get data only, no
    // padding)
    rc = ReadPatternEOF(handle, off2 + blk / 2, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // read data at offset 0 (expect success and zero data)
    rc = ReadChunk(handle, 0, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // read data from gap (expect success and zero data)
    rc = ReadChunk(handle, off1 + blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // read partially written data (start pointing within written data)
    // (expect to get written data back and zeroes at the end)
    rc = ReadChunk(handle, off1 + blk / 2, blk, 0, blk / 2, blk / 2);
    ASSERT_EQ((int)blk, rc);

    // read partially written data (start pointing within unwritten data)
    // expect to get zeroes at the beginning and proper data at the end
    rc = ReadChunk(handle, off1 - blk / 2, blk, blk / 2, blk / 2, 0);
    ASSERT_EQ((int)blk, rc);

    // set file size to half way into first written block
    rc = storage_set_file_size(handle, off1 + blk / 2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // read partially written data at end of file(expect to get data only, no
    // padding)
    rc = ReadPatternEOF(handle, off1, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // write known pattern data at non-zero offset2
    rc = WritePatternChunk(handle, off2, blk, true);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset1
    rc = ReadPattern(handle, off1, blk / 2, blk / 2);
    ASSERT_EQ((int)blk / 2, rc);

    // read data from gap (expect success and zero data)
    rc = ReadChunk(handle, off1 + blk / 2, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // write known pattern data at non-zero offset1 - test again with
    // set_file_size
    rc = WritePatternChunk(handle, off1, blk, true);
    ASSERT_EQ((int)blk, rc);

    // set file size to half way into first written block
    rc = storage_set_file_size(handle, off1 + blk / 2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // read partially written data at end of file(expect to get data only, no
    // padding)
    rc = ReadPatternEOF(handle, off1, blk);
    ASSERT_EQ((int)blk / 2, rc);

    // set file size to offset2
    rc = storage_set_file_size(handle, off2, STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write known pattern data at non-zero offset2
    rc = WritePatternChunk(handle, off2, blk, true);
    ASSERT_EQ((int)blk, rc);

    // read data back at offset1
    rc = ReadPattern(handle, off1, blk / 2, blk / 2);
    ASSERT_EQ((int)blk / 2, rc);

    // read data from gap (expect success and zero data)
    rc = ReadChunk(handle, off1 + blk / 2, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

#ifndef STORAGE_FAKE
// STORAGE_FAKE does not support multiple sessionsa

// Leave a small transaction open with uncommitted data blocks. This test should
// fit in the cache so no data should need to be written to disk. This can be
// used on a newly wiped filesystem to test the cleanup path if storageproxyd
// disconnects before anything has been written.
TEST_F(StorageInitNoCommitSmallTest, CreatePersistentNoCommitSmall) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_persistent_small_uncommited_file";

    rc = storage_open_session(&storage_test_ss_persist,
                              storage_test_client_port);
    ASSERT_EQ(0, rc);

    // create/truncate file.
    rc = storage_open_file(
            storage_test_ss_persist, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    ASSERT_EQ(0, rc);

    // close but do not delete file
    storage_close_file(handle);

test_abort:;
}

// Leave large a transaction open with uncommitted data blocks. This can be used
// on a newly wiped filesystem to leave it in a state where only data blocks
// have been written and expose bugs in how we auto clear the td partition.
TEST_F(StorageInitNoCommitLargeTest, CreatePersistentNoCommitLarge) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    // Size of file should be too large to fit in block cache
    size_t file_size = 64 * 2048;
    const char* fname = "test_persistent_large_uncommited_file";

    rc = storage_open_session(&storage_test_ss_persist,
                              storage_test_client_port);
    ASSERT_EQ(0, rc);

    // create/truncate file.
    rc = storage_open_file(
            storage_test_ss_persist, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    ASSERT_EQ(0, rc);

    // write a bunch of blocks filled with pattern
    rc = WritePattern(handle, 0, file_size, blk, false);
    ASSERT_EQ((int)file_size, rc);

    // close but do not delete file
    storage_close_file(handle);

test_abort:;
}

// Empty test that can be used to close the session created by
// CreatePersistentNoCommitSmall or CreatePersistentNoCommitLarge.
TEST_F(StorageInitNoCommitCleanupTest, NoCommitCleanup) {
    // Fixture setup function closes the previous persistent session.
}
#endif

TEST_F(StorageInitTest, CreatePersistent32K) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t file_size = 32768;
    const char* fname = "test_persistent_32K_file";

    // create/truncate file. Don't commit until the write is complete so we
    // only perform a single superblock update
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    ASSERT_EQ(0, rc);

    // write a bunch of blocks filled with pattern
    rc = WritePattern(handle, 0, file_size, blk, true);
    ASSERT_EQ((int)file_size, rc);

    // close but do not delete file
    storage_close_file(handle);

test_abort:;
}

TEST_F(StorageCheckTest, ReadPersistent32k) {
    int rc;
    file_handle_t handle;
    size_t exp_len = 32 * 1024;
    const char* fname = "test_persistent_32K_file";

    // create/truncate file.
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    rc = ReadPatternEOF(handle, 0, 2048);
    ASSERT_EQ((int)exp_len, rc);

    rc = ReadPatternEOF(handle, 0, 1024);
    ASSERT_EQ((int)exp_len, rc);

    rc = ReadPatternEOF(handle, 0, 332);
    ASSERT_EQ((int)exp_len, rc);

    // close but do not delete file
    storage_close_file(handle);

test_abort:;
}

TEST_F(StorageCleanTest, CleanUpPersistent32K) {
    int rc;
    const char* fname = "test_persistent_32K_file";

    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    rc = (rc == ERR_NOT_FOUND) ? 0 : rc;
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest, WriteReadLong) {
    int rc;
    file_handle_t handle;
    size_t wc = 10000;
    const char* fname = "test_write_read_long";

    uint32_t* test_buf_ = malloc(wc * sizeof(uint32_t));
    ASSERT_NE(NULL, test_buf_);

    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    fill_pattern32(test_buf_, wc * sizeof(uint32_t), 0);
    rc = storage_write(handle, 0, test_buf_, wc * sizeof(uint32_t),
                       STORAGE_OP_COMPLETE);
    ASSERT_EQ((int)(wc * sizeof(uint32_t)), rc);

    rc = storage_read(handle, 0, test_buf_, wc * sizeof(uint32_t));
    ASSERT_EQ((int)(wc * sizeof(uint32_t)), rc);

    bool res = check_pattern32(test_buf_, wc * sizeof(uint32_t), 0);
    ASSERT_EQ(true, res);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:
    if (test_buf_)
        free(test_buf_);
}

// Negative tests

TEST_F(StorageTest, OpenInvalidFileName) {
    int rc;
    file_handle_t handle;
    const char* fname1 = "";
    const char* fname2 = "ffff$ffff";
    const char* fname3 = "ffff\\ffff";
    char max_name[STORAGE_MAX_NAME_LENGTH_BYTES + 1];

    rc = storage_open_file(
            ss, &handle, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    rc = storage_open_file(
            ss, &handle, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    rc = storage_open_file(
            ss, &handle, fname3,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    /* max name */
    memset(max_name, 'a', sizeof(max_name));
    max_name[sizeof(max_name) - 1] = 0;

    rc = storage_open_file(
            ss, &handle, max_name,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    max_name[sizeof(max_name) - 2] = 0;
    rc = storage_open_file(
            ss, &handle, max_name,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    storage_close_file(handle);
    storage_delete_file(ss, max_name, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, BadFileHandle) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_invalid_file_handle";

    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write to invalid file handle
    uint32_t val = 0xDEDBEEF;
    rc = storage_write(handle + 1, 0, &val, sizeof(val), STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // read from invalid handle
    rc = storage_read(handle + 1, 0, &val, sizeof(val));
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // set size
    rc = storage_set_file_size(handle + 1, 0, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // get size
    storage_off_t fsize = (storage_off_t)(-1);
    rc = storage_get_file_size(handle + 1, &fsize);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // close (there is no way to check errors here)
    storage_close_file(handle + 1);

    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, ClosedFileHandle) {
    int rc;
    file_handle_t handle1;
    file_handle_t handle2;
    const char* fname1 = "test_invalid_file_handle1";
    const char* fname2 = "test_invalid_file_handle2";

    rc = storage_open_file(
            ss, &handle1, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    rc = storage_open_file(
            ss, &handle2, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // close first file handle
    storage_close_file(handle1);

    // write to invalid file handle
    uint32_t val = 0xDEDBEEF;
    rc = storage_write(handle1, 0, &val, sizeof(val), STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // read from invalid handle
    rc = storage_read(handle1, 0, &val, sizeof(val));
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // set size
    rc = storage_set_file_size(handle1, 0, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // get size
    storage_off_t fsize = (storage_off_t)(-1);
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // close (there is no way to check errors here)
    storage_close_file(handle1);

    // clean up
    storage_close_file(handle2);
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);

test_abort:;
}

#ifndef STORAGE_FAKE
TEST_F(StorageTest, TransactDiscardInactive) {
    int rc;

    // discard current transaction (there should not be any)
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // try it again
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest, TransactCommitInactive) {
    int rc;

    // try to commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // try it again
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardWrite) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_discard_write";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // write (without commit)
    rc = WritePattern(handle, 0, exp_len, blk, false);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardWriteAppend) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_write_append";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data with commit
    rc = WritePattern(handle, 0, exp_len / 2, blk, true);
    ASSERT_EQ((int)exp_len / 2, rc);

    // write data without commit
    rc = WritePattern(handle, exp_len / 2, exp_len / 2, blk, false);
    ASSERT_EQ((int)exp_len / 2, rc);

    // check file size (should be exp_len)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // discard transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // check file size, it should be exp_len/2
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 2, fsize);

    // check file data
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ((int)exp_len / 2, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardWriteRead) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_discard_write_read";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // Fill with zeroes (with commit)
    for (uint32_t i = 0; i < 32; i++) {
        rc = WriteZeroChunk(handle, i * blk, blk, true);
        ASSERT_EQ((int)blk, rc);
    }

    // check that test chunk is filled with zeroes
    rc = ReadChunk(handle, blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // write test pattern (without commit)
    rc = WritePattern(handle, blk, blk, blk, false);
    ASSERT_EQ((int)blk, rc);

    // read it back an check pattern
    rc = ReadChunk(handle, blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // read same chunk back (should be filled with zeros)
    rc = ReadChunk(handle, blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardWriteMany) {
    int rc;
    file_handle_t handle1;
    file_handle_t handle2;
    size_t blk = 2048;
    size_t exp_len1 = 32 * 1024;
    size_t exp_len2 = 31 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname1 = "test_transact_discard_write_file1";
    const char* fname2 = "test_transact_discard_write_file2";

    // open create truncate (with commit)
    rc = storage_open_file(
            ss, &handle1, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open create truncate (with commit)
    rc = storage_open_file(
            ss, &handle2, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // file1: fill file with pattern (without commit)
    rc = WritePattern(handle1, 0, exp_len1, blk, false);
    ASSERT_EQ((int)exp_len1, rc);

    // file2: fill file with pattern (without commit)
    rc = WritePattern(handle2, 0, exp_len2, blk, false);
    ASSERT_EQ((int)exp_len2, rc);

    // check file size, it should be exp_len1
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len1, fsize);

    // check file size, it should be exp_len2
    rc = storage_get_file_size(handle2, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len2, fsize);

    // discard transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // check file size, it should be 0
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // check file size, it should be 0
    rc = storage_get_file_size(handle2, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // check data
    rc = ReadPatternEOF(handle1, 0, blk);
    ASSERT_EQ(0, rc);

    rc = ReadPatternEOF(handle2, 0, blk);
    ASSERT_EQ(0, rc);

    // cleanup
    storage_close_file(handle1);
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_close_file(handle2);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardTruncate) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_discard_truncate";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // close file
    storage_close_file(handle);

    // open truncate file (without commit)
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_TRUNCATE, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // check file size (should be an oruginal size)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardSetSize) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_discard_set_size";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // set file size to half of original (no commit)
    rc = storage_set_file_size(handle, (storage_off_t)exp_len / 2, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 2, fsize);

    // set file size to 1/3 of original (no commit)
    rc = storage_set_file_size(handle, (storage_off_t)exp_len / 3, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 3, fsize);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // check file size (should be an original size)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardDelete) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_discard_delete";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // close it
    storage_close_file(handle);

    // delete file (without commit)
    rc = storage_delete_file(ss, fname, 0);
    ASSERT_EQ(0, rc);

    // try to open it (should fail)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // try to open it
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // check file size (should be an original size)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactDiscardCreate) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_transact_discard_create_excl";

    // delete test file just in case
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

    // create file (without commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE, 0);
    ASSERT_EQ(0, rc);

    // close it
    storage_close_file(handle);

    // open it again without create and without commit (expect success)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // close it
    storage_close_file(handle);

    // abort current transaction
    rc = storage_end_transaction(ss, false);
    ASSERT_EQ(0, rc);

    // open it again without create without commit (expect not found)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // cleanup
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitWrites) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_commit_writes";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open the same file in aux session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // check file size, it should be 0
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // write data in primary session (without commit)
    rc = WritePattern(handle, 0, exp_len / 2, blk, false);
    ASSERT_EQ((int)exp_len / 2, rc);

    // write more data in primary (without commit)
    rc = WritePattern(handle, exp_len / 2, exp_len / 2, blk, false);
    ASSERT_EQ((int)exp_len / 2, rc);

    // check file size in aux session, it should still be 0
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check file size of aux session, should fail
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(ERR_BUSY, rc);

    // abort transaction in aux session to recover
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // check file size in aux session, it should be exp_len
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // check file size in primary session, it should be exp_len
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // check data in primary session
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ((int)exp_len, rc);

    // check data in aux session
    rc = ReadPatternEOF(handle_aux, 0, blk);
    ASSERT_EQ((int)exp_len, rc);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitWrites2) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_commit_writes2";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open the same file in separate session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // discard transaction in aux_session
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // Fill with zeroes (with commit)
    for (uint32_t i = 0; i < 8; i++) {
        rc = WriteZeroChunk(handle, i * blk, blk, true);
        ASSERT_EQ((int)blk, rc);
    }

    // check that test chunks is filled with zeroes
    rc = ReadChunk(handle, blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    rc = ReadChunk(handle, 2 * blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // write test pattern (without commit)
    rc = WritePattern(handle, blk, blk, blk, false);
    ASSERT_EQ((int)blk, rc);

    // write test pattern (without commit)
    rc = WritePattern(handle, 2 * blk, blk, blk, false);
    ASSERT_EQ((int)blk, rc);

    // read it back and check pattern
    rc = ReadChunk(handle, blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    rc = ReadChunk(handle, 2 * blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    // In aux session it still should be empty
    rc = ReadChunk(handle_aux, blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    rc = ReadChunk(handle_aux, 2 * blk, blk, blk, 0, 0);
    ASSERT_EQ((int)blk, rc);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // read same chunk back in primary session
    rc = ReadChunk(handle, blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    rc = ReadChunk(handle, 2 * blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    // read same chunk back in aux session
    rc = ReadChunk(handle_aux, blk, blk, 0, blk, 0);
    ASSERT_EQ(ERR_BUSY, rc);

    rc = ReadChunk(handle_aux, 2 * blk, blk, 0, blk, 0);
    ASSERT_EQ(ERR_BUSY, rc);

    // abort transaction in aux session
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // read same chunk again in aux session
    rc = ReadChunk(handle_aux, blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    rc = ReadChunk(handle_aux, 2 * blk, blk, 0, blk, 0);
    ASSERT_EQ((int)blk, rc);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitSetSize) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_commit_set_size";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open the same file in separate session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // same in aux session
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // set file size to half of original (no commit)
    rc = storage_set_file_size(handle, (storage_off_t)exp_len / 2, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 2, fsize);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // set file size to 1/3 of original (no commit)
    rc = storage_set_file_size(handle, (storage_off_t)exp_len / 3, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 3, fsize);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check file size (should be 1/3 of an original size)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 3, fsize);

    // check file size from aux session
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(ERR_BUSY, rc);

    // abort transaction
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // check again
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 3, fsize);

    // write data, increasing file size to exp_len (no commit)
    rc = WritePattern(handle, 0, exp_len, blk, false);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // abort aux transaction
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // check file size from aux session
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 3, fsize);

    // set file size without actually changing size, but ask to commit
    rc = storage_set_file_size(handle, (storage_off_t)exp_len,
                               STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // abort aux transaction
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // check file size from aux session
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitDelete) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    const char* fname = "test_transact_commit_delete";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // close it
    storage_close_file(handle);

    // open the same file in separate session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);
    storage_close_file(handle_aux);

    // delete file (without commit)
    rc = storage_delete_file(ss, fname, 0);
    ASSERT_EQ(0, rc);

    // try to open it (should fail)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // open the same file in separate session (should be fine)
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);
    storage_close_file(handle_aux);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // try to open it (still fails)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // open the same file in separate session (should fail)
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

test_abort:;
}

TEST_F(StorageTest, TransactCommitTruncate) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_commit_truncate";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // close file
    storage_close_file(handle);

    // check from different session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // open truncate file (without commit)
    rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_TRUNCATE, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check file size (should be 0)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // check file size in aux session (should be ERR_BUSY)
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(ERR_BUSY, rc);

    // abort transaction in aux session
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // check again
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitCreate) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_commit_create";

    // delete test file just in case
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

    // check from different session
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // create file (without commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE, 0);
    ASSERT_EQ(0, rc);

    // check file size
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // close file
    storage_close_file(handle);

    // check from aux session (should fail)
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check open from normal session
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // check open from aux session (should succeed)
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitCreateMany) {
    int rc;
    file_handle_t handle1;
    file_handle_t handle2;
    file_handle_t handle1_aux;
    file_handle_t handle2_aux;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname1 = "test_transact_commit_create1";
    const char* fname2 = "test_transact_commit_create2";

    // delete test file just in case
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);

    // create file (without commit)
    rc = storage_open_file(
            ss, &handle1, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE, 0);
    ASSERT_EQ(0, rc);

    // create file (without commit)
    rc = storage_open_file(
            ss, &handle2, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE, 0);
    ASSERT_EQ(0, rc);

    // check file sizes
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // close files
    storage_close_file(handle1);
    storage_close_file(handle2);

    rc = storage_open_file(ss_aux, &handle1_aux, fname1, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    rc = storage_open_file(ss_aux, &handle2_aux, fname2, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // open from primary session
    rc = storage_open_file(ss, &handle1, fname1, 0, 0);
    ASSERT_EQ(0, rc);

    rc = storage_open_file(ss, &handle2, fname2, 0, 0);
    ASSERT_EQ(0, rc);

    // open from aux session
    rc = storage_open_file(ss_aux, &handle1_aux, fname1, 0, 0);
    ASSERT_EQ(0, rc);

    rc = storage_open_file(ss_aux, &handle2_aux, fname2, 0, 0);
    ASSERT_EQ(0, rc);

    // cleanup
    storage_close_file(handle1);
    storage_close_file(handle1_aux);
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_close_file(handle2);
    storage_close_file(handle2_aux);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitWriteMany) {
    int rc;
    file_handle_t handle1;
    file_handle_t handle2;
    file_handle_t handle1_aux;
    file_handle_t handle2_aux;
    size_t blk = 2048;
    size_t exp_len1 = 32 * 1024;
    size_t exp_len2 = 31 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname1 = "test_transact_commit_write_file1";
    const char* fname2 = "test_transact_commit_write_file2";

    // open create truncate (with commit)
    rc = storage_open_file(
            ss, &handle1, fname1,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open create truncate (with commit)
    rc = storage_open_file(
            ss, &handle2, fname2,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // open same files from aux session
    rc = storage_open_file(ss_aux, &handle1_aux, fname1, 0, 0);
    ASSERT_EQ(0, rc);

    rc = storage_open_file(ss_aux, &handle2_aux, fname2, 0, 0);
    ASSERT_EQ(0, rc);

    // file1: fill file with pattern (without commit)
    rc = WritePattern(handle1, 0, exp_len1, blk, false);
    ASSERT_EQ((int)exp_len1, rc);

    // file2: fill file with pattern (without commit)
    rc = WritePattern(handle2, 0, exp_len2, blk, false);
    ASSERT_EQ((int)exp_len2, rc);

    // check file size, it should be exp_len1
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len1, fsize);

    // check file size, it should be exp_len2
    rc = storage_get_file_size(handle2, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len2, fsize);

    // check file sizes from aux session (should be 0)
    rc = storage_get_file_size(handle1_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    rc = storage_get_file_size(handle2_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)0, fsize);

    // commit transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check file size, it should be exp_len1
    rc = storage_get_file_size(handle1, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len1, fsize);

    // check file size, it should be exp_len2
    rc = storage_get_file_size(handle2, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len2, fsize);

    // from aux session
    rc = storage_get_file_size(handle1_aux, &fsize);
    ASSERT_EQ(ERR_BUSY, rc);

    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    rc = storage_get_file_size(handle1_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len1, fsize);

    rc = storage_get_file_size(handle2_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len2, fsize);

    // check data
    rc = ReadPatternEOF(handle1, 0, blk);
    ASSERT_EQ((int)exp_len1, rc);

    rc = ReadPatternEOF(handle2, 0, blk);
    ASSERT_EQ((int)exp_len2, rc);

    rc = ReadPatternEOF(handle1_aux, 0, blk);
    ASSERT_EQ((int)exp_len1, rc);

    rc = ReadPatternEOF(handle2_aux, 0, blk);
    ASSERT_EQ((int)exp_len2, rc);

    // cleanup
    storage_close_file(handle1);
    storage_close_file(handle1_aux);
    storage_delete_file(ss, fname1, STORAGE_OP_COMPLETE);
    storage_close_file(handle2);
    storage_close_file(handle2_aux);
    storage_delete_file(ss, fname2, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactCommitDeleteCreate) {
    int rc;
    file_handle_t handle;
    file_handle_t handle_aux;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_delete_create";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write data (with commit)
    rc = WritePattern(handle, 0, exp_len, blk, true);
    ASSERT_EQ((int)exp_len, rc);

    // close it
    storage_close_file(handle);

    // delete file (without commit)
    rc = storage_delete_file(ss, fname, 0);
    ASSERT_EQ(0, rc);

    // try to open it (should fail)
    rc = storage_open_file(ss, &handle, fname, 0, 0);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // try to open it in aux session (should succeed)
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    // create file with the same name (no commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_CREATE_EXCLUSIVE, 0);
    ASSERT_EQ(0, rc);

    // write half of data (with commit)
    rc = WritePattern(handle, 0, exp_len / 2, blk, true);
    ASSERT_EQ((int)exp_len / 2, rc);

    // check file size (should be half)
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 2, fsize);

    // commit transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check data
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ((int)exp_len / 2, rc);

    // check from aux session
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // abort trunsaction
    rc = storage_end_transaction(ss_aux, false);
    ASSERT_EQ(0, rc);

    // and try again
    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // close file and reopen it again
    storage_close_file(handle_aux);
    rc = storage_open_file(ss_aux, &handle_aux, fname, 0, 0);
    ASSERT_EQ(0, rc);

    rc = storage_get_file_size(handle_aux, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len / 2, fsize);

    rc = ReadPatternEOF(handle_aux, 0, blk);
    ASSERT_EQ((int)exp_len / 2, rc);

    // cleanup
    storage_close_file(handle);
    storage_close_file(handle_aux);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactRewriteExistingTruncate) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    const char* fname = "test_transact_rewrite_existing_truncate";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // close it
    storage_close_file(handle);

    // up
    for (uint32_t i = 1; i < 32; i++) {
        // open truncate (no commit)
        rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_TRUNCATE,
                               0);
        ASSERT_EQ(0, rc);

        // write data (with commit)
        rc = WritePattern(handle, 0, i * blk, blk, true);
        ASSERT_EQ((int)(i * blk), rc);

        // close
        storage_close_file(handle);
    }

    // down
    for (uint32_t i = 1; i < 32; i++) {
        // open truncate (no commit)
        rc = storage_open_file(ss, &handle, fname, STORAGE_FILE_OPEN_TRUNCATE,
                               0);
        ASSERT_EQ(0, rc);

        // write data (with commit)
        rc = WritePattern(handle, 0, (32 - i) * blk, blk, true);
        ASSERT_EQ((int)((32 - i) * blk), rc);

        // close
        storage_close_file(handle);
    }

    // cleanup
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactRewriteExistingSetSize) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    const char* fname = "test_transact_rewrite_existing_set_size";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // close it
    storage_close_file(handle);

    // up
    for (unsigned int i = 1; i < 32; i++) {
        // open truncate (no commit)
        rc = storage_open_file(ss, &handle, fname, 0, 0);
        ASSERT_EQ(0, rc);

        // write data (with commit)
        rc = WritePattern(handle, 0, i * blk, blk, false);
        ASSERT_EQ((int)(i * blk), rc);

        // update size (with commit)
        rc = storage_set_file_size(handle, i * blk, STORAGE_OP_COMPLETE);
        ASSERT_EQ(0, rc);

        // close
        storage_close_file(handle);
    }

    // down
    for (unsigned int i = 1; i < 32; i++) {
        // open trancate (no commit)
        rc = storage_open_file(ss, &handle, fname, 0, 0);
        ASSERT_EQ(0, rc);

        // write data (with commit)
        rc = WritePattern(handle, 0, (32 - i) * blk, blk, false);
        ASSERT_EQ((int)((32 - i) * blk), rc);

        // update size (with commit)
        rc = storage_set_file_size(handle, (32 - i) * blk, STORAGE_OP_COMPLETE);
        ASSERT_EQ(0, rc);

        // close
        storage_close_file(handle);
    }

    // cleanup
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

TEST_F(StorageTest, TransactResumeAfterNonFatalError) {
    int rc;
    file_handle_t handle;
    file_handle_t handle1;
    size_t blk = 2048;
    size_t exp_len = 32 * 1024;
    storage_off_t fsize = (storage_off_t)(-1);
    const char* fname = "test_transact_resume_writes";

    // open create truncate file (with commit)
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE);
    ASSERT_EQ(0, rc);

    // write (without commit)
    rc = WritePattern(handle, 0, exp_len / 2, blk, false);
    ASSERT_EQ((int)exp_len / 2, rc);

    // issue some commands that should fail with non-fatal errors

    // read past end of file
    uint32_t val = 0xDEADBEEF;
    rc = storage_read(handle, exp_len / 2 + 1, &val, sizeof(val));
    ASSERT_EQ(ERR_NOT_VALID, rc);

    // open non existing file
    rc = storage_open_file(ss, &handle1, "foo", STORAGE_FILE_OPEN_TRUNCATE,
                           STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // delete non-existing file
    rc = storage_delete_file(ss, "foo", STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    // then resume writinga (without commit)
    rc = WritePattern(handle, exp_len / 2, exp_len / 2, blk, false);
    ASSERT_EQ((int)exp_len / 2, rc);

    // commit current transaction
    rc = storage_end_transaction(ss, true);
    ASSERT_EQ(0, rc);

    // check file size, it should be exp_len
    rc = storage_get_file_size(handle, &fsize);
    ASSERT_EQ(0, rc);
    ASSERT_EQ((storage_off_t)exp_len, fsize);

    // check data
    rc = ReadPatternEOF(handle, 0, blk);
    ASSERT_EQ((int)exp_len, rc);

    // cleanup
    storage_close_file(handle);
    storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);

test_abort:;
}

/*
 * Test that every API that should allow checkpointing does (when provisioning
 * is enabled)
 */
TEST_F(StorageTest, ENABLED_ON_EMULATOR_ONLY(CheckpointApis)) {
    int rc;
    file_handle_t handle;
    size_t blk = 2048;
    size_t len = 32 * blk;
    const char* fname = "test_checkpoint_create";
    const char* new_fname = "test_checkpoint_move";

    if (!system_state_provisioning_allowed()) {
        trusty_unittest_printf(
                "[  SKIPPED ] CheckpointApis - Provisioning is not allowed\n");
        return;
    }

    /* We don't allow OP_CHECKPOINT without STORAGE_OP_COMPLETE */
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    /* open create truncate file (with checkpoint) */
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(0, rc);

    /* move the file and checkpoint */
    rc = storage_move_file(
            ss, handle, fname, new_fname,
            STORAGE_FILE_MOVE_CREATE | STORAGE_FILE_MOVE_OPEN_FILE,
            STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(0, rc);

    /* write to the file and checkpoint */
    rc = WritePatternExt(handle, 0, len, blk,
                         STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ((int)len, rc);

    rc = ReadPattern(handle, 0, len, blk);
    ASSERT_EQ((int)len, rc);

    /* cleanup */
    storage_close_file(handle);

    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    rc = storage_delete_file(ss, new_fname,
                             STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(0, rc);

test_abort:;
}

TEST_F(StorageTest,
       ENABLED_ON_EMULATOR_ONLY(CheckpointRequiresProvisioningAllowed)) {
    int rc;
    file_handle_t handle;
    const char* fname = "test_checkpoint_create";
    const char* new_fname = "test_checkpoint_move";

    if (system_state_provisioning_allowed()) {
        trusty_unittest_printf(
                "[  SKIPPED ] CheckpointRequiresProvisioningAllowed - "
                "Provisioning is allowed\n");
        return;
    }

    /* We don't allow OP_CHECKPOINT without STORAGE_OP_COMPLETE */
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(ERR_NOT_VALID, rc);

    /* open create truncate file (with checkpoint) */
    rc = storage_open_file(
            ss, &handle, fname,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE,
            STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(ERR_NOT_ALLOWED, rc);

    /* move the file and checkpoint */
    rc = storage_move_file(
            ss, handle, fname, new_fname,
            STORAGE_FILE_MOVE_CREATE | STORAGE_FILE_MOVE_OPEN_FILE,
            STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(ERR_NOT_ALLOWED, rc);

    rc = storage_delete_file(ss, fname, STORAGE_OP_COMPLETE);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

    rc = storage_delete_file(ss, new_fname,
                             STORAGE_OP_COMPLETE | STORAGE_OP_CHECKPOINT);
    ASSERT_EQ(ERR_NOT_ALLOWED, rc);

test_abort:;
}
#endif

#define RUN_MODE_ALL NULL
#define RUN_MODE_INIT_NO_COMMIT_SMALL "StorageInitNoCommitSmallTest"
#define RUN_MODE_INIT_NO_COMMIT_LARGE "StorageInitNoCommitLargeTest"
#define RUN_MODE_INIT_NO_COMMIT_CLEANUP "StorageInitNoCommitClenupTest"
#define RUN_MODE_INIT "StorageInitTest"
#define RUN_MODE_CHECK "StorageCheckTest"
#define RUN_MODE_CLEAN "StorageCleanTest"

static bool run_all_tests(const char* port, const char* suite) {
    int rc;
    storage_session_t session;

    TLOGI("SS-unittest: %s: waiting for server\n", port);
    do {
        rc = storage_open_session(&session, port);
        if (rc < 0) {
            TLOGE("failed (%d) to connect to storage server - retrying\n", rc);
#ifndef STORAGE_FAKE
            trusty_nanosleep(0, 0, 1000000);
#endif
        }

    } while (rc < 0);
    storage_close_session(session);

    storage_test_client_port = port;

    return RUN_ALL_SUITE_TESTS(suite);
}

#ifdef STORAGE_FAKE

int main(void) {
    bool passed = run_all_tests(STORAGE_CLIENT_TD_PORT, RUN_MODE_ALL);
    //  run_all_tests(STORAGE_CLIENT_TDEA_PORT, RUN_MODE_ALL);
    //  run_all_tests(STORAGE_CLIENT_TP_PORT, RUN_MODE_ALL);
    return passed ? 0 : 1;
}

#else

struct storage_unittest {
    struct unittest unittest;
    const char* port;
    const char* run_mode;
};

static bool run_test(struct unittest* test) {
    struct storage_unittest* storage_test =
            containerof(test, struct storage_unittest, unittest);
    return run_all_tests(storage_test->port, storage_test->run_mode);
}

#define PORT_BASE "com.android.storage-unittest."

#define DEFINE_STORAGE_UNIT_TEST(fs, fs_name, run_mode_val, run_mode_name) \
    {                                                                      \
        .unittest =                                                        \
                {                                                          \
                        .port_name = PORT_BASE fs_name run_mode_name,      \
                        .run_test = run_test,                              \
                },                                                         \
        .port = (fs), .run_mode = (run_mode_val),                          \
    }

#define DEFINE_STORAGE_UNIT_TESTS_FS(fs, fs_name)                              \
    DEFINE_STORAGE_UNIT_TEST((fs), fs_name, RUN_MODE_ALL, ""),                 \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name,                            \
                                     RUN_MODE_INIT_NO_COMMIT_SMALL,            \
                                     ".initnocommitsmall"),                    \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name,                            \
                                     RUN_MODE_INIT_NO_COMMIT_LARGE,            \
                                     ".initnocommitlarge"),                    \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name,                            \
                                     RUN_MODE_INIT_NO_COMMIT_CLEANUP,          \
                                     ".initnocommitcleanup"),                  \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name, RUN_MODE_INIT, ".init"),   \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name, RUN_MODE_CHECK, ".check"), \
            DEFINE_STORAGE_UNIT_TEST((fs), fs_name, RUN_MODE_CLEAN, ".clean")

int main(void) {
    static struct storage_unittest storage_unittests[] = {
            DEFINE_STORAGE_UNIT_TESTS_FS(STORAGE_CLIENT_NSP_PORT, "nsp"),
            DEFINE_STORAGE_UNIT_TESTS_FS(STORAGE_CLIENT_TD_PORT, "td"),
            DEFINE_STORAGE_UNIT_TESTS_FS(STORAGE_CLIENT_TDP_PORT, "tdp"),
            DEFINE_STORAGE_UNIT_TESTS_FS(STORAGE_CLIENT_TDEA_PORT, "tdea"),
            DEFINE_STORAGE_UNIT_TESTS_FS(STORAGE_CLIENT_TP_PORT, "tp"),
    };
    static struct unittest* unittests[countof(storage_unittests)];

    for (size_t i = 0; i < countof(storage_unittests); i++) {
        unittests[i] = &storage_unittests[i].unittest;
    }

    return unittest_main(unittests, countof(unittests));
}

#endif
