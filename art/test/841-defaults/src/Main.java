/*
 * Copyright (C) 2022 The Android Open Source Project
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

interface Itf {
  default int defaultMethod1() { return 1; }
  default int defaultMethod2() { return 2; }
  default int defaultMethod3() { return 3; }
  default int defaultMethod4() { return 4; }
  default int defaultMethod5() { return 5; }
  default int defaultMethod6() { return 6; }
  default int defaultMethod7() { return 7; }
  default int defaultMethod8() { return 8; }
  default int defaultMethod9() { return 9; }
  default int defaultMethod10() { return 10; }
  default int defaultMethod11() { return 11; }
  default int defaultMethod12() { return 12; }
  default int defaultMethod13() { return 13; }
  default int defaultMethod14() { return 14; }
  default int defaultMethod15() { return 15; }
  default int defaultMethod16() { return 16; }
  default int defaultMethod17() { return 17; }
  default int defaultMethod18() { return 18; }
  default int defaultMethod19() { return 19; }
  default int defaultMethod20() { return 20; }
  default int defaultMethod21() { return 21; }
  default int defaultMethod22() { return 22; }
  default int defaultMethod23() { return 23; }
  default int defaultMethod24() { return 24; }
  default int defaultMethod25() { return 25; }
  default int defaultMethod26() { return 26; }
  default int defaultMethod27() { return 27; }
  default int defaultMethod28() { return 28; }
  default int defaultMethod29() { return 29; }
  default int defaultMethod30() { return 30; }
  default int defaultMethod31() { return 31; }
  default int defaultMethod32() { return 32; }
  default int defaultMethod33() { return 33; }
  default int defaultMethod34() { return 34; }
  default int defaultMethod35() { return 35; }
  default int defaultMethod36() { return 36; }
  default int defaultMethod37() { return 37; }
  default int defaultMethod38() { return 38; }
  default int defaultMethod39() { return 39; }
  default int defaultMethod40() { return 40; }
  default int defaultMethod41() { return 41; }
  default int defaultMethod42() { return 42; }
  default int defaultMethod43() { return 43; }
  default int defaultMethod44() { return 44; }
  default int defaultMethod45() { return 45; }
  default int defaultMethod46() { return 46; }
  default int defaultMethod47() { return 47; }
  default int defaultMethod48() { return 48; }
  default int defaultMethod49() { return 49; }
  default int defaultMethod50() { return 50; }
  default int defaultMethod51() { return 51; }
}

public class Main implements Itf {
  static Itf itf = new Main();
  public static void assertEquals(int value, int expected) {
    if (value != expected) {
      throw new Error("Expected " + expected + ", got " + value);
    }
  }

  public static void main(String[] args) throws Exception {
    assertEquals(itf.defaultMethod1(), 1);
    assertEquals(itf.defaultMethod2(), 2);
    assertEquals(itf.defaultMethod3(), 3);
    assertEquals(itf.defaultMethod4(), 4);
    assertEquals(itf.defaultMethod5(), 5);
    assertEquals(itf.defaultMethod6(), 6);
    assertEquals(itf.defaultMethod7(), 7);
    assertEquals(itf.defaultMethod8(), 8);
    assertEquals(itf.defaultMethod9(), 9);
    assertEquals(itf.defaultMethod10(), 10);
    assertEquals(itf.defaultMethod11(), 11);
    assertEquals(itf.defaultMethod12(), 12);
    assertEquals(itf.defaultMethod13(), 13);
    assertEquals(itf.defaultMethod14(), 14);
    assertEquals(itf.defaultMethod15(), 15);
    assertEquals(itf.defaultMethod16(), 16);
    assertEquals(itf.defaultMethod17(), 17);
    assertEquals(itf.defaultMethod18(), 18);
    assertEquals(itf.defaultMethod19(), 19);
    assertEquals(itf.defaultMethod20(), 20);
    assertEquals(itf.defaultMethod21(), 21);
    assertEquals(itf.defaultMethod22(), 22);
    assertEquals(itf.defaultMethod23(), 23);
    assertEquals(itf.defaultMethod24(), 24);
    assertEquals(itf.defaultMethod25(), 25);
    assertEquals(itf.defaultMethod26(), 26);
    assertEquals(itf.defaultMethod27(), 27);
    assertEquals(itf.defaultMethod28(), 28);
    assertEquals(itf.defaultMethod29(), 29);
    assertEquals(itf.defaultMethod30(), 30);
    assertEquals(itf.defaultMethod31(), 31);
    assertEquals(itf.defaultMethod32(), 32);
    assertEquals(itf.defaultMethod33(), 33);
    assertEquals(itf.defaultMethod34(), 34);
    assertEquals(itf.defaultMethod35(), 35);
    assertEquals(itf.defaultMethod36(), 36);
    assertEquals(itf.defaultMethod37(), 37);
    assertEquals(itf.defaultMethod38(), 38);
    assertEquals(itf.defaultMethod39(), 39);
    assertEquals(itf.defaultMethod40(), 40);
    assertEquals(itf.defaultMethod41(), 41);
    assertEquals(itf.defaultMethod42(), 42);
    assertEquals(itf.defaultMethod43(), 43);
    assertEquals(itf.defaultMethod44(), 44);
    assertEquals(itf.defaultMethod45(), 45);
    assertEquals(itf.defaultMethod46(), 46);
    assertEquals(itf.defaultMethod47(), 47);
    assertEquals(itf.defaultMethod48(), 48);
    assertEquals(itf.defaultMethod49(), 49);
    assertEquals(itf.defaultMethod50(), 50);
    assertEquals(itf.defaultMethod51(), 51);
  }
}
