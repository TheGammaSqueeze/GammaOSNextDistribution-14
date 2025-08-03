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

// Don't lint the next line, as cpplint will suggest adding
// /tools/security as an include_dir
// NOLINTNEXTLINE
#include "fuzz_ffi.h"

#include <vector>
#include "include/ffi_common.h"

// Empty functions we can use for our function targets
void fn(int num_args, ...) {}
void closure_fn(ffi_cif* cif __UNUSED__,
        void* resp, void** args, void* userdata) {}
void raw_closure_fn(ffi_cif* cif __UNUSED__,
        void* resp, ffi_raw* args, void* userdata) {}
void java_raw_closure_fn(ffi_cif* cif __UNUSED__,
        void* resp, ffi_java_raw* args, void* userdata) {}

ffi_type* generateCustomType(FuzzedDataProvider* dataProvider) {
    // Set our flag so we don't call a java-related function (triggers an abort)
    args_contain_struct = true;

    ffi_type* new_type = reinterpret_cast<ffi_type*>(malloc(sizeof(ffi_type)));
    ffi_alloc_vector.push_back(new_type);

    new_type->size = 0;
    new_type->alignment = 0;
    new_type->type = FFI_TYPE_STRUCT;

    // Generate our subobjects
    size_t num_elements = dataProvider->ConsumeIntegralInRange<size_t>(0,
            MAX_NUM_ELEMENTS);
    new_type->elements = reinterpret_cast<ffi_type**>(
            malloc(sizeof(ffi_type*)*(num_elements+1)));

    // Nested custom structs will cause an assert, so disable them
    // TODO(michael.ensing@leviathansecurity.com):
    //     change the 'false' here to true once libffi supports nested structs.
    //     It'll just throw an assert currently.
    for (size_t i=0; i < num_elements; i++) {
        new_type->elements[i] = getRandomType(dataProvider, false);
    }

    // The final element must be a nullptr
    new_type->elements[num_elements] = NULL;

    // Get our size/alignment
    ffi_get_struct_offsets(abi, new_type, NULL);

    return new_type;
}

size_t getTotalSize(ffi_type* type) {
    if (type == NULL) {
        return 0;
    }

    // Start the total as the size of the object itself
    size_t total_size = type->size > sizeof(void*) ?
            type->size : sizeof(void*);

    // Recursively add the size of the subelements
    if (type->elements != NULL) {
        for (size_t i=0; type->elements[i] != NULL; i++) {
            total_size += getTotalSize(type->elements[i]);
        }
    }

    return total_size;
}

ffi_type* getRandomType(FuzzedDataProvider* dataProvider,
        bool allowCustomTypes) {
    // Which type? Let type==NUM_TYPES be our custom struct case
    size_t type_index = dataProvider->ConsumeIntegralInRange<size_t>(0,
            NUM_TYPES);
    ffi_type* type;
    if (type_index == NUM_TYPES) {
        if (allowCustomTypes) {
            type = generateCustomType(dataProvider);
        } else {
            return NULL;
        }
    } else {
        type = ffi_types[type_index];
    }

    return type;
}

void* genArg(ffi_type* type, FuzzedDataProvider* dataProvider) {
    // Allocate the space for our arg
    // TODO(michael.ensing@leviathansecurity.com):
    //    Properly allocate the correct amount of aligned-space,
    //    don't just double (which should contain any alignment)
    size_t type_size = getTotalSize(type)*2;

    if (type_size == 0) {
        return NULL;
    }

    void* ret = malloc(type_size);

    std::vector<uint8_t> bytes = dataProvider->ConsumeBytes<uint8_t>(type_size);
    memcpy(ret, bytes.data(), bytes.size());

    return ret;
}

bool buildArgArrays(ffi_type* arg_types[], void* arg_array[], size_t num_args,
        FuzzedDataProvider* dataProvider) {
    // The first value in our array should be the number of arguments
    arg_types[0] = &ffi_type_sint;
    size_t* size_ptr = reinterpret_cast<size_t*>(malloc(sizeof(size_t)));
    *size_ptr = num_args;
    arg_array[0] = size_ptr;

    // Grab our arguments
    for (size_t i = 1; i <= num_args; i++) {
        // Determine what type we're using
        ffi_type* type = getRandomType(dataProvider, true);
        if (type == NULL) {
            return false;
        }
        arg_types[i] = type;

        // Generate a value for it and add to our arguments array
        arg_array[i] = genArg(type, dataProvider);
    }

    // Our arrays of pointers need to be nullptr-terminated
    arg_types[num_args+1] = NULL;
    arg_array[num_args+1] = NULL;

    return true;
}

void runMainFunctions(ffi_cif* cif, void* resp_buf, void** arg_array,
        FuzzedDataProvider* dataProvider) {
    // Call function
    ffi_call(cif, FFI_FN(fn), resp_buf, arg_array);

    // Prep Closure
    ffi_closure* pcl = NULL;
    void* code;

    pcl = reinterpret_cast<ffi_closure*>(
            ffi_closure_alloc(sizeof(ffi_closure), &code));
    if (pcl == NULL) {
        return;
    }

    size_t buf_size = dataProvider->ConsumeIntegralInRange<size_t>(0,
            MAX_RESP_SIZE);
    std::vector<uint8_t> data_vector =
            dataProvider->ConsumeBytes<uint8_t>(buf_size);
    ffi_prep_closure_loc(pcl, cif, closure_fn, data_vector.data(), code);
    ffi_closure_free(pcl);
}

void runRawFunctions(ffi_cif* cif, void* resp_buf, void** arg_array,
        FuzzedDataProvider* dataProvider) {
    #if !FFI_NO_RAW_API && !FFI_NATIVE_RAW_API
    // Allocate our ffi_raw and put our args there
    size_t rsize = ffi_raw_size(cif);
    ffi_raw* raw_args = reinterpret_cast<ffi_raw*>(malloc(rsize));
    raw_alloc_vector.push_back(raw_args);
    ffi_ptrarray_to_raw(cif, arg_array, raw_args);

    // Call
    ffi_raw_call(cif, FFI_FN(fn), resp_buf, raw_args);

    // Prep Closure
    #if FFI_CLOSURES
    ffi_raw_closure* pcl = NULL;
    void* code;

    pcl = static_cast<ffi_raw_closure*>(
            ffi_closure_alloc(sizeof(ffi_raw_closure), &code));
    if (pcl == NULL) {
        return;
    }
    size_t buf_size = dataProvider->ConsumeIntegralInRange<size_t>(0,
            MAX_RESP_SIZE);
    std::vector<uint8_t> data_vector =
            dataProvider->ConsumeBytes<uint8_t>(buf_size);
    ffi_prep_raw_closure_loc(pcl, cif, raw_closure_fn, data_vector.data(),
                             code);
    ffi_closure_free(pcl);

    #endif  // FFI_CLOSURES
    #endif  // !FFI_NO_RAW_API && !FFI_NATIVE_RAW_API
}

void runJavaFunctions(ffi_cif* cif, void* resp_buf, void** arg_array,
        FuzzedDataProvider* dataProvider) {
    #if !defined(NO_JAVA_RAW_API)
    #if  !FFI_NO_RAW_API && !FFI_NATIVE_RAW_API

    // Allocate our ffi_java_raw and put our args there
    size_t rsize = ffi_java_raw_size(cif);
    // NOTE: a buffer overread will occasionally happen if we don't
    //       increase rsize.
    ffi_java_raw* raw_args = reinterpret_cast<ffi_raw*>(malloc(rsize*2));
    raw_alloc_vector.push_back(raw_args);
    ffi_ptrarray_to_raw(cif, arg_array, raw_args);

    // Call
    ffi_java_raw_call(cif, FFI_FN(fn), resp_buf, raw_args);

    // Prep Closure
    #if FFI_CLOSURES
    ffi_java_raw_closure* pcl = NULL;
    void* code;

    pcl = static_cast<ffi_java_raw_closure*>(
            ffi_closure_alloc(sizeof(ffi_java_raw_closure), &code));
    if (pcl == NULL) {
        return;
    }
    size_t buf_size = dataProvider->ConsumeIntegralInRange<size_t>(0,
            MAX_RESP_SIZE);
    std::vector<uint8_t> data_vector =
            dataProvider->ConsumeBytes<uint8_t>(buf_size);
    ffi_prep_java_raw_closure_loc(pcl, cif, raw_closure_fn, data_vector.data(),
                                  code);

    ffi_closure_free(pcl);


    #endif  // FFI_CLOSURES
    #endif  // !FFI_NATIVE_RAW_API
    #endif  // !NO_JAVA_RAW_API
}

void freeFFI(ffi_type* ffi_type) {
    // Make sure it's one of our structs
    if (ffi_type == NULL || ffi_type->type != FFI_TYPE_STRUCT) {
        return;
    }

    if (ffi_type->elements != NULL) {
        free(ffi_type->elements);
    }

    // Finally, free our object
    free(ffi_type);
}

void freeAll(void* arg_array[], size_t num_args, void* resp_buf) {
    // Free our custom struct objects
    for (const auto& ffi : ffi_alloc_vector) {
        freeFFI(ffi);
    }
    ffi_alloc_vector.clear();
    for (const auto& raw : raw_alloc_vector) {
        free(raw);
    }
    raw_alloc_vector.clear();

    for (size_t i=0; i <= num_args; i++) {
        free(arg_array[i]);
    }

    if (resp_buf) {
        free(resp_buf);
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *Data, size_t Size) {
    // Init our wrapper
    FuzzedDataProvider dataProvider(Data, Size);
    ffi_cif cif;
    ffi_status ret;
    void* resp_buf = NULL;
    args_contain_struct = false;
    ffi_type* rtype;

    // How many args are we sending?
    size_t num_args = dataProvider.ConsumeIntegralInRange<size_t>(0,
            MAX_NUM_ARGS);

    // Build our array of args (+2 for leading arg_count and trailing nullptr)
    ffi_type* arg_types[num_args+2];
    void* arg_array[num_args+2];
    bool success = buildArgArrays(arg_types, arg_array, num_args,
            &dataProvider);
    if (!success) {
        goto free;
    }

    // Get return type
    rtype = dataProvider.PickValueInArray<ffi_type*, NUM_TYPES>(ffi_types);

    // Create a buffer for our return value
    resp_buf = malloc(MAX_RESP_SIZE);
    if (resp_buf == NULL) {
        goto free;
    }

    // Set up our ABI
    // NOTE: fuzzing abi triggers an abort on linux-x86_64,
    //       so only fuzz it on ARM
    #if MAX_ABI > 0 && defined(ARM)
    abi = static_cast<ffi_abi>(
           dataProvider.ConsumeIntegralInRange<uint32_t>(0, MAX_ABI));
    #endif
    #if HAVE_LONG_DOUBLE_VARIANT
    ffi_prep_types(abi);
    #endif

    // ============= Call Functions =============
    ret = ffi_prep_cif_var(&cif, abi, 1, num_args, rtype,
            arg_types);
    if (ret != FFI_OK) {
        goto free;
    }

    runMainFunctions(&cif, resp_buf, arg_array, &dataProvider);
    runRawFunctions(&cif, resp_buf, arg_array, &dataProvider);
    if (!args_contain_struct) {
        runJavaFunctions(&cif, resp_buf, arg_array, &dataProvider);
    }

free:
    freeAll(arg_array, num_args, resp_buf);
    return 0;
}
