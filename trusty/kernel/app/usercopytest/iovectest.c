#include <kernel/usercopy.h>
#include <lib/unittest/unittest.h>

typedef struct iovectest {
    struct vmm_aspace* aspace;
    user_addr_t buffer_addr;
    user_addr_t iovec_addr;
} iovectest_t;

static const char test_pattern[] = "abcdefghijklmnopqrstuvwxyz!";

TEST_F_SETUP(iovectest) {
    _state->aspace = NULL;

    /* Setup a user address space. */
    struct vmm_aspace* aspace = NULL;
    ASSERT_EQ(0, vmm_create_aspace(&aspace, "iovectest", 0));
    _state->aspace = aspace;
    _state->buffer_addr = 8 * PAGE_SIZE;
    _state->iovec_addr = _state->buffer_addr + 256;

    /* Allocate a page of memory. */
    void* user_ptr = (void*)(uintptr_t)_state->buffer_addr;
    ASSERT_EQ(0, vmm_alloc(aspace, "iovectest", PAGE_SIZE, &user_ptr, 0,
                           VMM_FLAG_VALLOC_SPECIFIC, ARCH_MMU_FLAG_PERM_USER));

    vmm_set_active_aspace(aspace);

    /* Write the test pattern into memory. */
    ASSERT_EQ(0, copy_to_user(_state->buffer_addr, test_pattern,
                              sizeof(test_pattern)));

test_abort:;
}

TEST_F_TEARDOWN(iovectest) {
    vmm_set_active_aspace(NULL);
    if (_state->aspace) {
        vmm_free_aspace(_state->aspace);
        _state->aspace = NULL;
    }
}

/* Check the test fixture is OK. */
TEST_F(iovectest, fixture) {
    ASSERT_LT(0, sizeof(test_pattern));

    /* Non-zero clear because last character of the pattern is null. */
    char buf[sizeof(test_pattern)];
    memset(buf, 0xff, sizeof(test_pattern));

    /* Read back the test pattern. */
    int ret = copy_from_user(buf, _state->buffer_addr, sizeof(test_pattern));
    ASSERT_EQ(0, ret);

    /* Make sure it's what we expect. */
    ASSERT_EQ(0, memcmp(test_pattern, buf, sizeof(test_pattern)));

test_abort:;
}

/* Write an iovec with evenly spaced chunks to userspace. */
static int write_chunked_iovec(iovectest_t* _state, int size) {
    struct iovec_user uiov[sizeof(test_pattern)];
    unsigned int index = 0;
    for (unsigned int i = 0; i < sizeof(test_pattern); i += size, index += 1) {
        uiov[index].iov_base = _state->buffer_addr + i;
        int len = sizeof(test_pattern) - i;
        if (size < len) {
            len = size;
        }
        uiov[index].iov_len = len;
    }

    /* Copy into user space. */
    ASSERT_EQ(0,
              copy_to_user(_state->iovec_addr, uiov,
                           sizeof(struct iovec_user) * index),
              "chunk %d", size);

    return index;

test_abort:
    return 0;
}

/* A format string to help understand the exact case the assertion failed. */
#define LOCATION_MESSAGE "chunk size %zu / iov_cnt %d", buffer_chunk, iov_cnt

/* Copy all the data from userspace using a buffer of limited size. */
static void iovectest_readback(iovectest_t* _state,
                               size_t buffer_chunk,
                               int iov_cnt,
                               const void* expected,
                               size_t expected_len) {
    uint8_t buf[sizeof(test_pattern) * 2];
    memset(buf, 0xff, sizeof(buf));

    uint8_t tmp[sizeof(test_pattern) * 2];
    memset(tmp, 0xff, sizeof(tmp));

    /* Check chunk sizes. */
    ASSERT_LE(expected_len, sizeof(buf), LOCATION_MESSAGE);
    ASSERT_LE(0, buffer_chunk, LOCATION_MESSAGE);
    ASSERT_LE(buffer_chunk, sizeof(tmp), LOCATION_MESSAGE);

    /* Read the data a buffer at a time. */
    struct iovec_iter iter = iovec_iter_create(iov_cnt);
    size_t total_bytes = 0;
    while (iovec_iter_has_next(&iter)) {
        int ret = user_iovec_to_membuf_iter(tmp, buffer_chunk,
                                            _state->iovec_addr, &iter);
        /* Check the return value. */
        ASSERT_LE(0, ret, LOCATION_MESSAGE);
        ASSERT_LE(ret, buffer_chunk, LOCATION_MESSAGE);
        /* If there is more data, the buffer should be filled. */
        if (iter.iov_index < iter.iov_cnt) {
            ASSERT_EQ(ret, buffer_chunk, LOCATION_MESSAGE);
        }
        /* Accumulate the result. */
        memcpy(buf + total_bytes, tmp, ret);
        total_bytes += ret;
    }
    /* Did we get the data we expect? */
    ASSERT_EQ(expected_len, total_bytes, LOCATION_MESSAGE);
    ASSERT_EQ(0, memcmp(expected, buf, expected_len), LOCATION_MESSAGE);

test_abort:;
}

/* Test various combinations of iovec size and read buffer size. */
TEST_F(iovectest, varied_chunk_sizes) {
    /* Note the chunk sizes can exceed the size of the payload. */
    for (size_t iovec_chunk = 1; iovec_chunk <= sizeof(test_pattern) + 2;
         iovec_chunk++) {
        for (size_t buffer_chunk = 1; buffer_chunk <= sizeof(test_pattern) + 2;
             buffer_chunk++) {
            int iov_cnt = write_chunked_iovec(_state, iovec_chunk);
            iovectest_readback(_state, buffer_chunk, iov_cnt, test_pattern,
                               sizeof(test_pattern));
        }
    }
}

/* Make sure that zero-length iovecs have no effect. */
TEST_F(iovectest, zerolength) {
    struct iovec_user uiov[] = {
            {
                    .iov_base = _state->buffer_addr + 0,
                    .iov_len = 0,
            },
            {
                    .iov_base = 0,
                    .iov_len = 0,
            },
            {
                    .iov_base = _state->buffer_addr + 0,
                    .iov_len = 3,
            },
            {
                    .iov_base = _state->buffer_addr + 3,
                    .iov_len = 0,
            },
            {
                    .iov_base = 0,
                    .iov_len = 0,
            },
            {
                    .iov_base = _state->buffer_addr + 3,
                    .iov_len = 0,
            },
            {
                    .iov_base = _state->buffer_addr + 3,
                    .iov_len = 25,
            },
            {
                    .iov_base = _state->buffer_addr + 28,
                    .iov_len = 0,
            },
            {
                    .iov_base = 0,
                    .iov_len = 0,
            },
    };
    ASSERT_EQ(0, copy_to_user(_state->iovec_addr, uiov, sizeof(uiov)));

    iovectest_readback(_state, 10, countof(uiov), test_pattern,
                       sizeof(test_pattern));

test_abort:;
}

/* Make sure we can read something other than the exact test pattern. */
TEST_F(iovectest, swap_data) {
    struct iovec_user uiov[] = {
            {
                    .iov_base = _state->buffer_addr + 14,
                    .iov_len = 14,
            },
            {
                    .iov_base = _state->buffer_addr + 0,
                    .iov_len = 14,
            },
    };
    ASSERT_EQ(0, copy_to_user(_state->iovec_addr, uiov, sizeof(uiov)));

    const char expected[] = "opqrstuvwxyz!\0abcdefghijklmn";
    iovectest_readback(_state, 11, countof(uiov), expected, 28);

test_abort:;
}

PORT_TEST(iovectest, "com.android.kernel.iovectest");
