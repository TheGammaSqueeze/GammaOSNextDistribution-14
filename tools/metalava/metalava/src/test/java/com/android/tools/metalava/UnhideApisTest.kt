/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:Suppress("ALL")

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class UnhideApisTest : DriverTest() {
    @Test
    fun `Report hidden API access rather than opening up access`() {
        check(
            format = FileFormat.V2,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "HiddenSuperclass",
                    ARG_HIDE,
                    "UnavailableSymbol",
                    ARG_HIDE,
                    "HiddenTypeParameter",
                    ARG_ERROR,
                    "ReferencesHidden"
                ),
            expectedIssues =
                """
            src/test/pkg/Foo.java:3: error: Class test.pkg.Hidden1 is not public but was referenced (in field type) from public field test.pkg.Foo.hidden1 [ReferencesHidden]
            src/test/pkg/Foo.java:4: error: Class test.pkg.Hidden2 is hidden but was referenced (in field type) from public field test.pkg.Foo.hidden2 [ReferencesHidden]
            src/test/pkg/Foo.java:2: error: Class test.pkg.Hidden1 is not public but was referenced (as type parameter) from public class test.pkg.Foo [ReferencesHidden]
            src/test/pkg/Foo.java:2: error: Class test.pkg.Hidden2 is hidden but was referenced (as type parameter) from public class test.pkg.Foo [ReferencesHidden]
            src/test/pkg/Foo.java:2: error: Class test.pkg.Hidden3 is hidden but was referenced (as type parameter) from public class test.pkg.Foo [ReferencesHidden]
            src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden1 is not public but was referenced (in parameter type) from public parameter hidden1 in test.pkg.Foo.method(test.pkg.Hidden1 hidden1, test.pkg.Hidden2 hidden2) [ReferencesHidden]
            src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden2 is hidden but was referenced (in parameter type) from public parameter hidden2 in test.pkg.Foo.method(test.pkg.Hidden1 hidden1, test.pkg.Hidden2 hidden2) [ReferencesHidden]
            src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden3 is hidden but was referenced (as exception) from public method test.pkg.Foo.method(test.pkg.Hidden1,test.pkg.Hidden2) [ReferencesHidden]
            src/test/pkg/Foo.java:7: error: Class test.pkg.Hidden1 is not public but was referenced (as type parameter) from public method test.pkg.Foo.get(T) [ReferencesHidden]
            src/test/pkg/Foo.java:7: error: Class test.pkg.Hidden2 is hidden but was referenced (as type parameter) from public method test.pkg.Foo.get(T) [ReferencesHidden]
            src/test/pkg/Foo.java:8: error: Class test.pkg.Hidden1 is not public but was referenced (in return type) from public method test.pkg.Foo.getHidden1() [ReferencesHidden]
            src/test/pkg/Foo.java:9: error: Class test.pkg.Hidden2 is hidden but was referenced (in return type) from public method test.pkg.Foo.getHidden2() [ReferencesHidden]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo<A extends Hidden1 & Hidden2, B extends Hidden3> extends Hidden2 {
                        public Hidden1 hidden1;
                        public Hidden2 hidden2;
                        public void method(Hidden1 hidden1, Hidden2 hidden2) throws Hidden3 {
                        }
                        public <S extends Hidden1, T extends Hidden2> S get(T t) { return null; }
                        public Hidden1 getHidden1() { return null; }
                        public Hidden2 getHidden2() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    // Implicitly not part of the API by being package private
                    class Hidden1 {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    public class Hidden2 {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    public class Hidden3 extends IOException {
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class Foo<A extends test.pkg.Hidden1 & test.pkg.Hidden2, B extends test.pkg.Hidden3> {
                    ctor public Foo();
                    method public <S extends test.pkg.Hidden1, T extends test.pkg.Hidden2> S get(T);
                    method public test.pkg.Hidden1 getHidden1();
                    method public test.pkg.Hidden2 getHidden2();
                    method public void method(test.pkg.Hidden1, test.pkg.Hidden2);
                    field public test.pkg.Hidden1 hidden1;
                    field public test.pkg.Hidden2 hidden2;
                  }
                }
                """
        )
    }

    @Test
    fun `Including private interfaces from types`() {
        check(
            format = FileFormat.V2,
            extraArguments = arrayOf(ARG_ERROR, "ReferencesHidden"),
            sourceFiles =
                arrayOf(
                    java("""package test.pkg1; interface Interface1 { }"""),
                    java("""package test.pkg1; abstract class Class1 { }"""),
                    java("""package test.pkg1; abstract class Class2 { }"""),
                    java("""package test.pkg1; abstract class Class3 { }"""),
                    java("""package test.pkg1; abstract class Class4 { }"""),
                    java("""package test.pkg1; abstract class Class5 { }"""),
                    java("""package test.pkg1; abstract class Class6 { }"""),
                    java("""package test.pkg1; abstract class Class7 { }"""),
                    java("""package test.pkg1; abstract class Class8 { }"""),
                    java("""package test.pkg1; abstract class Class9 { }"""),
                    java(
                        """
                    package test.pkg1;

                    import java.util.List;
                    import java.util.Map;
                    public abstract class Usage implements List<Class1> {
                       <T extends java.lang.Comparable<? super T>> void sort(java.util.List<T> list) {}
                       public Class3 myClass1 = null;
                       public List<? extends Class4> myClass2 = null;
                       public Map<String, ? extends Class5> myClass3 = null;
                       public <T extends Class6> void mySort(List<Class7> list, T element) {}
                       public void ellipsisType(Class8... myargs);
                       public void arrayType(Class9[] myargs);
                    }
                    """
                    )
                ),

            // TODO: Test annotations! (values, annotation classes, etc.)
            expectedIssues =
                """
                    src/test/pkg1/Usage.java:7: error: Class test.pkg1.Class3 is not public but was referenced (in field type) from public field test.pkg1.Usage.myClass1 [ReferencesHidden]
                    src/test/pkg1/Usage.java:8: error: Class test.pkg1.Class4 is not public but was referenced (in field type) from public field test.pkg1.Usage.myClass2 [ReferencesHidden]
                    src/test/pkg1/Usage.java:9: error: Class test.pkg1.Class5 is not public but was referenced (in field type) from public field test.pkg1.Usage.myClass3 [ReferencesHidden]
                    src/test/pkg1/Usage.java:10: error: Class test.pkg1.Class6 is not public but was referenced (as type parameter) from public method test.pkg1.Usage.mySort(java.util.List<test.pkg1.Class7>,T) [ReferencesHidden]
                    src/test/pkg1/Usage.java:10: error: Class test.pkg1.Class7 is not public but was referenced (in parameter type) from public parameter list in test.pkg1.Usage.mySort(java.util.List<test.pkg1.Class7> list, T element) [ReferencesHidden]
                    src/test/pkg1/Usage.java:11: error: Class test.pkg1.Class8 is not public but was referenced (in parameter type) from public parameter myargs in test.pkg1.Usage.ellipsisType(test.pkg1.Class8... myargs) [ReferencesHidden]
                    src/test/pkg1/Usage.java:12: error: Class test.pkg1.Class9 is not public but was referenced (in parameter type) from public parameter myargs in test.pkg1.Usage.arrayType(test.pkg1.Class9[] myargs) [ReferencesHidden]
                    src/test/pkg1/Usage.java:12: warning: Parameter myargs references hidden type test.pkg1.Class9. [HiddenTypeParameter]
                    src/test/pkg1/Usage.java:11: warning: Parameter myargs references hidden type test.pkg1.Class8. [HiddenTypeParameter]
                    src/test/pkg1/Usage.java:10: warning: Parameter list references hidden type test.pkg1.Class7. [HiddenTypeParameter]
                    src/test/pkg1/Usage.java:7: warning: Field Usage.myClass1 references hidden type test.pkg1.Class3. [HiddenTypeParameter]
                    src/test/pkg1/Usage.java:8: warning: Field Usage.myClass2 references hidden type test.pkg1.Class4. [HiddenTypeParameter]
                    src/test/pkg1/Usage.java:9: warning: Field Usage.myClass3 references hidden type test.pkg1.Class5. [HiddenTypeParameter]
                    """,
            expectedFail = DefaultLintErrorMessage,
            api =
                """
                    package test.pkg1 {
                      public abstract class Usage implements java.util.List<test.pkg1.Class1> {
                        ctor public Usage();
                        method public void arrayType(test.pkg1.Class9[]);
                        method public void ellipsisType(test.pkg1.Class8...);
                        method public <T extends test.pkg1.Class6> void mySort(java.util.List<test.pkg1.Class7>, T);
                        field public test.pkg1.Class3 myClass1;
                        field public java.util.List<? extends test.pkg1.Class4> myClass2;
                        field public java.util.Map<java.lang.String,? extends test.pkg1.Class5> myClass3;
                      }
                    }
                """
        )
    }
}
