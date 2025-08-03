/* Common BPF helpers to be used by all BPF programs loaded by Android */

#include <linux/bpf.h>
#include <stdbool.h>
#include <stdint.h>

#include "bpf_map_def.h"

/******************************************************************************
 * WARNING: CHANGES TO THIS FILE OUTSIDE OF AOSP/MASTER ARE LIKELY TO BREAK   *
 * DEVICE COMPATIBILITY WITH MAINLINE MODULES SHIPPING EBPF CODE.             *
 *                                                                            *
 * THIS WILL LIKELY RESULT IN BRICKED DEVICES AT SOME ARBITRARY FUTURE TIME   *
 *                                                                            *
 * THAT GOES ESPECIALLY FOR THE 'SECTION' 'LICENSE' AND 'CRITICAL' MACROS     *
 *                                                                            *
 * We strongly suggest that if you need changes to bpfloader functionality    *
 * you get your changes reviewed and accepted into aosp/master.               *
 *                                                                            *
 ******************************************************************************/

// The actual versions of the bpfloader that shipped in various Android releases

// Android P/Q/R: BpfLoader was initially part of netd,
// this was later split out into a standalone binary, but was unversioned.

// Android S / 12 (api level 31) - added 'tethering' mainline eBPF support
#define BPFLOADER_S_VERSION 2u

// Android T / 13 (api level 33) - support for shared/selinux_context/pindir
#define BPFLOADER_T_VERSION 19u

// BpfLoader v0.25+ support obj@ver.o files
#define BPFLOADER_OBJ_AT_VER_VERSION 25u

// Bpfloader v0.33+ supports {map,prog}.ignore_on_{eng,user,userdebug}
#define BPFLOADER_IGNORED_ON_VERSION 33u

// Android U / 14 (api level 34) - various new program types added
#define BPFLOADER_U_VERSION 37u

/* For mainline module use, you can #define BPFLOADER_{MIN/MAX}_VER
 * before #include "bpf_helpers.h" to change which bpfloaders will
 * process the resulting .o file.
 *
 * While this will work outside of mainline too, there just is no point to
 * using it when the .o and the bpfloader ship in sync with each other.
 * In which case it's just best to use the default.
 */
#ifndef BPFLOADER_MIN_VER
#define BPFLOADER_MIN_VER COMPILE_FOR_BPFLOADER_VERSION
#endif

#ifndef BPFLOADER_MAX_VER
#define BPFLOADER_MAX_VER DEFAULT_BPFLOADER_MAX_VER
#endif

/* place things in different elf sections */
#define SECTION(NAME) __attribute__((section(NAME), used))

/* Must be present in every program, example usage:
 *   LICENSE("GPL"); or LICENSE("Apache 2.0");
 *
 * We also take this opportunity to embed a bunch of other useful values in
 * the resulting .o (This is to enable some limited forward compatibility
 * with mainline module shipped ebpf programs)
 *
 * The bpfloader_{min/max}_ver defines the [min, max) range of bpfloader
 * versions that should load this .o file (bpfloaders outside of this range
 * will simply ignore/skip this *entire* .o)
 * The [inclusive,exclusive) matches what we do for kernel ver dependencies.
 *
 * The size_of_bpf_{map,prog}_def allow the bpfloader to load programs where
 * these structures have been extended with additional fields (they will of
 * course simply be ignored then).
 *
 * If missing, bpfloader_{min/max}_ver default to 0/0x10000 ie. [v0.0, v1.0),
 * while size_of_bpf_{map/prog}_def default to 32/20 which are the v0.0 sizes.
 */
#define LICENSE(NAME)                                                                           \
    unsigned int _bpfloader_min_ver SECTION("bpfloader_min_ver") = BPFLOADER_MIN_VER;           \
    unsigned int _bpfloader_max_ver SECTION("bpfloader_max_ver") = BPFLOADER_MAX_VER;           \
    size_t _size_of_bpf_map_def SECTION("size_of_bpf_map_def") = sizeof(struct bpf_map_def);    \
    size_t _size_of_bpf_prog_def SECTION("size_of_bpf_prog_def") = sizeof(struct bpf_prog_def); \
    char _license[] SECTION("license") = (NAME)

/* This macro disables loading BTF map debug information on Android <=U *and* all user builds.
 *
 * Note: Bpfloader v0.39+ honours 'btf_user_min_bpfloader_ver' on user builds,
 * and 'btf_min_bpfloader_ver' on non-user builds.
 * Older BTF capable versions unconditionally honour 'btf_min_bpfloader_ver'
 */
#define DISABLE_BTF_ON_USER_BUILDS() \
    unsigned _btf_min_bpfloader_ver SECTION("btf_min_bpfloader_ver") = 39u; \
    unsigned _btf_user_min_bpfloader_ver SECTION("btf_user_min_bpfloader_ver") = 0xFFFFFFFFu

/* flag the resulting bpf .o file as critical to system functionality,
 * loading all kernel version appropriate programs in it must succeed
 * for bpfloader success
 */
#define CRITICAL(REASON) char _critical[] SECTION("critical") = (REASON)

/*
 * Helper functions called from eBPF programs written in C. These are
 * implemented in the kernel sources.
 */

struct kver_uint { unsigned int kver; };
#define KVER_(v) ((struct kver_uint){ .kver = (v) })
#define KVER(a, b, c) KVER_(((a) << 24) + ((b) << 16) + (c))
#define KVER_NONE KVER_(0)
#define KVER_4_14 KVER(4, 14, 0)
#define KVER_4_19 KVER(4, 19, 0)
#define KVER_5_4 KVER(5, 4, 0)
#define KVER_5_8 KVER(5, 8, 0)
#define KVER_5_9 KVER(5, 9, 0)
#define KVER_5_15 KVER(5, 15, 0)
#define KVER_INF KVER_(0xFFFFFFFFu)

#define KVER_IS_AT_LEAST(kver, a, b, c) ((kver).kver >= KVER(a, b, c).kver)

/*
 * BPFFS (ie. /sys/fs/bpf) labelling is as follows:
 *   subdirectory   selinux context      mainline  usecase / usable by
 *   /              fs_bpf               no [*]    core operating system (ie. platform)
 *   /loader        fs_bpf_loader        no, U+    (as yet unused)
 *   /net_private   fs_bpf_net_private   yes, T+   network_stack
 *   /net_shared    fs_bpf_net_shared    yes, T+   network_stack & system_server
 *   /netd_readonly fs_bpf_netd_readonly yes, T+   network_stack & system_server & r/o to netd
 *   /netd_shared   fs_bpf_netd_shared   yes, T+   network_stack & system_server & netd [**]
 *   /tethering     fs_bpf_tethering     yes, S+   network_stack
 *   /vendor        fs_bpf_vendor        no, T+    vendor
 *
 * [*] initial support for bpf was added back in P,
 *     but things worked differently back then with no bpfloader,
 *     and instead netd doing stuff by hand,
 *     bpfloader with pinning into /sys/fs/bpf was (I believe) added in Q
 *     (and was definitely there in R).
 *
 * [**] additionally bpf programs are accessible to netutils_wrapper
 *      for use by iptables xt_bpf extensions.
 *
 * See cs/p:aosp-master%20-file:prebuilts/%20file:genfs_contexts%20"genfscon%20bpf"
 */

/* generic functions */

/*
 * Type-unsafe bpf map functions - avoid if possible.
 *
 * Using these it is possible to pass in keys/values of the wrong type/size,
 * or, for 'bpf_map_lookup_elem_unsafe' receive into a pointer to the wrong type.
 * You will not get a compile time failure, and for certain types of errors you
 * might not even get a failure from the kernel's ebpf verifier during program load,
 * instead stuff might just not work right at runtime.
 *
 * Instead please use:
 *   DEFINE_BPF_MAP(foo_map, TYPE, KeyType, ValueType, num_entries)
 * where TYPE can be something like HASH or ARRAY, and num_entries is an integer.
 *
 * This defines the map (hence this should not be used in a header file included
 * from multiple locations) and provides type safe accessors:
 *   ValueType * bpf_foo_map_lookup_elem(const KeyType *)
 *   int bpf_foo_map_update_elem(const KeyType *, const ValueType *, flags)
 *   int bpf_foo_map_delete_elem(const KeyType *)
 *
 * This will make sure that if you change the type of a map you'll get compile
 * errors at any spots you forget to update with the new type.
 *
 * Note: these all take pointers to const map because from the C/eBPF point of view
 * the map struct is really just a readonly map definition of the in kernel object.
 * Runtime modification of the map defining struct is meaningless, since
 * the contents is only ever used during bpf program loading & map creation
 * by the bpf loader, and not by the eBPF program itself.
 */
static void* (*bpf_map_lookup_elem_unsafe)(const struct bpf_map_def* map,
                                           const void* key) = (void*)BPF_FUNC_map_lookup_elem;
static int (*bpf_map_update_elem_unsafe)(const struct bpf_map_def* map, const void* key,
                                         const void* value, unsigned long long flags) = (void*)
        BPF_FUNC_map_update_elem;
static int (*bpf_map_delete_elem_unsafe)(const struct bpf_map_def* map,
                                         const void* key) = (void*)BPF_FUNC_map_delete_elem;
static int (*bpf_ringbuf_output_unsafe)(const struct bpf_map_def* ringbuf,
                                        const void* data, __u64 size, __u64 flags) = (void*)
        BPF_FUNC_ringbuf_output;
static void* (*bpf_ringbuf_reserve_unsafe)(const struct bpf_map_def* ringbuf,
                                           __u64 size, __u64 flags) = (void*)
        BPF_FUNC_ringbuf_reserve;
static void (*bpf_ringbuf_submit_unsafe)(const void* data, __u64 flags) = (void*)
        BPF_FUNC_ringbuf_submit;

#define BPF_ANNOTATE_KV_PAIR(name, type_key, type_val)  \
        struct ____btf_map_##name {                     \
                type_key key;                           \
                type_val value;                         \
        };                                              \
        struct ____btf_map_##name                       \
        __attribute__ ((section(".maps." #name), used)) \
                ____btf_map_##name = { }

#define BPF_ASSERT_LOADER_VERSION(min_loader, ignore_eng, ignore_user, ignore_userdebug) \
    _Static_assert(                                                                      \
        (min_loader) >= BPFLOADER_IGNORED_ON_VERSION ||                                  \
            !((ignore_eng).ignore_on_eng ||                                              \
              (ignore_user).ignore_on_user ||                                            \
              (ignore_userdebug).ignore_on_userdebug),                                   \
        "bpfloader min version must be >= 0.33 in order to use ignored_on");

#define DEFINE_BPF_MAP_BASE(the_map, TYPE, keysize, valuesize, num_entries, \
                            usr, grp, md, selinux, pindir, share, minkver,  \
                            maxkver, minloader, maxloader, ignore_eng,      \
                            ignore_user, ignore_userdebug)                  \
    const struct bpf_map_def SECTION("maps") the_map = {                    \
        .type = BPF_MAP_TYPE_##TYPE,                                        \
        .key_size = (keysize),                                              \
        .value_size = (valuesize),                                          \
        .max_entries = (num_entries),                                       \
        .map_flags = 0,                                                     \
        .uid = (usr),                                                       \
        .gid = (grp),                                                       \
        .mode = (md),                                                       \
        .bpfloader_min_ver = (minloader),                                   \
        .bpfloader_max_ver = (maxloader),                                   \
        .min_kver = (minkver).kver,                                         \
        .max_kver = (maxkver).kver,                                         \
        .selinux_context = (selinux),                                       \
        .pin_subdir = (pindir),                                             \
        .shared = (share).shared,                                           \
        .ignore_on_eng = (ignore_eng).ignore_on_eng,                        \
        .ignore_on_user = (ignore_user).ignore_on_user,                     \
        .ignore_on_userdebug = (ignore_userdebug).ignore_on_userdebug,      \
    };                                                                      \
    BPF_ASSERT_LOADER_VERSION(minloader, ignore_eng, ignore_user, ignore_userdebug);

// Type safe macro to declare a ring buffer and related output functions.
// Compatibility:
// * BPF ring buffers are only available kernels 5.8 and above. Any program
//   accessing the ring buffer should set a program level min_kver >= 5.8.
// * The definition below sets a map min_kver of 5.8 which requires targeting
//   a BPFLOADER_MIN_VER >= BPFLOADER_S_VERSION.
#define DEFINE_BPF_RINGBUF_EXT(the_map, ValueType, size_bytes, usr, grp, md,   \
                               selinux, pindir, share, min_loader, max_loader, \
                               ignore_eng, ignore_user, ignore_userdebug)      \
    DEFINE_BPF_MAP_BASE(the_map, RINGBUF, 0, 0, size_bytes, usr, grp, md,      \
                        selinux, pindir, share, KVER_5_8, KVER_INF,            \
                        min_loader, max_loader, ignore_eng, ignore_user,       \
                        ignore_userdebug);                                     \
                                                                               \
    _Static_assert((size_bytes) >= 4096, "min 4 kiB ringbuffer size");         \
    _Static_assert((size_bytes) <= 0x10000000, "max 256 MiB ringbuffer size"); \
    _Static_assert(((size_bytes) & ((size_bytes) - 1)) == 0,                   \
                   "ring buffer size must be a power of two");                 \
                                                                               \
    static inline __always_inline __unused int bpf_##the_map##_output(         \
            const ValueType* v) {                                              \
        return bpf_ringbuf_output_unsafe(&the_map, v, sizeof(*v), 0);          \
    }                                                                          \
                                                                               \
    static inline __always_inline __unused                                     \
            ValueType* bpf_##the_map##_reserve() {                             \
        return bpf_ringbuf_reserve_unsafe(&the_map, sizeof(ValueType), 0);     \
    }                                                                          \
                                                                               \
    static inline __always_inline __unused void bpf_##the_map##_submit(        \
            const ValueType* v) {                                              \
        bpf_ringbuf_submit_unsafe(v, 0);                                       \
    }

/* There exist buggy kernels with pre-T OS, that due to
 * kernel patch "[ALPS05162612] bpf: fix ubsan error"
 * do not support userspace writes into non-zero index of bpf map arrays.
 *
 * We use this assert to prevent us from being able to define such a map.
 */

#ifdef THIS_BPF_PROGRAM_IS_FOR_TEST_PURPOSES_ONLY
#define BPF_MAP_ASSERT_OK(type, entries, mode)
#elif BPFLOADER_MIN_VER >= BPFLOADER_T_VERSION
#define BPF_MAP_ASSERT_OK(type, entries, mode)
#else
#define BPF_MAP_ASSERT_OK(type, entries, mode) \
  _Static_assert(((type) != BPF_MAP_TYPE_ARRAY) || ((entries) <= 1) || !((mode) & 0222), \
  "Writable arrays with more than 1 element not supported on pre-T devices.")
#endif

/* type safe macro to declare a map and related accessor functions */
#define DEFINE_BPF_MAP_EXT(the_map, TYPE, KeyType, ValueType, num_entries, usr, grp, md,         \
                           selinux, pindir, share, min_loader, max_loader, ignore_eng,           \
                           ignore_user, ignore_userdebug)                                        \
  DEFINE_BPF_MAP_BASE(the_map, TYPE, sizeof(KeyType), sizeof(ValueType),                         \
                      num_entries, usr, grp, md, selinux, pindir, share,                         \
                      KVER_NONE, KVER_INF, min_loader, max_loader,                               \
                      ignore_eng, ignore_user, ignore_userdebug);                                \
    BPF_MAP_ASSERT_OK(BPF_MAP_TYPE_##TYPE, (num_entries), (md));                                 \
    _Static_assert(sizeof(KeyType) < 1024, "aosp/2370288 requires < 1024 byte keys");            \
    _Static_assert(sizeof(ValueType) < 65536, "aosp/2370288 requires < 65536 byte values");      \
    BPF_ANNOTATE_KV_PAIR(the_map, KeyType, ValueType);                                           \
                                                                                                 \
    static inline __always_inline __unused ValueType* bpf_##the_map##_lookup_elem(               \
            const KeyType* k) {                                                                  \
        return bpf_map_lookup_elem_unsafe(&the_map, k);                                          \
    };                                                                                           \
                                                                                                 \
    static inline __always_inline __unused int bpf_##the_map##_update_elem(                      \
            const KeyType* k, const ValueType* v, unsigned long long flags) {                    \
        return bpf_map_update_elem_unsafe(&the_map, k, v, flags);                                \
    };                                                                                           \
                                                                                                 \
    static inline __always_inline __unused int bpf_##the_map##_delete_elem(const KeyType* k) {   \
        return bpf_map_delete_elem_unsafe(&the_map, k);                                          \
    };

#ifndef DEFAULT_BPF_MAP_SELINUX_CONTEXT
#define DEFAULT_BPF_MAP_SELINUX_CONTEXT ""
#endif

#ifndef DEFAULT_BPF_MAP_PIN_SUBDIR
#define DEFAULT_BPF_MAP_PIN_SUBDIR ""
#endif

#ifndef DEFAULT_BPF_MAP_UID
#define DEFAULT_BPF_MAP_UID AID_ROOT
#elif BPFLOADER_MIN_VER < 28u
#error "Bpf Map UID must be left at default of AID_ROOT for BpfLoader prior to v0.28"
#endif

#define DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, usr, grp, md)     \
    DEFINE_BPF_MAP_EXT(the_map, TYPE, KeyType, ValueType, num_entries, usr, grp, md,         \
                       DEFAULT_BPF_MAP_SELINUX_CONTEXT, DEFAULT_BPF_MAP_PIN_SUBDIR, PRIVATE, \
                       BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, LOAD_ON_ENG,                    \
                       LOAD_ON_USER, LOAD_ON_USERDEBUG)

#define DEFINE_BPF_MAP(the_map, TYPE, KeyType, ValueType, num_entries) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, \
                       DEFAULT_BPF_MAP_UID, AID_ROOT, 0600)

#define DEFINE_BPF_MAP_RO(the_map, TYPE, KeyType, ValueType, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, \
                       DEFAULT_BPF_MAP_UID, gid, 0440)

#define DEFINE_BPF_MAP_GWO(the_map, TYPE, KeyType, ValueType, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, \
                       DEFAULT_BPF_MAP_UID, gid, 0620)

#define DEFINE_BPF_MAP_GRO(the_map, TYPE, KeyType, ValueType, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, \
                       DEFAULT_BPF_MAP_UID, gid, 0640)

#define DEFINE_BPF_MAP_GRW(the_map, TYPE, KeyType, ValueType, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, KeyType, ValueType, num_entries, \
                       DEFAULT_BPF_MAP_UID, gid, 0660)

// LLVM eBPF builtins: they directly generate BPF_LD_ABS/BPF_LD_IND (skb may be ignored?)
unsigned long long load_byte(void* skb, unsigned long long off) asm("llvm.bpf.load.byte");
unsigned long long load_half(void* skb, unsigned long long off) asm("llvm.bpf.load.half");
unsigned long long load_word(void* skb, unsigned long long off) asm("llvm.bpf.load.word");

static int (*bpf_probe_read)(void* dst, int size, void* unsafe_ptr) = (void*) BPF_FUNC_probe_read;
static int (*bpf_probe_read_str)(void* dst, int size, void* unsafe_ptr) = (void*) BPF_FUNC_probe_read_str;
static int (*bpf_probe_read_user)(void* dst, int size, const void* unsafe_ptr) = (void*)BPF_FUNC_probe_read_user;
static int (*bpf_probe_read_user_str)(void* dst, int size, const void* unsafe_ptr) = (void*) BPF_FUNC_probe_read_user_str;
static unsigned long long (*bpf_ktime_get_ns)(void) = (void*) BPF_FUNC_ktime_get_ns;
static unsigned long long (*bpf_ktime_get_boot_ns)(void) = (void*)BPF_FUNC_ktime_get_boot_ns;
static int (*bpf_trace_printk)(const char* fmt, int fmt_size, ...) = (void*) BPF_FUNC_trace_printk;
static unsigned long long (*bpf_get_current_pid_tgid)(void) = (void*) BPF_FUNC_get_current_pid_tgid;
static unsigned long long (*bpf_get_current_uid_gid)(void) = (void*) BPF_FUNC_get_current_uid_gid;
static unsigned long long (*bpf_get_smp_processor_id)(void) = (void*) BPF_FUNC_get_smp_processor_id;
static long (*bpf_get_stackid)(void* ctx, void* map, uint64_t flags) = (void*) BPF_FUNC_get_stackid;
static long (*bpf_get_current_comm)(void* buf, uint32_t buf_size) = (void*) BPF_FUNC_get_current_comm;

#define DEFINE_BPF_PROG_EXT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv,  \
                            min_loader, max_loader, opt, selinux, pindir, ignore_eng,    \
                            ignore_user, ignore_userdebug)                               \
    const struct bpf_prog_def SECTION("progs") the_prog##_def = {                        \
        .uid = (prog_uid),                                                               \
        .gid = (prog_gid),                                                               \
        .min_kver = (min_kv).kver,                                                       \
        .max_kver = (max_kv).kver,                                                       \
        .optional = (opt).optional,                                                      \
        .bpfloader_min_ver = (min_loader),                                               \
        .bpfloader_max_ver = (max_loader),                                               \
        .selinux_context = (selinux),                                                    \
        .pin_subdir = (pindir),                                                          \
        .ignore_on_eng = (ignore_eng).ignore_on_eng,                                     \
        .ignore_on_user = (ignore_user).ignore_on_user,                                  \
        .ignore_on_userdebug = (ignore_userdebug).ignore_on_userdebug,                   \
    };                                                                                   \
    SECTION(SECTION_NAME)                                                                \
    int the_prog

#ifndef DEFAULT_BPF_PROG_SELINUX_CONTEXT
#define DEFAULT_BPF_PROG_SELINUX_CONTEXT ""
#endif

#ifndef DEFAULT_BPF_PROG_PIN_SUBDIR
#define DEFAULT_BPF_PROG_PIN_SUBDIR ""
#endif

#define DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv, \
                                       opt)                                                        \
    DEFINE_BPF_PROG_EXT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv,                \
                        BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, opt,                                 \
                        DEFAULT_BPF_PROG_SELINUX_CONTEXT, DEFAULT_BPF_PROG_PIN_SUBDIR,             \
                        LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

// Programs (here used in the sense of functions/sections) marked optional are allowed to fail
// to load (for example due to missing kernel patches).
// The bpfloader will just ignore these failures and continue processing the next section.
//
// A non-optional program (function/section) failing to load causes a failure and aborts
// processing of the entire .o, if the .o is additionally marked critical, this will result
// in the entire bpfloader process terminating with a failure and not setting the bpf.progs_loaded
// system property.  This in turn results in waitForProgsLoaded() never finishing.
//
// ie. a non-optional program in a critical .o is mandatory for kernels matching the min/max kver.

// programs requiring a kernel version >= min_kv && < max_kv
#define DEFINE_BPF_PROG_KVER_RANGE(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv) \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv, \
                                   MANDATORY)
#define DEFINE_OPTIONAL_BPF_PROG_KVER_RANGE(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, \
                                            max_kv)                                             \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, max_kv, \
                                   OPTIONAL)

// programs requiring a kernel version >= min_kv
#define DEFINE_BPF_PROG_KVER(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv)                 \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, KVER_INF, \
                                   MANDATORY)
#define DEFINE_OPTIONAL_BPF_PROG_KVER(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv)        \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, min_kv, KVER_INF, \
                                   OPTIONAL)

// programs with no kernel version requirements
#define DEFINE_BPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog) \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, KVER_NONE, KVER_INF, \
                                   MANDATORY)
#define DEFINE_OPTIONAL_BPF_PROG(SECTION_NAME, prog_uid, prog_gid, the_prog) \
    DEFINE_BPF_PROG_KVER_RANGE_OPT(SECTION_NAME, prog_uid, prog_gid, the_prog, KVER_NONE, KVER_INF, \
                                   OPTIONAL)
