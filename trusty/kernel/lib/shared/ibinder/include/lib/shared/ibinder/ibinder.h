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

#include <lk/compiler.h>

__BEGIN_CDECLS

struct ibinder;

/**
 * ibinder_add_ref() - Increment the reference count for an ibinder.
 * @self: Pointer to the struct ibinder to increment.
 */
void ibinder_add_ref(struct ibinder* self);

/**
 * ibinder_release() - Release the reference to the struct ibinder.
 * @self: Pointer to pointer to the struct ibinder to be released.
 *
 * This function will decrement the reference count of the object pointed to by
 * @self and then replace the inner pointer with %NULL.
 */
void ibinder_release(struct ibinder** self);

__END_CDECLS
