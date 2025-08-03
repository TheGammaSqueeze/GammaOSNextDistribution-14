/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "perfetto_hprof"

#include "perfetto_hprof.h"

#include <fcntl.h>
#include <fnmatch.h>
#include <inttypes.h>
#include <sched.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <thread>
#include <time.h>

#include <limits>
#include <optional>
#include <type_traits>

#include "android-base/file.h"
#include "android-base/logging.h"
#include "android-base/properties.h"
#include "base/fast_exit.h"
#include "base/systrace.h"
#include "gc/heap-visit-objects-inl.h"
#include "gc/heap.h"
#include "gc/scoped_gc_critical_section.h"
#include "mirror/object-refvisitor-inl.h"
#include "nativehelper/scoped_local_ref.h"
#include "perfetto/profiling/parse_smaps.h"
#include "perfetto/trace/interned_data/interned_data.pbzero.h"
#include "perfetto/trace/profiling/heap_graph.pbzero.h"
#include "perfetto/trace/profiling/profile_common.pbzero.h"
#include "perfetto/trace/profiling/smaps.pbzero.h"
#include "perfetto/config/profiling/java_hprof_config.pbzero.h"
#include "perfetto/protozero/packed_repeated_fields.h"
#include "perfetto/tracing.h"
#include "runtime-inl.h"
#include "runtime_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "thread_list.h"
#include "well_known_classes.h"
#include "dex/descriptors_names.h"

// There are three threads involved in this:
// * listener thread: this is idle in the background when this plugin gets loaded, and waits
//   for data on on g_signal_pipe_fds.
// * signal thread: an arbitrary thread that handles the signal and writes data to
//   g_signal_pipe_fds.
// * perfetto producer thread: once the signal is received, the app forks. In the newly forked
//   child, the Perfetto Client API spawns a thread to communicate with traced.

namespace perfetto_hprof {

constexpr int kJavaHeapprofdSignal = __SIGRTMIN + 6;
constexpr time_t kWatchdogTimeoutSec = 120;
// This needs to be lower than the maximum acceptable chunk size, because this
// is checked *before* writing another submessage. We conservatively assume
// submessages can be up to 100k here for a 500k chunk size.
// DropBox has a 500k chunk limit, and each chunk needs to parse as a proto.
constexpr uint32_t kPacketSizeThreshold = 400000;
constexpr char kByte[1] = {'x'};
static art::Mutex& GetStateMutex() {
  static art::Mutex state_mutex("perfetto_hprof_state_mutex", art::LockLevel::kGenericBottomLock);
  return state_mutex;
}

static art::ConditionVariable& GetStateCV() {
  static art::ConditionVariable state_cv("perfetto_hprof_state_cv", GetStateMutex());
  return state_cv;
}

static int requested_tracing_session_id = 0;
static State g_state = State::kUninitialized;
static bool g_oome_triggered = false;
static uint32_t g_oome_sessions_pending = 0;

// Pipe to signal from the signal handler into a worker thread that handles the
// dump requests.
int g_signal_pipe_fds[2];
static struct sigaction g_orig_act = {};

template <typename T>
uint64_t FindOrAppend(std::map<T, uint64_t>* m, const T& s) {
  auto it = m->find(s);
  if (it == m->end()) {
    std::tie(it, std::ignore) = m->emplace(s, m->size());
  }
  return it->second;
}

void ArmWatchdogOrDie() {
  timer_t timerid{};
  struct sigevent sev {};
  sev.sigev_notify = SIGEV_SIGNAL;
  sev.sigev_signo = SIGKILL;

  if (timer_create(CLOCK_MONOTONIC, &sev, &timerid) == -1) {
    // This only gets called in the child, so we can fatal without impacting
    // the app.
    PLOG(FATAL) << "failed to create watchdog timer";
  }

  struct itimerspec its {};
  its.it_value.tv_sec = kWatchdogTimeoutSec;

  if (timer_settime(timerid, 0, &its, nullptr) == -1) {
    // This only gets called in the child, so we can fatal without impacting
    // the app.
    PLOG(FATAL) << "failed to arm watchdog timer";
  }
}

bool StartsWith(const std::string& str, const std::string& prefix) {
  return str.compare(0, prefix.length(), prefix) == 0;
}

// Sample entries that match one of the following
// start with /system/
// start with /vendor/
// start with /data/app/
// contains "extracted in memory from Y", where Y matches any of the above
bool ShouldSampleSmapsEntry(const perfetto::profiling::SmapsEntry& e) {
  if (StartsWith(e.pathname, "/system/") || StartsWith(e.pathname, "/vendor/") ||
      StartsWith(e.pathname, "/data/app/")) {
    return true;
  }
  if (StartsWith(e.pathname, "[anon:")) {
    if (e.pathname.find("extracted in memory from /system/") != std::string::npos) {
      return true;
    }
    if (e.pathname.find("extracted in memory from /vendor/") != std::string::npos) {
      return true;
    }
    if (e.pathname.find("extracted in memory from /data/app/") != std::string::npos) {
      return true;
    }
  }
  return false;
}

uint64_t GetCurrentBootClockNs() {
  struct timespec ts = {};
  if (clock_gettime(CLOCK_BOOTTIME, &ts) != 0) {
    LOG(FATAL) << "Failed to get boottime.";
  }
  return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

bool IsDebugBuild() {
  std::string build_type = android::base::GetProperty("ro.build.type", "");
  return !build_type.empty() && build_type != "user";
}

// Verifies the manifest restrictions are respected.
// For regular heap dumps this is already handled by heapprofd.
bool IsOomeHeapDumpAllowed(const perfetto::DataSourceConfig& ds_config) {
  if (art::Runtime::Current()->IsJavaDebuggable() || IsDebugBuild()) {
    return true;
  }

  if (ds_config.session_initiator() ==
      perfetto::DataSourceConfig::SESSION_INITIATOR_TRUSTED_SYSTEM) {
    return art::Runtime::Current()->IsProfileable() || art::Runtime::Current()->IsSystemServer();
  } else {
    return art::Runtime::Current()->IsProfileableFromShell();
  }
}

class JavaHprofDataSource : public perfetto::DataSource<JavaHprofDataSource> {
 public:
  constexpr static perfetto::BufferExhaustedPolicy kBufferExhaustedPolicy =
    perfetto::BufferExhaustedPolicy::kStall;

  explicit JavaHprofDataSource(bool is_oome_heap) : is_oome_heap_(is_oome_heap) {}

  void OnSetup(const SetupArgs& args) override {
    if (!is_oome_heap_) {
      uint64_t normalized_tracing_session_id =
        args.config->tracing_session_id() % std::numeric_limits<int32_t>::max();
      if (requested_tracing_session_id < 0) {
        LOG(ERROR) << "invalid requested tracing session id " << requested_tracing_session_id;
        return;
      }
      if (static_cast<uint64_t>(requested_tracing_session_id) != normalized_tracing_session_id) {
        return;
      }
    }

    // This is on the heap as it triggers -Wframe-larger-than.
    std::unique_ptr<perfetto::protos::pbzero::JavaHprofConfig::Decoder> cfg(
        new perfetto::protos::pbzero::JavaHprofConfig::Decoder(
          args.config->java_hprof_config_raw()));

    dump_smaps_ = cfg->dump_smaps();
    for (auto it = cfg->ignored_types(); it; ++it) {
      std::string name = (*it).ToStdString();
      ignored_types_.emplace_back(art::InversePrettyDescriptor(name));
    }
    // This tracing session ID matches the requesting tracing session ID, so we know heapprofd
    // has verified it targets this process.
    enabled_ =
        !is_oome_heap_ || (IsOomeHeapDumpAllowed(*args.config) && IsOomeDumpEnabled(*cfg.get()));
  }

  bool dump_smaps() { return dump_smaps_; }

  // Per-DataSource enable bit. Invoked by the ::Trace method.
  bool enabled() { return enabled_; }

  void OnStart(const StartArgs&) override {
    art::MutexLock lk(art_thread(), GetStateMutex());
    // In case there are multiple tracing sessions waiting for an OOME error,
    // there will be a data source instance for each of them. Before the
    // transition to kStart and signaling the dumping thread, we need to make
    // sure all the data sources are ready.
    if (is_oome_heap_ && g_oome_sessions_pending > 0) {
      --g_oome_sessions_pending;
    }
    if (g_state == State::kWaitForStart) {
      // WriteHeapPackets is responsible for checking whether the DataSource is\
      // actually enabled.
      if (!is_oome_heap_ || g_oome_sessions_pending == 0) {
        g_state = State::kStart;
        GetStateCV().Broadcast(art_thread());
      }
    }
  }

  // This datasource can be used with a trace config with a short duration_ms
  // but a long datasource_stop_timeout_ms. In that case, OnStop is called (in
  // general) before the dump is done. In that case, we handle the stop
  // asynchronously, and notify the tracing service once we are done.
  // In case OnStop is called after the dump is done (but before the process)
  // has exited, we just acknowledge the request.
  void OnStop(const StopArgs& a) override {
    art::MutexLock lk(art_thread(), finish_mutex_);
    if (is_finished_) {
      return;
    }
    is_stopped_ = true;
    async_stop_ = std::move(a.HandleStopAsynchronously());
  }

  static art::Thread* art_thread() {
    // TODO(fmayer): Attach the Perfetto producer thread to ART and give it a name. This is
    // not trivial, we cannot just attach the first time this method is called, because
    // AttachCurrentThread deadlocks with the ConditionVariable::Wait in WaitForDataSource.
    //
    // We should attach the thread as soon as the Client API spawns it, but that needs more
    // complicated plumbing.
    return nullptr;
  }

  std::vector<std::string> ignored_types() { return ignored_types_; }

  void Finish() {
    art::MutexLock lk(art_thread(), finish_mutex_);
    if (is_stopped_) {
      async_stop_();
    } else {
      is_finished_ = true;
    }
  }

 private:
  static bool IsOomeDumpEnabled(const perfetto::protos::pbzero::JavaHprofConfig::Decoder& cfg) {
    std::string cmdline;
    if (!android::base::ReadFileToString("/proc/self/cmdline", &cmdline)) {
      return false;
    }
    const char* argv0 = cmdline.c_str();

    for (auto it = cfg.process_cmdline(); it; ++it) {
      std::string pattern = (*it).ToStdString();
      if (fnmatch(pattern.c_str(), argv0, FNM_NOESCAPE) == 0) {
        return true;
      }
    }
    return false;
  }

  bool is_oome_heap_ = false;
  bool enabled_ = false;
  bool dump_smaps_ = false;
  std::vector<std::string> ignored_types_;

  art::Mutex finish_mutex_{"perfetto_hprof_ds_mutex", art::LockLevel::kGenericBottomLock};
  bool is_finished_ = false;
  bool is_stopped_ = false;
  std::function<void()> async_stop_;
};

void SetupDataSource(const std::string& ds_name, bool is_oome_heap) {
  perfetto::TracingInitArgs args;
  args.backends = perfetto::BackendType::kSystemBackend;
  perfetto::Tracing::Initialize(args);

  perfetto::DataSourceDescriptor dsd;
  dsd.set_name(ds_name);
  dsd.set_will_notify_on_stop(true);
  JavaHprofDataSource::Register(dsd, is_oome_heap);
  LOG(INFO) << "registered data source " << ds_name;
}

// Waits for the data source OnStart
void WaitForDataSource(art::Thread* self) {
  art::MutexLock lk(self, GetStateMutex());
  while (g_state != State::kStart) {
    GetStateCV().Wait(self);
  }
}

// Waits for the data source OnStart with a timeout. Returns false on timeout.
bool TimedWaitForDataSource(art::Thread* self, int64_t timeout_ms) {
  const uint64_t cutoff_ns = GetCurrentBootClockNs() + timeout_ms * 1000000;
  art::MutexLock lk(self, GetStateMutex());
  while (g_state != State::kStart) {
    const uint64_t current_ns = GetCurrentBootClockNs();
    if (current_ns >= cutoff_ns) {
      return false;
    }
    GetStateCV().TimedWait(self, (cutoff_ns - current_ns) / 1000000, 0);
  }
  return true;
}

// Helper class to write Java heap dumps to `ctx`. The whole heap dump can be
// split into more perfetto.protos.HeapGraph messages, to avoid making each
// message too big.
class Writer {
 public:
  Writer(pid_t pid, JavaHprofDataSource::TraceContext* ctx, uint64_t timestamp)
      : pid_(pid), ctx_(ctx), timestamp_(timestamp),
        last_written_(ctx_->written()) {}

  // Return whether the next call to GetHeapGraph will create a new TracePacket.
  bool will_create_new_packet() const {
    return !heap_graph_ || ctx_->written() - last_written_ > kPacketSizeThreshold;
  }

  perfetto::protos::pbzero::HeapGraph* GetHeapGraph() {
    if (will_create_new_packet()) {
      CreateNewHeapGraph();
    }
    return heap_graph_;
  }

  void Finalize() {
    if (trace_packet_) {
      trace_packet_->Finalize();
    }
    heap_graph_ = nullptr;
  }

  ~Writer() { Finalize(); }

 private:
  Writer(const Writer&) = delete;
  Writer& operator=(const Writer&) = delete;
  Writer(Writer&&) = delete;
  Writer& operator=(Writer&&) = delete;

  void CreateNewHeapGraph() {
    if (heap_graph_) {
      heap_graph_->set_continued(true);
    }
    Finalize();

    uint64_t written = ctx_->written();

    trace_packet_ = ctx_->NewTracePacket();
    trace_packet_->set_timestamp(timestamp_);
    heap_graph_ = trace_packet_->set_heap_graph();
    heap_graph_->set_pid(pid_);
    heap_graph_->set_index(index_++);

    last_written_ = written;
  }

  const pid_t pid_;
  JavaHprofDataSource::TraceContext* const ctx_;
  const uint64_t timestamp_;

  uint64_t last_written_ = 0;

  perfetto::DataSource<JavaHprofDataSource>::TraceContext::TracePacketHandle
      trace_packet_;
  perfetto::protos::pbzero::HeapGraph* heap_graph_ = nullptr;

  uint64_t index_ = 0;
};

class ReferredObjectsFinder {
 public:
  explicit ReferredObjectsFinder(
      std::vector<std::pair<std::string, art::mirror::Object*>>* referred_objects,
      bool emit_field_ids)
      : referred_objects_(referred_objects), emit_field_ids_(emit_field_ids) {}

  // For art::mirror::Object::VisitReferences.
  void operator()(art::ObjPtr<art::mirror::Object> obj, art::MemberOffset offset,
                  bool is_static) const
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    if (offset.Uint32Value() == art::mirror::Object::ClassOffset().Uint32Value()) {
      // Skip shadow$klass pointer.
      return;
    }
    art::mirror::Object* ref = obj->GetFieldObject<art::mirror::Object>(offset);
    art::ArtField* field;
    if (is_static) {
      field = art::ArtField::FindStaticFieldWithOffset(obj->AsClass(), offset.Uint32Value());
    } else {
      field = art::ArtField::FindInstanceFieldWithOffset(obj->GetClass(), offset.Uint32Value());
    }
    std::string field_name = "";
    if (field != nullptr && emit_field_ids_) {
      field_name = field->PrettyField(/*with_type=*/true);
    }
    referred_objects_->emplace_back(std::move(field_name), ref);
  }

  void VisitRootIfNonNull(
      [[maybe_unused]] art::mirror::CompressedReference<art::mirror::Object>* root) const {}
  void VisitRoot(
      [[maybe_unused]] art::mirror::CompressedReference<art::mirror::Object>* root) const {}

 private:
  // We can use a raw Object* pointer here, because there are no concurrent GC threads after the
  // fork.
  std::vector<std::pair<std::string, art::mirror::Object*>>* referred_objects_;
  // Prettifying field names is expensive; avoid if field name will not be used.
  bool emit_field_ids_;
};

class RootFinder : public art::SingleRootVisitor {
 public:
  explicit RootFinder(
    std::map<art::RootType, std::vector<art::mirror::Object*>>* root_objects)
      : root_objects_(root_objects) {}

  void VisitRoot(art::mirror::Object* root, const art::RootInfo& info) override {
    (*root_objects_)[info.GetType()].emplace_back(root);
  }

 private:
  // We can use a raw Object* pointer here, because there are no concurrent GC threads after the
  // fork.
  std::map<art::RootType, std::vector<art::mirror::Object*>>* root_objects_;
};

perfetto::protos::pbzero::HeapGraphRoot::Type ToProtoType(art::RootType art_type) {
  using perfetto::protos::pbzero::HeapGraphRoot;
  switch (art_type) {
    case art::kRootUnknown:
      return HeapGraphRoot::ROOT_UNKNOWN;
    case art::kRootJNIGlobal:
      return HeapGraphRoot::ROOT_JNI_GLOBAL;
    case art::kRootJNILocal:
      return HeapGraphRoot::ROOT_JNI_LOCAL;
    case art::kRootJavaFrame:
      return HeapGraphRoot::ROOT_JAVA_FRAME;
    case art::kRootNativeStack:
      return HeapGraphRoot::ROOT_NATIVE_STACK;
    case art::kRootStickyClass:
      return HeapGraphRoot::ROOT_STICKY_CLASS;
    case art::kRootThreadBlock:
      return HeapGraphRoot::ROOT_THREAD_BLOCK;
    case art::kRootMonitorUsed:
      return HeapGraphRoot::ROOT_MONITOR_USED;
    case art::kRootThreadObject:
      return HeapGraphRoot::ROOT_THREAD_OBJECT;
    case art::kRootInternedString:
      return HeapGraphRoot::ROOT_INTERNED_STRING;
    case art::kRootFinalizing:
      return HeapGraphRoot::ROOT_FINALIZING;
    case art::kRootDebugger:
      return HeapGraphRoot::ROOT_DEBUGGER;
    case art::kRootReferenceCleanup:
      return HeapGraphRoot::ROOT_REFERENCE_CLEANUP;
    case art::kRootVMInternal:
      return HeapGraphRoot::ROOT_VM_INTERNAL;
    case art::kRootJNIMonitor:
      return HeapGraphRoot::ROOT_JNI_MONITOR;
  }
}

perfetto::protos::pbzero::HeapGraphType::Kind ProtoClassKind(uint32_t class_flags) {
  using perfetto::protos::pbzero::HeapGraphType;
  switch (class_flags) {
    case art::mirror::kClassFlagNormal:
    case art::mirror::kClassFlagRecord:
      return HeapGraphType::KIND_NORMAL;
    case art::mirror::kClassFlagNoReferenceFields:
    case art::mirror::kClassFlagNoReferenceFields | art::mirror::kClassFlagRecord:
      return HeapGraphType::KIND_NOREFERENCES;
    case art::mirror::kClassFlagString | art::mirror::kClassFlagNoReferenceFields:
      return HeapGraphType::KIND_STRING;
    case art::mirror::kClassFlagObjectArray:
      return HeapGraphType::KIND_ARRAY;
    case art::mirror::kClassFlagClass:
      return HeapGraphType::KIND_CLASS;
    case art::mirror::kClassFlagClassLoader:
      return HeapGraphType::KIND_CLASSLOADER;
    case art::mirror::kClassFlagDexCache:
      return HeapGraphType::KIND_DEXCACHE;
    case art::mirror::kClassFlagSoftReference:
      return HeapGraphType::KIND_SOFT_REFERENCE;
    case art::mirror::kClassFlagWeakReference:
      return HeapGraphType::KIND_WEAK_REFERENCE;
    case art::mirror::kClassFlagFinalizerReference:
      return HeapGraphType::KIND_FINALIZER_REFERENCE;
    case art::mirror::kClassFlagPhantomReference:
      return HeapGraphType::KIND_PHANTOM_REFERENCE;
    default:
      return HeapGraphType::KIND_UNKNOWN;
  }
}

std::string PrettyType(art::mirror::Class* klass) NO_THREAD_SAFETY_ANALYSIS {
  if (klass == nullptr) {
    return "(raw)";
  }
  std::string temp;
  std::string result(art::PrettyDescriptor(klass->GetDescriptor(&temp)));
  return result;
}

void DumpSmaps(JavaHprofDataSource::TraceContext* ctx) {
  FILE* smaps = fopen("/proc/self/smaps", "re");
  if (smaps != nullptr) {
    auto trace_packet = ctx->NewTracePacket();
    auto* smaps_packet = trace_packet->set_smaps_packet();
    smaps_packet->set_pid(getpid());
    perfetto::profiling::ParseSmaps(smaps,
        [&smaps_packet](const perfetto::profiling::SmapsEntry& e) {
      if (ShouldSampleSmapsEntry(e)) {
        auto* smaps_entry = smaps_packet->add_entries();
        smaps_entry->set_path(e.pathname);
        smaps_entry->set_size_kb(e.size_kb);
        smaps_entry->set_private_dirty_kb(e.private_dirty_kb);
        smaps_entry->set_swap_kb(e.swap_kb);
      }
    });
    fclose(smaps);
  } else {
    PLOG(ERROR) << "failed to open smaps";
  }
}

uint64_t GetObjectId(const art::mirror::Object* obj) {
  return reinterpret_cast<uint64_t>(obj) / std::alignment_of<art::mirror::Object>::value;
}

template <typename F>
void ForInstanceReferenceField(art::mirror::Class* klass, F fn) NO_THREAD_SAFETY_ANALYSIS {
  for (art::ArtField& af : klass->GetIFields()) {
    if (af.IsPrimitiveType() ||
        af.GetOffset().Uint32Value() == art::mirror::Object::ClassOffset().Uint32Value()) {
      continue;
    }
    fn(af.GetOffset());
  }
}

size_t EncodedSize(uint64_t n) {
  if (n == 0) return 1;
  return 1 + static_cast<size_t>(art::MostSignificantBit(n)) / 7;
}

// Returns all the references that `*obj` (an object of type `*klass`) is holding.
std::vector<std::pair<std::string, art::mirror::Object*>> GetReferences(art::mirror::Object* obj,
                                                                        art::mirror::Class* klass,
                                                                        bool emit_field_ids)
    REQUIRES_SHARED(art::Locks::mutator_lock_) {
  std::vector<std::pair<std::string, art::mirror::Object*>> referred_objects;
  ReferredObjectsFinder objf(&referred_objects, emit_field_ids);

  uint32_t klass_flags = klass->GetClassFlags();
  if (klass_flags != art::mirror::kClassFlagNormal &&
      klass_flags != art::mirror::kClassFlagSoftReference &&
      klass_flags != art::mirror::kClassFlagWeakReference &&
      klass_flags != art::mirror::kClassFlagFinalizerReference &&
      klass_flags != art::mirror::kClassFlagPhantomReference) {
    obj->VisitReferences(objf, art::VoidFunctor());
  } else {
    for (art::mirror::Class* cls = klass; cls != nullptr; cls = cls->GetSuperClass().Ptr()) {
      ForInstanceReferenceField(cls,
                                [obj, objf](art::MemberOffset offset) NO_THREAD_SAFETY_ANALYSIS {
                                  objf(art::ObjPtr<art::mirror::Object>(obj),
                                       offset,
                                       /*is_static=*/false);
                                });
    }
  }
  return referred_objects;
}

// Returns the base for delta encoding all the `referred_objects`. If delta
// encoding would waste space, returns 0.
uint64_t EncodeBaseObjId(
    const std::vector<std::pair<std::string, art::mirror::Object*>>& referred_objects,
    const art::mirror::Object* min_nonnull_ptr) REQUIRES_SHARED(art::Locks::mutator_lock_) {
  uint64_t base_obj_id = GetObjectId(min_nonnull_ptr);
  if (base_obj_id <= 1) {
    return 0;
  }

  // We need to decrement the base for object ids so that we can tell apart
  // null references.
  base_obj_id--;
  uint64_t bytes_saved = 0;
  for (const auto& p : referred_objects) {
    art::mirror::Object* referred_obj = p.second;
    if (!referred_obj) {
      continue;
    }
    uint64_t referred_obj_id = GetObjectId(referred_obj);
    bytes_saved += EncodedSize(referred_obj_id) - EncodedSize(referred_obj_id - base_obj_id);
  }

  // +1 for storing the field id.
  if (bytes_saved <= EncodedSize(base_obj_id) + 1) {
    // Subtracting the base ptr gains fewer bytes than it takes to store it.
    return 0;
  }
  return base_obj_id;
}

// Helper to keep intermediate state while dumping objects and classes from ART into
// perfetto.protos.HeapGraph.
class HeapGraphDumper {
 public:
  // Instances of classes whose name is in `ignored_types` will be ignored.
  explicit HeapGraphDumper(const std::vector<std::string>& ignored_types)
      : ignored_types_(ignored_types),
        reference_field_ids_(std::make_unique<protozero::PackedVarInt>()),
        reference_object_ids_(std::make_unique<protozero::PackedVarInt>()) {}

  // Dumps a heap graph from `*runtime` and writes it to `writer`.
  void Dump(art::Runtime* runtime, Writer& writer) REQUIRES(art::Locks::mutator_lock_) {
    DumpRootObjects(runtime, writer);

    DumpObjects(runtime, writer);

    WriteInternedData(writer);
  }

 private:
  // Dumps the root objects from `*runtime` to `writer`.
  void DumpRootObjects(art::Runtime* runtime, Writer& writer)
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    std::map<art::RootType, std::vector<art::mirror::Object*>> root_objects;
    RootFinder rcf(&root_objects);
    runtime->VisitRoots(&rcf);
    std::unique_ptr<protozero::PackedVarInt> object_ids(new protozero::PackedVarInt);
    for (const auto& p : root_objects) {
      const art::RootType root_type = p.first;
      const std::vector<art::mirror::Object*>& children = p.second;
      perfetto::protos::pbzero::HeapGraphRoot* root_proto = writer.GetHeapGraph()->add_roots();
      root_proto->set_root_type(ToProtoType(root_type));
      for (art::mirror::Object* obj : children) {
        if (writer.will_create_new_packet()) {
          root_proto->set_object_ids(*object_ids);
          object_ids->Reset();
          root_proto = writer.GetHeapGraph()->add_roots();
          root_proto->set_root_type(ToProtoType(root_type));
        }
        object_ids->Append(GetObjectId(obj));
      }
      root_proto->set_object_ids(*object_ids);
      object_ids->Reset();
    }
  }

  // Dumps all the objects from `*runtime` to `writer`.
  void DumpObjects(art::Runtime* runtime, Writer& writer) REQUIRES(art::Locks::mutator_lock_) {
    runtime->GetHeap()->VisitObjectsPaused(
        [this, &writer](art::mirror::Object* obj)
            REQUIRES_SHARED(art::Locks::mutator_lock_) { WriteOneObject(obj, writer); });
  }

  // Writes all the previously accumulated (while dumping objects and roots) interned data to
  // `writer`.
  void WriteInternedData(Writer& writer) {
    for (const auto& p : interned_locations_) {
      const std::string& str = p.first;
      uint64_t id = p.second;

      perfetto::protos::pbzero::InternedString* location_proto =
          writer.GetHeapGraph()->add_location_names();
      location_proto->set_iid(id);
      location_proto->set_str(reinterpret_cast<const uint8_t*>(str.c_str()), str.size());
    }
    for (const auto& p : interned_fields_) {
      const std::string& str = p.first;
      uint64_t id = p.second;

      perfetto::protos::pbzero::InternedString* field_proto =
          writer.GetHeapGraph()->add_field_names();
      field_proto->set_iid(id);
      field_proto->set_str(reinterpret_cast<const uint8_t*>(str.c_str()), str.size());
    }
  }

  // Writes `*obj` into `writer`.
  void WriteOneObject(art::mirror::Object* obj, Writer& writer)
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    if (obj->IsClass()) {
      WriteClass(obj->AsClass().Ptr(), writer);
    }

    art::mirror::Class* klass = obj->GetClass();
    uintptr_t class_ptr = reinterpret_cast<uintptr_t>(klass);
    // We need to synethesize a new type for Class<Foo>, which does not exist
    // in the runtime. Otherwise, all the static members of all classes would be
    // attributed to java.lang.Class.
    if (klass->IsClassClass()) {
      class_ptr = WriteSyntheticClassFromObj(obj, writer);
    }

    if (IsIgnored(obj)) {
      return;
    }

    auto class_id = FindOrAppend(&interned_classes_, class_ptr);

    uint64_t object_id = GetObjectId(obj);
    perfetto::protos::pbzero::HeapGraphObject* object_proto = writer.GetHeapGraph()->add_objects();
    if (prev_object_id_ && prev_object_id_ < object_id) {
      object_proto->set_id_delta(object_id - prev_object_id_);
    } else {
      object_proto->set_id(object_id);
    }
    prev_object_id_ = object_id;
    object_proto->set_type_id(class_id);

    // Arrays / strings are magic and have an instance dependent size.
    if (obj->SizeOf() != klass->GetObjectSize()) {
      object_proto->set_self_size(obj->SizeOf());
    }

    FillReferences(obj, klass, object_proto);

    FillFieldValues(obj, klass, object_proto);
  }

  // Writes `*klass` into `writer`.
  void WriteClass(art::mirror::Class* klass, Writer& writer)
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    perfetto::protos::pbzero::HeapGraphType* type_proto = writer.GetHeapGraph()->add_types();
    type_proto->set_id(FindOrAppend(&interned_classes_, reinterpret_cast<uintptr_t>(klass)));
    type_proto->set_class_name(PrettyType(klass));
    type_proto->set_location_id(FindOrAppend(&interned_locations_, klass->GetLocation()));
    type_proto->set_object_size(klass->GetObjectSize());
    type_proto->set_kind(ProtoClassKind(klass->GetClassFlags()));
    type_proto->set_classloader_id(GetObjectId(klass->GetClassLoader().Ptr()));
    if (klass->GetSuperClass().Ptr()) {
      type_proto->set_superclass_id(FindOrAppend(
          &interned_classes_, reinterpret_cast<uintptr_t>(klass->GetSuperClass().Ptr())));
    }
    ForInstanceReferenceField(
        klass, [klass, this](art::MemberOffset offset) NO_THREAD_SAFETY_ANALYSIS {
          auto art_field = art::ArtField::FindInstanceFieldWithOffset(klass, offset.Uint32Value());
          reference_field_ids_->Append(
              FindOrAppend(&interned_fields_, art_field->PrettyField(true)));
        });
    type_proto->set_reference_field_id(*reference_field_ids_);
    reference_field_ids_->Reset();
  }

  // Creates a fake class that represents a type only used by `*obj` into `writer`.
  uintptr_t WriteSyntheticClassFromObj(art::mirror::Object* obj, Writer& writer)
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    CHECK(obj->IsClass());
    perfetto::protos::pbzero::HeapGraphType* type_proto = writer.GetHeapGraph()->add_types();
    // All pointers are at least multiples of two, so this way we can make sure
    // we are not colliding with a real class.
    uintptr_t class_ptr = reinterpret_cast<uintptr_t>(obj) | 1;
    auto class_id = FindOrAppend(&interned_classes_, class_ptr);
    type_proto->set_id(class_id);
    type_proto->set_class_name(obj->PrettyTypeOf());
    type_proto->set_location_id(FindOrAppend(&interned_locations_, obj->AsClass()->GetLocation()));
    return class_ptr;
  }

  // Fills `*object_proto` with all the references held by `*obj` (an object of type `*klass`).
  void FillReferences(art::mirror::Object* obj,
                      art::mirror::Class* klass,
                      perfetto::protos::pbzero::HeapGraphObject* object_proto)
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    const uint32_t klass_flags = klass->GetClassFlags();
    const bool emit_field_ids = klass_flags != art::mirror::kClassFlagObjectArray &&
                                klass_flags != art::mirror::kClassFlagNormal &&
                                klass_flags != art::mirror::kClassFlagSoftReference &&
                                klass_flags != art::mirror::kClassFlagWeakReference &&
                                klass_flags != art::mirror::kClassFlagFinalizerReference &&
                                klass_flags != art::mirror::kClassFlagPhantomReference;
    std::vector<std::pair<std::string, art::mirror::Object*>> referred_objects =
        GetReferences(obj, klass, emit_field_ids);

    art::mirror::Object* min_nonnull_ptr = FilterIgnoredReferencesAndFindMin(referred_objects);

    uint64_t base_obj_id = EncodeBaseObjId(referred_objects, min_nonnull_ptr);

    for (const auto& p : referred_objects) {
      const std::string& field_name = p.first;
      art::mirror::Object* referred_obj = p.second;
      if (emit_field_ids) {
        reference_field_ids_->Append(FindOrAppend(&interned_fields_, field_name));
      }
      uint64_t referred_obj_id = GetObjectId(referred_obj);
      if (referred_obj_id) {
        referred_obj_id -= base_obj_id;
      }
      reference_object_ids_->Append(referred_obj_id);
    }
    if (emit_field_ids) {
      object_proto->set_reference_field_id(*reference_field_ids_);
      reference_field_ids_->Reset();
    }
    if (base_obj_id) {
      // The field is called `reference_field_id_base`, but it has always been used as a base for
      // `reference_object_id`. It should be called `reference_object_id_base`.
      object_proto->set_reference_field_id_base(base_obj_id);
    }
    object_proto->set_reference_object_id(*reference_object_ids_);
    reference_object_ids_->Reset();
  }

  // Iterates all the `referred_objects` and sets all the objects that are supposed to be ignored
  // to nullptr. Returns the object with the smallest address (ignoring nullptr).
  art::mirror::Object* FilterIgnoredReferencesAndFindMin(
      std::vector<std::pair<std::string, art::mirror::Object*>>& referred_objects) const
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    art::mirror::Object* min_nonnull_ptr = nullptr;
    for (auto& p : referred_objects) {
      art::mirror::Object*& referred_obj = p.second;
      if (referred_obj == nullptr)
        continue;
      if (IsIgnored(referred_obj)) {
        referred_obj = nullptr;
        continue;
      }
      if (min_nonnull_ptr == nullptr || min_nonnull_ptr > referred_obj) {
        min_nonnull_ptr = referred_obj;
      }
    }
    return min_nonnull_ptr;
  }

  // Fills `*object_proto` with the value of a subset of potentially interesting fields of `*obj`
  // (an object of type `*klass`).
  void FillFieldValues(art::mirror::Object* obj,
                       art::mirror::Class* klass,
                       perfetto::protos::pbzero::HeapGraphObject* object_proto) const
      REQUIRES_SHARED(art::Locks::mutator_lock_) {
    if (obj->IsClass() || klass->IsClassClass()) {
      return;
    }

    for (art::mirror::Class* cls = klass; cls != nullptr; cls = cls->GetSuperClass().Ptr()) {
      if (cls->IsArrayClass()) {
        continue;
      }

      if (cls->DescriptorEquals("Llibcore/util/NativeAllocationRegistry;")) {
        art::ArtField* af = cls->FindDeclaredInstanceField(
            "size", art::Primitive::Descriptor(art::Primitive::kPrimLong));
        if (af) {
          object_proto->set_native_allocation_registry_size_field(af->GetLong(obj));
        }
      }
    }
  }

  // Returns true if `*obj` has a type that's supposed to be ignored.
  bool IsIgnored(art::mirror::Object* obj) const REQUIRES_SHARED(art::Locks::mutator_lock_) {
    if (obj->IsClass()) {
      return false;
    }
    art::mirror::Class* klass = obj->GetClass();
    std::string temp;
    std::string_view name(klass->GetDescriptor(&temp));
    return std::find(ignored_types_.begin(), ignored_types_.end(), name) != ignored_types_.end();
  }

  // Name of classes whose instances should be ignored.
  const std::vector<std::string> ignored_types_;

  // Make sure that intern ID 0 (default proto value for a uint64_t) always maps to ""
  // (default proto value for a string) or to 0 (default proto value for a uint64).

  // Map from string (the field name) to its index in perfetto.protos.HeapGraph.field_names
  std::map<std::string, uint64_t> interned_fields_{{"", 0}};
  // Map from string (the location name) to its index in perfetto.protos.HeapGraph.location_names
  std::map<std::string, uint64_t> interned_locations_{{"", 0}};
  // Map from addr (the class pointer) to its id in perfetto.protos.HeapGraph.types
  std::map<uintptr_t, uint64_t> interned_classes_{{0, 0}};

  // Temporary buffers: used locally in some methods and then cleared.
  std::unique_ptr<protozero::PackedVarInt> reference_field_ids_;
  std::unique_ptr<protozero::PackedVarInt> reference_object_ids_;

  // Id of the previous object that was dumped. Used for delta encoding.
  uint64_t prev_object_id_ = 0;
};

// waitpid with a timeout implemented by ~busy-waiting
// See b/181031512 for rationale.
void BusyWaitpid(pid_t pid, uint32_t timeout_ms) {
  for (size_t i = 0;; ++i) {
    if (i == timeout_ms) {
      // The child hasn't exited.
      // Give up and SIGKILL it. The next waitpid should succeed.
      LOG(ERROR) << "perfetto_hprof child timed out. Sending SIGKILL.";
      kill(pid, SIGKILL);
    }
    int stat_loc;
    pid_t wait_result = waitpid(pid, &stat_loc, WNOHANG);
    if (wait_result == -1 && errno != EINTR) {
      if (errno != ECHILD) {
        // This hopefully never happens (should only be EINVAL).
        PLOG(FATAL_WITHOUT_ABORT) << "waitpid";
      }
      // If we get ECHILD, the parent process was handling SIGCHLD, or did a wildcard wait.
      // The child is no longer here either way, so that's good enough for us.
      break;
    } else if (wait_result > 0) {
      break;
    } else {  // wait_result == 0 || errno == EINTR.
      usleep(1000);
    }
  }
}

enum class ResumeParentPolicy {
  IMMEDIATELY,
  DEFERRED
};

void ForkAndRun(art::Thread* self,
                ResumeParentPolicy resume_parent_policy,
                const std::function<void(pid_t child)>& parent_runnable,
                const std::function<void(pid_t parent, uint64_t timestamp)>& child_runnable) {
  pid_t parent_pid = getpid();
  LOG(INFO) << "forking for " << parent_pid;
  // Need to take a heap dump while GC isn't running. See the comment in
  // Heap::VisitObjects(). Also we need the critical section to avoid visiting
  // the same object twice. See b/34967844.
  //
  // We need to do this before the fork, because otherwise it can deadlock
  // waiting for the GC, as all other threads get terminated by the clone, but
  // their locks are not released.
  // This does not perfectly solve all fork-related issues, as there could still be threads that
  // are unaffected by ScopedSuspendAll and in a non-fork-friendly situation
  // (e.g. inside a malloc holding a lock). This situation is quite rare, and in that case we will
  // hit the watchdog in the grand-child process if it gets stuck.
  std::optional<art::gc::ScopedGCCriticalSection> gcs(std::in_place, self, art::gc::kGcCauseHprof,
                                                      art::gc::kCollectorTypeHprof);

  std::optional<art::ScopedSuspendAll> ssa(std::in_place, __FUNCTION__, /* long_suspend=*/ true);

  pid_t pid = fork();
  if (pid == -1) {
    // Fork error.
    PLOG(ERROR) << "fork";
    return;
  }
  if (pid != 0) {
    // Parent
    if (resume_parent_policy == ResumeParentPolicy::IMMEDIATELY) {
      // Stop the thread suspension as soon as possible to allow the rest of the application to
      // continue while we waitpid here.
      ssa.reset();
      gcs.reset();
    }
    parent_runnable(pid);
    if (resume_parent_policy != ResumeParentPolicy::IMMEDIATELY) {
      ssa.reset();
      gcs.reset();
    }
    return;
  }
  // The following code is only executed by the child of the original process.
  // Uninstall signal handler, so we don't trigger a profile on it.
  if (sigaction(kJavaHeapprofdSignal, &g_orig_act, nullptr) != 0) {
    close(g_signal_pipe_fds[0]);
    close(g_signal_pipe_fds[1]);
    PLOG(FATAL) << "Failed to sigaction";
    return;
  }

  uint64_t ts = GetCurrentBootClockNs();
  child_runnable(parent_pid, ts);
  // Prevent the `atexit` handlers from running. We do not want to call cleanup
  // functions the parent process has registered.
  art::FastExit(0);
}

void WriteHeapPackets(pid_t parent_pid, uint64_t timestamp) {
  JavaHprofDataSource::Trace(
      [parent_pid, timestamp](JavaHprofDataSource::TraceContext ctx)
          NO_THREAD_SAFETY_ANALYSIS {
            bool dump_smaps;
            std::vector<std::string> ignored_types;
            {
              auto ds = ctx.GetDataSourceLocked();
              if (!ds || !ds->enabled()) {
                if (ds) ds->Finish();
                LOG(INFO) << "skipping irrelevant data source.";
                return;
              }
              dump_smaps = ds->dump_smaps();
              ignored_types = ds->ignored_types();
            }
            LOG(INFO) << "dumping heap for " << parent_pid;
            if (dump_smaps) {
              DumpSmaps(&ctx);
            }
            Writer writer(parent_pid, &ctx, timestamp);
            HeapGraphDumper dumper(ignored_types);

            dumper.Dump(art::Runtime::Current(), writer);

            writer.Finalize();
            ctx.Flush([] {
              art::MutexLock lk(JavaHprofDataSource::art_thread(), GetStateMutex());
              g_state = State::kEnd;
              GetStateCV().Broadcast(JavaHprofDataSource::art_thread());
            });
            // Wait for the Flush that will happen on the Perfetto thread.
            {
              art::MutexLock lk(JavaHprofDataSource::art_thread(), GetStateMutex());
              while (g_state != State::kEnd) {
                GetStateCV().Wait(JavaHprofDataSource::art_thread());
              }
            }
            {
              auto ds = ctx.GetDataSourceLocked();
              if (ds) {
                ds->Finish();
              } else {
                LOG(ERROR) << "datasource timed out (duration_ms + datasource_stop_timeout_ms) "
                              "before dump finished";
              }
            }
          });
}

void DumpPerfetto(art::Thread* self) {
  ForkAndRun(
    self,
    ResumeParentPolicy::IMMEDIATELY,
    // parent thread
    [](pid_t child) {
      // Busy waiting here will introduce some extra latency, but that is okay because we have
      // already unsuspended all other threads. This runs on the perfetto_hprof_listener, which
      // is not needed for progress of the app itself.
      // We daemonize the child process, so effectively we only need to wait
      // for it to fork and exit.
      BusyWaitpid(child, 1000);
    },
    // child thread
    [self](pid_t dumped_pid, uint64_t timestamp) {
      // Daemon creates a new process that is the grand-child of the original process, and exits.
      if (daemon(0, 0) == -1) {
        PLOG(FATAL) << "daemon";
      }
      // The following code is only executed by the grand-child of the original process.

      // Make sure that this is the first thing we do after forking, so if anything
      // below hangs, the fork will go away from the watchdog.
      ArmWatchdogOrDie();
      SetupDataSource("android.java_hprof", false);
      WaitForDataSource(self);
      WriteHeapPackets(dumped_pid, timestamp);
      LOG(INFO) << "finished dumping heap for " << dumped_pid;
    });
}

void DumpPerfettoOutOfMemory() REQUIRES_SHARED(art::Locks::mutator_lock_) {
  art::Thread* self = art::Thread::Current();
  if (!self) {
    LOG(FATAL_WITHOUT_ABORT) << "no thread in DumpPerfettoOutOfMemory";
    return;
  }

  // Ensure that there is an active, armed tracing session
  uint32_t session_cnt =
      android::base::GetUintProperty<uint32_t>("traced.oome_heap_session.count", 0);
  if (session_cnt == 0) {
    return;
  }
  {
    // OutOfMemoryErrors are reentrant, make sure we do not fork and process
    // more than once.
    art::MutexLock lk(self, GetStateMutex());
    if (g_oome_triggered) {
      return;
    }
    g_oome_triggered = true;
    g_oome_sessions_pending = session_cnt;
  }

  art::ScopedThreadSuspension sts(self, art::ThreadState::kSuspended);
  // If we fork & resume the original process execution it will most likely exit
  // ~immediately due to the OOME error thrown. When the system detects that
  // that, it will cleanup by killing all processes in the cgroup (including
  // the process we just forked).
  // We need to avoid the race between the heap dump and the process group
  // cleanup, and the only way to do this is to avoid resuming the original
  // process until the heap dump is complete.
  // Given we are already about to crash anyway, the diagnostic data we get
  // outweighs the cost of introducing some latency.
  ForkAndRun(
    self,
    ResumeParentPolicy::DEFERRED,
    // parent process
    [](pid_t child) {
      // waitpid to reap the zombie
      // we are explicitly waiting for the child to exit
      // The reason for the timeout on top of the watchdog is that it is
      // possible (albeit unlikely) that even the watchdog will fail to be
      // activated in the case of an atfork handler.
      BusyWaitpid(child, kWatchdogTimeoutSec * 1000);
    },
    // child process
    [self](pid_t dumped_pid, uint64_t timestamp) {
      ArmWatchdogOrDie();
      art::ScopedTrace trace("perfetto_hprof oome");
      SetupDataSource("android.java_hprof.oom", true);
      perfetto::Tracing::ActivateTriggers({"com.android.telemetry.art-outofmemory"}, 500);

      // A pre-armed tracing session might not exist, so we should wait for a
      // limited amount of time before we decide to let the execution continue.
      if (!TimedWaitForDataSource(self, 1000)) {
        LOG(INFO) << "OOME hprof timeout (state " << g_state << ")";
        return;
      }
      WriteHeapPackets(dumped_pid, timestamp);
      LOG(INFO) << "OOME hprof complete for " << dumped_pid;
    });
}

// The plugin initialization function.
extern "C" bool ArtPlugin_Initialize() {
  if (art::Runtime::Current() == nullptr) {
    return false;
  }
  art::Thread* self = art::Thread::Current();
  {
    art::MutexLock lk(self, GetStateMutex());
    if (g_state != State::kUninitialized) {
      LOG(ERROR) << "perfetto_hprof already initialized. state: " << g_state;
      return false;
    }
    g_state = State::kWaitForListener;
  }

  if (pipe2(g_signal_pipe_fds, O_CLOEXEC) == -1) {
    PLOG(ERROR) << "Failed to pipe";
    return false;
  }

  struct sigaction act = {};
  act.sa_flags = SA_SIGINFO | SA_RESTART;
  act.sa_sigaction = [](int, siginfo_t* si, void*) {
    requested_tracing_session_id = si->si_value.sival_int;
    if (write(g_signal_pipe_fds[1], kByte, sizeof(kByte)) == -1) {
      PLOG(ERROR) << "Failed to trigger heap dump";
    }
  };

  // TODO(fmayer): We can probably use the SignalCatcher thread here to not
  // have an idle thread.
  if (sigaction(kJavaHeapprofdSignal, &act, &g_orig_act) != 0) {
    close(g_signal_pipe_fds[0]);
    close(g_signal_pipe_fds[1]);
    PLOG(ERROR) << "Failed to sigaction";
    return false;
  }

  std::thread th([] {
    art::Runtime* runtime = art::Runtime::Current();
    if (!runtime) {
      LOG(FATAL_WITHOUT_ABORT) << "no runtime in perfetto_hprof_listener";
      return;
    }
    if (!runtime->AttachCurrentThread("perfetto_hprof_listener", /*as_daemon=*/ true,
                                      runtime->GetSystemThreadGroup(), /*create_peer=*/ false)) {
      LOG(ERROR) << "failed to attach thread.";
      {
        art::MutexLock lk(nullptr, GetStateMutex());
        g_state = State::kUninitialized;
        GetStateCV().Broadcast(nullptr);
      }

      return;
    }
    art::Thread* self = art::Thread::Current();
    if (!self) {
      LOG(FATAL_WITHOUT_ABORT) << "no thread in perfetto_hprof_listener";
      return;
    }
    {
      art::MutexLock lk(self, GetStateMutex());
      if (g_state == State::kWaitForListener) {
        g_state = State::kWaitForStart;
        GetStateCV().Broadcast(self);
      }
    }
    char buf[1];
    for (;;) {
      int res;
      do {
        res = read(g_signal_pipe_fds[0], buf, sizeof(buf));
      } while (res == -1 && errno == EINTR);

      if (res <= 0) {
        if (res == -1) {
          PLOG(ERROR) << "failed to read";
        }
        close(g_signal_pipe_fds[0]);
        return;
      }

      perfetto_hprof::DumpPerfetto(self);
    }
  });
  th.detach();

  // Register the OOM error handler.
  art::Runtime::Current()->SetOutOfMemoryErrorHook(perfetto_hprof::DumpPerfettoOutOfMemory);

  return true;
}

extern "C" bool ArtPlugin_Deinitialize() {
  art::Runtime::Current()->SetOutOfMemoryErrorHook(nullptr);

  if (sigaction(kJavaHeapprofdSignal, &g_orig_act, nullptr) != 0) {
    PLOG(ERROR) << "failed to reset signal handler";
    // We cannot close the pipe if the signal handler wasn't unregistered,
    // to avoid receiving SIGPIPE.
    return false;
  }
  close(g_signal_pipe_fds[1]);

  art::Thread* self = art::Thread::Current();
  art::MutexLock lk(self, GetStateMutex());
  // Wait until after the thread was registered to the runtime. This is so
  // we do not attempt to register it with the runtime after it had been torn
  // down (ArtPlugin_Deinitialize gets called in the Runtime dtor).
  while (g_state == State::kWaitForListener) {
    GetStateCV().Wait(art::Thread::Current());
  }
  g_state = State::kUninitialized;
  GetStateCV().Broadcast(self);
  return true;
}

}  // namespace perfetto_hprof

namespace perfetto {

PERFETTO_DEFINE_DATA_SOURCE_STATIC_MEMBERS(perfetto_hprof::JavaHprofDataSource);

}
