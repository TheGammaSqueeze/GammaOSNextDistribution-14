/*
 * Copyright (C) 2023 The Android Open Source Project
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

package org.apache.test.android;

import java.util.Hashtable;
import java.util.Properties;

/**
 * Logs messages into Android's logcat and throws any reported Throwable directly, because
 * we run the tests as JUnit tests on Android.
 */
public class TestLogger implements org.apache.qetest.Logger {
    private Properties prop = new Properties();

    @Override
    public String getDescription() {
        return "Android test logger printing information into logcat.";
    }

    @Override
    public String[][] getParameterInfo() {
        return new String[0][];
    }

    @Override
    public Properties getProperties() {
        return prop;
    }

    @Override
    public void setProperties(Properties p) {
        prop = (Properties) p.clone();
    }

    @Override
    public boolean initialize(Properties p) {
        setProperties(p);

        return true;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    @Override
    public void testFileInit(String name, String comment) {
        logMsg(INFOMSG, "testFileInit: " + name + " - comment: " + comment);
    }

    @Override
    public void testFileClose(String msg, String result) {
        logMsg(INFOMSG, "testFileClose" + msg + " - result: " + result);
    }

    @Override
    public void testCaseInit(String comment) {
        logMsg(INFOMSG, comment);
    }

    @Override
    public void testCaseClose(String msg, String result) {
        logMsg(CRITICALMSG, "testCaseClose" + msg + " - result: " + result);
    }

    @Override
    public void logMsg(int level, String msg) {
        switch (level) {
            case ERRORMSG, FAILSONLY -> System.logE(msg);
            case CRITICALMSG, WARNINGMSG -> System.logW(msg);
            default -> System.logI(msg);
        }
    }

    @Override
    public void logArbitrary(int level, String msg) {
        logMsg(level, msg);
    }

    @Override
    public void logStatistic(int level, long lVal, double dVal, String msg) {
        logMsg(level, "logStatistic(lVal: " + lVal + ", dVal:" + dVal + "): " + msg);
    }

    @Override
    public void logThrowable(int level, Throwable throwable, String msg) {
        logMsg(level, msg);
        throw new AssertionError(msg, throwable);
    }

    @Override
    public void logElement(int level, String element, Hashtable attrs, Object msg) {
        logMsg(level, msg + " - element: " + element + "\nAttributes below:\n" + attrs);

    }

    @Override
    public void logHashtable(int level, Hashtable hash, String msg) {
        logMsg(level, msg + "\nTable below:\n" + hash);
    }

    @Override
    public void checkPass(String comment) {
        logMsg(INFOMSG, "checkPass: " + comment);
    }

    @Override
    public void checkAmbiguous(String comment) {
        logMsg(INFOMSG, "checkAmbiguous: " + comment);
    }

    @Override
    public void checkFail(String comment) {
        logMsg(FAILSONLY, "checkFail: " + comment);
    }

    @Override
    public void checkErr(String comment) {
        logMsg(ERRORMSG, "checkErr: " + comment);
    }

    @Override
    public void checkPass(String comment, String id) {
        logMsg(INFOMSG, "checkPass (" + id + "): " + comment);
    }

    @Override
    public void checkAmbiguous(String comment, String id) {
        logMsg(INFOMSG, "checkAmbiguous (" + id + "): " + comment);
    }

    @Override
    public void checkFail(String comment, String id) {
        logMsg(FAILSONLY, "checkFail (" + id + "): " + comment);
    }

    @Override
    public void checkErr(String comment, String id) {
        logMsg(ERRORMSG, "checkErr (" + id + "): " + comment);
    }
}
