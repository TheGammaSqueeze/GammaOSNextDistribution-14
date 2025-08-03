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

package dagger.functional.kotlinsrc.cycle

import dagger.Component
import javax.inject.Inject
import javax.inject.Provider

/**
 * Component with a long enough cycle such that the initialization of a provider happens in a
 * separate `initialize` method from the one where it is used as a delegated factory.
 *
 * @see [http://b/23579213](http://b/23579213)
 */
// Each nested class's constructor has an intentionally unused parameter.
@Suppress("UNUSED_PARAMETER")
class LongCycle private constructor() {
  class Class1 @Inject constructor(class2: Class2)
  class Class2 @Inject constructor(class3: Class3)
  class Class3 @Inject constructor(class4: Class4)
  class Class4 @Inject constructor(class5: Class5)
  class Class5 @Inject constructor(class6: Class6)
  class Class6 @Inject constructor(class7: Class7)
  class Class7 @Inject constructor(class8: Class8)
  class Class8 @Inject constructor(class9: Class9)
  class Class9 @Inject constructor(class10: Class10)
  class Class10 @Inject constructor(class11: Class11)
  class Class11 @Inject constructor(class12: Class12)
  class Class12 @Inject constructor(class13: Class13)
  class Class13 @Inject constructor(class14: Class14)
  class Class14 @Inject constructor(class15: Class15)
  class Class15 @Inject constructor(class16: Class16)
  class Class16 @Inject constructor(class17: Class17)
  class Class17 @Inject constructor(class18: Class18)
  class Class18 @Inject constructor(class19: Class19)
  class Class19 @Inject constructor(class20: Class20)
  class Class20 @Inject constructor(class21: Class21)
  class Class21 @Inject constructor(class22: Class22)
  class Class22 @Inject constructor(class23: Class23)
  class Class23 @Inject constructor(class24: Class24)
  class Class24 @Inject constructor(class25: Class25)
  class Class25 @Inject constructor(class26: Class26)
  class Class26 @Inject constructor(class27: Class27)
  class Class27 @Inject constructor(class28: Class28)
  class Class28 @Inject constructor(class29: Class29)
  class Class29 @Inject constructor(class30: Class30)
  class Class30 @Inject constructor(class31: Class31)
  class Class31 @Inject constructor(class32: Class32)
  class Class32 @Inject constructor(class33: Class33)
  class Class33 @Inject constructor(class34: Class34)
  class Class34 @Inject constructor(class35: Class35)
  class Class35 @Inject constructor(class36: Class36)
  class Class36 @Inject constructor(class37: Class37)
  class Class37 @Inject constructor(class38: Class38)
  class Class38 @Inject constructor(class39: Class39)
  class Class39 @Inject constructor(class40: Class40)
  class Class40 @Inject constructor(class41: Class41)
  class Class41 @Inject constructor(class42: Class42)
  class Class42 @Inject constructor(class43: Class43)
  class Class43 @Inject constructor(class44: Class44)
  class Class44 @Inject constructor(class45: Class45)
  class Class45 @Inject constructor(class46: Class46)
  class Class46 @Inject constructor(class47: Class47)
  class Class47 @Inject constructor(class48: Class48)
  class Class48 @Inject constructor(class49: Class49)
  class Class49 @Inject constructor(class50: Class50)
  class Class50 @Inject constructor(class51: Class51)
  class Class51 @Inject constructor(class52: Class52)
  class Class52 @Inject constructor(class53: Class53)
  class Class53 @Inject constructor(class54: Class54)
  class Class54 @Inject constructor(class55: Class55)
  class Class55 @Inject constructor(class56: Class56)
  class Class56 @Inject constructor(class57: Class57)
  class Class57 @Inject constructor(class58: Class58)
  class Class58 @Inject constructor(class59: Class59)
  class Class59 @Inject constructor(class60: Class60)
  class Class60 @Inject constructor(class61: Class61)
  class Class61 @Inject constructor(class62: Class62)
  class Class62 @Inject constructor(class63: Class63)
  class Class63 @Inject constructor(class64: Class64)
  class Class64 @Inject constructor(class65: Class65)
  class Class65 @Inject constructor(class66: Class66)
  class Class66 @Inject constructor(class67: Class67)
  class Class67 @Inject constructor(class68: Class68)
  class Class68 @Inject constructor(class69: Class69)
  class Class69 @Inject constructor(class70: Class70)
  class Class70 @Inject constructor(class71: Class71)
  class Class71 @Inject constructor(class72: Class72)
  class Class72 @Inject constructor(class73: Class73)
  class Class73 @Inject constructor(class74: Class74)
  class Class74 @Inject constructor(class75: Class75)
  class Class75 @Inject constructor(class76: Class76)
  class Class76 @Inject constructor(class77: Class77)
  class Class77 @Inject constructor(class78: Class78)
  class Class78 @Inject constructor(class79: Class79)
  class Class79 @Inject constructor(class80: Class80)
  class Class80 @Inject constructor(class81: Class81)
  class Class81 @Inject constructor(class82: Class82)
  class Class82 @Inject constructor(class83: Class83)
  class Class83 @Inject constructor(class84: Class84)
  class Class84 @Inject constructor(class85: Class85)
  class Class85 @Inject constructor(class86: Class86)
  class Class86 @Inject constructor(class87: Class87)
  class Class87 @Inject constructor(class88: Class88)
  class Class88 @Inject constructor(class89: Class89)
  class Class89 @Inject constructor(class90: Class90)
  class Class90 @Inject constructor(class91: Class91)
  class Class91 @Inject constructor(class92: Class92)
  class Class92 @Inject constructor(class93: Class93)
  class Class93 @Inject constructor(class94: Class94)
  class Class94 @Inject constructor(class95: Class95)
  class Class95 @Inject constructor(class96: Class96)
  class Class96 @Inject constructor(class97: Class97)
  class Class97 @Inject constructor(class98: Class98)
  class Class98 @Inject constructor(class99: Class99)
  class Class99 @Inject constructor(class100: Class100)
  class Class100 @Inject constructor(class101: Class101)
  class Class101 @Inject constructor(class1Provider: Provider<Class1>)

  @Component
  interface LongCycleComponent {
    fun class1(): Class1
  }
}
