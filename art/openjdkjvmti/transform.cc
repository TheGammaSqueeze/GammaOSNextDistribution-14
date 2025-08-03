/* Copyright (C) 2016 The Android Open Source Project
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file implements interfaces from the file jvmti.h. This implementation
 * is licensed under the same terms as the file jvmti.h.  The
 * copyright and license information for the file jvmti.h follows.
 *
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "transform.h"

#include <stddef.h>
#include <sys/types.h>

#include <unordered_map>
#include <unordered_set>

#include "art_method.h"
#include "base/array_ref.h"
#include "base/globals.h"
#include "base/logging.h"
#include "base/mem_map.h"
#include "class_linker.h"
#include "dex/dex_file.h"
#include "dex/dex_file_types.h"
#include "dex/utf.h"
#include "events-inl.h"
#include "events.h"
#include "fault_handler.h"
#include "gc_root-inl.h"
#include "handle_scope-inl.h"
#include "jni/jni_env_ext-inl.h"
#include "jvalue.h"
#include "jvmti.h"
#include "linear_alloc.h"
#include "mirror/array.h"
#include "mirror/class-inl.h"
#include "mirror/class_ext.h"
#include "mirror/class_loader-inl.h"
#include "mirror/string-inl.h"
#include "oat/oat_file.h"
#include "scoped_thread_state_change-inl.h"
#include "stack.h"
#include "thread_list.h"
#include "ti_logging.h"
#include "ti_redefine.h"

namespace openjdkjvmti {

static EventHandler* gEventHandler = nullptr;

void Transformer::Register(EventHandler* eh) {
  gEventHandler = eh;
}

// Initialize templates.
template void Transformer::CallClassFileLoadHooksSingleClass<
    ArtJvmtiEvent::kClassFileLoadHookNonRetransformable>(EventHandler* event_handler,
                                                         art::Thread* self,
                                                         /*in-out*/ ArtClassDefinition* def);
template void Transformer::CallClassFileLoadHooksSingleClass<
    ArtJvmtiEvent::kClassFileLoadHookRetransformable>(EventHandler* event_handler,
                                                      art::Thread* self,
                                                      /*in-out*/ ArtClassDefinition* def);
template void Transformer::CallClassFileLoadHooksSingleClass<
    ArtJvmtiEvent::kStructuralDexFileLoadHook>(EventHandler* event_handler,
                                               art::Thread* self,
                                               /*in-out*/ ArtClassDefinition* def);

template <ArtJvmtiEvent kEvent>
void Transformer::CallClassFileLoadHooksSingleClass(EventHandler* event_handler,
                                                    art::Thread* self,
                                                    /*in-out*/ ArtClassDefinition* def) {
  static_assert(kEvent == ArtJvmtiEvent::kClassFileLoadHookNonRetransformable ||
                kEvent == ArtJvmtiEvent::kClassFileLoadHookRetransformable ||
                kEvent == ArtJvmtiEvent::kStructuralDexFileLoadHook,
                "bad event type");
  // We don't want to do transitions between calling the event and setting the new data so change to
  // native state early.
  art::ScopedThreadStateChange stsc(self, art::ThreadState::kNative);
  jint new_len = -1;
  unsigned char* new_data = nullptr;
  art::ArrayRef<const unsigned char> dex_data = def->GetDexData();
  event_handler->DispatchEvent<kEvent>(
      self,
      static_cast<JNIEnv*>(self->GetJniEnv()),
      def->GetClass(),
      def->GetLoader(),
      def->GetName().c_str(),
      def->GetProtectionDomain(),
      static_cast<jint>(dex_data.size()),
      dex_data.data(),
      /*out*/&new_len,
      /*out*/&new_data);
  def->SetNewDexData(new_len, new_data, kEvent);
}

template <RedefinitionType kType>
void Transformer::CallClassFileLoadHooks(art::Thread* self,
                                         /*in-out*/ std::vector<ArtClassDefinition>* definitions) {
  if (kType == RedefinitionType::kNormal) {
    // For normal redefinition we have to call ClassFileLoadHook according to the spec. We use an
    // internal event "ClassFileLoadHookRetransformable" for agents that can redefine and a
    // "ClassFileLoadHookNonRetransformable" for agents that cannot redefine. When an agent is
    // attached to a non-debuggable environment, we cannot redefine any classes. Splitting the
    // ClassFileLoadHooks allows us to differentiate between these two cases. This method is only
    // called when redefinition is allowed so just run ClassFileLoadHookRetransformable hooks.
    for (ArtClassDefinition& def : *definitions) {
      CallClassFileLoadHooksSingleClass<ArtJvmtiEvent::kClassFileLoadHookRetransformable>(
          gEventHandler, self, &def);
    }
  } else {
    // For structural redefinition we call StructualDexFileLoadHook in addition to the
    // ClassFileLoadHooks. This let's us specify if structural modifications are allowed.
    // TODO(mythria): The spec only specifies we need to call ClassFileLoadHooks, the
    // StructuralDexFileLoadHooks is internal to ART. It is not clear if we need to run all
    // StructuralDexFileHooks before ClassFileLoadHooks. Doing it this way to keep the existing
    // behaviour.
    for (ArtClassDefinition& def : *definitions) {
      CallClassFileLoadHooksSingleClass<ArtJvmtiEvent::kStructuralDexFileLoadHook>(
          gEventHandler, self, &def);
    }
    for (ArtClassDefinition& def : *definitions) {
      CallClassFileLoadHooksSingleClass<ArtJvmtiEvent::kClassFileLoadHookRetransformable>(
          gEventHandler, self, &def);
    }
  }
}

template void Transformer::CallClassFileLoadHooks<RedefinitionType::kNormal>(
    art::Thread* self, /*in-out*/ std::vector<ArtClassDefinition>* definitions);
template void Transformer::CallClassFileLoadHooks<RedefinitionType::kStructural>(
    art::Thread* self, /*in-out*/ std::vector<ArtClassDefinition>* definitions);

jvmtiError Transformer::RetransformClasses(jvmtiEnv* env,
                                           jint class_count,
                                           const jclass* classes) {
  if (class_count < 0) {
    JVMTI_LOG(WARNING, env) << "FAILURE TO RETRANSFORM class_count was less then 0";
    return ERR(ILLEGAL_ARGUMENT);
  } else if (class_count == 0) {
    // We don't actually need to do anything. Just return OK.
    return OK;
  } else if (classes == nullptr) {
    JVMTI_LOG(WARNING, env) << "FAILURE TO RETRANSFORM null classes!";
    return ERR(NULL_POINTER);
  }
  art::Thread* self = art::Thread::Current();
  art::Runtime* runtime = art::Runtime::Current();
  // A holder that will Deallocate all the class bytes buffers on destruction.
  std::string error_msg;
  std::vector<ArtClassDefinition> definitions;
  jvmtiError res = OK;
  for (jint i = 0; i < class_count; i++) {
    res = Redefiner::CanRedefineClass<RedefinitionType::kNormal>(classes[i], &error_msg);
    if (res != OK) {
      JVMTI_LOG(WARNING, env) << "FAILURE TO RETRANSFORM " << error_msg;
      return res;
    }
    ArtClassDefinition def;
    res = def.Init(self, classes[i]);
    if (res != OK) {
      JVMTI_LOG(WARNING, env) << "FAILURE TO RETRANSFORM definition init failed";
      return res;
    }
    definitions.push_back(std::move(def));
  }

  CallClassFileLoadHooks<RedefinitionType::kStructural>(self, &definitions);
  RedefinitionType redef_type =
      std::any_of(definitions.cbegin(),
                  definitions.cend(),
                  [](const auto& it) { return it.HasStructuralChanges(); })
          ? RedefinitionType::kStructural
          : RedefinitionType::kNormal;
  res = Redefiner::RedefineClassesDirect(
      ArtJvmTiEnv::AsArtJvmTiEnv(env), runtime, self, definitions, redef_type, &error_msg);
  if (res != OK) {
    JVMTI_LOG(WARNING, env) << "FAILURE TO RETRANSFORM " << error_msg;
  }
  return res;
}

// TODO Move this somewhere else, ti_class?
jvmtiError GetClassLocation(ArtJvmTiEnv* env, jclass klass, /*out*/std::string* location) {
  JNIEnv* jni_env = nullptr;
  jint ret = env->art_vm->GetEnv(reinterpret_cast<void**>(&jni_env), JNI_VERSION_1_1);
  if (ret != JNI_OK) {
    // TODO Different error might be better?
    return ERR(INTERNAL);
  }
  art::ScopedObjectAccess soa(jni_env);
  art::StackHandleScope<1> hs(art::Thread::Current());
  art::Handle<art::mirror::Class> hs_klass(hs.NewHandle(soa.Decode<art::mirror::Class>(klass)));
  const art::DexFile& dex = hs_klass->GetDexFile();
  *location = dex.GetLocation();
  return OK;
}

}  // namespace openjdkjvmti
