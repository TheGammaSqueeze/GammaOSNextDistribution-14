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

/* This file is separate because it's included both by eBPF programs (via include
 * in bpf_helpers.h) and directly by the boot time bpfloader (Loader.cpp).
 */

#include <linux/bpf.h>

// Pull in AID_* constants from //system/core/libcutils/include/private/android_filesystem_config.h
#include <cutils/android_filesystem_config.h>

/******************************************************************************
 *                                                                            *
 *                          ! ! ! W A R N I N G ! ! !                         *
 *                                                                            *
 * CHANGES TO THESE STRUCTURE DEFINITIONS OUTSIDE OF AOSP/MASTER *WILL* BREAK *
 * MAINLINE MODULE COMPATIBILITY                                              *
 *                                                                            *
 * AND THUS MAY RESULT IN YOUR DEVICE BRICKING AT SOME ARBITRARY POINT IN     *
 * THE FUTURE                                                                 *
 *                                                                            *
 * (and even in aosp/master you may only append new fields at the very end,   *
 *  you may *never* delete fields, change their types, ordering, insert in    *
 *  the middle, etc.  If a mainline module using the old definition has       *
 *  already shipped (which happens roughly monthly), then it's set in stone)  *
 *                                                                            *
 ******************************************************************************/

// These are the values used if these fields are missing
#define DEFAULT_BPFLOADER_MIN_VER 0u        // v0.0 (this is inclusive ie. >= v0.0)
#define DEFAULT_BPFLOADER_MAX_VER 0x10000u  // v1.0 (this is exclusive ie. < v1.0)
#define DEFAULT_SIZEOF_BPF_MAP_DEF 32       // v0.0 struct: enum (uint sized) + 7 uint
#define DEFAULT_SIZEOF_BPF_PROG_DEF 20      // v0.0 struct: 4 uint + bool + 3 byte alignment pad

// By default, unless otherwise specified, allow the use of features only supported by v0.37.
#define COMPILE_FOR_BPFLOADER_VERSION 37u

/*
 * The bpf_{map,prog}_def structures are compiled for different architectures.
 * Once by the BPF compiler for the BPF architecture, and once by a C++
 * compiler for the native Android architecture for the bpfloader.
 *
 * For things to work, their layout must be the same between the two.
 * The BPF architecture is platform independent ('64-bit LSB bpf').
 * So this effectively means these structures must be the same layout
 * on 5 architectures, all of them little endian:
 *   64-bit BPF, x86_64, arm  and  32-bit x86 and arm
 *
 * As such for any types we use inside of these structs we must make sure that
 * the size and alignment are the same, so the same amount of padding is used.
 *
 * Currently we only use: bool, enum bpf_map_type and unsigned int.
 * Additionally we use char for padding.
 *
 * !!! WARNING: HERE BE DRAGONS !!!
 *
 * Be particularly careful with 64-bit integers.
 * You will need to manually override their alignment to 8 bytes.
 *
 * To quote some parts of https://gcc.gnu.org/bugzilla/show_bug.cgi?id=69560
 *
 * Some types have weaker alignment requirements when they are structure members.
 *
 * unsigned long long on x86 is such a type.
 *
 * C distinguishes C11 _Alignof (the minimum alignment the type is guaranteed
 * to have in all contexts, so 4, see min_align_of_type) from GNU C __alignof
 * (the normal alignment of the type, so 8).
 *
 * alignof / _Alignof == minimum alignment required by target ABI
 * __alignof / __alignof__ == preferred alignment
 *
 * When in a struct, apparently the minimum alignment is used.
 */

_Static_assert(sizeof(bool) == 1, "sizeof bool != 1");
_Static_assert(__alignof__(bool) == 1, "__alignof__ bool != 1");
_Static_assert(_Alignof(bool) == 1, "_Alignof bool != 1");

_Static_assert(sizeof(char) == 1, "sizeof char != 1");
_Static_assert(__alignof__(char) == 1, "__alignof__ char != 1");
_Static_assert(_Alignof(char) == 1, "_Alignof char != 1");

// This basically verifies that an enum is 'just' a 32-bit int
_Static_assert(sizeof(enum bpf_map_type) == 4, "sizeof enum bpf_map_type != 4");
_Static_assert(__alignof__(enum bpf_map_type) == 4, "__alignof__ enum bpf_map_type != 4");
_Static_assert(_Alignof(enum bpf_map_type) == 4, "_Alignof enum bpf_map_type != 4");

// Linux kernel requires sizeof(int) == 4, sizeof(void*) == sizeof(long), sizeof(long long) == 8
_Static_assert(sizeof(unsigned int) == 4, "sizeof unsigned int != 4");
_Static_assert(__alignof__(unsigned int) == 4, "__alignof__ unsigned int != 4");
_Static_assert(_Alignof(unsigned int) == 4, "_Alignof unsigned int != 4");

// We don't currently use any 64-bit types in these structs, so this is purely to document issue.
// Here sizeof & __alignof__ are consistent, but _Alignof is not: compile for 'aosp_cf_x86_phone'
_Static_assert(sizeof(unsigned long long) == 8, "sizeof unsigned long long != 8");
_Static_assert(__alignof__(unsigned long long) == 8, "__alignof__ unsigned long long != 8");
// BPF wants 8, but 32-bit x86 wants 4
//_Static_assert(_Alignof(unsigned long long) == 8, "_Alignof unsigned long long != 8");


// for maps:
struct shared_bool { bool shared; };
#define PRIVATE ((struct shared_bool){ .shared = false })
#define SHARED ((struct shared_bool){ .shared = true })

// for programs:
struct optional_bool { bool optional; };
#define MANDATORY ((struct optional_bool){ .optional = false })
#define OPTIONAL ((struct optional_bool){ .optional = true })

// for both maps and programs:
struct ignore_on_eng_bool { bool ignore_on_eng; };
#define LOAD_ON_ENG ((struct ignore_on_eng_bool){ .ignore_on_eng = false })
#define IGNORE_ON_ENG ((struct ignore_on_eng_bool){ .ignore_on_eng = true })

struct ignore_on_user_bool { bool ignore_on_user; };
#define LOAD_ON_USER ((struct ignore_on_user_bool){ .ignore_on_user = false })
#define IGNORE_ON_USER ((struct ignore_on_user_bool){ .ignore_on_user = true })

struct ignore_on_userdebug_bool { bool ignore_on_userdebug; };
#define LOAD_ON_USERDEBUG ((struct ignore_on_userdebug_bool){ .ignore_on_userdebug = false })
#define IGNORE_ON_USERDEBUG ((struct ignore_on_userdebug_bool){ .ignore_on_userdebug = true })


// Length of strings (incl. selinux_context and pin_subdir)
// in the bpf_map_def and bpf_prog_def structs.
//
// WARNING: YOU CANNOT *EVER* CHANGE THESE
// as this would affect the structure size in backwards incompatible ways
// and break mainline module loading on older Android T devices
#define BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE 32
#define BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE 32

/*
 * Map structure to be used by Android eBPF C programs. The Android eBPF loader
 * uses this structure from eBPF object to create maps at boot time.
 *
 * The eBPF C program should define structure in the maps section using
 * SECTION("maps") otherwise it will be ignored by the eBPF loader.
 *
 * For example:
 *   const struct bpf_map_def SECTION("maps") mymap { .type=... , .key_size=... }
 *
 * See 'bpf_helpers.h' for helpful macros for eBPF program use.
 */
struct bpf_map_def {
    enum bpf_map_type type;
    unsigned int key_size;
    unsigned int value_size;
    unsigned int max_entries;
    unsigned int map_flags;

    // The following are not supported by the Android bpfloader:
    //   unsigned int inner_map_idx;
    //   unsigned int numa_node;

    unsigned int zero;  // uid_t, for compat with old (buggy) bpfloader must be AID_ROOT == 0
    unsigned int gid;   // gid_t
    unsigned int mode;  // mode_t

    // The following fields were added in version 0.1
    unsigned int bpfloader_min_ver;  // if missing, defaults to 0, ie. v0.0
    unsigned int bpfloader_max_ver;  // if missing, defaults to 0x10000, ie. v1.0

    // The following fields were added in version 0.2 (S)
    // kernelVersion() must be >= min_kver and < max_kver
    unsigned int min_kver;
    unsigned int max_kver;

    // The following fields were added in version 0.18 (T)
    //
    // These are fixed length strings, padded with null bytes
    //
    // Warning: supported values depend on .o location
    // (additionally a newer Android OS and/or bpfloader may support more values)
    //
    // overrides default selinux context (which is based on pin subdir)
    char selinux_context[BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE];
    //
    // overrides default prefix (which is based on .o location)
    char pin_subdir[BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE];

    bool shared;  // use empty string as 'file' component of pin path - allows cross .o map sharing

    // The following 3 ignore_on_* fields were added in version 0.32 (U). These are ignored in
    // older bpfloader versions, and zero in programs compiled before 0.32.
    bool ignore_on_eng:1;
    bool ignore_on_user:1;
    bool ignore_on_userdebug:1;
    // The following 5 ignore_on_* fields were added in version 0.38 (U). These are ignored in
    // older bpfloader versions, and zero in programs compiled before 0.38.
    // These are tests on the kernel architecture, ie. they ignore userspace bit-ness.
    bool ignore_on_arm32:1;
    bool ignore_on_aarch64:1;
    bool ignore_on_x86_32:1;
    bool ignore_on_x86_64:1;
    bool ignore_on_riscv64:1;

    char pad0[2];  // manually pad up to 4 byte alignment, may be used for extensions in the future

    unsigned int uid;   // uid_t
};

_Static_assert(sizeof(((struct bpf_map_def *)0)->selinux_context) == 32, "must be 32 bytes");
_Static_assert(sizeof(((struct bpf_map_def *)0)->pin_subdir) == 32, "must be 32 bytes");

// This needs to be updated whenever the above structure definition is expanded.
_Static_assert(sizeof(struct bpf_map_def) == 120, "sizeof struct bpf_map_def != 120");
_Static_assert(__alignof__(struct bpf_map_def) == 4, "__alignof__ struct bpf_map_def != 4");
_Static_assert(_Alignof(struct bpf_map_def) == 4, "_Alignof struct bpf_map_def != 4");

struct bpf_prog_def {
    unsigned int uid;
    unsigned int gid;

    // kernelVersion() must be >= min_kver and < max_kver
    unsigned int min_kver;
    unsigned int max_kver;

    bool optional;  // program section (ie. function) may fail to load, continue onto next func.

    // The following 3 ignore_on_* fields were added in version 0.33 (U). These are ignored in
    // older bpfloader versions, and zero in programs compiled before 0.33.
    bool ignore_on_eng:1;
    bool ignore_on_user:1;
    bool ignore_on_userdebug:1;
    // The following 5 ignore_on_* fields were added in version 0.38 (U). These are ignored in
    // older bpfloader versions, and zero in programs compiled before 0.38.
    // These are tests on the kernel architecture, ie. they ignore userspace bit-ness.
    bool ignore_on_arm32:1;
    bool ignore_on_aarch64:1;
    bool ignore_on_x86_32:1;
    bool ignore_on_x86_64:1;
    bool ignore_on_riscv64:1;

    char pad0[2];  // manually pad up to 4 byte alignment, may be used for extensions in the future

    // The following fields were added in version 0.1
    unsigned int bpfloader_min_ver;  // if missing, defaults to 0, ie. v0.0
    unsigned int bpfloader_max_ver;  // if missing, defaults to 0x10000, ie. v1.0

    // The following fields were added in version 0.18, see description up above in bpf_map_def
    char selinux_context[BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE];
    char pin_subdir[BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE];
};

_Static_assert(sizeof(((struct bpf_prog_def *)0)->selinux_context) == 32, "must be 32 bytes");
_Static_assert(sizeof(((struct bpf_prog_def *)0)->pin_subdir) == 32, "must be 32 bytes");

// This needs to be updated whenever the above structure definition is expanded.
_Static_assert(sizeof(struct bpf_prog_def) == 92, "sizeof struct bpf_prog_def != 92");
_Static_assert(__alignof__(struct bpf_prog_def) == 4, "__alignof__ struct bpf_prog_def != 4");
_Static_assert(_Alignof(struct bpf_prog_def) == 4, "_Alignof struct bpf_prog_def != 4");
