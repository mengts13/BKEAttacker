package main 

/*
#include <android/log.h>
#include <stdlib.h>

static void _go_log_error(const char* tag, const char* msg) {
    __android_log_print(ANDROID_LOG_ERROR, tag, "%s", msg);
}

static void _go_log_info(const char* tag, const char* msg) {
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", msg);
}
*/
import "C"
import "unsafe"

const logTag = "GoKCP" 

func LogInfo(msg string) {
    cmsg := C.CString(msg)
    ctag := C.CString(logTag)
    defer C.free(unsafe.Pointer(cmsg))
    defer C.free(unsafe.Pointer(ctag))
    C._go_log_info(ctag, cmsg)
}

func LogError(msg string) {
    cmsg := C.CString(msg)
    ctag := C.CString(logTag)
    defer C.free(unsafe.Pointer(cmsg))
    defer C.free(unsafe.Pointer(ctag))
    C._go_log_error(ctag, cmsg)
}