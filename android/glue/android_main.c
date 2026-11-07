/* Android entry point for XRoar (SDL2 build).
 *
 * SDLActivity loads libmain.so and calls SDL_main().  We set up a usable
 * environment and then call XRoar's own main() from main_unix.c, which is
 * compiled with -Dmain=xroar_unix_main.
 *
 * - HOME and the working directory become Android/media/<package>/ (passed by
 *   MainActivity in XROAR_HOME), where file managers can reach ROMs and
 *   xroar.conf.  XRoar's ROMPATH/CONFPATH are set at build time to
 *   "~/roms:~/.:~/.xroar/roms" and "~/.:~/.xroar" (XRoar only expands "~/").
 * - stdout/stderr go to xroar-log.txt there (there is no console).
 * - A starter xroar.conf is written if none exists.
 * - settings.conf ("key=value" lines, written by the app's settings screen)
 *   is turned into XRoar command-line options, so the first boot already has
 *   the chosen machine, cartridge and picture settings.  Command-line options
 *   override xroar.conf.
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

#include "android_glue.h"

extern int xroar_unix_main(int argc, char **argv);

static const char *starter_conf =
"# XRoar configuration (Android build)\n"
"#\n"
"# This file lives in Android/media/io.github.carloscamacho.xroar/ on your\n"
"# phone.  Most settings are in the app: open the menu (the gear button).\n"
"# What the menu sets overrides this file.  Anything else XRoar supports can\n"
"# go here: https://www.6809.org.uk/xroar/doc/xroar.shtml\n"
"#\n"
"# ROM images go in the same folder or its roms/ subfolder.  This build is\n"
"# CoCo 1/2 only.  It needs bas13.rom (or bas12/bas11/bas10), extbas11.rom\n"
"# (or extbas10) for Extended BASIC, and disk11.rom (or disk10) for disks.\n"
"\n"
"# Uncomment to turn sound off:\n"
"# ao null\n"
"\n"
"# CoCoSDC: the SD card is the sdcard/ folder next to this file; put .DSK\n"
"# images there, and optionally a STARTUP.CFG with lines like  0=GAME.DSK\n"
"# With roms/sdcdos.rom present the app plugs in a CoCoSDC by itself (the\n"
"# menu's Cartridge setting can change that).\n"
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

/* settings.conf: the last "key=value" line for key wins.  Returns 1 and copies
 * the value if present and non-empty. */
static int setting(const char *key, char *out, size_t outsz) {
	FILE *f = fopen("settings.conf", "r");
	if (!f) {
		return 0;
	}
	char line[1024];
	int found = 0;
	size_t klen = strlen(key);
	while (fgets(line, sizeof(line), f)) {
		if (line[0] == '#') continue;
		if (strncmp(line, key, klen) != 0 || line[klen] != '=') continue;
		line[strcspn(line, "\r\n")] = 0;
		if (line[klen + 1]) {
			snprintf(out, outsz, "%s", line + klen + 1);
			found = 1;
		} else {
			found = 0;
		}
	}
	fclose(f);
	return found;
}

/* Is any of these ROM images (NULL-terminated list of base names) where
 * XRoar's ROM path ("~/roms:~/.") will find it?  XRoar tries NAME.rom,
 * NAME.ROM and NAME. */
int android_rom_present(const char *const *names) {
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

const char *const android_sdcdos_names[] = { "sdcdos", "sdc-dos", "sdc_dos", "SDCDOS", NULL };

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

static void add(char **args, int *n, const char *flag, const char *val) {
	args[(*n)++] = strdup(flag);
	if (val) {
		args[(*n)++] = strdup(val);
	}
}

/* settings.conf keys that map straight onto one XRoar option.  "S" passes the
 * value through (unless empty or "auto"); "B" is a yes/no option, given as
 * -flag for 1 and -no-flag for 0. */
static const struct {
	const char *key, *flag;
	char kind;
} option_map[] = {
	{ "ccr",               "ccr",               'S' },
	{ "picture",           "vo-picture",        'S' },
	{ "gl_filter",         "gl-filter",         'S' },
	{ "brightness",        "vo-brightness",     'S' },
	{ "contrast",          "vo-contrast",       'S' },
	{ "saturation",        "vo-colour",         'S' },
	{ "hue",               "vo-hue",            'S' },
	{ "ntsc_scaling",      "vo-scale-60hz",     'B' },
	{ "cmp_colour_killer", "vo-colour-killer",  'B' },
	{ "vdg_inverse",       "invert-text",       'B' },
	{ "joy_right",         "joy-right",         'S' },
	{ "joy_left",          "joy-left",          'S' },
};

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
		mkdir("disks", 0777);
		mkdir("paks", 0777);
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

	// argv from the Java side: argv[0] is "app_process"; anything after it
	// (e.g. "-run /path/file.dsk") comes from MainActivity.getArguments().
	char **args = calloc((size_t)argc + 64, sizeof(char *));
	int n = 0;
	args[n++] = strdup("xroar");
	args[n++] = strdup("-kbd-translate");

	char v[1024];

	// Machine first: -ram and -tv-input apply to the machine chosen so far.
	char machine[64] = "cocous";  // the build's default
	if (setting("machine", v, sizeof(v)) && strcmp(v, "auto") != 0) {
		snprintf(machine, sizeof(machine), "%.63s", v);
		add(args, &n, "-machine", v);
	}
	if (setting("ram", v, sizeof(v)) && atoi(v) > 0) {
		add(args, &n, "-ram", v);
	}
	if (setting("tv_input", v, sizeof(v)) && strcmp(v, "auto") != 0) {
		add(args, &n, "-tv-input", v);
	}

	// CoCoSDC: SD card folder.
	if (dir && !conf_has("sdc-root", NULL, 0)) {
		mkdir("sdcard", 0777);
		snprintf(v, sizeof(v), "%s/sdcard", dir);
		add(args, &n, "-sdc-root", v);
	}

	// Cartridge.  "auto": a CoCoSDC when the SDC-DOS ROM is present, unless
	// xroar.conf picks a cartridge itself.
	char cart[32] = "auto";
	setting("cart", cart, sizeof(cart));
	int sdcdos = android_rom_present(android_sdcdos_names);
	int user_cart = conf_has("machine-cart", NULL, 0);
	fprintf(stderr, "android: machine '%s', cart setting '%s', sdcdos.rom %s, machine-cart in xroar.conf: %s\n",
		machine, cart, sdcdos ? "found" : "not found", user_cart ? "yes" : "no");
	// Sound cartridge: with one chosen, everything goes in a Multi-Pak, the
	// sound cartridge in slot 1 and the disk cartridge in slot 4 (which the
	// Multi-Pak selects at power-up), as software for them expects.
	char sound[32] = "none";
	setting("sound", sound, sizeof(sound));
	android_set_initial_carts(cart, sound);
	if (strcmp(sound, "none") != 0) {
		const char *disk = strcmp(cart, "auto") == 0 ? (sdcdos ? "cocosdc" : "rsdos") : cart;
		add(args, &n, "-cart", "mpi");
		if (strcmp(disk, "none") != 0) {
			snprintf(v, sizeof(v), "3=%s", disk);
			add(args, &n, "-mpi-load-cart", v);
		}
		snprintf(v, sizeof(v), "0=%s", sound);
		add(args, &n, "-mpi-load-cart", v);
		add(args, &n, "-mpi-slot", strcmp(disk, "none") != 0 ? "3" : "0");
	} else if (strcmp(cart, "auto") == 0) {
		if (sdcdos && !user_cart) {
			add(args, &n, "-cart", "cocosdc");
		}
	} else if (strcmp(cart, "none") == 0) {
		add(args, &n, "-no-cart", NULL);
	} else {
		add(args, &n, "-cart", cart);
	}

	// Picture defaults: "zoomed" shows just the CoCo's 256x192 screen, so it
	// fills the phone's width at a true 4:3, and 60 Hz scaling off keeps all 16
	// rows (with it on, a sixth of the screen is cut off or stretched).
	if (!setting("picture", v, sizeof(v))) {
		add(args, &n, "-vo-picture", "zoomed");
	}
	if (!setting("ntsc_scaling", v, sizeof(v))) {
		add(args, &n, "-no-vo-scale-60hz", NULL);
	}

	for (unsigned i = 0; i < sizeof(option_map) / sizeof(option_map[0]); i++) {
		if (!setting(option_map[i].key, v, sizeof(v))) {
			continue;
		}
		char flag[64];
		if (option_map[i].kind == 'B') {
			snprintf(flag, sizeof(flag), "%s%s", atoi(v) ? "-" : "-no-", option_map[i].flag);
			add(args, &n, flag, NULL);
		} else if (strcmp(v, "auto") != 0) {
			snprintf(flag, sizeof(flag), "-%s", option_map[i].flag);
			add(args, &n, flag, v);
		}
	}

	// Sound: mute is the quietest gain the sound code accepts.
	char gain[32];
	int have_gain = setting("gain", gain, sizeof(gain));
	if (setting("mute", v, sizeof(v)) && atoi(v)) {
		add(args, &n, "-ao-gain", "-50");
	} else if (have_gain) {
		add(args, &n, "-ao-gain", gain);
	}

	for (int i = 1; i < argc; i++) {
		// "Open with" at start-up: the same code as a file opened later, so a
		// disk image reaches the CoCoSDC's drive 0 when one is plugged in.
		if (strcmp(argv[i], "-run") == 0 && i + 1 < argc) {
			android_enqueue(OP_RUN, 0, 0, argv[i + 1]);
			i++;
			continue;
		}
		args[n++] = strdup(argv[i]);
	}

	// Disks the user left in the drives last time (inserted once XRoar is up).
	for (int d = 0; d < 4; d++) {
		char key[16];
		snprintf(key, sizeof(key), "disk%d", d);
		struct stat st;
		if (setting(key, v, sizeof(v)) && stat(v, &st) == 0) {
			android_enqueue(OP_DISK_INSERT, d, 0, v);
		}
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
