cmake_minimum_required(VERSION 4.1)
project(jvmwork)

set(CMAKE_CXX_STANDARD 20)

add_library(jvmwork SHARED
        jvm/jni.h
        jvm/jvmti.h
        jvm/jni_md.h
        main.cpp
        utils.cpp
        utils.h
        jvmwork.cpp
        jvmwork.h
        injecting_classes/injector.h
        injecting_classes/jar.h
        hooks/jnihook.hpp
        hooks/jnihook.h
        hooks/classfile.cpp
        hooks/classfile.hpp
        hooks/jnihook.cpp
        hooks/uuid.cpp
        hooks/uuid.hpp
)

# Указываем путь к JDK
set(JAVA_HOME "ваш путь к java") # я юзал jdk21

# Include для JNI
include_directories("${JAVA_HOME}/include")
include_directories("${JAVA_HOME}/include/win32")  # для Windows

# Линковка с jvm.lib
target_link_libraries(jvmwork PRIVATE "${JAVA_HOME}/lib/jvm.lib")