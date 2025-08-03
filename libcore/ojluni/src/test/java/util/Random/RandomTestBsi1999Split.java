/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package test.java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.random.RandomGeneratorFactory;

/**
 * This is just parameterized version of RandomTestBsi1999 as
 * testing infra is not happy about long running tests.
 */
@RunWith(Parameterized.class)
public class RandomTestBsi1999Split {

    @Parameterized.Parameters(name = "{1}")
    public static Object[][] data() {
        return RandomGeneratorFactory.all()
            // skip because stochastic
            .filter(factory -> !factory.name().equals("SecureRandom"))
            // autocorrelation failure for java.util.Random longs bit 0:
            // count=2207 (should be in [2267,2733]), tau=2819
            .filter(factory -> !factory.name().equals("Random"))
            .map(factory -> new Object[] { factory, factory.name() } )
            .toArray(Object[][]::new);
    }

    private final RandomGeneratorFactory factory;
    private final String name;

    public RandomTestBsi1999Split(RandomGeneratorFactory factory, String name) {
        this.factory = factory;
        this.name = name;
    }

    @Test
    public void randomTestBsi1999() {
        RandomTestBsi1999.failCount = 0;
        RandomTestBsi1999.setRNG(name);

        RandomTestBsi1999.testOneRng(factory.create(59), 0);

        RandomTestBsi1999.exceptionOnFail();
    }
}
