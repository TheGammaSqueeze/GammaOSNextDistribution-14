/**
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

#define LOG_TAG "libimsmediajni"

#include <assert.h>
#include <utils/Log.h>
#include <binder/Parcel.h>
#include <android_os_Parcel.h>
#include <nativehelper/JNIHelp.h>
#include <MediaManagerFactory.h>
#include <VideoManager.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaTrace.h>
#include <android/native_window_jni.h>
#include <android/asset_manager_jni.h>

#define IMS_MEDIA_JNI_VERSION JNI_VERSION_1_4

static const char* gClassPath = "com/android/telephony/imsmedia/JNIImsMediaService";

static JavaVM* gJVM = nullptr;
static jclass gClass_JNIImsMediaService = nullptr;
static jmethodID gMethod_sendData2Java = nullptr;
AAssetManager* gpAssetManager = nullptr;

JavaVM* GetJavaVM()
{
    return gJVM;
}

static int SendData2Java(int sessionId, const android::Parcel& objParcel)
{
    JNIEnv* env;

    if ((gClass_JNIImsMediaService == nullptr) || (gMethod_sendData2Java == nullptr))
    {
        ALOGE(0, "SendData2Java: Method is null", 0, 0, 0);
        return 0;
    }

    JavaVM* jvm = GetJavaVM();

    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK)
    {
        ALOGE(0, "SendData2Java: AttachCurrentThread fail", 0, 0, 0);
        return 0;
    }

    jbyteArray baData = env->NewByteArray(objParcel.dataSize());
    jbyte* pBuffer = env->GetByteArrayElements(baData, nullptr);

    if (pBuffer != nullptr)
    {
        memcpy(pBuffer, objParcel.data(), objParcel.dataSize());
        env->ReleaseByteArrayElements(baData, pBuffer, 0);
        env->CallStaticIntMethod(
                gClass_JNIImsMediaService, gMethod_sendData2Java, sessionId, baData);
    }

    env->DeleteLocalRef(baData);

    return 1;
}

void setAudioThreadPriority(int threadId)
{
    JNIEnv* env = nullptr;
    JavaVM* jvm = GetJavaVM();

    if (gClass_JNIImsMediaService == nullptr)
    {
        IMLOGE0("gClass_JNIImsMediaService is null");
        return;
    }

    if (jvm == nullptr || jvm->AttachCurrentThread(&env, nullptr) != JNI_OK)
    {
        IMLOGE0("setAudioThreadPriority: AttachCurrentThread fail");
        return;
    }

    if (env == nullptr)
    {
        IMLOGE0("env is null");
        return;
    }

    jmethodID gMethod_setAudioThreadPriority =
            env->GetStaticMethodID(gClass_JNIImsMediaService, "setAudioThreadPriority", "(I)V");

    if (gMethod_setAudioThreadPriority == nullptr)
    {
        IMLOGE0("setAudioThreadPriority: GetStaticMethodID gMethod_setAudioThreadPriority failed");
        return;
    }

    env->CallStaticIntMethod(gClass_JNIImsMediaService, gMethod_setAudioThreadPriority, threadId);
}

static jlong JNIImsMediaService_getInterface(
        JNIEnv* /* env */, jobject /* object */, jint mediatype)
{
    ALOGD("JNIImsMediaService_getInterface: type[%d]", mediatype);
    BaseManager* manager = nullptr;
    manager = MediaManagerFactory::getInterface(mediatype);
    if (manager != nullptr)
    {
        manager->setCallback(SendData2Java);
    }

    return static_cast<jlong>(reinterpret_cast<long>(manager));
}

static void JNIImsMediaService_sendMessage(
        JNIEnv* env, jobject, jlong nativeObj, jint sessionId, jbyteArray baData)
{
    BaseManager* manager = reinterpret_cast<BaseManager*>(nativeObj);
    android::Parcel parcel;
    jbyte* pBuff = env->GetByteArrayElements(baData, nullptr);
    int nBuffSize = env->GetArrayLength(baData);
    parcel.setData(reinterpret_cast<const uint8_t*>(pBuff), nBuffSize);
    parcel.setDataPosition(0);

    if (manager)
    {
        manager->sendMessage(sessionId, parcel);
    }

    env->ReleaseByteArrayElements(baData, pBuff, 0);
}

static void JNIImsMediaService_setPreviewSurface(
        JNIEnv* env, jobject, jlong nativeObj, jint sessionId, jobject surface)
{
    VideoManager* manager = reinterpret_cast<VideoManager*>(nativeObj);

    if (manager != nullptr)
    {
        manager->setPreviewSurface(sessionId, ANativeWindow_fromSurface(env, surface));
    }
}

static void JNIImsMediaService_setDisplaySurface(
        JNIEnv* env, jobject, jlong nativeObj, jint sessionId, jobject surface)
{
    VideoManager* manager = reinterpret_cast<VideoManager*>(nativeObj);

    if (manager != nullptr)
    {
        manager->setDisplaySurface(sessionId, ANativeWindow_fromSurface(env, surface));
    }
}

static jstring JNIImsMediaUtil_generateSPROP(JNIEnv* env, jobject, jbyteArray baData)
{
    android::Parcel parcel;
    jbyte* pBuff = env->GetByteArrayElements(baData, nullptr);
    int nBuffSize = env->GetArrayLength(baData);
    parcel.setData(reinterpret_cast<const uint8_t*>(pBuff), nBuffSize);
    parcel.setDataPosition(0);

    VideoConfig videoConfig;
    videoConfig.readFromParcel(&parcel);
    env->ReleaseByteArrayElements(baData, pBuff, 0);
    ALOGE("[GenerateVideoSprop] Profile[%d] level[%d]", videoConfig.getCodecProfile(),
            videoConfig.getCodecLevel());

    char* sprop = ImsMediaVideoUtil::GenerateVideoSprop(&videoConfig);
    jstring str = nullptr;
    if (sprop != nullptr)
    {
        str = env->NewStringUTF(sprop);
        free(sprop);
    }

    return str;
}

static void SetAssetManager(JNIEnv* env, jobject, jobject jobjAssetManager)
{
    gpAssetManager = AAssetManager_fromJava(env, jobjAssetManager);
    ALOGD("[SetAssetManager] Asset manager has been set in JNI");
}

static void JNIImsMediaService_setLogMode(JNIEnv*, jobject, jint logMode, jint debugLogMode)
{
    ImsMediaTrace::IMSetLogMode(logMode);
    ImsMediaTrace::IMSetDebugLogMode(debugLogMode);
}

static JNINativeMethod gMethods[] = {
        {"getInterface", "(I)J", (void*)JNIImsMediaService_getInterface},
        {"sendMessage", "(JI[B)V", (void*)JNIImsMediaService_sendMessage},
        {"setPreviewSurface", "(JILandroid/view/Surface;)V",
                (void*)JNIImsMediaService_setPreviewSurface},
        {"setDisplaySurface", "(JILandroid/view/Surface;)V",
                (void*)JNIImsMediaService_setDisplaySurface},
        {"generateSprop", "([B)Ljava/lang/String;", (void*)JNIImsMediaUtil_generateSPROP},
        {"setAssetManager", "(Landroid/content/res/AssetManager;)V", (void*)SetAssetManager},
        {"setLogMode", "(II)V", (void*)JNIImsMediaService_setLogMode},
};

jint ImsMediaServiceJni_OnLoad(JavaVM* vm, JNIEnv* env)
{
    gJVM = vm;

    jclass _jclassImsMediaService = env->FindClass(gClassPath);

    if (_jclassImsMediaService == nullptr)
    {
        ALOGE("ImsMediaServiceJni_OnLoad :: FindClass failed");
        return -1;
    }

    gClass_JNIImsMediaService = reinterpret_cast<jclass>(env->NewGlobalRef(_jclassImsMediaService));

    if (gClass_JNIImsMediaService == nullptr)
    {
        ALOGE("ImsMediaServiceJni_OnLoad :: FindClass failed2");
        return -1;
    }

    if (jniRegisterNativeMethods(env, gClassPath, gMethods, NELEM(gMethods)) < 0)
    {
        ALOGE("ImsMediaServiceJni_OnLoad: RegisterNatives failed");
        return -1;
    }

    gMethod_sendData2Java =
            env->GetStaticMethodID(gClass_JNIImsMediaService, "sendData2Java", "(I[B)I");

    if (gMethod_sendData2Java == nullptr)
    {
        ALOGE("ImsMediaServiceJni_OnLoad: GetStaticMethodID failed");
        return -1;
    }

    return IMS_MEDIA_JNI_VERSION;
}
