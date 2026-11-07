/* Host-only test harness for the Android glue: stand-ins for the Android-only
 * pieces, plus scripted scenarios that drive the real JNI entry point
 * (nativeCommand) from a second thread, as the Android UI thread would.
 *
 * Build it against a Linux desktop build of XRoar made with the same patches
 * and flags as the Android one; see tools/hosttest/run.sh.
 *
 * Scenario (env SCENARIO), run once XRoar has booted:
 *   keys    type a BASIC line with OP_KEY presses (CoCo keyboard matrix)
 *   shiftrapid  shifted keys with SHIFT released right after the key
 *   text    type a BASIC line with OP_TYPE (auto-typing)
 *   joy     hold the on-screen joystick Left, then read JOYSTK(0)
 *   nojoy   the same without pressing anything (centre value)
 *   ram16   change RAM to 16K live, then read BASIC's MEM (ram64: no change)
 *   sdcdisk disk insert/eject with a CoCoSDC plugged in
 *   misc    live settings, resets, tape and disk commands; no output check
 * Each BASIC line writes a marker to the emulated printer (-lp-file), so
 * the shell script can check what the CoCo really saw.
 *
 * This file is GPL-3.0-or-later, like XRoar.
 */

#include <SDL.h>
#include <jni.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "android_glue.h"

const char *SDL_AndroidGetExternalStoragePath(void) { return getenv("FAKE_EXT"); }
const char *SDL_AndroidGetInternalStoragePath(void) { return NULL; }
int __android_log_print(int prio, const char *tag, const char *fmt, ...) {
	va_list ap; va_start(ap, fmt); printf("[logcat %s] ", tag); vprintf(fmt, ap); printf("\n"); va_end(ap); return 0; }

int SDL_main(int argc, char **argv);
void Java_io_github_carloscamacho_xroar_MainActivity_nativeCommand(JNIEnv *, jclass, jint, jint, jint, jstring);

/* A minimal fake JNIEnv: only the two calls nativeCommand uses.  A "jstring"
 * here is just a C string. */
static const char *fake_utf(JNIEnv *e, jstring s, jboolean *c) { (void)e; (void)c; return (const char *)s; }
static void fake_rel(JNIEnv *e, jstring s, const char *u) { (void)e; (void)s; (void)u; }

static void cmd(int op, int a, int b, const char *s) {
	struct JNINativeInterface_ fns;
	memset(&fns, 0, sizeof fns);
	fns.GetStringUTFChars = fake_utf;
	fns.ReleaseStringUTFChars = fake_rel;
	const struct JNINativeInterface_ *envp = &fns;
	Java_io_github_carloscamacho_xroar_MainActivity_nativeCommand((JNIEnv *)&envp, NULL, op, a, b, (jstring)s);
}

/* DSCAN_* values from dkbd.h that the scenarios need. */
#define K_COLON 0x0a
#define K_COMMA 0x0c
#define K_MINUS 0x0d
#define K_A     0x11
#define K_SPACE 0x2f
#define K_ENTER 0x30
#define K_SHIFT 0x37

static void tap(int k) { cmd(OP_KEY, k, 1, NULL); usleep(110000); cmd(OP_KEY, k, 0, NULL); usleep(70000); }

/* Type ASCII through the on-screen keyboard's path, as a person would. */
static void press_text(const char *t) {
	for (; *t; t++) {
		int c = *t;
		if (c >= 'A' && c <= 'Z') tap(K_A + (c - 'A'));
		else if (c >= '0' && c <= '9') tap(c - '0');
		else if (c == ' ') tap(K_SPACE);
		else if (c == ':') tap(K_COLON);
		else if (c == ',') tap(K_COMMA);
		else if (c == '-') tap(K_MINUS);
		else if (c == '"' || c == '#') {
			cmd(OP_KEY, K_SHIFT, 1, NULL); usleep(60000);
			tap(c == '"' ? 2 : 3);
			cmd(OP_KEY, K_SHIFT, 0, NULL); usleep(110000);
		} else if (c == '\n') tap(K_ENTER);
	}
}

/* A shifted key as the sticky on-screen SHIFT sends it: SHIFT was latched
 * earlier, then a quick tap on the key (down, 20 ms, up), and SHIFT is let go
 * straight after the key, with no pause. */
static void rapid_shifted(int k) {
	cmd(OP_KEY, K_SHIFT, 1, NULL);
	usleep(250000);                   // latched for a while
	cmd(OP_KEY, k, 1, NULL);
	usleep(20000);
	cmd(OP_KEY, k, 0, NULL);
	cmd(OP_KEY, K_SHIFT, 0, NULL);
	usleep(300000);
}

static void *scenario(void *arg) {
	const char *name = arg;
	sleep(4);  // boot
	if (!strcmp(name, "keys")) {
		press_text("PRINT#-2,\"KEYS OK\"\n");
	} else if (!strcmp(name, "shiftrapid")) {
		press_text("PRINT");
		rapid_shifted(3);                 // #
		press_text("-2,");
		rapid_shifted(2);                 // "
		press_text("RAPID OK");
		rapid_shifted(2);                 // "
		press_text("\n");
	} else if (!strcmp(name, "text")) {
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"TEXT OK\"\n");
	} else if (!strcmp(name, "joy") || !strcmp(name, "nojoy")) {
		if (!strcmp(name, "joy")) {
			cmd(OP_JOY, 0, 1, NULL);  // Left down, held
			usleep(300000);
		}
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"JOY=\";JOYSTK(0)\n");
	} else if (!strcmp(name, "hardreset") || !strcmp(name, "softreset") || !strcmp(name, "keysrebuild")) {
		if (!strcmp(name, "hardreset")) cmd(OP_HARD_RESET, 0, 0, NULL);
		else if (!strcmp(name, "softreset")) cmd(OP_SOFT_RESET, 0, 0, NULL);
		else cmd(OP_SET, 0, 0, "machine=cocous");
		sleep(4);
		if (!strcmp(name, "keysrebuild")) press_text("PRINT#-2,\"MEM=\";MEM\n");
		else cmd(OP_TYPE, 0, 0, "PRINT#-2,\"MEM=\";MEM\n");
	} else if (!strcmp(name, "rebuild")) {
		cmd(OP_SET, 0, 0, "machine=cocous");  // same machine, rebuilt
		sleep(4);
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"MEM=\";MEM\n");
	} else if (!strcmp(name, "ram16") || !strcmp(name, "ram64")) {
		if (!strcmp(name, "ram16")) {
			cmd(OP_SET, 0, 0, "ram=16");  // a live change: rebuilds the machine
			sleep(4);
		}
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"MEM=\";MEM\n");
	} else if (!strcmp(name, "sdcdisk")) {
		const char *dsk = getenv("TEST_DSK");
		const char *dsk2 = getenv("TEST_DSK2");
		cmd(OP_DISK_INSERT, 0, 0, dsk);
		sleep(1);
		cmd(OP_DISK_INSERT, 1, 0, dsk);   // same image: refused, no floppy fallback
		sleep(1);
		cmd(OP_DISK_INSERT, 1, 0, dsk2);
		sleep(1);
		cmd(OP_DISK_EJECT, 1, 0, NULL);
		cmd(OP_DISK_EJECT, 0, 0, NULL);
		cmd(OP_DISK_INSERT, 2, 0, dsk);   // drive 2 is never the SDC's
		sleep(1);
	} else if (!strcmp(name, "misc")) {
		const char *dsk = getenv("TEST_DSK");
		cmd(OP_SET, 0, 0, "brightness=60");
		cmd(OP_SET, 0, 0, "contrast=40");
		cmd(OP_SET, 0, 0, "saturation=70");
		cmd(OP_SET, 0, 0, "hue=-20");
		cmd(OP_SET, 0, 0, "tv_input=rgb");
		cmd(OP_SET, 0, 0, "tv_input=cmp-rb");
		cmd(OP_SET, 0, 0, "ccr=simple");
		cmd(OP_SET, 0, 0, "picture=zoomed");
		cmd(OP_SET, 0, 0, "gl_filter=nearest");
		cmd(OP_SET, 0, 0, "ntsc_scaling=1");
		cmd(OP_SET, 0, 0, "cmp_colour_killer=0");
		cmd(OP_SET, 0, 0, "vdg_inverse=0");
		cmd(OP_SET, 0, 0, "gain=-12");
		cmd(OP_SET, 0, 0, "gain=-50");
		cmd(OP_SET, 0, 0, "joy_right=kjoy0");
		cmd(OP_SET, 0, 0, "joy_left=none");
		usleep(500000);
		cmd(OP_SOFT_RESET, 0, 0, NULL);
		sleep(2);
		cmd(OP_SET, 0, 0, "machine=coco2bus");
		sleep(3);
		cmd(OP_SET, 0, 0, "cart=none");
		sleep(2);
		cmd(OP_SET, 0, 0, "cart=rsdos");
		sleep(2);
		if (dsk) {
			cmd(OP_DISK_INSERT, 0, 0, dsk);
			sleep(1);
			cmd(OP_DISK_INSERT, 1, 0, dsk);
			sleep(1);
			cmd(OP_DISK_EJECT, 1, 0, NULL);
			cmd(OP_DISK_EJECT, 0, 0, NULL);
			sleep(1);
		}
		cmd(OP_TAPE_PLAYPAUSE, 0, 0, NULL);
		cmd(OP_TAPE_REWIND, 0, 0, NULL);
		cmd(OP_TAPE_EJECT, 0, 0, NULL);
		cmd(OP_HARD_RESET, 0, 0, NULL);
		sleep(2);
	}
	sleep(getenv("TAIL") ? atoi(getenv("TAIL")) : 6);
	SDL_Event q = { .type = SDL_QUIT };
	SDL_PushEvent(&q);
	return NULL;
}

int main(int argc, char **argv) {
	pthread_t t;
	if (getenv("SCENARIO")) {
		pthread_create(&t, NULL, scenario, getenv("SCENARIO"));
	}
	return SDL_main(argc, argv);
}
