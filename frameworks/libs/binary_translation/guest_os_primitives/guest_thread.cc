/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/guest_os_primitives/guest_thread.h"

#include <sys/mman.h>  // mprotect

#if defined(__BIONIC__)
#include "private/bionic_constants.h"
#include "private/bionic_tls.h"
#endif

#include "berberis/base/checks.h"
#include "berberis/base/logging.h"
#include "berberis/base/mmap.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_addr.h"  // ToGuestAddr
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_stack.h"
#include "native_bridge_support/linker/static_tls_config.h"

#include "get_tls.h"

extern "C" void berberis_UnmapAndExit(void* ptr, size_t size, int status);

namespace berberis {

NativeBridgeStaticTlsConfig g_static_tls_config;

namespace {

const size_t kGuestThreadPageAlignedSize = AlignUpPageSize(sizeof(GuestThread));

}  // namespace

// static
GuestThread* GuestThread::Create() {
  // ATTENTION: GuestThread is aligned on 16, as fp registers in CPUState are aligned on 16, for
  // efficient handling with aligned SSE memory access instructions. Thus, avoid using 'new', as
  // it might not honor alignment! See b/64554026.
  //
  // ATTENTION: Bionic allocates thread internal data together with thread stack.
  // In case of user provided stack, thread internal data goes there.
  void* thread_storage = Mmap(kGuestThreadPageAlignedSize);
  if (thread_storage == MAP_FAILED) {
    return nullptr;
  }

  GuestThread* thread = new (thread_storage) GuestThread;
  CHECK(thread);

  thread->state_ = CreateThreadState();
  if (!thread->state_) {
    TRACE("failed to allocate thread state");
    Destroy(thread);
    return nullptr;
  }
  SetGuestThread(*thread->state_, thread);

  return thread;
}

// static
GuestThread* GuestThread::CreateClone(const GuestThread* parent) {
  GuestThread* thread = Create();
  if (thread == nullptr) {
    return nullptr;
  }

  // TODO(156271630): alloc host stack guard?
  thread->host_stack_ = MmapOrDie(GetStackSizeForTranslation());
  if (thread->host_stack_ == MAP_FAILED) {
    TRACE("failed to allocate host stack!");
    thread->host_stack_ = nullptr;
    Destroy(thread);
    return nullptr;
  }

  SetCPUState(*thread->state(), GetCPUState(*parent->state()));
  SetTlsAddr(*thread->state(), GetTlsAddr(*parent->state()));

  return thread;
}

// static
GuestThread* GuestThread::CreatePthread(void* stack, size_t stack_size, size_t guard_size) {
  GuestThread* thread = Create();
  if (thread == nullptr) {
    return nullptr;
  }

  if (!thread->AllocStack(stack, stack_size, guard_size)) {
    Destroy(thread);
    return nullptr;
  }

  SetStackRegister(GetCPUState(*thread->state()), thread->stack_top_);

  if (!thread->AllocShadowCallStack()) {
    Destroy(thread);
    return nullptr;
  }

  SetShadowCallStackPointer(GetCPUState(*thread->state()), thread->scs_base_);

  // Static TLS must be in an independent mapping, because on creation of main thread its config
  // is yet unknown. Loader sets main thread's static TLS explicitly later.
  if (!thread->AllocStaticTls()) {
    Destroy(thread);
    return nullptr;
  }

  return thread;
}

// static
void GuestThread::Destroy(GuestThread* thread) {
  CHECK(thread);
  // ATTENTION: Don't run guest code from here!
  if (ArePendingSignalsPresent(*thread->state_)) {
    TRACE("thread destroyed with pending signals, signals ignored!");
  }

  if (thread->host_stack_) {
    // This happens only on cleanup after failed creation.
    MunmapOrDie(thread->host_stack_, GetStackSizeForTranslation());
  }
  if (thread->mmap_size_) {
    MunmapOrDie(thread->stack_, thread->mmap_size_);
  }
#if defined(__BIONIC__)
  if (thread->static_tls_ != nullptr) {
    MunmapOrDie(thread->static_tls_, AlignUpPageSize(g_static_tls_config.size));
  }
  if (thread->scs_region_ != nullptr) {
    MunmapOrDie(thread->scs_region_, SCS_GUARD_REGION_SIZE);
  }
#endif  // defined(__BIONIC__)
  if (thread->state_) {
    DestroyThreadState(thread->state_);
  }
  MunmapOrDie(thread, kGuestThreadPageAlignedSize);
}

// static
void GuestThread::Exit(GuestThread* thread, int status) {
  // Destroy the thread without unmapping the host stack.
  void* host_stack = thread->host_stack_;
  thread->host_stack_ = nullptr;
  Destroy(thread);

  if (host_stack) {
    berberis_UnmapAndExit(host_stack, GetStackSizeForTranslation(), status);
  } else {
    syscall(__NR_exit, status);
  }
  LOG_ALWAYS_FATAL("thread didn't exit");
}

bool GuestThread::AllocStack(void* stack, size_t stack_size, size_t guard_size) {
  // Here is what bionic does, see bionic/pthread_create.cpp:
  //
  // For user-provided stack, it assumes guard_size is included in stack size.
  //
  // For new stack, it adds given guard and stack sizes to get actual stack size:
  //   |<- guard_size ->|<- stack_size -------------------->|
  //   | guard          | stack        | pthread_internal_t | tls | GUARD |
  //   |<- actual stack_size --------->|
  //   ^ stack_base                    ^ stack_top

  if (stack) {
    // User-provided stack.
    stack_ = nullptr;  // Do not unmap in Destroy!
    mmap_size_ = 0;
    guard_size_ = guard_size;
    stack_size_ = stack_size;
    stack_top_ = ToGuestAddr(stack) + stack_size_;
    return true;
  }

  guard_size_ = AlignUpPageSize(guard_size);
  mmap_size_ = guard_size_ + AlignUpPageSize(stack_size);
  stack_size_ = mmap_size_;

  stack_ = Mmap(mmap_size_);
  if (stack_ == MAP_FAILED) {
    TRACE("failed to allocate stack!");
    stack_ = nullptr;  // Do not unmap in Destroy!
    return false;
  }

  if (mprotect(stack_, guard_size_, PROT_NONE) != 0) {
    TRACE("failed to protect stack!");
    return false;
  }

  stack_top_ = ToGuestAddr(stack_) + stack_size_ - 16;
  return true;
}

bool GuestThread::AllocShadowCallStack() {
#if defined(__BIONIC__) && defined(BERBERIS_GUEST_LP64) && !defined(BERBERIS_GUEST_ARCH_X86_64)
  CHECK(IsAlignedPageSize(SCS_GUARD_REGION_SIZE));
  CHECK(IsAlignedPageSize(SCS_SIZE));

  scs_region_ = Mmap(SCS_GUARD_REGION_SIZE);
  if (scs_region_ == MAP_FAILED) {
    TRACE("failed to allocate shadow call stack!");
    scs_region_ = nullptr;  // do not unmap in Destroy!
    return false;
  }

  GuestAddr scs_region_base = ToGuestAddr(scs_region_);
  // TODO(b/138425729): use random offset!
  scs_base_ = AlignUp(scs_region_base, SCS_SIZE);
  GuestAddr scs_top = scs_base_ + SCS_SIZE;

  if (mprotect(scs_region_, scs_base_ - scs_region_base, PROT_NONE) != 0 ||
      mprotect(ToHostAddr<void>(scs_top),
               scs_region_base + SCS_GUARD_REGION_SIZE - scs_top,
               PROT_NONE) != 0) {
    TRACE("failed to protect shadow call stack!");
    return false;
  }
#endif  // defined(__BIONIC__) && defined(BERBERIS_GUEST_LP64) &&
        // !defined(BERBERIS_GUEST_ARCH_X86_64)
  return true;
}

bool GuestThread::AllocStaticTls() {
  // For the main thread, this function is called twice.

  CHECK_EQ(nullptr, static_tls_);

#if defined(__BIONIC__)
  if (g_static_tls_config.size > 0) {
    static_tls_ = Mmap(AlignUpPageSize(g_static_tls_config.size));
    if (static_tls_ == MAP_FAILED) {
      TRACE("failed to allocate static tls!");
      static_tls_ = nullptr;  // Do not unmap in Destroy!
      return false;
    }
  }
#endif  // defined(__BIONIC__)

  return true;
}

void GuestThread::InitStaticTls() {
#if defined(__BIONIC__)
  if (static_tls_ == nullptr) {
    // Leave the thread pointer unset when starting the main thread.
    return;
  }
  // First initialize static TLS using the initialization image, then update
  // some of the TLS slots. Reuse the host's pthread_internal_t and
  // bionic_tls objects. We verify that these structures are safe to use with
  // checks in berberis/android_api/libc/pthread_translation.h.
  memcpy(static_tls_, g_static_tls_config.init_img, g_static_tls_config.size);
  void** tls =
      reinterpret_cast<void**>(reinterpret_cast<char*>(static_tls_) + g_static_tls_config.tpoff);
  tls[g_static_tls_config.tls_slot_thread_id] = GetTls()[TLS_SLOT_THREAD_ID];
  tls[g_static_tls_config.tls_slot_bionic_tls] = GetTls()[TLS_SLOT_BIONIC_TLS];
  SetTlsAddr(*state_, ToGuestAddr(tls));
#else
  // For Glibc we provide stub which is only usable to distinguish different threads.
  // This is the only thing that many applications need.
  SetTlsAddr(*state_, GettidSyscall());
#endif
}

void GuestThread::ConfigStaticTls(const NativeBridgeStaticTlsConfig* config) {
  // This function is called during Bionic linker initialization, before any
  // guest constructor functions run. It should be safe to omit locking.
  g_static_tls_config = *config;

  // Reinitialize the main thread's static TLS.
  CHECK_EQ(true, AllocStaticTls());
  InitStaticTls();
}

void* GuestThread::GetHostStackTop() const {
  CHECK(host_stack_);
  auto top = reinterpret_cast<uintptr_t>(host_stack_) + GetStackSizeForTranslation();
  return reinterpret_cast<void*>(top);
}

}  // namespace berberis
