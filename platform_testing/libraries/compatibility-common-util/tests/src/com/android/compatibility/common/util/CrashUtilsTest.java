/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.compatibility.common.util;

import com.android.compatibility.common.util.CrashUtils.Config.BacktraceFilterPattern;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.util.List;
import java.util.regex.Pattern;

/** Unit tests for {@link CrashUtils}. */
@RunWith(JUnit4.class)
public class CrashUtilsTest {

    private JSONArray mCrashes;

    @Before
    public void setUp() throws IOException {
        try (BufferedReader txtReader =
                new BufferedReader(
                        new InputStreamReader(
                                getClass().getClassLoader().getResourceAsStream("logcat.txt")))) {
            StringBuffer input = new StringBuffer();
            String tmp;
            while ((tmp = txtReader.readLine()) != null) {
                input.append(tmp + "\n");
            }
            mCrashes = CrashUtils.addAllCrashes(input.toString(), new JSONArray());
        }
    }

    @Test
    public void testGetAllCrashes() throws Exception {
        JSONArray expectedResults = new JSONArray();
        expectedResults.put(
                createCrashJson(
                        11071,
                        11189,
                        "AudioOut_D",
                        "/system/bin/audioserver",
                        "e9380000",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame(
                                        "/system/lib/libaudioutils.so",
                                        "memcpy_to_float_from_i16+5"),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        12736,
                        12761,
                        "Binder:12736_2",
                        "/system/bin/audioserver",
                        "0",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame("/system/lib/libc.so", "strlen+71"),
                                stackFrame("/system/lib/libc.so", "__strlen_chk+4"),
                                stackFrame(
                                        "/system/lib/libutils.so", "_ZN7android7String8C2EPKc+12"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZNK7android18HwModuleCollection19getDeviceDescriptorEjPKcS2_b+458"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZN7android18AudioPolicyManager27setDeviceConnectionStateIntEj24"
                                            + "audio_policy_dev_state_tPKcS3_+178"),
                                stackFrame("/system/lib/libaudiopolicyservice.so", null),
                                stackFrame(
                                        "/system/lib/libmedia.so",
                                        "_ZN7android20BnAudioPolicyService10onTransactEjRKNS_6ParcelEPS1_j+1256"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android7BBinder8transactEjRKNS_6ParcelEPS1_j+70"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14executeCommandEi+702"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState20getAndExecuteCommandEv+114"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14joinThreadPoolEb+46"),
                                stackFrame("/system/lib/libbinder.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        26201,
                        26227,
                        "Binder:26201_3",
                        "/system/bin/audioserver",
                        "0",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame("/system/lib/libc.so", "strlen+71"),
                                stackFrame("/system/lib/libc.so", "__strlen_chk+4"),
                                stackFrame(
                                        "/system/lib/libutils.so", "_ZN7android7String8C2EPKc+12"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZNK7android18HwModuleCollection19getDeviceDescriptorEjPKcS2_b+458"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZN7android18AudioPolicyManager27setDeviceConnectionStateIntEj24"
                                            + "audio_policy_dev_state_tPKcS3_+178"),
                                stackFrame("/system/lib/libaudiopolicyservice.so", null),
                                stackFrame(
                                        "/system/lib/libmedia.so",
                                        "_ZN7android20BnAudioPolicyService10onTransactEjRKNS_6ParcelEPS1_j+1256"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android7BBinder8transactEjRKNS_6ParcelEPS1_j+70"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14executeCommandEi+702"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState20getAndExecuteCommandEv+114"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14joinThreadPoolEb+46"),
                                stackFrame("/system/lib/libbinder.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        26246,
                        26282,
                        "Binder:26246_5",
                        "/system/bin/audioserver",
                        "0",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame("/system/lib/libc.so", "strlen+71"),
                                stackFrame("/system/lib/libc.so", "__strlen_chk+4"),
                                stackFrame(
                                        "/system/lib/libutils.so", "_ZN7android7String8C2EPKc+12"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZNK7android18HwModuleCollection19getDeviceDescriptorEjPKcS2_b+458"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZN7android18AudioPolicyManager27setDeviceConnectionStateIntEj24"
                                            + "audio_policy_dev_state_tPKcS3_+178"),
                                stackFrame("/system/lib/libaudiopolicyservice.so", null),
                                stackFrame(
                                        "/system/lib/libmedia.so",
                                        "_ZN7android20BnAudioPolicyService10onTransactEjRKNS_6ParcelEPS1_j+1256"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android7BBinder8transactEjRKNS_6ParcelEPS1_j+70"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14executeCommandEi+702"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState20getAndExecuteCommandEv+114"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14joinThreadPoolEb+46"),
                                stackFrame("/system/lib/libbinder.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        245,
                        245,
                        "installd",
                        "/system/bin/installd",
                        null,
                        "SIGABRT",
                        "'utils.cpp:67] Check failed: is_valid_package_name(package_name) == 0 '",
                        ImmutableList.of(
                                stackFrame("/system/lib64/libc.so", "tgkill+8"),
                                stackFrame("/system/lib64/libc.so", "pthread_kill+64"),
                                stackFrame("/system/lib64/libc.so", "raise+24"),
                                stackFrame("/system/lib64/libc.so", "abort+52"),
                                stackFrame(
                                        "/system/lib64/libbase.so",
                                        "_ZN7android4base10LogMessageD1Ev+1084"),
                                stackFrame("/system/bin/installd", null),
                                stackFrame("/system/bin/installd", null),
                                stackFrame("/system/bin/installd", null),
                                stackFrame("/system/bin/installd", null),
                                stackFrame("/system/lib64/libc.so", "__libc_init+88"),
                                stackFrame("/system/bin/installd", null))));
        expectedResults.put(
                createCrashJson(
                        6371,
                        8072,
                        "media.codec",
                        "omx@1.0-service",
                        "ed000000",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame("/system/lib/libstagefright_soft_hevcdec.so", null))));
        expectedResults.put(
                createCrashJson(
                        8373,
                        8414,
                        "loo",
                        "com.android.bluetooth",
                        null,
                        "SIGABRT",
                        "'[FATAL:allocation_tracker.cc(143)] Check failed: map_entry !="
                                + " allocations.end().",
                        ImmutableList.of(
                                stackFrame("/system/lib64/libc.so", "abort+120"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::debug::BreakDebugger()+20"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "logging::LogMessage::~LogMessage()+1068"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "allocation_tracker_notify_free(unsigned char, void*)+720"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "osi_free(void*)+20"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "l2c_fcr_cleanup(t_l2c_ccb*)+92"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "l2cu_release_ccb(t_l2c_ccb*)+176"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "l2c_csm_execute(t_l2c_ccb*, unsigned short, void*)+1852"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "L2CA_DisconnectRsp(unsigned short)+92"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "sdp_disconnect_ind(unsigned short, bool)+52"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "l2c_csm_execute(t_l2c_ccb*, unsigned short, void*)+3600"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "l2c_rcv_acl_data(BT_HDR*)+3980"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::debug::TaskAnnotator::RunTask(char const*, "
                                                + "base::PendingTask const&)+188"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::MessageLoop::RunTask(base::PendingTask const&)+444"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::MessageLoop::DeferOrRunPendingTask(base::PendingTask)+52"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::MessageLoop::DoWork()+356"),
                                stackFrame(
                                        "/system/lib64/libchrome.so",
                                        "base::MessagePumpDefault::Run(base::MessagePump::Delegate*)+220"),
                                stackFrame(
                                        "/system/lib64/libchrome.so", "base::RunLoop::Run()+136"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "btu_message_loop_run(void*)+248"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "work_queue_read_cb(void*)+92"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "run_reactor(reactor_t*, int)+320"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "reactor_start(reactor_t*)+84"),
                                stackFrame(
                                        "/system/lib64/hw/bluetooth.default.so",
                                        "run_thread(void*)+184"),
                                stackFrame("/system/lib64/libc.so", "__pthread_start(void*)+36"),
                                stackFrame("/system/lib64/libc.so", "__start_thread+68"))));
        expectedResults.put(
                createCrashJson(
                        8080,
                        11665,
                        "generic",
                        "/system/bin/mediaserver",
                        null,
                        "SIGABRT",
                        "'frameworks/av/media/libstagefright/MPEG4Extractor.cpp:6853 CHECK_EQ("
                                + " (unsigned)ptr[0],1u) failed: 129 vs. 1'",
                        ImmutableList.of(
                                stackFrame("/system/lib/libc.so", "tgkill+12"),
                                stackFrame("/system/lib/libc.so", "pthread_kill+32"),
                                stackFrame("/system/lib/libc.so", "raise+10"),
                                stackFrame("/system/lib/libc.so", "__libc_android_abort+34"),
                                stackFrame("/system/lib/libc.so", "abort+4"),
                                stackFrame("/system/lib/libcutils.so", "__android_log_assert+86"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor25avcc_getCodecSpecificInfo"
                                                + "ERNS_2spINS_7ABufferEEEPKcj+392"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor15checkConfigDataEjRKNS_2spINS_8MetaDataEEE+218"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor12checkSupportEjRKNS_2spINS_8MetaDataEEE+136"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+13060"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+12508"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+6174"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+6174"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+6174"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+6174"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor10parseChunkEPxi+6174"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor12readMetaDataEv+94"),
                                stackFrame(
                                        "/system/lib/libstagefright.so",
                                        "_ZN7android14MPEG4Extractor11getMetaDataEv+10"),
                                stackFrame(
                                        "/system/lib/libmediaplayerservice.so",
                                        "_ZN7android8NuPlayer13GenericSource18initFromDataSourceEv+386"),
                                stackFrame(
                                        "/system/lib/libmediaplayerservice.so",
                                        "_ZN7android8NuPlayer13GenericSource14onPrepareAsyncEv+238"),
                                stackFrame(
                                        "/system/lib/libstagefright_foundation.so",
                                        "_ZN7android8AHandler14deliverMessageERKNS_2spINS_8AMessageEEE+16"),
                                stackFrame(
                                        "/system/lib/libstagefright_foundation.so",
                                        "_ZN7android8AMessage7deliverEv+54"),
                                stackFrame(
                                        "/system/lib/libstagefright_foundation.so",
                                        "_ZN7android7ALooper4loopEv+224"),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+112"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+30"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        11071,
                        11189,
                        "synthetic_thread",
                        "synthetic_process_0",
                        "e9380000",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame(
                                        "/system/lib/libaudioutils.so",
                                        "memcpy_to_float_from_i16+5"),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame("/system/lib/libaudioflinger.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        12736,
                        12761,
                        "synthetic_thread",
                        "synthetic_process_1",
                        "0",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame("/system/lib/libc.so", "strlen+71"),
                                stackFrame("/system/lib/libc.so", "__strlen_chk+4"),
                                stackFrame(
                                        "/system/lib/libutils.so", "_ZN7android7String8C2EPKc+12"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZNK7android18HwModuleCollection19getDeviceDescriptorEjPKcS2_b+458"),
                                stackFrame(
                                        "/system/lib/libaudiopolicymanagerdefault.so",
                                        "_ZN7android18AudioPolicyManager27setDeviceConnectionStateIntEj24"
                                            + "audio_policy_dev_state_tPKcS3_+178"),
                                stackFrame("/system/lib/libaudiopolicyservice.so", null),
                                stackFrame(
                                        "/system/lib/libmedia.so",
                                        "_ZN7android20BnAudioPolicyService10onTransactEjRKNS_6ParcelEPS1_j+1256"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android7BBinder8transactEjRKNS_6ParcelEPS1_j+70"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14executeCommandEi+702"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState20getAndExecuteCommandEv+114"),
                                stackFrame(
                                        "/system/lib/libbinder.so",
                                        "_ZN7android14IPCThreadState14joinThreadPoolEb+46"),
                                stackFrame("/system/lib/libbinder.so", null),
                                stackFrame(
                                        "/system/lib/libutils.so",
                                        "_ZN7android6Thread11_threadLoopEPv+140"),
                                stackFrame("/system/lib/libc.so", "_ZL15__pthread_startPv+22"),
                                stackFrame("/system/lib/libc.so", "__start_thread+6"))));
        expectedResults.put(
                createCrashJson(
                        8925,
                        8925,
                        "CVE-2021-0684",
                        "/data/local/tmp/CVE-2021-0684",
                        "7e772b8cfbe0",
                        "SIGSEGV",
                        null,
                        ImmutableList.of(
                                stackFrame(
                                        "/system/lib64/libinputreader.so",
                                        "android::TouchInputMapper::assignPointerIds("
                                                + "android::TouchInputMapper::RawState const*, "
                                                + "android::TouchInputMapper::RawState*)+37"),
                                stackFrame(
                                        "/system/lib64/libinputreader.so",
                                        "android::TouchInputMapper::sync(long, long)+285"),
                                stackFrame(
                                        "/system/lib64/libinputreader.so",
                                        "android::MultiTouchInputMapper::process(android::RawEvent"
                                                + " const*)+14"),
                                stackFrame("/data/local/tmp/CVE-2021-0684", null),
                                stackFrame("/data/local/tmp/CVE-2021-0684", null),
                                stackFrame(
                                        "/apex/com.android.runtime/lib64/bionic/libc.so",
                                        "__libc_init+86"))));

        Assert.assertEquals(
                expectedResults.toString() + "\n" + mCrashes.toString() + "\n",
                expectedResults.toString(),
                mCrashes.toString());
    }

    /** Helper method to shorten code for readability. */
    private JSONObject stackFrame(String filename, String method) throws JSONException {
        return new JSONObject().put(CrashUtils.FILENAME, filename).put(CrashUtils.METHOD, method);
    }

    public JSONObject createCrashJson(
            int pid,
            int tid,
            String name,
            String process,
            String faultaddress,
            String signal,
            String abortMessage) {
        return createCrashJson(
                pid, tid, name, process, faultaddress, signal, abortMessage, ImmutableList.of());
    }

    public JSONObject createCrashJson(
            int pid,
            int tid,
            String name,
            String process,
            String faultaddress,
            String signal,
            String abortMessage,
            List<JSONObject> stacktrace) {
        JSONObject json = new JSONObject();
        try {
            json.put(CrashUtils.PID, pid);
            json.put(CrashUtils.TID, tid);
            json.put(CrashUtils.NAME, name);
            json.put(CrashUtils.PROCESS, process);
            json.put(CrashUtils.FAULT_ADDRESS, faultaddress);
            json.put(CrashUtils.SIGNAL, signal);
            json.put(CrashUtils.ABORT_MESSAGE, abortMessage);

            JSONArray stacktraceJson = new JSONArray();
            for (JSONObject stackframe : stacktrace) {
                stacktraceJson.put(stackframe);
            }
            json.put(CrashUtils.BACKTRACE, stacktraceJson);
        } catch (JSONException e) {}
        return json;
    }

    @Test
    public void testValidCrash() throws Exception {
        Assert.assertTrue(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile("synthetic_process_0"))));
    }

    @Test
    public void testMissingName() throws Exception {
        Assert.assertFalse(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile(""))));
    }

    @Test
    public void testSIGABRT() throws Exception {
        Assert.assertFalse(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile("installd"))));
    }

    @Test
    public void testFaultAddressBelowMin() throws Exception {
        Assert.assertFalse(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile("synthetic_process_1"))));
    }

    @Test
    public void testIgnoreMinAddressCheck() throws Exception {
        Assert.assertTrue(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(false)
                .setProcessPatterns(Pattern.compile("synthetic_process_1"))));
    }

    @Test
    public void testBadAbortMessage() throws Exception {
        Assert.assertFalse(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile("generic"))));
    }

    @Test
    public void testGoodAndBadCrashes() throws Exception {
        Assert.assertTrue(CrashUtils.securityCrashDetected(mCrashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(
                        Pattern.compile("synthetic_process_0"),
                        Pattern.compile("generic"))));
    }

    @Test
    public void testNullFaultAddress() throws Exception {
        JSONArray crashes = new JSONArray();
        crashes.put(
                createCrashJson(8373, 8414, "loo", "com.android.bluetooth", null, "SIGSEGV", ""));
        Assert.assertTrue(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .checkMinAddress(true)
                .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth"))));
    }

    @Test
    public void testAbortMessageInclude() throws Exception {
        JSONArray crashes = new JSONArray();
        crashes.put(createCrashJson(8373, 8414, "loo", "com.android.bluetooth", null, "SIGABRT",
              "'[FATAL:allocation_tracker.cc(143)] Check failed: map_entry != allocations.end()."));
        Assert.assertTrue(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .appendAbortMessageIncludes("Check failed:")
                .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth"))));

        Assert.assertFalse(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .appendAbortMessageIncludes("include not matches")
                .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth"))));
    }

    @Test
    public void testAbortMessageExclude() throws Exception {
        JSONArray crashes = new JSONArray();
        crashes.put(createCrashJson(8373, 8414, "loo", "com.android.bluetooth", null, "SIGABRT",
              "'[FATAL:allocation_tracker.cc(143)] Check failed: map_entry != allocations.end()."));
        Assert.assertFalse(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .appendAbortMessageExcludes("Check failed:")
                .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth"))));

        Assert.assertTrue(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .appendAbortMessageExcludes("exclude not matches")
                .setProcessPatterns(Pattern.compile("com\\.android\\.bluetooth"))));
    }

    @Test
    public void testAbortMessageExcludeCannotLink() throws Exception {
        JSONArray crashes = new JSONArray();
        crashes.put(createCrashJson(
                18959, 18959, "CVE-2020-0073", "/data/local/tmp/CVE-2020-0073", null, "SIGABRT",
                "'CANNOT LINK EXECUTABLE \"/data/local/tmp/CVE-2020-0073\": library "
                + "\"libnfc-nci.so\" (\"(default)\", \"/data/local/tmp/CVE-2020-0073\", \"\") "
                + "not found'"));
        Assert.assertFalse(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .setProcessPatterns(Pattern.compile("CVE-2020-0073"))));

        crashes.put(createCrashJson(
                5105, 5105, "CVE-2015-6616-2", "/data/local/tmp/CVE-2015-6616-2", null, "SIGABRT",
                "'CANNOT LINK EXECUTABLE \"/data/local/tmp/CVE-2015-6616-2\": "
                + "cannot locate symbol \""
                + "_ZN7android14MediaExtractor17CreateFromServiceERKNS_2spINS_10DataSourceEEEPKc"
                + "\" referenced by \"/data/local/tmp/CVE-2015-6616-2\"...'"));
        Assert.assertFalse(CrashUtils.securityCrashDetected(crashes, new CrashUtils.Config()
                .appendSignals(CrashUtils.SIGABRT)
                .setProcessPatterns(Pattern.compile("CVE-2015-6616-2"))));

    }

    @Test
    public void testBacktraceFilterIncludeFilename() throws Exception {
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern("libaudioutils", null))));
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern("libstagefright", null),
                                        new BacktraceFilterPattern("libaudioflinger\\.so", null))));
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern("libstagefright", null))));
    }

    @Test
    public void testBacktraceFilterExcludeFilename() throws Exception {
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(
                                        new BacktraceFilterPattern("libaudioutils", null))));
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(
                                        new BacktraceFilterPattern("libstagefright", null),
                                        new BacktraceFilterPattern("libaudioflinger\\.so", null))));
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(
                                        new BacktraceFilterPattern("libstagefright", null))));
    }

    @Test
    public void testBacktraceFilterIncludeMethodName() throws Exception {
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern(null, "memcpy_to_float"))));
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern(null, "strlen"),
                                        new BacktraceFilterPattern(null, "memcpy_[^_]+_float"))));
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(new BacktraceFilterPattern(null, "strlen"))));
    }

    @Test
    public void testBacktraceFilterExcludeMethodName() throws Exception {
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(
                                        new BacktraceFilterPattern(null, "memcpy_to_float"))));
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(
                                        new BacktraceFilterPattern(null, "strlen"),
                                        new BacktraceFilterPattern(null, "memcpy_[^_]+_float"))));
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceExcludes(new BacktraceFilterPattern(null, "strlen"))));
    }

    @Test
    public void testBacktraceFilterCombinations() throws Exception {
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(new BacktraceFilterPattern(null, null))));
        Assert.assertTrue(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern("libaudioutils", "memcpy"))));
        Assert.assertFalse(
                CrashUtils.securityCrashDetected(
                        mCrashes,
                        new CrashUtils.Config()
                                .checkMinAddress(true)
                                .setProcessPatterns(Pattern.compile("synthetic_process_0"))
                                .setBacktraceIncludes(
                                        new BacktraceFilterPattern("libaudioutils", "strlen"))));
    }
}
