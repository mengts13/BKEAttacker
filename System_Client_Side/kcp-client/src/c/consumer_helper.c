// consumer_helper.c

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "libgo_kcp_client.h"


// JNI 
JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_Consumer_PopGattRpc(JNIEnv *env, jclass clazz) {

    char* json_cstr = GoPopGattRpcJson();


    jstring result = (*env)->NewStringUTF(env, json_cstr);

    GoFreeCString(json_cstr);

    return result;
}

// JNI 
JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_Consumer_PopServiceData(JNIEnv *env, jclass clazz) {

    char* cstr = GoPopServiceDataString();


    jstring result = (*env)->NewStringUTF(env, cstr);


    GoFreeCString(cstr);

    return result;
}