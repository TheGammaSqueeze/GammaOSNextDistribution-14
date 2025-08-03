/*
 * Copyright (C) 2022 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.functional.kotlinsrc.membersinject.subpackage.a

import dagger.functional.kotlinsrc.membersinject.subpackage.b.BChild
import javax.inject.Inject

class AGrandchild : BChild() {
  @Inject internal lateinit var aGrandchildField: APublicObject
  private lateinit var aGrandchildMethod: APublicObject

  @Inject
  fun aGrandchildMethod(aGrandchildMethod: APublicObject) {
    this.aGrandchildMethod = aGrandchildMethod
  }

  @Inject
  protected override fun aParentMethod(aParentMethod: APublicObject) {
    super.aParentMethod(aParentMethod)
  }

  protected override fun aChildMethod(aChildMethod: APublicObject) {
    super.aChildMethod(aChildMethod)
  }

  fun aGrandchildField(): APublicObject = aGrandchildField

  fun aGrandchildMethod(): APublicObject = aGrandchildMethod
}
