/* Copyright (C) 2023 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http:#www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
 */

import com.google.errorprone.scanner.BuiltInCheckerSuppliers;
import com.google.errorprone.BugCheckerInfo;

public class PrintClasses {
  public static void main(String[] args) {
    System.out.println("DISABLED_CHECKS");
    for (BugCheckerInfo info : BuiltInCheckerSuppliers.DISABLED_CHECKS) {
      System.out.println(info.canonicalName());
    }

    System.out.println("ENABLED_ERRORS");
    for (BugCheckerInfo info : BuiltInCheckerSuppliers.ENABLED_ERRORS) {
      System.out.println(info.canonicalName());
    }

    System.out.println("ENABLED_WARNINGS");
    for (BugCheckerInfo info : BuiltInCheckerSuppliers.ENABLED_WARNINGS) {
      System.out.println(info.canonicalName());
    }
  }
}
