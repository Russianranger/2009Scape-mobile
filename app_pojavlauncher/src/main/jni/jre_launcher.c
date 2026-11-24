/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include "log.h"
#include "utils.h"
#include "environ/environ.h"

#define FULL_VERSION "1.8.0-internal"
#define DOT_VERSION "1.8"

typedef jint JLI_Launch_func(int argc, char ** argv, /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* ergonomics class policy */
);

JNIEXPORT jint JNICALL Java_com_oracle_dalvik_VMLauncher_launchJVM(JNIEnv *env, jclass clazz, jstring jliPath, jobjectArray argsArray) {
    void *libjli = open_runtime_library(env, jliPath);
    if (!libjli) return -1;

    JLI_Launch_func *launch = dlsym(libjli, "JLI_Launch");
    if (!launch) {
        char message[1024];
        snprintf(message, sizeof(message), "Cannot resolve JLI_Launch in the selected Java runtime: %s", dlerror());
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError"), message);
        return -1;
    }

    // Let ART and HotSpot install and chain their own signal handlers.
    pojav_environ->dalvikJNIEnvPtr_ANDROID = env;
    jint argc = (*env)->GetArrayLength(env, argsArray);
    char **argv = convert_to_char_array(env, argsArray);
    if (!argv) return -1;
    LOGD("Calling JLI_Launch");
    jint result = launch(argc, argv, 0, NULL, 0, NULL, FULL_VERSION, DOT_VERSION,
                         argv[0], argv[0], JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
    free_char_array(env, argsArray, argv);
    free(argv);
    return result;
}
