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
package vogar.target.testng;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import vogar.Result;
import vogar.monitor.TargetMonitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class TestNgListenerAdapter implements ITestListener {

    private final TargetMonitor monitor;

    public TestNgListenerAdapter(TargetMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void onTestStart(ITestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.getTestClass().getName());
        if (result.getName() != null) {
            sb.append('#').append(result.getName());
        }
        monitor.outcomeStarted(sb.toString());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        monitor.outcomeFinished(Result.SUCCESS);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable thrown = result.getThrowable();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream stackTrace = new PrintStream(out);
        thrown.printStackTrace(stackTrace);
        monitor.output(out.toString());
        monitor.outcomeFinished(Result.ERROR);
    }

    @Override
    public void onTestSkipped(ITestResult result) {}

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {}

    @Override
    public void onStart(ITestContext context) {}

    @Override
    public void onFinish(ITestContext context) {}
}
