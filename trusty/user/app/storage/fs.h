/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <stdbool.h>

#if BUILD_STORAGE_TEST
#define FULL_ASSERT 1
#else
#define FULL_ASSERT 0
#endif
#if FULL_ASSERT
#define full_assert assert
#else
#define full_assert(x) \
    do {               \
    } while (0)
#endif

#include "block_mac.h"
#include "block_set.h"
#include "block_tree.h"

/**
 * struct super_block_backup - Backup of root block for file system state
 * @flags:          Super-block flags for the backup, with the bits in
 *                  SUPER_BLOCK_VERSION_MASK set to 0 (i.e. the backup does not
 *                  contain a version).
 * @free:           Block and mac of backup free set root node.
 * @files:          Block and mac of backup files tree root node.
 * @checkpoint:     Block and mac of the backup checkpoint metadata block, if
 *                  any.
 *
 * Block numbers and macs in @free and @files are packed as indicated by
 * @block_num_size and @mac_size, but unlike other on-disk data, the size of the
 * whole field is always the full 24 bytes needed for a 8 byte block number and
 * 16 byte mac so this structure is always a fixed size.
 */
struct super_block_backup {
    uint32_t flags;
    struct block_mac free;
    struct block_mac files;
    struct block_mac checkpoint;
};
STATIC_ASSERT(sizeof(struct super_block_backup) == 76);

/**
 * struct fs - File system state
 * @node:                           List node for fs_list.
 * @dev:                            Main block device.
 * @transactions:                   Transaction list.
 * @allocated:                      List of block sets containing blocks
 *                                  allocated by active transactions.
 * @free:                           Block set of free blocks.
 * @files:                          B+ tree of all files.
 * @checkpoint:                     Block and mac of the latest committed
 *                                  checkpoint metadata. Points to a block that
 *                                  holds the files root and free set at the
 *                                  time of the most recent checkpoint.
 * @checkpoint_free:                Block set of free blocks at the time of the
 *                                  last committed checkpoint. A block is only
 *                                  free if it is in both @free and
 *                                  @checkpoint_free.
 * @super_dev:                      Block device used to store super blocks.
 * @readable:                       %true if the file system is initialized and
 *                                  readable. If false, no reads are valid and
 *                                  @writable must be %false.
 * @writable:                       %true if the file system may be modified. If
 *                                  %false, filesystem contents may be readable,
 *                                  but no superblock or block changes are
 *                                  permitted.
 * @allow_tampering:                %false if the filesystem must detect
 *                                  tampering of read and write operations.
 *                                  %true otherwise. If %false, when a write
 *                                  operation is reported as successfully
 *                                  completed it should not be possible for
 *                                  non-secure code to modify the stored data.
 * @key:                            Key to use for encrypt, decrypt and mac.
 * @super_block:                    Block numbers in @super_dev to store
 *                                  super-block in.
 * @super_block_version:            Last read or written super block version.
 * @written_super_block_version:    Last written super block version.
 * @main_repaired:                  %true if main file system has been repaired
 *                                  since being wiped. In alternate state only
 *                                  used to persist this flag in the super
 *                                  block.
 * @alternate_data:                 If true, the current superblock is for a
 *                                  filesystem with a backing store in an
 *                                  alternate data location and @backup contains
 *                                  the superblock of the normal filesystem. If
 *                                  false, @backup may contain a backup of the
 *                                  superblock for an alternate filesystem, but
 *                                  it may be outdated.
 * @needs_full_scan:                %true if an error was detected in this file
 *                                  system data and the file system should be
 *                                  scanned on the next mount. Persisted to the
 *                                  super block so that we can initiate a scan
 *                                  the next time we mount the file system.
 * @checkpoint_required:            %true if a checkpoint must be created before
 *                                  committing any changes to the file system.
 * @backup:                         Backup superblock of other filesystem state
 *                                  (alternate if @alternate_data is false, main
 *                                  otherwise) Should be preserved across all
 *                                  filesystem operations after initialization.
 * @min_block_num:                  First block number that can store non
 *                                  super blocks.
 * @block_num_size:                 Number of bytes used to store block numbers.
 * @mac_size:                       Number of bytes used to store mac values.
 *                                  Must be 16 if @dev is not tamper_detecting.
 * @reserved_count:                 Number of free blocks reserved for active
 *                                  transactions.
 * @initial_super_block_tr:         Internal transaction containing initial
 *                                  super block that must be written before any
 *                                  other data. If %NULL superblock is already
 *                                  a safe state.
 * @name:                           File system name, used to identify the file
 *                                  system in debugging and error reporting
 *                                  messages.
 */

struct fs {
    struct list_node node;
    struct block_device* dev;
    struct list_node transactions;
    struct list_node allocated;
    struct block_set free;
    struct block_tree files;
    struct block_mac checkpoint;
    struct block_set checkpoint_free;
    struct block_device* super_dev;
    bool readable;
    bool writable;
    bool allow_tampering;
    const struct key* key;
    data_block_t super_block[2];
    unsigned int super_block_version;
    unsigned int written_super_block_version;
    bool main_repaired;
    bool alternate_data;
    bool needs_full_scan;
    bool checkpoint_required;
    struct super_block_backup backup;
    data_block_t min_block_num;
    size_t block_num_size;
    size_t mac_size;
    data_block_t reserved_count;
    struct transaction* initial_super_block_tr;
    const char* name;
};

bool update_super_block(struct transaction* tr,
                        const struct block_mac* free,
                        const struct block_mac* files,
                        const struct block_mac* checkpoint);

void fs_mark_scan_required(struct fs* fs);

/**
 * typedef fs_init_flags32_t - Flags that control filesystem clearing and
 * backups. These flags may be ORed together.
 *
 * %FS_INIT_FLAGS_NONE
 *   No flags set
 *
 * %FS_INIT_FLAGS_DO_CLEAR
 *   Unconditionally clear the filesystem, regardless of corruption state.
 *   %FS_INIT_FLAGS_RECOVERY_* flags are ignored when combined with this flag.
 *
 * %FS_INIT_FLAGS_RECOVERY_CLEAR_ALLOWED
 *   Allows clearing of corrupt filesystem.
 *
 * %FS_INIT_FLAGS_ALTERNATE_DATA
 *   Indicates that the filesystem is temporarily running on top of an alternate
 *   location for the @dev block device and rollback should be enforced
 *   separately from the normal mode.
 *
 * %FS_INIT_FLAGS_ALLOW_TAMPERING
 *   Allow this filesystem to be initialized with the super block not stored on
 *   a tamper-detecting block device. This filesystem WILL NOT detect any
 *   tampering and a malicious actor may arbitrarily roll it back to any
 *   previous state.
 *
 * %FS_INIT_FLAGS_RESTORE_CHECKPOINT
 *   Restore this filesystem to the current checkpointed state, discarding any
 *   changes since that checkpoint was made.
 *
 * %FS_INIT_FLAGS_AUTO_CHECKPOINT
 *   Automatically create a checkpoint of the filesystem state on mount.
 */
typedef uint32_t fs_init_flags32_t;
#define FS_INIT_FLAGS_NONE 0U
#define FS_INIT_FLAGS_DO_CLEAR (1U << 0)
#define FS_INIT_FLAGS_RECOVERY_CLEAR_ALLOWED (1U << 1)
#define FS_INIT_FLAGS_ALTERNATE_DATA (1U << 2)
#define FS_INIT_FLAGS_ALLOW_TAMPERING (1U << 3)
#define FS_INIT_FLAGS_RESTORE_CHECKPOINT (1U << 4)
#define FS_INIT_FLAGS_AUTO_CHECKPOINT (1U << 5)
#define FS_INIT_FLAGS_MASK                                           \
    (FS_INIT_FLAGS_DO_CLEAR | FS_INIT_FLAGS_RECOVERY_CLEAR_ALLOWED | \
     FS_INIT_FLAGS_ALTERNATE_DATA | FS_INIT_FLAGS_ALLOW_TAMPERING |  \
     FS_INIT_FLAGS_RESTORE_CHECKPOINT | FS_INIT_FLAGS_AUTO_CHECKPOINT)

int fs_init(struct fs* fs,
            const char* name,
            const struct key* key,
            struct block_device* dev,
            struct block_device* super_dev,
            fs_init_flags32_t flags);

static inline bool fs_is_repaired(struct fs* fs) {
    return fs->main_repaired && !fs->alternate_data;
}

static inline bool fs_is_readable(struct fs* fs) {
    return fs->readable;
}

static inline bool fs_is_writable(struct fs* fs) {
    return fs->writable;
}

/**
 * enum fs_check_result - Result of a filesystem check
 * @FS_CHECK_NO_ERROR:      No error was enountered in the checked blocks.
 * @FS_CHECK_INVALID_BLOCK: A MAC mismatch error or invalid block was
 *                          encountered while trying to load a block in the
 *                          file-system. This type of error may indicate that
 *                          the non-secure data is out of sync with the RPMB
 *                          superblock. The file-system is likely corrupt.
 * @FS_CHECK_INVALID_FREE_SET:  The free set was not internally valid or invalid
 *                              blocks were encountered in the free set tree.
 * @FS_CHECK_INVALID_FILE_TREE: The file tree was not internally valid but no
 *                              invalid blocks were encountered.
 * @FS_CHECK_UNKNOWN:       An unknown error was encountered while checking the
 *                          file-system. The file-system may not be entirely
 *                          readable or valid.
 */
enum fs_check_result {
    FS_CHECK_NO_ERROR = 0,
    FS_CHECK_INVALID_BLOCK,
    FS_CHECK_INVALID_FREE_SET,
    FS_CHECK_INVALID_FILE_TREE,
    FS_CHECK_UNKNOWN,
};

/**
 * fs_check_full - Check the file system tree
 * @fs:                    File system state object.
 *
 * Walk the filesystem tree and visit each file, checking the file tree and each
 * file block map for consistency.
 *
 * Returns @fs_check_result.FS_CHECK_NO_ERROR if no corruption was encountered
 * or any encountered corruption was repaired. Returns another @fs_check_result
 * variant describing the error if the filesystem remains corrupted after this
 * operation. Errors are prioritized in the following order (highest to lowest):
 * %FS_CHECK_INVALID_BLOCK (except in the free set, which is reported
 * separately), %FS_CHECK_INVALID_FILE_TREE, %FS_CHECK_INVALID_FREE_SET,
 * %FS_CHECK_UNKNOWN. This ordering is intended to allow callers to
 * differentiate between invalid blocks that indicate corruption and possibly
 * transient communication errors with the storage proxy.
 */
enum fs_check_result fs_check_full(struct fs* fs);

/**
 * fs_check_quick - Quickly check the file-system tree
 * @fs:                    File system state object.
 *
 * Perform a basic check that the file-system roots are valid. Suitable for use
 * while mounting file-systems where we don't want to pay the cost to walk the
 * entire file-system tree.
 *
 * Returns @fs_check_result.FS_CHECK_NO_ERROR if no corruption was encountered,
 * or another @fs_check_result variant describing the error.
 */
enum fs_check_result fs_check_quick(struct fs* fs);

/**
 * fs_check - Check the file system tree
 * @fs:                    File system state object.
 *
 * If the filesystem was not previously marked as requiring a full scan, perform
 * a quick check (i.e. behave the same as fs_check_quick()). If the file system
 * has been marked as potentially having an error, do a full scan using
 * fs_check_full().
 *
 * Returns an @fs_check_result variant, see fs_check_quick() and fs_check_full()
 * for details.
 */
enum fs_check_result fs_check(struct fs* fs);

void fs_file_tree_init(const struct fs* fs, struct block_tree* tree);

void fs_unknown_super_block_state_all(void);
void write_current_super_block(struct fs* fs, bool reinitialize);

void fs_destroy(struct fs* fs);

/**
 * fs_fail_all_transactions - Fail all pending transactions in all filesystems
 *
 * This functions fails any pending transactions that have not already failed.
 */
void fs_fail_all_transactions(void);
