#ifndef UTILS_H_
#define UTILS_H_

#include <windows.h>
#include <jni.h>

extern HMODULE global_dll_instance;

void ShowMessage(const wchar_t* message);
void Error(const wchar_t* error);
char* JObjectToCString(JNIEnv* env, jobject obj); // Should be cleared after use
void JavaPrintln(JNIEnv* env, const char* message);

#endif  //UTILS_H_
