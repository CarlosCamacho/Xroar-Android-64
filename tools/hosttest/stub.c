/* Host-only test harness: stand-ins for the Android-only pieces. */
#include <SDL.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdarg.h>
const char *SDL_AndroidGetExternalStoragePath(void) { return getenv("FAKE_EXT"); }
const char *SDL_AndroidGetInternalStoragePath(void) { return NULL; }
int __android_log_print(int prio, const char *tag, const char *fmt, ...) {
	va_list ap; va_start(ap, fmt); printf("[logcat %s] ", tag); vprintf(fmt, ap); printf("\n"); va_end(ap); return 0; }
int SDL_main(int argc, char **argv);
void Java_io_github_carloscamacho_xroar_MainActivity_nativeOpenFile(JNIEnv *env, jclass cls, jstring p);
/* A minimal fake JNIEnv: only the two calls nativeOpenFile uses. */
static const char *fake_utf(JNIEnv *e, jstring s, jboolean *c) { (void)e; (void)c; return (const char *)s; }
static void fake_rel(JNIEnv *e, jstring s, const char *u) { (void)e; (void)s; (void)u; }
static void *opener(void *arg) {
	sleep(3);
	struct JNINativeInterface_ fns; memset(&fns, 0, sizeof fns);
	fns.GetStringUTFChars = fake_utf; fns.ReleaseStringUTFChars = fake_rel;
	const struct JNINativeInterface_ *envp = &fns; JNIEnv *env = (JNIEnv *)&envp;
	printf("[test] nativeOpenFile(%s)\n", (char *)arg);
	Java_io_github_carloscamacho_xroar_MainActivity_nativeOpenFile(env, NULL, (jstring)arg);
	sleep(3);
	printf("[test] quitting\n");
	SDL_Event q = { .type = SDL_QUIT }; SDL_PushEvent(&q);
	return NULL;
}
int main(int argc, char **argv) {
	pthread_t t;
	if (getenv("OPEN_LATER")) pthread_create(&t, NULL, opener, getenv("OPEN_LATER"));
	return SDL_main(argc, argv);
}
