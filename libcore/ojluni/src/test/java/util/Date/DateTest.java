/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4143459
 * @summary test Date
 * @library /java/text/testlib
 */
package test.java.util.Date;

import java.util.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DateTest {

    /**
     * Verify that the Date(String) constructor works.
     */
    @Test
    public void testParseOfGMT()
    {
        Date OUT;

        /* Input values */
        String stringVal = "Jan 01 00:00:00 GMT 1900";
        long expectedVal = -2208988800000L;

        OUT = new Date( stringVal );

        Assert.assertEquals(OUT.getTime( ), expectedVal );
    }

    // Check out Date's behavior with large negative year values; bug 664
    // As of the fix to bug 4056585, Date should work correctly with
    // large negative years.
    @Test
    public void testDateNegativeYears()
    {
        Date d1= new Date(80,-1,2);
        d1= new Date(-80,-1,2);
        try {
            d1= new Date(-800000,-1,2);
        }
        catch (IllegalArgumentException ex) {
            Assert.fail();
        }
    }

    // Verify the behavior of Date
    @Test
    public void testDate480()
    {
        TimeZone save = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("PST"));
            Date d1=new java.util.Date(97,8,13,10,8,13);
            Date d2=new java.util.Date(97,8,13,30,8,13); // 20 hours later

            double delta = (d2.getTime() - d1.getTime()) / 3600000;


            Assert.assertEquals(delta, 20.0);

            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(1997,8,13,10,8,13);
            Date t1 = cal.getTime();
            cal.clear();
            cal.set(1997,8,13,30,8,13); // 20 hours later
            Date t2 = cal.getTime();

            double delta2 = (t2.getTime() - t1.getTime()) / 3600000;

            Assert.assertEquals(delta2, 20.0);
        }
        finally {
            TimeZone.setDefault(save);
        }
    }
}