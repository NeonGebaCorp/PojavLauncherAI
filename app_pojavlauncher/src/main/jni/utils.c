#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>

#include "log.h"

typedef int (*Main_Function_t)(int, char**);
typedef void (*android_update_LD_LIBRARY_PATH_t)(char*);

long shared_awt_surface;

// Helper functions
jobjectArray convert_to_char_array(JNIEnv *env, jobjectArray jstringArray);
jobjectArray convert_from_char_array(JNIEnv *env, char **charArray, int num_rows);
void free_char_array(JNIEnv *env, jobjectArray jstringArray, const char **charArray);
jstring convertStringJVM(JNIEnv* srcEnv, JNIEnv* dstEnv, jstring srcStr);
jclass findClassGlobalRef(JNIEnv *env, const char *className);
void releaseLocalRef(JNIEnv *env, jobject localRef);

// JNI functions
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeSurfaceAWT(JNIEnv *env, jclass clazz, jlong surface) {
	shared_awt_surface = surface;
}

JNIEXPORT jlong JNICALL Java_android_view_Surface_nativeGetBridgeSurfaceAWT(JNIEnv *env, jclass clazz) {
	return (jlong) shared_awt_surface;
}

JNIEXPORT jint JNICALL Java_android_os_OpenJDKNativeRegister_nativeRegisterNatives(JNIEnv *env, jclass clazz, jstring registerSymbol) {
	const char *register_symbol_c = (*env)->GetStringUTFChars(env, registerSymbol, 0);
	void *symbol = dlsym(RTLD_DEFAULT, register_symbol_c);
	if (symbol == NULL) {
		printf("dlsym %s failed: %s\n", register_symbol_c, dlerror());
		(*env)->ReleaseStringUTFChars(env, registerSymbol, register_symbol_c);
		return -1;
	}
	
	int (*registerNativesForClass)(JNIEnv*) = symbol;
	int result = registerNativesForClass(env);
	(*env)->ReleaseStringUTFChars(env, registerSymbol, register_symbol_c);
	
	return (jint) result;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setLdLibraryPath(JNIEnv *env, jclass clazz, jstring ldLibraryPath) {
	jclass exception_cls = findClassGlobalRef(env, "java/lang/UnsatisfiedLinkError");

	android_update_LD_LIBRARY_PATH_t android_update_LD_LIBRARY_PATH;

	void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
	if (!libdl_handle) {
		LOGE("dlopen libdl.so failed: %s", dlerror());
		return;
	}

	void *updateLdLibPath = dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
	if (updateLdLibPath == NULL) {
		updateLdLibPath = dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
		if (updateLdLibPath == NULL) {
			char *dl_error_c = dlerror();
			LOGE("Error getting symbol android_update_LD_LIBRARY_PATH: %s", dl_error_c);
			// (*env)->ThrowNew(env, exception_cls, dl_error_c);
			releaseLocalRef(env, exception_cls);
			dlclose(libdl_handle);
			return;
		}
	}

	android_update_LD_LIBRARY_PATH = (android_update_LD_LIBRARY_PATH_t) updateLdLibPath;
	const char* ldLibPathUtf = (*env)->GetStringUTFChars(env, ldLibraryPath, 0);
	android_update_LD_LIBRARY_PATH(ldLibPathUtf);
	(*env)->ReleaseStringUTFChars(env, ldLibraryPath, ldLibPathUtf);

	dlclose(libdl_handle);
	releaseLocalRef(env, exception_cls);
}

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_dlopen(JNIEnv *env, jclass clazz, jstring name) {
	const char *nameUtf = (*env)->GetStringUTFChars(env, name, 0);
	void* handle = dlopen(nameUtf, RTLD_GLOBAL | RTLD_LAZY);
	if (!handle) {
		LOGE("dlopen %s failed: %s", nameUtf, dlerror());
	} else {
		LOGD("dlopen %s success", nameUtf);
	}
	(*env)->ReleaseStringUTFChars(env, name, nameUtf);
	return handle != NULL;
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_chdir(JNIEnv *env, jclass clazz, jstring nameStr) {
	const char *name = (*env)->GetStringUTFChars(env, nameStr, NULL);
	int retval = chdir(name);
	(*env)->ReleaseStringUTFChars(env, nameStr, name);
	return retval;
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_executeBinary(JNIEnv *env, jclass clazz, jobjectArray cmdArgs) {
	jclass exception_cls = findClassGlobalRef(env, "java/lang/UnsatisfiedLinkError");
	jstring execFile = (*env)->GetObjectArrayElement(env, cmdArgs, 0);

	const char *exec_file_c = (*env)->GetStringUTFChars(env, execFile, 0);
	void *exec_binary_handle = dlopen(exec_file_c, RTLD_LAZY);

	(*env)->ReleaseStringUTFChars(env, execFile, exec_file_c);

	char *exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		releaseLocalRef(env, exception_cls);
		return -1;
	}

	Main_Function_t Main_Function;
	Main_Function = (Main_Function_t) dlsym(exec_binary_handle, "main");

	exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		releaseLocalRef(env, exception_cls);
		return -1;
	}

	int cmd_argv = (*env)->GetArrayLength(env, cmdArgs);
	char **cmd_args_c = convert_to_char_array(env, cmdArgs);
	int result = Main_Function(cmd_argv, cmd_args_c);
	free_char_array(env, cmdArgs, cmd_args_c);
	return result;
}

// Helper function implementations
jobjectArray convert_to_char_array(JNIEnv *env, jobjectArray jstringArray) {
	// Implementation here
}

jobjectArray convert_from_char_array(JNIEnv *env, char **charArray, int num_rows) {
	// Implementation here
}

void free_char_array(JNIEnv *env, jobjectArray jstringArray, const char **charArray) {
	// Implementation here
}

jstring convertStringJVM(JNIEnv* srcEnv, JNIEnv* dstEnv, jstring srcStr) {
	// Implementation here
}

jclass findClassGlobalRef(JNIEnv *env, const char *className) {
	jclass result = (*env)->FindClass(env, className);
	if (result == NULL) {
		LOGE("Error: Unable to find class %s", className);
	}
	return (*env)->NewGlobalRef(env, result);
}

void releaseLocalRef(JNIEnv *env, jobject localRef) {
	(*env)->DeleteLocalRef(env, localRef);
}
