#include <err.h>
#include <kernel/thread.h>
#include <lib/unittest/unittest.h>
#include <malloc.h>
#include <stdatomic.h>

static uintptr_t expected_malloc_alignment(size_t size) {
    /* TODO use ffs? */
    if (size >= 16) {
        return sizeof(void*) * 2;
    } else if (size >= 8) {
        return 8;
    } else if (size >= 4) {
        return 4;
    } else if (size >= 2) {
        return 2;
    } else {
        return 1;
    }
}

TEST(memorytest, malloc_alignment) {
    for (int size = 2; size < 256; size++) {
        const uintptr_t alignment_mask = expected_malloc_alignment(size) - 1;
        void* ptr1 = malloc(size);
        void* ptr2 = malloc(size / 2); /* Try to shake up the alignment. */
        void* ptr3 = malloc(size);

        ASSERT_EQ(0, (uintptr_t)ptr1 & alignment_mask, "size %d / align %ld\n",
                  size, alignment_mask + 1);
        ASSERT_EQ(0, (uintptr_t)ptr3 & alignment_mask, "size %d / align %ld\n",
                  size, alignment_mask + 1);

        free(ptr3);
        free(ptr2);
        free(ptr1);
    }
test_abort:;
}

#define MEMORYTEST_CONCURRENT_ALLOCATION_THREADS 64

struct memorytest_alloc_thread_arg {
    size_t alloc_size;
    int count;
    atomic_int threads_done;
    atomic_int chunks_allocated;
    atomic_bool go;
};

int memorytest_alloc_thread(void* _arg) {
    struct memorytest_alloc_thread_arg* arg = _arg;
    void** ptrs;
    int i;
    int ret;

    ptrs = calloc(arg->count, sizeof(*ptrs));
    if (!ptrs) {
        return ERR_NO_MEMORY;
    }

    /* Busy-wait until control thread says go. */
    while (!atomic_load(&arg->go)) {
    }

    for (i = 0; i < arg->count; i++) {
        ptrs[i] = malloc(arg->alloc_size);
        if (!ptrs[i]) {
            ret = ERR_NO_MEMORY;
            goto err_malloc;
        }
        atomic_fetch_add(&arg->chunks_allocated, 1);
    }
    ret = 0;

err_malloc:
    atomic_fetch_add(&arg->threads_done, 1);
    while (atomic_load(&arg->threads_done) !=
           MEMORYTEST_CONCURRENT_ALLOCATION_THREADS) {
        thread_sleep(10);
    }

    while (i-- > 0) {
        free(ptrs[i]);
    }
    free(ptrs);
    return ret;
}

TEST(memorytest, concurrent_allocation) {
    /*
     * Test concurrent allocation by creating many threads. If this test is
     * as the first test after boot, it will test the behavior while growing
     * the heap. Assuming the heap implemention never shrinks the heap,
     * additional test runs will not exercise this path.
     */
    struct memorytest_alloc_thread_arg thread_arg = {
            .alloc_size = PAGE_SIZE / 4 * 3, /* ~1 page after heap overhead */
            .count = 8,
            .threads_done = 0,
            .chunks_allocated = 0,
            .go = false,
    };
    struct thread* thread[MEMORYTEST_CONCURRENT_ALLOCATION_THREADS];
    for (size_t i = 0; i < countof(thread); i++) {
        thread[i] = thread_create("memorytest", memorytest_alloc_thread,
                                  &thread_arg, HIGH_PRIORITY - 1,
                                  DEFAULT_STACK_SIZE);
        ASSERT_NE(0, thread[i]);
    }
    for (size_t i = 0; i < countof(thread); i++) {
        ASSERT_EQ(0, thread_resume(thread[i]));
    }

    /* Wait for test threads to start and migrate to other cpus */
    thread_sleep(100);
    atomic_store(&thread_arg.go, true);

    for (size_t i = 0; i < countof(thread); i++) {
        int retcode;
        ASSERT_EQ(0, thread_join(thread[i], &retcode, INFINITE_TIME));
        EXPECT_EQ(0, retcode, "Chunks allocated: %d\n",
                  atomic_load(&thread_arg.chunks_allocated));
    }
test_abort:;
}

PORT_TEST(memorytest, "com.android.kernel.memorytest");
