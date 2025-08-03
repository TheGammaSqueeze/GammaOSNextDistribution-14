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
package com.android.car.cartelemetryapp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.telemetry.TelemetryProto.CarTelemetrydPublisher;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher;
import android.car.telemetry.TelemetryProto.MetricsConfig;
import android.car.telemetry.TelemetryProto.Publisher;
import android.car.telemetry.TelemetryProto.StatsPublisher;
import android.car.telemetry.TelemetryProto.Subscriber;
import android.car.telemetry.TelemetryProto.VehiclePropertyPublisher;
import android.content.Context;
import android.content.res.Resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ConfigParserTest {
    private static final String TEST_SCRIPT =
            "function testHandler(published_data, state)\n"
            + " on_script_finished(published_data)\n"
            + "end";
    private static final String FAKE_PACKAGE_NAME = "fake_package_name";
    private static final int FAKE_RES_ID = 1234;

    private InputStream mScriptStream;
    private ConfigParser mConfigParser;

    @Mock private Context mMockContext;
    @Mock private Resources mResources;

    @Captor ArgumentCaptor<Integer> mResIdCaptor;
    @Captor ArgumentCaptor<String> mScriptNameCaptor;
    @Captor ArgumentCaptor<String> mResTypeCaptor;
    @Captor ArgumentCaptor<String> mPackageNameCaptor;

    @Before
    public void setUp() {
        when(mMockContext.getResources()).thenReturn(mResources);
        when(mMockContext.getPackageName()).thenReturn(FAKE_PACKAGE_NAME);
        when(mResources.getIdentifier(anyString(), anyString(), anyString()))
                .thenReturn(FAKE_RES_ID);
        mScriptStream = new ByteArrayInputStream(TEST_SCRIPT.getBytes());
        when(mResources.openRawResource(anyInt())).thenReturn(mScriptStream);
        mConfigParser = new ConfigParser(mMockContext);
    }

    @Test
    public void test_parseConfigReturnsCorrectMetricsConfig() {
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"test_conifg_name\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        verify(mResources, times(1)).getIdentifier(
                mScriptNameCaptor.capture(),
                mResTypeCaptor.capture(),
                mPackageNameCaptor.capture());
        verify(mResources, times(1)).openRawResource(mResIdCaptor.capture());
        assertThat(mScriptNameCaptor.getValue()).isEqualTo("testScript");
        assertThat(mResTypeCaptor.getValue()).isEqualTo("raw");
        assertThat(mPackageNameCaptor.getValue()).isEqualTo(FAKE_PACKAGE_NAME);
        assertThat(mResIdCaptor.getValue()).isEqualTo(FAKE_RES_ID);

        assertThat(configs.size()).isEqualTo(1);
        assertThat(configs.containsKey("test_conifg_name")).isTrue();

        MetricsConfig config = configs.get("test_conifg_name");
        assertThat(config.getName()).isEqualTo("test_conifg_name");
        assertThat(config.getVersion()).isEqualTo(2);
        assertThat(config.getScript()).isEqualTo(TEST_SCRIPT);

        Subscriber sub = config.getSubscribers(0);
        assertThat(sub.getHandler()).isEqualTo("testHandler");
        assertThat(sub.getPriority()).isEqualTo(10);

        Publisher pub = sub.getPublisher();
        assertThat(pub.hasStats()).isTrue();

        StatsPublisher statsPub = pub.getStats();
        assertThat(statsPub.getSystemMetric())
                .isEqualTo(StatsPublisher.SystemMetric.PROCESS_MEMORY_STATE);
    }

    @Test
    public void test_emptyOrNoConfigNameReturnsNoConfig() {
        // First config doesn't have "name"; second config has empty "name"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_malformedOrNoConfigVersionReturnsNoConfig() {
        // First config doesn't have "version"; second config has malformed "version"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"config_name2\"\n"
                + "       version=\"abc\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_emptyOrNoConfigScriptNameReturnsNoConfig() {
        // First config doesn't have "script_name"; second config has empty "script_name"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       version=\"1\"\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"config_name2\"\n"
                + "       version=\"abc\"\n"
                + "       script_name=\"\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_emptyOrNoSubscribersTagsReturnsNoConfig() {
        // First config doesn't have "subscribers"; second config has empty "subscribers"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       version=\"1\"\n"
                + "       script_name=\"script\">\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       version=\"abc\"\n"
                + "       script_name=\"\">\n"
                + "   <subscribers>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_emptyOrNoSubscriberHandlerReturnsNoConfig() {
        // First config doesn't have subscriber "handler";
        // second config has empty subscriber "handler"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       version=\"1\"\n"
                + "       script_name=\"script1\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"config_name2\"\n"
                + "       version=\"abc\"\n"
                + "       script_name=\"script2\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_noPublisherTagOrTypeReturnsNoConfig() {
        // First config doesn't have "publisher" tag; second config doesn't have publisher "type"
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"config_name\"\n"
                + "       version=\"1\"\n"
                + "       script_name=\"script1\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testhandler\"\n"
                + "           priority=\"10\">\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "   <config\n"
                + "       name=\"config_name2\"\n"
                + "       version=\"abc\"\n"
                + "       script_name=\"script2\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testhandler2\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher>\n"
                + "                 <system_metric>PROCESS_MEMORY_STATE</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);

        assertThat(configs.size()).isEqualTo(0);
    }

    @Test
    public void test_parseStatsPublisherCorrectly() {
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"test_conifg_name\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"stats\">\n"
                + "                 <system_metric>APP_CRASH_OCCURRED</system_metric>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);
        MetricsConfig config = configs.get("test_conifg_name");
        Subscriber sub = config.getSubscribers(0);
        Publisher pub = sub.getPublisher();

        assertThat(pub.hasStats()).isTrue();
        StatsPublisher statsPub = pub.getStats();
        assertThat(statsPub.getSystemMetric())
                .isEqualTo(StatsPublisher.SystemMetric.APP_CRASH_OCCURRED);
    }

    @Test
    public void test_parseVehiclePropertyPublisherCorrectly() {
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"test_conifg_name\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"vehicle_property\">\n"
                + "                 <vehicle_property_id>123</vehicle_property_id>\n"
                + "                 <read_rate>2.0</read_rate>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);
        MetricsConfig config = configs.get("test_conifg_name");
        Subscriber sub = config.getSubscribers(0);
        Publisher pub = sub.getPublisher();

        assertThat(pub.hasVehicleProperty()).isTrue();
        VehiclePropertyPublisher propPub = pub.getVehicleProperty();
        assertThat(propPub.getVehiclePropertyId()).isEqualTo(123);
        assertThat(propPub.getReadRate()).isEqualTo(2.0f);
    }

    @Test
    public void test_parseCarTelemetrydPublisherCorrectly() {
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"test_conifg_name\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"cartelemetryd\">\n"
                + "                 <id>12345</id>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);
        MetricsConfig config = configs.get("test_conifg_name");
        Subscriber sub = config.getSubscribers(0);
        Publisher pub = sub.getPublisher();

        assertThat(pub.hasCartelemetryd()).isTrue();
        CarTelemetrydPublisher carTelemetrydPub = pub.getCartelemetryd();
        assertThat(carTelemetrydPub.getId()).isEqualTo(12345);
    }

    @Test
    public void test_parseConnectivityPublisherCorrectly() {
        String configXML =
                "<configs>\n"
                + "   <config\n"
                + "       name=\"test_conifg_name\"\n"
                + "       version=\"2\"\n"
                + "       script_name=\"testScript\">\n"
                + "   <subscribers>\n"
                + "       <subscriber\n"
                + "           handler=\"testHandler\"\n"
                + "           priority=\"10\">\n"
                + "         <publisher\n"
                + "             type=\"connectivity\">\n"
                + "                 <transport>TRANSPORT_WIFI</transport>\n"
                + "                 <oem_type>OEM_NONE</oem_type>\n"
                + "         </publisher>\n"
                + "       </subscriber>\n"
                + "   </subscribers>\n"
                + "   </config>\n"
                + "</configs>";
        XmlPullParser parser = createParserForXML(configXML);
        assertWithMessage("Failed to obtain parser from XML.").that(parser).isNotNull();
        Map<String, MetricsConfig> configs = mConfigParser.getConfigs(parser);
        MetricsConfig config = configs.get("test_conifg_name");
        Subscriber sub = config.getSubscribers(0);
        Publisher pub = sub.getPublisher();

        assertThat(pub.hasConnectivity()).isTrue();
        ConnectivityPublisher connPub = pub.getConnectivity();
        assertThat(connPub.getTransport())
                .isEqualTo(ConnectivityPublisher.Transport.TRANSPORT_WIFI);
        assertThat(connPub.getOemType())
                .isEqualTo(ConnectivityPublisher.OemType.OEM_NONE);
    }

    private XmlPullParser createParserForXML(String configXML) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(configXML));
            return parser;
        } catch (XmlPullParserException e) {
            return null;
        }
    }
}
