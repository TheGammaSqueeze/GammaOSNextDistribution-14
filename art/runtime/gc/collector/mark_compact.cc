/*
 * Copyright 2021 The Android Open Source Project
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

#include <fcntl.h>
// Glibc v2.19 doesn't include these in fcntl.h so host builds will fail without.
#if !defined(FALLOC_FL_PUNCH_HOLE) || !defined(FALLOC_FL_KEEP_SIZE)
#include <linux/falloc.h>
#endif
#include <linux/userfaultfd.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>

#include <fstream>
#include <numeric>
#include <string>
#include <string_view>
#include <vector>

#include "android-base/file.h"
#include "android-base/parsebool.h"
#include "android-base/parseint.h"
#include "android-base/properties.h"
#include "android-base/strings.h"
#include "base/file_utils.h"
#include "base/memfd.h"
#include "base/quasi_atomic.h"
#include "base/systrace.h"
#include "base/utils.h"
#include "gc/accounting/mod_union_table-inl.h"
#include "gc/collector_type.h"
#include "gc/reference_processor.h"
#include "gc/space/bump_pointer_space.h"
#include "gc/task_processor.h"
#include "gc/verification-inl.h"
#include "jit/jit_code_cache.h"
#include "mark_compact-inl.h"
#include "mirror/object-refvisitor-inl.h"
#include "read_barrier_config.h"
#include "scoped_thread_state_change-inl.h"
#include "sigchain.h"
#include "thread_list.h"

#ifdef ART_TARGET_ANDROID
#include "android-modules-utils/sdk_level.h"
#include "com_android_art.h"
#endif

#ifndef __BIONIC__
#ifndef MREMAP_DONTUNMAP
#define MREMAP_DONTUNMAP 4
#endif
#ifndef MAP_FIXED_NOREPLACE
#define MAP_FIXED_NOREPLACE 0x100000
#endif
#ifndef __NR_userfaultfd
#if defined(__x86_64__)
#define __NR_userfaultfd 323
#elif defined(__i386__)
#define __NR_userfaultfd 374
#elif defined(__aarch64__)
#define __NR_userfaultfd 282
#elif defined(__arm__)
#define __NR_userfaultfd 388
#else
#error "__NR_userfaultfd undefined"
#endif
#endif  // __NR_userfaultfd
#endif  // __BIONIC__

#ifdef ART_TARGET_ANDROID
namespace {

using ::android::base::GetBoolProperty;
using ::android::base::ParseBool;
using ::android::base::ParseBoolResult;
using ::android::modules::sdklevel::IsAtLeastV;

}  // namespace
#endif

namespace art HIDDEN {

static bool HaveMremapDontunmap() {
  const size_t page_size = GetPageSizeSlow();
  void* old = mmap(nullptr, page_size, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_SHARED, -1, 0);
  CHECK_NE(old, MAP_FAILED);
  void* addr = mremap(old, page_size, page_size, MREMAP_MAYMOVE | MREMAP_DONTUNMAP, nullptr);
  CHECK_EQ(munmap(old, page_size), 0);
  if (addr != MAP_FAILED) {
    CHECK_EQ(munmap(addr, page_size), 0);
    return true;
  } else {
    return false;
  }
}
// We require MREMAP_DONTUNMAP functionality of the mremap syscall, which was
// introduced in 5.13 kernel version. But it was backported to GKI kernels.
static bool gHaveMremapDontunmap = IsKernelVersionAtLeast(5, 13) || HaveMremapDontunmap();
// Bitmap of features supported by userfaultfd. This is obtained via uffd API ioctl.
static uint64_t gUffdFeatures = 0;
// Both, missing and minor faults on shmem are needed only for minor-fault mode.
static constexpr uint64_t kUffdFeaturesForMinorFault =
    UFFD_FEATURE_MISSING_SHMEM | UFFD_FEATURE_MINOR_SHMEM;
static constexpr uint64_t kUffdFeaturesForSigbus = UFFD_FEATURE_SIGBUS;

// We consider SIGBUS feature necessary to enable this GC as it's superior than
// threading-based implementation for janks. However, since we have the latter
// already implemented, for testing purposes, we allow choosing either of the
// two at boot time in the constructor below.
// We may want minor-fault in future to be available for making jit-code-cache
// updation concurrent, which uses shmem.
bool KernelSupportsUffd() {
#ifdef __linux__
  if (gHaveMremapDontunmap) {
    int fd = syscall(__NR_userfaultfd, O_CLOEXEC | UFFD_USER_MODE_ONLY);
    // On non-android devices we may not have the kernel patches that restrict
    // userfaultfd to user mode. But that is not a security concern as we are
    // on host. Therefore, attempt one more time without UFFD_USER_MODE_ONLY.
    if (!kIsTargetAndroid && fd == -1 && errno == EINVAL) {
      fd = syscall(__NR_userfaultfd, O_CLOEXEC);
    }
    if (fd >= 0) {
      // We are only fetching the available features, which is returned by the
      // ioctl.
      struct uffdio_api api = {.api = UFFD_API, .features = 0, .ioctls = 0};
      CHECK_EQ(ioctl(fd, UFFDIO_API, &api), 0) << "ioctl_userfaultfd : API:" << strerror(errno);
      gUffdFeatures = api.features;
      close(fd);
      // Minimum we need is sigbus feature for using userfaultfd.
      return (api.features & kUffdFeaturesForSigbus) == kUffdFeaturesForSigbus;
    }
  }
#endif
  return false;
}

// The other cases are defined as constexpr in runtime/read_barrier_config.h
#if !defined(ART_FORCE_USE_READ_BARRIER) && defined(ART_USE_READ_BARRIER)
// Returns collector type asked to be used on the cmdline.
static gc::CollectorType FetchCmdlineGcType() {
  std::string argv;
  gc::CollectorType gc_type = gc::CollectorType::kCollectorTypeNone;
  if (android::base::ReadFileToString("/proc/self/cmdline", &argv)) {
    if (argv.find("-Xgc:CMC") != std::string::npos) {
      gc_type = gc::CollectorType::kCollectorTypeCMC;
    } else if (argv.find("-Xgc:CC") != std::string::npos) {
      gc_type = gc::CollectorType::kCollectorTypeCC;
    }
  }
  return gc_type;
}

#ifdef ART_TARGET_ANDROID
static int GetOverrideCacheInfoFd() {
  std::string args_str;
  if (!android::base::ReadFileToString("/proc/self/cmdline", &args_str)) {
    LOG(WARNING) << "Failed to load /proc/self/cmdline";
    return -1;
  }
  std::vector<std::string_view> args;
  Split(std::string_view(args_str), /*separator=*/'\0', &args);
  for (std::string_view arg : args) {
    if (android::base::ConsumePrefix(&arg, "--cache-info-fd=")) {  // This is a dex2oat flag.
      int fd;
      if (!android::base::ParseInt(std::string(arg), &fd)) {
        LOG(ERROR) << "Failed to parse --cache-info-fd (value: '" << arg << "')";
        return -1;
      }
      return fd;
    }
  }
  return -1;
}

static std::unordered_map<std::string, std::string> GetCachedProperties() {
  // For simplicity, we don't handle multiple calls because otherwise we would have to reset the fd.
  static bool called = false;
  CHECK(!called) << "GetCachedBoolProperty can be called only once";
  called = true;

  std::string cache_info_contents;
  int fd = GetOverrideCacheInfoFd();
  if (fd >= 0) {
    if (!android::base::ReadFdToString(fd, &cache_info_contents)) {
      PLOG(ERROR) << "Failed to read cache-info from fd " << fd;
      return {};
    }
  } else {
    std::string path = GetApexDataDalvikCacheDirectory(InstructionSet::kNone) + "/cache-info.xml";
    if (!android::base::ReadFileToString(path, &cache_info_contents)) {
      // If the file is not found, then we are in chroot or in a standalone runtime process (e.g.,
      // IncidentHelper), or odsign/odrefresh failed to generate and sign the cache info. There's
      // nothing we can do.
      if (errno != ENOENT) {
        PLOG(ERROR) << "Failed to read cache-info from the default path";
      }
      return {};
    }
  }

  std::optional<com::android::art::CacheInfo> cache_info =
      com::android::art::parse(cache_info_contents.c_str());
  if (!cache_info.has_value()) {
    // This should never happen.
    LOG(ERROR) << "Failed to parse cache-info";
    return {};
  }
  const com::android::art::KeyValuePairList* list = cache_info->getFirstSystemProperties();
  if (list == nullptr) {
    // This should never happen.
    LOG(ERROR) << "Missing system properties from cache-info";
    return {};
  }
  const std::vector<com::android::art::KeyValuePair>& properties = list->getItem();
  std::unordered_map<std::string, std::string> result;
  for (const com::android::art::KeyValuePair& pair : properties) {
    result[pair.getK()] = pair.getV();
  }
  return result;
}

static bool GetCachedBoolProperty(
    const std::unordered_map<std::string, std::string>& cached_properties,
    const std::string& key,
    bool default_value) {
  auto it = cached_properties.find(key);
  if (it == cached_properties.end()) {
    return default_value;
  }
  ParseBoolResult result = ParseBool(it->second);
  switch (result) {
    case ParseBoolResult::kTrue:
      return true;
    case ParseBoolResult::kFalse:
      return false;
    case ParseBoolResult::kError:
      return default_value;
  }
}

static bool SysPropSaysUffdGc() {
  // The phenotype flag can change at time time after boot, but it shouldn't take effect until a
  // reboot. Therefore, we read the phenotype flag from the cache info, which is generated on boot.
  std::unordered_map<std::string, std::string> cached_properties = GetCachedProperties();
  bool phenotype_enable = GetCachedBoolProperty(
      cached_properties, "persist.device_config.runtime_native_boot.enable_uffd_gc_2", false);
  bool phenotype_force_disable = GetCachedBoolProperty(
      cached_properties, "persist.device_config.runtime_native_boot.force_disable_uffd_gc", false);
  bool build_enable = GetBoolProperty("ro.dalvik.vm.enable_uffd_gc", false);
  bool is_at_most_u = !IsAtLeastV();
  return (phenotype_enable || build_enable || is_at_most_u) && !phenotype_force_disable;
}
#else
// Never called.
static bool SysPropSaysUffdGc() { return false; }
#endif

static bool ShouldUseUserfaultfd() {
  static_assert(kUseBakerReadBarrier || kUseTableLookupReadBarrier);
#ifdef __linux__
  // Use CMC/CC if that is being explicitly asked for on cmdline. Otherwise,
  // always use CC on host. On target, use CMC only if system properties says so
  // and the kernel supports it.
  gc::CollectorType gc_type = FetchCmdlineGcType();
  return gc_type == gc::CollectorType::kCollectorTypeCMC ||
         (gc_type == gc::CollectorType::kCollectorTypeNone &&
          kIsTargetAndroid &&
          SysPropSaysUffdGc() &&
          KernelSupportsUffd());
#else
  return false;
#endif
}

const bool gUseUserfaultfd = ShouldUseUserfaultfd();
const bool gUseReadBarrier = !gUseUserfaultfd;
#endif

namespace gc {
namespace collector {

// Turn off kCheckLocks when profiling the GC as it slows down the GC
// significantly.
static constexpr bool kCheckLocks = kDebugLocking;
static constexpr bool kVerifyRootsMarked = kIsDebugBuild;
// Two threads should suffice on devices.
static constexpr size_t kMaxNumUffdWorkers = 2;
// Number of compaction buffers reserved for mutator threads in SIGBUS feature
// case. It's extremely unlikely that we will ever have more than these number
// of mutator threads trying to access the moving-space during one compaction
// phase.
static constexpr size_t kMutatorCompactionBufferCount = 2048;
// Minimum from-space chunk to be madvised (during concurrent compaction) in one go.
static constexpr ssize_t kMinFromSpaceMadviseSize = 1 * MB;
// Concurrent compaction termination logic is different (and slightly more efficient) if the
// kernel has the fault-retry feature (allowing repeated faults on the same page), which was
// introduced in 5.7 (https://android-review.git.corp.google.com/c/kernel/common/+/1540088).
// This allows a single page fault to be handled, in turn, by each worker thread, only waking
// up the GC thread at the end.
static const bool gKernelHasFaultRetry = IsKernelVersionAtLeast(5, 7);

std::pair<bool, bool> MarkCompact::GetUffdAndMinorFault() {
  bool uffd_available;
  // In most cases the gUffdFeatures will already be initialized at boot time
  // when libart is loaded. On very old kernels we may get '0' from the kernel,
  // in which case we would be doing the syscalls each time this function is
  // called. But that's very unlikely case. There are no correctness issues as
  // the response from kernel never changes after boot.
  if (UNLIKELY(gUffdFeatures == 0)) {
    uffd_available = KernelSupportsUffd();
  } else {
    // We can have any uffd features only if uffd exists.
    uffd_available = true;
  }
  bool minor_fault_available =
      (gUffdFeatures & kUffdFeaturesForMinorFault) == kUffdFeaturesForMinorFault;
  return std::pair<bool, bool>(uffd_available, minor_fault_available);
}

bool MarkCompact::CreateUserfaultfd(bool post_fork) {
  if (post_fork || uffd_ == kFdUnused) {
    // Check if we have MREMAP_DONTUNMAP here for cases where
    // 'ART_USE_READ_BARRIER=false' is used. Additionally, this check ensures
    // that userfaultfd isn't used on old kernels, which cause random ioctl
    // failures.
    if (gHaveMremapDontunmap) {
      // Don't use O_NONBLOCK as we rely on read waiting on uffd_ if there isn't
      // any read event available. We don't use poll.
      uffd_ = syscall(__NR_userfaultfd, O_CLOEXEC | UFFD_USER_MODE_ONLY);
      // On non-android devices we may not have the kernel patches that restrict
      // userfaultfd to user mode. But that is not a security concern as we are
      // on host. Therefore, attempt one more time without UFFD_USER_MODE_ONLY.
      if (!kIsTargetAndroid && UNLIKELY(uffd_ == -1 && errno == EINVAL)) {
        uffd_ = syscall(__NR_userfaultfd, O_CLOEXEC);
      }
      if (UNLIKELY(uffd_ == -1)) {
        uffd_ = kFallbackMode;
        LOG(WARNING) << "Userfaultfd isn't supported (reason: " << strerror(errno)
                     << ") and therefore falling back to stop-the-world compaction.";
      } else {
        DCHECK(IsValidFd(uffd_));
        // Initialize uffd with the features which are required and available.
        // Using private anonymous mapping in threading mode is the default,
        // for which we don't need to ask for any features. Note: this mode
        // is not used in production.
        struct uffdio_api api = {.api = UFFD_API, .features = 0, .ioctls = 0};
        if (use_uffd_sigbus_) {
          // We should add SIGBUS feature only if we plan on using it as
          // requesting it here will mean threading mode will not work.
          CHECK_EQ(gUffdFeatures & kUffdFeaturesForSigbus, kUffdFeaturesForSigbus);
          api.features |= kUffdFeaturesForSigbus;
        }
        if (uffd_minor_fault_supported_) {
          // NOTE: This option is currently disabled.
          CHECK_EQ(gUffdFeatures & kUffdFeaturesForMinorFault, kUffdFeaturesForMinorFault);
          api.features |= kUffdFeaturesForMinorFault;
        }
        CHECK_EQ(ioctl(uffd_, UFFDIO_API, &api), 0)
            << "ioctl_userfaultfd: API: " << strerror(errno);
      }
    } else {
      uffd_ = kFallbackMode;
    }
  }
  uffd_initialized_ = !post_fork || uffd_ == kFallbackMode;
  return IsValidFd(uffd_);
}

template <size_t kAlignment>
MarkCompact::LiveWordsBitmap<kAlignment>* MarkCompact::LiveWordsBitmap<kAlignment>::Create(
    uintptr_t begin, uintptr_t end) {
  return static_cast<LiveWordsBitmap<kAlignment>*>(
          MemRangeBitmap::Create("Concurrent Mark Compact live words bitmap", begin, end));
}

static bool IsSigbusFeatureAvailable() {
  MarkCompact::GetUffdAndMinorFault();
  return (gUffdFeatures & kUffdFeaturesForSigbus) == kUffdFeaturesForSigbus;
}

size_t MarkCompact::InitializeInfoMap(uint8_t* p, size_t moving_space_sz) {
  size_t nr_moving_pages = DivideByPageSize(moving_space_sz);

  chunk_info_vec_ = reinterpret_cast<uint32_t*>(p);
  vector_length_ = moving_space_sz / kOffsetChunkSize;
  size_t total = vector_length_ * sizeof(uint32_t);

  first_objs_non_moving_space_ = reinterpret_cast<ObjReference*>(p + total);
  total += DivideByPageSize(heap_->GetNonMovingSpace()->Capacity()) * sizeof(ObjReference);

  first_objs_moving_space_ = reinterpret_cast<ObjReference*>(p + total);
  total += nr_moving_pages * sizeof(ObjReference);

  pre_compact_offset_moving_space_ = reinterpret_cast<uint32_t*>(p + total);
  total += nr_moving_pages * sizeof(uint32_t);
  return total;
}

MarkCompact::MarkCompact(Heap* heap)
    : GarbageCollector(heap, "concurrent mark compact"),
      gc_barrier_(0),
      lock_("mark compact lock", kGenericBottomLock),
      bump_pointer_space_(heap->GetBumpPointerSpace()),
      moving_space_bitmap_(bump_pointer_space_->GetMarkBitmap()),
      moving_space_begin_(bump_pointer_space_->Begin()),
      moving_space_end_(bump_pointer_space_->Limit()),
      moving_to_space_fd_(kFdUnused),
      moving_from_space_fd_(kFdUnused),
      uffd_(kFdUnused),
      sigbus_in_progress_count_(kSigbusCounterCompactionDoneMask),
      compaction_in_progress_count_(0),
      thread_pool_counter_(0),
      compacting_(false),
      uffd_initialized_(false),
      uffd_minor_fault_supported_(false),
      use_uffd_sigbus_(IsSigbusFeatureAvailable()),
      minor_fault_initialized_(false),
      map_linear_alloc_shared_(false),
      clamp_info_map_status_(ClampInfoStatus::kClampInfoNotDone) {
  if (kIsDebugBuild) {
    updated_roots_.reset(new std::unordered_set<void*>());
  }
  // TODO: When using minor-fault feature, the first GC after zygote-fork
  // requires mapping the linear-alloc again with MAP_SHARED. This leaves a
  // gap for suspended threads to access linear-alloc when it's empty (after
  // mremap) and not yet userfaultfd registered. This cannot be fixed by merely
  // doing uffd registration first. For now, just assert that we are not using
  // minor-fault. Eventually, a cleanup of linear-alloc update logic to only
  // use private anonymous would be ideal.
  CHECK(!uffd_minor_fault_supported_);

  // TODO: Depending on how the bump-pointer space move is implemented. If we
  // switch between two virtual memories each time, then we will have to
  // initialize live_words_bitmap_ accordingly.
  live_words_bitmap_.reset(LiveWordsBitmap<kAlignment>::Create(
          reinterpret_cast<uintptr_t>(bump_pointer_space_->Begin()),
          reinterpret_cast<uintptr_t>(bump_pointer_space_->Limit())));

  // Create one MemMap for all the data structures
  size_t moving_space_size = bump_pointer_space_->Capacity();
  size_t chunk_info_vec_size = moving_space_size / kOffsetChunkSize;
  size_t nr_moving_pages = DivideByPageSize(moving_space_size);
  size_t nr_non_moving_pages = DivideByPageSize(heap->GetNonMovingSpace()->Capacity());

  std::string err_msg;
  info_map_ = MemMap::MapAnonymous("Concurrent mark-compact chunk-info vector",
                                   chunk_info_vec_size * sizeof(uint32_t)
                                   + nr_non_moving_pages * sizeof(ObjReference)
                                   + nr_moving_pages * sizeof(ObjReference)
                                   + nr_moving_pages * sizeof(uint32_t),
                                   PROT_READ | PROT_WRITE,
                                   /*low_4gb=*/ false,
                                   &err_msg);
  if (UNLIKELY(!info_map_.IsValid())) {
    LOG(FATAL) << "Failed to allocate concurrent mark-compact chunk-info vector: " << err_msg;
  } else {
    size_t total = InitializeInfoMap(info_map_.Begin(), moving_space_size);
    DCHECK_EQ(total, info_map_.Size());
  }

  size_t moving_space_alignment = Heap::BestPageTableAlignment(moving_space_size);
  // The moving space is created at a fixed address, which is expected to be
  // PMD-size aligned.
  if (!IsAlignedParam(bump_pointer_space_->Begin(), moving_space_alignment)) {
    LOG(WARNING) << "Bump pointer space is not aligned to " << PrettySize(moving_space_alignment)
                 << ". This can lead to longer stop-the-world pauses for compaction";
  }
  // NOTE: PROT_NONE is used here as these mappings are for address space reservation
  // only and will be used only after appropriately remapping them.
  from_space_map_ = MemMap::MapAnonymousAligned("Concurrent mark-compact from-space",
                                                moving_space_size,
                                                PROT_NONE,
                                                /*low_4gb=*/kObjPtrPoisoning,
                                                moving_space_alignment,
                                                &err_msg);
  if (UNLIKELY(!from_space_map_.IsValid())) {
    LOG(FATAL) << "Failed to allocate concurrent mark-compact from-space" << err_msg;
  } else {
    from_space_begin_ = from_space_map_.Begin();
  }

  // In some cases (32-bit or kObjPtrPoisoning) it's too much to ask for 3
  // heap-sized mappings in low-4GB. So tolerate failure here by attempting to
  // mmap again right before the compaction pause. And if even that fails, then
  // running the GC cycle in copy-mode rather than minor-fault.
  //
  // This map doesn't have to be aligned to 2MB as we don't mremap on it.
  if (!kObjPtrPoisoning && uffd_minor_fault_supported_) {
    // We need this map only if minor-fault feature is supported. But in that case
    // don't create the mapping if obj-ptr poisoning is enabled as then the mapping
    // has to be created in low_4gb. Doing this here rather than later causes the
    // Dex2oatImageTest.TestExtension gtest to fail in 64-bit platforms.
    shadow_to_space_map_ = MemMap::MapAnonymous("Concurrent mark-compact moving-space shadow",
                                                moving_space_size,
                                                PROT_NONE,
                                                /*low_4gb=*/false,
                                                &err_msg);
    if (!shadow_to_space_map_.IsValid()) {
      LOG(WARNING) << "Failed to allocate concurrent mark-compact moving-space shadow: " << err_msg;
    }
  }
  const size_t num_pages =
      1 + (use_uffd_sigbus_ ? kMutatorCompactionBufferCount :
                              std::min(heap_->GetParallelGCThreadCount(), kMaxNumUffdWorkers));
  compaction_buffers_map_ = MemMap::MapAnonymous("Concurrent mark-compact compaction buffers",
                                                 gPageSize * num_pages,
                                                 PROT_READ | PROT_WRITE,
                                                 /*low_4gb=*/kObjPtrPoisoning,
                                                 &err_msg);
  if (UNLIKELY(!compaction_buffers_map_.IsValid())) {
    LOG(FATAL) << "Failed to allocate concurrent mark-compact compaction buffers" << err_msg;
  }
  // We also use the first page-sized buffer for the purpose of terminating concurrent compaction.
  conc_compaction_termination_page_ = compaction_buffers_map_.Begin();
  // Touch the page deliberately to avoid userfaults on it. We madvise it in
  // CompactionPhase() before using it to terminate concurrent compaction.
  ForceRead(conc_compaction_termination_page_);

  // In most of the cases, we don't expect more than one LinearAlloc space.
  linear_alloc_spaces_data_.reserve(1);

  // Initialize GC metrics.
  metrics::ArtMetrics* metrics = GetMetrics();
  // The mark-compact collector supports only full-heap collections at the moment.
  gc_time_histogram_ = metrics->FullGcCollectionTime();
  metrics_gc_count_ = metrics->FullGcCount();
  metrics_gc_count_delta_ = metrics->FullGcCountDelta();
  gc_throughput_histogram_ = metrics->FullGcThroughput();
  gc_tracing_throughput_hist_ = metrics->FullGcTracingThroughput();
  gc_throughput_avg_ = metrics->FullGcThroughputAvg();
  gc_tracing_throughput_avg_ = metrics->FullGcTracingThroughputAvg();
  gc_scanned_bytes_ = metrics->FullGcScannedBytes();
  gc_scanned_bytes_delta_ = metrics->FullGcScannedBytesDelta();
  gc_freed_bytes_ = metrics->FullGcFreedBytes();
  gc_freed_bytes_delta_ = metrics->FullGcFreedBytesDelta();
  gc_duration_ = metrics->FullGcDuration();
  gc_duration_delta_ = metrics->FullGcDurationDelta();
  are_metrics_initialized_ = true;
}

void MarkCompact::AddLinearAllocSpaceData(uint8_t* begin, size_t len) {
  DCHECK_ALIGNED_PARAM(begin, gPageSize);
  DCHECK_ALIGNED_PARAM(len, gPageSize);
  DCHECK_GE(len, Heap::GetPMDSize());
  size_t alignment = Heap::BestPageTableAlignment(len);
  bool is_shared = false;
  // We use MAP_SHARED on non-zygote processes for leveraging userfaultfd's minor-fault feature.
  if (map_linear_alloc_shared_) {
    void* ret = mmap(begin,
                     len,
                     PROT_READ | PROT_WRITE,
                     MAP_ANONYMOUS | MAP_SHARED | MAP_FIXED,
                     /*fd=*/-1,
                     /*offset=*/0);
    CHECK_EQ(ret, begin) << "mmap failed: " << strerror(errno);
    is_shared = true;
  }
  std::string err_msg;
  MemMap shadow(MemMap::MapAnonymousAligned("linear-alloc shadow map",
                                            len,
                                            PROT_NONE,
                                            /*low_4gb=*/false,
                                            alignment,
                                            &err_msg));
  if (!shadow.IsValid()) {
    LOG(FATAL) << "Failed to allocate linear-alloc shadow map: " << err_msg;
    UNREACHABLE();
  }

  MemMap page_status_map(MemMap::MapAnonymous("linear-alloc page-status map",
                                              DivideByPageSize(len),
                                              PROT_READ | PROT_WRITE,
                                              /*low_4gb=*/false,
                                              &err_msg));
  if (!page_status_map.IsValid()) {
    LOG(FATAL) << "Failed to allocate linear-alloc page-status shadow map: " << err_msg;
    UNREACHABLE();
  }
  linear_alloc_spaces_data_.emplace_back(std::forward<MemMap>(shadow),
                                         std::forward<MemMap>(page_status_map),
                                         begin,
                                         begin + len,
                                         is_shared);
}

void MarkCompact::ClampGrowthLimit(size_t new_capacity) {
  // From-space is the same size as moving-space in virtual memory.
  // However, if it's in >4GB address space then we don't need to do it
  // synchronously.
#if defined(__LP64__)
  constexpr bool kClampFromSpace = kObjPtrPoisoning;
#else
  constexpr bool kClampFromSpace = true;
#endif
  size_t old_capacity = bump_pointer_space_->Capacity();
  new_capacity = bump_pointer_space_->ClampGrowthLimit(new_capacity);
  if (new_capacity < old_capacity) {
    CHECK(from_space_map_.IsValid());
    if (kClampFromSpace) {
      from_space_map_.SetSize(new_capacity);
    }
    // NOTE: We usually don't use shadow_to_space_map_ and therefore the condition will
    // mostly be false.
    if (shadow_to_space_map_.IsValid() && shadow_to_space_map_.Size() > new_capacity) {
      shadow_to_space_map_.SetSize(new_capacity);
    }
    clamp_info_map_status_ = ClampInfoStatus::kClampInfoPending;
  }
  CHECK_EQ(moving_space_begin_, bump_pointer_space_->Begin());
}

void MarkCompact::MaybeClampGcStructures() {
  size_t moving_space_size = bump_pointer_space_->Capacity();
  DCHECK(thread_running_gc_ != nullptr);
  if (UNLIKELY(clamp_info_map_status_ == ClampInfoStatus::kClampInfoPending)) {
    CHECK(from_space_map_.IsValid());
    if (from_space_map_.Size() > moving_space_size) {
      from_space_map_.SetSize(moving_space_size);
    }
    // Bitmaps and other data structures
    live_words_bitmap_->SetBitmapSize(moving_space_size);
    size_t set_size = InitializeInfoMap(info_map_.Begin(), moving_space_size);
    CHECK_LT(set_size, info_map_.Size());
    info_map_.SetSize(set_size);

    clamp_info_map_status_ = ClampInfoStatus::kClampInfoFinished;
  }
}

void MarkCompact::PrepareCardTableForMarking(bool clear_alloc_space_cards) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  accounting::CardTable* const card_table = heap_->GetCardTable();
  // immune_spaces_ is emptied in InitializePhase() before marking starts. This
  // function is invoked twice during marking. We only need to populate immune_spaces_
  // once per GC cycle. And when it's done (below), all the immune spaces are
  // added to it. We can never have partially filled immune_spaces_.
  bool update_immune_spaces = immune_spaces_.IsEmpty();
  // Mark all of the spaces we never collect as immune.
  for (const auto& space : GetHeap()->GetContinuousSpaces()) {
    if (space->GetGcRetentionPolicy() == space::kGcRetentionPolicyNeverCollect ||
        space->GetGcRetentionPolicy() == space::kGcRetentionPolicyFullCollect) {
      CHECK(space->IsZygoteSpace() || space->IsImageSpace());
      if (update_immune_spaces) {
        immune_spaces_.AddSpace(space);
      }
      accounting::ModUnionTable* table = heap_->FindModUnionTableFromSpace(space);
      if (table != nullptr) {
        table->ProcessCards();
      } else {
        // Keep cards aged if we don't have a mod-union table since we need
        // to scan them in future GCs. This case is for app images.
        card_table->ModifyCardsAtomic(
            space->Begin(),
            space->End(),
            [](uint8_t card) {
              return (card == gc::accounting::CardTable::kCardClean)
                  ? card
                  : gc::accounting::CardTable::kCardAged;
            },
            /* card modified visitor */ VoidFunctor());
      }
    } else if (clear_alloc_space_cards) {
      CHECK(!space->IsZygoteSpace());
      CHECK(!space->IsImageSpace());
      // The card-table corresponding to bump-pointer and non-moving space can
      // be cleared, because we are going to traverse all the reachable objects
      // in these spaces. This card-table will eventually be used to track
      // mutations while concurrent marking is going on.
      card_table->ClearCardRange(space->Begin(), space->Limit());
      if (space != bump_pointer_space_) {
        CHECK_EQ(space, heap_->GetNonMovingSpace());
        non_moving_space_ = space;
        non_moving_space_bitmap_ = space->GetMarkBitmap();
      }
    } else {
      card_table->ModifyCardsAtomic(
          space->Begin(),
          space->End(),
          [](uint8_t card) {
            return (card == gc::accounting::CardTable::kCardDirty) ?
                       gc::accounting::CardTable::kCardAged :
                       gc::accounting::CardTable::kCardClean;
          },
          /* card modified visitor */ VoidFunctor());
    }
  }
}

void MarkCompact::MarkZygoteLargeObjects() {
  Thread* self = thread_running_gc_;
  DCHECK_EQ(self, Thread::Current());
  space::LargeObjectSpace* const los = heap_->GetLargeObjectsSpace();
  if (los != nullptr) {
    // Pick the current live bitmap (mark bitmap if swapped).
    accounting::LargeObjectBitmap* const live_bitmap = los->GetLiveBitmap();
    accounting::LargeObjectBitmap* const mark_bitmap = los->GetMarkBitmap();
    // Walk through all of the objects and explicitly mark the zygote ones so they don't get swept.
    std::pair<uint8_t*, uint8_t*> range = los->GetBeginEndAtomic();
    live_bitmap->VisitMarkedRange(reinterpret_cast<uintptr_t>(range.first),
                                  reinterpret_cast<uintptr_t>(range.second),
                                  [mark_bitmap, los, self](mirror::Object* obj)
                                      REQUIRES(Locks::heap_bitmap_lock_)
                                          REQUIRES_SHARED(Locks::mutator_lock_) {
                                            if (los->IsZygoteLargeObject(self, obj)) {
                                              mark_bitmap->Set(obj);
                                            }
                                          });
  }
}

void MarkCompact::InitializePhase() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  mark_stack_ = heap_->GetMarkStack();
  CHECK(mark_stack_->IsEmpty());
  immune_spaces_.Reset();
  moving_first_objs_count_ = 0;
  non_moving_first_objs_count_ = 0;
  black_page_count_ = 0;
  bytes_scanned_ = 0;
  freed_objects_ = 0;
  // The first buffer is used by gc-thread.
  compaction_buffer_counter_.store(1, std::memory_order_relaxed);
  from_space_slide_diff_ = from_space_begin_ - bump_pointer_space_->Begin();
  black_allocations_begin_ = bump_pointer_space_->Limit();
  CHECK_EQ(moving_space_begin_, bump_pointer_space_->Begin());
  moving_space_end_ = bump_pointer_space_->Limit();
  walk_super_class_cache_ = nullptr;
  // TODO: Would it suffice to read it once in the constructor, which is called
  // in zygote process?
  pointer_size_ = Runtime::Current()->GetClassLinker()->GetImagePointerSize();
}

class MarkCompact::ThreadFlipVisitor : public Closure {
 public:
  explicit ThreadFlipVisitor(MarkCompact* collector) : collector_(collector) {}

  void Run(Thread* thread) override REQUIRES_SHARED(Locks::mutator_lock_) {
    // Note: self is not necessarily equal to thread since thread may be suspended.
    Thread* self = Thread::Current();
    CHECK(thread == self || thread->GetState() != ThreadState::kRunnable)
        << thread->GetState() << " thread " << thread << " self " << self;
    thread->VisitRoots(collector_, kVisitRootFlagAllRoots);
    // Interpreter cache is thread-local so it needs to be swept either in a
    // flip, or a stop-the-world pause.
    CHECK(collector_->compacting_);
    thread->SweepInterpreterCache(collector_);
    thread->AdjustTlab(collector_->black_objs_slide_diff_);
  }

 private:
  MarkCompact* const collector_;
};

class MarkCompact::FlipCallback : public Closure {
 public:
  explicit FlipCallback(MarkCompact* collector) : collector_(collector) {}

  void Run([[maybe_unused]] Thread* thread) override REQUIRES(Locks::mutator_lock_) {
    collector_->CompactionPause();
  }

 private:
  MarkCompact* const collector_;
};

void MarkCompact::RunPhases() {
  Thread* self = Thread::Current();
  thread_running_gc_ = self;
  Runtime* runtime = Runtime::Current();
  InitializePhase();
  GetHeap()->PreGcVerification(this);
  {
    ReaderMutexLock mu(self, *Locks::mutator_lock_);
    MarkingPhase();
  }
  {
    // Marking pause
    ScopedPause pause(this);
    MarkingPause();
    if (kIsDebugBuild) {
      bump_pointer_space_->AssertAllThreadLocalBuffersAreRevoked();
    }
  }
  {
    ReaderMutexLock mu(self, *Locks::mutator_lock_);
    ReclaimPhase();
    PrepareForCompaction();
  }
  if (uffd_ != kFallbackMode && !use_uffd_sigbus_) {
    heap_->GetThreadPool()->WaitForWorkersToBeCreated();
  }

  {
    // Compaction pause
    ThreadFlipVisitor visitor(this);
    FlipCallback callback(this);
    runtime->GetThreadList()->FlipThreadRoots(
        &visitor, &callback, this, GetHeap()->GetGcPauseListener());
  }

  if (IsValidFd(uffd_)) {
    ReaderMutexLock mu(self, *Locks::mutator_lock_);
    CompactionPhase();
  }

  FinishPhase();
  thread_running_gc_ = nullptr;
}

void MarkCompact::InitMovingSpaceFirstObjects(const size_t vec_len) {
  // Find the first live word first.
  size_t to_space_page_idx = 0;
  uint32_t offset_in_chunk_word;
  uint32_t offset;
  mirror::Object* obj;
  const uintptr_t heap_begin = moving_space_bitmap_->HeapBegin();

  size_t chunk_idx;
  // Find the first live word in the space
  for (chunk_idx = 0; chunk_info_vec_[chunk_idx] == 0; chunk_idx++) {
    if (chunk_idx > vec_len) {
      // We don't have any live data on the moving-space.
      return;
    }
  }
  // Use live-words bitmap to find the first word
  offset_in_chunk_word = live_words_bitmap_->FindNthLiveWordOffset(chunk_idx, /*n*/ 0);
  offset = chunk_idx * kBitsPerVectorWord + offset_in_chunk_word;
  DCHECK(live_words_bitmap_->Test(offset)) << "offset=" << offset
                                           << " chunk_idx=" << chunk_idx
                                           << " N=0"
                                           << " offset_in_word=" << offset_in_chunk_word
                                           << " word=" << std::hex
                                           << live_words_bitmap_->GetWord(chunk_idx);
  // The first object doesn't require using FindPrecedingObject().
  obj = reinterpret_cast<mirror::Object*>(heap_begin + offset * kAlignment);
  // TODO: add a check to validate the object.

  pre_compact_offset_moving_space_[to_space_page_idx] = offset;
  first_objs_moving_space_[to_space_page_idx].Assign(obj);
  to_space_page_idx++;

  uint32_t page_live_bytes = 0;
  while (true) {
    for (; page_live_bytes <= gPageSize; chunk_idx++) {
      if (chunk_idx > vec_len) {
        moving_first_objs_count_ = to_space_page_idx;
        return;
      }
      page_live_bytes += chunk_info_vec_[chunk_idx];
    }
    chunk_idx--;
    page_live_bytes -= gPageSize;
    DCHECK_LE(page_live_bytes, kOffsetChunkSize);
    DCHECK_LE(page_live_bytes, chunk_info_vec_[chunk_idx])
        << " chunk_idx=" << chunk_idx
        << " to_space_page_idx=" << to_space_page_idx
        << " vec_len=" << vec_len;
    DCHECK(IsAligned<kAlignment>(chunk_info_vec_[chunk_idx] - page_live_bytes));
    offset_in_chunk_word =
            live_words_bitmap_->FindNthLiveWordOffset(
                chunk_idx, (chunk_info_vec_[chunk_idx] - page_live_bytes) / kAlignment);
    offset = chunk_idx * kBitsPerVectorWord + offset_in_chunk_word;
    DCHECK(live_words_bitmap_->Test(offset))
        << "offset=" << offset
        << " chunk_idx=" << chunk_idx
        << " N=" << ((chunk_info_vec_[chunk_idx] - page_live_bytes) / kAlignment)
        << " offset_in_word=" << offset_in_chunk_word
        << " word=" << std::hex << live_words_bitmap_->GetWord(chunk_idx);
    // TODO: Can we optimize this for large objects? If we are continuing a
    // large object that spans multiple pages, then we may be able to do without
    // calling FindPrecedingObject().
    //
    // Find the object which encapsulates offset in it, which could be
    // starting at offset itself.
    obj = moving_space_bitmap_->FindPrecedingObject(heap_begin + offset * kAlignment);
    // TODO: add a check to validate the object.
    pre_compact_offset_moving_space_[to_space_page_idx] = offset;
    first_objs_moving_space_[to_space_page_idx].Assign(obj);
    to_space_page_idx++;
    chunk_idx++;
  }
}

void MarkCompact::InitNonMovingSpaceFirstObjects() {
  accounting::ContinuousSpaceBitmap* bitmap = non_moving_space_->GetLiveBitmap();
  uintptr_t begin = reinterpret_cast<uintptr_t>(non_moving_space_->Begin());
  const uintptr_t end = reinterpret_cast<uintptr_t>(non_moving_space_->End());
  mirror::Object* prev_obj;
  size_t page_idx;
  {
    // Find first live object
    mirror::Object* obj = nullptr;
    bitmap->VisitMarkedRange</*kVisitOnce*/ true>(begin,
                                                  end,
                                                  [&obj] (mirror::Object* o) {
                                                    obj = o;
                                                  });
    if (obj == nullptr) {
      // There are no live objects in the non-moving space
      return;
    }
    page_idx = DivideByPageSize(reinterpret_cast<uintptr_t>(obj) - begin);
    first_objs_non_moving_space_[page_idx++].Assign(obj);
    prev_obj = obj;
  }
  // TODO: check obj is valid
  uintptr_t prev_obj_end = reinterpret_cast<uintptr_t>(prev_obj)
                           + RoundUp(prev_obj->SizeOf<kDefaultVerifyFlags>(), kAlignment);
  // For every page find the object starting from which we need to call
  // VisitReferences. It could either be an object that started on some
  // preceding page, or some object starting within this page.
  begin = RoundDown(reinterpret_cast<uintptr_t>(prev_obj) + gPageSize, gPageSize);
  while (begin < end) {
    // Utilize, if any, large object that started in some preceding page, but
    // overlaps with this page as well.
    if (prev_obj != nullptr && prev_obj_end > begin) {
      DCHECK_LT(prev_obj, reinterpret_cast<mirror::Object*>(begin));
      first_objs_non_moving_space_[page_idx].Assign(prev_obj);
      mirror::Class* klass = prev_obj->GetClass<kVerifyNone, kWithoutReadBarrier>();
      if (HasAddress(klass)) {
        LOG(WARNING) << "found inter-page object " << prev_obj
                     << " in non-moving space with klass " << klass
                     << " in moving space";
      }
    } else {
      prev_obj_end = 0;
      // It's sufficient to only search for previous object in the preceding page.
      // If no live object started in that page and some object had started in
      // the page preceding to that page, which was big enough to overlap with
      // the current page, then we wouldn't be in the else part.
      prev_obj = bitmap->FindPrecedingObject(begin, begin - gPageSize);
      if (prev_obj != nullptr) {
        prev_obj_end = reinterpret_cast<uintptr_t>(prev_obj)
                        + RoundUp(prev_obj->SizeOf<kDefaultVerifyFlags>(), kAlignment);
      }
      if (prev_obj_end > begin) {
        mirror::Class* klass = prev_obj->GetClass<kVerifyNone, kWithoutReadBarrier>();
        if (HasAddress(klass)) {
          LOG(WARNING) << "found inter-page object " << prev_obj
                       << " in non-moving space with klass " << klass
                       << " in moving space";
        }
        first_objs_non_moving_space_[page_idx].Assign(prev_obj);
      } else {
        // Find the first live object in this page
        bitmap->VisitMarkedRange</*kVisitOnce*/ true>(
                begin,
                begin + gPageSize,
                [this, page_idx] (mirror::Object* obj) {
                  first_objs_non_moving_space_[page_idx].Assign(obj);
                });
      }
      // An empty entry indicates that the page has no live objects and hence
      // can be skipped.
    }
    begin += gPageSize;
    page_idx++;
  }
  non_moving_first_objs_count_ = page_idx;
}

bool MarkCompact::CanCompactMovingSpaceWithMinorFault() {
  size_t min_size = (moving_first_objs_count_ + black_page_count_) * gPageSize;
  return minor_fault_initialized_ && shadow_to_space_map_.IsValid() &&
         shadow_to_space_map_.Size() >= min_size;
}

class MarkCompact::ConcurrentCompactionGcTask : public SelfDeletingTask {
 public:
  explicit ConcurrentCompactionGcTask(MarkCompact* collector, size_t idx)
      : collector_(collector), index_(idx) {}

  void Run([[maybe_unused]] Thread* self) override REQUIRES_SHARED(Locks::mutator_lock_) {
    if (collector_->CanCompactMovingSpaceWithMinorFault()) {
      collector_->ConcurrentCompaction<MarkCompact::kMinorFaultMode>(/*buf=*/nullptr);
    } else {
      // The passed page/buf to ConcurrentCompaction is used by the thread as a
      // gPageSize buffer for compacting and updating objects into and then
      // passing the buf to uffd ioctls.
      uint8_t* buf = collector_->compaction_buffers_map_.Begin() + index_ * gPageSize;
      collector_->ConcurrentCompaction<MarkCompact::kCopyMode>(buf);
    }
  }

 private:
  MarkCompact* const collector_;
  size_t index_;
};

void MarkCompact::PrepareForCompaction() {
  uint8_t* space_begin = bump_pointer_space_->Begin();
  size_t vector_len = (black_allocations_begin_ - space_begin) / kOffsetChunkSize;
  DCHECK_LE(vector_len, vector_length_);
  for (size_t i = 0; i < vector_len; i++) {
    DCHECK_LE(chunk_info_vec_[i], kOffsetChunkSize);
    DCHECK_EQ(chunk_info_vec_[i], live_words_bitmap_->LiveBytesInBitmapWord(i));
  }
  InitMovingSpaceFirstObjects(vector_len);
  InitNonMovingSpaceFirstObjects();

  // TODO: We can do a lot of neat tricks with this offset vector to tune the
  // compaction as we wish. Originally, the compaction algorithm slides all
  // live objects towards the beginning of the heap. This is nice because it
  // keeps the spatial locality of objects intact.
  // However, sometimes it's desired to compact objects in certain portions
  // of the heap. For instance, it is expected that, over time,
  // objects towards the beginning of the heap are long lived and are always
  // densely packed. In this case, it makes sense to only update references in
  // there and not try to compact it.
  // Furthermore, we might have some large objects and may not want to move such
  // objects.
  // We can adjust, without too much effort, the values in the chunk_info_vec_ such
  // that the objects in the dense beginning area aren't moved. OTOH, large
  // objects, which could be anywhere in the heap, could also be kept from
  // moving by using a similar trick. The only issue is that by doing this we will
  // leave an unused hole in the middle of the heap which can't be used for
  // allocations until we do a *full* compaction.
  //
  // At this point every element in the chunk_info_vec_ contains the live-bytes
  // of the corresponding chunk. For old-to-new address computation we need
  // every element to reflect total live-bytes till the corresponding chunk.

  // Live-bytes count is required to compute post_compact_end_ below.
  uint32_t total;
  // Update the vector one past the heap usage as it is required for black
  // allocated objects' post-compact address computation.
  if (vector_len < vector_length_) {
    vector_len++;
    total = 0;
  } else {
    // Fetch the value stored in the last element before it gets overwritten by
    // std::exclusive_scan().
    total = chunk_info_vec_[vector_len - 1];
  }
  std::exclusive_scan(chunk_info_vec_, chunk_info_vec_ + vector_len, chunk_info_vec_, 0);
  total += chunk_info_vec_[vector_len - 1];

  for (size_t i = vector_len; i < vector_length_; i++) {
    DCHECK_EQ(chunk_info_vec_[i], 0u);
  }
  post_compact_end_ = AlignUp(space_begin + total, gPageSize);
  CHECK_EQ(post_compact_end_, space_begin + moving_first_objs_count_ * gPageSize);
  black_objs_slide_diff_ = black_allocations_begin_ - post_compact_end_;
  // We shouldn't be consuming more space after compaction than pre-compaction.
  CHECK_GE(black_objs_slide_diff_, 0);
  // How do we handle compaction of heap portion used for allocations after the
  // marking-pause?
  // All allocations after the marking-pause are considered black (reachable)
  // for this GC cycle. However, they need not be allocated contiguously as
  // different mutators use TLABs. So we will compact the heap till the point
  // where allocations took place before the marking-pause. And everything after
  // that will be slid with TLAB holes, and then TLAB info in TLS will be
  // appropriately updated in the pre-compaction pause.
  // The chunk-info vector entries for the post marking-pause allocations will be
  // also updated in the pre-compaction pause.

  bool is_zygote = Runtime::Current()->IsZygote();
  if (!uffd_initialized_ && CreateUserfaultfd(/*post_fork*/false)) {
    if (!use_uffd_sigbus_) {
      // Register the buffer that we use for terminating concurrent compaction
      struct uffdio_register uffd_register;
      uffd_register.range.start = reinterpret_cast<uintptr_t>(conc_compaction_termination_page_);
      uffd_register.range.len = gPageSize;
      uffd_register.mode = UFFDIO_REGISTER_MODE_MISSING;
      CHECK_EQ(ioctl(uffd_, UFFDIO_REGISTER, &uffd_register), 0)
          << "ioctl_userfaultfd: register compaction termination page: " << strerror(errno);
    }
    if (!uffd_minor_fault_supported_ && shadow_to_space_map_.IsValid()) {
      // A valid shadow-map for moving space is only possible if we
      // were able to map it in the constructor. That also means that its size
      // matches the moving-space.
      CHECK_EQ(shadow_to_space_map_.Size(), bump_pointer_space_->Capacity());
      // Release the shadow map for moving-space if we don't support minor-fault
      // as it's not required.
      shadow_to_space_map_.Reset();
    }
  }
  // For zygote we create the thread pool each time before starting compaction,
  // and get rid of it when finished. This is expected to happen rarely as
  // zygote spends most of the time in native fork loop.
  if (uffd_ != kFallbackMode) {
    if (!use_uffd_sigbus_) {
      ThreadPool* pool = heap_->GetThreadPool();
      if (UNLIKELY(pool == nullptr)) {
        // On devices with 2 cores, GetParallelGCThreadCount() will return 1,
        // which is desired number of workers on such devices.
        heap_->CreateThreadPool(std::min(heap_->GetParallelGCThreadCount(), kMaxNumUffdWorkers));
        pool = heap_->GetThreadPool();
      }
      size_t num_threads = pool->GetThreadCount();
      thread_pool_counter_ = num_threads;
      for (size_t i = 0; i < num_threads; i++) {
        pool->AddTask(thread_running_gc_, new ConcurrentCompactionGcTask(this, i + 1));
      }
      CHECK_EQ(pool->GetTaskCount(thread_running_gc_), num_threads);
    }
    /*
     * Possible scenarios for mappings:
     * A) All zygote GCs (or if minor-fault feature isn't available): uses
     * uffd's copy mode
     *  1) For moving-space ('to' space is same as the moving-space):
     *    a) Private-anonymous mappings for 'to' and 'from' space are created in
     *    the constructor.
     *    b) In the compaction pause, we mremap(dontunmap) from 'to' space to
     *    'from' space. This results in moving all pages to 'from' space and
     *    emptying the 'to' space, thereby preparing it for userfaultfd
     *    registration.
     *
     *  2) For linear-alloc space:
     *    a) Private-anonymous mappings for the linear-alloc and its 'shadow'
     *    are created by the arena-pool.
     *    b) In the compaction pause, we mremap(dontumap) with similar effect as
     *    (A.1.b) above.
     *
     * B) First GC after zygote: uses uffd's copy-mode
     *  1) For moving-space:
     *    a) If the mmap for shadow-map has been successful in the constructor,
     *    then we remap it (mmap with MAP_FIXED) to get a shared-anonymous
     *    mapping.
     *    b) Else, we create two memfd and ftruncate them to the moving-space
     *    size.
     *    c) Same as (A.1.b)
     *    d) If (B.1.a), then mremap(dontunmap) from shadow-map to
     *    'to' space. This will make both of them map to the same pages
     *    e) If (B.1.b), then mmap with the first memfd in shared mode on the
     *    'to' space.
     *    f) At the end of compaction, we will have moved the moving-space
     *    objects to a MAP_SHARED mapping, readying it for minor-fault from next
     *    GC cycle.
     *
     *  2) For linear-alloc space:
     *    a) Same as (A.2.b)
     *    b) mmap a shared-anonymous mapping onto the linear-alloc space.
     *    c) Same as (B.1.f)
     *
     * C) All subsequent GCs: preferable minor-fault mode. But may also require
     * using copy-mode.
     *  1) For moving-space:
     *    a) If the shadow-map is created and no memfd was used, then that means
     *    we are using shared-anonymous. Therefore, mmap a shared-anonymous on
     *    the shadow-space.
     *    b) If the shadow-map is not mapped yet, then mmap one with a size
     *    big enough to hold the compacted moving space. This may fail, in which
     *    case we will use uffd's copy-mode.
     *    c) If (b) is successful, then mmap the free memfd onto shadow-map.
     *    d) Same as (A.1.b)
     *    e) In compaction pause, if the shadow-map was not created, then use
     *    copy-mode.
     *    f) Else, if the created map is smaller than the required-size, then
     *    use mremap (without dontunmap) to expand the size. If failed, then use
     *    copy-mode.
     *    g) Otherwise, same as (B.1.d) and use minor-fault mode.
     *
     *  2) For linear-alloc space:
     *    a) Same as (A.2.b)
     *    b) Use minor-fault mode
     */
    auto mmap_shadow_map = [this](int flags, int fd) {
      void* ret = mmap(shadow_to_space_map_.Begin(),
                       shadow_to_space_map_.Size(),
                       PROT_READ | PROT_WRITE,
                       flags,
                       fd,
                       /*offset=*/0);
      DCHECK_NE(ret, MAP_FAILED) << "mmap for moving-space shadow failed:" << strerror(errno);
    };
    // Setup all the virtual memory ranges required for concurrent compaction.
    if (minor_fault_initialized_) {
      DCHECK(!is_zygote);
      if (UNLIKELY(!shadow_to_space_map_.IsValid())) {
        // This case happens only once on the first GC in minor-fault mode, if
        // we were unable to reserve shadow-map for moving-space in the
        // beginning.
        DCHECK_GE(moving_to_space_fd_, 0);
        // Take extra 4MB to reduce the likelihood of requiring resizing this
        // map in the pause due to black allocations.
        size_t reqd_size = std::min(moving_first_objs_count_ * gPageSize + 4 * MB,
                                    bump_pointer_space_->Capacity());
        // We cannot support memory-tool with shadow-map (as it requires
        // appending a redzone) in this case because the mapping may have to be expanded
        // using mremap (in KernelPreparation()), which would ignore the redzone.
        // MemMap::MapFile() appends a redzone, but MemMap::MapAnonymous() doesn't.
        std::string err_msg;
        shadow_to_space_map_ = MemMap::MapAnonymous("moving-space-shadow",
                                                    reqd_size,
                                                    PROT_NONE,
                                                    /*low_4gb=*/kObjPtrPoisoning,
                                                    &err_msg);

        if (shadow_to_space_map_.IsValid()) {
          CHECK(!kMemoryToolAddsRedzones || shadow_to_space_map_.GetRedzoneSize() == 0u);
          // We want to use MemMap to get low-4GB mapping, if required, but then also
          // want to have its ownership as we may grow it (in
          // KernelPreparation()). If the ownership is not taken and we try to
          // resize MemMap, then it unmaps the virtual range.
          MemMap temp = shadow_to_space_map_.TakeReservedMemory(shadow_to_space_map_.Size(),
                                                                /*reuse*/ true);
          std::swap(temp, shadow_to_space_map_);
          DCHECK(!temp.IsValid());
        } else {
          LOG(WARNING) << "Failed to create moving space's shadow map of " << PrettySize(reqd_size)
                       << " size. " << err_msg;
        }
      }

      if (LIKELY(shadow_to_space_map_.IsValid())) {
        int fd = moving_to_space_fd_;
        int mmap_flags = MAP_SHARED | MAP_FIXED;
        if (fd == kFdUnused) {
          // Unused moving-to-space fd means we are using anonymous shared
          // mapping.
          DCHECK_EQ(shadow_to_space_map_.Size(), bump_pointer_space_->Capacity());
          mmap_flags |= MAP_ANONYMOUS;
          fd = -1;
        }
        // If the map is smaller than required, then we'll do mremap in the
        // compaction pause to increase the size.
        mmap_shadow_map(mmap_flags, fd);
      }

      for (auto& data : linear_alloc_spaces_data_) {
        DCHECK_EQ(mprotect(data.shadow_.Begin(), data.shadow_.Size(), PROT_READ | PROT_WRITE), 0)
            << "mprotect failed: " << strerror(errno);
      }
    } else if (!is_zygote && uffd_minor_fault_supported_) {
      // First GC after zygote-fork. We will still use uffd's copy mode but will
      // use it to move objects to MAP_SHARED (to prepare for subsequent GCs, which
      // will use uffd's minor-fault feature).
      if (shadow_to_space_map_.IsValid() &&
          shadow_to_space_map_.Size() == bump_pointer_space_->Capacity()) {
        mmap_shadow_map(MAP_SHARED | MAP_FIXED | MAP_ANONYMOUS, /*fd=*/-1);
      } else {
        size_t size = bump_pointer_space_->Capacity();
        DCHECK_EQ(moving_to_space_fd_, kFdUnused);
        DCHECK_EQ(moving_from_space_fd_, kFdUnused);
        const char* name = bump_pointer_space_->GetName();
        moving_to_space_fd_ = memfd_create(name, MFD_CLOEXEC);
        CHECK_NE(moving_to_space_fd_, -1)
            << "memfd_create: failed for " << name << ": " << strerror(errno);
        moving_from_space_fd_ = memfd_create(name, MFD_CLOEXEC);
        CHECK_NE(moving_from_space_fd_, -1)
            << "memfd_create: failed for " << name << ": " << strerror(errno);

        // memfds are considered as files from resource limits point of view.
        // And the moving space could be several hundred MBs. So increase the
        // limit, if it's lower than moving-space size.
        bool rlimit_changed = false;
        rlimit rlim_read;
        CHECK_EQ(getrlimit(RLIMIT_FSIZE, &rlim_read), 0) << "getrlimit failed: " << strerror(errno);
        if (rlim_read.rlim_cur < size) {
          rlimit_changed = true;
          rlimit rlim = rlim_read;
          rlim.rlim_cur = size;
          CHECK_EQ(setrlimit(RLIMIT_FSIZE, &rlim), 0) << "setrlimit failed: " << strerror(errno);
        }

        // moving-space will map this fd so that we compact objects into it.
        int ret = ftruncate(moving_to_space_fd_, size);
        CHECK_EQ(ret, 0) << "ftruncate failed for moving-space:" << strerror(errno);
        ret = ftruncate(moving_from_space_fd_, size);
        CHECK_EQ(ret, 0) << "ftruncate failed for moving-space:" << strerror(errno);

        if (rlimit_changed) {
          // reset the rlimit to the original limits.
          CHECK_EQ(setrlimit(RLIMIT_FSIZE, &rlim_read), 0)
              << "setrlimit failed: " << strerror(errno);
        }
      }
    }
  }
}

class MarkCompact::VerifyRootMarkedVisitor : public SingleRootVisitor {
 public:
  explicit VerifyRootMarkedVisitor(MarkCompact* collector) : collector_(collector) { }

  void VisitRoot(mirror::Object* root, const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_, Locks::heap_bitmap_lock_) {
    CHECK(collector_->IsMarked(root) != nullptr) << info.ToString();
  }

 private:
  MarkCompact* const collector_;
};

void MarkCompact::ReMarkRoots(Runtime* runtime) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  DCHECK_EQ(thread_running_gc_, Thread::Current());
  Locks::mutator_lock_->AssertExclusiveHeld(thread_running_gc_);
  MarkNonThreadRoots(runtime);
  MarkConcurrentRoots(static_cast<VisitRootFlags>(kVisitRootFlagNewRoots
                                                  | kVisitRootFlagStopLoggingNewRoots
                                                  | kVisitRootFlagClearRootLog),
                      runtime);

  if (kVerifyRootsMarked) {
    TimingLogger::ScopedTiming t2("(Paused)VerifyRoots", GetTimings());
    VerifyRootMarkedVisitor visitor(this);
    runtime->VisitRoots(&visitor);
  }
}

void MarkCompact::MarkingPause() {
  TimingLogger::ScopedTiming t("(Paused)MarkingPause", GetTimings());
  Runtime* runtime = Runtime::Current();
  Locks::mutator_lock_->AssertExclusiveHeld(thread_running_gc_);
  {
    // Handle the dirty objects as we are a concurrent GC
    WriterMutexLock mu(thread_running_gc_, *Locks::heap_bitmap_lock_);
    {
      MutexLock mu2(thread_running_gc_, *Locks::runtime_shutdown_lock_);
      MutexLock mu3(thread_running_gc_, *Locks::thread_list_lock_);
      std::list<Thread*> thread_list = runtime->GetThreadList()->GetList();
      for (Thread* thread : thread_list) {
        thread->VisitRoots(this, static_cast<VisitRootFlags>(0));
        DCHECK_EQ(thread->GetThreadLocalGcBuffer(), nullptr);
        // Need to revoke all the thread-local allocation stacks since we will
        // swap the allocation stacks (below) and don't want anybody to allocate
        // into the live stack.
        thread->RevokeThreadLocalAllocationStack();
        bump_pointer_space_->RevokeThreadLocalBuffers(thread);
      }
    }
    // Fetch only the accumulated objects-allocated count as it is guaranteed to
    // be up-to-date after the TLAB revocation above.
    freed_objects_ += bump_pointer_space_->GetAccumulatedObjectsAllocated();
    // Capture 'end' of moving-space at this point. Every allocation beyond this
    // point will be considered as black.
    // Align-up to page boundary so that black allocations happen from next page
    // onwards. Also, it ensures that 'end' is aligned for card-table's
    // ClearCardRange().
    black_allocations_begin_ = bump_pointer_space_->AlignEnd(thread_running_gc_, gPageSize, heap_);
    DCHECK_ALIGNED_PARAM(black_allocations_begin_, gPageSize);

    // Re-mark root set. Doesn't include thread-roots as they are already marked
    // above.
    ReMarkRoots(runtime);
    // Scan dirty objects.
    RecursiveMarkDirtyObjects(/*paused*/ true, accounting::CardTable::kCardDirty);
    {
      TimingLogger::ScopedTiming t2("SwapStacks", GetTimings());
      heap_->SwapStacks();
      live_stack_freeze_size_ = heap_->GetLiveStack()->Size();
    }
  }
  // TODO: For PreSweepingGcVerification(), find correct strategy to visit/walk
  // objects in bump-pointer space when we have a mark-bitmap to indicate live
  // objects. At the same time we also need to be able to visit black allocations,
  // even though they are not marked in the bitmap. Without both of these we fail
  // pre-sweeping verification. As well as we leave windows open wherein a
  // VisitObjects/Walk on the space would either miss some objects or visit
  // unreachable ones. These windows are when we are switching from shared
  // mutator-lock to exclusive and vice-versa starting from here till compaction pause.
  // heap_->PreSweepingGcVerification(this);

  // Disallow new system weaks to prevent a race which occurs when someone adds
  // a new system weak before we sweep them. Since this new system weak may not
  // be marked, the GC may incorrectly sweep it. This also fixes a race where
  // interning may attempt to return a strong reference to a string that is
  // about to be swept.
  runtime->DisallowNewSystemWeaks();
  // Enable the reference processing slow path, needs to be done with mutators
  // paused since there is no lock in the GetReferent fast path.
  heap_->GetReferenceProcessor()->EnableSlowPath();
}

void MarkCompact::SweepSystemWeaks(Thread* self, Runtime* runtime, const bool paused) {
  TimingLogger::ScopedTiming t(paused ? "(Paused)SweepSystemWeaks" : "SweepSystemWeaks",
                               GetTimings());
  ReaderMutexLock mu(self, *Locks::heap_bitmap_lock_);
  runtime->SweepSystemWeaks(this);
}

void MarkCompact::ProcessReferences(Thread* self) {
  WriterMutexLock mu(self, *Locks::heap_bitmap_lock_);
  GetHeap()->GetReferenceProcessor()->ProcessReferences(self, GetTimings());
}

void MarkCompact::Sweep(bool swap_bitmaps) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  // Ensure that nobody inserted objects in the live stack after we swapped the
  // stacks.
  CHECK_GE(live_stack_freeze_size_, GetHeap()->GetLiveStack()->Size());
  {
    TimingLogger::ScopedTiming t2("MarkAllocStackAsLive", GetTimings());
    // Mark everything allocated since the last GC as live so that we can sweep
    // concurrently, knowing that new allocations won't be marked as live.
    accounting::ObjectStack* live_stack = heap_->GetLiveStack();
    heap_->MarkAllocStackAsLive(live_stack);
    live_stack->Reset();
    DCHECK(mark_stack_->IsEmpty());
  }
  for (const auto& space : GetHeap()->GetContinuousSpaces()) {
    if (space->IsContinuousMemMapAllocSpace() && space != bump_pointer_space_ &&
        !immune_spaces_.ContainsSpace(space)) {
      space::ContinuousMemMapAllocSpace* alloc_space = space->AsContinuousMemMapAllocSpace();
      DCHECK(!alloc_space->IsZygoteSpace());
      TimingLogger::ScopedTiming split("SweepMallocSpace", GetTimings());
      RecordFree(alloc_space->Sweep(swap_bitmaps));
    }
  }
  SweepLargeObjects(swap_bitmaps);
}

void MarkCompact::SweepLargeObjects(bool swap_bitmaps) {
  space::LargeObjectSpace* los = heap_->GetLargeObjectsSpace();
  if (los != nullptr) {
    TimingLogger::ScopedTiming split(__FUNCTION__, GetTimings());
    RecordFreeLOS(los->Sweep(swap_bitmaps));
  }
}

void MarkCompact::ReclaimPhase() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  DCHECK(thread_running_gc_ == Thread::Current());
  Runtime* const runtime = Runtime::Current();
  // Process the references concurrently.
  ProcessReferences(thread_running_gc_);
  // TODO: Try to merge this system-weak sweeping with the one while updating
  // references during the compaction pause.
  SweepSystemWeaks(thread_running_gc_, runtime, /*paused*/ false);
  runtime->AllowNewSystemWeaks();
  // Clean up class loaders after system weaks are swept since that is how we know if class
  // unloading occurred.
  runtime->GetClassLinker()->CleanupClassLoaders();
  {
    WriterMutexLock mu(thread_running_gc_, *Locks::heap_bitmap_lock_);
    // Reclaim unmarked objects.
    Sweep(false);
    // Swap the live and mark bitmaps for each space which we modified space. This is an
    // optimization that enables us to not clear live bits inside of the sweep. Only swaps unbound
    // bitmaps.
    SwapBitmaps();
    // Unbind the live and mark bitmaps.
    GetHeap()->UnBindBitmaps();
  }
}

// We want to avoid checking for every reference if it's within the page or
// not. This can be done if we know where in the page the holder object lies.
// If it doesn't overlap either boundaries then we can skip the checks.
template <bool kCheckBegin, bool kCheckEnd>
class MarkCompact::RefsUpdateVisitor {
 public:
  explicit RefsUpdateVisitor(MarkCompact* collector,
                             mirror::Object* obj,
                             uint8_t* begin,
                             uint8_t* end)
      : collector_(collector),
        moving_space_begin_(collector->moving_space_begin_),
        moving_space_end_(collector->moving_space_end_),
        obj_(obj),
        begin_(begin),
        end_(end) {
    DCHECK(!kCheckBegin || begin != nullptr);
    DCHECK(!kCheckEnd || end != nullptr);
  }

  void operator()([[maybe_unused]] mirror::Object* old,
                  MemberOffset offset,
                  [[maybe_unused]] bool is_static) const ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES_SHARED(Locks::heap_bitmap_lock_) {
    bool update = true;
    if (kCheckBegin || kCheckEnd) {
      uint8_t* ref = reinterpret_cast<uint8_t*>(obj_) + offset.Int32Value();
      update = (!kCheckBegin || ref >= begin_) && (!kCheckEnd || ref < end_);
    }
    if (update) {
      collector_->UpdateRef(obj_, offset, moving_space_begin_, moving_space_end_);
    }
  }

  // For object arrays we don't need to check boundaries here as it's done in
  // VisitReferenes().
  // TODO: Optimize reference updating using SIMD instructions. Object arrays
  // are perfect as all references are tightly packed.
  void operator()([[maybe_unused]] mirror::Object* old,
                  MemberOffset offset,
                  [[maybe_unused]] bool is_static,
                  [[maybe_unused]] bool is_obj_array) const ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES_SHARED(Locks::heap_bitmap_lock_) {
    collector_->UpdateRef(obj_, offset, moving_space_begin_, moving_space_end_);
  }

  void VisitRootIfNonNull(mirror::CompressedReference<mirror::Object>* root) const
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    collector_->UpdateRoot(root, moving_space_begin_, moving_space_end_);
  }

 private:
  MarkCompact* const collector_;
  uint8_t* const moving_space_begin_;
  uint8_t* const moving_space_end_;
  mirror::Object* const obj_;
  uint8_t* const begin_;
  uint8_t* const end_;
};

bool MarkCompact::IsValidObject(mirror::Object* obj) const {
  mirror::Class* klass = obj->GetClass<kVerifyNone, kWithoutReadBarrier>();
  if (!heap_->GetVerification()->IsValidHeapObjectAddress(klass)) {
    return false;
  }
  return heap_->GetVerification()->IsValidClassUnchecked<kWithFromSpaceBarrier>(
          obj->GetClass<kVerifyNone, kWithFromSpaceBarrier>());
}

template <typename Callback>
void MarkCompact::VerifyObject(mirror::Object* ref, Callback& callback) const {
  if (kIsDebugBuild) {
    mirror::Class* klass = ref->GetClass<kVerifyNone, kWithFromSpaceBarrier>();
    mirror::Class* pre_compact_klass = ref->GetClass<kVerifyNone, kWithoutReadBarrier>();
    mirror::Class* klass_klass = klass->GetClass<kVerifyNone, kWithFromSpaceBarrier>();
    mirror::Class* klass_klass_klass = klass_klass->GetClass<kVerifyNone, kWithFromSpaceBarrier>();
    if (HasAddress(pre_compact_klass) &&
        reinterpret_cast<uint8_t*>(pre_compact_klass) < black_allocations_begin_) {
      CHECK(moving_space_bitmap_->Test(pre_compact_klass))
          << "ref=" << ref
          << " post_compact_end=" << static_cast<void*>(post_compact_end_)
          << " pre_compact_klass=" << pre_compact_klass
          << " black_allocations_begin=" << static_cast<void*>(black_allocations_begin_);
      CHECK(live_words_bitmap_->Test(pre_compact_klass));
    }
    if (!IsValidObject(ref)) {
      std::ostringstream oss;
      oss << "Invalid object: "
          << "ref=" << ref
          << " klass=" << klass
          << " klass_klass=" << klass_klass
          << " klass_klass_klass=" << klass_klass_klass
          << " pre_compact_klass=" << pre_compact_klass
          << " from_space_begin=" << static_cast<void*>(from_space_begin_)
          << " pre_compact_begin=" << static_cast<void*>(bump_pointer_space_->Begin())
          << " post_compact_end=" << static_cast<void*>(post_compact_end_)
          << " black_allocations_begin=" << static_cast<void*>(black_allocations_begin_);

      // Call callback before dumping larger data like RAM and space dumps.
      callback(oss);

      oss << " \nobject="
          << heap_->GetVerification()->DumpRAMAroundAddress(reinterpret_cast<uintptr_t>(ref), 128)
          << " \nklass(from)="
          << heap_->GetVerification()->DumpRAMAroundAddress(reinterpret_cast<uintptr_t>(klass), 128)
          << "spaces:\n";
      heap_->DumpSpaces(oss);
      LOG(FATAL) << oss.str();
    }
  }
}

void MarkCompact::CompactPage(mirror::Object* obj,
                              uint32_t offset,
                              uint8_t* addr,
                              bool needs_memset_zero) {
  DCHECK(moving_space_bitmap_->Test(obj)
         && live_words_bitmap_->Test(obj));
  DCHECK(live_words_bitmap_->Test(offset)) << "obj=" << obj
                                           << " offset=" << offset
                                           << " addr=" << static_cast<void*>(addr)
                                           << " black_allocs_begin="
                                           << static_cast<void*>(black_allocations_begin_)
                                           << " post_compact_addr="
                                           << static_cast<void*>(post_compact_end_);
  uint8_t* const start_addr = addr;
  // How many distinct live-strides do we have.
  size_t stride_count = 0;
  uint8_t* last_stride = addr;
  uint32_t last_stride_begin = 0;
  auto verify_obj_callback = [&] (std::ostream& os) {
                               os << " stride_count=" << stride_count
                                  << " last_stride=" << static_cast<void*>(last_stride)
                                  << " offset=" << offset
                                  << " start_addr=" << static_cast<void*>(start_addr);
                             };
  obj = GetFromSpaceAddr(obj);
  live_words_bitmap_->VisitLiveStrides(
      offset,
      black_allocations_begin_,
      gPageSize,
      [&addr, &last_stride, &stride_count, &last_stride_begin, verify_obj_callback, this](
          uint32_t stride_begin, size_t stride_size, [[maybe_unused]] bool is_last)
          REQUIRES_SHARED(Locks::mutator_lock_) {
            const size_t stride_in_bytes = stride_size * kAlignment;
            DCHECK_LE(stride_in_bytes, gPageSize);
            last_stride_begin = stride_begin;
            DCHECK(IsAligned<kAlignment>(addr));
            memcpy(addr, from_space_begin_ + stride_begin * kAlignment, stride_in_bytes);
            if (kIsDebugBuild) {
              uint8_t* space_begin = bump_pointer_space_->Begin();
              // We can interpret the first word of the stride as an
              // obj only from second stride onwards, as the first
              // stride's first-object may have started on previous
              // page. The only exception is the first page of the
              // moving space.
              if (stride_count > 0 || stride_begin * kAlignment < gPageSize) {
                mirror::Object* o =
                    reinterpret_cast<mirror::Object*>(space_begin + stride_begin * kAlignment);
                CHECK(live_words_bitmap_->Test(o)) << "ref=" << o;
                CHECK(moving_space_bitmap_->Test(o))
                    << "ref=" << o << " bitmap: " << moving_space_bitmap_->DumpMemAround(o);
                VerifyObject(reinterpret_cast<mirror::Object*>(addr), verify_obj_callback);
              }
            }
            last_stride = addr;
            addr += stride_in_bytes;
            stride_count++;
          });
  DCHECK_LT(last_stride, start_addr + gPageSize);
  DCHECK_GT(stride_count, 0u);
  size_t obj_size = 0;
  uint32_t offset_within_obj = offset * kAlignment
                               - (reinterpret_cast<uint8_t*>(obj) - from_space_begin_);
  // First object
  if (offset_within_obj > 0) {
    mirror::Object* to_ref = reinterpret_cast<mirror::Object*>(start_addr - offset_within_obj);
    if (stride_count > 1) {
      RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/false> visitor(this,
                                                                         to_ref,
                                                                         start_addr,
                                                                         nullptr);
      obj_size = obj->VisitRefsForCompaction</*kFetchObjSize*/true, /*kVisitNativeRoots*/false>(
              visitor, MemberOffset(offset_within_obj), MemberOffset(-1));
    } else {
      RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/true> visitor(this,
                                                                        to_ref,
                                                                        start_addr,
                                                                        start_addr + gPageSize);
      obj_size = obj->VisitRefsForCompaction</*kFetchObjSize*/true, /*kVisitNativeRoots*/false>(
              visitor, MemberOffset(offset_within_obj), MemberOffset(offset_within_obj
                                                                     + gPageSize));
    }
    obj_size = RoundUp(obj_size, kAlignment);
    DCHECK_GT(obj_size, offset_within_obj)
        << "obj:" << obj
        << " class:"
        << obj->GetClass<kDefaultVerifyFlags, kWithFromSpaceBarrier>()
        << " to_addr:" << to_ref
        << " black-allocation-begin:" << reinterpret_cast<void*>(black_allocations_begin_)
        << " post-compact-end:" << reinterpret_cast<void*>(post_compact_end_)
        << " offset:" << offset * kAlignment
        << " class-after-obj-iter:"
        << (class_after_obj_iter_ != class_after_obj_ordered_map_.rend() ?
            class_after_obj_iter_->first.AsMirrorPtr() : nullptr)
        << " last-reclaimed-page:" << reinterpret_cast<void*>(last_reclaimed_page_)
        << " last-checked-reclaim-page-idx:" << last_checked_reclaim_page_idx_
        << " offset-of-last-idx:"
        << pre_compact_offset_moving_space_[last_checked_reclaim_page_idx_] * kAlignment
        << " first-obj-of-last-idx:"
        << first_objs_moving_space_[last_checked_reclaim_page_idx_].AsMirrorPtr();

    obj_size -= offset_within_obj;
    // If there is only one stride, then adjust last_stride_begin to the
    // end of the first object.
    if (stride_count == 1) {
      last_stride_begin += obj_size / kAlignment;
    }
  }

  // Except for the last page being compacted, the pages will have addr ==
  // start_addr + gPageSize.
  uint8_t* const end_addr = addr;
  addr = start_addr;
  size_t bytes_done = obj_size;
  // All strides except the last one can be updated without any boundary
  // checks.
  DCHECK_LE(addr, last_stride);
  size_t bytes_to_visit = last_stride - addr;
  DCHECK_LE(bytes_to_visit, gPageSize);
  while (bytes_to_visit > bytes_done) {
    mirror::Object* ref = reinterpret_cast<mirror::Object*>(addr + bytes_done);
    VerifyObject(ref, verify_obj_callback);
    RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/false>
            visitor(this, ref, nullptr, nullptr);
    obj_size = ref->VisitRefsForCompaction(visitor, MemberOffset(0), MemberOffset(-1));
    obj_size = RoundUp(obj_size, kAlignment);
    bytes_done += obj_size;
  }
  // Last stride may have multiple objects in it and we don't know where the
  // last object which crosses the page boundary starts, therefore check
  // page-end in all of these objects. Also, we need to call
  // VisitRefsForCompaction() with from-space object as we fetch object size,
  // which in case of klass requires 'class_size_'.
  uint8_t* from_addr = from_space_begin_ + last_stride_begin * kAlignment;
  bytes_to_visit = end_addr - addr;
  DCHECK_LE(bytes_to_visit, gPageSize);
  while (bytes_to_visit > bytes_done) {
    mirror::Object* ref = reinterpret_cast<mirror::Object*>(addr + bytes_done);
    obj = reinterpret_cast<mirror::Object*>(from_addr);
    VerifyObject(ref, verify_obj_callback);
    RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/true>
            visitor(this, ref, nullptr, start_addr + gPageSize);
    obj_size = obj->VisitRefsForCompaction(visitor,
                                           MemberOffset(0),
                                           MemberOffset(end_addr - (addr + bytes_done)));
    obj_size = RoundUp(obj_size, kAlignment);
    DCHECK_GT(obj_size, 0u)
        << "from_addr:" << obj
        << " from-space-class:"
        << obj->GetClass<kDefaultVerifyFlags, kWithFromSpaceBarrier>()
        << " to_addr:" << ref
        << " black-allocation-begin:" << reinterpret_cast<void*>(black_allocations_begin_)
        << " post-compact-end:" << reinterpret_cast<void*>(post_compact_end_)
        << " offset:" << offset * kAlignment
        << " bytes_done:" << bytes_done
        << " class-after-obj-iter:"
        << (class_after_obj_iter_ != class_after_obj_ordered_map_.rend() ?
            class_after_obj_iter_->first.AsMirrorPtr() : nullptr)
        << " last-reclaimed-page:" << reinterpret_cast<void*>(last_reclaimed_page_)
        << " last-checked-reclaim-page-idx:" << last_checked_reclaim_page_idx_
        << " offset-of-last-idx:"
        << pre_compact_offset_moving_space_[last_checked_reclaim_page_idx_] * kAlignment
        << " first-obj-of-last-idx:"
        << first_objs_moving_space_[last_checked_reclaim_page_idx_].AsMirrorPtr();

    from_addr += obj_size;
    bytes_done += obj_size;
  }
  // The last page that we compact may have some bytes left untouched in the
  // end, we should zero them as the kernel copies at page granularity.
  if (needs_memset_zero && UNLIKELY(bytes_done < gPageSize)) {
    std::memset(addr + bytes_done, 0x0, gPageSize - bytes_done);
  }
}

// We store the starting point (pre_compact_page - first_obj) and first-chunk's
// size. If more TLAB(s) started in this page, then those chunks are identified
// using mark bitmap. All this info is prepared in UpdateMovingSpaceBlackAllocations().
// If we find a set bit in the bitmap, then we copy the remaining page and then
// use the bitmap to visit each object for updating references.
void MarkCompact::SlideBlackPage(mirror::Object* first_obj,
                                 mirror::Object* next_page_first_obj,
                                 uint32_t first_chunk_size,
                                 uint8_t* const pre_compact_page,
                                 uint8_t* dest,
                                 bool needs_memset_zero) {
  DCHECK(IsAlignedParam(pre_compact_page, gPageSize));
  size_t bytes_copied;
  uint8_t* src_addr = reinterpret_cast<uint8_t*>(GetFromSpaceAddr(first_obj));
  uint8_t* pre_compact_addr = reinterpret_cast<uint8_t*>(first_obj);
  uint8_t* const pre_compact_page_end = pre_compact_page + gPageSize;
  uint8_t* const dest_page_end = dest + gPageSize;

  auto verify_obj_callback = [&] (std::ostream& os) {
                               os << " first_obj=" << first_obj
                                  << " next_page_first_obj=" << next_page_first_obj
                                  << " first_chunk_sie=" << first_chunk_size
                                  << " dest=" << static_cast<void*>(dest)
                                  << " pre_compact_page="
                                  << static_cast<void* const>(pre_compact_page);
                             };
  // We have empty portion at the beginning of the page. Zero it.
  if (pre_compact_addr > pre_compact_page) {
    bytes_copied = pre_compact_addr - pre_compact_page;
    DCHECK_LT(bytes_copied, gPageSize);
    if (needs_memset_zero) {
      std::memset(dest, 0x0, bytes_copied);
    }
    dest += bytes_copied;
  } else {
    bytes_copied = 0;
    size_t offset = pre_compact_page - pre_compact_addr;
    pre_compact_addr = pre_compact_page;
    src_addr += offset;
    DCHECK(IsAlignedParam(src_addr, gPageSize));
  }
  // Copy the first chunk of live words
  std::memcpy(dest, src_addr, first_chunk_size);
  // Update references in the first chunk. Use object size to find next object.
  {
    size_t bytes_to_visit = first_chunk_size;
    size_t obj_size;
    // The first object started in some previous page. So we need to check the
    // beginning.
    DCHECK_LE(reinterpret_cast<uint8_t*>(first_obj), pre_compact_addr);
    size_t offset = pre_compact_addr - reinterpret_cast<uint8_t*>(first_obj);
    if (bytes_copied == 0 && offset > 0) {
      mirror::Object* to_obj = reinterpret_cast<mirror::Object*>(dest - offset);
      mirror::Object* from_obj = reinterpret_cast<mirror::Object*>(src_addr - offset);
      // If the next page's first-obj is in this page or nullptr, then we don't
      // need to check end boundary
      if (next_page_first_obj == nullptr
          || (first_obj != next_page_first_obj
              && reinterpret_cast<uint8_t*>(next_page_first_obj) <= pre_compact_page_end)) {
        RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/false> visitor(this,
                                                                           to_obj,
                                                                           dest,
                                                                           nullptr);
        obj_size = from_obj->VisitRefsForCompaction<
                /*kFetchObjSize*/true, /*kVisitNativeRoots*/false>(visitor,
                                                                   MemberOffset(offset),
                                                                   MemberOffset(-1));
      } else {
        RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/true> visitor(this,
                                                                          to_obj,
                                                                          dest,
                                                                          dest_page_end);
        obj_size = from_obj->VisitRefsForCompaction<
                /*kFetchObjSize*/true, /*kVisitNativeRoots*/false>(visitor,
                                                                   MemberOffset(offset),
                                                                   MemberOffset(offset
                                                                                + gPageSize));
        if (first_obj == next_page_first_obj) {
          // First object is the only object on this page. So there's nothing else left to do.
          return;
        }
      }
      obj_size = RoundUp(obj_size, kAlignment);
      obj_size -= offset;
      dest += obj_size;
      bytes_to_visit -= obj_size;
    }
    bytes_copied += first_chunk_size;
    // If the last object in this page is next_page_first_obj, then we need to check end boundary
    bool check_last_obj = false;
    if (next_page_first_obj != nullptr
        && reinterpret_cast<uint8_t*>(next_page_first_obj) < pre_compact_page_end
        && bytes_copied == gPageSize) {
      size_t diff = pre_compact_page_end - reinterpret_cast<uint8_t*>(next_page_first_obj);
      DCHECK_LE(diff, gPageSize);
      DCHECK_LE(diff, bytes_to_visit);
      bytes_to_visit -= diff;
      check_last_obj = true;
    }
    while (bytes_to_visit > 0) {
      mirror::Object* dest_obj = reinterpret_cast<mirror::Object*>(dest);
      VerifyObject(dest_obj, verify_obj_callback);
      RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/false> visitor(this,
                                                                          dest_obj,
                                                                          nullptr,
                                                                          nullptr);
      obj_size = dest_obj->VisitRefsForCompaction(visitor, MemberOffset(0), MemberOffset(-1));
      obj_size = RoundUp(obj_size, kAlignment);
      bytes_to_visit -= obj_size;
      dest += obj_size;
    }
    DCHECK_EQ(bytes_to_visit, 0u);
    if (check_last_obj) {
      mirror::Object* dest_obj = reinterpret_cast<mirror::Object*>(dest);
      VerifyObject(dest_obj, verify_obj_callback);
      RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/true> visitor(this,
                                                                         dest_obj,
                                                                         nullptr,
                                                                         dest_page_end);
      mirror::Object* obj = GetFromSpaceAddr(next_page_first_obj);
      obj->VisitRefsForCompaction</*kFetchObjSize*/false>(visitor,
                                                          MemberOffset(0),
                                                          MemberOffset(dest_page_end - dest));
      return;
    }
  }

  // Probably a TLAB finished on this page and/or a new TLAB started as well.
  if (bytes_copied < gPageSize) {
    src_addr += first_chunk_size;
    pre_compact_addr += first_chunk_size;
    // Use mark-bitmap to identify where objects are. First call
    // VisitMarkedRange for only the first marked bit. If found, zero all bytes
    // until that object and then call memcpy on the rest of the page.
    // Then call VisitMarkedRange for all marked bits *after* the one found in
    // this invocation. This time to visit references.
    uintptr_t start_visit = reinterpret_cast<uintptr_t>(pre_compact_addr);
    uintptr_t page_end = reinterpret_cast<uintptr_t>(pre_compact_page_end);
    mirror::Object* found_obj = nullptr;
    moving_space_bitmap_->VisitMarkedRange</*kVisitOnce*/true>(start_visit,
                                                                page_end,
                                                                [&found_obj](mirror::Object* obj) {
                                                                  found_obj = obj;
                                                                });
    size_t remaining_bytes = gPageSize - bytes_copied;
    if (found_obj == nullptr) {
      if (needs_memset_zero) {
        // No more black objects in this page. Zero the remaining bytes and return.
        std::memset(dest, 0x0, remaining_bytes);
      }
      return;
    }
    // Copy everything in this page, which includes any zeroed regions
    // in-between.
    std::memcpy(dest, src_addr, remaining_bytes);
    DCHECK_LT(reinterpret_cast<uintptr_t>(found_obj), page_end);
    moving_space_bitmap_->VisitMarkedRange(
            reinterpret_cast<uintptr_t>(found_obj) + mirror::kObjectHeaderSize,
            page_end,
            [&found_obj, pre_compact_addr, dest, this, verify_obj_callback] (mirror::Object* obj)
            REQUIRES_SHARED(Locks::mutator_lock_) {
              ptrdiff_t diff = reinterpret_cast<uint8_t*>(found_obj) - pre_compact_addr;
              mirror::Object* ref = reinterpret_cast<mirror::Object*>(dest + diff);
              VerifyObject(ref, verify_obj_callback);
              RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/false>
                      visitor(this, ref, nullptr, nullptr);
              ref->VisitRefsForCompaction</*kFetchObjSize*/false>(visitor,
                                                                  MemberOffset(0),
                                                                  MemberOffset(-1));
              // Remember for next round.
              found_obj = obj;
            });
    // found_obj may have been updated in VisitMarkedRange. Visit the last found
    // object.
    DCHECK_GT(reinterpret_cast<uint8_t*>(found_obj), pre_compact_addr);
    DCHECK_LT(reinterpret_cast<uintptr_t>(found_obj), page_end);
    ptrdiff_t diff = reinterpret_cast<uint8_t*>(found_obj) - pre_compact_addr;
    mirror::Object* dest_obj = reinterpret_cast<mirror::Object*>(dest + diff);
    VerifyObject(dest_obj, verify_obj_callback);
    RefsUpdateVisitor</*kCheckBegin*/ false, /*kCheckEnd*/ true> visitor(
        this, dest_obj, nullptr, dest_page_end);
    // Last object could overlap with next page. And if it happens to be a
    // class, then we may access something (like static-fields' offsets) which
    // is on the next page. Therefore, use from-space's reference.
    mirror::Object* obj = GetFromSpaceAddr(found_obj);
    obj->VisitRefsForCompaction</*kFetchObjSize*/ false>(
        visitor, MemberOffset(0), MemberOffset(page_end - reinterpret_cast<uintptr_t>(found_obj)));
  }
}

template <bool kFirstPageMapping>
void MarkCompact::MapProcessedPages(uint8_t* to_space_start,
                                    Atomic<PageState>* state_arr,
                                    size_t arr_idx,
                                    size_t arr_len) {
  DCHECK(minor_fault_initialized_);
  DCHECK_LT(arr_idx, arr_len);
  DCHECK_ALIGNED_PARAM(to_space_start, gPageSize);
  // Claim all the contiguous pages, which are ready to be mapped, and then do
  // so in a single ioctl. This helps avoid the overhead of invoking syscall
  // several times and also maps the already-processed pages, avoiding
  // unnecessary faults on them.
  size_t length = kFirstPageMapping ? gPageSize : 0;
  if (kFirstPageMapping) {
    arr_idx++;
  }
  // We need to guarantee that we don't end up sucsessfully marking a later
  // page 'mapping' and then fail to mark an earlier page. To guarantee that
  // we use acq_rel order.
  for (; arr_idx < arr_len; arr_idx++, length += gPageSize) {
    PageState expected_state = PageState::kProcessed;
    if (!state_arr[arr_idx].compare_exchange_strong(
            expected_state, PageState::kProcessedAndMapping, std::memory_order_acq_rel)) {
      break;
    }
  }
  if (length > 0) {
    // Note: We need the first page to be attempted (to be mapped) by the ioctl
    // as this function is called due to some mutator thread waiting on the
    // 'to_space_start' page. Therefore, the ioctl must always be called
    // with 'to_space_start' as the 'start' address because it can bail out in
    // the middle (not attempting to map the subsequent pages) if it finds any
    // page either already mapped in between, or missing on the shadow-map.
    struct uffdio_continue uffd_continue;
    uffd_continue.range.start = reinterpret_cast<uintptr_t>(to_space_start);
    uffd_continue.range.len = length;
    uffd_continue.mode = 0;
    int ret = ioctl(uffd_, UFFDIO_CONTINUE, &uffd_continue);
    if (UNLIKELY(ret == -1 && errno == EAGAIN)) {
      // This can happen only in linear-alloc.
      DCHECK(linear_alloc_spaces_data_.end() !=
             std::find_if(linear_alloc_spaces_data_.begin(),
                          linear_alloc_spaces_data_.end(),
                          [to_space_start](const LinearAllocSpaceData& data) {
                            return data.begin_ <= to_space_start && to_space_start < data.end_;
                          }));

      // This could happen if userfaultfd couldn't find any pages mapped in the
      // shadow map. For instance, if there are certain (contiguous) pages on
      // linear-alloc which are allocated and have first-object set-up but have
      // not been accessed yet.
      // Bail out by setting the remaining pages' state back to kProcessed and
      // then waking up any waiting threads.
      DCHECK_GE(uffd_continue.mapped, 0);
      DCHECK_ALIGNED_PARAM(uffd_continue.mapped, gPageSize);
      DCHECK_LT(uffd_continue.mapped, static_cast<ssize_t>(length));
      if (kFirstPageMapping) {
        // In this case the first page must be mapped.
        DCHECK_GE(uffd_continue.mapped, static_cast<ssize_t>(gPageSize));
      }
      // Nobody would modify these pages' state simultaneously so only atomic
      // store is sufficient. Use 'release' order to ensure that all states are
      // modified sequentially.
      for (size_t remaining_len = length - uffd_continue.mapped; remaining_len > 0;
           remaining_len -= gPageSize) {
        arr_idx--;
        DCHECK_EQ(state_arr[arr_idx].load(std::memory_order_relaxed),
                  PageState::kProcessedAndMapping);
        state_arr[arr_idx].store(PageState::kProcessed, std::memory_order_release);
      }
      uffd_continue.range.start =
          reinterpret_cast<uintptr_t>(to_space_start) + uffd_continue.mapped;
      uffd_continue.range.len = length - uffd_continue.mapped;
      ret = ioctl(uffd_, UFFDIO_WAKE, &uffd_continue.range);
      CHECK_EQ(ret, 0) << "ioctl_userfaultfd: wake failed: " << strerror(errno);
    } else {
      // We may receive ENOENT if gc-thread unregisters the
      // range behind our back, which is fine because that
      // happens only when it knows compaction is done.
      CHECK(ret == 0 || !kFirstPageMapping || errno == ENOENT)
          << "ioctl_userfaultfd: continue failed: " << strerror(errno);
      if (ret == 0) {
        DCHECK_EQ(uffd_continue.mapped, static_cast<ssize_t>(length));
      }
    }
    if (use_uffd_sigbus_) {
      // Nobody else would modify these pages' state simultaneously so atomic
      // store is sufficient.
      for (; uffd_continue.mapped > 0; uffd_continue.mapped -= gPageSize) {
        arr_idx--;
        DCHECK_EQ(state_arr[arr_idx].load(std::memory_order_relaxed),
                  PageState::kProcessedAndMapping);
        state_arr[arr_idx].store(PageState::kProcessedAndMapped, std::memory_order_release);
      }
    }
  }
}

void MarkCompact::ZeropageIoctl(void* addr, bool tolerate_eexist, bool tolerate_enoent) {
  struct uffdio_zeropage uffd_zeropage;
  DCHECK(IsAlignedParam(addr, gPageSize));
  uffd_zeropage.range.start = reinterpret_cast<uintptr_t>(addr);
  uffd_zeropage.range.len = gPageSize;
  uffd_zeropage.mode = 0;
  int ret = ioctl(uffd_, UFFDIO_ZEROPAGE, &uffd_zeropage);
  if (LIKELY(ret == 0)) {
    DCHECK_EQ(uffd_zeropage.zeropage, static_cast<ssize_t>(gPageSize));
  } else {
    CHECK((tolerate_enoent && errno == ENOENT) || (tolerate_eexist && errno == EEXIST))
        << "ioctl_userfaultfd: zeropage failed: " << strerror(errno) << ". addr:" << addr;
  }
}

void MarkCompact::CopyIoctl(void* dst, void* buffer) {
  struct uffdio_copy uffd_copy;
  uffd_copy.src = reinterpret_cast<uintptr_t>(buffer);
  uffd_copy.dst = reinterpret_cast<uintptr_t>(dst);
  uffd_copy.len = gPageSize;
  uffd_copy.mode = 0;
  CHECK_EQ(ioctl(uffd_, UFFDIO_COPY, &uffd_copy), 0)
      << "ioctl_userfaultfd: copy failed: " << strerror(errno) << ". src:" << buffer
      << " dst:" << dst;
  DCHECK_EQ(uffd_copy.copy, static_cast<ssize_t>(gPageSize));
}

template <int kMode, typename CompactionFn>
void MarkCompact::DoPageCompactionWithStateChange(size_t page_idx,
                                                  size_t status_arr_len,
                                                  uint8_t* to_space_page,
                                                  uint8_t* page,
                                                  CompactionFn func) {
  PageState expected_state = PageState::kUnprocessed;
  PageState desired_state =
      kMode == kCopyMode ? PageState::kProcessingAndMapping : PageState::kProcessing;
  // In the concurrent case (kMode != kFallbackMode) we need to ensure that the update
  // to moving_spaces_status_[page_idx] is released before the contents of the page are
  // made accessible to other threads.
  //
  // We need acquire ordering here to ensure that when the CAS fails, another thread
  // has completed processing the page, which is guaranteed by the release below.
  if (kMode == kFallbackMode || moving_pages_status_[page_idx].compare_exchange_strong(
                                    expected_state, desired_state, std::memory_order_acquire)) {
    func();
    if (kMode == kCopyMode) {
      CopyIoctl(to_space_page, page);
      if (use_uffd_sigbus_) {
        // Store is sufficient as no other thread would modify the status at this point.
        moving_pages_status_[page_idx].store(PageState::kProcessedAndMapped,
                                             std::memory_order_release);
      }
    } else if (kMode == kMinorFaultMode) {
      expected_state = PageState::kProcessing;
      desired_state = PageState::kProcessed;
      // the CAS needs to be with release order to ensure that stores to the
      // page makes it to memory *before* other threads observe that it's
      // ready to be mapped.
      if (!moving_pages_status_[page_idx].compare_exchange_strong(
              expected_state, desired_state, std::memory_order_release)) {
        // Some mutator has requested to map the page after processing it.
        DCHECK_EQ(expected_state, PageState::kProcessingAndMapping);
        MapProcessedPages</*kFirstPageMapping=*/true>(
            to_space_page, moving_pages_status_, page_idx, status_arr_len);
      }
    }
  } else {
    DCHECK_GT(expected_state, PageState::kProcessed);
  }
}

void MarkCompact::FreeFromSpacePages(size_t cur_page_idx, int mode) {
  // Thanks to sliding compaction, bump-pointer allocations, and reverse
  // compaction (see CompactMovingSpace) the logic here is pretty simple: find
  // the to-space page up to which compaction has finished, all the from-space
  // pages corresponding to this onwards can be freed. There are some corner
  // cases to be taken care of, which are described below.
  size_t idx = last_checked_reclaim_page_idx_;
  // Find the to-space page up to which the corresponding from-space pages can be
  // freed.
  for (; idx > cur_page_idx; idx--) {
    PageState state = moving_pages_status_[idx - 1].load(std::memory_order_acquire);
    if (state == PageState::kMutatorProcessing) {
      // Some mutator is working on the page.
      break;
    }
    DCHECK(state >= PageState::kProcessed ||
           (state == PageState::kUnprocessed &&
            (mode == kFallbackMode || idx > moving_first_objs_count_)));
  }
  DCHECK_LE(idx, last_checked_reclaim_page_idx_);
  if (idx == last_checked_reclaim_page_idx_) {
    // Nothing to do.
    return;
  }

  uint8_t* reclaim_begin;
  uint8_t* idx_addr;
  // Calculate the first from-space page to be freed using 'idx'. If the
  // first-object of the idx'th to-space page started before the corresponding
  // from-space page, which is almost always the case in the compaction portion
  // of the moving-space, then it indicates that the subsequent pages that are
  // yet to be compacted will need the from-space pages. Therefore, find the page
  // (from the already compacted pages) whose first-object is different from
  // ours. All the from-space pages starting from that one are safe to be
  // removed. Please note that this iteration is not expected to be long in
  // normal cases as objects are smaller than page size.
  if (idx >= moving_first_objs_count_) {
    // black-allocated portion of the moving-space
    idx_addr = black_allocations_begin_ + (idx - moving_first_objs_count_) * gPageSize;
    reclaim_begin = idx_addr;
    mirror::Object* first_obj = first_objs_moving_space_[idx].AsMirrorPtr();
    if (first_obj != nullptr && reinterpret_cast<uint8_t*>(first_obj) < reclaim_begin) {
      size_t idx_len = moving_first_objs_count_ + black_page_count_;
      for (size_t i = idx + 1; i < idx_len; i++) {
        mirror::Object* obj = first_objs_moving_space_[i].AsMirrorPtr();
        // A null first-object indicates that the corresponding to-space page is
        // not used yet. So we can compute its from-space page and use that.
        if (obj != first_obj) {
          reclaim_begin = obj != nullptr
                          ? AlignUp(reinterpret_cast<uint8_t*>(obj), gPageSize)
                          : (black_allocations_begin_ + (i - moving_first_objs_count_) * gPageSize);
          break;
        }
      }
    }
  } else {
    DCHECK_GE(pre_compact_offset_moving_space_[idx], 0u);
    idx_addr = bump_pointer_space_->Begin() + pre_compact_offset_moving_space_[idx] * kAlignment;
    reclaim_begin = idx_addr;
    DCHECK_LE(reclaim_begin, black_allocations_begin_);
    mirror::Object* first_obj = first_objs_moving_space_[idx].AsMirrorPtr();
    if (reinterpret_cast<uint8_t*>(first_obj) < reclaim_begin) {
      DCHECK_LT(idx, moving_first_objs_count_);
      mirror::Object* obj = first_obj;
      for (size_t i = idx + 1; i < moving_first_objs_count_; i++) {
        obj = first_objs_moving_space_[i].AsMirrorPtr();
        if (first_obj != obj) {
          DCHECK_LT(first_obj, obj);
          DCHECK_LT(reclaim_begin, reinterpret_cast<uint8_t*>(obj));
          reclaim_begin = reinterpret_cast<uint8_t*>(obj);
          break;
        }
      }
      if (obj == first_obj) {
        reclaim_begin = black_allocations_begin_;
      }
    }
    reclaim_begin = AlignUp(reclaim_begin, gPageSize);
  }

  DCHECK_NE(reclaim_begin, nullptr);
  DCHECK_ALIGNED_PARAM(reclaim_begin, gPageSize);
  DCHECK_ALIGNED_PARAM(last_reclaimed_page_, gPageSize);
  // Check if the 'class_after_obj_map_' map allows pages to be freed.
  for (; class_after_obj_iter_ != class_after_obj_ordered_map_.rend(); class_after_obj_iter_++) {
    mirror::Object* klass = class_after_obj_iter_->first.AsMirrorPtr();
    mirror::Class* from_klass = static_cast<mirror::Class*>(GetFromSpaceAddr(klass));
    // Check with class' end to ensure that, if required, the entire class survives.
    uint8_t* klass_end = reinterpret_cast<uint8_t*>(klass) + from_klass->SizeOf<kVerifyNone>();
    DCHECK_LE(klass_end, last_reclaimed_page_);
    if (reinterpret_cast<uint8_t*>(klass_end) >= reclaim_begin) {
      // Found a class which is in the reclaim range.
      uint8_t* obj_addr = reinterpret_cast<uint8_t*>(class_after_obj_iter_->second.AsMirrorPtr());
      // NOTE: Don't assert that obj is of 'klass' type as klass could instead
      // be its super-class.
      if (obj_addr < idx_addr) {
        // Its lowest-address object is not compacted yet. Reclaim starting from
        // the end of this class.
        reclaim_begin = AlignUp(klass_end, gPageSize);
      } else {
        // Continue consuming pairs wherein the lowest address object has already
        // been compacted.
        continue;
      }
    }
    // All the remaining class (and thereby corresponding object) addresses are
    // lower than the reclaim range.
    break;
  }

  ssize_t size = last_reclaimed_page_ - reclaim_begin;
  if (size >= kMinFromSpaceMadviseSize) {
    int behavior = minor_fault_initialized_ ? MADV_REMOVE : MADV_DONTNEED;
    CHECK_EQ(madvise(reclaim_begin + from_space_slide_diff_, size, behavior), 0)
        << "madvise of from-space failed: " << strerror(errno);
    last_reclaimed_page_ = reclaim_begin;
  }
  last_checked_reclaim_page_idx_ = idx;
}

void MarkCompact::UpdateClassAfterObjMap() {
  CHECK(class_after_obj_ordered_map_.empty());
  for (const auto& pair : class_after_obj_hash_map_) {
    auto super_class_iter = super_class_after_class_hash_map_.find(pair.first);
    ObjReference key = super_class_iter != super_class_after_class_hash_map_.end()
                       ? super_class_iter->second
                       : pair.first;
    if (std::less<mirror::Object*>{}(pair.second.AsMirrorPtr(), key.AsMirrorPtr()) &&
        HasAddress(key.AsMirrorPtr())) {
      auto [ret_iter, success] = class_after_obj_ordered_map_.try_emplace(key, pair.second);
      // It could fail only if the class 'key' has objects of its own, which are lower in
      // address order, as well of some of its derived class. In this case
      // choose the lowest address object.
      if (!success &&
          std::less<mirror::Object*>{}(pair.second.AsMirrorPtr(), ret_iter->second.AsMirrorPtr())) {
        ret_iter->second = pair.second;
      }
    }
  }
  class_after_obj_hash_map_.clear();
  super_class_after_class_hash_map_.clear();
}

template <int kMode>
void MarkCompact::CompactMovingSpace(uint8_t* page) {
  // For every page we have a starting object, which may have started in some
  // preceding page, and an offset within that object from where we must start
  // copying.
  // Consult the live-words bitmap to copy all contiguously live words at a
  // time. These words may constitute multiple objects. To avoid the need for
  // consulting mark-bitmap to find where does the next live object start, we
  // use the object-size returned by VisitRefsForCompaction.
  //
  // We do the compaction in reverse direction so that the pages containing
  // TLAB and latest allocations are processed first.
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  size_t page_status_arr_len = moving_first_objs_count_ + black_page_count_;
  size_t idx = page_status_arr_len;
  uint8_t* to_space_end = bump_pointer_space_->Begin() + page_status_arr_len * gPageSize;
  uint8_t* shadow_space_end = nullptr;
  if (kMode == kMinorFaultMode) {
    shadow_space_end = shadow_to_space_map_.Begin() + page_status_arr_len * gPageSize;
  }
  uint8_t* pre_compact_page = black_allocations_begin_ + (black_page_count_ * gPageSize);

  DCHECK(IsAlignedParam(pre_compact_page, gPageSize));

  UpdateClassAfterObjMap();
  // These variables are maintained by FreeFromSpacePages().
  last_reclaimed_page_ = pre_compact_page;
  last_checked_reclaim_page_idx_ = idx;
  class_after_obj_iter_ = class_after_obj_ordered_map_.rbegin();
  // Allocated-black pages
  mirror::Object* next_page_first_obj = nullptr;
  while (idx > moving_first_objs_count_) {
    idx--;
    pre_compact_page -= gPageSize;
    to_space_end -= gPageSize;
    if (kMode == kMinorFaultMode) {
      shadow_space_end -= gPageSize;
      page = shadow_space_end;
    } else if (kMode == kFallbackMode) {
      page = to_space_end;
    }
    mirror::Object* first_obj = first_objs_moving_space_[idx].AsMirrorPtr();
    uint32_t first_chunk_size = black_alloc_pages_first_chunk_size_[idx];
    if (first_obj != nullptr) {
      DoPageCompactionWithStateChange<kMode>(idx,
                                             page_status_arr_len,
                                             to_space_end,
                                             page,
                                             [&]() REQUIRES_SHARED(Locks::mutator_lock_) {
                                               SlideBlackPage(first_obj,
                                                              next_page_first_obj,
                                                              first_chunk_size,
                                                              pre_compact_page,
                                                              page,
                                                              kMode == kCopyMode);
                                             });
      // We are sliding here, so no point attempting to madvise for every
      // page. Wait for enough pages to be done.
      if (idx % DivideByPageSize(kMinFromSpaceMadviseSize) == 0) {
        FreeFromSpacePages(idx, kMode);
      }
    }
    next_page_first_obj = first_obj;
  }
  DCHECK_EQ(pre_compact_page, black_allocations_begin_);

  while (idx > 0) {
    idx--;
    to_space_end -= gPageSize;
    if (kMode == kMinorFaultMode) {
      shadow_space_end -= gPageSize;
      page = shadow_space_end;
    } else if (kMode == kFallbackMode) {
      page = to_space_end;
    }
    mirror::Object* first_obj = first_objs_moving_space_[idx].AsMirrorPtr();
    DoPageCompactionWithStateChange<kMode>(
        idx, page_status_arr_len, to_space_end, page, [&]() REQUIRES_SHARED(Locks::mutator_lock_) {
          CompactPage(first_obj, pre_compact_offset_moving_space_[idx], page, kMode == kCopyMode);
        });
    FreeFromSpacePages(idx, kMode);
  }
  DCHECK_EQ(to_space_end, bump_pointer_space_->Begin());
}

void MarkCompact::UpdateNonMovingPage(mirror::Object* first, uint8_t* page) {
  DCHECK_LT(reinterpret_cast<uint8_t*>(first), page + gPageSize);
  // For every object found in the page, visit the previous object. This ensures
  // that we can visit without checking page-end boundary.
  // Call VisitRefsForCompaction with from-space read-barrier as the klass object and
  // super-class loads require it.
  // TODO: Set kVisitNativeRoots to false once we implement concurrent
  // compaction
  mirror::Object* curr_obj = first;
  non_moving_space_bitmap_->VisitMarkedRange(
          reinterpret_cast<uintptr_t>(first) + mirror::kObjectHeaderSize,
          reinterpret_cast<uintptr_t>(page + gPageSize),
          [&](mirror::Object* next_obj) {
            // TODO: Once non-moving space update becomes concurrent, we'll
            // require fetching the from-space address of 'curr_obj' and then call
            // visitor on that.
            if (reinterpret_cast<uint8_t*>(curr_obj) < page) {
              RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/false>
                      visitor(this, curr_obj, page, page + gPageSize);
              MemberOffset begin_offset(page - reinterpret_cast<uint8_t*>(curr_obj));
              // Native roots shouldn't be visited as they are done when this
              // object's beginning was visited in the preceding page.
              curr_obj->VisitRefsForCompaction</*kFetchObjSize*/false, /*kVisitNativeRoots*/false>(
                      visitor, begin_offset, MemberOffset(-1));
            } else {
              RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/false>
                      visitor(this, curr_obj, page, page + gPageSize);
              curr_obj->VisitRefsForCompaction</*kFetchObjSize*/false>(visitor,
                                                                       MemberOffset(0),
                                                                       MemberOffset(-1));
            }
            curr_obj = next_obj;
          });

  MemberOffset end_offset(page + gPageSize - reinterpret_cast<uint8_t*>(curr_obj));
  if (reinterpret_cast<uint8_t*>(curr_obj) < page) {
    RefsUpdateVisitor</*kCheckBegin*/true, /*kCheckEnd*/true>
            visitor(this, curr_obj, page, page + gPageSize);
    curr_obj->VisitRefsForCompaction</*kFetchObjSize*/false, /*kVisitNativeRoots*/false>(
            visitor, MemberOffset(page - reinterpret_cast<uint8_t*>(curr_obj)), end_offset);
  } else {
    RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/true>
            visitor(this, curr_obj, page, page + gPageSize);
    curr_obj->VisitRefsForCompaction</*kFetchObjSize*/false>(visitor, MemberOffset(0), end_offset);
  }
}

void MarkCompact::UpdateNonMovingSpace() {
  TimingLogger::ScopedTiming t("(Paused)UpdateNonMovingSpace", GetTimings());
  // Iterating in reverse ensures that the class pointer in objects which span
  // across more than one page gets updated in the end. This is necessary for
  // VisitRefsForCompaction() to work correctly.
  // TODO: If and when we make non-moving space update concurrent, implement a
  // mechanism to remember class pointers for such objects off-heap and pass it
  // to VisitRefsForCompaction().
  uint8_t* page = non_moving_space_->Begin() + non_moving_first_objs_count_ * gPageSize;
  for (ssize_t i = non_moving_first_objs_count_ - 1; i >= 0; i--) {
    mirror::Object* obj = first_objs_non_moving_space_[i].AsMirrorPtr();
    page -= gPageSize;
    // null means there are no objects on the page to update references.
    if (obj != nullptr) {
      UpdateNonMovingPage(obj, page);
    }
  }
}

void MarkCompact::UpdateMovingSpaceBlackAllocations() {
  // For sliding black pages, we need the first-object, which overlaps with the
  // first byte of the page. Additionally, we compute the size of first chunk of
  // black objects. This will suffice for most black pages. Unlike, compaction
  // pages, here we don't need to pre-compute the offset within first-obj from
  // where sliding has to start. That can be calculated using the pre-compact
  // address of the page. Therefore, to save space, we store the first chunk's
  // size in black_alloc_pages_first_chunk_size_ array.
  // For the pages which may have holes after the first chunk, which could happen
  // if a new TLAB starts in the middle of the page, we mark the objects in
  // the mark-bitmap. So, if the first-chunk size is smaller than gPageSize,
  // then we use the mark-bitmap for the remainder of the page.
  uint8_t* const begin = bump_pointer_space_->Begin();
  uint8_t* black_allocs = black_allocations_begin_;
  DCHECK_LE(begin, black_allocs);
  size_t consumed_blocks_count = 0;
  size_t first_block_size;
  // Needed only for debug at the end of the function. Hopefully compiler will
  // eliminate it otherwise.
  size_t num_blocks = 0;
  // Get the list of all blocks allocated in the bump-pointer space.
  std::vector<size_t>* block_sizes = bump_pointer_space_->GetBlockSizes(thread_running_gc_,
                                                                        &first_block_size);
  DCHECK_LE(first_block_size, (size_t)(black_allocs - begin));
  if (block_sizes != nullptr) {
    size_t black_page_idx = moving_first_objs_count_;
    uint8_t* block_end = begin + first_block_size;
    uint32_t remaining_chunk_size = 0;
    uint32_t first_chunk_size = 0;
    mirror::Object* first_obj = nullptr;
    num_blocks = block_sizes->size();
    for (size_t block_size : *block_sizes) {
      block_end += block_size;
      // Skip the blocks that are prior to the black allocations. These will be
      // merged with the main-block later.
      if (black_allocs >= block_end) {
        consumed_blocks_count++;
        continue;
      }
      mirror::Object* obj = reinterpret_cast<mirror::Object*>(black_allocs);
      bool set_mark_bit = remaining_chunk_size > 0;
      // We don't know how many objects are allocated in the current block. When we hit
      // a null assume it's the end. This works as every block is expected to
      // have objects allocated linearly using bump-pointer.
      // BumpPointerSpace::Walk() also works similarly.
      while (black_allocs < block_end
             && obj->GetClass<kDefaultVerifyFlags, kWithoutReadBarrier>() != nullptr) {
        // Try to keep instructions which access class instance together to
        // avoid reloading the pointer from object.
        size_t obj_size = obj->SizeOf();
        bytes_scanned_ += obj_size;
        obj_size = RoundUp(obj_size, kAlignment);
        UpdateClassAfterObjectMap(obj);
        if (first_obj == nullptr) {
          first_obj = obj;
        }
        // We only need the mark-bitmap in the pages wherein a new TLAB starts in
        // the middle of the page.
        if (set_mark_bit) {
          moving_space_bitmap_->Set(obj);
        }
        // Handle objects which cross page boundary, including objects larger
        // than page size.
        if (remaining_chunk_size + obj_size >= gPageSize) {
          set_mark_bit = false;
          first_chunk_size += gPageSize - remaining_chunk_size;
          remaining_chunk_size += obj_size;
          // We should not store first-object and remaining_chunk_size if there were
          // unused bytes before this TLAB, in which case we must have already
          // stored the values (below).
          if (black_alloc_pages_first_chunk_size_[black_page_idx] == 0) {
            black_alloc_pages_first_chunk_size_[black_page_idx] = first_chunk_size;
            first_objs_moving_space_[black_page_idx].Assign(first_obj);
          }
          black_page_idx++;
          remaining_chunk_size -= gPageSize;
          // Consume an object larger than page size.
          while (remaining_chunk_size >= gPageSize) {
            black_alloc_pages_first_chunk_size_[black_page_idx] = gPageSize;
            first_objs_moving_space_[black_page_idx].Assign(obj);
            black_page_idx++;
            remaining_chunk_size -= gPageSize;
          }
          first_obj = remaining_chunk_size > 0 ? obj : nullptr;
          first_chunk_size = remaining_chunk_size;
        } else {
          DCHECK_LE(first_chunk_size, remaining_chunk_size);
          first_chunk_size += obj_size;
          remaining_chunk_size += obj_size;
        }
        black_allocs += obj_size;
        obj = reinterpret_cast<mirror::Object*>(black_allocs);
      }
      DCHECK_LE(black_allocs, block_end);
      DCHECK_LT(remaining_chunk_size, gPageSize);
      // consume the unallocated portion of the block
      if (black_allocs < block_end) {
        // first-chunk of the current page ends here. Store it.
        if (first_chunk_size > 0 && black_alloc_pages_first_chunk_size_[black_page_idx] == 0) {
          black_alloc_pages_first_chunk_size_[black_page_idx] = first_chunk_size;
          first_objs_moving_space_[black_page_idx].Assign(first_obj);
        }
        first_chunk_size = 0;
        first_obj = nullptr;
        size_t page_remaining = gPageSize - remaining_chunk_size;
        size_t block_remaining = block_end - black_allocs;
        if (page_remaining <= block_remaining) {
          block_remaining -= page_remaining;
          // current page and the subsequent empty pages in the block
          black_page_idx += 1 + DivideByPageSize(block_remaining);
          remaining_chunk_size = ModuloPageSize(block_remaining);
        } else {
          remaining_chunk_size += block_remaining;
        }
        black_allocs = block_end;
      }
    }
    if (black_page_idx < DivideByPageSize(bump_pointer_space_->Size())) {
      // Store the leftover first-chunk, if any, and update page index.
      if (black_alloc_pages_first_chunk_size_[black_page_idx] > 0) {
        black_page_idx++;
      } else if (first_chunk_size > 0) {
        black_alloc_pages_first_chunk_size_[black_page_idx] = first_chunk_size;
        first_objs_moving_space_[black_page_idx].Assign(first_obj);
        black_page_idx++;
      }
    }
    black_page_count_ = black_page_idx - moving_first_objs_count_;
    delete block_sizes;
  }
  // Update bump-pointer space by consuming all the pre-black blocks into the
  // main one.
  bump_pointer_space_->SetBlockSizes(thread_running_gc_,
                                     post_compact_end_ - begin,
                                     consumed_blocks_count);
  if (kIsDebugBuild) {
    size_t moving_space_size = bump_pointer_space_->Size();
    size_t los_size = 0;
    if (heap_->GetLargeObjectsSpace()) {
      los_size = heap_->GetLargeObjectsSpace()->GetBytesAllocated();
    }
    // The moving-space size is already updated to post-compact size in SetBlockSizes above.
    // Also, bytes-allocated has already been adjusted with large-object space' freed-bytes
    // in Sweep(), but not with moving-space freed-bytes.
    CHECK_GE(heap_->GetBytesAllocated() - black_objs_slide_diff_, moving_space_size + los_size)
        << " moving-space size:" << moving_space_size
        << " moving-space bytes-freed:" << black_objs_slide_diff_
        << " large-object-space size:" << los_size
        << " large-object-space bytes-freed:" << GetCurrentIteration()->GetFreedLargeObjectBytes()
        << " num-tlabs-merged:" << consumed_blocks_count
        << " main-block-size:" << (post_compact_end_ - begin)
        << " total-tlabs-moving-space:" << num_blocks;
  }
}

void MarkCompact::UpdateNonMovingSpaceBlackAllocations() {
  accounting::ObjectStack* stack = heap_->GetAllocationStack();
  const StackReference<mirror::Object>* limit = stack->End();
  uint8_t* const space_begin = non_moving_space_->Begin();
  for (StackReference<mirror::Object>* it = stack->Begin(); it != limit; ++it) {
    mirror::Object* obj = it->AsMirrorPtr();
    if (obj != nullptr && non_moving_space_bitmap_->HasAddress(obj)) {
      non_moving_space_bitmap_->Set(obj);
      // Clear so that we don't try to set the bit again in the next GC-cycle.
      it->Clear();
      size_t idx = DivideByPageSize(reinterpret_cast<uint8_t*>(obj) - space_begin);
      uint8_t* page_begin = AlignDown(reinterpret_cast<uint8_t*>(obj), gPageSize);
      mirror::Object* first_obj = first_objs_non_moving_space_[idx].AsMirrorPtr();
      if (first_obj == nullptr
          || (obj < first_obj && reinterpret_cast<uint8_t*>(first_obj) > page_begin)) {
        first_objs_non_moving_space_[idx].Assign(obj);
      }
      mirror::Object* next_page_first_obj = first_objs_non_moving_space_[++idx].AsMirrorPtr();
      uint8_t* next_page_begin = page_begin + gPageSize;
      if (next_page_first_obj == nullptr
          || reinterpret_cast<uint8_t*>(next_page_first_obj) > next_page_begin) {
        size_t obj_size = RoundUp(obj->SizeOf<kDefaultVerifyFlags>(), kAlignment);
        uint8_t* obj_end = reinterpret_cast<uint8_t*>(obj) + obj_size;
        while (next_page_begin < obj_end) {
          first_objs_non_moving_space_[idx++].Assign(obj);
          next_page_begin += gPageSize;
        }
      }
      // update first_objs count in case we went past non_moving_first_objs_count_
      non_moving_first_objs_count_ = std::max(non_moving_first_objs_count_, idx);
    }
  }
}

class MarkCompact::ImmuneSpaceUpdateObjVisitor {
 public:
  explicit ImmuneSpaceUpdateObjVisitor(MarkCompact* collector) : collector_(collector) {}

  void operator()(mirror::Object* obj) const ALWAYS_INLINE REQUIRES(Locks::mutator_lock_) {
    RefsUpdateVisitor</*kCheckBegin*/false, /*kCheckEnd*/false> visitor(collector_,
                                                                        obj,
                                                                        /*begin_*/nullptr,
                                                                        /*end_*/nullptr);
    obj->VisitRefsForCompaction</*kFetchObjSize*/ false>(
        visitor, MemberOffset(0), MemberOffset(-1));
  }

  static void Callback(mirror::Object* obj, void* arg) REQUIRES(Locks::mutator_lock_) {
    reinterpret_cast<ImmuneSpaceUpdateObjVisitor*>(arg)->operator()(obj);
  }

 private:
  MarkCompact* const collector_;
};

class MarkCompact::ClassLoaderRootsUpdater : public ClassLoaderVisitor {
 public:
  explicit ClassLoaderRootsUpdater(MarkCompact* collector)
      : collector_(collector),
        moving_space_begin_(collector->moving_space_begin_),
        moving_space_end_(collector->moving_space_end_) {}

  void Visit(ObjPtr<mirror::ClassLoader> class_loader) override
      REQUIRES_SHARED(Locks::classlinker_classes_lock_, Locks::mutator_lock_) {
    ClassTable* const class_table = class_loader->GetClassTable();
    if (class_table != nullptr) {
      // Classes are updated concurrently.
      class_table->VisitRoots(*this, /*skip_classes=*/true);
    }
  }

  void VisitRootIfNonNull(mirror::CompressedReference<mirror::Object>* root) const ALWAYS_INLINE
      REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const ALWAYS_INLINE
      REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_) {
    collector_->UpdateRoot(
        root, moving_space_begin_, moving_space_end_, RootInfo(RootType::kRootVMInternal));
  }

 private:
  MarkCompact* collector_;
  uint8_t* const moving_space_begin_;
  uint8_t* const moving_space_end_;
};

class MarkCompact::LinearAllocPageUpdater {
 public:
  explicit LinearAllocPageUpdater(MarkCompact* collector)
      : collector_(collector),
        moving_space_begin_(collector->moving_space_begin_),
        moving_space_end_(collector->moving_space_end_),
        last_page_touched_(false) {}

  // Update a page in multi-object arena.
  void MultiObjectArena(uint8_t* page_begin, uint8_t* first_obj)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(first_obj != nullptr);
    DCHECK_ALIGNED_PARAM(page_begin, gPageSize);
    uint8_t* page_end = page_begin + gPageSize;
    uint32_t obj_size;
    for (uint8_t* byte = first_obj; byte < page_end;) {
      TrackingHeader* header = reinterpret_cast<TrackingHeader*>(byte);
      obj_size = header->GetSize();
      if (UNLIKELY(obj_size == 0)) {
        // No more objects in this page to visit.
        last_page_touched_ = byte >= page_begin;
        return;
      }
      uint8_t* obj = byte + sizeof(TrackingHeader);
      uint8_t* obj_end = byte + obj_size;
      if (header->Is16Aligned()) {
        obj = AlignUp(obj, 16);
      }
      uint8_t* begin_boundary = std::max(obj, page_begin);
      uint8_t* end_boundary = std::min(obj_end, page_end);
      if (begin_boundary < end_boundary) {
        VisitObject(header->GetKind(), obj, begin_boundary, end_boundary);
      }
      if (ArenaAllocator::IsRunningOnMemoryTool()) {
        obj_size += ArenaAllocator::kMemoryToolRedZoneBytes;
      }
      byte += RoundUp(obj_size, LinearAlloc::kAlignment);
    }
    last_page_touched_ = true;
  }

  // This version is only used for cases where the entire page is filled with
  // GC-roots. For example, class-table and intern-table.
  void SingleObjectArena(uint8_t* page_begin, size_t page_size)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    static_assert(sizeof(uint32_t) == sizeof(GcRoot<mirror::Object>));
    DCHECK_ALIGNED(page_begin, kAlignment);
    // Least significant bits are used by class-table.
    static constexpr uint32_t kMask = kObjectAlignment - 1;
    size_t num_roots = page_size / sizeof(GcRoot<mirror::Object>);
    uint32_t* root_ptr = reinterpret_cast<uint32_t*>(page_begin);
    for (size_t i = 0; i < num_roots; root_ptr++, i++) {
      uint32_t word = *root_ptr;
      if (word != 0) {
        uint32_t lsbs = word & kMask;
        word &= ~kMask;
        VisitRootIfNonNull(reinterpret_cast<mirror::CompressedReference<mirror::Object>*>(&word));
        *root_ptr = word | lsbs;
        last_page_touched_ = true;
      }
    }
  }

  bool WasLastPageTouched() const { return last_page_touched_; }

  void VisitRootIfNonNull(mirror::CompressedReference<mirror::Object>* root) const
      ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const
      ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    mirror::Object* old_ref = root->AsMirrorPtr();
    DCHECK_NE(old_ref, nullptr);
    if (MarkCompact::HasAddress(old_ref, moving_space_begin_, moving_space_end_)) {
      mirror::Object* new_ref = old_ref;
      if (reinterpret_cast<uint8_t*>(old_ref) >= collector_->black_allocations_begin_) {
        new_ref = collector_->PostCompactBlackObjAddr(old_ref);
      } else if (collector_->live_words_bitmap_->Test(old_ref)) {
        DCHECK(collector_->moving_space_bitmap_->Test(old_ref))
            << "ref:" << old_ref << " root:" << root;
        new_ref = collector_->PostCompactOldObjAddr(old_ref);
      }
      if (old_ref != new_ref) {
        root->Assign(new_ref);
      }
    }
  }

 private:
  void VisitObject(LinearAllocKind kind,
                   void* obj,
                   uint8_t* start_boundary,
                   uint8_t* end_boundary) const ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    switch (kind) {
      case LinearAllocKind::kNoGCRoots:
        break;
      case LinearAllocKind::kGCRootArray:
        {
          GcRoot<mirror::Object>* root = reinterpret_cast<GcRoot<mirror::Object>*>(start_boundary);
          GcRoot<mirror::Object>* last = reinterpret_cast<GcRoot<mirror::Object>*>(end_boundary);
          for (; root < last; root++) {
            VisitRootIfNonNull(root->AddressWithoutBarrier());
          }
        }
        break;
      case LinearAllocKind::kArtMethodArray:
        {
          LengthPrefixedArray<ArtMethod>* array = static_cast<LengthPrefixedArray<ArtMethod>*>(obj);
          // Old methods are clobbered in debug builds. Check size to confirm if the array
          // has any GC roots to visit. See ClassLinker::LinkMethodsHelper::ClobberOldMethods()
          if (array->size() > 0) {
            if (collector_->pointer_size_ == PointerSize::k64) {
              ArtMethod::VisitArrayRoots<PointerSize::k64>(
                  *this, start_boundary, end_boundary, array);
            } else {
              DCHECK_EQ(collector_->pointer_size_, PointerSize::k32);
              ArtMethod::VisitArrayRoots<PointerSize::k32>(
                  *this, start_boundary, end_boundary, array);
            }
          }
        }
        break;
      case LinearAllocKind::kArtMethod:
        ArtMethod::VisitRoots(*this, start_boundary, end_boundary, static_cast<ArtMethod*>(obj));
        break;
      case LinearAllocKind::kArtFieldArray:
        ArtField::VisitArrayRoots(*this,
                                  start_boundary,
                                  end_boundary,
                                  static_cast<LengthPrefixedArray<ArtField>*>(obj));
        break;
      case LinearAllocKind::kDexCacheArray:
        {
          mirror::DexCachePair<mirror::Object>* first =
              reinterpret_cast<mirror::DexCachePair<mirror::Object>*>(start_boundary);
          mirror::DexCachePair<mirror::Object>* last =
              reinterpret_cast<mirror::DexCachePair<mirror::Object>*>(end_boundary);
          mirror::DexCache::VisitDexCachePairRoots(*this, first, last);
      }
    }
  }

  MarkCompact* const collector_;
  // Cache to speed up checking if GC-root is in moving space or not.
  uint8_t* const moving_space_begin_;
  uint8_t* const moving_space_end_;
  // Whether the last page was touched or not.
  bool last_page_touched_ = false;
};

void MarkCompact::UpdateClassTableClasses(Runtime* runtime, bool immune_class_table_only) {
  // If the process is debuggable then redefinition is allowed, which may mean
  // pre-zygote-fork class-tables may have pointer to class in moving-space.
  // So visit classes from class-sets that are not in linear-alloc arena-pool.
  if (UNLIKELY(runtime->IsJavaDebuggableAtInit())) {
    ClassLinker* linker = runtime->GetClassLinker();
    ClassLoaderRootsUpdater updater(this);
    GcVisitedArenaPool* pool = static_cast<GcVisitedArenaPool*>(runtime->GetLinearAllocArenaPool());
    auto cond = [this, pool, immune_class_table_only](ClassTable::ClassSet& set) -> bool {
      if (!set.empty()) {
        return immune_class_table_only ?
               immune_spaces_.ContainsObject(reinterpret_cast<mirror::Object*>(&*set.begin())) :
               !pool->Contains(reinterpret_cast<void*>(&*set.begin()));
      }
      return false;
    };
    linker->VisitClassTables([cond, &updater](ClassTable* table)
                                 REQUIRES_SHARED(Locks::mutator_lock_) {
                               table->VisitClassesIfConditionMet(cond, updater);
                             });
    ReaderMutexLock rmu(thread_running_gc_, *Locks::classlinker_classes_lock_);
    linker->GetBootClassTable()->VisitClassesIfConditionMet(cond, updater);
  }
}

void MarkCompact::CompactionPause() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  Runtime* runtime = Runtime::Current();
  non_moving_space_bitmap_ = non_moving_space_->GetLiveBitmap();
  if (kIsDebugBuild) {
    DCHECK_EQ(thread_running_gc_, Thread::Current());
    stack_low_addr_ = thread_running_gc_->GetStackEnd();
    stack_high_addr_ =
        reinterpret_cast<char*>(stack_low_addr_) + thread_running_gc_->GetStackSize();
  }
  {
    TimingLogger::ScopedTiming t2("(Paused)UpdateCompactionDataStructures", GetTimings());
    ReaderMutexLock rmu(thread_running_gc_, *Locks::heap_bitmap_lock_);
    // Refresh data-structures to catch-up on allocations that may have
    // happened since marking-phase pause.
    // There could be several TLABs that got allocated since marking pause. We
    // don't want to compact them and instead update the TLAB info in TLS and
    // let mutators continue to use the TLABs.
    // We need to set all the bits in live-words bitmap corresponding to allocated
    // objects. Also, we need to find the objects that are overlapping with
    // page-begin boundaries. Unlike objects allocated before
    // black_allocations_begin_, which can be identified via mark-bitmap, we can get
    // this info only via walking the space past black_allocations_begin_, which
    // involves fetching object size.
    // TODO: We can reduce the time spent on this in a pause by performing one
    // round of this concurrently prior to the pause.
    UpdateMovingSpaceBlackAllocations();
    // TODO: If we want to avoid this allocation in a pause then we will have to
    // allocate an array for the entire moving-space size, which can be made
    // part of info_map_.
    moving_pages_status_ = new Atomic<PageState>[moving_first_objs_count_ + black_page_count_];
    if (kIsDebugBuild) {
      size_t len = moving_first_objs_count_ + black_page_count_;
      for (size_t i = 0; i < len; i++) {
          CHECK_EQ(moving_pages_status_[i].load(std::memory_order_relaxed),
                   PageState::kUnprocessed);
      }
    }
    // Iterate over the allocation_stack_, for every object in the non-moving
    // space:
    // 1. Mark the object in live bitmap
    // 2. Erase the object from allocation stack
    // 3. In the corresponding page, if the first-object vector needs updating
    // then do so.
    UpdateNonMovingSpaceBlackAllocations();

    // This store is visible to mutator (or uffd worker threads) as the mutator
    // lock's unlock guarantees that.
    compacting_ = true;
    // Start updating roots and system weaks now.
    heap_->GetReferenceProcessor()->UpdateRoots(this);
  }
  {
    // TODO: Immune space updation has to happen either before or after
    // remapping pre-compact pages to from-space. And depending on when it's
    // done, we have to invoke VisitRefsForCompaction() with or without
    // read-barrier.
    TimingLogger::ScopedTiming t2("(Paused)UpdateImmuneSpaces", GetTimings());
    accounting::CardTable* const card_table = heap_->GetCardTable();
    for (auto& space : immune_spaces_.GetSpaces()) {
      DCHECK(space->IsImageSpace() || space->IsZygoteSpace());
      accounting::ContinuousSpaceBitmap* live_bitmap = space->GetLiveBitmap();
      accounting::ModUnionTable* table = heap_->FindModUnionTableFromSpace(space);
      // Having zygote-space indicates that the first zygote fork has taken
      // place and that the classes/dex-caches in immune-spaces may have allocations
      // (ArtMethod/ArtField arrays, dex-cache array, etc.) in the
      // non-userfaultfd visited private-anonymous mappings. Visit them here.
      ImmuneSpaceUpdateObjVisitor visitor(this);
      if (table != nullptr) {
        table->ProcessCards();
        table->VisitObjects(ImmuneSpaceUpdateObjVisitor::Callback, &visitor);
      } else {
        WriterMutexLock wmu(thread_running_gc_, *Locks::heap_bitmap_lock_);
        card_table->Scan<false>(
            live_bitmap,
            space->Begin(),
            space->Limit(),
            visitor,
            accounting::CardTable::kCardDirty - 1);
      }
    }
  }

  {
    TimingLogger::ScopedTiming t2("(Paused)UpdateRoots", GetTimings());
    runtime->VisitConcurrentRoots(this, kVisitRootFlagAllRoots);
    runtime->VisitNonThreadRoots(this);
    {
      ClassLinker* linker = runtime->GetClassLinker();
      ClassLoaderRootsUpdater updater(this);
      ReaderMutexLock rmu(thread_running_gc_, *Locks::classlinker_classes_lock_);
      linker->VisitClassLoaders(&updater);
      linker->GetBootClassTable()->VisitRoots(updater, /*skip_classes=*/true);
    }
    SweepSystemWeaks(thread_running_gc_, runtime, /*paused=*/true);

    bool has_zygote_space = heap_->HasZygoteSpace();
    GcVisitedArenaPool* arena_pool =
        static_cast<GcVisitedArenaPool*>(runtime->GetLinearAllocArenaPool());
    // Update immune/pre-zygote class-tables in case class redefinition took
    // place. pre-zygote class-tables that are not in immune spaces are updated
    // below if we are in fallback-mode or if there is no zygote space. So in
    // that case only visit class-tables that are there in immune-spaces.
    UpdateClassTableClasses(runtime, uffd_ == kFallbackMode || !has_zygote_space);

    // Acquire arena-pool's lock, which should be released after the pool is
    // userfaultfd registered. This is to ensure that no new arenas are
    // allocated and used in between. Since they will not be captured in
    // linear_alloc_arenas_ below, we will miss updating their pages. The same
    // reason also applies to new allocations within the existing arena which
    // may change last_byte.
    // Since we are in a STW pause, this shouldn't happen anyways, but holding
    // the lock confirms it.
    // TODO (b/305779657): Replace with ExclusiveTryLock() and assert that it
    // doesn't fail once it is available for ReaderWriterMutex.
    WriterMutexLock pool_wmu(thread_running_gc_, arena_pool->GetLock());

    // TODO: Find out why it's not sufficient to visit native roots of immune
    // spaces, and why all the pre-zygote fork arenas have to be linearly updated.
    // Is it possible that some native root starts getting pointed to by some object
    // in moving space after fork? Or are we missing a write-barrier somewhere
    // when a native root is updated?
    auto arena_visitor = [this](uint8_t* page_begin, uint8_t* first_obj, size_t page_size)
                             REQUIRES_SHARED(Locks::mutator_lock_) {
                           LinearAllocPageUpdater updater(this);
                           if (first_obj != nullptr) {
                             updater.MultiObjectArena(page_begin, first_obj);
                           } else {
                             updater.SingleObjectArena(page_begin, page_size);
                           }
                         };
    if (uffd_ == kFallbackMode || (!has_zygote_space && runtime->IsZygote())) {
      // Besides fallback-mode, visit linear-alloc space in the pause for zygote
      // processes prior to first fork (that's when zygote space gets created).
      if (kIsDebugBuild && IsValidFd(uffd_)) {
        // All arenas allocated so far are expected to be pre-zygote fork.
        arena_pool->ForEachAllocatedArena(
            [](const TrackedArena& arena)
                REQUIRES_SHARED(Locks::mutator_lock_) { CHECK(arena.IsPreZygoteForkArena()); });
      }
      arena_pool->VisitRoots(arena_visitor);
    } else {
      // Inform the arena-pool that compaction is going on. So the TrackedArena
      // objects corresponding to the arenas that are freed shouldn't be deleted
      // immediately. We will do that in FinishPhase(). This is to avoid ABA
      // problem.
      arena_pool->DeferArenaFreeing();
      arena_pool->ForEachAllocatedArena(
          [this, arena_visitor, has_zygote_space](const TrackedArena& arena)
              REQUIRES_SHARED(Locks::mutator_lock_) {
            // The pre-zygote fork arenas are not visited concurrently in the
            // zygote children processes. The native roots of the dirty objects
            // are visited during immune space visit below.
            if (!arena.IsPreZygoteForkArena()) {
              uint8_t* last_byte = arena.GetLastUsedByte();
              auto ret = linear_alloc_arenas_.insert({&arena, last_byte});
              CHECK(ret.second);
            } else if (!arena.IsSingleObjectArena() || !has_zygote_space) {
              // Pre-zygote class-table and intern-table don't need to be updated.
              // TODO: Explore the possibility of using /proc/self/pagemap to
              // fetch which pages in these arenas are private-dirty and then only
              // visit those pages. To optimize it further, we can keep all
              // pre-zygote arenas in a single memory range so that just one read
              // from pagemap is sufficient.
              arena.VisitRoots(arena_visitor);
            }
          });
    }
    if (use_uffd_sigbus_) {
      // Release order wrt to mutator threads' SIGBUS handler load.
      sigbus_in_progress_count_.store(0, std::memory_order_release);
    }
    KernelPreparation();
  }

  UpdateNonMovingSpace();
  // fallback mode
  if (uffd_ == kFallbackMode) {
    CompactMovingSpace<kFallbackMode>(nullptr);

    int32_t freed_bytes = black_objs_slide_diff_;
    bump_pointer_space_->RecordFree(freed_objects_, freed_bytes);
    RecordFree(ObjectBytePair(freed_objects_, freed_bytes));
  } else {
    DCHECK_EQ(compaction_in_progress_count_.load(std::memory_order_relaxed), 0u);
    DCHECK_EQ(compaction_buffer_counter_.load(std::memory_order_relaxed), 1);
    if (!use_uffd_sigbus_) {
      // We must start worker threads before resuming mutators to avoid deadlocks.
      heap_->GetThreadPool()->StartWorkers(thread_running_gc_);
    }
  }
  stack_low_addr_ = nullptr;
}

void MarkCompact::KernelPrepareRangeForUffd(uint8_t* to_addr,
                                            uint8_t* from_addr,
                                            size_t map_size,
                                            int fd,
                                            uint8_t* shadow_addr) {
  int mremap_flags = MREMAP_MAYMOVE | MREMAP_FIXED;
  if (gHaveMremapDontunmap) {
    mremap_flags |= MREMAP_DONTUNMAP;
  }

  void* ret = mremap(to_addr, map_size, map_size, mremap_flags, from_addr);
  CHECK_EQ(ret, static_cast<void*>(from_addr))
      << "mremap to move pages failed: " << strerror(errno)
      << ". space-addr=" << reinterpret_cast<void*>(to_addr) << " size=" << PrettySize(map_size);

  if (shadow_addr != nullptr) {
    DCHECK_EQ(fd, kFdUnused);
    DCHECK(gHaveMremapDontunmap);
    ret = mremap(shadow_addr, map_size, map_size, mremap_flags, to_addr);
    CHECK_EQ(ret, static_cast<void*>(to_addr))
        << "mremap from shadow to to-space map failed: " << strerror(errno);
  } else if (!gHaveMremapDontunmap || fd > kFdUnused) {
    // Without MREMAP_DONTUNMAP the source mapping is unmapped by mremap. So mmap
    // the moving space again.
    int mmap_flags = MAP_FIXED;
    if (fd == kFdUnused) {
      // Use MAP_FIXED_NOREPLACE so that if someone else reserves 'to_addr'
      // mapping in meantime, which can happen when MREMAP_DONTUNMAP isn't
      // available, to avoid unmapping someone else' mapping and then causing
      // crashes elsewhere.
      mmap_flags = MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE;
      // On some platforms MAP_ANONYMOUS expects fd to be -1.
      fd = -1;
    } else if (IsValidFd(fd)) {
      mmap_flags |= MAP_SHARED;
    } else {
      DCHECK_EQ(fd, kFdSharedAnon);
      mmap_flags |= MAP_SHARED | MAP_ANONYMOUS;
    }
    ret = mmap(to_addr, map_size, PROT_READ | PROT_WRITE, mmap_flags, fd, 0);
    CHECK_EQ(ret, static_cast<void*>(to_addr))
        << "mmap for moving space failed: " << strerror(errno);
  }
}

void MarkCompact::KernelPreparation() {
  TimingLogger::ScopedTiming t("(Paused)KernelPreparation", GetTimings());
  uint8_t* moving_space_begin = bump_pointer_space_->Begin();
  size_t moving_space_size = bump_pointer_space_->Capacity();
  int mode = kCopyMode;
  size_t moving_space_register_sz;
  if (minor_fault_initialized_) {
    moving_space_register_sz = (moving_first_objs_count_ + black_page_count_) * gPageSize;
    if (shadow_to_space_map_.IsValid()) {
      size_t shadow_size = shadow_to_space_map_.Size();
      void* addr = shadow_to_space_map_.Begin();
      if (shadow_size < moving_space_register_sz) {
        addr = mremap(addr,
                      shadow_size,
                      moving_space_register_sz,
                      // Don't allow moving with obj-ptr poisoning as the
                      // mapping needs to be in <4GB address space.
                      kObjPtrPoisoning ? 0 : MREMAP_MAYMOVE,
                      /*new_address=*/nullptr);
        if (addr != MAP_FAILED) {
          // Succeeded in expanding the mapping. Update the MemMap entry for shadow map.
          MemMap temp = MemMap::MapPlaceholder(
              "moving-space-shadow", static_cast<uint8_t*>(addr), moving_space_register_sz);
          std::swap(shadow_to_space_map_, temp);
        }
      }
      if (addr != MAP_FAILED) {
        mode = kMinorFaultMode;
      } else {
        // We are not going to use shadow map. So protect it to catch any
        // potential bugs.
        DCHECK_EQ(mprotect(shadow_to_space_map_.Begin(), shadow_to_space_map_.Size(), PROT_NONE), 0)
            << "mprotect failed: " << strerror(errno);
      }
    }
  } else {
    moving_space_register_sz = moving_space_size;
  }

  bool map_shared =
      minor_fault_initialized_ || (!Runtime::Current()->IsZygote() && uffd_minor_fault_supported_);
  uint8_t* shadow_addr = nullptr;
  if (moving_to_space_fd_ == kFdUnused && map_shared) {
    DCHECK(gHaveMremapDontunmap);
    DCHECK(shadow_to_space_map_.IsValid());
    DCHECK_EQ(shadow_to_space_map_.Size(), moving_space_size);
    shadow_addr = shadow_to_space_map_.Begin();
  }

  KernelPrepareRangeForUffd(moving_space_begin,
                            from_space_begin_,
                            moving_space_size,
                            moving_to_space_fd_,
                            shadow_addr);

  if (IsValidFd(uffd_)) {
    // Register the moving space with userfaultfd.
    RegisterUffd(moving_space_begin, moving_space_register_sz, mode);
    // Prepare linear-alloc for concurrent compaction.
    for (auto& data : linear_alloc_spaces_data_) {
      bool mmap_again = map_shared && !data.already_shared_;
      DCHECK_EQ(static_cast<ssize_t>(data.shadow_.Size()), data.end_ - data.begin_);
      // There could be threads running in suspended mode when the compaction
      // pause is being executed. In order to make the userfaultfd setup atomic,
      // the registration has to be done *before* moving the pages to shadow map.
      if (!mmap_again) {
        // See the comment in the constructor as to why it's conditionally done.
        RegisterUffd(data.begin_,
                     data.shadow_.Size(),
                     minor_fault_initialized_ ? kMinorFaultMode : kCopyMode);
      }
      KernelPrepareRangeForUffd(data.begin_,
                                data.shadow_.Begin(),
                                data.shadow_.Size(),
                                mmap_again ? kFdSharedAnon : kFdUnused);
      if (mmap_again) {
        data.already_shared_ = true;
        RegisterUffd(data.begin_,
                     data.shadow_.Size(),
                     minor_fault_initialized_ ? kMinorFaultMode : kCopyMode);
      }
    }
  }
  if (map_shared) {
    // Start mapping linear-alloc MAP_SHARED only after the compaction pause of
    // the first GC in non-zygote processes. This is the GC which sets up
    // mappings for using minor-fault in future. Up to this point we run
    // userfaultfd in copy-mode, which requires the mappings (of linear-alloc)
    // to be MAP_PRIVATE.
    map_linear_alloc_shared_ = true;
  }
}

template <int kMode>
void MarkCompact::ConcurrentCompaction(uint8_t* buf) {
  DCHECK_NE(kMode, kFallbackMode);
  DCHECK(kMode != kCopyMode || buf != nullptr);
  size_t nr_moving_space_used_pages = moving_first_objs_count_ + black_page_count_;
  while (true) {
    struct uffd_msg msg;
    ssize_t nread = read(uffd_, &msg, sizeof(msg));
    CHECK_GT(nread, 0);
    CHECK_EQ(msg.event, UFFD_EVENT_PAGEFAULT);
    DCHECK_EQ(nread, static_cast<ssize_t>(sizeof(msg)));
    uint8_t* fault_addr = reinterpret_cast<uint8_t*>(msg.arg.pagefault.address);
    if (fault_addr == conc_compaction_termination_page_) {
      // The counter doesn't need to be updated atomically as only one thread
      // would wake up against the gc-thread's load to this fault_addr. In fact,
      // the other threads would wake up serially because every exiting thread
      // will wake up gc-thread, which would retry load but again would find the
      // page missing. Also, the value will be flushed to caches due to the ioctl
      // syscall below.
      uint8_t ret = thread_pool_counter_--;
      // If 'gKernelHasFaultRetry == true' then only the last thread should map the
      // zeropage so that the gc-thread can proceed. Otherwise, each thread does
      // it and the gc-thread will repeat this fault until thread_pool_counter == 0.
      if (!gKernelHasFaultRetry || ret == 1) {
        ZeropageIoctl(fault_addr, /*tolerate_eexist=*/false, /*tolerate_enoent=*/false);
      } else {
        struct uffdio_range uffd_range;
        uffd_range.start = msg.arg.pagefault.address;
        uffd_range.len = gPageSize;
        CHECK_EQ(ioctl(uffd_, UFFDIO_WAKE, &uffd_range), 0)
            << "ioctl_userfaultfd: wake failed for concurrent-compaction termination page: "
            << strerror(errno);
      }
      break;
    }
    uint8_t* fault_page = AlignDown(fault_addr, gPageSize);
    if (HasAddress(reinterpret_cast<mirror::Object*>(fault_addr))) {
      ConcurrentlyProcessMovingPage<kMode>(fault_page, buf, nr_moving_space_used_pages);
    } else if (minor_fault_initialized_) {
      ConcurrentlyProcessLinearAllocPage<kMinorFaultMode>(
          fault_page, (msg.arg.pagefault.flags & UFFD_PAGEFAULT_FLAG_MINOR) != 0);
    } else {
      ConcurrentlyProcessLinearAllocPage<kCopyMode>(
          fault_page, (msg.arg.pagefault.flags & UFFD_PAGEFAULT_FLAG_MINOR) != 0);
    }
  }
}

bool MarkCompact::SigbusHandler(siginfo_t* info) {
  class ScopedInProgressCount {
   public:
    explicit ScopedInProgressCount(MarkCompact* collector) : collector_(collector) {
      // Increment the count only if compaction is not done yet.
      SigbusCounterType prev =
          collector_->sigbus_in_progress_count_.load(std::memory_order_relaxed);
      while ((prev & kSigbusCounterCompactionDoneMask) == 0) {
        if (collector_->sigbus_in_progress_count_.compare_exchange_strong(
                prev, prev + 1, std::memory_order_acquire)) {
          DCHECK_LT(prev, kSigbusCounterCompactionDoneMask - 1);
          compaction_done_ = false;
          return;
        }
      }
      compaction_done_ = true;
    }

    bool IsCompactionDone() const {
      return compaction_done_;
    }

    ~ScopedInProgressCount() {
      if (!IsCompactionDone()) {
        collector_->sigbus_in_progress_count_.fetch_sub(1, std::memory_order_release);
      }
    }

   private:
    MarkCompact* const collector_;
    bool compaction_done_;
  };

  DCHECK(use_uffd_sigbus_);
  if (info->si_code != BUS_ADRERR) {
    // Userfaultfd raises SIGBUS with BUS_ADRERR. All other causes can't be
    // handled here.
    return false;
  }

  ScopedInProgressCount spc(this);
  uint8_t* fault_page = AlignDown(reinterpret_cast<uint8_t*>(info->si_addr), gPageSize);
  if (!spc.IsCompactionDone()) {
    if (HasAddress(reinterpret_cast<mirror::Object*>(fault_page))) {
      Thread* self = Thread::Current();
      Locks::mutator_lock_->AssertSharedHeld(self);
      size_t nr_moving_space_used_pages = moving_first_objs_count_ + black_page_count_;
      if (minor_fault_initialized_) {
        ConcurrentlyProcessMovingPage<kMinorFaultMode>(
            fault_page, nullptr, nr_moving_space_used_pages);
      } else {
        ConcurrentlyProcessMovingPage<kCopyMode>(
            fault_page, self->GetThreadLocalGcBuffer(), nr_moving_space_used_pages);
      }
      return true;
    } else {
      // Find the linear-alloc space containing fault-addr
      for (auto& data : linear_alloc_spaces_data_) {
        if (data.begin_ <= fault_page && data.end_ > fault_page) {
          if (minor_fault_initialized_) {
            ConcurrentlyProcessLinearAllocPage<kMinorFaultMode>(fault_page, false);
          } else {
            ConcurrentlyProcessLinearAllocPage<kCopyMode>(fault_page, false);
          }
          return true;
        }
      }
      // Fault address doesn't belong to either moving-space or linear-alloc.
      return false;
    }
  } else {
    // We may spuriously get SIGBUS fault, which was initiated before the
    // compaction was finished, but ends up here. In that case, if the fault
    // address is valid then consider it handled.
    return HasAddress(reinterpret_cast<mirror::Object*>(fault_page)) ||
           linear_alloc_spaces_data_.end() !=
               std::find_if(linear_alloc_spaces_data_.begin(),
                            linear_alloc_spaces_data_.end(),
                            [fault_page](const LinearAllocSpaceData& data) {
                              return data.begin_ <= fault_page && data.end_ > fault_page;
                            });
  }
}

static void BackOff(uint32_t i) {
  static constexpr uint32_t kYieldMax = 5;
  // TODO: Consider adding x86 PAUSE and/or ARM YIELD here.
  if (i <= kYieldMax) {
    sched_yield();
  } else {
    // nanosleep is not in the async-signal-safe list, but bionic implements it
    // with a pure system call, so it should be fine.
    NanoSleep(10000ull * (i - kYieldMax));
  }
}

template <int kMode>
void MarkCompact::ConcurrentlyProcessMovingPage(uint8_t* fault_page,
                                                uint8_t* buf,
                                                size_t nr_moving_space_used_pages) {
  class ScopedInProgressCount {
   public:
    explicit ScopedInProgressCount(MarkCompact* collector) : collector_(collector) {
      collector_->compaction_in_progress_count_.fetch_add(1, std::memory_order_relaxed);
    }

    ~ScopedInProgressCount() {
      collector_->compaction_in_progress_count_.fetch_sub(1, std::memory_order_relaxed);
    }

   private:
    MarkCompact* collector_;
  };

  uint8_t* unused_space_begin =
      bump_pointer_space_->Begin() + nr_moving_space_used_pages * gPageSize;
  DCHECK(IsAlignedParam(unused_space_begin, gPageSize));
  DCHECK(kMode == kCopyMode || fault_page < unused_space_begin);
  if (kMode == kCopyMode && fault_page >= unused_space_begin) {
    // There is a race which allows more than one thread to install a
    // zero-page. But we can tolerate that. So absorb the EEXIST returned by
    // the ioctl and move on.
    ZeropageIoctl(fault_page, /*tolerate_eexist=*/true, /*tolerate_enoent=*/true);
    return;
  }
  size_t page_idx = DivideByPageSize(fault_page - bump_pointer_space_->Begin());
  DCHECK_LT(page_idx, moving_first_objs_count_ + black_page_count_);
  mirror::Object* first_obj = first_objs_moving_space_[page_idx].AsMirrorPtr();
  if (first_obj == nullptr) {
    // We should never have a case where two workers are trying to install a
    // zeropage in this range as we synchronize using moving_pages_status_[page_idx].
    PageState expected_state = PageState::kUnprocessed;
    if (moving_pages_status_[page_idx].compare_exchange_strong(
            expected_state, PageState::kProcessedAndMapping, std::memory_order_relaxed)) {
      // Note: ioctl acts as an acquire fence.
      ZeropageIoctl(fault_page, /*tolerate_eexist=*/false, /*tolerate_enoent=*/true);
    } else {
      DCHECK_EQ(expected_state, PageState::kProcessedAndMapping);
    }
    return;
  }

  PageState state = moving_pages_status_[page_idx].load(
      use_uffd_sigbus_ ? std::memory_order_acquire : std::memory_order_relaxed);
  uint32_t backoff_count = 0;
  while (true) {
    switch (state) {
      case PageState::kUnprocessed: {
        // The increment to the in-progress counter must be done before updating
        // the page's state. Otherwise, we will end up leaving a window wherein
        // the GC-thread could observe that no worker is working on compaction
        // and could end up unregistering the moving space from userfaultfd.
        ScopedInProgressCount spc(this);
        // Acquire order to ensure we don't start writing to shadow map, which is
        // shared, before the CAS is successful. Release order to ensure that the
        // increment to moving_compactions_in_progress above is not re-ordered
        // after the CAS.
        if (moving_pages_status_[page_idx].compare_exchange_strong(
                state, PageState::kMutatorProcessing, std::memory_order_acq_rel)) {
          if (kMode == kMinorFaultMode) {
            DCHECK_EQ(buf, nullptr);
            buf = shadow_to_space_map_.Begin() + page_idx * gPageSize;
          } else if (UNLIKELY(buf == nullptr)) {
            DCHECK_EQ(kMode, kCopyMode);
            uint16_t idx = compaction_buffer_counter_.fetch_add(1, std::memory_order_relaxed);
            // The buffer-map is one page bigger as the first buffer is used by GC-thread.
            CHECK_LE(idx, kMutatorCompactionBufferCount);
            buf = compaction_buffers_map_.Begin() + idx * gPageSize;
            DCHECK(compaction_buffers_map_.HasAddress(buf));
            Thread::Current()->SetThreadLocalGcBuffer(buf);
          }

          if (fault_page < post_compact_end_) {
            // The page has to be compacted.
            CompactPage(
                first_obj, pre_compact_offset_moving_space_[page_idx], buf, kMode == kCopyMode);
          } else {
            DCHECK_NE(first_obj, nullptr);
            DCHECK_GT(pre_compact_offset_moving_space_[page_idx], 0u);
            uint8_t* pre_compact_page = black_allocations_begin_ + (fault_page - post_compact_end_);
            uint32_t first_chunk_size = black_alloc_pages_first_chunk_size_[page_idx];
            mirror::Object* next_page_first_obj = nullptr;
            if (page_idx + 1 < moving_first_objs_count_ + black_page_count_) {
              next_page_first_obj = first_objs_moving_space_[page_idx + 1].AsMirrorPtr();
            }
            DCHECK(IsAlignedParam(pre_compact_page, gPageSize));
            SlideBlackPage(first_obj,
                           next_page_first_obj,
                           first_chunk_size,
                           pre_compact_page,
                           buf,
                           kMode == kCopyMode);
          }
          // Nobody else would simultaneously modify this page's state so an
          // atomic store is sufficient. Use 'release' order to guarantee that
          // loads/stores to the page are finished before this store.
          moving_pages_status_[page_idx].store(PageState::kProcessedAndMapping,
                                               std::memory_order_release);
          if (kMode == kCopyMode) {
            CopyIoctl(fault_page, buf);
            if (use_uffd_sigbus_) {
              // Store is sufficient as no other thread modifies the status at this stage.
              moving_pages_status_[page_idx].store(PageState::kProcessedAndMapped,
                                                   std::memory_order_release);
            }
            return;
          } else {
            break;
          }
        }
      }
        continue;
      case PageState::kProcessing:
        DCHECK_EQ(kMode, kMinorFaultMode);
        if (moving_pages_status_[page_idx].compare_exchange_strong(
                state, PageState::kProcessingAndMapping, std::memory_order_relaxed) &&
            !use_uffd_sigbus_) {
          // Somebody else took or will take care of finishing the compaction and
          // then mapping the page.
          return;
        }
        continue;
      case PageState::kProcessed:
        // The page is processed but not mapped. We should map it.
        break;
      case PageState::kProcessingAndMapping:
      case PageState::kMutatorProcessing:
      case PageState::kProcessedAndMapping:
        if (use_uffd_sigbus_) {
          // Wait for the page to be mapped before returning.
          BackOff(backoff_count++);
          state = moving_pages_status_[page_idx].load(std::memory_order_acquire);
          continue;
        }
        return;
      case PageState::kProcessedAndMapped:
        // Somebody else took care of the page.
        return;
    }
    break;
  }

  DCHECK_EQ(kMode, kMinorFaultMode);
  if (state == PageState::kUnprocessed) {
    MapProcessedPages</*kFirstPageMapping=*/true>(
        fault_page, moving_pages_status_, page_idx, nr_moving_space_used_pages);
  } else {
    DCHECK_EQ(state, PageState::kProcessed);
    MapProcessedPages</*kFirstPageMapping=*/false>(
        fault_page, moving_pages_status_, page_idx, nr_moving_space_used_pages);
  }
}

void MarkCompact::MapUpdatedLinearAllocPage(uint8_t* page,
                                            uint8_t* shadow_page,
                                            Atomic<PageState>& state,
                                            bool page_touched) {
  DCHECK(!minor_fault_initialized_);
  if (page_touched) {
    CopyIoctl(page, shadow_page);
  } else {
    // If the page wasn't touched, then it means it is empty and
    // is most likely not present on the shadow-side. Furthermore,
    // since the shadow is also userfaultfd registered doing copy
    // ioctl fail as the copy-from-user in the kernel will cause
    // userfault. Instead, just map a zeropage, which is not only
    // correct but also efficient as it avoids unnecessary memcpy
    // in the kernel.
    ZeropageIoctl(page, /*tolerate_eexist=*/false, /*tolerate_enoent=*/false);
  }
  if (use_uffd_sigbus_) {
    // Store is sufficient as no other thread can modify the
    // status of this page at this point.
    state.store(PageState::kProcessedAndMapped, std::memory_order_release);
  }
}

template <int kMode>
void MarkCompact::ConcurrentlyProcessLinearAllocPage(uint8_t* fault_page, bool is_minor_fault) {
  DCHECK(!is_minor_fault || kMode == kMinorFaultMode);
  auto arena_iter = linear_alloc_arenas_.end();
  {
    TrackedArena temp_arena(fault_page);
    arena_iter = linear_alloc_arenas_.upper_bound(&temp_arena);
    arena_iter = arena_iter != linear_alloc_arenas_.begin() ? std::prev(arena_iter)
                                                            : linear_alloc_arenas_.end();
  }
  // Unlike ProcessLinearAlloc(), we don't need to hold arena-pool's lock here
  // because a thread trying to access the page and as a result causing this
  // userfault confirms that nobody can delete the corresponding arena and
  // release its pages.
  // NOTE: We may have some memory range be recycled several times during a
  // compaction cycle, thereby potentially causing userfault on the same page
  // several times. That's not a problem as all of them (except for possibly the
  // first one) would require us mapping a zero-page, which we do without updating
  // the 'state_arr'.
  if (arena_iter == linear_alloc_arenas_.end() ||
      arena_iter->first->IsWaitingForDeletion() ||
      arena_iter->second <= fault_page) {
    // Fault page isn't in any of the arenas that existed before we started
    // compaction. So map zeropage and return.
    ZeropageIoctl(fault_page, /*tolerate_eexist=*/true, /*tolerate_enoent=*/false);
  } else {
    // Find the linear-alloc space containing fault-page
    LinearAllocSpaceData* space_data = nullptr;
    for (auto& data : linear_alloc_spaces_data_) {
      if (data.begin_ <= fault_page && fault_page < data.end_) {
        space_data = &data;
        break;
      }
    }
    DCHECK_NE(space_data, nullptr);
    ptrdiff_t diff = space_data->shadow_.Begin() - space_data->begin_;
    size_t page_idx = DivideByPageSize(fault_page - space_data->begin_);
    Atomic<PageState>* state_arr =
        reinterpret_cast<Atomic<PageState>*>(space_data->page_status_map_.Begin());
    PageState state = state_arr[page_idx].load(use_uffd_sigbus_ ? std::memory_order_acquire :
                                                                  std::memory_order_relaxed);
    uint32_t backoff_count = 0;
    while (true) {
      switch (state) {
        case PageState::kUnprocessed: {
          // Acquire order to ensure we don't start writing to shadow map, which is
          // shared, before the CAS is successful.
          if (state_arr[page_idx].compare_exchange_strong(
                  state, PageState::kProcessingAndMapping, std::memory_order_acquire)) {
            if (kMode == kCopyMode || is_minor_fault) {
              LinearAllocPageUpdater updater(this);
              uint8_t* first_obj = arena_iter->first->GetFirstObject(fault_page);
              // null first_obj indicates that it's a page from arena for
              // intern-table/class-table. So first object isn't required.
              if (first_obj != nullptr) {
                updater.MultiObjectArena(fault_page + diff, first_obj + diff);
              } else {
                updater.SingleObjectArena(fault_page + diff, gPageSize);
              }
              if (kMode == kCopyMode) {
                MapUpdatedLinearAllocPage(fault_page,
                                          fault_page + diff,
                                          state_arr[page_idx],
                                          updater.WasLastPageTouched());
                return;
              }
            } else {
              // Don't touch the page in this case (there is no reason to do so
              // anyways) as it would mean reading from first_obj, which could be on
              // another missing page and hence may cause this thread to block, leading
              // to deadlocks.
              // Force read the page if it is missing so that a zeropage gets mapped on
              // the shadow map and then CONTINUE ioctl will map it on linear-alloc.
              ForceRead(fault_page + diff);
            }
            MapProcessedPages</*kFirstPageMapping=*/true>(
                fault_page, state_arr, page_idx, space_data->page_status_map_.Size());
            return;
          }
        }
          continue;
        case PageState::kProcessing:
          DCHECK_EQ(kMode, kMinorFaultMode);
          if (state_arr[page_idx].compare_exchange_strong(
                  state, PageState::kProcessingAndMapping, std::memory_order_relaxed) &&
              !use_uffd_sigbus_) {
            // Somebody else took or will take care of finishing the updates and
            // then mapping the page.
            return;
          }
          continue;
        case PageState::kProcessed:
          // The page is processed but not mapped. We should map it.
          break;
        case PageState::kMutatorProcessing:
          UNREACHABLE();
        case PageState::kProcessingAndMapping:
        case PageState::kProcessedAndMapping:
          if (use_uffd_sigbus_) {
            // Wait for the page to be mapped before returning.
            BackOff(backoff_count++);
            state = state_arr[page_idx].load(std::memory_order_acquire);
            continue;
          }
          return;
        case PageState::kProcessedAndMapped:
          // Somebody else took care of the page.
          return;
      }
      break;
    }

    DCHECK_EQ(kMode, kMinorFaultMode);
    DCHECK_EQ(state, PageState::kProcessed);
    if (!is_minor_fault) {
      // Force read the page if it is missing so that a zeropage gets mapped on
      // the shadow map and then CONTINUE ioctl will map it on linear-alloc.
      ForceRead(fault_page + diff);
    }
    MapProcessedPages</*kFirstPageMapping=*/false>(
        fault_page, state_arr, page_idx, space_data->page_status_map_.Size());
  }
}

void MarkCompact::ProcessLinearAlloc() {
  GcVisitedArenaPool* arena_pool =
      static_cast<GcVisitedArenaPool*>(Runtime::Current()->GetLinearAllocArenaPool());
  DCHECK_EQ(thread_running_gc_, Thread::Current());
  for (auto& pair : linear_alloc_arenas_) {
    const TrackedArena* arena = pair.first;
    size_t arena_size;
    uint8_t* arena_begin;
    ptrdiff_t diff;
    bool others_processing;
    {
      // Acquire arena-pool's lock (in shared-mode) so that the arena being updated
      // does not get deleted at the same time. If this critical section is too
      // long and impacts mutator response time, then we get rid of this lock by
      // holding onto memory ranges of all deleted (since compaction pause)
      // arenas until completion finishes.
      ReaderMutexLock rmu(thread_running_gc_, arena_pool->GetLock());
      // If any arenas were freed since compaction pause then skip them from
      // visiting.
      if (arena->IsWaitingForDeletion()) {
        continue;
      }
      uint8_t* last_byte = pair.second;
      DCHECK_ALIGNED_PARAM(last_byte, gPageSize);
      others_processing = false;
      arena_begin = arena->Begin();
      arena_size = arena->Size();
      // Find the linear-alloc space containing the arena
      LinearAllocSpaceData* space_data = nullptr;
      for (auto& data : linear_alloc_spaces_data_) {
        if (data.begin_ <= arena_begin && arena_begin < data.end_) {
          space_data = &data;
          break;
        }
      }
      CHECK_NE(space_data, nullptr);
      diff = space_data->shadow_.Begin() - space_data->begin_;
      auto visitor = [space_data, last_byte, diff, this, &others_processing](
                         uint8_t* page_begin,
                         uint8_t* first_obj,
                         size_t page_size) REQUIRES_SHARED(Locks::mutator_lock_) {
        // No need to process pages past last_byte as they already have updated
        // gc-roots, if any.
        if (page_begin >= last_byte) {
          return;
        }
        LinearAllocPageUpdater updater(this);
        size_t page_idx = DivideByPageSize(page_begin - space_data->begin_);
        DCHECK_LT(page_idx, space_data->page_status_map_.Size());
        Atomic<PageState>* state_arr =
            reinterpret_cast<Atomic<PageState>*>(space_data->page_status_map_.Begin());
        PageState expected_state = PageState::kUnprocessed;
        PageState desired_state =
            minor_fault_initialized_ ? PageState::kProcessing : PageState::kProcessingAndMapping;
        // Acquire order to ensure that we don't start accessing the shadow page,
        // which is shared with other threads, prior to CAS. Also, for same
        // reason, we used 'release' order for changing the state to 'processed'.
        if (state_arr[page_idx].compare_exchange_strong(
                expected_state, desired_state, std::memory_order_acquire)) {
          // null first_obj indicates that it's a page from arena for
          // intern-table/class-table. So first object isn't required.
          if (first_obj != nullptr) {
            updater.MultiObjectArena(page_begin + diff, first_obj + diff);
          } else {
            DCHECK_EQ(page_size, gPageSize);
            updater.SingleObjectArena(page_begin + diff, page_size);
          }
          expected_state = PageState::kProcessing;
          if (!minor_fault_initialized_) {
            MapUpdatedLinearAllocPage(
                page_begin, page_begin + diff, state_arr[page_idx], updater.WasLastPageTouched());
          } else if (!state_arr[page_idx].compare_exchange_strong(
                         expected_state, PageState::kProcessed, std::memory_order_release)) {
            DCHECK_EQ(expected_state, PageState::kProcessingAndMapping);
            // Force read in case the page was missing and updater didn't touch it
            // as there was nothing to do. This will ensure that a zeropage is
            // faulted on the shadow map.
            ForceRead(page_begin + diff);
            MapProcessedPages</*kFirstPageMapping=*/true>(
                page_begin, state_arr, page_idx, space_data->page_status_map_.Size());
          }
        } else {
          others_processing = true;
        }
      };

      arena->VisitRoots(visitor);
    }
    // If we are not in minor-fault mode and if no other thread was found to be
    // processing any pages in this arena, then we can madvise the shadow size.
    // Otherwise, we will double the memory use for linear-alloc.
    if (!minor_fault_initialized_ && !others_processing) {
      ZeroAndReleaseMemory(arena_begin + diff, arena_size);
    }
  }
}

void MarkCompact::RegisterUffd(void* addr, size_t size, int mode) {
  DCHECK(IsValidFd(uffd_));
  struct uffdio_register uffd_register;
  uffd_register.range.start = reinterpret_cast<uintptr_t>(addr);
  uffd_register.range.len = size;
  uffd_register.mode = UFFDIO_REGISTER_MODE_MISSING;
  if (mode == kMinorFaultMode) {
    uffd_register.mode |= UFFDIO_REGISTER_MODE_MINOR;
  }
  CHECK_EQ(ioctl(uffd_, UFFDIO_REGISTER, &uffd_register), 0)
      << "ioctl_userfaultfd: register failed: " << strerror(errno)
      << ". start:" << static_cast<void*>(addr) << " len:" << PrettySize(size);
}

void MarkCompact::UnregisterUffd(uint8_t* start, size_t len) {
  DCHECK(IsValidFd(uffd_));
  struct uffdio_range range;
  range.start = reinterpret_cast<uintptr_t>(start);
  range.len = len;
  CHECK_EQ(ioctl(uffd_, UFFDIO_UNREGISTER, &range), 0)
      << "ioctl_userfaultfd: unregister failed: " << strerror(errno)
      << ". addr:" << static_cast<void*>(start) << " len:" << PrettySize(len);
  // Due to an oversight in the kernel implementation of 'unregister', the
  // waiting threads are woken up only for copy uffds. Therefore, for now, we
  // have to explicitly wake up the threads in minor-fault case.
  // TODO: The fix in the kernel is being worked on. Once the kernel version
  // containing the fix is known, make it conditional on that as well.
  if (minor_fault_initialized_) {
    CHECK_EQ(ioctl(uffd_, UFFDIO_WAKE, &range), 0)
        << "ioctl_userfaultfd: wake failed: " << strerror(errno)
        << ". addr:" << static_cast<void*>(start) << " len:" << PrettySize(len);
  }
}

void MarkCompact::CompactionPhase() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  {
    int32_t freed_bytes = black_objs_slide_diff_;
    bump_pointer_space_->RecordFree(freed_objects_, freed_bytes);
    RecordFree(ObjectBytePair(freed_objects_, freed_bytes));
  }

  size_t moving_space_size = bump_pointer_space_->Capacity();
  size_t used_size = (moving_first_objs_count_ + black_page_count_) * gPageSize;
  if (CanCompactMovingSpaceWithMinorFault()) {
    CompactMovingSpace<kMinorFaultMode>(/*page=*/nullptr);
  } else {
    if (used_size < moving_space_size) {
      // mremap clears 'anon_vma' field of anonymous mappings. If we
      // uffd-register only the used portion of the space, then the vma gets
      // split (between used and unused portions) and as soon as pages are
      // mapped to the vmas, they get different `anon_vma` assigned, which
      // ensures that the two vmas cannot merged after we uffd-unregister the
      // used portion. OTOH, registering the entire space avoids the split, but
      // unnecessarily causes userfaults on allocations.
      // By mapping a zero-page (below) we let the kernel assign an 'anon_vma'
      // *before* the vma-split caused by uffd-unregister of the unused portion
      // This ensures that when we unregister the used portion after compaction,
      // the two split vmas merge. This is necessary for the mremap of the
      // next GC cycle to not fail due to having more than one vmas in the source
      // range.
      uint8_t* unused_first_page = bump_pointer_space_->Begin() + used_size;
      // It's ok if somebody else already mapped the page.
      ZeropageIoctl(unused_first_page, /*tolerate_eexist*/ true, /*tolerate_enoent*/ false);
      UnregisterUffd(unused_first_page, moving_space_size - used_size);
    }
    CompactMovingSpace<kCopyMode>(compaction_buffers_map_.Begin());
  }

  // Make sure no mutator is reading from the from-space before unregistering
  // userfaultfd from moving-space and then zapping from-space. The mutator
  // and GC may race to set a page state to processing or further along. The two
  // attempts are ordered. If the collector wins, then the mutator will see that
  // and not access the from-space page. If the muator wins, then the
  // compaction_in_progress_count_ increment by the mutator happens-before the test
  // here, and we will not see a zero value until the mutator has completed.
  for (uint32_t i = 0; compaction_in_progress_count_.load(std::memory_order_acquire) > 0; i++) {
    BackOff(i);
  }
  if (used_size > 0) {
    UnregisterUffd(bump_pointer_space_->Begin(), used_size);
  }
  // Release all of the memory taken by moving-space's from-map
  if (minor_fault_initialized_) {
    if (IsValidFd(moving_from_space_fd_)) {
      // A strange behavior is observed wherein between GC cycles the from-space'
      // first page is accessed. But the memfd that is mapped on from-space, is
      // used on to-space in next GC cycle, causing issues with userfaultfd as the
      // page isn't missing. A possible reason for this could be prefetches. The
      // mprotect ensures that such accesses don't succeed.
      int ret = mprotect(from_space_begin_, moving_space_size, PROT_NONE);
      CHECK_EQ(ret, 0) << "mprotect(PROT_NONE) for from-space failed: " << strerror(errno);
      // madvise(MADV_REMOVE) needs PROT_WRITE. Use fallocate() instead, which
      // does the same thing.
      ret = fallocate(moving_from_space_fd_,
                      FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE,
                      /*offset=*/0,
                      moving_space_size);
      CHECK_EQ(ret, 0) << "fallocate for from-space failed: " << strerror(errno);
    } else {
      // We don't have a valid fd, so use madvise(MADV_REMOVE) instead. mprotect
      // is not required in this case as we create fresh
      // MAP_SHARED+MAP_ANONYMOUS mapping in each GC cycle.
      int ret = madvise(from_space_begin_, moving_space_size, MADV_REMOVE);
      CHECK_EQ(ret, 0) << "madvise(MADV_REMOVE) failed for from-space map:" << strerror(errno);
    }
  } else {
    from_space_map_.MadviseDontNeedAndZero();
  }
  // mprotect(PROT_NONE) all maps except to-space in debug-mode to catch any unexpected accesses.
  if (shadow_to_space_map_.IsValid()) {
    DCHECK_EQ(mprotect(shadow_to_space_map_.Begin(), shadow_to_space_map_.Size(), PROT_NONE), 0)
        << "mprotect(PROT_NONE) for shadow-map failed:" << strerror(errno);
  }
  if (!IsValidFd(moving_from_space_fd_)) {
    // The other case is already mprotected above.
    DCHECK_EQ(mprotect(from_space_begin_, moving_space_size, PROT_NONE), 0)
        << "mprotect(PROT_NONE) for from-space failed: " << strerror(errno);
  }

  ProcessLinearAlloc();

  if (use_uffd_sigbus_) {
    // Set compaction-done bit so that no new mutator threads start compaction
    // process in the SIGBUS handler.
    SigbusCounterType count = sigbus_in_progress_count_.fetch_or(kSigbusCounterCompactionDoneMask,
                                                                 std::memory_order_acq_rel);
    // Wait for SIGBUS handlers already in play.
    for (uint32_t i = 0; count > 0; i++) {
      BackOff(i);
      count = sigbus_in_progress_count_.load(std::memory_order_acquire);
      count &= ~kSigbusCounterCompactionDoneMask;
    }
  } else {
    DCHECK(IsAlignedParam(conc_compaction_termination_page_, gPageSize));
    // We will only iterate once if gKernelHasFaultRetry is true.
    do {
      // madvise the page so that we can get userfaults on it.
      ZeroAndReleaseMemory(conc_compaction_termination_page_, gPageSize);
      // The following load triggers 'special' userfaults. When received by the
      // thread-pool workers, they will exit out of the compaction task. This fault
      // happens because we madvised the page.
      ForceRead(conc_compaction_termination_page_);
    } while (thread_pool_counter_ > 0);
  }
  // Unregister linear-alloc spaces
  for (auto& data : linear_alloc_spaces_data_) {
    DCHECK_EQ(data.end_ - data.begin_, static_cast<ssize_t>(data.shadow_.Size()));
    UnregisterUffd(data.begin_, data.shadow_.Size());
    // madvise linear-allocs's page-status array
    data.page_status_map_.MadviseDontNeedAndZero();
    // Madvise the entire linear-alloc space's shadow. In copy-mode it gets rid
    // of the pages which are still mapped. In minor-fault mode this unmaps all
    // pages, which is good in reducing the mremap (done in STW pause) time in
    // next GC cycle.
    data.shadow_.MadviseDontNeedAndZero();
    if (minor_fault_initialized_) {
      DCHECK_EQ(mprotect(data.shadow_.Begin(), data.shadow_.Size(), PROT_NONE), 0)
          << "mprotect failed: " << strerror(errno);
    }
  }

  if (!use_uffd_sigbus_) {
    heap_->GetThreadPool()->StopWorkers(thread_running_gc_);
  }
}

template <size_t kBufferSize>
class MarkCompact::ThreadRootsVisitor : public RootVisitor {
 public:
  explicit ThreadRootsVisitor(MarkCompact* mark_compact, Thread* const self)
        : mark_compact_(mark_compact), self_(self) {}

  ~ThreadRootsVisitor() {
    Flush();
  }

  void VisitRoots(mirror::Object*** roots,
                  size_t count,
                  [[maybe_unused]] const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_) {
    for (size_t i = 0; i < count; i++) {
      mirror::Object* obj = *roots[i];
      if (mark_compact_->MarkObjectNonNullNoPush</*kParallel*/true>(obj)) {
        Push(obj);
      }
    }
  }

  void VisitRoots(mirror::CompressedReference<mirror::Object>** roots,
                  size_t count,
                  [[maybe_unused]] const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_) {
    for (size_t i = 0; i < count; i++) {
      mirror::Object* obj = roots[i]->AsMirrorPtr();
      if (mark_compact_->MarkObjectNonNullNoPush</*kParallel*/true>(obj)) {
        Push(obj);
      }
    }
  }

 private:
  void Flush() REQUIRES_SHARED(Locks::mutator_lock_)
               REQUIRES(Locks::heap_bitmap_lock_) {
    StackReference<mirror::Object>* start;
    StackReference<mirror::Object>* end;
    {
      MutexLock mu(self_, mark_compact_->lock_);
      // Loop here because even after expanding once it may not be sufficient to
      // accommodate all references. It's almost impossible, but there is no harm
      // in implementing it this way.
      while (!mark_compact_->mark_stack_->BumpBack(idx_, &start, &end)) {
        mark_compact_->ExpandMarkStack();
      }
    }
    while (idx_ > 0) {
      *start++ = roots_[--idx_];
    }
    DCHECK_EQ(start, end);
  }

  void Push(mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_)
                                 REQUIRES(Locks::heap_bitmap_lock_) {
    if (UNLIKELY(idx_ >= kBufferSize)) {
      Flush();
    }
    roots_[idx_++].Assign(obj);
  }

  StackReference<mirror::Object> roots_[kBufferSize];
  size_t idx_ = 0;
  MarkCompact* const mark_compact_;
  Thread* const self_;
};

class MarkCompact::CheckpointMarkThreadRoots : public Closure {
 public:
  explicit CheckpointMarkThreadRoots(MarkCompact* mark_compact) : mark_compact_(mark_compact) {}

  void Run(Thread* thread) override NO_THREAD_SAFETY_ANALYSIS {
    ScopedTrace trace("Marking thread roots");
    // Note: self is not necessarily equal to thread since thread may be
    // suspended.
    Thread* const self = Thread::Current();
    CHECK(thread == self
          || thread->IsSuspended()
          || thread->GetState() == ThreadState::kWaitingPerformingGc)
        << thread->GetState() << " thread " << thread << " self " << self;
    {
      ThreadRootsVisitor</*kBufferSize*/ 20> visitor(mark_compact_, self);
      thread->VisitRoots(&visitor, kVisitRootFlagAllRoots);
    }
    // Clear page-buffer to prepare for compaction phase.
    thread->SetThreadLocalGcBuffer(nullptr);

    // If thread is a running mutator, then act on behalf of the garbage
    // collector. See the code in ThreadList::RunCheckpoint.
    mark_compact_->GetBarrier().Pass(self);
  }

 private:
  MarkCompact* const mark_compact_;
};

void MarkCompact::MarkRootsCheckpoint(Thread* self, Runtime* runtime) {
  // We revote TLABs later during paused round of marking.
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  CheckpointMarkThreadRoots check_point(this);
  ThreadList* thread_list = runtime->GetThreadList();
  gc_barrier_.Init(self, 0);
  // Request the check point is run on all threads returning a count of the threads that must
  // run through the barrier including self.
  size_t barrier_count = thread_list->RunCheckpoint(&check_point);
  // Release locks then wait for all mutator threads to pass the barrier.
  // If there are no threads to wait which implys that all the checkpoint functions are finished,
  // then no need to release locks.
  if (barrier_count == 0) {
    return;
  }
  Locks::heap_bitmap_lock_->ExclusiveUnlock(self);
  Locks::mutator_lock_->SharedUnlock(self);
  {
    ScopedThreadStateChange tsc(self, ThreadState::kWaitingForCheckPointsToRun);
    gc_barrier_.Increment(self, barrier_count);
  }
  Locks::mutator_lock_->SharedLock(self);
  Locks::heap_bitmap_lock_->ExclusiveLock(self);
}

void MarkCompact::MarkNonThreadRoots(Runtime* runtime) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  runtime->VisitNonThreadRoots(this);
}

void MarkCompact::MarkConcurrentRoots(VisitRootFlags flags, Runtime* runtime) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  runtime->VisitConcurrentRoots(this, flags);
}

void MarkCompact::RevokeAllThreadLocalBuffers() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  bump_pointer_space_->RevokeAllThreadLocalBuffers();
}

class MarkCompact::ScanObjectVisitor {
 public:
  explicit ScanObjectVisitor(MarkCompact* const mark_compact) ALWAYS_INLINE
      : mark_compact_(mark_compact) {}

  void operator()(ObjPtr<mirror::Object> obj) const
      ALWAYS_INLINE
      REQUIRES(Locks::heap_bitmap_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    mark_compact_->ScanObject</*kUpdateLiveWords*/ false>(obj.Ptr());
  }

 private:
  MarkCompact* const mark_compact_;
};

void MarkCompact::UpdateAndMarkModUnion() {
  accounting::CardTable* const card_table = heap_->GetCardTable();
  for (const auto& space : immune_spaces_.GetSpaces()) {
    const char* name = space->IsZygoteSpace()
        ? "UpdateAndMarkZygoteModUnionTable"
        : "UpdateAndMarkImageModUnionTable";
    DCHECK(space->IsZygoteSpace() || space->IsImageSpace()) << *space;
    TimingLogger::ScopedTiming t(name, GetTimings());
    accounting::ModUnionTable* table = heap_->FindModUnionTableFromSpace(space);
    if (table != nullptr) {
      // UpdateAndMarkReferences() doesn't visit Reference-type objects. But
      // that's fine because these objects are immutable enough (referent can
      // only be cleared) and hence the only referents they can have are intra-space.
      table->UpdateAndMarkReferences(this);
    } else {
      // No mod-union table, scan all dirty/aged cards in the corresponding
      // card-table. This can only occur for app images.
      card_table->Scan</*kClearCard*/ false>(space->GetMarkBitmap(),
                                             space->Begin(),
                                             space->End(),
                                             ScanObjectVisitor(this),
                                             gc::accounting::CardTable::kCardAged);
    }
  }
}

void MarkCompact::MarkReachableObjects() {
  UpdateAndMarkModUnion();
  // Recursively mark all the non-image bits set in the mark bitmap.
  ProcessMarkStack();
}

void MarkCompact::ScanDirtyObjects(bool paused, uint8_t minimum_age) {
  accounting::CardTable* card_table = heap_->GetCardTable();
  for (const auto& space : heap_->GetContinuousSpaces()) {
    const char* name = nullptr;
    switch (space->GetGcRetentionPolicy()) {
    case space::kGcRetentionPolicyNeverCollect:
      name = paused ? "(Paused)ScanGrayImmuneSpaceObjects" : "ScanGrayImmuneSpaceObjects";
      break;
    case space::kGcRetentionPolicyFullCollect:
      name = paused ? "(Paused)ScanGrayZygoteSpaceObjects" : "ScanGrayZygoteSpaceObjects";
      break;
    case space::kGcRetentionPolicyAlwaysCollect:
      name = paused ? "(Paused)ScanGrayAllocSpaceObjects" : "ScanGrayAllocSpaceObjects";
      break;
    default:
      LOG(FATAL) << "Unreachable";
      UNREACHABLE();
    }
    TimingLogger::ScopedTiming t(name, GetTimings());
    card_table->Scan</*kClearCard*/ false>(
        space->GetMarkBitmap(), space->Begin(), space->End(), ScanObjectVisitor(this), minimum_age);
  }
}

void MarkCompact::RecursiveMarkDirtyObjects(bool paused, uint8_t minimum_age) {
  ScanDirtyObjects(paused, minimum_age);
  ProcessMarkStack();
}

void MarkCompact::MarkRoots(VisitRootFlags flags) {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  Runtime* runtime = Runtime::Current();
  // Make sure that the checkpoint which collects the stack roots is the first
  // one capturning GC-roots. As this one is supposed to find the address
  // everything allocated after that (during this marking phase) will be
  // considered 'marked'.
  MarkRootsCheckpoint(thread_running_gc_, runtime);
  MarkNonThreadRoots(runtime);
  MarkConcurrentRoots(flags, runtime);
}

void MarkCompact::PreCleanCards() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  CHECK(!Locks::mutator_lock_->IsExclusiveHeld(thread_running_gc_));
  // Age the card-table before thread stack scanning checkpoint in MarkRoots()
  // as it ensures that there are no in-progress write barriers which started
  // prior to aging the card-table.
  PrepareCardTableForMarking(/*clear_alloc_space_cards*/ false);
  MarkRoots(static_cast<VisitRootFlags>(kVisitRootFlagClearRootLog | kVisitRootFlagNewRoots));
  RecursiveMarkDirtyObjects(/*paused*/ false, accounting::CardTable::kCardDirty - 1);
}

// In a concurrent marking algorithm, if we are not using a write/read barrier, as
// in this case, then we need a stop-the-world (STW) round in the end to mark
// objects which were written into concurrently while concurrent marking was
// performed.
// In order to minimize the pause time, we could take one of the two approaches:
// 1. Keep repeating concurrent marking of dirty cards until the time spent goes
// below a threshold.
// 2. Do two rounds concurrently and then attempt a paused one. If we figure
// that it's taking too long, then resume mutators and retry.
//
// Given the non-trivial fixed overhead of running a round (card table and root
// scan), it might be better to go with approach 2.
void MarkCompact::MarkingPhase() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  DCHECK_EQ(thread_running_gc_, Thread::Current());
  WriterMutexLock mu(thread_running_gc_, *Locks::heap_bitmap_lock_);
  MaybeClampGcStructures();
  PrepareCardTableForMarking(/*clear_alloc_space_cards*/ true);
  MarkZygoteLargeObjects();
  MarkRoots(
        static_cast<VisitRootFlags>(kVisitRootFlagAllRoots | kVisitRootFlagStartLoggingNewRoots));
  MarkReachableObjects();
  // Pre-clean dirtied cards to reduce pauses.
  PreCleanCards();

  // Setup reference processing and forward soft references once before enabling
  // slow path (in MarkingPause)
  ReferenceProcessor* rp = GetHeap()->GetReferenceProcessor();
  bool clear_soft_references = GetCurrentIteration()->GetClearSoftReferences();
  rp->Setup(thread_running_gc_, this, /*concurrent=*/ true, clear_soft_references);
  if (!clear_soft_references) {
    // Forward as many SoftReferences as possible before inhibiting reference access.
    rp->ForwardSoftReferences(GetTimings());
  }
}

class MarkCompact::RefFieldsVisitor {
 public:
  ALWAYS_INLINE explicit RefFieldsVisitor(MarkCompact* const mark_compact)
    : mark_compact_(mark_compact) {}

  ALWAYS_INLINE void operator()(mirror::Object* obj,
                                MemberOffset offset,
                                [[maybe_unused]] bool is_static) const
      REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (kCheckLocks) {
      Locks::mutator_lock_->AssertSharedHeld(Thread::Current());
      Locks::heap_bitmap_lock_->AssertExclusiveHeld(Thread::Current());
    }
    mark_compact_->MarkObject(obj->GetFieldObject<mirror::Object>(offset), obj, offset);
  }

  void operator()(ObjPtr<mirror::Class> klass, ObjPtr<mirror::Reference> ref) const ALWAYS_INLINE
      REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_) {
    mark_compact_->DelayReferenceReferent(klass, ref);
  }

  void VisitRootIfNonNull(mirror::CompressedReference<mirror::Object>* root) const ALWAYS_INLINE
      REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const
      REQUIRES(Locks::heap_bitmap_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (kCheckLocks) {
      Locks::mutator_lock_->AssertSharedHeld(Thread::Current());
      Locks::heap_bitmap_lock_->AssertExclusiveHeld(Thread::Current());
    }
    mark_compact_->MarkObject(root->AsMirrorPtr());
  }

 private:
  MarkCompact* const mark_compact_;
};

template <size_t kAlignment>
size_t MarkCompact::LiveWordsBitmap<kAlignment>::LiveBytesInBitmapWord(size_t chunk_idx) const {
  const size_t index = chunk_idx * kBitmapWordsPerVectorWord;
  size_t words = 0;
  for (uint32_t i = 0; i < kBitmapWordsPerVectorWord; i++) {
    words += POPCOUNT(Bitmap::Begin()[index + i]);
  }
  return words * kAlignment;
}

void MarkCompact::UpdateLivenessInfo(mirror::Object* obj, size_t obj_size) {
  DCHECK(obj != nullptr);
  DCHECK_EQ(obj_size, obj->SizeOf<kDefaultVerifyFlags>());
  uintptr_t obj_begin = reinterpret_cast<uintptr_t>(obj);
  UpdateClassAfterObjectMap(obj);
  size_t size = RoundUp(obj_size, kAlignment);
  uintptr_t bit_index = live_words_bitmap_->SetLiveWords(obj_begin, size);
  size_t chunk_idx = (obj_begin - live_words_bitmap_->Begin()) / kOffsetChunkSize;
  // Compute the bit-index within the chunk-info vector word.
  bit_index %= kBitsPerVectorWord;
  size_t first_chunk_portion = std::min(size, (kBitsPerVectorWord - bit_index) * kAlignment);

  chunk_info_vec_[chunk_idx++] += first_chunk_portion;
  DCHECK_LE(first_chunk_portion, size);
  for (size -= first_chunk_portion; size > kOffsetChunkSize; size -= kOffsetChunkSize) {
    DCHECK_EQ(chunk_info_vec_[chunk_idx], 0u);
    chunk_info_vec_[chunk_idx++] = kOffsetChunkSize;
  }
  chunk_info_vec_[chunk_idx] += size;
  freed_objects_--;
}

template <bool kUpdateLiveWords>
void MarkCompact::ScanObject(mirror::Object* obj) {
  // The size of `obj` is used both here (to update `bytes_scanned_`) and in
  // `UpdateLivenessInfo`. As fetching this value can be expensive, do it once
  // here and pass that information to `UpdateLivenessInfo`.
  size_t obj_size = obj->SizeOf<kDefaultVerifyFlags>();
  bytes_scanned_ += obj_size;

  RefFieldsVisitor visitor(this);
  DCHECK(IsMarked(obj)) << "Scanning marked object " << obj << "\n" << heap_->DumpSpaces();
  if (kUpdateLiveWords && HasAddress(obj)) {
    UpdateLivenessInfo(obj, obj_size);
  }
  obj->VisitReferences(visitor, visitor);
}

// Scan anything that's on the mark stack.
void MarkCompact::ProcessMarkStack() {
  TimingLogger::ScopedTiming t(__FUNCTION__, GetTimings());
  // TODO: try prefetch like in CMS
  while (!mark_stack_->IsEmpty()) {
    mirror::Object* obj = mark_stack_->PopBack();
    DCHECK(obj != nullptr);
    ScanObject</*kUpdateLiveWords*/ true>(obj);
  }
}

void MarkCompact::ExpandMarkStack() {
  const size_t new_size = mark_stack_->Capacity() * 2;
  std::vector<StackReference<mirror::Object>> temp(mark_stack_->Begin(),
                                                   mark_stack_->End());
  mark_stack_->Resize(new_size);
  for (auto& ref : temp) {
    mark_stack_->PushBack(ref.AsMirrorPtr());
  }
  DCHECK(!mark_stack_->IsFull());
}

inline void MarkCompact::PushOnMarkStack(mirror::Object* obj) {
  if (UNLIKELY(mark_stack_->IsFull())) {
    ExpandMarkStack();
  }
  mark_stack_->PushBack(obj);
}

inline void MarkCompact::MarkObjectNonNull(mirror::Object* obj,
                                           mirror::Object* holder,
                                           MemberOffset offset) {
  DCHECK(obj != nullptr);
  if (MarkObjectNonNullNoPush</*kParallel*/false>(obj, holder, offset)) {
    PushOnMarkStack(obj);
  }
}

template <bool kParallel>
inline bool MarkCompact::MarkObjectNonNullNoPush(mirror::Object* obj,
                                                 mirror::Object* holder,
                                                 MemberOffset offset) {
  // We expect most of the referenes to be in bump-pointer space, so try that
  // first to keep the cost of this function minimal.
  if (LIKELY(HasAddress(obj))) {
    return kParallel ? !moving_space_bitmap_->AtomicTestAndSet(obj)
                     : !moving_space_bitmap_->Set(obj);
  } else if (non_moving_space_bitmap_->HasAddress(obj)) {
    return kParallel ? !non_moving_space_bitmap_->AtomicTestAndSet(obj)
                     : !non_moving_space_bitmap_->Set(obj);
  } else if (immune_spaces_.ContainsObject(obj)) {
    DCHECK(IsMarked(obj) != nullptr);
    return false;
  } else {
    // Must be a large-object space, otherwise it's a case of heap corruption.
    if (!IsAlignedParam(obj, space::LargeObjectSpace::ObjectAlignment())) {
      // Objects in large-object space are aligned to the large-object alignment.
      // So if we have an object which doesn't belong to any space and is not
      // page-aligned as well, then it's memory corruption.
      // TODO: implement protect/unprotect in bump-pointer space.
      heap_->GetVerification()->LogHeapCorruption(holder, offset, obj, /*fatal*/ true);
    }
    DCHECK_NE(heap_->GetLargeObjectsSpace(), nullptr)
        << "ref=" << obj
        << " doesn't belong to any of the spaces and large object space doesn't exist";
    accounting::LargeObjectBitmap* los_bitmap = heap_->GetLargeObjectsSpace()->GetMarkBitmap();
    DCHECK(los_bitmap->HasAddress(obj));
    if (kParallel) {
      los_bitmap->AtomicTestAndSet(obj);
    } else {
      los_bitmap->Set(obj);
    }
    // We only have primitive arrays in large object space. So there is no
    // reason to push into mark-stack.
    DCHECK(obj->IsString() || (obj->IsArrayInstance() && !obj->IsObjectArray()));
    return false;
  }
}

inline void MarkCompact::MarkObject(mirror::Object* obj,
                                    mirror::Object* holder,
                                    MemberOffset offset) {
  if (obj != nullptr) {
    MarkObjectNonNull(obj, holder, offset);
  }
}

mirror::Object* MarkCompact::MarkObject(mirror::Object* obj) {
  MarkObject(obj, nullptr, MemberOffset(0));
  return obj;
}

void MarkCompact::MarkHeapReference(mirror::HeapReference<mirror::Object>* obj,
                                    [[maybe_unused]] bool do_atomic_update) {
  MarkObject(obj->AsMirrorPtr(), nullptr, MemberOffset(0));
}

void MarkCompact::VisitRoots(mirror::Object*** roots,
                             size_t count,
                             const RootInfo& info) {
  if (compacting_) {
    uint8_t* moving_space_begin = moving_space_begin_;
    uint8_t* moving_space_end = moving_space_end_;
    for (size_t i = 0; i < count; ++i) {
      UpdateRoot(roots[i], moving_space_begin, moving_space_end, info);
    }
  } else {
    for (size_t i = 0; i < count; ++i) {
      MarkObjectNonNull(*roots[i]);
    }
  }
}

void MarkCompact::VisitRoots(mirror::CompressedReference<mirror::Object>** roots,
                             size_t count,
                             const RootInfo& info) {
  // TODO: do we need to check if the root is null or not?
  if (compacting_) {
    uint8_t* moving_space_begin = moving_space_begin_;
    uint8_t* moving_space_end = moving_space_end_;
    for (size_t i = 0; i < count; ++i) {
      UpdateRoot(roots[i], moving_space_begin, moving_space_end, info);
    }
  } else {
    for (size_t i = 0; i < count; ++i) {
      MarkObjectNonNull(roots[i]->AsMirrorPtr());
    }
  }
}

mirror::Object* MarkCompact::IsMarked(mirror::Object* obj) {
  if (HasAddress(obj)) {
    const bool is_black = reinterpret_cast<uint8_t*>(obj) >= black_allocations_begin_;
    if (compacting_) {
      if (is_black) {
        return PostCompactBlackObjAddr(obj);
      } else if (live_words_bitmap_->Test(obj)) {
        return PostCompactOldObjAddr(obj);
      } else {
        return nullptr;
      }
    }
    return (is_black || moving_space_bitmap_->Test(obj)) ? obj : nullptr;
  } else if (non_moving_space_bitmap_->HasAddress(obj)) {
    return non_moving_space_bitmap_->Test(obj) ? obj : nullptr;
  } else if (immune_spaces_.ContainsObject(obj)) {
    return obj;
  } else {
    DCHECK(heap_->GetLargeObjectsSpace())
        << "ref=" << obj
        << " doesn't belong to any of the spaces and large object space doesn't exist";
    accounting::LargeObjectBitmap* los_bitmap = heap_->GetLargeObjectsSpace()->GetMarkBitmap();
    if (los_bitmap->HasAddress(obj)) {
      DCHECK(IsAlignedParam(obj, space::LargeObjectSpace::ObjectAlignment()));
      return los_bitmap->Test(obj) ? obj : nullptr;
    } else {
      // The given obj is not in any of the known spaces, so return null. This could
      // happen for instance in interpreter caches wherein a concurrent updation
      // to the cache could result in obj being a non-reference. This is
      // tolerable because SweepInterpreterCaches only updates if the given
      // object has moved, which can't be the case for the non-reference.
      return nullptr;
    }
  }
}

bool MarkCompact::IsNullOrMarkedHeapReference(mirror::HeapReference<mirror::Object>* obj,
                                              [[maybe_unused]] bool do_atomic_update) {
  mirror::Object* ref = obj->AsMirrorPtr();
  if (ref == nullptr) {
    return true;
  }
  return IsMarked(ref);
}

// Process the 'referent' field in a java.lang.ref.Reference. If the referent
// has not yet been marked, put it on the appropriate list in the heap for later
// processing.
void MarkCompact::DelayReferenceReferent(ObjPtr<mirror::Class> klass,
                                         ObjPtr<mirror::Reference> ref) {
  heap_->GetReferenceProcessor()->DelayReferenceReferent(klass, ref, this);
}

void MarkCompact::FinishPhase() {
  GetCurrentIteration()->SetScannedBytes(bytes_scanned_);
  bool is_zygote = Runtime::Current()->IsZygote();
  compacting_ = false;
  minor_fault_initialized_ = !is_zygote && uffd_minor_fault_supported_;
  // Madvise compaction buffers. When using threaded implementation, skip the first page,
  // which is used by the gc-thread for the next iteration. Otherwise, we get into a
  // deadlock due to userfault on it in the next iteration. This page is not consuming any
  // physical memory because we already madvised it above and then we triggered a read
  // userfault, which maps a special zero-page.
  if (use_uffd_sigbus_ || !minor_fault_initialized_ || !shadow_to_space_map_.IsValid() ||
      shadow_to_space_map_.Size() < (moving_first_objs_count_ + black_page_count_) * gPageSize) {
    size_t adjustment = use_uffd_sigbus_ ? 0 : gPageSize;
    ZeroAndReleaseMemory(compaction_buffers_map_.Begin() + adjustment,
                         compaction_buffers_map_.Size() - adjustment);
  } else if (shadow_to_space_map_.Size() == bump_pointer_space_->Capacity()) {
    // Now that we are going to use minor-faults from next GC cycle, we can
    // unmap the buffers used by worker threads.
    compaction_buffers_map_.SetSize(gPageSize);
  }
  info_map_.MadviseDontNeedAndZero();
  live_words_bitmap_->ClearBitmap();
  // TODO: We can clear this bitmap right before compaction pause. But in that
  // case we need to ensure that we don't assert on this bitmap afterwards.
  // Also, we would still need to clear it here again as we may have to use the
  // bitmap for black-allocations (see UpdateMovingSpaceBlackAllocations()).
  moving_space_bitmap_->Clear();

  if (UNLIKELY(is_zygote && IsValidFd(uffd_))) {
    heap_->DeleteThreadPool();
    // This unregisters all ranges as a side-effect.
    close(uffd_);
    uffd_ = kFdUnused;
    uffd_initialized_ = false;
  }
  CHECK(mark_stack_->IsEmpty());  // Ensure that the mark stack is empty.
  mark_stack_->Reset();
  DCHECK_EQ(thread_running_gc_, Thread::Current());
  if (kIsDebugBuild) {
    MutexLock mu(thread_running_gc_, lock_);
    if (updated_roots_.get() != nullptr) {
      updated_roots_->clear();
    }
  }
  class_after_obj_ordered_map_.clear();
  delete[] moving_pages_status_;
  linear_alloc_arenas_.clear();
  {
    ReaderMutexLock mu(thread_running_gc_, *Locks::mutator_lock_);
    WriterMutexLock mu2(thread_running_gc_, *Locks::heap_bitmap_lock_);
    heap_->ClearMarkedObjects();
  }
  std::swap(moving_to_space_fd_, moving_from_space_fd_);
  if (IsValidFd(moving_to_space_fd_)) {
    // Confirm that the memfd to be used on to-space in next GC cycle is empty.
    struct stat buf;
    DCHECK_EQ(fstat(moving_to_space_fd_, &buf), 0) << "fstat failed: " << strerror(errno);
    DCHECK_EQ(buf.st_blocks, 0u);
  }
  GcVisitedArenaPool* arena_pool =
      static_cast<GcVisitedArenaPool*>(Runtime::Current()->GetLinearAllocArenaPool());
  arena_pool->DeleteUnusedArenas();
}

}  // namespace collector
}  // namespace gc
}  // namespace art
