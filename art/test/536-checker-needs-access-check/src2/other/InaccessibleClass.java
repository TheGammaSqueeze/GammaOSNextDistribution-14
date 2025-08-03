/*
 * Copyright (C) 2015 The Android Open Source Project
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

package other;

import other2.GetInaccessibleClass;

/*package*/ class InaccessibleClass {
  /// CHECK-START: java.lang.Class other.InaccessibleClass.$noinline$getReferrersClass() builder (after)
  /// CHECK: LoadClass class_name:other.InaccessibleClass needs_access_check:false
  public static Class<?> $noinline$getReferrersClass() {
    return InaccessibleClass.class;
  }

  /// CHECK-START: java.lang.Class other.InaccessibleClass.$noinline$getReferrersClassViaAnotherClass() inliner (after)
  /// CHECK: LoadClass class_name:other.InaccessibleClass needs_access_check:true
  public static Class<?> $noinline$getReferrersClassViaAnotherClass() {
    Class<?> klass = null;
    try {
      klass = GetInaccessibleClass.$inline$get();
      System.out.println("Unreachable");
    } catch (IllegalAccessError expected) {}
    return klass;
  }
}
