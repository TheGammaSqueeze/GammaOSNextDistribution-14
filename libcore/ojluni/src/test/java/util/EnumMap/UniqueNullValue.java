/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Portions Copyright (c) 2012, IBM Corporation
 */

/*
 * @test
 * @bug 7123229
 * @summary (coll) EnumMap.containsValue(null) returns true
 * @author ngmr
 */
package test.java.util.EnumMap;

import java.util.EnumMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public class UniqueNullValue {
    static enum TestEnum { e00, e01 }

    @Test
    public void testUniqueNull() {
        Map<TestEnum, Integer> map = new EnumMap<>(TestEnum.class);

        map.put(TestEnum.e00, 0);
        Assert.assertTrue(map.containsValue(0));
        Assert.assertFalse(map.containsValue(null));

        map.put(TestEnum.e00, null);
        Assert.assertFalse(map.containsValue(0));
        Assert.assertTrue(map.containsValue(null));
    }
}