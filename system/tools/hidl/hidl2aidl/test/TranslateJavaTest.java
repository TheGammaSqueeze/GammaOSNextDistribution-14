/*
 * Copyright (C) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, is(Version 2.0 (the "License"));
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hidl2aidl.tests;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import hidl2aidl.test.Translate;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runners.JUnit4;

public class TranslateJavaTest {
    @Test
    public void OnlyIn10() {
        hidl2aidl.test.OnlyIn10 dest;
        hidl2aidl.test.V1_0.OnlyIn10 source = new hidl2aidl.test.V1_0.OnlyIn10();
        source.str = "Hello";
        dest = Translate.h2aTranslate(source);
        assertThat(source.str, is(dest.str));
    }

    @Test
    public void OnlyIn11() {
        hidl2aidl.test.OnlyIn11 dest;
        hidl2aidl.test.V1_1.OnlyIn11 source = new hidl2aidl.test.V1_1.OnlyIn11();
        source.str = 12;
        dest = Translate.h2aTranslate(source);
        assertThat(source.str, is(dest.str));
    }

    @Test
    public void OverrideMe() {
        hidl2aidl.test.OverrideMe dest;
        hidl2aidl.test.V1_1.OverrideMe source = new hidl2aidl.test.V1_1.OverrideMe();
        source.a = "World";
        dest = Translate.h2aTranslate(source);
        assertThat(source.a, is(dest.a));
    }

    @Test
    public void Outer() {
        hidl2aidl.test.Outer dest;
        hidl2aidl.test.V1_1.Outer source = new hidl2aidl.test.V1_1.Outer();
        source.a = 12;
        source.v1_0.inner.a = 16;
        dest = Translate.h2aTranslate(source);
        assertThat(source.v1_0.inner.a, is(dest.inner.a));
    }

    @Test
    public void OuterInner() {
        hidl2aidl.test.Outer.Inner dest;
        hidl2aidl.test.V1_0.Outer.Inner source = new hidl2aidl.test.V1_0.Outer.Inner();
        source.a = 12;
        dest = Translate.h2aTranslate(source);
        assertThat(source.a, is(dest.a));
    }

    @Test
    public void NameCollision() {
        hidl2aidl.test.NameCollision dest;
        hidl2aidl.test.V1_2.NameCollision source = new hidl2aidl.test.V1_2.NameCollision();
        source.reference.reference.a = 12;
        source.reference.b = "Fancy";
        source.c = "Car";
        dest = Translate.h2aTranslate(source);
        assertThat(source.reference.reference.a, is(dest.a));
        assertThat(source.reference.b, is(dest.b));
        assertThat(source.c, is(dest.c));
    }

    @Test
    public void IFooBigStruct() {
        hidl2aidl.test.IFoo.BigStruct dest;
        hidl2aidl.test.V1_1.IFoo.BigStruct source = new hidl2aidl.test.V1_1.IFoo.BigStruct();
        source.type = 12;
        source.value = 16;
        dest = Translate.h2aTranslate(source);
        assertThat(source.type, is(dest.type));
        assertThat(source.value, is(dest.value));
    }

    @Test
    public void IBarInner() {
        hidl2aidl.test.IBar.Inner dest;
        hidl2aidl.test.V1_0.IBar.Inner source = new hidl2aidl.test.V1_0.IBar.Inner();
        source.a = 0x70000000;
        dest = Translate.h2aTranslate(source);
        assertThat(source.a, is(dest.a));
    }

    @Test
    public void UnsignedToSignedTooLarge() {
        hidl2aidl.test.IBar.Inner dest;
        hidl2aidl.test.V1_0.IBar.Inner source = new hidl2aidl.test.V1_0.IBar.Inner();
        // source.a is uint32_t, dest.a is int32_t
        source.a = -1;
        try {
            dest = Translate.h2aTranslate(source);
            fail("Expected an exception to be thrown for out of bounds unsigned to signed translation");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            assertThat("Unsafe conversion between signed and unsigned scalars for field: in.a",
                    is(message));
        }
    }

    @Test
    public void SafeUnionBarByte() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.a((byte) 8);
        assertThat(hidl2aidl.test.V1_2.SafeUnionBar.hidl_discriminator.a,
                is(source.getDiscriminator()));
        dest = Translate.h2aTranslate(source);
        assertThat(source.a(), is(dest.getA()));
    }

    @Test
    public void SafeUnionBarInt64() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.b(25000);
        dest = Translate.h2aTranslate(source);
        assertThat(source.b(), is(dest.getB()));
    }

    @Test
    public void SafeUnionBarInnerStructBar() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        hidl2aidl.test.V1_2.SafeUnionBar.InnerStructBar inner =
                new hidl2aidl.test.V1_2.SafeUnionBar.InnerStructBar();
        inner.x = 8;
        inner.z = 12;
        source.innerStructBar(inner);
        dest = Translate.h2aTranslate(source);
        assertThat(source.innerStructBar().x, is(dest.getInnerStructBar().x));
        assertThat(source.innerStructBar().z, is(dest.getInnerStructBar().z));
    }

    @Test
    public void SafeUnionBarOnlyIn11() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        hidl2aidl.test.V1_1.OnlyIn11 onlyIn11 = new hidl2aidl.test.V1_1.OnlyIn11();
        onlyIn11.str = 12;
        source.c(onlyIn11);
        dest = Translate.h2aTranslate(source);
        assertThat(source.c().str, is(dest.getC().str));
    }

    @Test
    public void SafeUnionBarString() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.d("Hello world!");
        dest = Translate.h2aTranslate(source);
        assertThat(source.d(), is(dest.getD()));
    }

    @Test
    public void SafeUnionBarFloat() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.e(3.5f);
        dest = Translate.h2aTranslate(source);
        assertThat(source.e(), is(dest.getE()));
    }

    @Test
    public void SafeUnionBarDouble() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.f(3e10);
        dest = Translate.h2aTranslate(source);
        assertThat(source.f(), is(dest.getF()));
    }

    @Test
    public void SafeUnionBarBitfield() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.g(hidl2aidl.test.V1_2.FooFlag.THIRD);
        dest = Translate.h2aTranslate(source);
        assertThat(source.g(), is(dest.getG()));
    }

    @Test
    public void SafeUnionBarEnum() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.h(hidl2aidl.test.V1_1.Value.B);
        dest = Translate.h2aTranslate(source);
        assertThat(source.h(), is(dest.getH()));
    }

    @Test
    public void SafeUnionBarChar() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.i((short) 12);
        dest = Translate.h2aTranslate(source);
        assertThat(source.i(), is((short) dest.getI()));
    }

    @Test
    public void SafeUnionBarNegativeChar() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.i((short) -1);
        try {
            dest = Translate.h2aTranslate(source);
            fail("Expected an exception to be thrown for out of bounds signed to unsigned translation");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            assertThat("Unsafe conversion between signed and unsigned scalars for field: in.i()",
                    is(message));
        }
    }

    @Test
    public void SafeUnionBarVecByte() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.j(new ArrayList<Byte>());
        source.j().add((byte) 12);
        source.j().add((byte) 6);
        dest = Translate.h2aTranslate(source);
        assertThat(source.j().get(0), is((byte) dest.getJ()[0]));
        assertThat(source.j().get(1), is((byte) dest.getJ()[1]));
    }

    @Test
    public void SafeUnionBarVecLong() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.k(new ArrayList<Integer>());
        source.k().add(hidl2aidl.test.Value.A);
        source.k().add(hidl2aidl.test.Value.B);
        dest = Translate.h2aTranslate(source);
        assertThat(source.k().get(0), is(dest.getK()[0]));
        assertThat(source.k().get(1), is(dest.getK()[1]));
    }

    @Test
    public void SafeUnionBarArrayByte() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.l(new byte[2]);
        source.l()[0] = (byte) 12;
        source.l()[1] = (byte) 6;
        dest = Translate.h2aTranslate(source);
        assertThat(source.l()[0], is(dest.getL()[0]));
        assertThat(source.l()[1], is(dest.getL()[1]));
    }

    @Test
    public void SafeUnionBarRepeatedFloat() {
        hidl2aidl.test.SafeUnionBar dest;
        hidl2aidl.test.V1_2.SafeUnionBar source = new hidl2aidl.test.V1_2.SafeUnionBar();
        source.m(3.5f);
        dest = Translate.h2aTranslate(source);
        assertThat(source.m(), is(dest.getM()));
    }

    @Test
    public void ArrayFoo() {
        hidl2aidl.test.ArrayFoo dest;
        hidl2aidl.test.V1_2.ArrayFoo source = new hidl2aidl.test.V1_2.ArrayFoo();
        source.a[0] = 42;
        source.a[0] = 42;
        assertThat(source.e.length, is(0));
        dest = Translate.h2aTranslate(source);
        assertThat(source.a[0], is(dest.a[0]));
    }

    @Test
    public void ArrayFooEmpty() {
        hidl2aidl.test.ArrayFoo dest;
        hidl2aidl.test.V1_2.ArrayFoo source = new hidl2aidl.test.V1_2.ArrayFoo();
        dest = Translate.h2aTranslate(source);
        assertThat(source.a.length, is(dest.a.length));
    }

    @Test
    public void ArrayFooEnum() {
        hidl2aidl.test.ArrayFoo dest;
        hidl2aidl.test.V1_2.ArrayFoo source = new hidl2aidl.test.V1_2.ArrayFoo();
        source.c[0] = hidl2aidl.test.Value.A;
        source.c[1] = hidl2aidl.test.Value.B;
        dest = Translate.h2aTranslate(source);
        assertThat(source.c[0], is(dest.c[0]));
        assertThat(source.c[1], is(dest.c[1]));
    }

    @Test
    public void ArrayFooString() {
        hidl2aidl.test.ArrayFoo dest;
        hidl2aidl.test.V1_2.ArrayFoo source = new hidl2aidl.test.V1_2.ArrayFoo();
        source.d[0] = "hello";
        source.d[1] = "world";
        dest = Translate.h2aTranslate(source);
        assertThat(source.d[0], is(dest.d[0]));
        assertThat(source.d[1], is(dest.d[1]));
    }

    @Test
    public void VectorFoo() {
        hidl2aidl.test.VectorFoo dest;
        hidl2aidl.test.V1_2.VectorFoo source = new hidl2aidl.test.V1_2.VectorFoo();
        source.a.add((byte) 42);
        source.a.add((byte) 8);
        dest = Translate.h2aTranslate(source);
        assertThat(source.a.get(0), is(dest.a[0]));
        assertThat(source.a.get(1), is(dest.a[1]));
    }

    @Test
    public void VectorFooEmpty() {
        hidl2aidl.test.VectorFoo dest;
        hidl2aidl.test.V1_2.VectorFoo source = new hidl2aidl.test.V1_2.VectorFoo();
        dest = Translate.h2aTranslate(source);
        assertThat(source.a.size(), is(dest.a.length));
    }

    @Test
    public void VectorFooUnsigned() {
        hidl2aidl.test.VectorFoo dest;
        hidl2aidl.test.V1_2.VectorFoo source = new hidl2aidl.test.V1_2.VectorFoo();
        source.b.add(12);
        source.b.add(0xf0000000);
        try {
            dest = Translate.h2aTranslate(source);
            fail("Expected an exception to be thrown for out of bounds unsigned to signed translation");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            assertThat(
                    "Unsafe conversion between signed and unsigned scalars for field: in.b.get(i)",
                    is(message));
        }
    }

    @Test
    public void VectorFooEnum() {
        hidl2aidl.test.VectorFoo dest;
        hidl2aidl.test.V1_2.VectorFoo source = new hidl2aidl.test.V1_2.VectorFoo();
        source.c.add(hidl2aidl.test.Value.A);
        source.c.add(hidl2aidl.test.Value.B);
        dest = Translate.h2aTranslate(source);
        assertThat(source.c.get(0), is(dest.c[0]));
        assertThat(source.c.get(1), is(dest.c[1]));
    }

    @Test
    public void VectorFooString() {
        hidl2aidl.test.VectorFoo dest;
        hidl2aidl.test.V1_2.VectorFoo source = new hidl2aidl.test.V1_2.VectorFoo();
        source.d.add("hello");
        source.d.add("world");
        dest = Translate.h2aTranslate(source);
        assertThat(source.d.get(0), is(dest.d[0]));
        assertThat(source.d.get(1), is(dest.d[1]));
    }

    @Test
    public void ExtensionArrayFoo() {
        hidl2aidl.test.extension.ArrayFoo dest;
        hidl2aidl.test.extension.V1_2.ArrayFoo source =
                new hidl2aidl.test.extension.V1_2.ArrayFoo();
        source.e[0] = 12;
        source.e[1] = 2;
        dest = hidl2aidl.test.extension.Translate.h2aTranslate(source);
        assertThat(source.e[0], is(dest.e[0]));
        assertThat(source.e[1], is(dest.e[1]));
    }
}
