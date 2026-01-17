#include "jvmwork.h"

#include "utils.h"
#include "jvm/jni.h"
#include "jvm/jvmti.h"
#include "injecting_classes/injector.h"
#include "injecting_classes/jar.h"
#include "hooks/classfile.hpp"
#include "hooks/uuid.hpp"

#include <string>
#include <algorithm>

#include "hooks/jnihook.h"

jvmtiEnv *jvmti;
JavaVM *jvm;
JNIEnv *env;

static jobject g_classLoader = nullptr;
static jmethodID g_loadClass = nullptr;

jobject findKnotInThreads(JNIEnv *env) {
    jclass threadClass = env->FindClass("java/lang/Thread");
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");

    jmethodID getAllStackTraces = env->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");
    jobject threadsMap = env->CallStaticObjectMethod(threadClass, getAllStackTraces);

    jclass mapClass = env->FindClass("java/util/Map");
    jmethodID keySetMethod = env->GetMethodID(mapClass, "keySet", "()Ljava/util/Set;");
    jobject threadSet = env->CallObjectMethod(threadsMap, keySetMethod);

    jclass setClass = env->FindClass("java/util/Set");
    jmethodID toArrayMethod = env->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray threadArray = (jobjectArray) env->CallObjectMethod(threadSet, toArrayMethod);

    jint threadCount = env->GetArrayLength(threadArray);
    jmethodID getContextCL = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");

    jobject resultCL = nullptr;

    for (int i = 0; i < threadCount; i++) {
        jobject thread = env->GetObjectArrayElement(threadArray, i);
        jobject cl = env->CallObjectMethod(thread, getContextCL);

        if (cl != nullptr) {
            jclass clObjClass = env->GetObjectClass(cl);
            jclass classClazz = env->FindClass("java/lang/Class");
            jmethodID getName = env->GetMethodID(classClazz, "getName", "()Ljava/lang/String;");
            jstring clName = (jstring) env->CallObjectMethod(clObjClass, getName);

            const char *nameStr = env->GetStringUTFChars(clName, nullptr);
            bool isKnot = (strstr(nameStr, "knot") != nullptr);

            env->ReleaseStringUTFChars(clName, nameStr);

            env->DeleteLocalRef(clName);
            env->DeleteLocalRef(clObjClass);
            env->DeleteLocalRef(classClazz);

            if (isKnot) {
                resultCL = cl;
                env->DeleteLocalRef(thread);
                break;
            }
            env->DeleteLocalRef(cl);
        }
        env->DeleteLocalRef(thread);
    }

    env->DeleteLocalRef(threadArray);
    env->DeleteLocalRef(threadSet);
    env->DeleteLocalRef(threadsMap);
    env->DeleteLocalRef(threadClass);
    env->DeleteLocalRef(classLoaderClass);
    env->DeleteLocalRef(mapClass);
    env->DeleteLocalRef(setClass);

    return resultCL;
}

bool InitFabricClassLoader(JNIEnv *env) {
    jobject classLoader = findKnotInThreads(env);

    if (!classLoader || env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    g_classLoader = env->NewGlobalRef(classLoader);
    env->DeleteLocalRef(classLoader);

    jclass clClass = env->FindClass("java/lang/ClassLoader");
    g_loadClass = env->GetMethodID(
        clClass,
        "loadClass",
        "(Ljava/lang/String;)Ljava/lang/Class;"
    );

    env->DeleteLocalRef(clClass);

    return true;
}

jbyteArray MakeByteArray(const jbyte *data, size_t len) {
    jbyteArray arr = env->NewByteArray(len);
    env->SetByteArrayRegion(arr, 0, len, data);
    return arr;
}

jclass DefineInjectorWithKnotClassLoader() {
    size_t len = sizeof(injector_class_data);
    if (!env || !g_classLoader) {
        Error(L"env or g_classLoader null");
        return nullptr;
    }

    jbyteArray byteArray = MakeByteArray(injector_class_data, len);
    if (!byteArray) {
        Error(L"injector bytearr null");
        return nullptr;
    }

    jclass clClass = env->GetObjectClass(g_classLoader);
    jmethodID defineClass = env->GetMethodID(
        clClass,
        "defineClass",
        "(Ljava/lang/String;[BII)Ljava/lang/Class;"
    );

    if (!defineClass) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(byteArray);
        env->DeleteLocalRef(clClass);
        Error(L"defineClass error");
        return nullptr;
    }

    jstring name = env->NewStringUTF(injector_name);

    jclass cls = (jclass) env->CallObjectMethod(
        g_classLoader,
        defineClass,
        name,
        byteArray,
        0,
        (jint) len
    );

    env->DeleteLocalRef(name);
    env->DeleteLocalRef(byteArray);
    env->DeleteLocalRef(clClass);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        Error(L"Env error");
        return nullptr;
    }

    return cls;
}

void SetupCaps() {
    jvmtiCapabilities caps = {};

    caps.can_redefine_classes = 1;
    caps.can_retransform_classes = 1;
    caps.can_redefine_any_class = 1;

    jvmtiError error = jvmti->AddCapabilities(&caps);

    if (error != JVMTI_ERROR_NONE) {
        Error(L"Error initialize capabilities.");
    }
}

void SetupEnv() {
    jsize count;
    if (JNI_GetCreatedJavaVMs(&jvm, 1, &count) != JNI_OK || count == 0) {
        Error(L"Failed to get the JVM");
        return;
    }

    jint res = jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        res = jvm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr);
    }

    if (res != JNI_OK) {
        Error(L"Failed to attach to the thread");
        return;
    }

    res = jvm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION_1_0);
    if (res != JNI_OK) {
        Error(L"Failed to get JVMTI env!");
        return;
    }

    if (!InitFabricClassLoader(env)) {
        Error(L"Failed when attemp get fabric class loader");
    }

    SetupCaps();
    JNIHook_Init(jvm);
}

jclass FindClassFabric(const char *fqcn) {
    if (!g_classLoader || !g_loadClass) {
        return nullptr;
    }

    jstring name = env->NewStringUTF(fqcn);

    jclass cls = (jclass) env->CallObjectMethod(
        g_classLoader,
        g_loadClass,
        name
    );

    env->DeleteLocalRef(name);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return nullptr;
    }

    return cls;
}

static jobjectArray GetMixinClassesArray() {
    const auto byte_array_class = env->FindClass("[B");
    if (!byte_array_class) {
        Error(L"Failed to get byte array class");
    }
    const auto jar_classes_array = env->NewObjectArray(
        sizeof(mixin_classes_sizes) / sizeof(mixin_classes_sizes[0]),
        byte_array_class, nullptr);
    if (!jar_classes_array) {
        Error(L"Failed to create jar classes array");
    }
    for (size_t i = 0;
         i < sizeof(mixin_classes_sizes) / sizeof(mixin_classes_sizes[0]); i++) {
        const auto class_byte_array = env->NewByteArray(mixin_classes_sizes[i]);
        if (!class_byte_array) {
            Error(L"Failed to create class byte array");
        }
        env->SetByteArrayRegion(class_byte_array, 0, mixin_classes_sizes[i],
                                mixin_classes_data[i]);
        env->SetObjectArrayElement(jar_classes_array, static_cast<jint>(i),
                                   class_byte_array);
        env->DeleteLocalRef(class_byte_array);
    }

    env->DeleteLocalRef(byte_array_class);
    return jar_classes_array;
}

static jobjectArray GetJarClassesArray() {
    const auto byte_array_class = env->FindClass("[B");
    if (!byte_array_class) {
        Error(L"Failed to get byte array class");
    }
    const auto jar_classes_array = env->NewObjectArray(
        sizeof(other_classes_sizes) / sizeof(other_classes_sizes[0]),
        byte_array_class, nullptr);
    if (!jar_classes_array) {
        Error(L"Failed to create jar classes array");
    }
    for (size_t i = 0;
         i < sizeof(other_classes_sizes) / sizeof(other_classes_sizes[0]); i++) {
        const auto class_byte_array = env->NewByteArray(other_classes_sizes[i]);
        if (!class_byte_array) {
            Error(L"Failed to create class byte array");
        }
        env->SetByteArrayRegion(class_byte_array, 0, other_classes_sizes[i],
                                other_classes_data[i]);
        env->SetObjectArrayElement(jar_classes_array, static_cast<jint>(i),
                                   class_byte_array);
        env->DeleteLocalRef(class_byte_array);
    }

    env->DeleteLocalRef(byte_array_class);
    return jar_classes_array;
}

jmethodID original_redefineClass;

void hook_redefineClass(JNIEnv *env, jclass clazz, jclass redefine, jbyteArray bytes) {
    jsize byteCount = env->GetArrayLength(bytes);

    jbyte *buffer = env->GetByteArrayElements(bytes, nullptr);
    if (buffer == nullptr) {
        Error(L"Failed jbyteArray -> char*");
        return;
    }

    jvmtiClassDefinition classDef;
    classDef.klass = redefine;
    classDef.class_byte_count = byteCount;
    classDef.class_bytes = reinterpret_cast<unsigned char *>(buffer);

    jvmtiError error = jvmti->RedefineClasses(1, &classDef);

    env->ReleaseByteArrayElements(bytes, buffer, JNI_ABORT);

    if (error != JVMTI_ERROR_NONE) {
        char *errorName = nullptr;
        jvmti->GetErrorName(error, &errorName);
        JavaPrintln(env, errorName);
        jvmti->Deallocate(reinterpret_cast<unsigned char *>(errorName));
        Error(L"Failed jvmti RedefineClasses");
        return;
    }

    JavaPrintln(env, "[Injector-Navive-Hook] Success class redefine!");
}

void StartModLoad() {
    if (env == nullptr) {
        ShowMessage(L"JNIEnv is NULL");
        return;
    }

    jclass base_class = FindClassFabric(
        "net.fabricmc.loader.impl.launch.FabricLauncherBase"
    );
    if (base_class == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        ShowMessage(L"FabricLauncherBase class not found");
        return;
    }

    jmethodID get_launcher_method = env->GetStaticMethodID(
        base_class,
        "getLauncher",
        "()Lnet/fabricmc/loader/impl/launch/FabricLauncher;"
    );
    if (get_launcher_method == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        ShowMessage(L"getLauncher() method not found");
        env->DeleteLocalRef(base_class);
        return;
    }

    jobject launcher_base = env->CallStaticObjectMethod(
        base_class,
        get_launcher_method
    );
    if (env->ExceptionCheck() || launcher_base == nullptr) {
        env->ExceptionClear();
        ShowMessage(L"getLauncher() returned NULL or threw exception");
        env->DeleteLocalRef(base_class);
        return;
    }

    jclass knot = FindClassFabric("net.fabricmc.loader.impl.launch.knot.Knot");
    if (knot == nullptr) {
        env->DeleteLocalRef(launcher_base);
        env->DeleteLocalRef(base_class);
        Error(L"Failed when attempt get Knot class");
        return;
    }

    if (!env->IsInstanceOf(launcher_base, knot)) {
        env->DeleteLocalRef(knot);
        env->DeleteLocalRef(launcher_base);
        env->DeleteLocalRef(base_class);
        Error(L"Fabric uses other loader, not Knot. Is`nt supported. Run fabric with Knot.");
        return;
    }

    jclass knotDelegateClassGot = FindClassFabric("net.fabricmc.loader.impl.launch.knot.KnotClassDelegate");
    if (knotDelegateClassGot == nullptr) {
        env->DeleteLocalRef(knot);
        env->DeleteLocalRef(launcher_base);
        env->DeleteLocalRef(base_class);
        Error(L"KnotDelegate is null");
        return;
    }

    jclass injector = DefineInjectorWithKnotClassLoader();
    if (injector == nullptr) {
        env->DeleteLocalRef(knotDelegateClassGot);
        env->DeleteLocalRef(knot);
        env->DeleteLocalRef(launcher_base);
        env->DeleteLocalRef(base_class);
        Error(L"Injector init failed");
        return;
    }

    jmethodID redefineClass = env->GetStaticMethodID(injector, "redefineClass",
                                                     "(Ljava/lang/Class;[B)V");

    if (redefineClass == nullptr) {
        Error(L"Error hooking redefine class");
    } else {
        JNIHook_Attach(redefineClass, hook_redefineClass, &original_redefineClass);
    }

    jmethodID startMethod = env->GetStaticMethodID(injector, "start",
                                                   "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[[B[[B)V");
    if (startMethod == nullptr) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(injector);
        env->DeleteLocalRef(knotDelegateClassGot);
        env->DeleteLocalRef(knot);
        env->DeleteLocalRef(launcher_base);
        env->DeleteLocalRef(base_class);
        Error(L"StartMethod failed");
        return;
    }

    jstring j_modConfig = env->NewStringUTF(mod_config);
    jstring j_mixinConfig = env->NewStringUTF(mixin_config);
    jstring j_mixinRefMap = mixin_refmap ? env->NewStringUTF(mixin_refmap) : nullptr;
    jstring j_accessWidener = access_widener ? env->NewStringUTF(access_widener) : nullptr;

    jobjectArray otherArr = GetJarClassesArray();
    jobjectArray mixinArr = GetMixinClassesArray();

    if (j_modConfig && j_mixinConfig && otherArr && mixinArr) {
        env->CallStaticVoidMethod(
            injector,
            startMethod,
            j_mixinConfig,
            j_modConfig,
            j_mixinRefMap,
            j_accessWidener,
            mixinArr,
            otherArr
        );
    }

    jmethodID initModMethod = env->GetStaticMethodID(injector, "initMod", "()V");
    if (initModMethod) {
        env->CallStaticVoidMethod(injector, initModMethod);
    }

    if (j_modConfig) env->DeleteLocalRef(j_modConfig);
    if (j_mixinConfig) env->DeleteLocalRef(j_mixinConfig);
    if (j_mixinRefMap) env->DeleteLocalRef(j_mixinRefMap);
    if (j_accessWidener) env->DeleteLocalRef(j_accessWidener);
    if (otherArr) env->DeleteLocalRef(otherArr);
    if (mixinArr) env->DeleteLocalRef(mixinArr);

    if (g_classLoader) {
        env->DeleteGlobalRef(g_classLoader);
        g_classLoader = nullptr;
    }

    env->DeleteLocalRef(injector);
    env->DeleteLocalRef(knotDelegateClassGot);
    env->DeleteLocalRef(knot);
    env->DeleteLocalRef(launcher_base);
    env->DeleteLocalRef(base_class);

    Beep(750, 160);

    FreeLibraryAndExitThread(global_dll_instance, 1);
}
