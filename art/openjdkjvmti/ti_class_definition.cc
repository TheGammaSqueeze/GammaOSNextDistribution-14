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

#include "ti_class_definition.h"

#include "base/array_slice.h"
#include "base/logging.h"
#include "class_linker-inl.h"
#include "class_root-inl.h"
#include "dex/dex_file.h"
#include "dex/art_dex_file_loader.h"
#include "handle.h"
#include "handle_scope-inl.h"
#include "mirror/class-inl.h"
#include "mirror/class_ext-inl.h"
#include "mirror/object-inl.h"
#include "reflection.h"
#include "thread.h"

namespace openjdkjvmti {

bool ArtClassDefinition::IsModified() const {
  // RedefineClasses calls always are 'modified' since they need to change the current_dex_file of
  // the class.
  if (redefined_) {
    return true;
  }

  // Check to see if any change has taken place.
  if (current_dex_file_.data() == dex_data_.data()) {
    // no change at all.
    return false;
  }

  // Check if the dex file we want to set is the same as the current one.
  // Unfortunately we need to do this check even if no modifications have been done since it could
  // be that agents were removed in the mean-time so we still have a different dex file. The dex
  // checksum means this is likely to be fairly fast.
  return current_dex_file_.size() != dex_data_.size() ||
      memcmp(current_dex_file_.data(), dex_data_.data(), current_dex_file_.size()) != 0;
}

jvmtiError ArtClassDefinition::InitCommon(art::Thread* self, jclass klass) {
  art::ScopedObjectAccess soa(self);
  art::ObjPtr<art::mirror::Class> m_klass(soa.Decode<art::mirror::Class>(klass));
  if (m_klass.IsNull()) {
    return ERR(INVALID_CLASS);
  }
  initialized_ = true;
  klass_ = klass;
  loader_ = soa.AddLocalReference<jobject>(m_klass->GetClassLoader());
  std::string descriptor_store;
  std::string descriptor(m_klass->GetDescriptor(&descriptor_store));
  name_ = descriptor.substr(1, descriptor.size() - 2);
  // Android doesn't really have protection domains.
  protection_domain_ = nullptr;
  return OK;
}

jvmtiError ArtClassDefinition::Init(art::Thread* self, jclass klass) {
  jvmtiError res = InitCommon(self, klass);
  if (res != OK) {
    return res;
  }
  art::ScopedObjectAccess soa(self);
  art::StackHandleScope<1> hs(self);
  art::Handle<art::mirror::Class> m_klass(hs.NewHandle(self->DecodeJObject(klass)->AsClass()));
  art::ObjPtr<art::mirror::ClassExt> ext(m_klass->GetExtData());
  if (!ext.IsNull()) {
    art::ObjPtr<art::mirror::Object> orig_dex(ext->GetOriginalDexFile());
    if (!orig_dex.IsNull()) {
      if (orig_dex->IsArrayInstance()) {
        // An array instance means the original-dex-file is from a redefineClasses which cannot have any
        // compact dex, so it's fine to use directly.
        art::ObjPtr<art::mirror::ByteArray> byte_array(orig_dex->AsByteArray());
        dex_data_memory_.resize(byte_array->GetLength());
        memcpy(dex_data_memory_.data(), byte_array->GetData(), dex_data_memory_.size());
        dex_data_ = art::ArrayRef<const unsigned char>(dex_data_memory_);

        const art::DexFile& cur_dex = m_klass->GetDexFile();
        current_dex_file_ =
            art::ArrayRef<const unsigned char>(cur_dex.Begin(), cur_dex.SizeIncludingSharedData());
        return OK;
      }

      if (orig_dex->IsDexCache()) {
        res = Init(*orig_dex->AsDexCache()->GetDexFile());
        if (res != OK) {
          return res;
        }
      } else {
        DCHECK(orig_dex->GetClass()->DescriptorEquals("Ljava/lang/Long;"))
            << "Expected java/lang/Long but found object of type "
            << orig_dex->GetClass()->PrettyClass();
        art::ObjPtr<art::mirror::Class> prim_long_class(
            art::GetClassRoot(art::ClassRoot::kPrimitiveLong));
        art::JValue val;
        if (!art::UnboxPrimitiveForResult(orig_dex.Ptr(), prim_long_class, &val)) {
          // This should never happen.
          LOG(FATAL) << "Unable to unbox a primitive long value!";
        }
        res = Init(*reinterpret_cast<const art::DexFile*>(static_cast<uintptr_t>(val.GetJ())));
        if (res != OK) {
          return res;
        }
      }
      const art::DexFile& cur_dex = m_klass->GetDexFile();
      current_dex_file_ =
          art::ArrayRef<const unsigned char>(cur_dex.Begin(), cur_dex.SizeIncludingSharedData());
      return OK;
    }
  }
  // No redefinition must have ever happened so we can use the class's dex file.
  return Init(m_klass->GetDexFile());
}

jvmtiError ArtClassDefinition::Init(art::Thread* self, const jvmtiClassDefinition& def) {
  jvmtiError res = InitCommon(self, def.klass);
  if (res != OK) {
    return res;
  }
  // We are being directly redefined.
  redefined_ = true;
  current_dex_file_ = art::ArrayRef<const unsigned char>(def.class_bytes, def.class_byte_count);
  dex_data_ = art::ArrayRef<const unsigned char>(def.class_bytes, def.class_byte_count);
  return OK;
}

jvmtiError ArtClassDefinition::InitFirstLoad(const char* descriptor,
                                             art::Handle<art::mirror::ClassLoader> klass_loader,
                                             const art::DexFile& dex_file) {
  art::Thread* self = art::Thread::Current();
  art::ScopedObjectAccess soa(self);
  initialized_ = true;
  // No Class
  klass_ = nullptr;
  loader_ = soa.AddLocalReference<jobject>(klass_loader.Get());
  std::string descriptor_str(descriptor);
  name_ = descriptor_str.substr(1, descriptor_str.size() - 2);
  // Android doesn't really have protection domains.
  protection_domain_ = nullptr;
  return Init(dex_file);
}

jvmtiError ArtClassDefinition::Init(const art::DexFile& dex_file) {
  if (dex_file.IsCompactDexFile()) {
    std::string error_msg;
    std::vector<std::unique_ptr<const art::DexFile>> dex_files;
    art::ArtDexFileLoader dex_file_loader(dex_file.GetLocation());
    if (!dex_file_loader.Open(/* verify= */ false,
                              /* verify_checksum= */ false,
                              &error_msg,
                              &dex_files)) {
      return ERR(INTERNAL);
    }
    const std::vector<const art::OatDexFile*>& oat_dex_files =
        dex_file.GetOatDexFile()->GetOatFile()->GetOatDexFiles();
    const art::DexFile* original_dex_file = nullptr;
    for (uint32_t i = 0; i < oat_dex_files.size(); ++i) {
      if (dex_file.GetOatDexFile() == oat_dex_files[i]) {
        original_dex_file = dex_files[i].get();
        break;
      }
    }
    // Keep the dex_data alive.
    dex_data_memory_.resize(original_dex_file->SizeIncludingSharedData());
    memcpy(dex_data_memory_.data(), original_dex_file->Begin(), dex_data_memory_.size());
    dex_data_ = art::ArrayRef<const unsigned char>(dex_data_memory_);

    // In case dex_data gets re-used for redefinition, keep the dex file live
    // with current_dex_memory.
    current_dex_memory_.resize(dex_data_.size());
    memcpy(current_dex_memory_.data(), dex_data_.data(), current_dex_memory_.size());
    current_dex_file_ = art::ArrayRef<const unsigned char>(current_dex_memory_);
  } else {
    // Dex file will always stay live, use it directly.
    dex_data_ =
        art::ArrayRef<const unsigned char>(dex_file.Begin(), dex_file.SizeIncludingSharedData());
    current_dex_file_ = dex_data_;
  }
  return OK;
}

}  // namespace openjdkjvmti
