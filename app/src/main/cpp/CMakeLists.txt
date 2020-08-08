# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

set(pathToProject /Users/pj/AndroidStudioProjects/ArucoSLAM)
set(pathToOpenCv /Users/pj/APMS/OpenCV-android-sdk)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             native-lib.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.



include_directories(${pathToOpenCv}/sdk/native/jni/include)

add_library(lib_opencv SHARED IMPORTED)
set_target_properties(lib_opencv PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../myJniLibs/${ANDROID_ABI}/libopencv_java3.so)

add_library(libnonfree SHARED IMPORTED )
set_target_properties(libnonfree PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../myJniLibs/${ANDROID_ABI}/libnonfree.so)

add_library(libgnustl SHARED IMPORTED )
set_target_properties(libgnustl PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../myJniLibs/${ANDROID_ABI}/libgnustl_shared.so)

target_link_libraries( # Specifies the target library.
        native-lib

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}

        # opencv ndk
        lib_opencv
        libgnustl
        libnonfree)