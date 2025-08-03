/*
 * Copyright (c) 2009-2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *            notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *            notice, this list of conditions and the following disclaimer in the
 *            documentation and/or other materials provided with the distribution.
 *        * Neither the name of The Linux Foundation nor
 *            the names of its contributors may be used to endorse or promote
 *            products derived from this software without specific prior written
 *            permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOG_TAG "android_hardware_fm"

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>
#include "utils/misc.h"
#include <cutils/properties.h>
#include <fcntl.h>
#include <math.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <assert.h>
#include <dlfcn.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"
#include "radio-helium.h"
#include "FmIoctlsInterface.h"
#include "ConfigFmThs.h"

#define RADIO "/dev/radio0"
#define FM_JNI_SUCCESS 0L
#define FM_JNI_FAILURE -1L
#define SEARCH_DOWN 0
#define SEARCH_UP 1
#define HIGH_BAND 2
#define LOW_BAND  1
#define CAL_DATA_SIZE 23
#define V4L2_CTRL_CLASS_USER 0x00980000
#define V4L2_CID_PRIVATE_IRIS_SET_CALIBRATION           (V4L2_CTRL_CLASS_USER + 0x92A)
#define V4L2_CID_PRIVATE_TAVARUA_ON_CHANNEL_THRESHOLD   (V4L2_CTRL_CLASS_USER + 0x92B)
#define V4L2_CID_PRIVATE_TAVARUA_OFF_CHANNEL_THRESHOLD  (V4L2_CTRL_CLASS_USER + 0x92C)
#define V4L2_CID_PRIVATE_IRIS_SET_SPURTABLE             (V4L2_CTRL_CLASS_USER + 0x92D)
#define TX_RT_LENGTH       63
#define WAIT_TIMEOUT 200000 /* 200*1000us */
#define TX_RT_DELIMITER    0x0d
#define PS_LEN    9
#define V4L2_CID_PRIVATE_TAVARUA_STOP_RDS_TX_RT 0x08000017
#define V4L2_CID_PRIVATE_TAVARUA_STOP_RDS_TX_PS_NAME 0x08000016
#define V4L2_CID_PRIVATE_UPDATE_SPUR_TABLE 0x08000034
#define V4L2_CID_PRIVATE_TAVARUA_TX_SETPSREPEATCOUNT 0x08000034
#define MASK_PI                    (0x0000FFFF)
#define MASK_PI_MSB                (0x0000FF00)
#define MASK_PI_LSB                (0x000000FF)
#define MASK_PTY                   (0x0000001F)
#define MASK_TXREPCOUNT            (0x0000000F)

enum search_dir_t {
    SEEK_UP,
    SEEK_DN,
    SCAN_UP,
    SCAN_DN
};

static JavaVM *g_jVM = NULL;

namespace android {

const char *FM_LIBRARY_NAME = "fm_helium.so";
const char *FM_LIBRARY_SYMBOL_NAME = "FM_HELIUM_LIB_INTERFACE";
void *lib_handle;
static int slimbus_flag = 0;

static JNIEnv *mCallbackEnv = NULL;
static jobject mCallbacksObj = NULL;
static bool mCallbacksObjCreated = false;

static jclass javaClassRef;
static jmethodID method_psInfoCallback;
static jmethodID method_rtCallback;
static jmethodID method_ertCallback;
static jmethodID method_aflistCallback;
static jmethodID method_rtplusCallback;
static jmethodID method_eccCallback;

jmethodID method_enableCallback;
jmethodID method_tuneCallback;
jmethodID method_seekCmplCallback;
jmethodID method_scanNxtCallback;
jmethodID method_srchListCallback;
jmethodID method_stereostsCallback;
jmethodID method_rdsAvlStsCallback;
jmethodID method_disableCallback;
jmethodID method_getSigThCallback;
jmethodID method_getChDetThrCallback;
jmethodID method_defDataRdCallback;
jmethodID method_getBlendCallback;
jmethodID method_setChDetThrCallback;
jmethodID method_defDataWrtCallback;
jmethodID method_setBlendCallback;
jmethodID method_getStnParamCallback;
jmethodID method_getStnDbgParamCallback;
jmethodID method_enableSlimbusCallback;
jmethodID method_enableSoftMuteCallback;
jmethodID method_FmReceiverJNICtor;

static bool checkCallbackThread() {
   JNIEnv* env = AndroidRuntime::getJNIEnv();
   if (mCallbackEnv != env || mCallbackEnv == NULL)
   {
       ALOGE("Callback env check fail: env: %p, callback: %p", env, mCallbackEnv);
       return false;
   }
    return true;
}

void fm_enabled_cb(void) {
    ALOGD("Entered %s", __func__);

    if (slimbus_flag) {
        if (!checkCallbackThread())
            return;

        mCallbackEnv->CallVoidMethod(mCallbacksObj, method_enableCallback);
    } else {
        if (mCallbackEnv != NULL) {
            ALOGE("javaObjectRef creating");
            jobject javaObjectRef =  mCallbackEnv->NewObject(javaClassRef, method_FmReceiverJNICtor);
            mCallbacksObj = javaObjectRef;
            ALOGE("javaObjectRef = %p mCallbackobject =%p \n",javaObjectRef,mCallbacksObj);
            mCallbackEnv->CallVoidMethod(mCallbacksObj, method_enableCallback);
        }
    }
    ALOGD("exit  %s", __func__);
}

void fm_tune_cb(int Freq)
{
    ALOGD("TUNE:Freq:%d", Freq);
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_tuneCallback, (jint) Freq);
}

void fm_seek_cmpl_cb(int Freq)
{
    ALOGI("SEEK_CMPL: Freq: %d", Freq);
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_seekCmplCallback, (jint) Freq);
}

void fm_scan_next_cb(void)
{
    ALOGI("SCAN_NEXT");
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_scanNxtCallback);
}

void fm_srch_list_cb(uint16_t *scan_tbl)
{
    ALOGI("SRCH_LIST");
    jbyteArray srch_buffer = NULL;

    if (!checkCallbackThread())
        return;

    srch_buffer = mCallbackEnv->NewByteArray(STD_BUF_SIZE);
    if (srch_buffer == NULL) {
        ALOGE(" af list allocate failed :");
        return;
    }
    mCallbackEnv->SetByteArrayRegion(srch_buffer, 0, STD_BUF_SIZE, (jbyte *)scan_tbl);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_srchListCallback, srch_buffer);
    mCallbackEnv->DeleteLocalRef(srch_buffer);
}

void fm_stereo_status_cb(bool stereo)
{
    ALOGI("STEREO: %d", stereo);
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_stereostsCallback, (jboolean) stereo);
}

void fm_rds_avail_status_cb(bool rds_avl)
{
    ALOGD("fm_rds_avail_status_cb: %d", rds_avl);
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_rdsAvlStsCallback, (jboolean) rds_avl);
}

void fm_af_list_update_cb(uint16_t *af_list)
{
    ALOGD("AF_LIST");
    jbyteArray af_buffer = NULL;

    if (!checkCallbackThread())
        return;

    af_buffer = mCallbackEnv->NewByteArray(STD_BUF_SIZE);
    if (af_buffer == NULL) {
        ALOGE(" af list allocate failed :");
        return;
    }

    mCallbackEnv->SetByteArrayRegion(af_buffer, 0, STD_BUF_SIZE,(jbyte *)af_list);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_aflistCallback,af_buffer);
    mCallbackEnv->DeleteLocalRef(af_buffer);
}

void fm_rt_update_cb(char *rt)
{
    ALOGD("RT_EVT: " );
    jbyteArray rt_buff = NULL;
    int len;

    if (!checkCallbackThread())
        return;

    len  = (int)(rt[0] & 0xFF);
    ALOGD(" rt data len=%d :",len);
    len = len+5;

    ALOGD(" rt data len=%d :",len);
    rt_buff = mCallbackEnv->NewByteArray(len);
    if (rt_buff == NULL) {
        ALOGE(" ps data allocate failed :");
        return;
    }

    mCallbackEnv->SetByteArrayRegion(rt_buff, 0, len,(jbyte *)rt);

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_rtCallback,rt_buff);
    mCallbackEnv->DeleteLocalRef(rt_buff);
}

void fm_ps_update_cb(char *ps)
{
    jbyteArray ps_data = NULL;
    int len;
    int numPs;
    if (!checkCallbackThread())
        return;

    numPs  = (int)(ps[0] & 0xFF);
    len = (numPs *8)+5;

    ALOGD(" ps data len=%d :",len);
    ps_data = mCallbackEnv->NewByteArray(len);
    if(ps_data == NULL) {
       ALOGE(" ps data allocate failed :");
       return;
    }

    mCallbackEnv->SetByteArrayRegion(ps_data, 0, len,(jbyte *)ps);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_psInfoCallback,ps_data);
    mCallbackEnv->DeleteLocalRef(ps_data);
}

void fm_oda_update_cb(void)
{
    ALOGD("ODA_EVT");
}

void fm_rt_plus_update_cb(char *rt_plus)
{
    jbyteArray RtPlus = NULL;
    ALOGD("RT_PLUS");
    int len;

    len =  (int)(rt_plus[0] & 0xFF);
    ALOGD(" rt plus len=%d :",len);
    if (!checkCallbackThread())
        return;

    RtPlus = mCallbackEnv->NewByteArray(len);
    if (RtPlus == NULL) {
        ALOGE(" rt plus data allocate failed :");
        return;
    }
    mCallbackEnv->SetByteArrayRegion(RtPlus, 0, len,(jbyte *)rt_plus);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_rtplusCallback,RtPlus);
    mCallbackEnv->DeleteLocalRef(RtPlus);
}

void fm_ert_update_cb(char *ert)
{
    ALOGD("ERT_EVT");
    jbyteArray ert_buff = NULL;
    int len;

    if (!checkCallbackThread())
        return;

    len = (int)(ert[0] & 0xFF);
    len = len+3;

    ALOGI(" ert data len=%d :",len);
    ert_buff = mCallbackEnv->NewByteArray(len);
    if (ert_buff == NULL) {
        ALOGE(" ert data allocate failed :");
        return;
    }

    mCallbackEnv->SetByteArrayRegion(ert_buff, 0, len,(jbyte *)ert);
   // jbyte* bytes= mCallbackEnv->GetByteArrayElements(ert_buff,0);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_ertCallback,ert_buff);
    mCallbackEnv->DeleteLocalRef(ert_buff);
}

void fm_ext_country_code_cb(char *ecc)
{
    ALOGI("Extended Contry code ");
    jbyteArray ecc_buff = NULL;
    int len;

    if (!checkCallbackThread())
        return;

    len = (int)(ecc[0] & 0xFF);

    ALOGI(" ecc data len=%d :",len);
    ecc_buff = mCallbackEnv->NewByteArray(len);
    if (ecc_buff == NULL) {
        ALOGE(" ecc data allocate failed :");
        return;
    }
    mCallbackEnv->SetByteArrayRegion(ecc_buff, 0, len,(jbyte *)ecc);
    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_eccCallback,ecc_buff);
    mCallbackEnv->DeleteLocalRef(ecc_buff);
}


void rds_grp_cntrs_rsp_cb(char * evt_buffer __unused)
{
   ALOGD("rds_grp_cntrs_rsp_cb");
}

void rds_grp_cntrs_ext_rsp_cb(char * evt_buffer __unused)
{
   ALOGE("rds_grp_cntrs_ext_rsp_cb");
}

void fm_disabled_cb(void)
{
    ALOGE("DISABLE");
    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_disableCallback);
    mCallbacksObjCreated = false;
}

void fm_peek_rsp_cb(char *peek_rsp __unused) {
    ALOGD("fm_peek_rsp_cb");
}

void fm_ssbi_peek_rsp_cb(char *ssbi_peek_rsp __unused){
    ALOGD("fm_ssbi_peek_rsp_cb");
}

void fm_agc_gain_rsp_cb(char *agc_gain_rsp __unused){
    ALOGE("fm_agc_gain_rsp_cb");
}

void fm_ch_det_th_rsp_cb(char *ch_det_rsp __unused){
    ALOGD("fm_ch_det_th_rsp_cb");
}

static void fm_thread_evt_cb(unsigned int event) {
    JavaVM* vm = AndroidRuntime::getJavaVM();
    if (event  == 0) {
        JavaVMAttachArgs args;
        char name[] = "FM Service Callback Thread";
        args.version = JNI_VERSION_1_6;
        args.name = name;
        args.group = NULL;
       vm->AttachCurrentThread(&mCallbackEnv, &args);
        ALOGD("Callback thread attached: %p", mCallbackEnv);
    } else if (event == 1) {
        if (!checkCallbackThread()) {
            ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
            return;
        }
        vm->DetachCurrentThread();
        mCallbackEnv = NULL;
    }
}

static void fm_get_sig_thres_cb(int val, int status)
{
    ALOGD("Get signal Thres callback");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_getSigThCallback, val, status);
}

static void fm_get_ch_det_thr_cb(int val, int status)
{
    ALOGD("fm_get_ch_det_thr_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_getChDetThrCallback, val, status);
}

static void fm_set_ch_det_thr_cb(int status)
{
    ALOGD("fm_set_ch_det_thr_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_setChDetThrCallback, status);
}

static void fm_def_data_read_cb(int val, int status)
{
    ALOGD("fm_def_data_read_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_defDataRdCallback, val, status);
}

static void fm_def_data_write_cb(int status)
{
    ALOGD("fm_def_data_write_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_defDataWrtCallback, status);
}

static void fm_get_blend_cb(int val, int status)
{
    ALOGD("fm_get_blend_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_getBlendCallback, val, status);
}

static void fm_set_blend_cb(int status)
{
    ALOGD("fm_set_blend_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_setBlendCallback, status);
}

static void fm_get_station_param_cb(int val, int status)
{
    ALOGD("fm_get_station_param_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_getStnParamCallback, val, status);
}

static void fm_get_station_debug_param_cb(int val, int status)
{
    ALOGD("fm_get_station_debug_param_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_getStnDbgParamCallback, val, status);
}

static void fm_enable_slimbus_cb(int status)
{
    ALOGD("++fm_enable_slimbus_cb mCallbacksObjCreatedtor: %d", mCallbacksObjCreated);
    slimbus_flag = 1;
    if (mCallbacksObjCreated == false) {
        jobject javaObjectRef =  mCallbackEnv->NewObject(javaClassRef, method_FmReceiverJNICtor);
        mCallbacksObj = javaObjectRef;
        mCallbacksObjCreated = true;
        mCallbackEnv->CallVoidMethod(mCallbacksObj, method_enableSlimbusCallback, status);
        return;
    }

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_enableSlimbusCallback, status);
    ALOGD("--fm_enable_slimbus_cb");
}

static void fm_enable_softmute_cb(int status)
{
    ALOGD("++fm_enable_softmute_cb");

    if (!checkCallbackThread())
        return;

    mCallbackEnv->CallVoidMethod(mCallbacksObj, method_enableSoftMuteCallback, status);
    ALOGD("--fm_enable_softmute_cb");
}

fm_interface_t *vendor_interface;
static   fm_hal_callbacks_t fm_callbacks = {
    sizeof(fm_callbacks),
    fm_enabled_cb,
    fm_tune_cb,
    fm_seek_cmpl_cb,
    fm_scan_next_cb,
    fm_srch_list_cb,
    fm_stereo_status_cb,
    fm_rds_avail_status_cb,
    fm_af_list_update_cb,
    fm_rt_update_cb,
    fm_ps_update_cb,
    fm_oda_update_cb,
    fm_rt_plus_update_cb,
    fm_ert_update_cb,
    fm_disabled_cb,
    rds_grp_cntrs_rsp_cb,
    rds_grp_cntrs_ext_rsp_cb,
    fm_peek_rsp_cb,
    fm_ssbi_peek_rsp_cb,
    fm_agc_gain_rsp_cb,
    fm_ch_det_th_rsp_cb,
    fm_ext_country_code_cb,
    fm_thread_evt_cb,
    fm_get_sig_thres_cb,
    fm_get_ch_det_thr_cb,
    fm_def_data_read_cb,
    fm_get_blend_cb,
    fm_set_ch_det_thr_cb,
    fm_def_data_write_cb,
    fm_set_blend_cb,
    fm_get_station_param_cb,
    fm_get_station_debug_param_cb,
    fm_enable_slimbus_cb,
    fm_enable_softmute_cb
};
/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_acquireFdNative
        (JNIEnv* env, jobject thiz __unused, jstring path)
{
    int fd;
    int i = 0, err;
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    char versionStr[40] = {'\0'};
    int init_success = 0;
    jboolean isCopy;
    v4l2_capability cap;
    const char* radio_path = env->GetStringUTFChars(path, &isCopy);
    if(radio_path == NULL){
        return FM_JNI_FAILURE;
    }
    fd = open(radio_path, O_RDONLY);
    if(isCopy == JNI_TRUE){
        env->ReleaseStringUTFChars(path, radio_path);
    }
    if(fd < 0){
        return FM_JNI_FAILURE;
    }
    //Read the driver verions
    err = ioctl(fd, VIDIOC_QUERYCAP, &cap);

    ALOGD("VIDIOC_QUERYCAP returns :%d: version: %d \n", err , cap.version );

    if( err >= 0 ) {
       ALOGD("Driver Version(Same as ChipId): %x \n",  cap.version );
       /*Conver the integer to string */
       snprintf(versionStr, sizeof(versionStr), "%d", cap.version);
       property_set("vendor.hw.fm.version", versionStr);
    } else {
       close(fd);
       return FM_JNI_FAILURE;
    }

    property_get("vendor.qcom.bluetooth.soc", value, NULL);

    ALOGD("BT soc is %s\n", value);

    if ((strcmp(value, "rome") != 0) && (strcmp(value, "hastings") != 0))
    {
       /*Set the mode for soc downloader*/
       property_set("vendor.hw.fm.mode", "normal");
       /* Need to clear the hw.fm.init firstly */
       property_set("vendor.hw.fm.init", "0");
       property_set("ctl.start", "vendor.fm");
       sched_yield();
       for(i=0; i<45; i++) {
         property_get("vendor.hw.fm.init", value, NULL);
         if (strcmp(value, "1") == 0) {
            init_success = 1;
            break;
         } else {
            usleep(WAIT_TIMEOUT);
         }
       }
       ALOGE("init_success:%d after %f seconds \n", init_success, 0.2*i);
       if(!init_success) {
         property_set("ctl.stop", "vendor.fm");
         // close the fd(power down)
         close(fd);
         return FM_JNI_FAILURE;
       }
    }
    return fd;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_closeFdNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd)
{
    char value[PROPERTY_VALUE_MAX] = {'\0'};

    property_get("vendor.qcom.bluetooth.soc", value, NULL);

    ALOGD("BT soc is %s\n", value);

    if ((strcmp(value, "rome") != 0) && (strcmp(value, "hastings") != 0))
    {
        property_set("ctl.stop", "vendor.fm");
    }
    close(fd);
    return FM_JNI_SUCCESS;
}

static bool is_soc_cherokee() {
    char value[PROPERTY_VALUE_MAX] = {'\0'};
    property_get("vendor.qcom.bluetooth.soc", value, NULL);
    ALOGD("BT soc is %s\n", value);

    if(strcmp(value, "cherokee") == 0)
        return true;
    else
        return false;
}
/********************************************************************
 * Current JNI
 *******************************************************************/

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getFreqNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused)
{
    int err;
    long freq;
    if (is_soc_cherokee())
    {
        err = vendor_interface->get_fm_ctrl(V4L2_CID_PRV_IRIS_FREQ, (int *)&freq);
        if (err == FM_JNI_SUCCESS) {
            err = freq;
        } else {
            err = FM_JNI_FAILURE;
            ALOGE("%s: get freq failed\n", LOG_TAG);
        }
    }
    else
    {
        if (fd >= 0) {
            err = FmIoctlsInterface :: get_cur_freq(fd, freq);
            if(err < 0) {
               err = FM_JNI_FAILURE;
               ALOGE("%s: get freq failed\n", LOG_TAG);
            } else {
               err = freq;
            }
        } else {
            ALOGE("%s: get freq failed because fd is negative, fd: %d\n",
                  LOG_TAG, fd);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/*native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setFreqNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint freq)
{
    int err;
    if (is_soc_cherokee())
    {
        err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_IRIS_FREQ, freq);
    }
    else
    {
        if ((fd >= 0) && (freq > 0)) {
            err = FmIoctlsInterface :: set_freq(fd, freq);
            if (err < 0) {
                ALOGE("%s: set freq failed, freq: %d\n", LOG_TAG, freq);
                err = FM_JNI_FAILURE;
            } else {
                err = FM_JNI_SUCCESS;
            }
        } else {
            ALOGE("%s: set freq failed because either fd/freq is negative,\
                   fd: %d, freq: %d\n", LOG_TAG, fd, freq);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setControlNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint id,
    jint value)
{
    int err;
    ALOGE("id(%x) value: %x\n", id, value);
    if (is_soc_cherokee())
    {
        err = vendor_interface->set_fm_ctrl(id, value);
    }
    else
    {
        if ((fd >= 0) && (id >= 0)) {
            err = FmIoctlsInterface :: set_control(fd, id, value);
            if (err < 0) {
                ALOGE("%s: set control failed, id: %d\n", LOG_TAG, id);
                err = FM_JNI_FAILURE;
            } else {
                err = FM_JNI_SUCCESS;
            }
        } else {
            ALOGE("%s: set control failed because either fd/id is negavtive,\
                   fd: %d, id: %d\n", LOG_TAG, fd, id);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getControlNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint id)
{
    int err;
    long val;

    ALOGE("id(%x)\n", id);
    if (is_soc_cherokee())
    {
        err = vendor_interface->get_fm_ctrl(id, (int *)&val);
        if (err < 0) {
            ALOGE("%s: get control failed, id: %d\n", LOG_TAG, id);
            err = FM_JNI_FAILURE;
        } else {
            err = val;
        }
    }
    else
    {
        if ((fd >= 0) && (id >= 0)) {
            err = FmIoctlsInterface :: get_control(fd, id, val);
            if (err < 0) {
                ALOGE("%s: get control failed, id: %d\n", LOG_TAG, id);
                err = FM_JNI_FAILURE;
            } else {
                err = val;
            }
        } else {
            ALOGE("%s: get control failed because either fd/id is negavtive,\
                   fd: %d, id: %d\n", LOG_TAG, fd, id);
            err = FM_JNI_FAILURE;
        }
    }

    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_startSearchNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint dir)
{
    int err;
    if (is_soc_cherokee())
    {
        err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_IRIS_SEEK, dir);
        if (err < 0) {
            ALOGE("%s: search failed, dir: %d\n", LOG_TAG, dir);
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    }
    else
    {
        if ((fd >= 0) && (dir >= 0)) {
            ALOGD("startSearchNative: Issuing the VIDIOC_S_HW_FREQ_SEEK");
            err = FmIoctlsInterface :: start_search(fd, dir);
            if (err < 0) {
                ALOGE("%s: search failed, dir: %d\n", LOG_TAG, dir);
                err = FM_JNI_FAILURE;
            } else {
                err = FM_JNI_SUCCESS;
            }
        } else {
            ALOGE("%s: search failed because either fd/dir is negative,\
                   fd: %d, dir: %d\n", LOG_TAG, fd, dir);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_cancelSearchNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused)
{
    int err;

    if (is_soc_cherokee())
    {
        err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_SRCHON, 0);
        if (err < 0) {
            ALOGE("%s: cancel search failed\n", LOG_TAG);
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    }
    else
    {
        if (fd >= 0) {
            err = FmIoctlsInterface :: set_control(fd, V4L2_CID_PRV_SRCHON, 0);
            if (err < 0) {
                ALOGE("%s: cancel search failed\n", LOG_TAG);
                err = FM_JNI_FAILURE;
            } else {
                err = FM_JNI_SUCCESS;
            }
        } else {
            ALOGE("%s: cancel search failed because fd is negative, fd: %d\n",
                   LOG_TAG, fd);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRSSINative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused)
{
    int err;
    long rmssi;

    if (is_soc_cherokee())
    {
        err = vendor_interface->get_fm_ctrl(V4L2_CID_PRV_IRIS_RMSSI, (int *)&rmssi);
        if (err < 0) {
            ALOGE("%s: Get Rssi failed", LOG_TAG);
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    }
    else
    {
        if (fd >= 0) {
            err = FmIoctlsInterface :: get_rmssi(fd, rmssi);
            if (err < 0) {
                ALOGE("%s: get rmssi failed\n", LOG_TAG);
                err = FM_JNI_FAILURE;
            } else {
                err = rmssi;
            }
        } else {
            ALOGE("%s: get rmssi failed because fd is negative, fd: %d\n",
                   LOG_TAG, fd);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setBandNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint low,
    jint high)
{
    int err;
    if (is_soc_cherokee())
    {
        err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_IRIS_UPPER_BAND, high);
        if (err < 0) {
            ALOGE("%s: set band failed, high: %d\n", LOG_TAG, high);
            err = FM_JNI_FAILURE;
            return err;
        }
        err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_IRIS_LOWER_BAND, low);
        if (err < 0) {
            ALOGE("%s: set band failed, low: %d\n", LOG_TAG, low);
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    }
    else
    {
        if ((fd >= 0) && (low >= 0) && (high >= 0)) {
            err = FmIoctlsInterface :: set_band(fd, low, high);
            if (err < 0) {
                ALOGE("%s: set band failed, low: %d, high: %d\n",
                       LOG_TAG, low, high);
                err = FM_JNI_FAILURE;
            } else {
                err = FM_JNI_SUCCESS;
            }
        } else {
            ALOGE("%s: set band failed because either fd/band is negative,\
                   fd: %d, low: %d, high: %d\n", LOG_TAG, fd, low, high);
            err = FM_JNI_FAILURE;
        }
    }
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getLowerBandNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd)
{
    int err;
    ULINT freq;
if (is_soc_cherokee())
{
    err = vendor_interface->get_fm_ctrl(V4L2_CID_PRV_IRIS_LOWER_BAND, (int *)&freq);
    if (err < 0) {
        ALOGE("%s: get lower band failed\n", LOG_TAG);
        err = FM_JNI_FAILURE;
    } else {
        err = freq;
    }
    return err;
}
else
{
    if (fd >= 0) {
        err = FmIoctlsInterface :: get_lowerband_limit(fd, freq);
        if (err < 0) {
            ALOGE("%s: get lower band failed\n", LOG_TAG);
            err = FM_JNI_FAILURE;
        } else {
            err = freq;
        }
    } else {
        ALOGE("%s: get lower band failed because fd is negative,\
               fd: %d\n", LOG_TAG, fd);
        err = FM_JNI_FAILURE;
    }
}
    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getUpperBandNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd)
{
    int err;
    ULINT freq;
if (is_soc_cherokee())
{
    err = vendor_interface->get_fm_ctrl(V4L2_CID_PRV_IRIS_UPPER_BAND, (int *)&freq);
    if (err < 0) {
        ALOGE("%s: get upper band failed\n", LOG_TAG);
        err = FM_JNI_FAILURE;
    } else {
        err = freq;
    }
    return err;
}
else
{
    if (fd >= 0) {
        err = FmIoctlsInterface :: get_upperband_limit(fd, freq);
        if (err < 0) {
            ALOGE("%s: get lower band failed\n", LOG_TAG);
            err = FM_JNI_FAILURE;
        } else {
            err = freq;
        }
    } else {
        ALOGE("%s: get lower band failed because fd is negative,\
               fd: %d\n", LOG_TAG, fd);
        err = FM_JNI_FAILURE;
    }
}
    return err;
}

static jint android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd, jint val)
{

    int err;
if (is_soc_cherokee())
{
    err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_IRIS_AUDIO_MODE, val);
    if (err < 0) {
        ALOGE("%s: set audio mode failed\n", LOG_TAG);
        err = FM_JNI_FAILURE;
    } else {
        err = FM_JNI_SUCCESS;
    }
    return err;
}
else
{
    if (fd >= 0) {
        err = FmIoctlsInterface :: set_audio_mode(fd, (enum AUDIO_MODE)val);
        if (err < 0) {
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    } else {
        err = FM_JNI_FAILURE;
    }
}
    return err;
}


/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getBufferNative
    (JNIEnv * env, jobject thiz __unused, jint fd, jbyteArray buff, jint index)
{
    int err;
    jboolean isCopy;
    jbyte *byte_buffer = NULL;

    if ((fd >= 0) && (index >= 0)) {
        ALOGE("index: %d\n", index);
        byte_buffer = env->GetByteArrayElements(buff, &isCopy);
        err = FmIoctlsInterface :: get_buffer(fd,
                                               (char *)byte_buffer,
                                               STD_BUF_SIZE,
                                               index);
        if (err < 0) {
            err = FM_JNI_FAILURE;
        }
        if (buff != NULL) {
            ALOGE("Free the buffer\n");
            env->ReleaseByteArrayElements(buff, byte_buffer, 0);
            byte_buffer =  NULL;
        }
    } else {
        err = FM_JNI_FAILURE;
    }

    return err;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRawRdsNative
    (JNIEnv * env, jobject thiz __unused, jint fd, jbyteArray buff, jint count)
{
    ALOGD("->android_hardware_fmradio_FmReceiverJNI_getRawRdsNative");

    if (env->GetArrayLength(buff) != count) {
        ALOGE("%s: unexpected buff size", __func__);
        return FM_JNI_FAILURE;
    }
    return (read (fd, buff, count));
}

static jint android_hardware_fmradio_FmReceiverJNI_configureSpurTable
    (JNIEnv * env __unused, jobject thiz __unused, jint fd)
{
    int err;

    ALOGD("->android_hardware_fmradio_FmReceiverJNI_configureSpurTable\n");

    if (fd >= 0) {
        err = FmIoctlsInterface :: set_control(fd,
                                                V4L2_CID_PRIVATE_UPDATE_SPUR_TABLE,
                                                0);
        if (err < 0) {
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    } else {
        err = FM_JNI_FAILURE;
    }

    return err;
}

static jint android_hardware_fmradio_FmReceiverJNI_setPSRepeatCountNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd, jint repCount)
{
    int masked_ps_repeat_cnt;
    int err;

    ALOGE("->android_hardware_fmradio_FmReceiverJNI_setPSRepeatCountNative\n");

    if (fd >= 0) {
        masked_ps_repeat_cnt = repCount & MASK_TXREPCOUNT;
        err = FmIoctlsInterface :: set_control(fd,
                                                V4L2_CID_PRIVATE_TAVARUA_TX_SETPSREPEATCOUNT,
                                                masked_ps_repeat_cnt);
        if (err < 0) {
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    } else {
        err = FM_JNI_FAILURE;
    }

    return err;
}

static jint android_hardware_fmradio_FmReceiverJNI_setTxPowerLevelNative
    (JNIEnv * env __unused, jobject thiz __unused, jint fd, jint powLevel)
{
    int err;

    ALOGE("->android_hardware_fmradio_FmReceiverJNI_setTxPowerLevelNative\n");

    if (fd >= 0) {
        err = FmIoctlsInterface :: set_control(fd,
                                                V4L2_CID_TUNE_POWER_LEVEL,
                                                powLevel);
        if (err < 0) {
            err = FM_JNI_FAILURE;
        } else {
            err = FM_JNI_SUCCESS;
        }
    } else {
        err = FM_JNI_FAILURE;
    }

    return err;
}

static void android_hardware_fmradio_FmReceiverJNI_configurePerformanceParams
    (JNIEnv * env __unused, jobject thiz __unused, jint fd)
{

     ConfigFmThs thsObj;

     thsObj.SetRxSearchAfThs(FM_PERFORMANCE_PARAMS, fd);
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setSpurDataNative
    (JNIEnv * env, jobject thiz __unused, jint fd, jshortArray buff,
    jint count)
{
    ALOGE("entered JNI's setSpurDataNative\n");
    int err, i = 0;
    struct v4l2_ext_control ext_ctl;
    struct v4l2_ext_controls v4l2_ctls;
    uint8_t *data;
    short *spur_data = env->GetShortArrayElements(buff, NULL);
    if (spur_data == NULL) {
        ALOGE("Spur data is NULL\n");
        return FM_JNI_FAILURE;
    }
    data = (uint8_t *) malloc(count);
    if (data == NULL) {
        ALOGE("Allocation failed for data\n");
        return FM_JNI_FAILURE;
    }
    for(i = 0; i < count; i++)
        data[i] = (uint8_t) spur_data[i];

    ext_ctl.id = V4L2_CID_PRIVATE_IRIS_SET_SPURTABLE;
    ext_ctl.string = (char*)data;
    ext_ctl.size = count;
    v4l2_ctls.ctrl_class = V4L2_CTRL_CLASS_USER;
    v4l2_ctls.count   = 1;
    v4l2_ctls.controls  = &ext_ctl;

    err = ioctl(fd, VIDIOC_S_EXT_CTRLS, &v4l2_ctls );
    if (err < 0){
        ALOGE("Set ioctl failed\n");
        free(data);
        return FM_JNI_FAILURE;
    }
    free(data);
    return FM_JNI_SUCCESS;
}

static jint android_hardware_fmradio_FmReceiverJNI_enableSlimbusNative
 (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint val)
{
    ALOGD("%s: val = %d\n", __func__, val);
    int err = JNI_ERR;
if (is_soc_cherokee()) {
    err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_ENABLE_SLIMBUS, val);
}
    return err;
}

static jint android_hardware_fmradio_FmReceiverJNI_enableSoftMuteNative
 (JNIEnv * env __unused, jobject thiz __unused, jint fd __unused, jint val)
{
    ALOGD("%s: val = %d\n", __func__, val);
    int err = JNI_ERR;
if (is_soc_cherokee()) {
    err = vendor_interface->set_fm_ctrl(V4L2_CID_PRV_SOFT_MUTE, val);
}
    return err;
}

static void classInitNative(JNIEnv* env __unused, jclass clazz __unused) {

    ALOGI("ClassInit native called \n");
    jclass dataClass = env->FindClass("qcom/fmradio/FmReceiverJNI");
    javaClassRef = (jclass) env->NewGlobalRef(dataClass);
    lib_handle = dlopen(FM_LIBRARY_NAME, RTLD_NOW);
    if (!lib_handle) {
        ALOGE("%s unable to open %s: %s", __func__, FM_LIBRARY_NAME, dlerror());
        goto error;
    }
    ALOGI("Opened %s shared object library successfully", FM_LIBRARY_NAME);

    ALOGI("Obtaining handle: '%s' to the shared object library...", FM_LIBRARY_SYMBOL_NAME);
    vendor_interface = (fm_interface_t *)dlsym(lib_handle, FM_LIBRARY_SYMBOL_NAME);
    if (!vendor_interface) {
        ALOGE("%s unable to find symbol %s in %s: %s", __func__, FM_LIBRARY_SYMBOL_NAME, FM_LIBRARY_NAME, dlerror());
        goto error;
    }

    method_psInfoCallback = env->GetMethodID(javaClassRef, "PsInfoCallback", "([B)V");
    method_rtCallback = env->GetMethodID(javaClassRef, "RtCallback", "([B)V");
    method_ertCallback = env->GetMethodID(javaClassRef, "ErtCallback", "([B)V");
    method_eccCallback = env->GetMethodID(javaClassRef, "EccCallback", "([B)V");
    method_rtplusCallback = env->GetMethodID(javaClassRef, "RtPlusCallback", "([B)V");
    method_aflistCallback = env->GetMethodID(javaClassRef, "AflistCallback", "([B)V");
    method_enableCallback = env->GetMethodID(javaClassRef, "enableCallback", "()V");
    method_tuneCallback = env->GetMethodID(javaClassRef, "tuneCallback", "(I)V");
    method_seekCmplCallback = env->GetMethodID(javaClassRef, "seekCmplCallback", "(I)V");
    method_scanNxtCallback = env->GetMethodID(javaClassRef, "scanNxtCallback", "()V");
    method_srchListCallback = env->GetMethodID(javaClassRef, "srchListCallback", "([B)V");
    method_stereostsCallback = env->GetMethodID(javaClassRef, "stereostsCallback", "(Z)V");
    method_rdsAvlStsCallback = env->GetMethodID(javaClassRef, "rdsAvlStsCallback", "(Z)V");
    method_disableCallback = env->GetMethodID(javaClassRef, "disableCallback", "()V");
    method_getSigThCallback = env->GetMethodID(javaClassRef, "getSigThCallback", "(II)V");
    method_getChDetThrCallback = env->GetMethodID(javaClassRef, "getChDetThCallback", "(II)V");
    method_defDataRdCallback = env->GetMethodID(javaClassRef, "DefDataRdCallback", "(II)V");
    method_getBlendCallback = env->GetMethodID(javaClassRef, "getBlendCallback", "(II)V");
    method_setChDetThrCallback = env->GetMethodID(javaClassRef, "setChDetThCallback","(I)V");
    method_defDataWrtCallback = env->GetMethodID(javaClassRef, "DefDataWrtCallback", "(I)V");
    method_setBlendCallback = env->GetMethodID(javaClassRef, "setBlendCallback", "(I)V");
    method_getStnParamCallback = env->GetMethodID(javaClassRef, "getStnParamCallback", "(II)V");
    method_getStnDbgParamCallback = env->GetMethodID(javaClassRef, "getStnDbgParamCallback", "(II)V");
    method_enableSlimbusCallback = env->GetMethodID(javaClassRef, "enableSlimbusCallback", "(I)V");
    method_enableSoftMuteCallback = env->GetMethodID(javaClassRef, "enableSoftMuteCallback", "(I)V");
    method_FmReceiverJNICtor = env->GetMethodID(javaClassRef,"<init>","()V");

    return;
error:
    vendor_interface = NULL;
    if (lib_handle)
        dlclose(lib_handle);
    lib_handle = NULL;
}

static void initNative(JNIEnv *env __unused, jobject object __unused) {
if (is_soc_cherokee()) {
    int status;
    ALOGI("Init native called \n");

    if (vendor_interface) {
        ALOGI("Initializing the FM HAL module & registering the JNI callback functions...");
        status = vendor_interface->hal_init(&fm_callbacks);
        if (status) {
            ALOGE("%s unable to initialize vendor library: %d", __func__, status);
            return;
        }
        ALOGI("***** FM HAL Initialization complete *****\n");
    }
    mCallbacksObj = env->NewGlobalRef(object);
}
}

static void cleanupNative(JNIEnv *env __unused, jobject object __unused) {

    if (is_soc_cherokee()) {
        if (mCallbacksObj != NULL) {
            env->DeleteGlobalRef(mCallbacksObj);
            mCallbacksObj = NULL;
        }
    }
}
/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        { "classInitNative", "()V", (void*)classInitNative},
        { "initNative", "()V", (void*)initNative},
        {"cleanupNative", "()V", (void *) cleanupNative},
        { "acquireFdNative", "(Ljava/lang/String;)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_acquireFdNative},
        { "closeFdNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_closeFdNative},
        { "getFreqNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getFreqNative},
        { "setFreqNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setFreqNative},
        { "getControlNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getControlNative},
        { "setControlNative", "(III)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setControlNative},
        { "startSearchNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_startSearchNative},
        { "cancelSearchNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_cancelSearchNative},
        { "getRSSINative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getRSSINative},
        { "setBandNative", "(III)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setBandNative},
        { "getLowerBandNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getLowerBandNative},
        { "getUpperBandNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getUpperBandNative},
        { "getBufferNative", "(I[BI)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getBufferNative},
        { "setMonoStereoNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative},
        { "getRawRdsNative", "(I[BI)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getRawRdsNative},
        { "setPSRepeatCountNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setPSRepeatCountNative},
        { "setTxPowerLevelNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setTxPowerLevelNative},
        { "configureSpurTable", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_configureSpurTable},
        { "setSpurDataNative", "(I[SI)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setSpurDataNative},
        { "configurePerformanceParams", "(I)V",
             (void*)android_hardware_fmradio_FmReceiverJNI_configurePerformanceParams},
        { "enableSlimbus", "(II)I",
             (void*)android_hardware_fmradio_FmReceiverJNI_enableSlimbusNative},
        { "enableSoftMute", "(II)I",
             (void*)android_hardware_fmradio_FmReceiverJNI_enableSoftMuteNative},
};

int register_android_hardware_fm_fmradio(JNIEnv* env)
{
        return jniRegisterNativeMethods(env, "qcom/fmradio/FmReceiverJNI", gMethods, NELEM(gMethods));
}

} // end namespace

jint JNI_OnLoad(JavaVM *jvm, void *reserved __unused)
{
    JNIEnv *e;
    int status;
    g_jVM = jvm;

    ALOGI("FM : Loading QCOMM FM-JNI");
    if (jvm->GetEnv((void **)&e, JNI_VERSION_1_6)) {
        ALOGE("JNI version mismatch error");
        return JNI_ERR;
    }

    if ((status = android::register_android_hardware_fm_fmradio(e)) < 0) {
        ALOGE("jni adapter service registration failure, status: %d", status);
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
