#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <jni.h>
#include <sys/types.h>
#include <stdbool.h>
#include <unistd.h>
#include <pthread.h>
#include <stdio.h>
#include <xhook.h>
#include <fcntl.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>

//
// Created by maks on 17.02.21.
//

static volatile jobject exitTrap_ctx;
static volatile jclass exitTrap_exitClass;
static volatile jmethodID exitTrap_staticMethod;
static JavaVM *exitTrap_jvm;

static JavaVM *stdiois_jvm;
static pthread_mutex_t log_lock = PTHREAD_MUTEX_INITIALIZER;
static bool pipe_installed;
static jmethodID logger_onEventLogged;
static jobject logListener = NULL;
static int latestlog_fd = -1;

static void writeBytes(int fd, const char *text, size_t length) {
    while (length > 0) {
        ssize_t count = write(fd, text, length);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) break;
        text += count;
        length -= count;
    }
}

// The file descriptor and listener may be replaced when an activity is recreated.
// Use a local reference for the callback and call Java outside the lock.
static jobject recordBuffer(JNIEnv *env, const char *buf, size_t len, bool newline) {
    if (strstr(buf, "Session ID is")) return NULL;
    pthread_mutex_lock(&log_lock);
    if (latestlog_fd >= 0) {
        writeBytes(latestlog_fd, buf, len);
        if (newline) writeBytes(latestlog_fd, "\n", 1);
    }
    jobject listener = logListener ? (*env)->NewLocalRef(env, logListener) : NULL;
    pthread_mutex_unlock(&log_lock);
    return listener;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, __attribute((unused)) void* reserved) {
    stdiois_jvm = vm;
    JNIEnv *env;
    (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4);
    jclass eventLogListener = (*env)->FindClass(env, "net/kdt/pojavlaunch/Logger$eventLogListener");
    logger_onEventLogged = (*env)->GetMethodID(env, eventLogListener, "onEventLogged", "(Ljava/lang/String;)V");
    return JNI_VERSION_1_4;
}

static void *logger_thread(void *argument) {
    int read_fd = (int)(intptr_t)argument;
    JNIEnv *env;
    (*stdiois_jvm)->AttachCurrentThread(stdiois_jvm, &env, NULL);
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jmethodID decode = (*env)->GetMethodID(env, string_class, "<init>", "([BLjava/lang/String;)V");
    jstring utf8 = (*env)->NewStringUTF(env, "UTF-8");
    char buf[4096];
    for (;;) {
        ssize_t count = read(read_fd, buf, sizeof(buf) - 1);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) break;
        buf[count] = 0;
        jobject listener = recordBuffer(env, buf, count, false);
        if (listener) {
            if (buf[count - 1] == '\n') count--;
            // stdout can contain raw bytes or a UTF-8 character split across two reads.
            jbyteArray bytes = (*env)->NewByteArray(env, count);
            if (bytes) {
                (*env)->SetByteArrayRegion(env, bytes, 0, count, (jbyte *)buf);
                jstring text = (*env)->NewObject(env, string_class, decode, bytes, utf8);
                if (text) {
                    (*env)->CallVoidMethod(env, listener, logger_onEventLogged, text);
                    (*env)->DeleteLocalRef(env, text);
                }
                (*env)->DeleteLocalRef(env, bytes);
            }
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            (*env)->DeleteLocalRef(env, listener);
        }
    }
    close(read_fd);
    (*stdiois_jvm)->DetachCurrentThread(stdiois_jvm);
    return NULL;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_begin(JNIEnv *env, __attribute__((unused)) jclass clazz, jstring logPath) {
    const char *path = (*env)->GetStringUTFChars(env, logPath, NULL);
    if (path == NULL) return;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    int error = fd < 0 ? errno : 0;
    (*env)->ReleaseStringUTFChars(env, logPath, path);

    pthread_mutex_lock(&log_lock);
    if (!error && !pipe_installed) {
        int ends[2];
        if (pipe2(ends, O_CLOEXEC) < 0) {
            error = errno;
        } else {
            pthread_t thread;
            error = pthread_create(&thread, NULL, logger_thread, (void *)(intptr_t)ends[0]);
            if (error) {
                close(ends[0]);
            } else {
                pthread_detach(thread);
                setvbuf(stdout, NULL, _IOLBF, 0);
                setvbuf(stderr, NULL, _IONBF, 0);
                if (dup2(ends[1], STDOUT_FILENO) < 0 || dup2(ends[1], STDERR_FILENO) < 0)
                    error = errno;
                else pipe_installed = true;
            }
            close(ends[1]);
        }
    }
    if (!error) {
        if (latestlog_fd >= 0) close(latestlog_fd);
        latestlog_fd = fd;
    } else if (fd >= 0) close(fd);
    pthread_mutex_unlock(&log_lock);
    if (error) (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), strerror(error));
}

void (*old_exit)(int code);
void custom_exit(int code) {
    if(code != 0) {
        JNIEnv *env;
        (*exitTrap_jvm)->AttachCurrentThread(exitTrap_jvm, &env, NULL);
        (*env)->CallStaticVoidMethod(env, exitTrap_exitClass, exitTrap_staticMethod, exitTrap_ctx,
                                     code);
        (*env)->DeleteGlobalRef(env, exitTrap_ctx);
        (*env)->DeleteGlobalRef(env, exitTrap_exitClass);
        (*exitTrap_jvm)->DetachCurrentThread(exitTrap_jvm);
    }
    old_exit(code);
}
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setupExitTrap(JNIEnv *env, __attribute((unused)) jclass clazz, jobject context) {
    exitTrap_ctx = (*env)->NewGlobalRef(env,context);
    (*env)->GetJavaVM(env,&exitTrap_jvm);
    exitTrap_exitClass = (*env)->NewGlobalRef(env,(*env)->FindClass(env,"net/kdt/pojavlaunch/ExitActivity"));
    exitTrap_staticMethod = (*env)->GetStaticMethodID(env,exitTrap_exitClass,"showExitMessage","(Landroid/content/Context;I)V");
    xhook_enable_debug(0);
    xhook_register(".*\\.so$", "exit", custom_exit, (void **) &old_exit);
    xhook_refresh(1);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_Logger_appendToLog(JNIEnv *env, __attribute((unused)) jclass clazz, jstring text) {
    const char *bytes = (*env)->GetStringUTFChars(env, text, NULL);
    if (!bytes) return;
    jobject listener = recordBuffer(env, bytes, (*env)->GetStringUTFLength(env, text), true);
    (*env)->ReleaseStringUTFChars(env, text, bytes);
    if (listener) {
        (*env)->CallVoidMethod(env, listener, logger_onEventLogged, text);
        (*env)->DeleteLocalRef(env, listener);
    }
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_setLogListener(JNIEnv *env, __attribute__((unused)) jclass clazz, jobject log_listener) {
    jobject next = log_listener ? (*env)->NewGlobalRef(env, log_listener) : NULL;
    pthread_mutex_lock(&log_lock);
    jobject previous = logListener;
    logListener = next;
    pthread_mutex_unlock(&log_lock);
    if (previous) (*env)->DeleteGlobalRef(env, previous);
}
