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

public class Main {

  // Testcase 1: the superclass has a package private version in the same package.
  static Interface s = new SubClass();

  // Testcase 2: the class has a package private version.
  static Interface s2;

  // Testcase 3: the superclass has a package private version in a different package.
  static Interface s3 = new SubClassFromPkg();

  // Testcase 4: there is no implementation in the hierarchy.
  static Interface s4 = new SubClassNoFoo();

  // Testcase 5: there is a private method in the hierarchy.
  static Interface s5 = new SubClassPrivateFoo();

  // Testcase 6: there is a static method in the hierarchy.
  static Interface s6 = new SubClassStaticFoo();

  static {
    try {
      s2 = (Interface) Class.forName("SubClass2").newInstance();
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  public static void assertEquals(Object expected, Object actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  public static void assertTrue(boolean value) {
    if (!value) {
      throw new Error("");
    }
  }

  public static void main(String[] args) throws Exception {
    assertEquals(SuperClass.class, ((SubClass) s).foo());
    assertEquals(SuperClass.class, ((SuperClass) s).foo());

    try {
      s.foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError ie) {
      // expected
    }

    assertEquals(null, ((SuperClass) s2).foo());
    try {
      s2.foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError ie) {
      // expected
    }

    try {
      ((pkg.PkgSuperClass) s3).foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError ie) {
      // expected
    }

    try {
      ((SubClassFromPkg) s3).foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError ie) {
      // expected
    }

    try {
      s3.foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError ie) {
      // expected
    }

    try {
      ((SuperClassNoFoo) s4).foo();
      throw new Error("Expected NoSuchMethodError");
    } catch (NoSuchMethodError e) {
      // expected
    }

    try {
      ((SubClassNoFoo) s4).foo();
      throw new Error("Expected AbstractMethodError");
    } catch (AbstractMethodError e) {
      // expected
    }

    try {
      s4.foo();
      throw new Error("Expected AbstractMethodError");
    } catch (AbstractMethodError e) {
      // expected
    }

    try {
      ((SuperClassPrivateFoo) s5).foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError e) {
      // expected
    }

    try {
      ((SubClassPrivateFoo) s5).foo();
      throw new Error("Expected IllegalAccessError");
    } catch (IllegalAccessError e) {
      // expected
    }

    try {
      s5.foo();
      throw new Error("Expected AbstractMethodError on RI, IllegalAccessError on ART");
    } catch (AbstractMethodError | IllegalAccessError e) {
      // expected
    }

    try {
      ((SuperClassStaticFoo) s6).foo();
      throw new Error("Expected IncompatibleClassChangeError");
    } catch (IncompatibleClassChangeError e) {
      // expected
    }

    try {
      ((SubClassStaticFoo) s6).foo();
      throw new Error("Expected IncompatibleClassChangeError");
    } catch (IncompatibleClassChangeError e) {
      // expected
    }

    try {
      s6.foo();
      throw new Error("Expected AbstractMethodError");
    } catch (AbstractMethodError e) {
      // expected
    }
  }
}

interface Interface {
  public Class<?> foo();
}

class SubClass extends SuperClass implements Interface {
}

class SubClassFromPkg extends pkg.PkgSuperClass implements Interface {
}

class SubClassNoFoo extends SuperClassNoFoo implements Interface {
}

class SubClassPrivateFoo extends SuperClassPrivateFoo implements Interface {
}

class SubClassStaticFoo extends SuperClassStaticFoo implements Interface {
}
