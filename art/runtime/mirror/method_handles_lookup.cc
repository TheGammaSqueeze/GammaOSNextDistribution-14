/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "method_handles_lookup.h"

#include "class-alloc-inl.h"
#include "class_root-inl.h"
#include "dex/modifiers.h"
#include "handle_scope.h"
#include "mirror/method_handle_impl.h"
#include "obj_ptr-inl.h"
#include "object-inl.h"
#include "well_known_classes.h"

namespace art HIDDEN {
namespace mirror {

ObjPtr<MethodHandlesLookup> MethodHandlesLookup::Create(Thread* const self,
                                                        Handle<Class> lookup_class)
  REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_) {
  static constexpr uint32_t kAllModes = kAccPublic | kAccPrivate | kAccProtected | kAccStatic;

  ObjPtr<MethodHandlesLookup> mhl = ObjPtr<MethodHandlesLookup>::DownCast(
      GetClassRoot<MethodHandlesLookup>()->AllocObject(self));
  mhl->SetFieldObject<false>(LookupClassOffset(), lookup_class.Get());
  mhl->SetField32<false>(AllowedModesOffset(), kAllModes);
  return mhl;
}

ObjPtr<MethodHandlesLookup> MethodHandlesLookup::GetDefault(Thread* const self) {
  ArtMethod* lookup = WellKnownClasses::java_lang_invoke_MethodHandles_lookup;
  return ObjPtr<MethodHandlesLookup>::DownCast(lookup->InvokeStatic<'L'>(self));
}

ObjPtr<MethodHandle> MethodHandlesLookup::FindConstructor(Thread* const self,
                                                          Handle<Class> klass,
                                                          Handle<MethodType> method_type) {
  ArtMethod* find_constructor =
      WellKnownClasses::java_lang_invoke_MethodHandles_Lookup_findConstructor;
  return ObjPtr<MethodHandle>::DownCast(
      find_constructor->InvokeFinal<'L', 'L', 'L'>(self, this, klass.Get(), method_type.Get()));
}

}  // namespace mirror
}  // namespace art
