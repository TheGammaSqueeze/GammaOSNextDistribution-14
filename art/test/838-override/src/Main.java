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

  public static void assertEquals(Object expected, Object actual) {
    if (expected != actual) {
      throw new Error("Expected " + expected + ", got " + actual);
    }
  }

  public static void main(String[] args) {
    // Tescase 1: a class with:
    // - a package-private pkg1 'foo'
    // - a public pkg2 'foo'.
    {
      pkg2.PublicFoo obj = new pkg2.PublicFoo();
      assertEquals(pkg1.Pkg1Foo.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg2.PublicFoo.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg2.PublicFoo.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg2.PublicFoo.class, obj.foo());
    }
    // Tescase 2: a class with:
    // - a package-private pkg1 'foo'
    // - a public pkg2 'foo'
    // - a public pkg1 'foo.
    {
      pkg1.PublicFoo obj = new pkg1.PublicFoo();
      assertEquals(pkg1.PublicFoo.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg1.PublicFoo.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg1.PublicFoo.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg1.PublicFoo.class, obj.foo());
    }

    // Tescase 3: a class with:
    // - a package-private pkg1 'foo'
    // - a package-private pkg2 'foo'
    // - a public pkg3 'foo.
    {
      pkg3.PublicFoo obj = new pkg3.PublicFoo();
      assertEquals(pkg1.Pkg1Foo.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg2.Pkg2Foo.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg3.PublicFoo.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg3.PublicFoo.class, obj.foo());
    }

    // Tescase 4: a class with:
    // - a package-private pkg1 'foo'
    // - a package-private pkg2 'foo'
    // - a public pkg3 'foo.
    // - a public pkg2 'foo'
    {
      pkg2.PublicFooInheritsPkg3 obj = new pkg2.PublicFooInheritsPkg3();
      assertEquals(pkg1.Pkg1Foo.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg2.PublicFooInheritsPkg3.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg2.PublicFooInheritsPkg3.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg2.PublicFooInheritsPkg3.class, obj.foo());
    }

    // Tescase 5: a class with:
    // - a package-private pkg1 'foo'
    // - a package-private pkg2 'foo'
    // - a public pkg3 'foo.
    // - a public pkg2 'foo'
    // - a public pkg1 'foo'
    {
      pkg1.PublicFooInheritsPkg2 obj = new pkg1.PublicFooInheritsPkg2();
      assertEquals(pkg1.PublicFooInheritsPkg2.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg1.PublicFooInheritsPkg2.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg1.PublicFooInheritsPkg2.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg1.PublicFooInheritsPkg2.class, obj.foo());
    }

    // Tescase 6: a class with:
    // - a package-private pkg1 'foo'
    // - a package-private pkg2 'foo'
    // - a public pkg1 'foo.
    {
      pkg1.LowerIndexImplementsFoo obj = new pkg1.LowerIndexImplementsFoo();
      assertEquals(pkg1.LowerIndexPublicFoo.class, pkg1.Pkg1Foo.callFoo(obj));
      assertEquals(pkg2.Pkg2Foo.class, pkg2.Pkg2Foo.callFoo(obj));
      assertEquals(pkg2.Pkg2Foo.class, pkg3.Pkg3Foo.callFoo(obj));
      assertEquals(pkg1.LowerIndexPublicFoo.class, obj.foo());
    }
  }
}
