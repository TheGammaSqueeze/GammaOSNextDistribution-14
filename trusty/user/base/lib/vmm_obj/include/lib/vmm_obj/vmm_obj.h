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

/**
 * vmm_obj_map_ro() - Map the vmm_obj the read-only object from the remote
 *                    service at the given port and return the base and size
 *                    of the mapping.
 * @port: Service port to connect to.
 * @base_out: Pointer to location to store base address into.
 * @size_out: Pointer to location to store size into. This will hold
 *            the size in bytes of the buffer pointed to by @base_out.
 *
 * The function maps the object into the current process using mmap().
 * The caller is responsible for unmapping that memory with munmap().
 * Both pointers must be valid, otherwise the function returns
 * %ERR_INVALID_ARGS.
 *
 * Return: %NO_ERROR in case of success, error code otherwise.
 */
int vmm_obj_map_ro(const char* port, const void** base_out, size_t* size_out);

__END_CDECLS
