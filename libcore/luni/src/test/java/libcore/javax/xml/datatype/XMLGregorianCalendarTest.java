/*
 * Copyright (C) 2021 The Android Open Source Project
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
package libcore.javax.xml.datatype;

import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import junit.framework.TestCase;

public class XMLGregorianCalendarTest extends TestCase {

    private XMLGregorianCalendar calendar;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        calendar = new XMLGregorianCalendarImpl();
    }

    public void testGetMillisecond() {
        assertEquals(DatatypeConstants.FIELD_UNDEFINED, calendar.getMillisecond());
    }

    public void testSetTime_iii() {
        calendar.setTime(1, 2, 3);
        assertEquals(1, calendar.getHour());
        assertEquals(2, calendar.getMinute());
        assertEquals(3, calendar.getSecond());
    }

    public void testSetTime_iiii() {
        calendar.setTime(1, 2, 3, 4);
        assertEquals(1, calendar.getHour());
        assertEquals(2, calendar.getMinute());
        assertEquals(3, calendar.getSecond());
        assertEquals(DatatypeConstants.FIELD_UNDEFINED, calendar.getMillisecond());
    }

    public void testSetTime_iiiBigDecimal() {
        calendar.setTime(1, 2, 3, BigDecimal.valueOf(0.1));
        assertEquals(1, calendar.getHour());
        assertEquals(2, calendar.getMinute());
        assertEquals(3, calendar.getSecond());
        assertEquals(100, calendar.getMillisecond());
        assertEquals(BigDecimal.valueOf(0.1), calendar.getFractionalSecond());
    }

    public void testEquals_differentObjectParam() {
        assertNotEquals(Integer.valueOf(0), calendar);
    }

    public void testEquals_nullObjectParam() {
        assertNotEquals(null, calendar);
    }

    public void testEquals_equalObjectParam() {
        calendar.setYear(2023);
        calendar.setMonth(6);
        calendar.setDay(28);
        calendar.setTime(23, 59, 59, BigDecimal.valueOf(0.1));

        XMLGregorianCalendar anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setYear(2023);
        anotherCalendar.setMonth(6);
        anotherCalendar.setDay(28);
        anotherCalendar.setTime(23, 59, 59, BigDecimal.valueOf(0.1));

        assertEquals(calendar, anotherCalendar);
    }

    public void testEquals_differentCalendarParam() {
        calendar.setYear(2023);
        calendar.setMonth(6);
        calendar.setDay(28);
        calendar.setTime(23, 59, 59, BigDecimal.valueOf(0.1));

        XMLGregorianCalendar anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setYear(2023);
        anotherCalendar.setMonth(7); // different month
        anotherCalendar.setDay(28);
        anotherCalendar.setTime(23, 59, 59, BigDecimal.valueOf(0.1));

        assertNotEquals(calendar, anotherCalendar);
    }

    public void testToString() {
        calendar.setYear(2006);
        calendar.setMonth(10);
        calendar.setDay(23);
        calendar.setTime(22, 15, 1, BigDecimal.valueOf(0.000000135));
        calendar.setTimezone(0);

        assertEquals(calendar.toXMLFormat(), calendar.toString());
    }

    public void testHashCode_sameTimeSameTimezone() {
        calendar.setTimezone(0);

        XMLGregorianCalendarImpl anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setTimezone(0);

        assertEquals(calendar.hashCode(), anotherCalendar.hashCode());
    }

    public void testHashCode_differentTimeSameTimezone() {
        calendar.setTimezone(0);

        XMLGregorianCalendarImpl anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setHour(1);
        anotherCalendar.setTimezone(0);

        assertNotEquals(calendar.hashCode(), anotherCalendar.hashCode());
    }

    public void testHashCode_sameTimeDifferentTimezone() {
        calendar.setHour(1);
        calendar.setTimezone(0);

        XMLGregorianCalendarImpl anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setHour(1);
        anotherCalendar.setMinute(30);
        anotherCalendar.setTimezone(30);

        assertEquals(calendar.hashCode(), anotherCalendar.hashCode());
    }

    public void testHashCode_differentTimeDifferentTimezone() {
        calendar.setHour(1);
        calendar.setTimezone(0);

        XMLGregorianCalendarImpl anotherCalendar = new XMLGregorianCalendarImpl();
        anotherCalendar.setHour(2);
        anotherCalendar.setMinute(30);
        anotherCalendar.setTimezone(30);

        assertNotEquals(calendar.hashCode(), anotherCalendar.hashCode());
    }

    /**
     * Stub implementation intended for test coverage.
     */
    private static final class XMLGregorianCalendarImpl extends XMLGregorianCalendar {

        private int year;
        private int month;
        private int day;
        private int hour;
        private int minute;
        private int second;
        private int millisecond;
        private BigDecimal fractional;
        private int timezoneOffset;

        @Override
        public void clear() {
            year = month = day = hour = minute = second = millisecond = timezoneOffset = 0;
            fractional = BigDecimal.valueOf(0);
        }

        @Override
        public void reset() {
            year = month = day = hour = minute = second = millisecond = timezoneOffset = 0;
            fractional = BigDecimal.valueOf(0);
        }

        @Override
        public void setYear(BigInteger year) {
            this.year = year.intValue();
        }

        @Override
        public void setYear(int year) {
            this.year = year;
        }

        @Override
        public void setMonth(int month) {
            this.month = month;
        }

        @Override
        public void setDay(int day) {
            this.day = day;
        }

        @Override
        public void setTimezone(int offset) {
            this.timezoneOffset = offset;
        }

        @Override
        public void setHour(int hour) {
            this.hour = hour;
        }

        @Override
        public void setMinute(int minute) {
            this.minute = minute;
        }

        @Override
        public void setSecond(int second) {
            this.second = second;
        }

        @Override
        public void setMillisecond(int millisecond) {
            this.millisecond = millisecond;
        }

        @Override
        public void setFractionalSecond(BigDecimal fractional) {
            this.fractional = fractional;
        }

        @Override
        public BigInteger getEon() {
            return null;
        }

        @Override
        public int getYear() {
            return year;
        }

        @Override
        public BigInteger getEonAndYear() {
            return null;
        }

        @Override
        public int getMonth() {
            return month;
        }

        @Override
        public int getDay() {
            return day;
        }

        @Override
        public int getTimezone() {
            return timezoneOffset;
        }

        @Override
        public int getHour() {
            return hour;
        }

        @Override
        public int getMinute() {
            return minute;
        }

        @Override
        public int getSecond() {
            return second;
        }

        @Override
        public BigDecimal getFractionalSecond() {
            return fractional;
        }

        @Override
        public int compare(XMLGregorianCalendar rhs) {
            if (year != rhs.getYear()) return Integer.signum(year - rhs.getYear());
            if (month != rhs.getMonth()) return Integer.signum(month - rhs.getMonth());
            if (day != rhs.getDay()) return Integer.signum(day - rhs.getDay());
            if (hour != rhs.getHour()) return Integer.signum(hour - rhs.getHour());
            if (minute != rhs.getMinute()) return Integer.signum(minute - rhs.getMinute());
            if (second != rhs.getSecond()) return Integer.signum(second - rhs.getSecond());
            // edge case - millisecond is calculated from fractional second
            if (getMillisecond() != rhs.getMillisecond())
                return Integer.signum(getMillisecond() - rhs.getMillisecond());
            return fractional.compareTo(rhs.getFractionalSecond());
        }

        @Override
        public XMLGregorianCalendar normalize() {
            if (this.timezoneOffset == 30) {
                this.minute -= 30;
                this.timezoneOffset = 0;
            }

            return this;
        }

        @Override
        public String toXMLFormat() {
            return null;
        }

        @Override
        public QName getXMLSchemaType() {
            return null;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void add(Duration duration) {}

        @Override
        public GregorianCalendar toGregorianCalendar() {
            return null;
        }

        @Override
        public GregorianCalendar toGregorianCalendar(TimeZone timezone, Locale aLocale,
                XMLGregorianCalendar defaults) {
            return null;
        }

        @Override
        public TimeZone getTimeZone(int defaultZoneoffset) {
            return null;
        }

        @Override
        public Object clone() {
            return null;
        }
    }
}
