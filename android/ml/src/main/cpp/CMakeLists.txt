cmake_minimum_required(VERSION 3.22)
project(vachak_ct2_jni)

# CTranslate2 CPU-only cross-compile for arm64-v8a
# NDK: $ANDROID_NDK or $ANDROID_NDK_HOME, API 28, -DANDROID_ABI=arm64-v8a
# Host fallback (no NDK) builds x86_64 for verification; CI uses NDK.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(WITH_CPU_DISPATCH "CPU dispatch" ON)
option(WITH_CUDA "CUDA" OFF)
option(WITH_CUDNN "cuDNN" OFF)
option(BUILD_CLI "CLI" OFF)
option(BUILD_TESTS "Tests" OFF)
option(ENABLE_PROFILING "Profiling" OFF)
option(WITH_OPENMP "OpenMP" ON)

# Fetch CTranslate2 source (pinned, CPU-only, no CLI/tests)
include(FetchContent)
FetchContent_Declare(
  ctranslate2
  GIT_REPOSITORY https://github.com/OpenNMT/CTranslate2.git
  GIT_TAG v4.4.0
  GIT_SHALLOW TRUE
)
# Disable unwanted backends before add_subdirectory (MKL/DNNL require x86, not Android arm64)
set(WITH_CUDA OFF CACHE BOOL "" FORCE)
set(WITH_CUDNN OFF CACHE BOOL "" FORCE)
set(WITH_MKL OFF CACHE BOOL "" FORCE)
set(WITH_DNNL OFF CACHE BOOL "" FORCE)
set(WITH_ACCELERATE OFF CACHE BOOL "" FORCE)
set(WITH_RUY ON CACHE BOOL "" FORCE)
set(WITH_OPENMP ON CACHE BOOL "" FORCE)
set(OPENMP_RUNTIME "COMP" CACHE STRING "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_PROFILING OFF CACHE BOOL "" FORCE)
# Note: OpenMP OFF for Android 2GB sequential (Translator numThreads=1); Ruy kept for CPU dispatch, OPENMP_RUNTIME NONE to avoid Intel libiomp5 search on Android
# Android bionic has no pthread_setaffinity_np - force CT2 thread_pool.cc throw-path via _OPENMP define
add_definitions(-D_OPENMP)
FetchContent_MakeAvailable(ctranslate2)

add_library(vachak_ct2_jni SHARED
  ctranslate2_jni.cpp
  ctranslate2_singleton.cpp
)

# CTranslate2 v4.4.0 target is `ctranslate2` (no namespace alias on Android static); try both
if(TARGET ctranslate2::ctranslate2)
  target_link_libraries(vachak_ct2_jni PRIVATE ctranslate2::ctranslate2)
elseif(TARGET ctranslate2)
  target_link_libraries(vachak_ct2_jni PRIVATE ctranslate2)
else()
  message(WARNING "CTranslate2 target not found - building stub")
endif()
find_library(log-lib log)
if(log-lib)
  target_link_libraries(vachak_ct2_jni PRIVATE ${log-lib})
endif()

# Android NDK specifics (let Gradle handle per-ABI output dir - do NOT hardcode arm64-v8a or x86_64 breaks)
if(ANDROID)
  target_compile_options(vachak_ct2_jni PRIVATE -O3 -fPIC -ffast-math)
endif()
