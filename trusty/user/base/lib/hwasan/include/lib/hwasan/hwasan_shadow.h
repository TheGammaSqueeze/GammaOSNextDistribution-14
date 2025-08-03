/*
 * Copyright (C) 2020 The Android Open Source Project
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

/*
 * hwasan_tag_memory() - tag a given a chunk of memory
 * @ptr: pointer to the memory region being tagged
 * @size: size of the memory region being tagged
 *
 * Generate a tag for the memory region and record it in the corresponding
 * shadow memory locations.
 *
 * Return: pointer to the same memory pointed to by @ptr, but with the generated
 *         tag placed in the top byte.
 */
void* hwasan_tag_memory(void* ptr, size_t size);

/*
 * hwasan_untag_memory() - untag a given a chunk of memory
 * @ptr: pointer to the memory region being untagged
 * @size: size of the memory region being untagged
 *
 * Find shadow memory location corresponding to the given memory region and
 * clear its contents.
 */
void hwasan_untag_memory(void* ptr, size_t size);

/*
 * hwasan_remove_ptr_tag() - clear the tag from the given pointer
 * @ptr: pointer being untagged
 *
 * Return: pointer to memory pointed to by @ptr, but with the tag cleared.
 */
void* hwasan_remove_ptr_tag(void* ptr);
