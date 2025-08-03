/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include "java_lang_VMClassLoader.h"

#include "base/zip_archive.h"
#include "class_linker.h"
#include "base/transform_iterator.h"
#include "base/stl_util.h"
#include "dex/descriptors_names.h"
#include "dex/dex_file_loader.h"
#include "dex/utf.h"
#include "handle_scope-inl.h"
#include "jni/jni_internal.h"
#include "mirror/class_loader.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "native_util.h"
#include "nativehelper/jni_macros.h"
#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"
#include "obj_ptr.h"
#include "scoped_fast_native_object_access-inl.h"
#include "string_array_utils.h"
#include "thread-inl.h"
#include "well_known_classes-inl.h"

namespace art HIDDEN {

// A class so we can be friends with ClassLinker and access internal methods.
class VMClassLoader {
 public:
  static ObjPtr<mirror::Class> LookupClass(ClassLinker* cl,
                                           Thread* self,
                                           const char* descriptor,
                                           size_t hash,
                                           ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES(!Locks::classlinker_classes_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return cl->LookupClass(self, descriptor, hash, class_loader);
  }

  static ObjPtr<mirror::Class> FindClassInPathClassLoader(ClassLinker* cl,
                                                          Thread* self,
                                                          const char* descriptor,
                                                          size_t hash,
                                                          Handle<mirror::ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ObjPtr<mirror::Class> result;
    if (cl->FindClassInBaseDexClassLoader(self, descriptor, hash, class_loader, &result)) {
      DCHECK(!self->IsExceptionPending());
      return result;
    }
    if (self->IsExceptionPending()) {
      self->ClearException();
    }
    return nullptr;
  }
};

static jclass VMClassLoader_findLoadedClass(JNIEnv* env, jclass, jobject javaLoader,
                                            jstring javaName) {
  ScopedFastNativeObjectAccess soa(env);
  ObjPtr<mirror::ClassLoader> loader = soa.Decode<mirror::ClassLoader>(javaLoader);
  ScopedUtfChars name(env, javaName);
  if (name.c_str() == nullptr) {
    return nullptr;
  }
  ClassLinker* cl = Runtime::Current()->GetClassLinker();

  // Compute hash once.
  std::string descriptor(DotToDescriptor(name.c_str()));
  const size_t descriptor_hash = ComputeModifiedUtf8Hash(descriptor.c_str());

  ObjPtr<mirror::Class> c = VMClassLoader::LookupClass(cl,
                                                       soa.Self(),
                                                       descriptor.c_str(),
                                                       descriptor_hash,
                                                       loader);
  if (c != nullptr && c->IsResolved()) {
    return soa.AddLocalReference<jclass>(c);
  }
  // If class is erroneous, throw the earlier failure, wrapped in certain cases. See b/28787733.
  if (c != nullptr && c->IsErroneous()) {
    cl->ThrowEarlierClassFailure(c);
    Thread* self = soa.Self();
    ObjPtr<mirror::Class> exception_class = self->GetException()->GetClass();
    if (exception_class == WellKnownClasses::java_lang_IllegalAccessError ||
        exception_class == WellKnownClasses::java_lang_NoClassDefFoundError) {
      self->ThrowNewWrappedException("Ljava/lang/ClassNotFoundException;",
                                     c->PrettyDescriptor().c_str());
    }
    return nullptr;
  }

  // Hard-coded performance optimization: We know that all failed libcore calls to findLoadedClass
  //                                      are followed by a call to the the classloader to actually
  //                                      load the class.
  if (loader != nullptr) {
    // Try the common case.
    StackHandleScope<1> hs(soa.Self());
    c = VMClassLoader::FindClassInPathClassLoader(cl,
                                                  soa.Self(),
                                                  descriptor.c_str(),
                                                  descriptor_hash,
                                                  hs.NewHandle(loader));
    if (c != nullptr) {
      return soa.AddLocalReference<jclass>(c);
    }
  }

  // The class wasn't loaded, yet, and our fast-path did not apply (e.g., we didn't understand the
  // classloader chain).
  return nullptr;
}

/*
 * Returns an array of entries from the boot classpath that could contain resources.
 */
static jobjectArray VMClassLoader_getBootClassPathEntries(JNIEnv* env, jclass) {
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  const std::vector<const DexFile*>& path = class_linker->GetBootClassPath();
  auto is_base_dex = [](const DexFile* dex_file) {
    return !DexFileLoader::IsMultiDexLocation(dex_file->GetLocation());
  };
  size_t jar_count = std::count_if(path.begin(), path.end(), is_base_dex);

  const DexFile* last_dex_file = nullptr;
  auto dchecked_is_base_dex = [&](const DexFile* dex_file) {
    // For multidex locations, e.g., x.jar!classes2.dex, we want to look into x.jar.
    // But we do not need to look into the base dex file more than once so we filter
    // out multidex locations using the fact that they follow the base location.
    if (kIsDebugBuild) {
      if (is_base_dex(dex_file)) {
        CHECK_EQ(DexFileLoader::GetBaseLocation(dex_file->GetLocation().c_str()),
                 dex_file->GetLocation());
      } else {
        CHECK(last_dex_file != nullptr);
        CHECK_EQ(DexFileLoader::GetBaseLocation(dex_file->GetLocation().c_str()),
                 DexFileLoader::GetBaseLocation(last_dex_file->GetLocation().c_str()));
      }
      last_dex_file = dex_file;
    }
    return is_base_dex(dex_file);
  };
  auto get_location = [](const DexFile* dex_file) { return dex_file->GetLocation(); };
  ScopedObjectAccess soa(Thread::ForEnv(env));
  return soa.AddLocalReference<jobjectArray>(CreateStringArray(
      soa.Self(),
      jar_count,
      MakeTransformRange(Filter(path, dchecked_is_base_dex), get_location)));
}

static JNINativeMethod gMethods[] = {
  FAST_NATIVE_METHOD(VMClassLoader, findLoadedClass, "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;"),
  NATIVE_METHOD(VMClassLoader, getBootClassPathEntries, "()[Ljava/lang/String;"),
};

void register_java_lang_VMClassLoader(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("java/lang/VMClassLoader");
}

}  // namespace art
