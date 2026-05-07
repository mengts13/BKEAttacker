// server_jni.c
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "jni_helper.h"
#include "libgo_kcp_client.h"  

// JNI: sendReadCharacteristicResponse
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ServerStub_sendReadCharacteristicResponse(
    JNIEnv *env, jclass clazz,
    jint status,
    jbyteArray value) {

    jsize valueLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, value, &valueLen);

    goSendReadCharacteristicResponse(status, bytes, (int)valueLen);

    free(bytes);
}

// JNI: sendWriteCharacteristicResponse
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ServerStub_sendWriteCharacteristicResponse(
    JNIEnv *env, jclass clazz,
    jint status,
    jbyteArray value) {

    jsize valueLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, value, &valueLen);

    goSendWriteCharacteristicResponse(status, bytes, (int)valueLen);

    free(bytes);
}

// JNI: sendReadDescriptorResponse
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ServerStub_sendReadDescriptorResponse(
    JNIEnv *env, jclass clazz,
    jint status,
    jbyteArray value) {

    jsize valueLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, value, &valueLen);

    goSendReadDescriptorResponse(status, bytes, (int)valueLen);

    free(bytes);
}

// JNI: sendWriteDescriptorResponse
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ServerStub_sendWriteDescriptorResponse(
    JNIEnv *env, jclass clazz,
    jint status,
    jbyteArray value) {

    jsize valueLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, value, &valueLen);

    goSendWriteDescriptorResponse(status, bytes, (int)valueLen);

    free(bytes);
}

// JNI: notifyCharacteristicChanged
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ServerStub_notifyCharacteristicChanged(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid,
    jbyteArray data) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);

    jsize dataLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, data, &dataLen);

    goNotifyCharacteristicChanged((char*)svc, (char*)charac, bytes, (int)dataLen);

    free(bytes);
    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_ServerStub_getMTU(JNIEnv *env, jclass clazz) {
    jint mtu = (jint)GetMTU();
    return mtu;
}