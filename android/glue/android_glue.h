/* Shared declarations for the Android glue (android_main.c, android_control.c).
 *
 * This file is GPL-3.0-or-later, like XRoar.
 */

#ifndef ANDROID_GLUE_H_
#define ANDROID_GLUE_H_

/* Commands from the Android UI (MainActivity.nativeCommand) or from start-up.
 * They are queued and run on the emulator thread, between XRoar time slices,
 * which is where XRoar expects to be called from.  Keep in step with the
 * OP_* constants in MainActivity.java. */
enum {
	OP_KEY = 1,        // a = DSCAN_* key code, b = 1 down / 0 up (CoCo keyboard)
	OP_JOY,            // a = 0 Left, 1 Right, 2 Up, 3 Down, 4 Fire 1, 5 Fire 2; b = 1/0
	OP_SOFT_RESET,
	OP_HARD_RESET,
	OP_DISK_INSERT,    // a = drive 0-3, s = path
	OP_DISK_EJECT,     // a = drive 0-3
	OP_TAPE_INSERT,    // s = path
	OP_TAPE_EJECT,
	OP_TAPE_PLAYPAUSE,
	OP_TAPE_REWIND,
	OP_TYPE,           // s = text to type, as typed (not XRoar escapes)
	OP_RUN,            // s = path: load and try to autorun
	OP_LOAD,           // s = path: load or attach, no autorun
	OP_SET,            // s = "name=value", applied live
	OP_SURFACE         // a, b = the picture view's size in pixels, as Java measured it
};

void android_enqueue(int op, int a, int b, const char *s);

/* Is any of these ROM names (NULL-terminated) in roms/ or the home folder? */
int android_rom_present(const char *const *names);
extern const char *const android_sdcdos_names[];

/* What android_main.c started with, so live changes to one of cart/sound keep the other. */
void android_set_initial_carts(const char *cart, const char *sound);

int android_is_disk_image(const char *path);
int android_is_pak(const char *path);   // .rom or .ccc: a cartridge image
int android_is_emesdos09_rom(const char *path);  // a 16K emesDOS09 ROM (needs its own cartridge)

#endif
