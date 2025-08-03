/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef FUZZING_ORPHANS_LIBFFI_FUZZ_FFI_H_
#define FUZZING_ORPHANS_LIBFFI_FUZZ_FFI_H_

#include <fuzzer/FuzzedDataProvider.h>
#include <ffi.h>
#include <cstdarg>
#include <vector>

#if defined(__GNUC__)
#define __UNUSED__ __attribute__((__unused__))
#endif

#define MAX_NUM_ARGS 128
#define MAX_NUM_ELEMENTS 16
#define MAX_RESP_SIZE 4096

// TODO(michael.ensing@leviathansecurity.com):
//     Ideally, we should add/remove supported types based on
//     arch-specific #defines (listed in ffi_gen.h)
#define NUM_TYPES 21
ffi_type* ffi_types[] = {
    &ffi_type_uchar,
    &ffi_type_schar,
    &ffi_type_ushort,
    &ffi_type_sshort,
    &ffi_type_uint,
    &ffi_type_sint,
    &ffi_type_ulong,
    &ffi_type_slong,
    &ffi_type_void,
    &ffi_type_uint8,
    &ffi_type_sint8,
    &ffi_type_uint16,
    &ffi_type_sint16,
    &ffi_type_uint32,
    &ffi_type_sint32,
    &ffi_type_uint64,
    &ffi_type_sint64,
    &ffi_type_float,
    &ffi_type_double,
    &ffi_type_pointer,
    &ffi_type_longdouble,
    // The following types are not available on some architectures
    // &ffi_type_complex_float,
    // &ffi_type_complex_double,
    // &ffi_type_complex_longdouble,
    // // nullptrs are used to terminate the array. Handle them manually.
    // nullptr
};

// Store vectors of allocated objects
std::vector<ffi_type*> ffi_alloc_vector;
std::vector<void*> raw_alloc_vector;


// Keep a boolean to track if the args have a struct,
// which will trigger an abort on java calls
bool args_contain_struct = false;

// Store the current ABI as a global
ffi_abi abi = FFI_DEFAULT_ABI;

// Define the number of possible ffi_abi values
// NOTE: Only supported architectures are arm/arm64, x86_64
// arm
#if defined(ARM)
#define MAX_ABI 4
// x86_64
#elif defined(X86_64) || (defined(__x86_64__) && defined(X86_DARWIN))
#define MAX_ABI 7
#else
#define MAX_ABI 0  // If we hit this case, do NOT fuzz the abi value.
#endif

// Retrieve the total size (in bytes) of a ffi_type.
// Useful for custom structs
size_t getTotalSize(ffi_type*);

// Retrieve a random type from the ffi_types array
ffi_type* getRandomType(FuzzedDataProvider*, bool);

// Generates a custom struct, in ffi_type format
ffi_type* generateCustomType(FuzzedDataProvider*);

// Copies buffer data into a buffer described by the provided ffi_type
// (may be a struct or have subobjects)
size_t copyArg(ffi_type*, void*, FuzzedDataProvider*);

// Builds out the arrays of ffi_types and arguments to define a function's
// parameters. Returns true on success, false on failure.
bool buildArgArrays(ffi_type*[], void*[], size_t, FuzzedDataProvider*);

// Allocates the necessary space for a new argument buffer for given ffi_type
// After allocation, calls copyArg() to fill buffer with data
void* genArg(ffi_type*, FuzzedDataProvider*);

// Functions to perform our library calls
void runMainFunctions(ffi_cif&, void*, void**, FuzzedDataProvider*);
void runRawFunctions(ffi_cif&, void*, void**, FuzzedDataProvider*);
void runJavaFunctions(ffi_cif&, void*, void**, FuzzedDataProvider*);

// Free any custom struct ffi_type objects
// Safe to call on default types.
void freeFFI(ffi_type*);

// Frees all elements that the fuzzer has allocated
void freeAll();

#endif  // FUZZING_ORPHANS_LIBFFI_FUZZ_FFI_H_
