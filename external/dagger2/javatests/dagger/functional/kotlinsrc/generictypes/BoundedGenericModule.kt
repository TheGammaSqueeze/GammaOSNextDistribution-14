/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.kotlinsrc.generictypes

import dagger.Module
import dagger.Provides
import java.util.ArrayList
import java.util.LinkedList

@Module
class BoundedGenericModule {
  @Provides fun provideInteger(): Int = 1

  @Provides fun provideDouble(): Double = 2.0

  @Provides fun provideArrayListString(): ArrayList<String> = arrayListOf("arrayListOfString")

  @Provides
  fun provideLinkedListString(): LinkedList<String> = LinkedList(listOf("linkedListOfString"))

  @Provides
  fun provideLinkedListCharSeq(): LinkedList<CharSequence> =
    LinkedList(listOf("linkedListOfCharSeq"))

  @Provides
  fun provideArrayListOfComparableString(): LinkedList<Comparable<String>> =
    LinkedList(listOf("arrayListOfComparableOfString"))

  @Provides fun provideListOfInteger(): List<Int> = listOf(3)

  @Provides fun provideSetOfDouble(): Set<Double> = setOf(4.0)
}
