// client_jni.c
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "jni_helper.h"
#include "libgo_kcp_client.h"  



// JNI: writeDescriptor
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_writeDescriptor(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid,
    jstring descriptorUuid,
    jbyteArray data) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);
    const char *desc = (*env)->GetStringUTFChars(env, descriptorUuid, NULL);

    jsize dataLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, data, &dataLen);

    goWriteDescriptor((char*)svc, (char*)charac, (char*)desc, bytes, (int)dataLen);

    free(bytes);
    (*env)->ReleaseStringUTFChars(env, descriptorUuid, desc);
    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

// JNI: writeCharacteristic
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_writeCharacteristic(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid,
    jint writeType,   
    jbyteArray data) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);

    jsize dataLen = 0;
    unsigned char *bytes = jbyteArrayToBytes(env, data, &dataLen);


    goWriteCharacteristic(
        (char*)svc,
        (char*)charac,
        (int)writeType,     
        bytes,
        (int)dataLen
    );

    free(bytes);
    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

// JNI: readCharacteristic
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_readCharacteristic(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);

    goReadCharacteristic((char*)svc, (char*)charac);

    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

// JNI: readDescriptor
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_readDescriptor(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid,
    jstring descriptorUuid) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);
    const char *desc = (*env)->GetStringUTFChars(env, descriptorUuid, NULL);

    goReadDescriptor((char*)svc, (char*)charac, (char*)desc);

    (*env)->ReleaseStringUTFChars(env, descriptorUuid, desc);
    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

// JNI: connectGatt
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_connectGatt(
    JNIEnv *env, jclass clazz,
    jstring deviceAddress) {

    const char *addr = (*env)->GetStringUTFChars(env, deviceAddress, NULL);
    goConnectGatt((char*)addr);
    (*env)->ReleaseStringUTFChars(env, deviceAddress, addr);
}

// JNI: setCharacteristicNotification
JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_setCharacteristicNotification(
    JNIEnv *env, jclass clazz,
    jstring serviceUuid,
    jstring characteristicUuid,
    jint enable) {

    const char *svc = (*env)->GetStringUTFChars(env, serviceUuid, NULL);
    const char *charac = (*env)->GetStringUTFChars(env, characteristicUuid, NULL);

    goSetCharacteristicNotification((char*)svc, (char*)charac, enable);

    (*env)->ReleaseStringUTFChars(env, characteristicUuid, charac);
    (*env)->ReleaseStringUTFChars(env, serviceUuid, svc);
}

JNIEXPORT void JNICALL
Java_com_example_bkeattacker_ClientStub_goDiscoverService(JNIEnv *env, jclass clazz) {
    goDiscoverService();
}