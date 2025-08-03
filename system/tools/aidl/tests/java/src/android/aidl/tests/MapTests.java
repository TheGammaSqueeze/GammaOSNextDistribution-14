/*
 * Copyright (C) 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package android.aidl.tests;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import android.aidl.tests.map.Bar;
import android.aidl.tests.map.Foo;
import android.aidl.tests.map.IEmpty;
import android.aidl.tests.map.IntEnum;
import android.os.Parcel;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapTests {

    @Test
    public void testWriteAndThenReadMaps() {
        Parcel parcel = Parcel.obtain();

        // Write
        Foo foo = new Foo();

        foo.intEnumArrayMap = new HashMap<>();
        foo.intEnumArrayMap.put("Foo", new int[] {IntEnum.FOO});

        foo.intArrayMap = new HashMap<>();
        foo.intArrayMap.put("Foo", new int[] {42});

        Bar bar = new Bar();
        bar.a = 42;
        bar.b = "Bar";
        foo.barMap = new HashMap<>();
        foo.barMap.put("Foo", bar);

        foo.barArrayMap = new HashMap<>();
        foo.barArrayMap.put("Foo", new Bar[] {bar});

        foo.stringMap = new HashMap<>();
        foo.stringMap.put("Foo", "Bar");

        foo.stringArrayMap = new HashMap<>();
        foo.stringArrayMap.put("Foo", new String[] {"Bar"});

        IEmpty intf = new IEmpty.Stub() {};
        foo.interfaceMap = new HashMap<>();
        foo.interfaceMap.put("Foo", intf);

        foo.ibinderMap = new HashMap<>();
        foo.ibinderMap.put("Foo", intf.asBinder());

        foo.writeToParcel(parcel, 0);

        // And then read
        parcel.setDataPosition(0);

        Foo readFoo = new Foo();
        readFoo.readFromParcel(parcel);

        assertThat(readFoo.intEnumArrayMap.size(), is(1));
        assertThat(readFoo.intEnumArrayMap.get("Foo"), is(new int[] {IntEnum.FOO}));

        assertThat(readFoo.intArrayMap.size(), is(1));
        assertThat(readFoo.intArrayMap.get("Foo"), is(new int[] {42}));

        assertThat(readFoo.barMap.containsKey("Foo"), is(true));
        assertThat(readFoo.barMap.size(), is(1));

        Bar readBar = readFoo.barMap.get("Foo");
        assertThat(readBar.a, is(42));
        assertThat(readBar.b, is("Bar"));

        Bar[] bars = readFoo.barArrayMap.get("Foo");
        assertThat(bars.length, is(1));
        assertThat(bars[0].a, is(42));
        assertThat(bars[0].b, is("Bar"));

        assertThat(readFoo.stringMap.size(), is(1));
        assertThat(readFoo.stringMap.get("Foo"), is("Bar"));

        String[] strings = readFoo.stringArrayMap.get("Foo");
        assertThat(strings.length, is(1));
        assertThat(strings[0], is("Bar"));

        assertThat(readFoo.interfaceMap.size(), is(1));
        assertThat(readFoo.interfaceMap.get("Foo"), is(intf));

        assertThat(readFoo.ibinderMap.size(), is(1));
        assertThat(readFoo.ibinderMap.get("Foo"), is(intf.asBinder()));
    }
}
