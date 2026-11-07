/* Android entry point for XRoar (SDL2 build).
 *
 * SDLActivity loads libmain.so and calls SDL_main().  We set up a usable
 * environment and then call XRoar's own main() from main_unix.c, which is
 * compiled with -Dmain=xroar_unix_main.
 *
 * - HOME and the working directory become the app's external files directory
 *   (Android/data/<package>/files), so ROMs and xroar.conf go there, as with
 *   the old 0.31 Android port.  XRoar's ROMPATH/CONFPATH are set at build time
 *   to "~/roms:~/.:~/.xroar/roms" and "~/.:~/.xroar" (XRoar only expands "~/").
 * - stdout/stderr go to xroar-log.txt in that directory (there is no console).
 * - A starter xroar.conf is written if none exists.
 * - The Android Back button toggles the on-screen keyboard instead of quitting.
 *
 * This file is GPL-3.0-or-later, like XRoar.
 */

#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#include <android/log.h>

extern int xroar_unix_main(int argc, char **argv);

static const char *starter_conf =
"# XRoar configuration (Android build)\n"
"#\n"
"# This file lives in Android/media/io.github.carloscamacho.xroar/ on your\n"
"# phone.  Put ROM images in the same folder, or in its roms/ subfolder.\n"
"# Full option list: https://www.6809.org.uk/xroar/doc/xroar.shtml\n"
"\n"
"# Which machine to start.  If this is not set, the app picks one from the\n"
"# ROMs it finds: coco3.rom -> coco3, else bas13.rom (etc.) -> cocous.\n"
"# Choices: coco3, cocous (NTSC CoCo 1/2), coco (PAL), coco2bus, dragon64, ...\n"
"# default-machine coco3\n"
"\n"
"# Touching the screen moves the right joystick; touch = fire button.\n"
"joy-right mjoy0\n"
"\n"
"# Uncomment to turn sound off:\n"
"# ao null\n"
"\n"
"# CoCoSDC\n"
"# The SD card is the sdcard/ folder next to this file: put .DSK images\n"
"# there, and optionally a STARTUP.CFG with lines like  0=GAME.DSK\n"
"# When the SDC-DOS ROM is present (roms/sdcdos.rom) and the machine is a\n"
"# CoCo, the app plugs in a CoCoSDC by itself.  To choose the cartridge\n"
"# yourself instead, uncomment ONE of these pairs:\n"
"# machine coco3\n"
"#   machine-cart cocosdc\n"
"# machine coco3\n"
"#   machine-cart rsdos\n"
"# To use a different folder as the SD card:\n"
"# sdc-root ~/sdcard\n";

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

/* xroar.conf scan: does the user's config set this key?  Only the first word
 * of each non-comment line is compared, with or without a leading '-'.  If
 * value is non-NULL, the rest of the line is copied there. */

static int conf_has(const char *key, char *value, size_t valuesz) {
	FILE *f = fopen("xroar.conf", "r");
	if (!f) {
		return 0;
	}
	char line[512];
	int found = 0;
	size_t klen = strlen(key);
	while (fgets(line, sizeof(line), f)) {
		char *p = line;
		while (*p == ' ' || *p == '\t') p++;
		if (*p == '#' || *p == '\n' || *p == 0) continue;
		if (*p == '-') p++;
		if (strncmp(p, key, klen) != 0) continue;
		char *q = p + klen;
		if (*q != ' ' && *q != '\t' && *q != '\n' && *q != '\r' && *q != 0) continue;
		found = 1;
		if (value && valuesz) {
			while (*q == ' ' || *q == '\t') q++;
			snprintf(value, valuesz, "%s", q);
			value[strcspn(value, " \t\r\n")] = 0;
		}
	}
	fclose(f);
	return found;
}

/* Is any of these ROM images (NULL-terminated list of base names) where
 * XRoar's ROM path ("~/roms:~/.") will find it?  XRoar tries NAME.rom,
 * NAME.ROM and NAME. */
static int rom_present(const char *const *names) {
	static const char *dirs[] = { "roms/", "" };
	static const char *exts[] = { ".rom", ".ROM", "" };
	char p[256];
	struct stat st;
	for (unsigned d = 0; d < 2; d++)
		for (unsigned n = 0; names[n]; n++)
			for (unsigned e = 0; e < 3; e++) {
				snprintf(p, sizeof(p), "%s%s%s", dirs[d], names[n], exts[e]);
				if (stat(p, &st) == 0 && S_ISREG(st.st_mode)) {
					return 1;
				}
			}
	return 0;
}

static const char *const sdcdos_names[] = { "sdcdos", "sdc-dos", "sdc_dos", "SDCDOS", NULL };
static const char *const coco3_names[] = { "coco3", NULL };
static const char *const cocobas_names[] = { "bas13", "bas12", "bas11", "bas10", NULL };

static int is_disk_image(const char *path) {
	const char *dot = strrchr(path, '.');
	if (!dot) return 0;
	return !strcasecmp(dot, ".dsk") || !strcasecmp(dot, ".vdk") || !strcasecmp(dot, ".jvc")
	    || !strcasecmp(dot, ".os9") || !strcasecmp(dot, ".dmk");
}

static void queue_open(char *path);

static int keyboard_watch(void *userdata, SDL_Event *event) {
	(void)userdata;
	if (event->type == SDL_KEYUP && event->key.keysym.sym == SDLK_AC_BACK) {
		if (SDL_IsTextInputActive()) {
			SDL_StopTextInput();
		} else {
			SDL_StartTextInput();
		}
	}
	return 1;
}

int SDL_main(int argc, char **argv) {
	// MainActivity sets XROAR_HOME to Android/media/<package>/, which file
	// managers can write to.  Android 11+ keeps them out of Android/data/.
	const char *dir = getenv("XROAR_HOME");
	if (!dir || !dir[0]) {
		dir = SDL_AndroidGetExternalStoragePath();
	}
	if (!dir) {
		dir = SDL_AndroidGetInternalStoragePath();
	}
	if (dir) {
		setenv("HOME", dir, 1);
		if (chdir(dir) != 0) {
			__android_log_print(ANDROID_LOG_ERROR, "XRoar", "chdir %s: %s", dir, strerror(errno));
		}
		mkdir("roms", 0777);
		char path[1024];
		snprintf(path, sizeof(path), "%s/xroar-log.txt", dir);
		if (!freopen(path, "w", stderr)) {
			__android_log_print(ANDROID_LOG_ERROR, "XRoar", "can't open %s", path);
		} else {
			setvbuf(stderr, NULL, _IONBF, 0);
			dup2(fileno(stderr), fileno(stdout));
		}
		FILE *f = fopen("xroar.conf", "r");
		if (f) {
			fclose(f);
		} else if ((f = fopen("xroar.conf", "w"))) {
			fputs(starter_conf, f);
			fclose(f);
		}
		__android_log_print(ANDROID_LOG_INFO, "XRoar", "data directory: %s", dir);
	}

	SDL_SetHint(SDL_HINT_ANDROID_TRAP_BACK_BUTTON, "1");
	SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight Portrait");
	SDL_AddEventWatch(keyboard_watch, NULL);

	// argv from the Java side: argv[0] is "app_process"; anything after it
	// (e.g. "-run /path/file.dsk") comes from MainActivity.getArguments().
	char **args = calloc(argc + 10, sizeof(char *));
	int n = 0;
	args[n++] = "xroar";
	args[n++] = "-kbd-translate";

	// CoCoSDC: SD card folder, and plug the cartridge in when it can work.
	static char sdroot[1024];
	if (dir && !conf_has("sdc-root", NULL, 0)) {
		mkdir("sdcard", 0777);
		snprintf(sdroot, sizeof(sdroot), "%s/sdcard", dir);
		args[n++] = "-sdc-root";
		args[n++] = sdroot;
	}
	char machine[64] = "";
	conf_has("default-machine", machine, sizeof(machine));
	if (!machine[0]) {
		// Not chosen in xroar.conf: pick what the ROMs in the folder can run.
		snprintf(machine, sizeof(machine), "%s",
			 rom_present(coco3_names) ? "coco3" :
			 rom_present(cocobas_names) ? "cocous" : "coco3");
		args[n++] = "-default-machine";
		args[n++] = machine;
	}
	int coco = strncmp(machine, "coco", 4) == 0;
	int sdcdos = rom_present(sdcdos_names);
	int user_cart = conf_has("machine-cart", NULL, 0);
	fprintf(stderr, "android: machine '%s', sdcdos.rom %s, machine-cart in xroar.conf: %s\n",
		machine, sdcdos ? "found" : "not found", user_cart ? "yes" : "no");
	if (coco && sdcdos && !user_cart) {
		args[n++] = "-cart";
		args[n++] = "cocosdc";
	}

	for (int i = 1; i < argc; i++) {
		// A disk image from "Open with" at start-up: hand it to the same
		// code as a file opened later, so it reaches the CoCoSDC's drive
		// 0 when one is plugged in (XRoar's -run only knows floppies).
		if (strcmp(argv[i], "-run") == 0 && i + 1 < argc && is_disk_image(argv[i + 1])) {
			queue_open(strdup(argv[i + 1]));
			i++;
			continue;
		}
		args[n++] = argv[i];
	}
	args[n] = NULL;
	for (int i = 0; i < n; i++) {
		fprintf(stderr, "arg[%d] = %s\n", i, args[i]);
	}
	int r = xroar_unix_main(n, args);
	fflush(stderr);
	// Don't let SDLActivity re-enter XRoar in a stale process.
	exit(r);
}

/* Files opened while XRoar is already running.
 *
 * MainActivity.onNewIntent() copies the file and calls nativeOpenFile() on
 * the Android UI thread.  sdl2/ui_sdl2.c is compiled with
 * -Dxroar_run=android_xroar_run, so the emulator thread picks the path up
 * between time slices and loads it there, where XRoar expects to be called.
 */

#include <jni.h>
#include <stdatomic.h>
#include <limits.h>

#include "top-config.h"
#include "auto_kbd.h"
#include "cart.h"
#include "cocosdc.h"
#include "machine.h"
#include "xroar.h"

static _Atomic(char *) pending_file = NULL;

static void queue_open(char *path) {
	char *old = atomic_exchange(&pending_file, path);
	free(old);
}

static int copy_file(const char *src, const char *dst) {
	FILE *in = fopen(src, "rb");
	if (!in) return -1;
	FILE *out = fopen(dst, "wb");
	if (!out) { fclose(in); return -1; }
	char buf[65536];
	size_t got;
	int r = 0;
	while ((got = fread(buf, 1, sizeof(buf), in)) > 0) {
		if (fwrite(buf, 1, got, out) != got) { r = -1; break; }
	}
	if (ferror(in)) r = -1;
	fclose(in);
	if (fclose(out) != 0) r = -1;
	return r;
}

/* Open a file on the emulator thread.  With a CoCoSDC plugged in, a disk
 * image goes onto the SD card (copied into OPENED/ unless it is already on
 * the card) and is mounted as SDC drive 0; then "DOS" is typed, as XRoar
 * does for a floppy.  Everything else, and any failure, takes XRoar's own
 * path (floppy drive 0, tape, cartridge, binary). */
static void android_open(const char *path) {
	struct cart *c = NULL;
	if (xroar.machine && xroar.machine->get_interface) {
		c = xroar.machine->get_interface(xroar.machine, "cart");
	}
	const char *root = cocosdc_sd_root(c);
	if (root && is_disk_image(path)) {
		char dest[PATH_MAX];
		size_t rl = strlen(root);
		int ok = 1;
		if (strncmp(path, root, rl) == 0 && path[rl] == '/') {
			snprintf(dest, sizeof(dest), "%s", path);
		} else {
			const char *base = strrchr(path, '/');
			base = base ? base + 1 : path;
			char odir[PATH_MAX];
			snprintf(odir, sizeof(odir), "%s/OPENED", root);
			mkdir(odir, 0777);
			if (snprintf(dest, sizeof(dest), "%s/%s", odir, base) >= (int)sizeof(dest)
			    || copy_file(path, dest) != 0) {
				fprintf(stderr, "android: could not copy %s to the SD card\n", path);
				ok = 0;
			}
		}
		if (ok) {
			int err = cocosdc_mount_host(c, 0, dest);
			if (err == 0) {
				fprintf(stderr, "android: CoCoSDC drive 0 = %s\n", dest);
				const char *arch = xroar.machine->config->architecture;
				if (strcmp(arch, "coco") == 0 || strcmp(arch, "coco3") == 0) {
					ak_parse_type_string(xroar.auto_kbd, "\\025DOS\\r");
				}
				return;
			}
			fprintf(stderr, "android: CoCoSDC mount of %s failed (%d); using a floppy drive\n", dest, err);
		}
	}
	xroar_load_file_by_type(path, 1);
}

JNIEXPORT void JNICALL
Java_io_github_carloscamacho_xroar_MainActivity_nativeOpenFile(JNIEnv *env, jclass cls, jstring jpath) {
	(void)cls;
	const char *p = (*env)->GetStringUTFChars(env, jpath, NULL);
	if (!p) {
		return;
	}
	char *copy = strdup(p);
	(*env)->ReleaseStringUTFChars(env, jpath, p);
	queue_open(copy);
}

void android_xroar_run(int ncycles) {
	char *path = atomic_exchange(&pending_file, NULL);
	if (path) {
		fprintf(stderr, "opening %s\n", path);
		android_open(path);
		free(path);
	}
	xroar_run(ncycles);
}
