/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Map;

/** Unit tests for {@link GceAvdInfo} */
@RunWith(JUnit4.class)
public class GceAvdInfoTest {

    @Test
    public void testValidGceJsonParsing() throws Exception {
        String valid =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22cf\",\n"
                        + "          \"logs\": [\n"
                        + "            {\n"
                        + "              \"path\": \"/text/log\",\n"
                        + "              \"type\": \"TEXT\",\n"
                        + "              \"name\": \"log.txt\"\n"
                        + "            },\n"
                        + "            {\n"
                        + "              \"path\": \"/unknown/log\",\n"
                        + "              \"type\": \"invalid\"\n"
                        + "            }\n"
                        + "          ]\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(valid, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHost(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22cf");
        List<GceAvdInfo.LogFileEntry> logs = avd.getLogs();
        assertEquals(logs.size(), 2);
        assertEquals(logs.get(0).path, "/text/log");
        assertEquals(logs.get(0).type, LogDataType.TEXT);
        assertEquals(logs.get(0).name, "log.txt");
        assertEquals(logs.get(1).path, "/unknown/log");
        assertEquals(logs.get(1).type, LogDataType.UNKNOWN);
        assertEquals(logs.get(1).name, "");
        assertTrue(avd.getBuildVars().isEmpty());
    }

    @Test
    public void testValidGceJsonParsingWithBuildVars() throws Exception {
        String valid =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"branch\": \"git_main\",\n"
                        + "          \"build_id\": \"5230832\",\n"
                        + "          \"build_target\": \"cf_x86_phone-userdebug\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22cf\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(valid, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHost(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22cf");
        assertTrue(avd.getLogs().isEmpty());
        assertEquals(avd.getBuildVars().get("branch"), "git_main");
        assertEquals(avd.getBuildVars().get("build_id"), "5230832");
        assertEquals(avd.getBuildVars().get("build_target"), "cf_x86_phone-userdebug");
    }

    @Test
    public void testDualAvdsJsonParsingWithBuildVars() throws Exception {
        String json1 =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"1.1.1.1\",\n"
                        + "          \"branch\": \"git_main\",\n"
                        + "          \"build_id\": \"1111111\",\n"
                        + "          \"build_target\": \"cf_x86_phone-userdebug\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-1111111-22cf\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        String json2 =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"2.2.2.2\",\n"
                        + "          \"branch\": \"git_main-release\",\n"
                        + "          \"build_id\": \"2222222\",\n"
                        + "          \"build_target\": \"cf_x86_phone-userdebug\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2222222-22cf\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        GceAvdInfo avd1 = GceAvdInfo.parseGceInfoFromString(json1, null, 1111);
        GceAvdInfo avd2 = GceAvdInfo.parseGceInfoFromString(json2, null, 2222);
        assertNotNull(avd1);
        assertEquals(avd1.hostAndPort().getHost(), "1.1.1.1");
        assertEquals(avd1.instanceName(), "gce-x86-phone-userdebug-1111111-22cf");
        assertEquals(avd1.getBuildVars().get("branch"), "git_main");
        assertEquals(avd1.getBuildVars().get("build_id"), "1111111");
        assertEquals(avd1.getBuildVars().get("build_target"), "cf_x86_phone-userdebug");
        assertNotNull(avd2);
        assertEquals(avd2.hostAndPort().getHost(), "2.2.2.2");
        assertEquals(avd2.instanceName(), "gce-x86-phone-userdebug-2222222-22cf");
        assertEquals(avd2.getBuildVars().get("branch"), "git_main-release");
        assertEquals(avd2.getBuildVars().get("build_id"), "2222222");
        assertEquals(avd2.getBuildVars().get("build_target"), "cf_x86_phone-userdebug");
    }

    @Test
    public void testNullStringJsonParsing() throws Exception {
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(null, null, 5555);
        assertNull(avd);
    }

    @Test
    public void testEmptyStringJsonParsing() throws Exception {
        assertNull(GceAvdInfo.parseGceInfoFromString(new String(), null, 5555));
    }

    @Test
    public void testMultipleGceJsonParsing() throws Exception {
        String multipleInstances =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        },\n"
                        + "       {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(multipleInstances, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testInvalidJsonParsing() throws Exception {
        String invalidJson = "bad_json";
        try {
            GceAvdInfo.parseGceInfoFromString(invalidJson, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testMissingGceJsonParsing() throws Exception {
        String missingInstance =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(missingInstance, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * In case of failure to boot in expected time, we need to parse the error to get the instance
     * name and stop it.
     */
    @Test
    public void testValidGceJsonParsingFail() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices_failing_boot\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ecc\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHost(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ecc");
    }

    /**
     * On a quota error No GceAvd information is created because the instance was not created.
     */
    @Test
    public void testValidGceJsonParsingFailQuota() throws Exception {
        String validError =
                " {\n"
                        + "    \"data\": {},\n"
                        + "    \"errors\": [\n"
                        + "\"Get operation state failed, errors: [{u'message': u\\\"Quota 'CPUS' "
                        + "exceeded.  Limit: 500.0\\\", u'code': u'QUOTA_EXCEEDED'}]\"\n"
                        + "],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(validError, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * In case of failure to boot in expected time, we need to parse the error to get the instance
     * name and stop it.
     */
    @Test
    public void testParseJson_Boot_Fail() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {\n"
                        + "      \"devices_failing_boot\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"104.154.62.236\",\n"
                        + "          \"instance_name\": \"gce-x86-phone-userdebug-2299773-22ec\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [\"device did not boot\"],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"BOOT_FAIL\"\n"
                        + "  }";
        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHost(), "104.154.62.236");
        assertEquals(avd.instanceName(), "gce-x86-phone-userdebug-2299773-22ec");
        assertEquals(GceAvdInfo.GceStatus.BOOT_FAIL, avd.getStatus());
    }

    /**
     * In case of failure to start the instance if no 'devices_failing_boot' is available avoid
     * parsing the instance.
     */
    @Test
    public void testParseJson_fail_error() throws Exception {
        String validFail =
                " {\n"
                        + "    \"data\": {},\n"
                        + "    \"errors\": [\"HttpError 403 when requesting\"],\n"
                        + "    \"command\": \"create\",\n"
                        + "    \"status\": \"FAIL\"\n"
                        + "  }";
        try {
            GceAvdInfo.parseGceInfoFromString(validFail, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /** Test CF start time metrics are added. */
    @Test
    public void testCfStartTimeMetricsAdded() throws Exception {
        String cuttlefish =
                " {\n"
                        + "    \"command\": \"create_cf\",\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"34.71.83.182\",\n"
                        + "          \"instance_name\": \"ins-cf-x86-phone-userdebug\",\n"
                        + "          \"fetch_artifact_time\": 63.22,\n"
                        + "          \"gce_create_time\": 23.5,\n"
                        + "          \"launch_cvd_time\": 226.5\n"
                        + "        },\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        JSONObject res = new JSONObject(cuttlefish);
        JSONArray devices = res.getJSONObject("data").getJSONArray("devices");
        GceAvdInfo.addCfStartTimeMetrics((JSONObject) devices.get(0));
        Map<String, String> metrics = InvocationMetricLogger.getInvocationMetrics();
        assertEquals("63220", metrics.get(InvocationMetricKey.CF_FETCH_ARTIFACT_TIME.toString()));
        assertEquals("23500", metrics.get(InvocationMetricKey.CF_GCE_CREATE_TIME.toString()));
        assertEquals("226500", metrics.get(InvocationMetricKey.CF_LAUNCH_CVD_TIME.toString()));
    }

    /** Test parsing valid json with error_type field defined. */
    @Test
    public void testValidGceJsonParsing_acloud_error_type() throws Exception {
        String acloudError =
                " {\n"
                    + "    \"data\": {\n"
                    + "      \"devices_failing_boot\": [\n"
                    + "        {\n"
                    + "          \"ip\": \"10.2.0.205\",\n"
                    + "          \"branch\": \"git_main\",\n"
                    + "          \"build_id\": \"P17712100\",\n"
                    + "          \"build_target\": \"cf_x86_phone-userdebug\",\n"
                    + "          \"instance_name\":"
                    + " \"ins-ae428ce9-p17712100-cf-x86-phone-userdebug\",\n"
                    + "          \"fetch_artifact_time\": \"89.86\",\n"
                    + "          \"gce_create_time\": \"22.31\",\n"
                    + "          \"launch_cvd_time\": \"540.01\"\n"
                    + "        }\n"
                    + "      ],\n"
                    + "      \"launch_cvd_command\": \"./bin/launch_cvd -daemon -x_res=720"
                    + " -y_res=1280 -dpi=320 -memory_mb=4096 -cpus 4"
                    + " -undefok=report_anonymous_usage_stats -report_anonymous_usage_stats=y\",\n"
                    + "      \"version\": \"2020-10-19_6914176\",\n"
                    + "      \"zone\": \"us-west1-b\"\n"
                    + "    },\n"
                    + "    \"error_type\": \"ACLOUD_INIT_ERROR\",\n"
                    + "    \"errors\": [\n"
                    + "\"Device ins-ae428ce9-p17712100-cf-x86-phone-userdebug did not finish on"
                    + " boot within timeout (540 secs)\"\n"
                    + "],\n"
                    + "    \"command\": \"create_cf\",\n"
                    + "    \"status\": \"BOOT_FAIL\"\n"
                    + "  }";

        GceAvdInfo avd = GceAvdInfo.parseGceInfoFromString(acloudError, null, 5555);
        assertNotNull(avd);
        assertEquals(avd.hostAndPort().getHost(), "10.2.0.205");
        assertEquals(avd.instanceName(), "ins-ae428ce9-p17712100-cf-x86-phone-userdebug");
        assertEquals(avd.getBuildVars().get("branch"), "git_main");
        assertEquals(avd.getBuildVars().get("build_id"), "P17712100");
        assertEquals(avd.getBuildVars().get("build_target"), "cf_x86_phone-userdebug");
        assertEquals(avd.getErrorType(), InfraErrorIdentifier.ACLOUD_INIT_ERROR);
    }

    /** Test parsing invalid json with error_type field defined and devices arbitrarily removed. */
    @Test
    public void testInvalidGceJsonParsing_acloud_errors_and_missing_devices() throws Exception {
        String acloudErrorAndMissingDevices =
                " {\n"
                        + "    \"data\": {\n"
                        /*
                        + "      \"devices_failing_boot\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"10.2.0.205\",\n"
                        + "          \"branch\": \"git_main\",\n"
                        + "          \"build_id\": \"P17712100\",\n"
                        + "          \"build_target\": \"cf_x86_phone-userdebug\",\n"
                        + "          \"instance_name\": \"ins-ae428ce9-p17712100-cf-x86-phone-userdebug\",\n"
                        + "          \"fetch_artifact_time\": \"89.86\",\n"
                        + "          \"gce_create_time\": \"22.31\",\n"
                        + "          \"launch_cvd_time\": \"540.01\"\n"
                        + "        }\n"
                        + "      ],\n"
                        */
                        + "      \"launch_cvd_command\": \"./bin/launch_cvd -daemon -x_res=720"
                        + " -y_res=1280 -dpi=320 -memory_mb=4096 -cpus 4"
                        + " -undefok=report_anonymous_usage_stats"
                        + " -report_anonymous_usage_stats=y\",\n"
                        + "      \"version\": \"2020-10-19_6914176\",\n"
                        + "      \"zone\": \"us-west1-b\"\n"
                        + "    },\n"
                        + "    \"error_type\": \"ACLOUD_INIT_ERROR\",\n"
                        + "    \"errors\": [\n"
                        + "\"Device ins-ae428ce9-p17712100-cf-x86-phone-userdebug did not finish"
                        + " on boot within timeout (540 secs)\"\n"
                        + "],\n"
                        + "    \"command\": \"create_cf\",\n"
                        + "    \"status\": \"BOOT_FAIL\"\n"
                        + "  }";

        try {
            GceAvdInfo.parseGceInfoFromString(acloudErrorAndMissingDevices, null, 5555);
            fail("A TargetSetupError should have been thrown.");
        } catch (TargetSetupError e) {
            assertEquals(e.getErrorId(), InfraErrorIdentifier.ACLOUD_INIT_ERROR);
        }
    }

    @Test
    public void testDetermineAcloudErrorType() {
        assertEquals(GceAvdInfo.determineAcloudErrorType(null),
                InfraErrorIdentifier.ACLOUD_UNRECOGNIZED_ERROR_TYPE);
        assertEquals(GceAvdInfo.determineAcloudErrorType(""),
                InfraErrorIdentifier.ACLOUD_UNRECOGNIZED_ERROR_TYPE);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("invalid error type"),
                InfraErrorIdentifier.ACLOUD_UNRECOGNIZED_ERROR_TYPE);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("ACLOUD_INIT_ERROR"),
                InfraErrorIdentifier.ACLOUD_INIT_ERROR);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("ACLOUD_CREATE_GCE_ERROR"),
                InfraErrorIdentifier.ACLOUD_CREATE_GCE_ERROR);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("ACLOUD_DOWNLOAD_ARTIFACT_ERROR"),
                InfraErrorIdentifier.ACLOUD_DOWNLOAD_ARTIFACT_ERROR);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("ACLOUD_BOOT_UP_ERROR"),
                InfraErrorIdentifier.ACLOUD_BOOT_UP_ERROR);
        assertEquals(
                GceAvdInfo.determineAcloudErrorType("GCE_QUOTA_ERROR"),
                InfraErrorIdentifier.GCE_QUOTA_ERROR);
    }

    /** Test handling succeeded Oxygen device lease request. */
    @Test
    public void testOxygenClientSucceedResponse() {
        String output =
                "debug info lease result: session_id:\"6a6a744e-0653-4926-b7b8-535d121a2fc9\"\n"
                    + " server_url:\"10.0.80.227\"\n"
                    + " ports:{type:test value:12345}\n"
                    + " random_key:\"this-is-12345678\"\n"
                    + " leased_device_spec:{type:TESTTYPE build_artifacts:{build_id:\"P1234567\""
                    + " build_target:\"target\" build_branch:\"testBranch\"}}"
                    + " oxygen_version:\"v20220509-0008-rc01-cl447382102\"  "
                    + " debug_info:{reserved_cores:1 region:\"test-region\" environment:\"test\"}";
        CommandResult res = Mockito.mock(CommandResult.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return CommandStatus.SUCCESS;
                            }
                        })
                .when(res)
                .getStatus();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return "";
                            }
                        })
                .when(res)
                .getStdout();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return output;
                            }
                        })
                .when(res)
                .getStderr();
        try {
            GceAvdInfo gceAvdInfo = GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234).get(0);
            assertEquals(gceAvdInfo.getStatus(), GceAvdInfo.GceStatus.SUCCESS);
            assertEquals(gceAvdInfo.instanceName(), "6a6a744e-0653-4926-b7b8-535d121a2fc9");
            assertEquals(gceAvdInfo.hostAndPort().getHost(), "10.0.80.227");
        } catch (TargetSetupError e) {
            e.printStackTrace();
        }
    }

    /** Test handling succeeded Oxygen device lease multiple devices request. */
    @Test
    public void testOxygenClientLeaseMultiDevicesSucceedResponse() {
        String output =
                "I0516 20:50:21.513705   21141 oxygen_proxy_cf_client.go:102] "
                        + "lease_infos:{session_id:\"17c788fa-be05-45e9-a8df-6e02f387d4a4\"  "
                        + "server_url:\"10.120.166.14\"  ports:{type:WATERFALL  value:26908}  "
                        + "ports:{type:WATERFALL_REVERSE_PORT_FORWARDER  value:24885}  "
                        + "leased_device_spec:{virtualization_type:CUTTLEFISH  "
                        + "build_artifacts:{build_id:\"8552002\"  "
                        + "build_target:\"cf_x86_64_phone-userdebug\"  "
                        + "build_branch:\"git_master\"  "
                        + "cuttlefish_build_artifacts:{build_id:\"8552002\"  "
                        + "build_target:\"cf_x86_64_phone-userdebug\"  image_type:DEVICE_IMAGE}}}"
                        + "  debug_info:{reserved_cores:5  region:\"us-east4\"  "
                        + "environment:\"prod\"  oxygen_version:\"v20220509-0008-rc01-cl447382102"
                        + "\"  prewarmed:false}}  lease_infos:{session_id:\"17c788fa-be05-45e9"
                        + "-a8df-6e02f387d4a4\"  server_url:\"10.120.166.14\"  "
                        + "ports:{type:WATERFALL  value:16590}  "
                        + "ports:{type:WATERFALL_REVERSE_PORT_FORWARDER  value:26010}  "
                        + "leased_device_spec:{virtualization_type:CUTTLEFISH  "
                        + "build_artifacts:{build_id:\"8558504\"  "
                        + "build_target:\"cf_x86_64_phone-userdebug\"  "
                        + "build_branch:\"git_master\"  "
                        + "cuttlefish_build_artifacts:{build_id:\"8558504\"  "
                        + "build_target:\"cf_x86_64_phone-userdebug\"  image_type:DEVICE_IMAGE}}}"
                        + "  index:1  debug_info:{reserved_cores:5  region:\"us-east4\"  "
                        + "environment:\"prod\"  oxygen_version:\"v20220509-0008-rc01-cl447382102"
                        + "\"  prewarmed:false}}  debug_info:{reserved_cores:5  "
                        + "region:\"us-east4\"  environment:\"prod\"  "
                        + "oxygen_version:\"v20220509-0008-rc01-cl447382102\"  prewarmed:false}";
        CommandResult res = Mockito.mock(CommandResult.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return CommandStatus.SUCCESS;
                            }
                        })
                .when(res)
                .getStatus();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return "";
                            }
                        })
                .when(res)
                .getStdout();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return output;
                            }
                        })
                .when(res)
                .getStderr();
        try {
            List<GceAvdInfo> gceAvdInfoList =
                    GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234);
            for (int i = 0; i < 2; i++) {
                GceAvdInfo gceAvdInfo =
                        GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234).get(i);
                assertEquals(gceAvdInfo.getStatus(), GceAvdInfo.GceStatus.SUCCESS);
                assertEquals(gceAvdInfo.instanceName(), "17c788fa-be05-45e9-a8df-6e02f387d4a4");
                assertEquals(gceAvdInfo.hostAndPort().getHost(), "10.120.166.14");
                assertEquals(gceAvdInfo.hostAndPort().getPort(), 1234 + i);
            }
        } catch (TargetSetupError e) {
            e.printStackTrace();
        }
    }

    /** Test handling corrupted Oxygen device lease request. */
    @Test
    public void testOxygenClientCorruptedResponse() {
        String corruptedOutput =
                "debug info lease result: leased_device_spec:{type:TESTTYPE"
                        + " build_artifacts:{build_id:\"P1234567\" build_target:\"target\""
                        + " build_branch:\"testBranch\"}} debug_info:{reserved_cores:1"
                        + " region:\"test-region\" environment:\"test\"}";
        CommandResult res = Mockito.mock(CommandResult.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return CommandStatus.SUCCESS;
                            }
                        })
                .when(res)
                .getStatus();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return "";
                            }
                        })
                .when(res)
                .getStdout();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return corruptedOutput;
                            }
                        })
                .when(res)
                .getStderr();
        try {
            GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            assertEquals(
                    "Failed to parse the output: debug "
                            + "info lease result: leased_device_spec:{type:TESTTYPE "
                            + "build_artifacts:{build_id:\"P1234567\" build_target:\"target\" "
                            + "build_branch:\"testBranch\"}} debug_info:{reserved_cores:1 "
                            + "region:\"test-region\" environment:\"test\"}",
                    expected.getMessage());
        }
    }

    /** Test handling timed out Oxygen device lease request. */
    @Test
    public void testOxygenClientTimeOut() {
        CommandResult res = Mockito.mock(CommandResult.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return CommandStatus.TIMED_OUT;
                            }
                        })
                .when(res)
                .getStatus();
        try {
            GceAvdInfo gceAvdInfo = GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234).get(0);
            assertEquals(gceAvdInfo.getStatus(), GceAvdInfo.GceStatus.FAIL);
            assertEquals(
                    gceAvdInfo.getErrorType(), InfraErrorIdentifier.OXYGEN_CLIENT_BINARY_TIMEOUT);
        } catch (TargetSetupError e) {
            e.printStackTrace();
        }
    }

    /** Test parsing failed Oxygen device lease request. */
    @Test
    public void testOxygenClientFailedResponse() {
        String output =
                "[Oxygen error: OXYGEN_CLIENT_BINARY_ERROR, CommandStatus: FAILED, output:  Error"
                        + " received while trying to lease device: rpc error: code = Internal "
                        + "desc = Internal error encountered. details = [type_url:\"this.random"
                        + ".com/try.rpc.DebugInfo\" value:\"\\x12\\x34\\x56[ORIGINAL ERROR] "
                        + "generic::internal: (length 6684)\"]";

        CommandResult res = Mockito.mock(CommandResult.class);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return CommandStatus.FAILED;
                            }
                        })
                .when(res)
                .getStatus();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return "";
                            }
                        })
                .when(res)
                .getStdout();
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock mock) throws Throwable {
                                return output;
                            }
                        })
                .when(res)
                .getStderr();
        try {
            GceAvdInfo.parseGceInfoFromOxygenClientOutput(res, 1234);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            assertEquals("Oxygen client failed to lease a device", expected.getMessage());
        }
    }

    /** Test CF fetch CVD wrapper metrics. */
    @Test
    public void testCfFetchCvdWrapperMetrics() throws Exception {
        String cuttlefish =
                " {\n"
                        + "    \"command\": \"create_cf\",\n"
                        + "    \"data\": {\n"
                        + "      \"devices\": [\n"
                        + "        {\n"
                        + "          \"ip\": \"34.71.83.182\",\n"
                        + "          \"instance_name\": \"ins-cf-x86-phone-userdebug\",\n"
                        + "          \"fetch_artifact_time\": 63.22,\n"
                        + "          \"gce_create_time\": 23.5,\n"
                        + "          \"launch_cvd_time\": 226.5,\n"
                        + "          \"fetch_cvd_wrapper_log\": {\n"
                        + "             \"cf_artifacts_fetch_source\": \"L1\",\n"
                        + "             \"cf_cache_wait_time_sec\": 1\n"
                        + "           }\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    \"errors\": [],\n"
                        + "    \"status\": \"SUCCESS\"\n"
                        + "  }";
        JSONObject res = new JSONObject(cuttlefish);
        JSONArray devices = res.getJSONObject("data").getJSONArray("devices");
        GceAvdInfo.addCfStartTimeMetrics((JSONObject) devices.get(0));
        Map<String, String> metrics = InvocationMetricLogger.getInvocationMetrics();
        assertEquals("1", metrics.get(InvocationMetricKey.CF_CACHE_WAIT_TIME.toString()));
        assertEquals("L1", metrics.get(InvocationMetricKey.CF_ARTIFACTS_FETCH_SOURCE.toString()));
    }

    @Test
    public void testRefineOxygenErrorType() throws Exception {
        assertEquals(
                InfraErrorIdentifier.OXYGEN_CLIENT_LEASE_ERROR,
                GceAvdInfo.refineOxygenErrorType(
                        "Lease aborted due to launcher failure: OxygenClient"));
        assertEquals(
                InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_FAILURE,
                GceAvdInfo.refineOxygenErrorType(
                        "Lease aborted due to launcher failure: some error"));
        assertEquals(
                InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_TIMEOUT,
                GceAvdInfo.refineOxygenErrorType(
                        "Lease aborted due to launcher failure: Timed out waiting for virtual"
                                + " device to start"));
    }
}

