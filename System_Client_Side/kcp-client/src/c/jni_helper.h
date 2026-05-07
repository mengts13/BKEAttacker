// jni_helper.h
#ifndef JNI_HELPER_H
#define JNI_HELPER_H

#include <jni.h>
#include <stdlib.h>
#include <string.h>

unsigned char* jbyteArrayToBytes(JNIEnv *env, jbyteArray array, jsize *len);

#endif // JNI_HELPER_H