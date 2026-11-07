/* Host-only test harness for the Android glue: stand-ins for the Android-only
 * pieces, plus scripted scenarios that drive the real JNI entry point
 * (nativeCommand) from a second thread, as the Android UI thread would.
 *
 * Build it against a Linux desktop build of XRoar made with the same patches
 * and flags as the Android one; see tools/hosttest/run.sh.
 *
 * Scenario (env SCENARIO), run once XRoar has booted:
 *   keys    type a BASIC line with OP_KEY presses (CoCo keyboard matrix)
 *   sets    apply the live settings in $SETS (k=v;k=v), then wait
 *   shiftrapid  shifted keys with SHIFT released right after the key
 *   text    type a BASIC line with OP_TYPE (auto-typing)
 *   joy     hold the on-screen joystick Left, then read JOYSTK(0)
 *   nojoy   the same without pressing anything (centre value)
 *   ram16   change RAM to 16K live, then read BASIC's MEM (ram64: no change)
 *   sid, sidlive, gmc  a sound cartridge in a Multi-Pak (from settings, or switched on live)
 *   pak     insert a cartridge (a tiny program Pak), see it start, eject, read $C000
 *   sdcdisk disk insert/eject with a CoCoSDC plugged in
 *   misc    live settings, resets, tape and disk commands; no output check
 *   emes    the emesDOS09 cartridge: optionally "tap" $EMES_ROM as a Pak and/or
 *           insert $EMES_IMG live, wait $EMES_WAIT s, then write the CoCo's
 *           text screen ($0400-$05FF) to $SCREEN_OUT; $EMES_KEYS is typed after
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

#include "top-config.h"
#include "android_glue.h"
#include "machine.h"
#include "xroar.h"

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
	} else if (!strcmp(name, "sets")) {
		// SETS="k=v;k=v": live settings after boot (for the frame-dump tests)
		char *sets = getenv("SETS") ? strdup(getenv("SETS")) : NULL;
		for (char *tok = sets ? strtok(sets, ";") : NULL; tok; tok = strtok(NULL, ";")) {
			cmd(OP_SET, 0, 0, tok);
			usleep(200000);
		}
		free(sets);
	} else if (!strcmp(name, "screenfull")) {
		// SURFACE="w,h": the app reports the picture view's real size, as MainActivity does
		if (getenv("SURFACE")) {
			int sw = 0, sh = 0;
			sscanf(getenv("SURFACE"), "%d,%d", &sw, &sh);
			cmd(OP_SURFACE, sw, sh, NULL);
			usleep(300000);
		}
		// fill the screen with text so the picture's edges show: all 16 rows
		char *sets = getenv("SETS") ? strdup(getenv("SETS")) : NULL;
		for (char *tok = sets ? strtok(sets, ";") : NULL; tok; tok = strtok(NULL, ";")) {
			cmd(OP_SET, 0, 0, tok);
			usleep(200000);
		}
		free(sets);
		cmd(OP_TYPE, 0, 0, "CLS:FORI=1TO15:PRINT@(I-1)*32,RIGHT$(\"0\"+MID$(STR$(I),2),2)+\"..............................\";:NEXT:PRINT@480,\"16.....LAST.ROW.....\";\n");
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
	} else if (!strcmp(name, "pakdbg")) {
		cmd(OP_LOAD, 0, 0, getenv("TEST_PAK"));
		sleep(3);
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"P=\";PEEK(49152);PEEK(49153);PEEK(49154);PEEK(49200)\n");
		sleep(2);
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"Q=\";PEEK(&HC000)\n");
	} else if (!strcmp(name, "sid") || !strcmp(name, "sidlive") || !strcmp(name, "gmc")) {
		// A CoCoXSID (or a Games Master Cartridge) in Multi-Pak slot 1 with the disk
		// controller in slot 4.  Select the sound slot for SCS ($FF7F = $30; CTS stays
		// on the disk), play a sawtooth on voice 3 and read the chip's registers back.
		if (!strcmp(name, "sidlive")) {
			cmd(OP_SET, 0, 0, "sound=cocoxsid");   // changed while running: builds the Multi-Pak
			sleep(5);
		}
		if (!strcmp(name, "gmc")) {
			cmd(OP_TYPE, 0, 0, "POKE&HFF7F,&H30:POKE&HFF41,&H9F:POKE&HFF41,&H8F:POKE&HFF41,&H0F:PRINT#-2,\"GMC DONE C=\";PEEK(&HC000)\n");
			sleep(4);
		} else {
			cmd(OP_TYPE, 0, 0, "POKE&HFF7F,&H30:POKE&HFF58,15:POKE&HFF4E,0:POKE&HFF4F,&H40:POKE&HFF53,0:POKE&HFF54,&HF0:POKE&HFF52,&H21\n");
			sleep(3);
			cmd(OP_TYPE, 0, 0, "FORI=1TO400:NEXT:PRINT#-2,\"ENV=\";PEEK(&HFF5C):PRINT#-2,\"OSC=\";PEEK(&HFF5B);PEEK(&HFF5B);PEEK(&HFF5B):PRINT#-2,\"C=\";PEEK(&HC000)\n");
			sleep(6);
		}
	} else if (!strcmp(name, "program")) {
		// a multi-line program (TEST_PROGRAM file), typed in one go, then listed on the printer
		const char *f = getenv("TEST_PROGRAM");
		FILE *fp = f ? fopen(f, "rb") : NULL;
		static char buf[65536];
		size_t n = fp ? fread(buf, 1, sizeof(buf) - 1, fp) : 0;
		buf[n] = 0;
		if (fp) fclose(fp);
		cmd(OP_TYPE, 0, 0, buf);
		sleep(getenv("TYPE_WAIT") ? atoi(getenv("TYPE_WAIT")) : 10);
		cmd(OP_TYPE, 0, 0, "LLIST\n");
		sleep(6);
	} else if (!strcmp(name, "pak")) {
		// PEEK(&HC000): the disk controller's ROM starts "DK" (68).  The pak made by
		// run.sh is a little program that prints PAKSTART OK on the printer when it
		// starts.  Inserting it resets the machine and starts it, as on a real CoCo.
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"A=\";PEEK(&HC000)\n");
		sleep(2);
		cmd(OP_LOAD, 0, 0, getenv("TEST_PAK"));
		sleep(4);
		cmd(OP_SET, 0, 0, "cart=rsdos");   // eject: the chosen cartridge comes back (hard reset)
		sleep(4);
		cmd(OP_TYPE, 0, 0, "PRINT#-2,\"C=\";PEEK(&HC000)\n");
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
	if (!strcmp(name, "emes")) {
		if (getenv("EMES_ROM") && *getenv("EMES_ROM")) {
			cmd(OP_RUN, 0, 0, getenv("EMES_ROM"));
			sleep(2);
		}
		if (getenv("EMES_IMG") && *getenv("EMES_IMG")) {
			char kv[1100];
			snprintf(kv, sizeof(kv), "emes_image=%s", getenv("EMES_IMG"));
			cmd(OP_SET, 0, 0, kv);
			sleep(1);
			cmd(OP_HARD_RESET, 0, 0, NULL);
		}
		sleep(getenv("EMES_WAIT") ? atoi(getenv("EMES_WAIT")) : 6);
		if (getenv("EMES_KEYS")) {
			cmd(OP_TYPE, 0, 0, getenv("EMES_KEYS"));
			sleep(getenv("EMES_WAIT2") ? atoi(getenv("EMES_WAIT2")) : 5);
		}
		FILE *f = getenv("SCREEN_OUT") ? fopen(getenv("SCREEN_OUT"), "wb") : NULL;
		if (f && xroar.machine) {
			for (unsigned a = 0x400; a < 0x600; a++) {
				fputc(xroar.machine->read_byte(xroar.machine, a, 0), f);
			}
		}
		if (f) {
			fclose(f);
		}
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
