/*
 * Copyright (C) 2015-2016 The Android Open Source Project
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
#include <inttypes.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#ifndef LOCAL_TRACE
#define LOCAL_TRACE TRACE_LEVEL_INIT
#endif
#ifndef LOCAL_TRACE_ERR
#define LOCAL_TRACE_ERR TRACE_LEVEL_INIT
#endif

#include "array.h"
#include "block_allocator.h"
#include "block_cache.h"
#include "block_set.h"
#include "checkpoint.h"
#include "debug.h"
#include "error_reporting.h"
#include "file.h"
#include "fs.h"
#include "transaction.h"

#define SUPER_BLOCK_MAGIC (0x0073797473757274ULL) /* trustys */
#define SUPER_BLOCK_FLAGS_VERSION_MASK (0x3U)
#define SUPER_BLOCK_FLAGS_BLOCK_INDEX_MASK (0x1U)
#define SUPER_BLOCK_FLAGS_EMPTY (0x4U)
#define SUPER_BLOCK_FLAGS_ALTERNATE (0x8U)
#define SUPER_BLOCK_FLAGS_SUPPORTED_MASK (0xfU)
#define SUPER_BLOCK_FS_VERSION (0U)

/**
 * typedef super_block_opt_flags8_t - Optional flags, can be ORed together
 *
 * %SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3
 *   Indicates that the superblock has additional data after flags2 and that
 *   flags3 should be set to the same value as flags
 * %SUPER_BLOCK_OPT_FLAGS_HAS_CHECKPOINT
 *   Indicates that the superblock contains the @checkpoint field
 * %SUPER_BLOCK_OPT_FLAGS_NEEDS_FULL_SCAN
 *   An error was detected in this file system, a full scan and possibly repair
 *   should be initiated on the next mount. Reset after scanning.
 */
typedef uint8_t super_block_opt_flags8_t;
#define SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3 (0x1U)
#define SUPER_BLOCK_OPT_FLAGS_HAS_CHECKPOINT (0x2U)
#define SUPER_BLOCK_OPT_FLAGS_NEEDS_FULL_SCAN (0x4U)

/**
 * typedef super_block_required_flags16_t - Required FS flags, can be ORed
 *                                          together
 *
 * These flags are required to be supported by the current implementation; if
 * any unrecognized flag bits are set the file system must not be mounted.
 * Versions of the storage service prior to the addition of the @required_flags
 * field will interpret non-zero flags as a high @fs_version and will refuse to
 * mount the file-system.
 *
 * %SUPER_BLOCK_REQUIRED_FLAGS_MAIN_REPAIRED
 *   Indicates that the main (i.e. flags does not contain
 *   %SUPER_BLOCK_FLAGS_ALTERNATE) file system has been repaired in a manner
 *   that effectively resulted in rollback to a previous state since it was last
 *   cleared. This flag is required to be supported, if set, so that we do not
 *   discard a repaired state by running an older version of the storage
 *   service. This flag is cleared when the main file system is cleared, and
 *   therefore only tracks repairs since the file system was last cleared.
 * %SUPER_BLOCK_REQUIRED_FLAGS_MASK
 *   Mask of bits that are understood by the current storage implementation. If
 *   any bits of this field are set outside of this mask, do not mount the file
 *   system.
 */
typedef uint16_t super_block_required_flags16_t;
#define SUPER_BLOCK_REQUIRED_FLAGS_MAIN_REPAIRED (0x1U)
#define SUPER_BLOCK_REQUIRED_FLAGS_MASK \
    (SUPER_BLOCK_REQUIRED_FLAGS_MAIN_REPAIRED)

/**
 * struct super_block - On-disk root block for file system state
 * @iv:             Initial value used for encrypt/decrypt.
 * @magic:          SUPER_BLOCK_MAGIC.
 * @flags:          Version in bottom two bits, other bits are reserved.
 * @fs_version:     Required file system version. If greater than
 *                  %SUPER_BLOCK_FS_VERSION, do not mount or overwrite
 *                  filesystem.
 * @required_flags: Required file system flags. To mount this file system, any
 *                  non-zero flag bits set must be supported by the storage
 *                  implementation.
 * @block_size:     Block size of file system.
 * @block_num_size: Number of bytes used to store block numbers.
 * @mac_size:       number of bytes used to store mac values.
 * @opt_flags:      Optional flags, any of &typedef super_block_opt_flags8_t
 *                  ORed together.
 * @res2:           Reserved for future use. Write 0, read ignore.
 * @block_count:    Size of file system.
 * @free:           Block and mac of free set root node.
 * @free_count:     Currently unused.
 * @files:          Block and mac of files tree root node.
 * @res3:           Reserved for future use. Write 0, read ignore.
 * @flags2:         Copy of @flags. Allows storing the super-block in a device
 *                  that does not support an atomic write of the entire
 *                  super-block.
 * @backup:         Backup of previous super-block, used to support an alternate
 *                  backing store. 0 if no backup has ever been written. Once a
 *                  backup exists, it will only ever be swapped, not cleared.
 * @checkpoint:     Block and mac of checkpoint metadata block. 0 if a
 *                  checkpoint does not exist.
 * @res4:           Reserved for future use. Write 0, read ignore.
 * @flags3:         Copy of @flags. Allows storing the super-block in a device
 *                  that does not support an atomic write of the entire
 *                  super-block. If SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3 is not set,
 *                  @flags3 is not checked and fields after @flags2 are ignored.
 *
 * Block numbers and macs in @free and @files are packed as indicated by
 * @block_num_size and @mac_size, but unlike other on-disk data, the size of the
 * whole field is always the full 24 bytes needed for a 8 byte block number and
 * 16 byte mac This allows the @flags2 and @flags3 to be validated before
 * knowing @block_num_size and @mac_size.
 */
struct super_block {
    struct iv iv;
    uint64_t magic;
    uint32_t flags;
    uint16_t fs_version;
    super_block_required_flags16_t required_flags;
    uint32_t block_size;
    uint8_t block_num_size;
    uint8_t mac_size;
    super_block_opt_flags8_t opt_flags;
    uint8_t res2;
    data_block_t block_count;
    struct block_mac free;
    data_block_t free_count;
    struct block_mac files;
    uint32_t res3[5];
    uint32_t flags2;
    struct super_block_backup backup;
    struct block_mac checkpoint;
    uint32_t res4[6];
    uint32_t flags3;
};
STATIC_ASSERT(offsetof(struct super_block, flags2) == 124);
STATIC_ASSERT(offsetof(struct super_block, flags3) == 252);
STATIC_ASSERT(sizeof(struct super_block) == 256);

/*
 * We rely on these offsets in future_fs_version_test and
 * unknown_required_flags_test in the storage_block_test to test that we will
 * not mount or modify a super block with unknown version or fs flags.
 */
STATIC_ASSERT(offsetof(struct super_block, fs_version) == 28);
STATIC_ASSERT(offsetof(struct super_block, required_flags) == 30);

/* block_device_tipc.c ensures that we have at least 256 bytes in RPMB blocks */
STATIC_ASSERT(sizeof(struct super_block) <= 256);

static struct list_node fs_list = LIST_INITIAL_VALUE(fs_list);

/**
 * update_super_block_internal - Generate and write superblock
 * @tr:         Transaction object.
 * @free:       New free root.
 * @files:      New files root.
 * @checkpoint: New checkpoint metadata block.
 * @pinned:     New block should not be reused in the block cache until
 *              it is successfully written.
 *
 * Return: %true if super block was updated (in cache), %false if transaction
 * failed before super block was updated.
 */
static bool update_super_block_internal(struct transaction* tr,
                                        const struct block_mac* free,
                                        const struct block_mac* files,
                                        const struct block_mac* checkpoint,
                                        bool pinned) {
    struct super_block* super_rw;
    struct obj_ref super_ref = OBJ_REF_INITIAL_VALUE(super_ref);
    unsigned int ver;
    unsigned int index;
    super_block_required_flags16_t required_flags = 0;
    uint32_t flags;
    uint32_t block_size = tr->fs->super_dev->block_size;
    super_block_opt_flags8_t opt_flags = SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3 |
                                         SUPER_BLOCK_OPT_FLAGS_HAS_CHECKPOINT;

    if (!tr->fs->writable) {
        pr_err("Attempting to write superblock for read-only filesystem\n");
        if (!tr->failed) {
            transaction_fail(tr);
        }
        return false;
    }

    assert(block_size >= sizeof(struct super_block));
    assert(tr->fs->initial_super_block_tr == NULL ||
           tr->fs->initial_super_block_tr == tr);

    ver = (tr->fs->super_block_version + 1) & SUPER_BLOCK_FLAGS_VERSION_MASK;
    index = ver & SUPER_BLOCK_FLAGS_BLOCK_INDEX_MASK;
    flags = ver;
    if (!free && !files) {
        /*
         * If the free and files trees are not provided, the filesystem is in
         * the initial empty state.
         */
        flags |= SUPER_BLOCK_FLAGS_EMPTY;
    } else {
        /* Non-empty filesystems must have both trees (with root node blocks) */
        assert(free);
        assert(files);
    }
    if (tr->fs->alternate_data) {
        flags |= SUPER_BLOCK_FLAGS_ALTERNATE;
    }
    if (tr->repaired || tr->fs->main_repaired) {
        /*
         * We don't track repairs in alternate data mode, so we shouldn't do
         * them - ensure the transaction does not include a repair if we are in
         * alternate state. The FS flag is used to persist the state for the
         * main FS.
         */
        assert(!tr->repaired || !tr->fs->alternate_data);
        required_flags |= SUPER_BLOCK_REQUIRED_FLAGS_MAIN_REPAIRED;
        /*
         * TODO: We would like to track the number of repairs in addition to the
         * current repair state. This may be up to three different counters: 1)
         * the number of times this fs has been repaired over the device
         * lifetime to report in metrics, 2) the number of repairs since last
         * clear, and 3) the overall fs generation count (number of device
         * lifetime repairs+clears). 2) and 3) would primarily be useful if we
         * expose them to clients via a new query API, while 1) would mostly be
         * for device metrics. We can implement some or all of these counters
         * when we add an API that consumes them.
         */
    }
    if (tr->fs->needs_full_scan) {
        opt_flags |= SUPER_BLOCK_OPT_FLAGS_NEEDS_FULL_SCAN;
    }

    pr_write("write super block %" PRIu64 ", ver %d\n",
             tr->fs->super_block[index], ver);

    super_rw = block_get_cleared_super(tr, tr->fs->super_block[index],
                                       &super_ref, pinned);
    if (tr->failed) {
        block_put_dirty_discard(super_rw, &super_ref);
        return false;
    }
    super_rw->magic = SUPER_BLOCK_MAGIC;
    super_rw->flags = flags;
    /* TODO: keep existing fs version when possible */
    super_rw->fs_version = SUPER_BLOCK_FS_VERSION;
    super_rw->required_flags = required_flags;
    super_rw->block_size = tr->fs->dev->block_size;
    super_rw->block_num_size = tr->fs->block_num_size;
    super_rw->mac_size = tr->fs->mac_size;
    super_rw->opt_flags = opt_flags;
    super_rw->block_count = tr->fs->dev->block_count;
    if (free) {
        super_rw->free = *free;
    }
    super_rw->free_count = 0; /* TODO: remove or update */
    if (files) {
        super_rw->files = *files;
    }
    if (checkpoint) {
        super_rw->checkpoint = *checkpoint;
    }
    super_rw->flags2 = flags;
    super_rw->backup = tr->fs->backup;
    super_rw->flags3 = flags;
    tr->fs->written_super_block_version = ver;

    block_put_dirty_no_mac(super_rw, &super_ref, tr->fs->allow_tampering);

    return true;
}

/**
 * update_super_block - Generate and write superblock
 * @tr:         Transaction object.
 * @free:       New free root.
 * @files:      New files root.
 * @checkpoint: New checkpoint metadata block.
 *
 * Return: %true if super block was updated (in cache), %false if transaction
 * failed before super block was updated.
 */
bool update_super_block(struct transaction* tr,
                        const struct block_mac* free,
                        const struct block_mac* files,
                        const struct block_mac* checkpoint) {
    return update_super_block_internal(tr, free, files, checkpoint, false);
}

/**
 * write_initial_super_block - Write initial superblock to internal transaction
 * @fs:         File system state object.
 *
 * When needed, this must be called before creating any other transactions on
 * this filesystem so we don't fill up the cache with entries that can't be
 * flushed to make room for this block.
 *
 * Return: %true if the initial empty superblock was successfully written to the
 * cache, or %false otherwise.
 */
static bool write_initial_super_block(struct fs* fs) {
    struct transaction* tr;
    tr = calloc(1, sizeof(*tr));
    if (!tr) {
        return false;
    }
    fs->initial_super_block_tr = tr;

    transaction_init(tr, fs, true);
    return update_super_block_internal(tr, NULL, NULL, NULL, true);
}

/**
 * write_current_super_block - Write current superblock to internal transaction
 * @fs:           File system state object.
 * @reinitialize: Allow the special transaction to be reinitialized if it has
 *                failed
 *
 * Write the current state of the super block to an internal transaction that
 * will be written before any other block. This can be used to re-sync the
 * in-memory fs-state with the on-disk state after detecting a write failure
 * where no longer know the on-disk super block state.
 *
 * @fs must be writable when calling this function.
 */
void write_current_super_block(struct fs* fs, bool reinitialize) {
    bool super_block_updated;
    struct transaction* tr;

    assert(fs->writable);

    if (fs->initial_super_block_tr) {
        /*
         * If initial_super_block_tr is already pending and not failed there is
         * no need to allocate a new one so return early.
         *
         * If the special transaction has failed, we need to re-initialize it so
         * that we can attempt to recover to a good state.
         *
         * We are only allowed to reinitialze if the @reinitialize parameter is
         * true. We don't want to allow reinitialization while cleaning blocks
         * (i.e. via fs_unknown_super_block_state_all()), as this would reset
         * the special transaction to non-failed state and create a situation
         * where transaction_initial_super_block_complete() cannot know if it
         * successfully flushed the special transaction to disk. Therefore we
         * only allow transaction_initial_super_block_complete() to reinitialize
         * a failed special transaction after it attempts and fails to write the
         * block to disk.
         *
         * Since we pin special superblock entries in the block cache and
         * therefore cannot evict them with normal transactions,
         * transaction_initial_super_block_complete() is the only place we can
         * attempt a special transaction write, and if it fails the transaction
         * is immediately reinitialized. Therefore we should only ever be in a
         * failed state if reinitialize is true (i.e. we are being called from
         * transaction_initial_super_block_complete()).
         */

        assert(reinitialize || !fs->initial_super_block_tr->failed);
        if (!fs->initial_super_block_tr->failed || !reinitialize) {
            return;
        }

        tr = fs->initial_super_block_tr;
        transaction_activate(tr);
    } else {
        tr = calloc(1, sizeof(*tr));
        if (!tr) {
            /* Not safe to proceed. TODO: add flag to defer this allocation? */
            abort();
        }
        transaction_init(tr, fs, true);
        fs->initial_super_block_tr = tr;
    }

    /*
     * Until the filesystem contains committed data, fs->free.block_tree.root
     * will be zero, i.e. an invalid block mac. fs->free.block_tree.root is only
     * updated in transaction_complete() after successfully writing a new
     * superblock. If the filesystem is empty, we need to emit a cleared
     * superblock with a special flag to prevent the superblock state from
     * getting out of sync with the filesystem data if a reboot occurrs before
     * committing a superblock with data.
     *
     * We can't use fs->files.root here because it may be invalid if there are
     * no files in the filesystem. If the free node is zero, then the files node
     * must be as well, so we assert this.
     */
    bool fs_is_cleared = !block_mac_valid(tr, &fs->free.block_tree.root);
    if (fs_is_cleared) {
        assert(!block_mac_valid(tr, &fs->files.root));
        super_block_updated =
                update_super_block_internal(tr, NULL, NULL, NULL, true);
    } else {
        super_block_updated = update_super_block_internal(
                tr, &fs->free.block_tree.root, &fs->files.root, &fs->checkpoint,
                true);
    }
    if (!super_block_updated) {
        /* Not safe to proceed. TODO: add flag to try again? */
        fprintf(stderr,
                "Could not create pending write for current superblock state. "
                "Not safe to proceed.\n");
        abort();
    }
}

/**
 * fs_mark_scan_required - Require a full scan for invalid blocks the next time
 *                         this FS is mounted
 * @fs:             File system object
 *
 * Marks the file system to require a full scan (and possibly repair) on the
 * next mount. If @fs is writable, this function immediately writes a new copy
 * of the current super block, so the flag will persist even with no further
 * writes to the file system.
 */
void fs_mark_scan_required(struct fs* fs) {
    fs->needs_full_scan = true;
    if (!fs->writable) {
        /* We can't write back the superblock until this FS is writable. */
        return;
    }
    write_current_super_block(fs, false);
    assert(fs->initial_super_block_tr);
    transaction_initial_super_block_complete(fs->initial_super_block_tr);
}

/**
 * super_block_valid - Check if superblock is valid
 * @dev:        Block device that supoer block was read from.
 * @super:      Super block data.
 *
 * Return: %true if @super is valid for @dev, %false otherwise.
 */
static bool super_block_valid(const struct block_device* dev,
                              const struct super_block* super) {
    if (super->magic != SUPER_BLOCK_MAGIC) {
        pr_init("bad magic, 0x%" PRIx64 "\n", super->magic);
        return false;
    }
    if (super->flags != super->flags2) {
        pr_warn("flags, 0x%x, does not match flags2, 0x%x\n", super->flags,
                super->flags2);
        return false;
    }
    if ((super->opt_flags & SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3) &&
        super->flags != super->flags3) {
        pr_warn("flags, 0x%x, does not match flags3, 0x%x\n", super->flags,
                super->flags3);
        return false;
    }
    if (super->fs_version > SUPER_BLOCK_FS_VERSION) {
        pr_warn("super block is from the future: 0x%x\n", super->fs_version);
        return true;
    }
    if (super->flags & ~SUPER_BLOCK_FLAGS_SUPPORTED_MASK) {
        pr_warn("unknown flags set, 0x%x\n", super->flags);
        return false;
    }
    if (super->block_size != dev->block_size) {
        pr_warn("bad block size 0x%x, expected 0x%zx\n", super->block_size,
                dev->block_size);
        return false;
    }
    if (super->block_num_size != dev->block_num_size) {
        pr_warn("invalid block_num_size %d, expected %zd\n",
                super->block_num_size, dev->block_num_size);
        return false;
    }
    if (super->mac_size != dev->mac_size) {
        pr_warn("invalid mac_size %d, expected %zd\n", super->mac_size,
                dev->mac_size);
        return false;
    }
    if (!dev->tamper_detecting && super->mac_size != sizeof(struct mac)) {
        pr_warn("invalid mac_size %d != %zd\n", super->mac_size,
                sizeof(data_block_t));
        return false;
    }

    return true;
}

/**
 * super_version_delta - Find the version delta between two superblocks
 * @new_super: Candidate new superblock
 * @old_super: Old superblock
 *
 * The overflow in this function is intentional as a way to use a wrapping
 * two-bit counter.
 *
 * Return: Wrapped difference between the two bit version numbers in the two
 * superblocks. This will be 1 when new is newer than old, 3 when old is
 * newer than new, and any other number indicates an invalid/corrupt version.
 */
__attribute__((no_sanitize("unsigned-integer-overflow"))) static inline uint8_t
super_version_delta(const struct super_block* new_super,
                    const struct super_block* old_super) {
    return (new_super->flags - old_super->flags) &
           SUPER_BLOCK_FLAGS_VERSION_MASK;
}

/**
 * use_new_super - Check if new superblock is valid and more recent than old
 * @dev:                Block device that super block was read from.
 * @new_super:          New super block data.
 * @new_super_index:    Index that @new_super was read from.
 * @old_super:          Old super block data, or %NULL.
 *
 * Return: %true if @new_super is valid for @dev, and more recent than
 * @old_super (or @old_super is %NULL), %false otherwise.
 */
static bool use_new_super(const struct block_device* dev,
                          const struct super_block* new_super,
                          unsigned int new_super_index,
                          const struct super_block* old_super) {
    uint8_t dv;
    if (!super_block_valid(dev, new_super)) {
        return false;
    }
    if ((new_super->flags & SUPER_BLOCK_FLAGS_BLOCK_INDEX_MASK) !=
        new_super_index) {
        pr_warn("block index, 0x%x, does not match flags, 0x%x\n",
                new_super_index, new_super->flags);
        return false;
    }
    if (!old_super) {
        return true;
    }
    dv = super_version_delta(new_super, old_super);
    pr_read("version delta, %d (new flags 0x%x, old flags 0x%x)\n", dv,
            new_super->flags, old_super->flags);
    if (dv == 1) {
        return true;
    }
    if (dv == 3) {
        return false;
    }
    pr_warn("bad version delta, %d (new flags 0x%x, old flags 0x%x)\n", dv,
            new_super->flags, old_super->flags);
    return false;
}

static void fs_init_free_set(struct fs* fs, struct block_set* set);

/**
 * fs_set_roots - Initialize fs state from super block roots
 * @fs:                File system state object
 * @free:              Free set root node
 * @files:             Files tree root node
 * @checkpoint:        Checkpoint metadata block. May be NULL.
 * @restore_checkpoint: If %true, restore files and free roots from @checkpoint
 *                      (which must not be NULL).
 *
 * Unconditionally sets the filesystem roots to @free and @files respectively,
 * then attempts to restore the checkpoint roots if @restore_checkpoint is
 * %true. When attempting to restore from a checkpoint that exists but is not
 * readable, return %false, leaving the filesystem roots initialized to @free
 * and @files. If attempting to restore from checkpoint but no checkpoint was
 * previously set, this function will clear the filesystem.
 *
 * Returns %true if fs roots were correctly initialized as requested, %false if
 * a requested checkpoint restore failed (but roots were still initialized to
 * the provided blocks).
 */
static bool fs_set_roots(struct fs* fs,
                         const struct block_mac* free,
                         const struct block_mac* files,
                         const struct block_mac* checkpoint,
                         bool restore_checkpoint) {
    bool success = true;
    struct transaction tr;
    struct block_tree checkpoint_files =
            BLOCK_TREE_INITIAL_VALUE(checkpoint_files);

    assert(!restore_checkpoint || checkpoint);

    fs->free.block_tree.root = *free;
    fs->files.root = *files;

    if (checkpoint) {
        fs->checkpoint = *checkpoint;
        transaction_init(&tr, fs, true);

        /*
         * fs->checkpoint_free is initialized to contain all blocks, so we
         * don't have to initialize it if there is no checkpoint on disk
         */
        assert(!block_range_empty(fs->checkpoint_free.initial_range));

        if (block_mac_valid(&tr, &fs->checkpoint)) {
            success = checkpoint_read(&tr, &fs->checkpoint, &checkpoint_files,
                                      &fs->checkpoint_free);
        } else if (restore_checkpoint) {
            /* We do not want to restore a non-existent checkpoint */
            success = false;
        }
        if (success && restore_checkpoint) {
            /*
             * Checkpoint restore counts as a repair which must set the repaired
             * flag. We disallow checkpoint restore in alternate mode in
             * fs_init().
             */
            fs->main_repaired = true;
            fs->files.root = checkpoint_files.root;
            block_set_copy_ro(&tr, &fs->free, &fs->checkpoint_free);
            /*
             * block_set_copy_ro() clears the copy_on_write flag for the free
             * set, so we have to reset it to allow modification.
             */
            fs->free.block_tree.copy_on_write = true;
        }
        if (!tr.failed) {
            /* temporary transaction is only for reading, drop it */
            transaction_fail(&tr);
        }
        transaction_free(&tr);
    }

    return success;
}

/**
 * fs_init_free_set - Initialize an initial free set for a file system
 * @fs:         File system state object.
 * @set:        Block set to initialize
 *
 * Initializes @set to the entire range of @fs, i.e. all blocks are free.
 */
static void fs_init_free_set(struct fs* fs, struct block_set* set) {
    struct block_range range = {
            .start = fs->min_block_num,
            .end = fs->dev->block_count,
    };
    block_set_add_initial_range(set, range);
}

/**
 * fs_init_from_super - Initialize file system from super block
 * @fs:         File system state object.
 * @super:      Superblock data, or %NULL.
 * @flags:      Any of &typedef fs_init_flags32_t, ORed together.
 *
 * Return: 0 if super block was usable, -1 if a fatal error was encountered and
 * initialization should not continue. The file system may not be readable, even
 * if this function returns 0. Check @fs->readable before attempting to read
 * from this file system.
 */
static int fs_init_from_super(struct fs* fs,
                              const struct super_block* super,
                              fs_init_flags32_t flags) {
    bool is_clear = false;
    bool do_clear = flags & FS_INIT_FLAGS_DO_CLEAR;
    bool do_swap = false; /* Does the active superblock alternate mode match the
                             current mode? */
    bool do_clear_backup = false;
    bool has_backup_field =
            super && (super->opt_flags & SUPER_BLOCK_OPT_FLAGS_HAS_FLAGS3);
    bool has_checkpoint_field =
            has_backup_field && super &&
            (super->opt_flags & SUPER_BLOCK_OPT_FLAGS_HAS_CHECKPOINT);
    bool recovery_allowed = flags & FS_INIT_FLAGS_RECOVERY_CLEAR_ALLOWED;
    bool read_only = false;
    const struct block_mac* new_files_root;
    const struct block_mac* new_free_root;
    const struct block_mac* new_checkpoint = NULL;

    /*
     * We check that the super-block matches these block device params in
     * super_block_valid(). If these params change, the filesystem (and
     * alternate backup) will be wiped and reset with the new params.
     */
    fs->block_num_size = fs->dev->block_num_size;
    fs->mac_size = fs->dev->mac_size;

    block_set_init(fs, &fs->free);
    fs->free.block_tree.copy_on_write = true;
    fs_file_tree_init(fs, &fs->files);
    fs->files.copy_on_write = true;
    fs->files.allow_copy_on_write = true;
    fs->main_repaired = false;

    memset(&fs->checkpoint, 0, sizeof(fs->checkpoint));
    block_set_init(fs, &fs->checkpoint_free);
    /*
     * checkpoint_init() will clear the checkpoint initial range if a valid
     * checkpoint exists.
     */
    fs_init_free_set(fs, &fs->checkpoint_free);

    /* Reserve 1/4 for tmp blocks plus half of the remaining space */
    fs->reserved_count = fs->dev->block_count / 8 * 5;

    fs->alternate_data = flags & FS_INIT_FLAGS_ALTERNATE_DATA;

    /*
     * Check version and flags after initializing an empty FS, so that we can
     * disallow writing and continue initializing other file systems. If we exit
     * early here this file system will be inaccessible, but its fields are
     * safely initialized.
     */
    if (super && super->fs_version > SUPER_BLOCK_FS_VERSION) {
        pr_err("ERROR: super block is from the future 0x%x\n",
               super->fs_version);
        error_report_superblock_invalid(fs->name);
        assert(!fs->readable);
        assert(!fs->writable);
        return 0;
    }

    if (super && (super->required_flags & ~SUPER_BLOCK_REQUIRED_FLAGS_MASK)) {
        pr_err("ERROR: super block requires unrecognized fs features: 0x%x\n",
               super->required_flags);
        error_report_superblock_invalid(fs->name);
        assert(!fs->readable);
        assert(!fs->writable);
        return 0;
    }

    if (super) {
        fs->super_block_version = super->flags & SUPER_BLOCK_FLAGS_VERSION_MASK;
        fs->needs_full_scan =
                super->opt_flags & SUPER_BLOCK_OPT_FLAGS_NEEDS_FULL_SCAN;
        fs->main_repaired = super->required_flags &
                            SUPER_BLOCK_REQUIRED_FLAGS_MAIN_REPAIRED;

        do_swap = !(super->flags & SUPER_BLOCK_FLAGS_ALTERNATE) !=
                  !(flags & FS_INIT_FLAGS_ALTERNATE_DATA);

        if (do_swap) {
            pr_init("Swapping super-block with alternate\n");

            fs->backup.flags = super->flags & (SUPER_BLOCK_FLAGS_EMPTY |
                                               SUPER_BLOCK_FLAGS_ALTERNATE);
            fs->backup.free = super->free;
            fs->backup.files = super->files;
            fs->backup.checkpoint = super->checkpoint;

            if (!has_backup_field ||
                super->backup.flags & SUPER_BLOCK_FLAGS_EMPTY) {
                is_clear = true;
            } else if (has_backup_field) {
                new_files_root = &super->backup.files;
                new_free_root = &super->backup.free;
                if (has_checkpoint_field) {
                    new_checkpoint = &super->backup.checkpoint;
                }
            }
        } else {
            if (has_backup_field) {
                fs->backup = super->backup;
            }

            if (super->flags & SUPER_BLOCK_FLAGS_EMPTY) {
                is_clear = true;
            } else {
                new_files_root = &super->files;
                new_free_root = &super->free;
                if (has_checkpoint_field) {
                    new_checkpoint = &super->checkpoint;
                }
            }
        }

        if (!is_clear && !do_clear &&
            (!block_probe(fs, new_files_root, true) ||
             !block_probe(fs, new_free_root, false))) {
            pr_init("Backing file probe failed, fs is corrupted.\n");
            if (recovery_allowed) {
                pr_init("Attempting to clear corrupted fs.\n");
                do_clear = true;
            }
        }

        /*
         * Check that the block device has not shrunk. Shrinking is only allowed
         * in limited circumstances if we are also clearing the filesystem.
         */
        if (super->block_count > fs->dev->block_count) {
            if ((!do_clear) && (!is_clear)) {
                /*
                 * If block device is smaller than super and we're not clearing
                 * the fs, we want to prevent write access to avoid losing data.
                 * Read-only access is still allowed, although blocks may be
                 * missing.
                 */
                pr_err("bad block count 0x%" PRIx64 ", expected <= 0x%" PRIx64
                       "\n",
                       super->block_count, fs->dev->block_count);
                read_only = true;
            } else if (flags & FS_INIT_FLAGS_ALTERNATE_DATA) {
                /*
                 * Either we are on main filesystem and switching to alternate
                 * or we are on alternate. Either case is an error. If we get
                 * here, then the alternate FS is not backed by a temp file,
                 * which should never happen. We want to error loudly in this
                 * case, but continue mounting other file systems.
                 */
                pr_err("Can't clear fs if FS_INIT_FLAGS_ALTERNATE_DATA is"
                       " set .\n");
                assert(!fs->readable);
                assert(!fs->writable);
                return 0;
            } else {
                /*
                 * If we are are on main filesystem and the backup is an
                 * alternate, clear the backup also.
                 */
                do_clear_backup = true;
            }
        }
    }

    if (!fs->alternate_data && (flags & FS_INIT_FLAGS_RESTORE_CHECKPOINT)) {
        fs->needs_full_scan = false;
    }

    /*
     * If any of the following are true:
     * - we are initializing a new fs
     * - we are not swapping but detect an old superblock without the backup
     * - filesystem device has shrunk and FS_INIT_FLAGS_DO_CLEAR is set
     * then ensure that the backup slot is a valid empty filesystem in case we
     * later switch filesystems without an explicit clear flag.
     */
    if (!super || (!do_swap && !has_backup_field) || do_clear_backup) {
        fs->backup = (struct super_block_backup){
                .flags = SUPER_BLOCK_FLAGS_EMPTY,
                .files = {0},
                .free = {0},
                .checkpoint = {0},
        };
    }

    if (super && !is_clear && !do_clear) {
        if (!fs_set_roots(fs, new_free_root, new_files_root, new_checkpoint,
                          flags & FS_INIT_FLAGS_RESTORE_CHECKPOINT)) {
            /*
             * fs_set_roots() returns false if the checkpoint restore failed,
             * but leaves the roots in a valid state to allow read-only access.
             */
            pr_err("fs %s: failed to initialize filesystem roots\n", fs->name);
            read_only = true;
        } else {
            pr_init("fs %s: loaded super block version %d, checkpoint exists: %d\n",
                    fs->name, fs->super_block_version,
                    block_range_empty(fs->checkpoint_free.initial_range));
        }
    } else {
        if (is_clear) {
            pr_init("fs %s: superblock, version %d, is empty fs\n", fs->name,
                    fs->super_block_version);
        } else if (do_clear) {
            pr_init("fs %s: clear requested, create empty, version %d\n",
                    fs->name, fs->super_block_version);
            if (!fs->alternate_data) {
                fs->main_repaired = false;
                fs->needs_full_scan = false;
            }
        } else {
            pr_init("fs %s: no valid super-block found, create empty\n",
                    fs->name);
        }
        fs_init_free_set(fs, &fs->free);
    }
    assert(fs->block_num_size >= fs->dev->block_num_size);
    assert(fs->block_num_size <= sizeof(data_block_t));
    assert(fs->mac_size >= fs->dev->mac_size);
    assert(fs->mac_size <= sizeof(struct mac));
    assert(fs->mac_size == sizeof(struct mac) || fs->dev->tamper_detecting);

    /*
     * fs_set_roots() unconditionally set the files and free roots. If it fails,
     * it failed to read the checkpoint block but that should only block
     * modification, not reading.
     */
    fs->readable = true;

    if (read_only) {
        assert(!fs->writable);
        return 0;
    }

    fs->writable = true;
    if (do_clear && !is_clear) {
        if (!write_initial_super_block(fs)) {
            return -1;
        }
    } else if (flags & FS_INIT_FLAGS_RESTORE_CHECKPOINT) {
        /*
         * Flush the new restored checkpoint to superblock before overwriting
         * any data blocks. We know that we can't already have a pending
         * initial_super_block_tr yet because we just made the filesystem
         * writable, and write_current_super_block() requires a writable
         * filesystem.
         */
        assert(!fs->initial_super_block_tr);
        write_current_super_block(fs, false);
    }

    return 0;
}

/**
 * load_super_block - Find and load superblock and initialize file system state
 * @fs:         File system state object.
 * @flags:      Any of &typedef fs_init_flags32_t, ORed together.
 *
 * Return: 0 if super block was readable and not from a future file system
 * version (regardless of its other content), -1 if not.
 */
static int load_super_block(struct fs* fs, fs_init_flags32_t flags) {
    unsigned int i;
    int ret;
    const struct super_block* new_super;
    struct obj_ref new_super_ref = OBJ_REF_INITIAL_VALUE(new_super_ref);
    const struct super_block* old_super = NULL;
    struct obj_ref old_super_ref = OBJ_REF_INITIAL_VALUE(old_super_ref);

    assert(fs->super_dev->block_size >= sizeof(struct super_block));

    for (i = 0; i < countof(fs->super_block); i++) {
        new_super = block_get_super(fs, fs->super_block[i], &new_super_ref);
        if (!new_super) {
            if (fs->allow_tampering) {
                /*
                 * Superblock may not exist yet in non-secure storage, proceed
                 * anyway
                 */
                continue;
            }
            pr_err("failed to read super-block\n");
            ret = -1;  // -EIO ? ERR_IO?;
            goto err;
        }
        if (use_new_super(fs->dev, new_super, i, old_super)) {
            if (old_super) {
                block_put(old_super, &old_super_ref);
            }
            old_super = new_super;
            obj_ref_transfer(&old_super_ref, &new_super_ref);
        } else {
            block_put(new_super, &new_super_ref);
        }
    }

    ret = fs_init_from_super(fs, old_super, flags);
err:
    if (old_super) {
        block_put(old_super, &old_super_ref);
    }
    return ret;
}

struct fs_check_state {
    struct file_iterate_state iter;
    bool delete_invalid_files;

    bool internal_state_valid;
    bool invalid_block_found;
};

static bool fs_check_file(struct file_iterate_state* iter,
                          struct transaction* tr,
                          const struct block_mac* block_mac,
                          bool added,
                          bool removed) {
    struct fs_check_state* fs_check_state =
            containerof(iter, struct fs_check_state, iter);
    struct obj_ref info_ref = OBJ_REF_INITIAL_VALUE(info_ref);
    struct file_handle file;
    char path[FS_PATH_MAX];

    assert(!tr->failed);
    assert(!tr->invalid_block_found);

    const struct file_info* info = file_get_info(tr, block_mac, &info_ref);
    if (!info) {
        pr_err("could not get file info at block %" PRIu64 "\n",
               block_mac_to_block(tr, block_mac));
        fs_check_state->internal_state_valid = false;
        goto err_file_info;
    }
    strncpy(path, info->path, sizeof(path));
    path[sizeof(path) - 1] = '\0';
    file_info_put(info, &info_ref);

    enum file_op_result result =
            file_open(tr, path, &file, FILE_OPEN_NO_CREATE, true);
    if (result != FILE_OP_SUCCESS) {
        /* TODO: is it ok to leak the filename here? we do it elsewhere */
        pr_err("could not open file %s\n", path);
        fs_check_state->internal_state_valid = false;
        goto err_file_open;
    }

    if (!file_check(tr, &file)) {
        fs_check_state->internal_state_valid = false;
    }

    file_close(&file);

err_file_open:
err_file_info:
    if (tr->invalid_block_found) {
        fs_check_state->invalid_block_found = true;
        /* We have noted the invalid block, reset for the next file. */
        tr->invalid_block_found = false;
    }
    if (tr->failed) {
        transaction_activate(tr);
    }

    /* Continue iterating unconditionally */
    return false;
}

enum fs_check_result fs_check_full(struct fs* fs) {
    bool free_set_valid, file_tree_valid;
    enum fs_check_result res = FS_CHECK_NO_ERROR;
    struct transaction iterate_tr;
    struct fs_check_state state = {
            .iter.file = fs_check_file,
            .internal_state_valid = true,
            .invalid_block_found = false,
    };

    transaction_init(&iterate_tr, fs, true);

    /* Check the free list for consistency */
    free_set_valid = block_set_check(&iterate_tr, &fs->free);
    if (!free_set_valid || iterate_tr.invalid_block_found) {
        pr_err("free block set is invalid\n");
        res = FS_CHECK_INVALID_FREE_SET;
        /*
         * We can recover the free set non-destructively by rebuilding from the
         * file tree, so we don't need to report the invalid block.
         */
        iterate_tr.invalid_block_found = false;
    }
    if (iterate_tr.failed) {
        pr_err("free set tree not fully readable\n");
        state.internal_state_valid = false;
        transaction_activate(&iterate_tr);
    }

    /* Check the file tree for consistency */
    file_tree_valid = block_tree_check(&iterate_tr, &fs->files);
    if (!file_tree_valid) {
        pr_err("file tree is invalid\n");
        res = FS_CHECK_INVALID_FILE_TREE;
    }
    if (iterate_tr.invalid_block_found) {
        pr_err("invalid block encountered in file tree\n");
        state.invalid_block_found = true;
        iterate_tr.invalid_block_found = false;
    }
    if (iterate_tr.failed) {
        pr_err("file tree not fully readable\n");
        state.internal_state_valid = false;
        transaction_activate(&iterate_tr);
    }

    file_iterate(&iterate_tr, NULL, false, &state.iter, true);

    /* Invalid blocks take precedence over internal consistency errors. */
    if (state.invalid_block_found) {
        res = FS_CHECK_INVALID_BLOCK;
    } else if (res == FS_CHECK_NO_ERROR && !state.internal_state_valid) {
        res = FS_CHECK_UNKNOWN;
    }
    if (!iterate_tr.failed) {
        transaction_fail(&iterate_tr);
    }
    transaction_free(&iterate_tr);

    return res;
}

enum fs_check_result fs_check_quick(struct fs* fs) {
    bool fs_is_clear = !block_range_empty(fs->free.initial_range);
    if (fs_is_clear || (block_probe(fs, &fs->files.root, true) &&
                        block_probe(fs, &fs->free.block_tree.root, false))) {
        return FS_CHECK_NO_ERROR;
    } else {
        return FS_CHECK_INVALID_BLOCK;
    }
}

enum fs_check_result fs_check(struct fs* fs) {
    if (fs->needs_full_scan) {
        pr_warn("%s filesystem requires full scan on mount\n", fs->name);
        return fs_check_full(fs);
    } else {
        return fs_check_quick(fs);
    }
}

/**
 * fs_file_tree_init - Initialize an empty file tree for a file system
 * @fs:        File system state object.
 * @tree:      Block tree to initialize as a file tree.
 */
void fs_file_tree_init(const struct fs* fs, struct block_tree* tree) {
    size_t block_num_size;
    size_t block_mac_size;

    block_num_size = fs->block_num_size;
    block_mac_size = block_num_size + fs->mac_size;
    block_tree_init(tree, fs->dev->block_size, block_num_size, block_mac_size,
                    block_mac_size);
}

/**
 * fs_init - Initialize file system state
 * @fs:         File system state object.
 * @name:       File system name for error reporting. Must be a static string.
 * @key:        Key pointer. Must not be freed while @fs is in use.
 * @dev:        Main block device.
 * @super_dev:  Block device for super block.
 * @flags:      Any of &typedef fs_init_flags32_t, ORed together.
 */
int fs_init(struct fs* fs,
            const char* name,
            const struct key* key,
            struct block_device* dev,
            struct block_device* super_dev,
            fs_init_flags32_t flags) {
    int ret;

    if (super_dev->block_size < sizeof(struct super_block)) {
        pr_err("unsupported block size for super_dev, %zd < %zd\n",
               super_dev->block_size, sizeof(struct super_block));
        return -1;  // ERR_NOT_VALID?
    }

    if (super_dev->block_count < 2) {
        pr_err("unsupported block count for super_dev, %" PRIu64 "\n",
               super_dev->block_count);
        return -1;  // ERR_NOT_VALID?
    }

    if ((flags & FS_INIT_FLAGS_ALTERNATE_DATA) &&
        (flags & FS_INIT_FLAGS_RESTORE_CHECKPOINT)) {
        pr_err("Alternate file system cannot restore to a checkpoint\n");
        return -1;
    }

    fs->name = name;
    fs->key = key;
    fs->dev = dev;
    fs->super_dev = super_dev;
    fs->readable = false;
    fs->writable = false;
    fs->allow_tampering = flags & FS_INIT_FLAGS_ALLOW_TAMPERING;
    fs->checkpoint_required = false;
    list_initialize(&fs->transactions);
    list_initialize(&fs->allocated);
    fs->initial_super_block_tr = NULL;
    list_add_tail(&fs_list, &fs->node);

    if (dev == super_dev) {
        fs->min_block_num = 2;
    } else {
        /* TODO: use 0 when btree code allows it */
        fs->min_block_num = 1;
    }
    fs->super_block[0] = 0;
    fs->super_block[1] = 1;
    ret = load_super_block(fs, flags);
    if (ret) {
        fs_destroy(fs);
        fs->dev = NULL;
        fs->super_dev = NULL;
        return ret;
    }

    if ((flags & FS_INIT_FLAGS_AUTO_CHECKPOINT) &&
        !block_mac_valid_fs(fs, &fs->checkpoint)) {
        if (fs_check_full(fs) == FS_CHECK_NO_ERROR) {
            fs->checkpoint_required = true;
        } else {
            pr_err("Not automatically creating a checkpoint; "
                   "an error was found in filesystem %s\n",
                   fs->name);
        }
    }

    return 0;
}

/**
 * fs_destroy - Destroy file system state
 * @fs:         File system state object.
 *
 * Free any dynamically allocated state and check that @fs is not referenced by
 * any transactions.
 */
void fs_destroy(struct fs* fs) {
    if (fs->initial_super_block_tr) {
        if (!fs->initial_super_block_tr->failed) {
            transaction_fail(fs->initial_super_block_tr);
        }
        transaction_free(fs->initial_super_block_tr);
        free(fs->initial_super_block_tr);
        fs->initial_super_block_tr = NULL;
    }
    assert(list_is_empty(&fs->transactions));
    assert(list_is_empty(&fs->allocated));
    list_delete(&fs->node);
    fs->readable = false;
    fs->writable = false;
}

/**
 * fs_unknown_super_block_state_all - Notify filesystems of unknown disk state
 *
 * Call from other layers when detecting write failues that can cause the
 * in-memory state of super blocks (or other block that we don't care about) to
 * be different from the on-disk state. Write in-memory state to disk before
 * writing any other block.
 */
void fs_unknown_super_block_state_all(void) {
    struct fs* fs;
    list_for_every_entry(&fs_list, fs, struct fs, node) {
        /* TODO: filter out filesystems that are not affected? */
        /*
         * We can't reinitialize an existing, failed special transaction here.
         * If a initial superblock write failed and triggered
         * fs_unknown_super_block_state_all() we need to leave that superblock
         * transaction in a failed state so that the transaction that that
         * triggered the failing write can also be failed further up the call
         * chain. If a special transaction already exists we are guaranteed that
         * it will be reinitialized and flushed to disk before any new writes to
         * that FS, so we don't need to reinitialize it here.
         *
         * If this file system is not writable, we should not try to re-write
         * the current super block state. A read-only file system cannot have
         * any modifications that we are allowed to save, and it does not need
         * to be re-synced here as we cannot have previously failed to write its
         * superblock.
         */
        if (fs->writable) {
            write_current_super_block(fs, false /* reinitialize */);
        }
    }
}

void fs_fail_all_transactions(void) {
    struct transaction* tmp_tr;
    struct transaction* tr;
    struct fs* fs;
    list_for_every_entry(&fs_list, fs, struct fs, node) {
        list_for_every_entry_safe(&fs->transactions, tr, tmp_tr,
                                  struct transaction, node) {
            if (transaction_is_active(tr) && !tr->failed) {
                transaction_fail(tr);
            }
        }
    }
}
