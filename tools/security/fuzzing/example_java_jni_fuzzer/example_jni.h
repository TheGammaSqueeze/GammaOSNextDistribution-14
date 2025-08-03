#include <jni.h>
#ifndef _Included_ExampleFuzzerWithNative
#define _Included_ExampleFuzzerWithNative
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jboolean JNICALL Java_ExampleJavaJniFuzzer_parse(
    JNIEnv*, jobject o, jstring);
#ifdef __cplusplus
}
#endif
#endif