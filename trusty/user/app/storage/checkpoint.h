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

#pragma once

#include "block_mac.h"
#include "fs.h"
#include "transaction.h"

struct checkpoint;

struct checkpoint* checkpoint_get_new_block(struct transaction* tr,
                                            struct obj_ref* new_checkpoint_ref,
                                            struct block_mac* checkpoint_mac);

void checkpoint_update_roots(struct transaction* tr,
                             struct checkpoint* new_checkpoint,
                             const struct block_mac* files,
                             const struct block_mac* free);

bool checkpoint_read(struct transaction* tr,
                     const struct block_mac* checkpoint,
                     struct block_tree* files,
                     struct block_set* free);

bool checkpoint_commit(struct fs* fs);
