/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "well_known_classes.h"

#include <stdlib.h>

#include <sstream>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include "art_method-inl.h"
#include "base/casts.h"
#include "base/enums.h"
#include "class_linker.h"
#include "class_root-inl.h"
#include "entrypoints/quick/quick_entrypoints_enum.h"
#include "handle_scope-inl.h"
#include "hidden_api.h"
#include "jni/java_vm_ext.h"
#include "jni/jni_internal.h"
#include "jni_id_type.h"
#include "mirror/class.h"
#include "mirror/throwable.h"
#include "nativehelper/scoped_local_ref.h"
#include "obj_ptr-inl.h"
#include "runtime.h"
#include "scoped_thread_state_change-inl.h"
#include "scoped_thread_state_change.h"
#include "thread-current-inl.h"

namespace art HIDDEN {

jclass WellKnownClasses::dalvik_annotation_optimization_CriticalNative;
jclass WellKnownClasses::dalvik_annotation_optimization_FastNative;
jclass WellKnownClasses::dalvik_annotation_optimization_NeverCompile;
jclass WellKnownClasses::dalvik_annotation_optimization_NeverInline;
jclass WellKnownClasses::java_lang_annotation_Annotation__array;
jclass WellKnownClasses::java_lang_ClassValue;
jclass WellKnownClasses::java_lang_Record;
jclass WellKnownClasses::java_lang_reflect_Parameter__array;
jclass WellKnownClasses::java_lang_StringFactory;
jclass WellKnownClasses::java_lang_System;
jclass WellKnownClasses::java_lang_Void;
jclass WellKnownClasses::libcore_reflect_AnnotationMember__array;

ArtMethod* WellKnownClasses::dalvik_system_BaseDexClassLoader_getLdLibraryPath;
ArtMethod* WellKnownClasses::dalvik_system_DelegateLastClassLoader_init;
ArtMethod* WellKnownClasses::dalvik_system_DexClassLoader_init;
ArtMethod* WellKnownClasses::dalvik_system_InMemoryDexClassLoader_init;
ArtMethod* WellKnownClasses::dalvik_system_PathClassLoader_init;
ArtMethod* WellKnownClasses::dalvik_system_VMRuntime_hiddenApiUsed;
ArtMethod* WellKnownClasses::java_lang_Boolean_valueOf;
ArtMethod* WellKnownClasses::java_lang_BootClassLoader_init;
ArtMethod* WellKnownClasses::java_lang_Byte_valueOf;
ArtMethod* WellKnownClasses::java_lang_Character_valueOf;
ArtMethod* WellKnownClasses::java_lang_ClassLoader_loadClass;
ArtMethod* WellKnownClasses::java_lang_ClassNotFoundException_init;
ArtMethod* WellKnownClasses::java_lang_Daemons_start;
ArtMethod* WellKnownClasses::java_lang_Daemons_stop;
ArtMethod* WellKnownClasses::java_lang_Daemons_waitForDaemonStart;
ArtMethod* WellKnownClasses::java_lang_Double_doubleToRawLongBits;
ArtMethod* WellKnownClasses::java_lang_Double_valueOf;
ArtMethod* WellKnownClasses::java_lang_Error_init;
ArtMethod* WellKnownClasses::java_lang_Float_floatToRawIntBits;
ArtMethod* WellKnownClasses::java_lang_Float_valueOf;
ArtMethod* WellKnownClasses::java_lang_IllegalAccessError_init;
ArtMethod* WellKnownClasses::java_lang_Integer_valueOf;
ArtMethod* WellKnownClasses::java_lang_Long_valueOf;
ArtMethod* WellKnownClasses::java_lang_NoClassDefFoundError_init;
ArtMethod* WellKnownClasses::java_lang_OutOfMemoryError_init;
ArtMethod* WellKnownClasses::java_lang_Runtime_nativeLoad;
ArtMethod* WellKnownClasses::java_lang_RuntimeException_init;
ArtMethod* WellKnownClasses::java_lang_Short_valueOf;
ArtMethod* WellKnownClasses::java_lang_StackOverflowError_init;
ArtMethod* WellKnownClasses::java_lang_String_charAt;
ArtMethod* WellKnownClasses::java_lang_Thread_dispatchUncaughtException;
ArtMethod* WellKnownClasses::java_lang_Thread_init;
ArtMethod* WellKnownClasses::java_lang_Thread_run;
ArtMethod* WellKnownClasses::java_lang_ThreadGroup_add;
ArtMethod* WellKnownClasses::java_lang_ThreadGroup_threadTerminated;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodHandle_asType;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodHandle_invokeExact;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodHandles_lookup;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodHandles_makeIdentity;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodHandles_Lookup_findConstructor;
ArtMethod* WellKnownClasses::java_lang_invoke_MethodType_makeImpl;
ArtMethod* WellKnownClasses::java_lang_ref_FinalizerReference_add;
ArtMethod* WellKnownClasses::java_lang_ref_ReferenceQueue_add;
ArtMethod* WellKnownClasses::java_lang_reflect_InvocationTargetException_init;
ArtMethod* WellKnownClasses::java_lang_reflect_Parameter_init;
ArtMethod* WellKnownClasses::java_lang_reflect_Proxy_init;
ArtMethod* WellKnownClasses::java_lang_reflect_Proxy_invoke;
ArtMethod* WellKnownClasses::java_nio_Buffer_isDirect;
ArtMethod* WellKnownClasses::java_nio_DirectByteBuffer_init;
ArtMethod* WellKnownClasses::java_util_function_Consumer_accept;
ArtMethod* WellKnownClasses::jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_D;
ArtMethod* WellKnownClasses::jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_F;
ArtMethod* WellKnownClasses::jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_getChars;
ArtMethod* WellKnownClasses::libcore_reflect_AnnotationFactory_createAnnotation;
ArtMethod* WellKnownClasses::libcore_reflect_AnnotationMember_init;
ArtMethod* WellKnownClasses::org_apache_harmony_dalvik_ddmc_DdmServer_broadcast;
ArtMethod* WellKnownClasses::org_apache_harmony_dalvik_ddmc_DdmServer_dispatch;

ArtField* WellKnownClasses::dalvik_system_BaseDexClassLoader_pathList;
ArtField* WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoaders;
ArtField* WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoadersAfter;
ArtField* WellKnownClasses::dalvik_system_DexFile_cookie;
ArtField* WellKnownClasses::dalvik_system_DexFile_fileName;
ArtField* WellKnownClasses::dalvik_system_DexPathList_dexElements;
ArtField* WellKnownClasses::dalvik_system_DexPathList__Element_dexFile;
ArtField* WellKnownClasses::dalvik_system_VMRuntime_nonSdkApiUsageConsumer;
ArtField* WellKnownClasses::java_io_FileDescriptor_descriptor;
ArtField* WellKnownClasses::java_lang_ClassLoader_parent;
ArtField* WellKnownClasses::java_lang_String_EMPTY;
ArtField* WellKnownClasses::java_lang_Thread_parkBlocker;
ArtField* WellKnownClasses::java_lang_Thread_daemon;
ArtField* WellKnownClasses::java_lang_Thread_group;
ArtField* WellKnownClasses::java_lang_Thread_lock;
ArtField* WellKnownClasses::java_lang_Thread_name;
ArtField* WellKnownClasses::java_lang_Thread_priority;
ArtField* WellKnownClasses::java_lang_Thread_nativePeer;
ArtField* WellKnownClasses::java_lang_Thread_systemDaemon;
ArtField* WellKnownClasses::java_lang_Thread_unparkedBeforeStart;
ArtField* WellKnownClasses::java_lang_ThreadGroup_groups;
ArtField* WellKnownClasses::java_lang_ThreadGroup_ngroups;
ArtField* WellKnownClasses::java_lang_ThreadGroup_mainThreadGroup;
ArtField* WellKnownClasses::java_lang_ThreadGroup_name;
ArtField* WellKnownClasses::java_lang_ThreadGroup_parent;
ArtField* WellKnownClasses::java_lang_ThreadGroup_systemThreadGroup;
ArtField* WellKnownClasses::java_lang_Throwable_cause;
ArtField* WellKnownClasses::java_lang_Throwable_detailMessage;
ArtField* WellKnownClasses::java_lang_Throwable_stackTrace;
ArtField* WellKnownClasses::java_lang_Throwable_stackState;
ArtField* WellKnownClasses::java_lang_Throwable_suppressedExceptions;
ArtField* WellKnownClasses::java_nio_Buffer_address;
ArtField* WellKnownClasses::java_nio_Buffer_capacity;
ArtField* WellKnownClasses::java_nio_Buffer_elementSizeShift;
ArtField* WellKnownClasses::java_nio_Buffer_limit;
ArtField* WellKnownClasses::java_nio_Buffer_position;
ArtField* WellKnownClasses::java_nio_ByteBuffer_hb;
ArtField* WellKnownClasses::java_nio_ByteBuffer_isReadOnly;
ArtField* WellKnownClasses::java_nio_ByteBuffer_offset;
ArtField* WellKnownClasses::java_util_Collections_EMPTY_LIST;
ArtField* WellKnownClasses::java_util_concurrent_ThreadLocalRandom_seeder;
ArtField* WellKnownClasses::jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_buffer;
ArtField* WellKnownClasses::jdk_internal_math_FloatingDecimal_ExceptionalBinaryToASCIIBuffer_image;
ArtField* WellKnownClasses::libcore_util_EmptyArray_STACK_TRACE_ELEMENT;
ArtField* WellKnownClasses::org_apache_harmony_dalvik_ddmc_Chunk_data;
ArtField* WellKnownClasses::org_apache_harmony_dalvik_ddmc_Chunk_length;
ArtField* WellKnownClasses::org_apache_harmony_dalvik_ddmc_Chunk_offset;
ArtField* WellKnownClasses::org_apache_harmony_dalvik_ddmc_Chunk_type;

ArtField* WellKnownClasses::java_lang_Byte_ByteCache_cache;
ArtField* WellKnownClasses::java_lang_Character_CharacterCache_cache;
ArtField* WellKnownClasses::java_lang_Short_ShortCache_cache;
ArtField* WellKnownClasses::java_lang_Integer_IntegerCache_cache;
ArtField* WellKnownClasses::java_lang_Long_LongCache_cache;

ArtField* WellKnownClasses::java_lang_Byte_value;
ArtField* WellKnownClasses::java_lang_Character_value;
ArtField* WellKnownClasses::java_lang_Short_value;
ArtField* WellKnownClasses::java_lang_Integer_value;
ArtField* WellKnownClasses::java_lang_Long_value;

static ObjPtr<mirror::Class> FindSystemClass(ClassLinker* class_linker,
                                             Thread* self,
                                             const char* descriptor)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> klass = class_linker->FindSystemClass(self, descriptor);
  CHECK(klass != nullptr) << "Couldn't find system class: " << descriptor;
  return klass;
}

static jclass CacheClass(JNIEnv* env, const char* jni_class_name) {
  ScopedLocalRef<jclass> c(env, env->FindClass(jni_class_name));
  if (c.get() == nullptr) {
    LOG(FATAL) << "Couldn't find class: " << jni_class_name;
  }
  return reinterpret_cast<jclass>(env->NewGlobalRef(c.get()));
}

static ArtField* CacheField(ObjPtr<mirror::Class> klass,
                            bool is_static,
                            const char* name,
                            const char* signature) REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtField* field = is_static
      ? klass->FindDeclaredStaticField(name, signature)
      : klass->FindDeclaredInstanceField(name, signature);
  if (UNLIKELY(field == nullptr)) {
    std::ostringstream os;
    klass->DumpClass(os, mirror::Class::kDumpClassFullDetail);
    LOG(FATAL) << "Couldn't find " << (is_static ? "static" : "instance") << " field \""
               << name << "\" with signature \"" << signature << "\": " << os.str();
    UNREACHABLE();
  }
  return field;
}

static ArtMethod* CacheMethod(ObjPtr<mirror::Class> klass,
                              bool is_static,
                              const char* name,
                              const char* signature,
                              PointerSize pointer_size) REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtMethod* method = klass->IsInterface()
      ? klass->FindInterfaceMethod(name, signature, pointer_size)
      : klass->FindClassMethod(name, signature, pointer_size);
  if (UNLIKELY(method == nullptr) || UNLIKELY(is_static != method->IsStatic())) {
    std::ostringstream os;
    klass->DumpClass(os, mirror::Class::kDumpClassFullDetail);
    LOG(FATAL) << "Couldn't find " << (is_static ? "static" : "instance") << " method \""
               << name << "\" with signature \"" << signature << "\": " << os.str();
    UNREACHABLE();
  }
  DCHECK(method->GetDeclaringClass() == klass);
  return method;
}

static ArtMethod* CachePrimitiveBoxingMethod(ClassLinker* class_linker,
                                             Thread* self,
                                             char prim_name,
                                             const char* boxed_name)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> boxed_class = FindSystemClass(class_linker, self, boxed_name);
  PointerSize pointer_size = class_linker->GetImagePointerSize();
  std::string signature = android::base::StringPrintf("(%c)%s", prim_name, boxed_name);
  return CacheMethod(boxed_class, /*is_static=*/ true, "valueOf", signature.c_str(), pointer_size);
}

static ArtField* CacheBoxingCacheField(ClassLinker* class_linker,
                                       Thread* self,
                                       const char* class_name,
                                       const char* cache_type)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> boxed_class = FindSystemClass(class_linker, self, class_name);
  return CacheField(boxed_class, /*is_static=*/ true, "cache", cache_type);
}

static ArtField* CacheValueInBoxField(ClassLinker* class_linker,
                                      Thread* self,
                                      const char* class_name,
                                      const char* cache_type)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> boxed_class = FindSystemClass(class_linker, self, class_name);
  return CacheField(boxed_class, /*is_static=*/ false, "value", cache_type);
}

#define STRING_INIT_LIST(V) \
  V(java_lang_String_init, "()V", newEmptyString, "newEmptyString", "()Ljava/lang/String;", NewEmptyString) \
  V(java_lang_String_init_B, "([B)V", newStringFromBytes_B, "newStringFromBytes", "([B)Ljava/lang/String;", NewStringFromBytes_B) \
  V(java_lang_String_init_BB, "([BB)V", newStringFromBytes_BB, "newStringFromBytes", "([BB)Ljava/lang/String;", NewStringFromBytes_BB) \
  V(java_lang_String_init_BI, "([BI)V", newStringFromBytes_BI, "newStringFromBytes", "([BI)Ljava/lang/String;", NewStringFromBytes_BI) \
  V(java_lang_String_init_BII, "([BII)V", newStringFromBytes_BII, "newStringFromBytes", "([BII)Ljava/lang/String;", NewStringFromBytes_BII) \
  V(java_lang_String_init_BIII, "([BIII)V", newStringFromBytes_BIII, "newStringFromBytes", "([BIII)Ljava/lang/String;", NewStringFromBytes_BIII) \
  V(java_lang_String_init_BIIString, "([BIILjava/lang/String;)V", newStringFromBytes_BIIString, "newStringFromBytes", "([BIILjava/lang/String;)Ljava/lang/String;", NewStringFromBytes_BIIString) \
  V(java_lang_String_init_BString, "([BLjava/lang/String;)V", newStringFromBytes_BString, "newStringFromBytes", "([BLjava/lang/String;)Ljava/lang/String;", NewStringFromBytes_BString) \
  V(java_lang_String_init_BIICharset, "([BIILjava/nio/charset/Charset;)V", newStringFromBytes_BIICharset, "newStringFromBytes", "([BIILjava/nio/charset/Charset;)Ljava/lang/String;", NewStringFromBytes_BIICharset) \
  V(java_lang_String_init_BCharset, "([BLjava/nio/charset/Charset;)V", newStringFromBytes_BCharset, "newStringFromBytes", "([BLjava/nio/charset/Charset;)Ljava/lang/String;", NewStringFromBytes_BCharset) \
  V(java_lang_String_init_C, "([C)V", newStringFromChars_C, "newStringFromChars", "([C)Ljava/lang/String;", NewStringFromChars_C) \
  V(java_lang_String_init_CII, "([CII)V", newStringFromChars_CII, "newStringFromChars", "([CII)Ljava/lang/String;", NewStringFromChars_CII) \
  V(java_lang_String_init_IIC, "(II[C)V", newStringFromChars_IIC, "newStringFromChars", "(II[C)Ljava/lang/String;", NewStringFromChars_IIC) \
  V(java_lang_String_init_String, "(Ljava/lang/String;)V", newStringFromString, "newStringFromString", "(Ljava/lang/String;)Ljava/lang/String;", NewStringFromString) \
  V(java_lang_String_init_StringBuffer, "(Ljava/lang/StringBuffer;)V", newStringFromStringBuffer, "newStringFromStringBuffer", "(Ljava/lang/StringBuffer;)Ljava/lang/String;", NewStringFromStringBuffer) \
  V(java_lang_String_init_III, "([III)V", newStringFromCodePoints, "newStringFromCodePoints", "([III)Ljava/lang/String;", NewStringFromCodePoints) \
  V(java_lang_String_init_StringBuilder, "(Ljava/lang/StringBuilder;)V", newStringFromStringBuilder, "newStringFromStringBuilder", "(Ljava/lang/StringBuilder;)Ljava/lang/String;", NewStringFromStringBuilder) \

#define STATIC_STRING_INIT(init_runtime_name, init_signature, new_runtime_name, ...) \
    static ArtMethod* init_runtime_name = nullptr; \
    static ArtMethod* new_runtime_name = nullptr;
    STRING_INIT_LIST(STATIC_STRING_INIT)
#undef STATIC_STRING_INIT

void WellKnownClasses::InitStringInit(ObjPtr<mirror::Class> string_class,
                                      ObjPtr<mirror::Class> string_builder_class) {
  PointerSize p_size = Runtime::Current()->GetClassLinker()->GetImagePointerSize();
  auto find_method = [p_size](ObjPtr<mirror::Class> klass,
                              const char* name,
                              const char* sig,
                              bool expext_static) REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod* ret = klass->FindClassMethod(name, sig, p_size);
    CHECK(ret != nullptr);
    CHECK_EQ(expext_static, ret->IsStatic());
    return ret;
  };

  #define LOAD_STRING_INIT(init_runtime_name, init_signature, new_runtime_name,                  \
                           new_java_name, new_signature, ...)                                    \
      init_runtime_name = find_method(string_class, "<init>", init_signature, false);            \
      new_runtime_name = find_method(string_builder_class, new_java_name, new_signature, true);
      STRING_INIT_LIST(LOAD_STRING_INIT)
  #undef LOAD_STRING_INIT
}

void Thread::InitStringEntryPoints() {
  QuickEntryPoints* qpoints = &tlsPtr_.quick_entrypoints;
#define SET_ENTRY_POINT(init_runtime_name,                                        \
                        init_signature,                                           \
                        new_runtime_name,                                         \
                        new_java_name,                                            \
                        new_signature,                                            \
                        entry_point_name)                                         \
  DCHECK_IMPLIES(Runtime::Current()->IsStarted(), (new_runtime_name) != nullptr); \
  qpoints->p##entry_point_name = reinterpret_cast<void*>(new_runtime_name);
  STRING_INIT_LIST(SET_ENTRY_POINT)
#undef SET_ENTRY_POINT
}

ArtMethod* WellKnownClasses::StringInitToStringFactory(ArtMethod* string_init) {
  #define TO_STRING_FACTORY(init_runtime_name, init_signature, new_runtime_name,            \
                            new_java_name, new_signature, entry_point_name)                 \
      DCHECK((init_runtime_name) != nullptr);                                               \
      if (string_init == (init_runtime_name)) {                                             \
        DCHECK((new_runtime_name) != nullptr);                                              \
        return (new_runtime_name);                                                          \
      }
      STRING_INIT_LIST(TO_STRING_FACTORY)
  #undef TO_STRING_FACTORY
  LOG(FATAL) << "Could not find StringFactory method for String.<init>";
  UNREACHABLE();
}

uint32_t WellKnownClasses::StringInitToEntryPoint(ArtMethod* string_init) {
  #define TO_ENTRY_POINT(init_runtime_name, init_signature, new_runtime_name,               \
                         new_java_name, new_signature, entry_point_name)                    \
      if (string_init == (init_runtime_name)) {                                             \
        return kQuick ## entry_point_name;                                                  \
      }
      STRING_INIT_LIST(TO_ENTRY_POINT)
  #undef TO_ENTRY_POINT
  LOG(FATAL) << "Could not find StringFactory method for String.<init>";
  UNREACHABLE();
}
#undef STRING_INIT_LIST

void WellKnownClasses::Init(JNIEnv* env) {
  hiddenapi::ScopedHiddenApiEnforcementPolicySetting hiddenapi_exemption(
      hiddenapi::EnforcementPolicy::kDisabled);

  dalvik_annotation_optimization_CriticalNative =
      CacheClass(env, "dalvik/annotation/optimization/CriticalNative");
  dalvik_annotation_optimization_FastNative = CacheClass(env, "dalvik/annotation/optimization/FastNative");
  dalvik_annotation_optimization_NeverCompile =
      CacheClass(env, "dalvik/annotation/optimization/NeverCompile");
  dalvik_annotation_optimization_NeverInline =
      CacheClass(env, "dalvik/annotation/optimization/NeverInline");

  java_lang_annotation_Annotation__array = CacheClass(env, "[Ljava/lang/annotation/Annotation;");
  java_lang_ClassValue = CacheClass(env, "java/lang/ClassValue");
  java_lang_Record = CacheClass(env, "java/lang/Record");
  java_lang_reflect_Parameter__array = CacheClass(env, "[Ljava/lang/reflect/Parameter;");
  java_lang_StringFactory = CacheClass(env, "java/lang/StringFactory");
  java_lang_System = CacheClass(env, "java/lang/System");
  java_lang_Void = CacheClass(env, "java/lang/Void");
  libcore_reflect_AnnotationMember__array = CacheClass(env, "[Llibcore/reflect/AnnotationMember;");

  InitFieldsAndMethodsOnly(env);
}

void WellKnownClasses::InitFieldsAndMethodsOnly(JNIEnv* env) {
  hiddenapi::ScopedHiddenApiEnforcementPolicySetting hiddenapi_exemption(
      hiddenapi::EnforcementPolicy::kDisabled);

  Thread* self = Thread::ForEnv(env);
  ScopedObjectAccess soa(self);
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();

  java_lang_Boolean_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'Z', "Ljava/lang/Boolean;");
  java_lang_Byte_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'B', "Ljava/lang/Byte;");
  java_lang_Character_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'C', "Ljava/lang/Character;");
  java_lang_Double_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'D', "Ljava/lang/Double;");
  java_lang_Float_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'F', "Ljava/lang/Float;");
  java_lang_Integer_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'I', "Ljava/lang/Integer;");
  java_lang_Long_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'J', "Ljava/lang/Long;");
  java_lang_Short_valueOf =
      CachePrimitiveBoxingMethod(class_linker, self, 'S', "Ljava/lang/Short;");

  java_lang_Byte_ByteCache_cache = CacheBoxingCacheField(
      class_linker, self, "Ljava/lang/Byte$ByteCache;", "[Ljava/lang/Byte;");
  java_lang_Character_CharacterCache_cache = CacheBoxingCacheField(
      class_linker, self, "Ljava/lang/Character$CharacterCache;", "[Ljava/lang/Character;");
  java_lang_Short_ShortCache_cache = CacheBoxingCacheField(
      class_linker, self, "Ljava/lang/Short$ShortCache;", "[Ljava/lang/Short;");
  java_lang_Integer_IntegerCache_cache = CacheBoxingCacheField(
      class_linker, self, "Ljava/lang/Integer$IntegerCache;", "[Ljava/lang/Integer;");
  java_lang_Long_LongCache_cache = CacheBoxingCacheField(
      class_linker, self, "Ljava/lang/Long$LongCache;", "[Ljava/lang/Long;");

  java_lang_Byte_value = CacheValueInBoxField(
      class_linker, self, "Ljava/lang/Byte;", "B");
  java_lang_Character_value = CacheValueInBoxField(
      class_linker, self, "Ljava/lang/Character;", "C");
  java_lang_Short_value = CacheValueInBoxField(
      class_linker, self, "Ljava/lang/Short;", "S");
  java_lang_Integer_value = CacheValueInBoxField(
      class_linker, self, "Ljava/lang/Integer;", "I");
  java_lang_Long_value = CacheValueInBoxField(
      class_linker, self, "Ljava/lang/Long;", "J");

  StackHandleScope<44u> hs(self);
  Handle<mirror::Class> d_s_bdcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/BaseDexClassLoader;"));
  Handle<mirror::Class> d_s_dlcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/DelegateLastClassLoader;"));
  Handle<mirror::Class> d_s_dcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/DexClassLoader;"));
  Handle<mirror::Class> d_s_df =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/DexFile;"));
  Handle<mirror::Class> d_s_dpl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/DexPathList;"));
  Handle<mirror::Class> d_s_dpl_e =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/DexPathList$Element;"));
  Handle<mirror::Class> d_s_imdcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/InMemoryDexClassLoader;"));
  Handle<mirror::Class> d_s_pcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/PathClassLoader;"));
  Handle<mirror::Class> d_s_vmr =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ldalvik/system/VMRuntime;"));
  Handle<mirror::Class> j_i_fd =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/io/FileDescriptor;"));
  Handle<mirror::Class> j_l_bcl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/BootClassLoader;"));
  Handle<mirror::Class> j_l_cl =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/ClassLoader;"));
  Handle<mirror::Class> j_l_cnfe =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/ClassNotFoundException;"));
  Handle<mirror::Class> j_l_Daemons =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/Daemons;"));
  Handle<mirror::Class> j_l_Error =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/Error;"));
  Handle<mirror::Class> j_l_IllegalAccessError =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/IllegalAccessError;"));
  Handle<mirror::Class> j_l_NoClassDefFoundError =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/NoClassDefFoundError;"));
  Handle<mirror::Class> j_l_OutOfMemoryError =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/OutOfMemoryError;"));
  Handle<mirror::Class> j_l_RuntimeException =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/RuntimeException;"));
  Handle<mirror::Class> j_l_StackOverflowError =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/StackOverflowError;"));
  Handle<mirror::Class> j_l_Thread =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/Thread;"));
  Handle<mirror::Class> j_l_tg =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/ThreadGroup;"));
  Handle<mirror::Class> j_l_i_MethodHandle =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/invoke/MethodHandle;"));
  Handle<mirror::Class> j_l_i_MethodHandles =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/invoke/MethodHandles;"));
  Handle<mirror::Class> j_l_i_MethodHandles_Lookup =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/invoke/MethodHandles$Lookup;"));
  Handle<mirror::Class> j_l_i_MethodType =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/invoke/MethodType;"));
  Handle<mirror::Class> j_l_r_fr =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/ref/FinalizerReference;"));
  Handle<mirror::Class> j_l_r_rq =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/ref/ReferenceQueue;"));
  Handle<mirror::Class> j_l_rl_ite = hs.NewHandle(
      FindSystemClass(class_linker, self, "Ljava/lang/reflect/InvocationTargetException;"));
  Handle<mirror::Class> j_l_rl_Parameter =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/reflect/Parameter;"));
  Handle<mirror::Class> j_n_b =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/nio/Buffer;"));
  Handle<mirror::Class> j_n_bb =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/nio/ByteBuffer;"));
  Handle<mirror::Class> j_n_dbb =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/nio/DirectByteBuffer;"));
  Handle<mirror::Class> j_u_c =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/util/Collections;"));
  Handle<mirror::Class> j_u_c_tlr =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/util/concurrent/ThreadLocalRandom;"));
  Handle<mirror::Class> j_u_f_c =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/util/function/Consumer;"));
  Handle<mirror::Class> j_i_m_fd =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljdk/internal/math/FloatingDecimal;"));
  Handle<mirror::Class> j_i_m_fd_btab = hs.NewHandle(FindSystemClass(
      class_linker, self, "Ljdk/internal/math/FloatingDecimal$BinaryToASCIIBuffer;"));
  Handle<mirror::Class> j_i_m_fd_ebtab = hs.NewHandle(FindSystemClass(
      class_linker, self, "Ljdk/internal/math/FloatingDecimal$ExceptionalBinaryToASCIIBuffer;"));
  Handle<mirror::Class> l_r_af =
      hs.NewHandle(FindSystemClass(class_linker, self, "Llibcore/reflect/AnnotationFactory;"));
  Handle<mirror::Class> l_r_am =
      hs.NewHandle(FindSystemClass(class_linker, self, "Llibcore/reflect/AnnotationMember;"));
  Handle<mirror::Class> l_u_ea =
      hs.NewHandle(FindSystemClass(class_linker, self, "Llibcore/util/EmptyArray;"));
  Handle<mirror::Class> o_a_h_d_c =
      hs.NewHandle(FindSystemClass(class_linker, self, "Lorg/apache/harmony/dalvik/ddmc/Chunk;"));
  Handle<mirror::Class> o_a_h_d_d_ds =
      hs.NewHandle(FindSystemClass(class_linker, self, "Lorg/apache/harmony/dalvik/ddmc/DdmServer;"));

  ScopedAssertNoThreadSuspension sants(__FUNCTION__);
  PointerSize pointer_size = class_linker->GetImagePointerSize();

  dalvik_system_BaseDexClassLoader_getLdLibraryPath = CacheMethod(
      d_s_bdcl.Get(),
      /*is_static=*/ false,
      "getLdLibraryPath",
      "()Ljava/lang/String;",
      pointer_size);
  dalvik_system_DelegateLastClassLoader_init = CacheMethod(
      d_s_dlcl.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
      pointer_size);
  dalvik_system_DexClassLoader_init = CacheMethod(
      d_s_dcl.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V",
      pointer_size);
  dalvik_system_InMemoryDexClassLoader_init = CacheMethod(
      d_s_imdcl.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V",
      pointer_size);
  dalvik_system_PathClassLoader_init = CacheMethod(
      d_s_pcl.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
      pointer_size);

  dalvik_system_VMRuntime_hiddenApiUsed = CacheMethod(
      d_s_vmr.Get(),
      /*is_static=*/ true,
      "hiddenApiUsed",
      "(ILjava/lang/String;Ljava/lang/String;IZ)V",
      pointer_size);

  java_lang_BootClassLoader_init =
      CacheMethod(j_l_bcl.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_ClassLoader_loadClass = CacheMethod(
      j_l_cl.Get(),
      /*is_static=*/ false,
      "loadClass",
      "(Ljava/lang/String;)Ljava/lang/Class;",
      pointer_size);

  java_lang_ClassNotFoundException_init = CacheMethod(
      j_l_cnfe.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;Ljava/lang/Throwable;)V",
      pointer_size);

  ObjPtr<mirror::Class> j_l_Double = java_lang_Double_valueOf->GetDeclaringClass();
  java_lang_Double_doubleToRawLongBits =
      CacheMethod(j_l_Double, /*is_static=*/ true, "doubleToRawLongBits", "(D)J", pointer_size);
  ObjPtr<mirror::Class> j_l_Float = java_lang_Float_valueOf->GetDeclaringClass();
  java_lang_Float_floatToRawIntBits =
      CacheMethod(j_l_Float, /*is_static=*/ true, "floatToRawIntBits", "(F)I", pointer_size);

  java_lang_Daemons_start = CacheMethod(
      j_l_Daemons.Get(), /*is_static=*/ true, "start", "()V", pointer_size);
  java_lang_Daemons_stop = CacheMethod(
      j_l_Daemons.Get(), /*is_static=*/ true, "stop", "()V", pointer_size);
  java_lang_Daemons_waitForDaemonStart = CacheMethod(
      j_l_Daemons.Get(), /*is_static=*/ true, "waitForDaemonStart", "()V", pointer_size);

  java_lang_Error_init = CacheMethod(
      j_l_Error.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_IllegalAccessError_init = CacheMethod(
      j_l_IllegalAccessError.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_NoClassDefFoundError_init = CacheMethod(
      j_l_NoClassDefFoundError.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_OutOfMemoryError_init = CacheMethod(
      j_l_OutOfMemoryError.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_RuntimeException_init = CacheMethod(
      j_l_RuntimeException.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);
  java_lang_StackOverflowError_init = CacheMethod(
      j_l_StackOverflowError.Get(), /*is_static=*/ false, "<init>", "()V", pointer_size);

  ObjPtr<mirror::Class> j_l_String = GetClassRoot<mirror::String>(class_linker);
  java_lang_String_charAt = CacheMethod(
      j_l_String, /*is_static=*/ false, "charAt", "(I)C", pointer_size);

  java_lang_Thread_dispatchUncaughtException = CacheMethod(
      j_l_Thread.Get(),
      /*is_static=*/ false,
      "dispatchUncaughtException",
      "(Ljava/lang/Throwable;)V",
      pointer_size);
  java_lang_Thread_init = CacheMethod(
      j_l_Thread.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/ThreadGroup;Ljava/lang/String;IZ)V",
      pointer_size);
  java_lang_Thread_run = CacheMethod(
      j_l_Thread.Get(), /*is_static=*/ false, "run", "()V", pointer_size);
  java_lang_ThreadGroup_add = CacheMethod(
      j_l_tg.Get(), /*is_static=*/ false, "add", "(Ljava/lang/Thread;)V", pointer_size);
  java_lang_ThreadGroup_threadTerminated = CacheMethod(
      j_l_tg.Get(),
      /*is_static=*/ false,
      "threadTerminated",
      "(Ljava/lang/Thread;)V",
      pointer_size);

  java_lang_invoke_MethodHandle_asType = CacheMethod(
      j_l_i_MethodHandle.Get(),
      /*is_static=*/ false,
      "asType",
      "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
      pointer_size);
  java_lang_invoke_MethodHandle_invokeExact = CacheMethod(
      j_l_i_MethodHandle.Get(),
      /*is_static=*/ false,
      "invokeExact",
      "([Ljava/lang/Object;)Ljava/lang/Object;",
      pointer_size);
  java_lang_invoke_MethodHandles_lookup = CacheMethod(
      j_l_i_MethodHandles.Get(),
      /*is_static=*/ true,
      "lookup",
      "()Ljava/lang/invoke/MethodHandles$Lookup;",
      pointer_size);
  java_lang_invoke_MethodHandles_makeIdentity = CacheMethod(
      j_l_i_MethodHandles.Get(),
      /*is_static=*/ true,
      "makeIdentity",
      "(Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
      pointer_size);
  java_lang_invoke_MethodHandles_Lookup_findConstructor = CacheMethod(
      j_l_i_MethodHandles_Lookup.Get(),
      /*is_static=*/ false,
      "findConstructor",
      "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
      pointer_size);
  java_lang_invoke_MethodType_makeImpl = CacheMethod(
      j_l_i_MethodType.Get(),
      /* is_static=*/ true,
      "makeImpl",
      "(Ljava/lang/Class;[Ljava/lang/Class;Z)Ljava/lang/invoke/MethodType;",
      pointer_size);

  java_lang_ref_FinalizerReference_add = CacheMethod(
      j_l_r_fr.Get(), /*is_static=*/ true, "add", "(Ljava/lang/Object;)V", pointer_size);
  java_lang_ref_ReferenceQueue_add = CacheMethod(
      j_l_r_rq.Get(), /*is_static=*/ true, "add", "(Ljava/lang/ref/Reference;)V", pointer_size);

  java_lang_reflect_InvocationTargetException_init = CacheMethod(
      j_l_rl_ite.Get(), /*is_static=*/ false, "<init>", "(Ljava/lang/Throwable;)V", pointer_size);
  java_lang_reflect_Parameter_init = CacheMethod(
      j_l_rl_Parameter.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;ILjava/lang/reflect/Executable;I)V",
      pointer_size);

  ObjPtr<mirror::Class> j_l_rl_Proxy = GetClassRoot<mirror::Proxy>(class_linker);
  java_lang_reflect_Proxy_init = CacheMethod(
      j_l_rl_Proxy,
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/reflect/InvocationHandler;)V",
      pointer_size);
  java_lang_reflect_Proxy_invoke = CacheMethod(
      j_l_rl_Proxy,
      /*is_static=*/ true,
      "invoke",
      "(Ljava/lang/reflect/Proxy;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
      pointer_size);

  java_nio_Buffer_isDirect =
      CacheMethod(j_n_b.Get(), /*is_static=*/ false, "isDirect", "()Z", pointer_size);
  java_nio_DirectByteBuffer_init =
      CacheMethod(j_n_dbb.Get(), /*is_static=*/ false, "<init>", "(JI)V", pointer_size);

  java_util_function_Consumer_accept = CacheMethod(
      j_u_f_c.Get(), /*is_static=*/ false, "accept", "(Ljava/lang/Object;)V", pointer_size);

  jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_D = CacheMethod(
      j_i_m_fd.Get(),
      /*is_static=*/ true,
      "getBinaryToASCIIConverter",
      "(D)Ljdk/internal/math/FloatingDecimal$BinaryToASCIIConverter;",
      pointer_size);
  jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_F = CacheMethod(
      j_i_m_fd.Get(),
      /*is_static=*/ true,
      "getBinaryToASCIIConverter",
      "(F)Ljdk/internal/math/FloatingDecimal$BinaryToASCIIConverter;",
      pointer_size);
  jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_getChars =
      CacheMethod(j_i_m_fd_btab.Get(), /*is_static=*/ false, "getChars", "([C)I", pointer_size);

  libcore_reflect_AnnotationFactory_createAnnotation = CacheMethod(
      l_r_af.Get(),
      /*is_static=*/ true,
      "createAnnotation",
      "(Ljava/lang/Class;[Llibcore/reflect/AnnotationMember;)Ljava/lang/annotation/Annotation;",
      pointer_size);
  libcore_reflect_AnnotationMember_init = CacheMethod(
      l_r_am.Get(),
      /*is_static=*/ false,
      "<init>",
      "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/reflect/Method;)V",
      pointer_size);

  org_apache_harmony_dalvik_ddmc_DdmServer_broadcast =
      CacheMethod(o_a_h_d_d_ds.Get(), /*is_static=*/ true, "broadcast", "(I)V", pointer_size);
  org_apache_harmony_dalvik_ddmc_DdmServer_dispatch = CacheMethod(
      o_a_h_d_d_ds.Get(),
      /*is_static=*/ true,
      "dispatch",
      "(I[BII)Lorg/apache/harmony/dalvik/ddmc/Chunk;",
      pointer_size);

  dalvik_system_BaseDexClassLoader_pathList = CacheField(
      d_s_bdcl.Get(), /*is_static=*/ false, "pathList", "Ldalvik/system/DexPathList;");
  dalvik_system_BaseDexClassLoader_sharedLibraryLoaders = CacheField(
      d_s_bdcl.Get(), /*is_static=*/ false, "sharedLibraryLoaders", "[Ljava/lang/ClassLoader;");
  dalvik_system_BaseDexClassLoader_sharedLibraryLoadersAfter = CacheField(
      d_s_bdcl.Get(),
      /*is_static=*/ false,
      "sharedLibraryLoadersAfter",
      "[Ljava/lang/ClassLoader;");
  dalvik_system_DexFile_cookie = CacheField(
      d_s_df.Get(), /*is_static=*/ false, "mCookie", "Ljava/lang/Object;");
  dalvik_system_DexFile_fileName = CacheField(
      d_s_df.Get(), /*is_static=*/ false, "mFileName", "Ljava/lang/String;");
  dalvik_system_DexPathList_dexElements = CacheField(
      d_s_dpl.Get(), /*is_static=*/ false, "dexElements", "[Ldalvik/system/DexPathList$Element;");
  dalvik_system_DexPathList__Element_dexFile = CacheField(
      d_s_dpl_e.Get(), /*is_static=*/ false, "dexFile", "Ldalvik/system/DexFile;");

  dalvik_system_VMRuntime_nonSdkApiUsageConsumer = CacheField(
      d_s_vmr.Get(),
      /*is_static=*/ true,
      "nonSdkApiUsageConsumer",
      "Ljava/util/function/Consumer;");

  java_io_FileDescriptor_descriptor = CacheField(
      j_i_fd.Get(), /*is_static=*/ false, "descriptor", "I");

  java_lang_ClassLoader_parent = CacheField(
      j_l_cl.Get(), /*is_static=*/ false, "parent", "Ljava/lang/ClassLoader;");

  java_lang_String_EMPTY =
      CacheField(j_l_String, /*is_static=*/true, "EMPTY", "Ljava/lang/String;");
  java_lang_Thread_parkBlocker =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "parkBlocker", "Ljava/lang/Object;");
  java_lang_Thread_daemon = CacheField(j_l_Thread.Get(), /*is_static=*/ false, "daemon", "Z");
  java_lang_Thread_group =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "group", "Ljava/lang/ThreadGroup;");
  java_lang_Thread_lock =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "lock", "Ljava/lang/Object;");
  java_lang_Thread_name =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "name", "Ljava/lang/String;");
  java_lang_Thread_priority = CacheField(j_l_Thread.Get(), /*is_static=*/ false, "priority", "I");
  java_lang_Thread_nativePeer =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "nativePeer", "J");
  java_lang_Thread_systemDaemon =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "systemDaemon", "Z");
  java_lang_Thread_unparkedBeforeStart =
      CacheField(j_l_Thread.Get(), /*is_static=*/ false, "unparkedBeforeStart", "Z");

  java_lang_ThreadGroup_groups =
      CacheField(j_l_tg.Get(), /*is_static=*/ false, "groups", "[Ljava/lang/ThreadGroup;");
  java_lang_ThreadGroup_ngroups = CacheField(j_l_tg.Get(), /*is_static=*/ false, "ngroups", "I");
  java_lang_ThreadGroup_mainThreadGroup =
      CacheField(j_l_tg.Get(), /*is_static=*/ true, "mainThreadGroup", "Ljava/lang/ThreadGroup;");
  java_lang_ThreadGroup_name =
      CacheField(j_l_tg.Get(), /*is_static=*/ false, "name", "Ljava/lang/String;");
  java_lang_ThreadGroup_parent =
      CacheField(j_l_tg.Get(), /*is_static=*/ false, "parent", "Ljava/lang/ThreadGroup;");
  java_lang_ThreadGroup_systemThreadGroup =
      CacheField(j_l_tg.Get(), /*is_static=*/ true, "systemThreadGroup", "Ljava/lang/ThreadGroup;");

  ObjPtr<mirror::Class> j_l_Throwable = GetClassRoot<mirror::Throwable>(class_linker);
  java_lang_Throwable_cause = CacheField(
      j_l_Throwable, /*is_static=*/ false, "cause", "Ljava/lang/Throwable;");
  java_lang_Throwable_detailMessage = CacheField(
      j_l_Throwable, /*is_static=*/ false, "detailMessage", "Ljava/lang/String;");
  java_lang_Throwable_stackTrace = CacheField(
      j_l_Throwable, /*is_static=*/ false, "stackTrace", "[Ljava/lang/StackTraceElement;");
  java_lang_Throwable_stackState = CacheField(
      j_l_Throwable, /*is_static=*/ false, "backtrace", "Ljava/lang/Object;");
  java_lang_Throwable_suppressedExceptions = CacheField(
      j_l_Throwable, /*is_static=*/ false, "suppressedExceptions", "Ljava/util/List;");

  java_nio_Buffer_address = CacheField(j_n_b.Get(), /*is_static=*/ false, "address", "J");
  java_nio_Buffer_capacity = CacheField(j_n_b.Get(), /*is_static=*/ false, "capacity", "I");
  java_nio_Buffer_elementSizeShift =
      CacheField(j_n_b.Get(), /*is_static=*/ false, "_elementSizeShift", "I");
  java_nio_Buffer_limit = CacheField(j_n_b.Get(), /*is_static=*/ false, "limit", "I");
  java_nio_Buffer_position = CacheField(j_n_b.Get(), /*is_static=*/ false, "position", "I");

  java_nio_ByteBuffer_hb = CacheField(j_n_bb.Get(), /*is_static=*/ false, "hb", "[B");
  java_nio_ByteBuffer_isReadOnly =
      CacheField(j_n_bb.Get(), /*is_static=*/ false, "isReadOnly", "Z");
  java_nio_ByteBuffer_offset = CacheField(j_n_bb.Get(), /*is_static=*/ false, "offset", "I");

  java_util_Collections_EMPTY_LIST =
      CacheField(j_u_c.Get(), /*is_static=*/ true, "EMPTY_LIST", "Ljava/util/List;");

  java_util_concurrent_ThreadLocalRandom_seeder = CacheField(
      j_u_c_tlr.Get(), /*is_static=*/ true, "seeder", "Ljava/util/concurrent/atomic/AtomicLong;");

  jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_buffer =
      CacheField(j_i_m_fd_btab.Get(), /*is_static=*/ false, "buffer", "[C");
  jdk_internal_math_FloatingDecimal_ExceptionalBinaryToASCIIBuffer_image = CacheField(
      j_i_m_fd_ebtab.Get(), /*is_static=*/ false, "image", "Ljava/lang/String;");

  libcore_util_EmptyArray_STACK_TRACE_ELEMENT = CacheField(
      l_u_ea.Get(), /*is_static=*/ true, "STACK_TRACE_ELEMENT", "[Ljava/lang/StackTraceElement;");

  org_apache_harmony_dalvik_ddmc_Chunk_data =
      CacheField(o_a_h_d_c.Get(), /*is_static=*/ false, "data", "[B");
  org_apache_harmony_dalvik_ddmc_Chunk_length =
      CacheField(o_a_h_d_c.Get(), /*is_static=*/ false, "length", "I");
  org_apache_harmony_dalvik_ddmc_Chunk_offset =
      CacheField(o_a_h_d_c.Get(), /*is_static=*/ false, "offset", "I");
  org_apache_harmony_dalvik_ddmc_Chunk_type =
      CacheField(o_a_h_d_c.Get(), /*is_static=*/ false, "type", "I");
}

void WellKnownClasses::LateInit(JNIEnv* env) {
  // Initialize the `Runtime` class that was previously initialized
  // by `CacheMethod()` calling `FindMethodJNI()`.
  // TODO: Move this initialization to `ClassLinker`.
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  Thread* self = Thread::ForEnv(env);
  ScopedObjectAccess soa(self);
  StackHandleScope<1u> hs(self);
  Handle<mirror::Class> j_l_Runtime =
      hs.NewHandle(FindSystemClass(class_linker, self, "Ljava/lang/Runtime;"));
  bool success = class_linker->EnsureInitialized(
      self, j_l_Runtime, /*can_init_fields=*/ true, /*can_init_parents=*/ true);
  CHECK(success) << "Failed to initialize " << j_l_Runtime->PrettyDescriptor();

  // The function `GetClassLoader()` in `jni_internal.cc` is checking if the caller
  // is `java_lang_Runtime_nativeLoad` and, if so, returns the class loader override.
  // However, this function is used several times between `WellKnownClasses::Init()`
  // and setting up the override by the `Runtime` and requires that we take the other
  // path, rather than returning the uninitialized override. Therefore we cannot
  // initialize this well-known method early and require the `LateInit()`.
  // TODO: Clean up the initialization steps.
  java_lang_Runtime_nativeLoad = CacheMethod(
      j_l_Runtime.Get(),
      /*is_static=*/ true,
      "nativeLoad",
      "(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
      class_linker->GetImagePointerSize());
}

void WellKnownClasses::HandleJniIdTypeChange(JNIEnv* env) {
  WellKnownClasses::InitFieldsAndMethodsOnly(env);
  WellKnownClasses::LateInit(env);
}

void WellKnownClasses::Clear() {
  dalvik_annotation_optimization_CriticalNative = nullptr;
  dalvik_annotation_optimization_FastNative = nullptr;
  dalvik_annotation_optimization_NeverCompile = nullptr;
  dalvik_annotation_optimization_NeverInline = nullptr;
  java_lang_annotation_Annotation__array = nullptr;
  java_lang_ClassValue = nullptr;
  java_lang_Record = nullptr;
  java_lang_reflect_Parameter__array = nullptr;
  java_lang_StringFactory = nullptr;
  java_lang_System = nullptr;
  java_lang_Void = nullptr;
  libcore_reflect_AnnotationMember__array = nullptr;

  dalvik_system_BaseDexClassLoader_getLdLibraryPath = nullptr;
  WellKnownClasses::dalvik_system_DelegateLastClassLoader_init = nullptr;
  WellKnownClasses::dalvik_system_DexClassLoader_init = nullptr;
  WellKnownClasses::dalvik_system_InMemoryDexClassLoader_init = nullptr;
  WellKnownClasses::dalvik_system_PathClassLoader_init = nullptr;
  dalvik_system_VMRuntime_hiddenApiUsed = nullptr;
  java_io_FileDescriptor_descriptor = nullptr;
  java_lang_Boolean_valueOf = nullptr;
  java_lang_Byte_valueOf = nullptr;
  java_lang_Character_valueOf = nullptr;
  java_lang_BootClassLoader_init = nullptr;
  java_lang_ClassLoader_loadClass = nullptr;
  java_lang_ClassNotFoundException_init = nullptr;
  java_lang_Daemons_start = nullptr;
  java_lang_Daemons_stop = nullptr;
  java_lang_Daemons_waitForDaemonStart = nullptr;
  java_lang_Double_doubleToRawLongBits = nullptr;
  java_lang_Double_valueOf = nullptr;
  java_lang_Error_init = nullptr;
  java_lang_Float_floatToRawIntBits = nullptr;
  java_lang_Float_valueOf = nullptr;
  java_lang_IllegalAccessError_init = nullptr;
  java_lang_Integer_valueOf = nullptr;
  java_lang_Long_valueOf = nullptr;
  java_lang_NoClassDefFoundError_init = nullptr;
  java_lang_OutOfMemoryError_init = nullptr;
  java_lang_Runtime_nativeLoad = nullptr;
  java_lang_RuntimeException_init = nullptr;
  java_lang_Short_valueOf = nullptr;
  java_lang_StackOverflowError_init = nullptr;
  java_lang_String_charAt = nullptr;
  java_lang_Thread_dispatchUncaughtException = nullptr;
  java_lang_Thread_init = nullptr;
  java_lang_Thread_run = nullptr;
  java_lang_ThreadGroup_add = nullptr;
  java_lang_ThreadGroup_threadTerminated = nullptr;
  java_lang_invoke_MethodHandle_asType = nullptr;
  java_lang_invoke_MethodHandle_invokeExact = nullptr;
  java_lang_invoke_MethodHandles_lookup = nullptr;
  java_lang_invoke_MethodHandles_makeIdentity = nullptr;
  java_lang_invoke_MethodHandles_Lookup_findConstructor = nullptr;
  java_lang_invoke_MethodType_makeImpl = nullptr;
  java_lang_ref_FinalizerReference_add = nullptr;
  java_lang_ref_ReferenceQueue_add = nullptr;
  java_lang_reflect_InvocationTargetException_init = nullptr;
  java_lang_reflect_Parameter_init = nullptr;
  java_lang_reflect_Proxy_init = nullptr;
  java_lang_reflect_Proxy_invoke = nullptr;
  java_nio_Buffer_isDirect = nullptr;
  java_nio_DirectByteBuffer_init = nullptr;
  jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_D = nullptr;
  jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_F = nullptr;
  jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_getChars = nullptr;
  libcore_reflect_AnnotationFactory_createAnnotation = nullptr;
  libcore_reflect_AnnotationMember_init = nullptr;
  org_apache_harmony_dalvik_ddmc_DdmServer_broadcast = nullptr;
  org_apache_harmony_dalvik_ddmc_DdmServer_dispatch = nullptr;

  dalvik_system_BaseDexClassLoader_pathList = nullptr;
  dalvik_system_DexFile_cookie = nullptr;
  dalvik_system_DexFile_fileName = nullptr;
  dalvik_system_DexPathList_dexElements = nullptr;
  dalvik_system_DexPathList__Element_dexFile = nullptr;
  dalvik_system_VMRuntime_nonSdkApiUsageConsumer = nullptr;
  java_lang_ClassLoader_parent = nullptr;
  java_lang_String_EMPTY = nullptr;
  java_lang_Thread_parkBlocker = nullptr;
  java_lang_Thread_daemon = nullptr;
  java_lang_Thread_group = nullptr;
  java_lang_Thread_lock = nullptr;
  java_lang_Thread_name = nullptr;
  java_lang_Thread_priority = nullptr;
  java_lang_Thread_nativePeer = nullptr;
  java_lang_ThreadGroup_groups = nullptr;
  java_lang_ThreadGroup_ngroups = nullptr;
  java_lang_ThreadGroup_mainThreadGroup = nullptr;
  java_lang_ThreadGroup_name = nullptr;
  java_lang_ThreadGroup_parent = nullptr;
  java_lang_ThreadGroup_systemThreadGroup = nullptr;
  java_lang_Throwable_cause = nullptr;
  java_lang_Throwable_detailMessage = nullptr;
  java_lang_Throwable_stackTrace = nullptr;
  java_lang_Throwable_stackState = nullptr;
  java_lang_Throwable_suppressedExceptions = nullptr;
  java_nio_Buffer_address = nullptr;
  java_nio_Buffer_elementSizeShift = nullptr;
  java_nio_Buffer_limit = nullptr;
  java_nio_Buffer_position = nullptr;
  java_nio_ByteBuffer_hb = nullptr;
  java_nio_ByteBuffer_isReadOnly = nullptr;
  java_nio_ByteBuffer_offset = nullptr;
  java_util_Collections_EMPTY_LIST = nullptr;
  java_util_concurrent_ThreadLocalRandom_seeder = nullptr;
  jdk_internal_math_FloatingDecimal_BinaryToASCIIBuffer_buffer = nullptr;
  jdk_internal_math_FloatingDecimal_ExceptionalBinaryToASCIIBuffer_image = nullptr;
  libcore_util_EmptyArray_STACK_TRACE_ELEMENT = nullptr;
  org_apache_harmony_dalvik_ddmc_Chunk_data = nullptr;
  org_apache_harmony_dalvik_ddmc_Chunk_length = nullptr;
  org_apache_harmony_dalvik_ddmc_Chunk_offset = nullptr;
  org_apache_harmony_dalvik_ddmc_Chunk_type = nullptr;

  java_lang_Byte_ByteCache_cache = nullptr;
  java_lang_Character_CharacterCache_cache = nullptr;
  java_lang_Short_ShortCache_cache = nullptr;
  java_lang_Integer_IntegerCache_cache = nullptr;
  java_lang_Long_LongCache_cache = nullptr;

  java_lang_Byte_value = nullptr;
  java_lang_Character_value = nullptr;
  java_lang_Short_value = nullptr;
  java_lang_Integer_value = nullptr;
  java_lang_Long_value = nullptr;
}

ObjPtr<mirror::Class> WellKnownClasses::ToClass(jclass global_jclass) {
  JavaVMExt* vm = Runtime::Current()->GetJavaVM();
  auto ret = ObjPtr<mirror::Class>::DownCast(vm->DecodeGlobal(global_jclass));
  DCHECK(!ret.IsNull());
  return ret;
}

}  // namespace art
