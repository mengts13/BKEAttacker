//dupclient.c

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "libgo_kcp_client.h"

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_StartClient(JNIEnv *env, jclass clazz) {
    int result = startClient();
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_RegisterPair(JNIEnv *env, jclass clazz, jstring roleByteStr) {
    if (roleByteStr == NULL) {
        return -1;
    }
    // 2. Convert Java String to a C string (UTF-8).
    const char* cstr = (*env)->GetStringUTFChars(env, roleByteStr, NULL);
    if (cstr == NULL) {
        return -2; // Out of memory or a JNI exception occurred.
    }
    // 3. Validate that the string length must be 1.
    jsize len = (*env)->GetStringUTFLength(env, roleByteStr);
    if (len != 1) {
        (*env)->ReleaseStringUTFChars(env, roleByteStr, cstr);
        return -3; // Error: must be a single character.
    }
    // 4. Extract the role byte
    char roleByte = cstr[0];
    // 5. Release the JNI string reference (important to avoid memory leaks)
    (*env)->ReleaseStringUTFChars(env, roleByteStr, cstr);
    // 6. Call the exported Go register function
    int result = registerPair((char)roleByte);  // Note: Go's C.char == C's char
    // 7. Return the result to Java
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_RegisterSelf(JNIEnv *env, jclass clazz, jstring roleByteStr) {
    // 1. Check if the input is null
    if (roleByteStr == NULL) {
        return -1; // Error: role cannot be null
    }
    // 2. Convert Java String to a C string (UTF-8)
    const char* cstr = (*env)->GetStringUTFChars(env, roleByteStr, NULL);
    if (cstr == NULL) {
        return -2; // Out of memory or exception
    }
    // 3. Validate that the string length must be 1.
    jsize len = (*env)->GetStringUTFLength(env, roleByteStr);
    if (len != 1) {
        (*env)->ReleaseStringUTFChars(env, roleByteStr, cstr);
        return -3; // Error: must be a single character.
    }
    // 4. Extract the role byte
    char roleByte = cstr[0];
    // 5. Release the JNI string reference (important to avoid memory leaks)
    (*env)->ReleaseStringUTFChars(env, roleByteStr, cstr);
    // 6. Call the exported Go register function
    int result = registerSelf((char)roleByte);  // Note: Go's C.char == C's char
    // 7. Return the result to Java
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_SetGlobalPairToken(JNIEnv *env, jclass clazz, jstring tokenStr) {
    const char* cstr = NULL;
    int goResult = -999; // fallback error code
    if (tokenStr == NULL) {
        goResult = (int)SetGlobalPairToken(NULL);
    } else {
        cstr = (*env)->GetStringUTFChars(env, tokenStr, NULL);
        if (cstr == NULL) {
            // GetStringUTFChars failed (e.g. OOM), pass NULL to Go
            goResult = (int)SetGlobalPairToken(NULL);
        } else {
            goResult = (int)SetGlobalPairToken((char*)cstr);
            (*env)->ReleaseStringUTFChars(env, tokenStr, cstr);
        }
    }
    return (jint)goResult;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_Login(JNIEnv *env, jclass clazz, jstring accountStr, jstring passwordStr) {
    // 1. Check if account is null
    if (accountStr == NULL) {
        return -1; // Account cannot be empty
    }
    // 2. Check if password is null
    if (passwordStr == NULL) {
        return -2; // Password cannot be empty
    }
    // 3. Convert account
    const char* accountC = (*env)->GetStringUTFChars(env, accountStr, NULL);
    if (accountC == NULL) {
        return -3; // Out of memory
    }
    // 4. Convert password
    const char* passwordC = (*env)->GetStringUTFChars(env, passwordStr, NULL);
    if (passwordC == NULL) {
        (*env)->ReleaseStringUTFChars(env, accountStr, accountC);
        return -4; // Out of memory
    }
    // 5. Call the Go login function
    // Note: The actual C interface for Go's //export login is login(char*, char*)
    int result = login((char*)accountC, (char*)passwordC);
    // 6. Release the JNI string reference (must be done!)
    (*env)->ReleaseStringUTFChars(env, passwordStr, passwordC);
    (*env)->ReleaseStringUTFChars(env, accountStr, accountC);
    // 7. Return the result
    // Go's error is represented in C as: 0=success, non-0=failure
    // But your Go function returns an error, and //export converts it to an int (0=OK, non-0=error)
    // So just return it directly
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_sendPin(JNIEnv *env, jclass clazz, jstring pinStr) {
    // 1. Check if the input is null
    if (pinStr == NULL) {
        // Pass NULL to Go, and Go will convert it to an empty string ""
        int result = SendPin(NULL);
        return (jint)result;
    }
    // 2. Convert Java String to a C string (UTF-8)
    const char* cstr = (*env)->GetStringUTFChars(env, pinStr, NULL);
    if (cstr == NULL) {
        // Out of memory, pass NULL to Go
        int result = SendPin(NULL);
        return (jint)result;
    }
    // 3. Call the exported Go function
    int result = SendPin((char*)cstr);
    // 4. Release the JNI string reference (must be done!)
    (*env)->ReleaseStringUTFChars(env, pinStr, cstr);
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_fetchPinToken(JNIEnv *env, jclass clazz, jstring pinStr) {
    // 1. Check if the input is null
    if (pinStr == NULL) {
        // Pass NULL to Go, and Go will convert it to an empty string ""
        int result = FetchPinToken(NULL);
        return (jint)result;
    }
    // 2. Convert Java String to a C string (UTF-8)
    const char* cstr = (*env)->GetStringUTFChars(env, pinStr, NULL);
    if (cstr == NULL) {
        // Out of memory, pass NULL to Go
        int result = FetchPinToken(NULL);
        return (jint)result;
    }
    // 3. Call the exported Go function
    int result = FetchPinToken((char*)cstr);
    // 4. Release the JNI string reference (must be done!)
    (*env)->ReleaseStringUTFChars(env, pinStr, cstr);
    return (jint)result;
}

JNIEXPORT void JNICALL
Java_com_example_bkeattacker_DupClientManager_setTokenFileDir(JNIEnv *env, jclass clazz, jstring pathStr) {
    // 1. Handle null input: Pass NULL to Go to indicate clearing or an invalid path
    if (pathStr == NULL) {
        SetTokenFileDir(NULL);
        return;
    }
    // 2. Convert Java String to a C string (UTF-8)
    const char* cstr = (*env)->GetStringUTFChars(env, pathStr, NULL);
    if (cstr == NULL) {
        // Memory allocation failed, pass NULL to Go
        SetTokenFileDir(NULL);
        return;
    }
    // 3. Call the exported Go function (Note: The Go function receives *C.char, i.e., char*)
    SetTokenFileDir((char*)cstr);  // cast to char*
    // 4. Release the JNI string reference (important to avoid memory leaks)
    (*env)->ReleaseStringUTFChars(env, pathStr, cstr);
    // 5. Returns void, no return value needed
    return;
}

JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_DupClientManager_readPin(JNIEnv *env, jclass clazz) {
    // 1. Call the exported Go ReadPin function
    char* pinC = ReadPin();  // Note: ReadPin must be exported with //export ReadPin
    // 2. Handle NULL or empty strings
    if (pinC == NULL) {
        // Optional: log or throw an exception, return null here
        return NULL;
    }
    // 3. Convert C string to Java String
    jstring pinJava = (*env)->NewStringUTF(env, pinC);
    // 4. Important! Free the memory allocated by C.CString in Go
    free(pinC);  // Because Go's C.CString uses C.malloc, it must be freed
    // 5. Return Java String (may be null if NewStringUTF fails)
    return pinJava;
}

JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_DupClientManager_readPinTempToken(JNIEnv *env, jclass clazz) {
    // 1. Call the exported Go function
    char* tokenC = ReadPinTempToken();
    // 2. Safety check: if NULL is returned (although your Go code will not return NULL, but for defensive programming)
    if (tokenC == NULL) {
        return NULL;
    }
    // 3. Convert to Java String
    jstring tokenJava = (*env)->NewStringUTF(env, tokenC);
    // 4. Free the memory allocated by C.CString in Go (critical!)
    free(tokenC);
    // 5. Return Java String (may be null if NewStringUTF fails)
    return tokenJava;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_reconnectToServer(JNIEnv *env, jclass clazz) {
    // Directly call the exported Go function
    int result = ReconnectToServer();
    return (jint)result;
}

JNIEXPORT void JNICALL
Java_com_example_bkeattacker_DupClientManager_SetMessageServerAddr(JNIEnv *env, jclass clazz, jstring addrStr) {
    if (addrStr == NULL) {
        SetMessageServerAddr(NULL);
        return;
    }
    const char* cstr = (*env)->GetStringUTFChars(env, addrStr, NULL);
    if (cstr == NULL) {
        SetMessageServerAddr(NULL);
        return;
    }
    SetMessageServerAddr((char*)cstr);
    (*env)->ReleaseStringUTFChars(env, addrStr, cstr);
}

JNIEXPORT void JNICALL
Java_com_example_bkeattacker_DupClientManager_SetStreamServerAddr(JNIEnv *env, jclass clazz, jstring addrStr) {
    if (addrStr == NULL) {
        SetStreamServerAddr(NULL);
        return;
    }
    const char* cstr = (*env)->GetStringUTFChars(env, addrStr, NULL);
    if (cstr == NULL) {
        SetStreamServerAddr(NULL);
        return;
    }
    SetStreamServerAddr((char*)cstr);
    (*env)->ReleaseStringUTFChars(env, addrStr, cstr);
}

JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_DupClientManager_readTimeStamp(JNIEnv *env, jclass clazz) {
    // 1. Call the exported Go function (always returns a valid C string, at least "")
    char* tsC = ReadTimeStamp();
    // 2. tsC will not be NULL (because all returns in Go are C.CString(...)), but still do a defensive check
    if (tsC == NULL) {
        // In theory, it won't happen, but for safety's sake
        return NULL;
    }
    // 3. Convert to Java String
    jstring tsJava = (*env)->NewStringUTF(env, tsC);
    // 4. Free the memory allocated by C.CString in Go (must be done!)
    free(tsC);
    // 5. Return the result (may be an empty Java string, or a valid base64 string)
    return tsJava;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_SetGlobalUUIDMap(JNIEnv *env, jclass clazz, jstring b64Str) {
    const char* cstr = NULL;
    int goResult = -999; // fallback error code
    if (b64Str == NULL) {
        // Pass NULL to Go → C.GoString(NULL) becomes ""
        goResult = (int)SetGlobalUUIDMap(NULL);
    } else {
        cstr = (*env)->GetStringUTFChars(env, b64Str, NULL);
        if (cstr == NULL) {
            // Out of memory or exception in JNI
            goResult = (int)SetGlobalUUIDMap(NULL);
        } else {
            goResult = (int)SetGlobalUUIDMap((char*)cstr); // cast to char* is safe
            (*env)->ReleaseStringUTFChars(env, b64Str, cstr);
        }
    }
    return (jint)goResult;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_CancelPair(JNIEnv *env, jclass clazz) {
    int goResult = -999; // fallback error code
    // Call the Go function that now returns C.int
    goResult = (int)CancelPair();
    return (jint)goResult;
}

JNIEXPORT jint JNICALL
Java_com_example_bkeattacker_DupClientManager_SendMessageData(JNIEnv *env, jclass clazz, jstring advDataStr) {
    // 1. Handle null input: pass NULL to Go
    if (advDataStr == NULL) {
        int result = SendMessageData(NULL);
        return (jint)result;
    }
    // 2. Convert Java String to C string (UTF-8)
    const char* cstr = (*env)->GetStringUTFChars(env, advDataStr, NULL);
    if (cstr == NULL) {
        // GetStringUTFChars failed (e.g., OOM), pass NULL to Go
        int result = SendMessageData(NULL);
        return (jint)result;
    }
    // 3. Call the exported Go function
    int result = SendMessageData((char*)cstr);
    // 4. Release the JNI string reference (important to avoid memory leaks)
    (*env)->ReleaseStringUTFChars(env, advDataStr, cstr);
    // 5. Return the result to Java
    return (jint)result;
}

JNIEXPORT jstring JNICALL
Java_com_example_bkeattacker_DupClientManager_ReadMessageData(JNIEnv *env, jclass clazz) {
    // 1. Call the exported Go function (no parameters)
    char* dataC = ReadMessageData();
    // 2. Defensive check: Although Go always returns C.CString(...) (non-NULL), for safety's sake
    if (dataC == NULL) {
        return NULL;
    }
    // 3. Convert C string to Java String
    jstring dataJava = (*env)->NewStringUTF(env, dataC);
    // 4. Free the memory allocated by C.CString in Go (critical! otherwise memory leak)
    free(dataC);
    // 5. Return Java String (may be "", but will not crash)
    return dataJava;
}

JNIEXPORT jlong JNICALL
Java_com_example_bkeattacker_DupClientManager_ReadCurrentTimestamp(JNIEnv *env, jclass clazz) {
    jlong result = ReadCurrentTimestamp();
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_example_bkeattacker_DupClientManager_WaitResponseLock(JNIEnv *env, jclass clazz, jbyte op) {
    // jbyte is signed char (-128 ~ 127), but Go's uint8 is 0~255
    // So it needs to be converted to an unsigned byte
    unsigned char op_u8 = (unsigned char)op;
    // Call the exported Go function
    _Bool result = WaitResponseLock(op_u8);
    // _Bool -> jboolean (JNI_TRUE / JNI_FALSE)
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_bkeattacker_DupClientManager_WaitResponseUnlock(JNIEnv *env, jclass clazz, jbyte op) {
    unsigned char op_u8 = (unsigned char)op;
    WaitResponseUnlock(op_u8);
}
