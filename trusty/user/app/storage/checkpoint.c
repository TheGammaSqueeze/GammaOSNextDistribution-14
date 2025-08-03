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

#include "checkpoint.h"
#include "block_allocator.h"
#include "block_cache.h"
#include "block_mac.h"
#include "debug.h"
#include "transaction.h"

#define CHECKPOINT_MAGIC (0x0063797473757274) /* trustyc\0 */

/**
 * struct checkpoint - On-disk block containing the checkpoint metadata
 * @iv:             Initial value used for encrypt/decrypt
 * @magic:          CHECKPOINT_MAGIC
 * @files:          Block and mac of checkpointed files tree root node
 * @free:           Block and mac of checkpointed free set root node. When a
 *                  checkpoint is active blocks may only be allocated if they
 *                  are marked as free in both the filesystem free set and this
 *                  checkpointed free set.
 */
struct checkpoint {
    struct iv iv;
    uint64_t magic;
    struct block_mac files;
    struct block_mac free;
};

/**
 * checkpoint_get_new_block - Get a new, writable copy of the checkpoint block
 * metadata
 * @tr:                 Transaction object.
 * @new_checkpoint_ref: Output pointer to hold the block reference for the new
 *                      block
 * @checkpoint_mac:     Pointer to the current checkpoint block mac.
 * Updated with the block number of the new checkpoint block on success.
 *
 * Returns a new, writable copy of the checkpoint metadata block, or %NULL on
 * failure (tr->failed will be set). The returned pointer should then be passed
 * to checkpoint_update_roots() after the file tree and free set are finalized.
 * We have to split this operation in two so that the newly allocated block will
 * be removed from the free set.
 *
 * Caller takes ownership of the returned new, dirty block and is responsible
 * for releasing @new_checkpoint_ref.
 */
struct checkpoint* checkpoint_get_new_block(struct transaction* tr,
                                            struct obj_ref* new_checkpoint_ref,
                                            struct block_mac* checkpoint_mac) {
    data_block_t new_checkpoint_block;
    struct checkpoint* new_checkpoint;

    new_checkpoint_block = block_allocate(tr);
    if (tr->failed) {
        pr_warn("transaction failed, abort\n");
        return NULL;
    }
    assert(new_checkpoint_block);

    if (block_mac_valid(tr, checkpoint_mac)) {
        block_free(tr, block_mac_to_block(tr, checkpoint_mac));
    }
    new_checkpoint = block_get_cleared(tr, new_checkpoint_block, false,
                                       new_checkpoint_ref);

    block_mac_set_block(tr, checkpoint_mac, new_checkpoint_block);

    new_checkpoint->magic = CHECKPOINT_MAGIC;

    return new_checkpoint;
}

/**
 * checkpoint_update_roots - Update the files and free blocks of a checkpoint
 * @tr:             Transaction object.
 * @new_checkpoint: Pointer to a checkpoint metadata block returned by
 *                  checkpoint_get_new_block()
 * @files:          New checkpoint files tree root node.
 * @free:           New checkpoint free set root node.
 */
void checkpoint_update_roots(struct transaction* tr,
                             struct checkpoint* new_checkpoint,
                             const struct block_mac* files,
                             const struct block_mac* free) {
    new_checkpoint->files = *files;
    new_checkpoint->free = *free;
}

/**
 * checkpoint_read - Initialize root blocks from a checkpoint page
 * @fs:             File-system to initialize checkpoint state in.
 * @checkpoint:     Checkpoint root page block and mac. Must be a valid block.
 * @files:          New checkpoint file tree. May be %NULL.
 * @free:           New checkpoint free set. May be %NULL.
 *
 * Returns %true if the @files and @free nodes were properly populated from the
 * fields in @checkpoint. Either @files or @free may be %NULL; %NULL out params
 * will not be set. Returns %false and does not change @files or @free if the
 * @checkpoint metadata page exists but could not be read.
 *
 * Example: checkpoint_read(tr, &tr->fs->checkpoint, &files,
 *                          &tr->fs->checkpoint_free)
 */
bool checkpoint_read(struct transaction* tr,
                     const struct block_mac* checkpoint,
                     struct block_tree* files,
                     struct block_set* free) {
    const struct checkpoint* checkpoint_ro;
    struct obj_ref checkpoint_ro_ref = OBJ_REF_INITIAL_VALUE(checkpoint_ro_ref);

    assert(block_mac_valid(tr, checkpoint));

    checkpoint_ro = block_get(tr, checkpoint, NULL, &checkpoint_ro_ref);
    if (tr->failed) {
        goto err_block_get;
    }

    if (checkpoint_ro->magic != CHECKPOINT_MAGIC) {
        pr_err("Checkpoint magic mismatch!\n");
        transaction_fail(tr);
        goto err_magic_mismatch;
    }

    if (files) {
        files->root = checkpoint_ro->files;
    }
    if (free) {
        free->block_tree.root = checkpoint_ro->free;
        block_range_clear(&free->initial_range);
    }

err_magic_mismatch:
    block_put(checkpoint_ro, &checkpoint_ro_ref);
err_block_get:
    return !tr->failed;
}

/**
 * checkpoint_commit - Save the current file-system state as a checkpoint
 * @fs:             File-system to checkpoint.
 *
 * Create and commit a checkpoint of the current state of @fs.
 *
 * Returns %true if the checkpoint was created and committed successfully,
 * %false otherwise.
 */
bool checkpoint_commit(struct fs* fs) {
    struct transaction tr;
    bool success;

    assert(fs);
    transaction_init(&tr, fs, true);
    transaction_complete_etc(&tr, true);
    success = !tr.failed;
    if (success) {
        pr_init("Automatically created a checkpoint for filesystem %s\n",
                fs->name);
    } else {
        pr_err("Failed to commit checkpoint for filesystem %s\n", fs->name);
    }
    transaction_free(&tr);
    return success;
}
