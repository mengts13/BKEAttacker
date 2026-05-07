#include "jni_helper.h"

// Helper: convert jbyteArray to unsigned char*
unsigned char* jbyteArrayToBytes(JNIEnv *env, jbyteArray array, jsize *len) {
    if (array == NULL) return NULL;
    *len = (*env)->GetArrayLength(env, array);
    jbyte *bytes = (*env)->GetByteArrayElements(env, array, NULL);
    if (bytes == NULL) return NULL;

    unsigned char *result = (unsigned char*)malloc(*len);
    if (result != NULL) {
        memcpy(result, bytes, *len);
    }
    (*env)->ReleaseByteArrayElements(env, array, bytes, JNI_ABORT);
    return result;
}