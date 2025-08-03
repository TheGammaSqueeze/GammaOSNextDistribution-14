/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.webkit.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.webkit.PacProcessor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class PacProcessorTest {

    private TestProcessClient mProcess;

    @Before
    public void setUp() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mProcess = TestProcessClient.createProcessB(context);
    }

    @After
    public void tearDown() throws Throwable {
        mProcess.close();
    }

    static class TestCreatePacProcessor extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            PacProcessor pacProcessor = PacProcessor.createInstance();
            PacProcessor otherPacProcessor = PacProcessor.createInstance();

            Assert.assertNotNull("createPacProcessor must not return null", pacProcessor);
            Assert.assertNotNull("createPacProcessor must not return null", otherPacProcessor);

            Assert.assertFalse(
                    "createPacProcessor must return different objects",
                    pacProcessor == otherPacProcessor);

            pacProcessor.setProxyScript(
                    "function FindProxyForURL(url, host) {"
                            + "return \"PROXY 1.2.3.4:8080\";"
                            + "}");
            otherPacProcessor.setProxyScript(
                    "function FindProxyForURL(url, host) {"
                            + "return \"PROXY 5.6.7.8:8080\";"
                            + "}");

            Assert.assertEquals("PROXY 1.2.3.4:8080", pacProcessor.findProxyForUrl("test.url"));
            Assert.assertEquals(
                    "PROXY 5.6.7.8:8080", otherPacProcessor.findProxyForUrl("test.url"));

            pacProcessor.release();
            otherPacProcessor.release();
        }
    }

    /**
     * Test that each {@link PacProcessor#createInstance} call returns a new not null instance.
     */
    @Test
    public void testCreatePacProcessor() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mProcess.run(TestCreatePacProcessor.class);
    }

    static class TestDefaultNetworkIsNull extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            PacProcessor pacProcessor = PacProcessor.createInstance();
            Assert.assertNull("PacProcessor must not have Network set", pacProcessor.getNetwork());

            pacProcessor.release();
        }
    }

    /**
     * Test PacProcessor does not have set Network by default.
     */
    @Test
    public void testDefaultNetworkIsNull() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mProcess.run(TestDefaultNetworkIsNull.class);
    }

    static class TestSetNetwork extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            ConnectivityManager connectivityManager =
                    ctx.getSystemService(ConnectivityManager.class);
            Network[] networks = connectivityManager.getAllNetworks();
            Assert.assertTrue(
                    "testSetNetwork requires at least one available Network", networks.length > 0);

            PacProcessor pacProcessor = PacProcessor.createInstance();
            PacProcessor otherPacProcessor = PacProcessor.createInstance();

            pacProcessor.setNetwork(networks[0]);
            Assert.assertEquals("Network is not set", networks[0], pacProcessor.getNetwork());
            Assert.assertNull(
                    "setNetwork must not affect other PacProcessors",
                    otherPacProcessor.getNetwork());

            pacProcessor.setNetwork(null);
            Assert.assertNull("Network is not unset", pacProcessor.getNetwork());

            pacProcessor.release();
            otherPacProcessor.release();
        }
    }

    /**
     * Test that setNetwork correctly set Network to PacProcessor.
     */
    @Test
    public void testSetNetwork() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mProcess.run(TestSetNetwork.class);
    }
}
