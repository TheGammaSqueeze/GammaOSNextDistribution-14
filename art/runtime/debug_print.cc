/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <sstream>

#include "debug_print.h"

#include "class_linker.h"
#include "class_table.h"
#include "class_loader_utils.h"
#include "dex/utf.h"
#include "gc/heap.h"
#include "gc/space/space-inl.h"
#include "mirror/class.h"
#include "mirror/class_loader-inl.h"
#include "runtime.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-current-inl.h"
#include "well_known_classes-inl.h"

namespace art HIDDEN {

std::string DescribeSpace(ObjPtr<mirror::Class> klass) {
  std::ostringstream oss;
  gc::Heap* heap = Runtime::Current()->GetHeap();
  gc::space::ContinuousSpace* cs =
      heap->FindContinuousSpaceFromObject(klass, /* fail_ok= */ true);
  if (cs != nullptr) {
    if (cs->IsImageSpace()) {
      gc::space::ImageSpace* ispace = cs->AsImageSpace();
      oss << "image;" << ispace->GetName() << ";"
          // If the file name is the same as the name, output "+" instead to shorten the output.
          << (ispace->GetImageFilename() == cs->GetName() ? "+" : ispace->GetImageFilename())
          << ";" << static_cast<const void*>(ispace->Begin());
    } else {
      oss << "continuous;" << cs->GetName();
    }
  } else {
    gc::space::DiscontinuousSpace* ds =
        heap->FindDiscontinuousSpaceFromObject(klass, /* fail_ok= */ true);
    if (ds != nullptr) {
      oss << "discontinuous;" << ds->GetName();
    } else {
      oss << "invalid";
    }
  }
  return oss.str();
}

std::string DescribeLoaders(ObjPtr<mirror::ClassLoader> loader, const char* class_descriptor) {
  std::ostringstream oss;
  uint32_t hash = ComputeModifiedUtf8Hash(class_descriptor);
  ObjPtr<mirror::Class> path_class_loader = WellKnownClasses::dalvik_system_PathClassLoader.Get();
  ObjPtr<mirror::Class> dex_class_loader = WellKnownClasses::dalvik_system_DexClassLoader.Get();
  ObjPtr<mirror::Class> delegate_last_class_loader =
      WellKnownClasses::dalvik_system_DelegateLastClassLoader.Get();

  // Print the class loader chain.
  bool found_class = false;
  const char* loader_separator = "";
  if (loader == nullptr) {
    oss << "BootClassLoader";  // This would be unexpected.
  }
  for (; loader != nullptr; loader = loader->GetParent()) {
    ClassTable* table = Runtime::Current()->GetClassLinker()->ClassTableForClassLoader(loader);
    oss << loader_separator << loader->GetClass()->PrettyDescriptor()
        << "/" << static_cast<const void*>(table);
    loader_separator = ";";
    // If we didn't find the class yet, try to find it in the current class loader.
    if (!found_class) {
      ObjPtr<mirror::Class> klass =
          (table != nullptr) ? table->Lookup(class_descriptor, hash) : nullptr;
      if (klass != nullptr) {
        found_class = true;
        oss << "[hit:" << DescribeSpace(klass) << "]";
      }
    }

    // For PathClassLoader, DexClassLoader or DelegateLastClassLoader
    // also dump the dex file locations.
    if (loader->GetClass() == path_class_loader ||
        loader->GetClass() == dex_class_loader ||
        loader->GetClass() == delegate_last_class_loader) {
      oss << "(";
      Thread* self = Thread::Current();
      StackHandleScope<1> hs(self);
      Handle<mirror::ClassLoader> handle(hs.NewHandle(loader));
      const char* path_separator = "";
      const DexFile* base_dex_file = nullptr;
      VisitClassLoaderDexFiles(
          self,
          handle,
          [&](const DexFile* dex_file) {
              oss << path_separator;
              path_separator = ":";
              if (base_dex_file != nullptr &&
                  dex_file->GetLocation().length() > base_dex_file->GetLocation().length() &&
                  dex_file->GetLocation().compare(0u,
                                                  base_dex_file->GetLocation().length(),
                                                  base_dex_file->GetLocation()) == 0) {
                // Replace the base location with "+" to shorten the output.
                oss << "+" << dex_file->GetLocation().substr(base_dex_file->GetLocation().length());
              } else {
                oss << dex_file->GetLocation();
                base_dex_file = dex_file;
              }
              oss << "/" << static_cast<const void*>(dex_file);
              return true;  // Continue with the next DexFile.
          });
      oss << ")";
    }
  }

  return oss.str();
}

}  // namespace art
