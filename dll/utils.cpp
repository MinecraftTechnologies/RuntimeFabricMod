#include "utils.h"

#include <windows.h>
#include <winuser.h>
#include <jni.h>

HMODULE global_dll_instance = nullptr;

void ShowMessage(const wchar_t* message) {
    MessageBoxW(nullptr, message, L"Fabric Injector", MB_OK | MB_ICONINFORMATION);
}

void Error(const wchar_t* error) {
    DWORD last_status_code = GetLastError();
    wchar_t second_message[256] = {};
    wsprintfW(second_message, L"Last error code: %u", last_status_code);
    MessageBoxW(nullptr, error, L"Fabric Injector", MB_OK | MB_ICONEXCLAMATION);
    MessageBoxW(nullptr, second_message, L"Fabric Injector", MB_OK | MB_ICONEXCLAMATION);
    FreeLibraryAndExitThread(global_dll_instance, 1);
}

void JavaPrintln(JNIEnv* env, const char* message) {
    if (env == nullptr || message == nullptr) {
        return;
    }

    // java.lang.System
    jclass systemClass = env->FindClass("java/lang/System");
    if (systemClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }

    // java.io.PrintStream
    jfieldID outField = env->GetStaticFieldID(
        systemClass,
        "out",
        "Ljava/io/PrintStream;"
    );
    if (outField == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(systemClass);
        return;
    }

    jobject outObject = env->GetStaticObjectField(systemClass, outField);
    if (outObject == nullptr) {
        env->DeleteLocalRef(systemClass);
        return;
    }

    jclass printStreamClass = env->FindClass("java/io/PrintStream");
    if (printStreamClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(outObject);
        env->DeleteLocalRef(systemClass);
        return;
    }

    jmethodID printlnMethod = env->GetMethodID(
        printStreamClass,
        "println",
        "(Ljava/lang/String;)V"
    );
    if (printlnMethod == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(printStreamClass);
        env->DeleteLocalRef(outObject);
        env->DeleteLocalRef(systemClass);
        return;
    }

    jstring jmsg = env->NewStringUTF(message);
    if (jmsg == nullptr) {
        env->DeleteLocalRef(printStreamClass);
        env->DeleteLocalRef(outObject);
        env->DeleteLocalRef(systemClass);
        return;
    }

    env->CallVoidMethod(outObject, printlnMethod, jmsg);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    // cleanup
    env->DeleteLocalRef(jmsg);
    env->DeleteLocalRef(printStreamClass);
    env->DeleteLocalRef(outObject);
    env->DeleteLocalRef(systemClass);
}

char* JObjectToCString(JNIEnv* env, jobject obj) {
    if (env == nullptr || obj == nullptr) {
        return nullptr;
    }

    // Получаем класс Object
    jclass objectClass = env->GetObjectClass(obj);
    if (objectClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }

    // Метод toString(): ()Ljava/lang/String;
    jmethodID toStringMethod = env->GetMethodID(
        objectClass,
        "toString",
        "()Ljava/lang/String;"
    );
    if (toStringMethod == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(objectClass);
        return nullptr;
    }

    // Вызываем toString
    jstring jstr = (jstring)env->CallObjectMethod(obj, toStringMethod);
    if (env->ExceptionCheck() || jstr == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(objectClass);
        return nullptr;
    }

    // Конвертация jstring → UTF-8
    const char* utfChars = env->GetStringUTFChars(jstr, nullptr);
    if (utfChars == nullptr) {
        env->DeleteLocalRef(jstr);
        env->DeleteLocalRef(objectClass);
        return nullptr;
    }

    // Копируем в native-память
    size_t len = strlen(utfChars);
    char* result = (char*)malloc(len + 1);
    if (result != nullptr) {
        memcpy(result, utfChars, len + 1);
    }

    // cleanup
    env->ReleaseStringUTFChars(jstr, utfChars);
    env->DeleteLocalRef(jstr);
    env->DeleteLocalRef(objectClass);

    return result; // caller должен free()
}