/* Android UI -> XRoar control layer.
 *
 * MainActivity.nativeCommand() queues commands from the Android UI thread.
 * sdl2/ui_sdl2.c is compiled with -Dxroar_run=android_xroar_run, so each
 * time XRoar's main loop gives the emulator a slice, android_xroar_run()
 * first runs the queued commands (on the emulator thread, where XRoar expects
 * to be called), then keeps the picture sized to the real Android surface,
 * then calls the real xroar_run().
 *
 * This file is GPL-3.0-or-later, like XRoar.
 */

#ifndef SDL_MAIN_HANDLED
#define SDL_MAIN_HANDLED
#endif
#include <SDL.h>

#include <jni.h>
#include <limits.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#include "top-config.h"
#include "auto_kbd.h"
#include "cart.h"
#include "cocosdc.h"
#include "dkbd.h"
#include "hkbd.h"
#include "joystick.h"
#include "keyboard.h"
#include "machine.h"
#include "ui.h"
#include "vo.h"
#include "xconfig.h"
#include "xroar.h"
#include "sdl2/common.h"

#include "android_glue.h"

void xroar_run(int ncycles);

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Command queue */

struct cmd {
	int op, a, b;
	char *s;
	struct cmd *next;
};

static pthread_mutex_t qlock = PTHREAD_MUTEX_INITIALIZER;
static struct cmd *qhead, *qtail;

void android_enqueue(int op, int a, int b, const char *s) {
	struct cmd *c = calloc(1, sizeof(*c));
	if (!c) {
		return;
	}
	c->op = op;
	c->a = a;
	c->b = b;
	c->s = s ? strdup(s) : NULL;
	pthread_mutex_lock(&qlock);
	if (qtail) {
		qtail->next = c;
	} else {
		qhead = c;
	}
	qtail = c;
	pthread_mutex_unlock(&qlock);
}

static struct cmd *dequeue(void) {
	pthread_mutex_lock(&qlock);
	struct cmd *c = qhead;
	if (c) {
		qhead = c->next;
		if (!qhead) {
			qtail = NULL;
		}
	}
	pthread_mutex_unlock(&qlock);
	return c;
}

JNIEXPORT void JNICALL
Java_io_github_carloscamacho_xroar_MainActivity_nativeCommand(JNIEnv *env, jclass cls,
							      jint op, jint a, jint b, jstring js) {
	(void)cls;
	const char *s = NULL;
	if (js) {
		s = (*env)->GetStringUTFChars(env, js, NULL);
	}
	android_enqueue(op, a, b, s);
	if (s) {
		(*env)->ReleaseStringUTFChars(env, js, s);
	}
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Messages for the user: written to the log, and held for the Android UI to
 * show (MainActivity polls nativePollMessage and shows a toast). */

#include <stdarg.h>

static char msg_buf[1024];
static size_t msg_len;

static void say(const char *fmt, ...) {
	char line[300];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(line, sizeof(line), fmt, ap);
	va_end(ap);
	fprintf(stderr, "android: %s\n", line);
	pthread_mutex_lock(&qlock);
	size_t n = strlen(line);
	if (msg_len + n + 2 < sizeof(msg_buf)) {
		if (msg_len) {
			msg_buf[msg_len++] = '\n';
		}
		memcpy(msg_buf + msg_len, line, n);
		msg_len += n;
		msg_buf[msg_len] = 0;
	}
	pthread_mutex_unlock(&qlock);
}

JNIEXPORT jstring JNICALL
Java_io_github_carloscamacho_xroar_MainActivity_nativePollMessage(JNIEnv *env, jclass cls) {
	(void)cls;
	char out[sizeof(msg_buf)];
	pthread_mutex_lock(&qlock);
	memcpy(out, msg_buf, msg_len + 1);
	size_t n = msg_len;
	msg_len = 0;
	msg_buf[0] = 0;
	pthread_mutex_unlock(&qlock);
	return n ? (*env)->NewStringUTF(env, out) : NULL;
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Keys */

/* A tap can be shorter than the CoCo's keyboard scan, so a key is held for at
 * least this long (milliseconds of host time) before it is released. */
#define MIN_HOLD_MS 80
#define KEY_SHIFT 0x37  // DSCAN_SHIFT

static uint8_t key_down[64];
static Uint32 key_pressed_at[64];
static Uint32 key_release_at[64];  // 0 = no release pending

static void key_release_now(int k) {
	struct keyboard_interface *ki = xroar.keyboard_interface;
	if (ki && key_down[k]) {
		keyboard_release(ki, k);
	}
	key_down[k] = 0;
	key_release_at[k] = 0;
}

static void key_event(int k, int down) {
	struct keyboard_interface *ki = xroar.keyboard_interface;
	if (!ki || k < 0 || k >= 64) {
		return;
	}
	if (down) {
		key_release_at[k] = 0;
		if (!key_down[k]) {
			keyboard_press(ki, k);
			key_down[k] = 1;
		}
		key_pressed_at[k] = SDL_GetTicks();
	} else if (key_down[k]) {
		Uint32 now = SDL_GetTicks();
		Uint32 due = key_pressed_at[k] + MIN_HOLD_MS;
		if (k == KEY_SHIFT) {
			// Never let SHIFT go before a key typed with it: wait for any
			// release already pending on another key.
			for (int j = 0; j < 64; j++) {
				if (j != k && key_down[j] && key_release_at[j]
				    && (Sint32)(key_release_at[j] + 1 - due) > 0) {
					due = key_release_at[j] + 1;
				}
			}
		}
		if ((Sint32)(now - due) >= 0) {
			key_release_now(k);
		} else {
			key_release_at[k] = due;
		}
	}
}

static void service_key_releases(void) {
	Uint32 now = SDL_GetTicks();
	for (int k = 0; k < 64; k++) {
		if (key_release_at[k] && (Sint32)(now - key_release_at[k]) >= 0) {
			key_release_now(k);
		}
	}
}

/* On-screen joystick: the same host keys as XRoar's keyboard joystick
 * "kjoy0" (cursors, Alt_L, Super_L), so it works when a port is set to it. */
static void joy_event(int which, int down) {
	static const uint8_t keys[6] = {
		hk_scan_Left, hk_scan_Right, hk_scan_Up, hk_scan_Down,
		hk_scan_Alt_L, hk_scan_Super_L
	};
	if (which < 0 || which >= 6) {
		return;
	}
	if (down) {
		hk_scan_press(keys[which]);
	} else {
		hk_scan_release(keys[which]);
	}
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Files and disks */

static struct cart *current_cart(void) {
	if (xroar.machine && xroar.machine->get_interface) {
		return xroar.machine->get_interface(xroar.machine, "cart");
	}
	return NULL;
}

int android_is_disk_image(const char *path) {
	const char *dot = strrchr(path, '.');
	if (!dot) return 0;
	return !strcasecmp(dot, ".dsk") || !strcasecmp(dot, ".vdk") || !strcasecmp(dot, ".jvc")
	    || !strcasecmp(dot, ".os9") || !strcasecmp(dot, ".dmk");
}

int android_is_pak(const char *path) {
	const char *dot = strrchr(path, '.');
	return dot && (!strcasecmp(dot, ".rom") || !strcasecmp(dot, ".ccc"));
}

static const char *base_name(const char *path);

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

static const char *base_name(const char *path) {
	const char *b = strrchr(path, '/');
	return b ? b + 1 : path;
}

/* Put a disk image into drive `drive`.  With a CoCoSDC plugged in, drives 0
 * and 1 are the SDC's: the image is copied onto the SD card (into OPENED/,
 * unless it is already there) and mounted; if that fails, nothing falls back
 * to the floppy controller, which the SDC replaces.  Otherwise the image goes
 * into the floppy controller's drive.  If autorun, "DOS" is typed on drive 0,
 * as XRoar does. */
static void insert_disk(int drive, const char *path, int autorun) {
	if (drive < 0 || drive > 3) {
		return;
	}
	struct cart *c = current_cart();
	const char *root = cocosdc_sd_root(c);
	if (root && drive < 2) {
		char dest[PATH_MAX];
		size_t rl = strlen(root);
		if (strncmp(path, root, rl) == 0 && path[rl] == '/') {
			snprintf(dest, sizeof(dest), "%s", path);
		} else {
			char odir[PATH_MAX];
			snprintf(odir, sizeof(odir), "%s/OPENED", root);
			mkdir(odir, 0777);
			if (snprintf(dest, sizeof(dest), "%s/%s", odir, base_name(path)) >= (int)sizeof(dest)
			    || copy_file(path, dest) != 0) {
				say("Could not copy %s onto the SD card", base_name(path));
				return;
			}
		}
		int err = cocosdc_mount_host(c, (unsigned)drive, dest);
		if (err == 0) {
			say("SD drive %d: %s", drive, base_name(dest));
			if (autorun && drive == 0) {
				ak_parse_type_string(xroar.auto_kbd, "\\025DOS\\r");
			}
		} else if (err == 32) {  // SDC_ERR_INUSE
			say("%s is already in the other SD drive", base_name(dest));
		} else {
			say("SD drive %d could not mount %s (error %d)", drive, base_name(dest), err);
		}
		return;
	}
	if (autorun) {
		xroar_load_disk(path, drive, 1);
	} else {
		xroar_insert_disk_file(drive, path);
	}
	say("Floppy drive %d: %s", drive, base_name(path));
}

static void eject_disk(int drive) {
	if (drive < 0 || drive > 3) {
		return;
	}
	struct cart *c = current_cart();
	if (cocosdc_sd_root(c) && drive < 2) {
		cocosdc_eject(c, (unsigned)drive);
		say("SD drive %d ejected", drive);
	} else {
		xroar_eject_disk(drive);
		say("Floppy drive %d ejected", drive);
	}
}

static void open_file(const char *path, int autorun) {
	fprintf(stderr, "android: %s %s\n", autorun ? "run" : "load", path);
	say("%s %s", autorun ? "Running" : "Loading", base_name(path));
	if (android_is_disk_image(path)) {
		insert_disk(0, path, autorun);
	} else if (android_is_pak(path)) {
		// A cartridge replaces whatever is in the slot, and Disk BASIC's hooks
		// still point into the old ROM, so the machine has to be reset, and a
		// Pak is made to start itself at power-up.  Like putting one in a real
		// CoCo, this always starts it (Load and Run are the same for a Pak).
		say("Cartridge: %s", base_name(path));
		xroar_load_file_by_type(path, 1);
	} else {
		xroar_load_file_by_type(path, autorun);
	}
}

/* Type text as the user typed it: XRoar's parser reads backslash escapes, so
 * backslashes are doubled and line ends become \r. */
static void type_text(const char *text) {
	size_t n = strlen(text);
	char *esc = malloc(n * 2 + 1);
	if (!esc) {
		return;
	}
	char *p = esc;
	for (size_t i = 0; i < n; i++) {
		unsigned char ch = (unsigned char)text[i];
		if (ch == '\\') {
			*p++ = '\\';
			*p++ = '\\';
		} else if (ch == '\r') {
			if (i + 1 < n && text[i + 1] == '\n') {
				continue;
			}
			*p++ = '\\';
			*p++ = 'r';
		} else if (ch == '\n') {
			*p++ = '\\';
			*p++ = 'r';
		} else if (ch >= 0x20 && ch < 0x7f) {
			*p++ = (char)ch;
		}
		// anything else (tabs, non-ASCII) has no CoCo key: dropped
	}
	*p = 0;
	ak_parse_type_string(xroar.auto_kbd, esc);
	free(esc);
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Live settings: "name=value" */

static int enum_lookup(const struct xconfig_enum *l, const char *name, int *val) {
	for (; l->name; l++) {
		if (strcmp(l->name, name) == 0) {
			*val = l->value;
			return 1;
		}
	}
	return 0;
}

static const char *resolve_cart(const char *v) {
	if (strcmp(v, "auto") == 0) {
		return android_rom_present(android_sdcdos_names) ? "cocosdc" : "rsdos";
	}
	return v;
}

/* The cartridge setup is two settings: "cart" (the disk cartridge: auto, rsdos,
 * cocosdc, none) and "sound" (none, gmc, cocoxsid).  With a sound cartridge
 * everything is put in a Multi-Pak: sound in slot 1, disk in slot 4. */
static char cur_cart[32] = "auto";
static char cur_sound[32] = "none";

void android_set_initial_carts(const char *cart, const char *sound) {
	snprintf(cur_cart, sizeof(cur_cart), "%s", cart);
	snprintf(cur_sound, sizeof(cur_sound), "%s", sound);
}

static void apply_carts(void) {
	const char *disk = resolve_cart(cur_cart);
	int has_disk = strcmp(disk, "none") != 0;
	struct cart_config *cc = NULL;
	if (strcmp(cur_sound, "none") == 0) {
		cc = has_disk ? cart_config_by_name(disk) : NULL;
	} else {
		cc = cart_config_by_name("android-mpi");
		if (!cc) {
			cc = cart_config_new();
			cc->name = strdup("android-mpi");
			cc->description = strdup("Multi-Pak (sound and disk)");
			cc->type = strdup("mpi");
		}
		for (int i = 0; i < 4; i++) {
			free(cc->mpi.slot_cart_name[i]);
			cc->mpi.slot_cart_name[i] = NULL;
		}
		cc->mpi.slot_cart_name[0] = strdup(cur_sound);
		if (has_disk) {
			cc->mpi.slot_cart_name[3] = strdup(disk);
		}
		cc->mpi.initial_slot = has_disk ? 3 : 0;
	}
	char what[96];
	if (strcmp(cur_sound, "none") != 0) {
		snprintf(what, sizeof(what), "Multi-Pak with %s%s", cur_sound, has_disk ? " and disk" : "");
	} else {
		snprintf(what, sizeof(what), "%s", has_disk ? disk : "none");
	}
	say("Cartridge: %s", what);
	ui_update_state(-1, ui_tag_cartridge, cc ? cc->id : 0, NULL);
	xroar_hard_reset();
}

static void apply_setting(char *kv) {
	char *eq = strchr(kv, '=');
	if (!eq) {
		return;
	}
	*eq = 0;
	const char *k = kv, *v = eq + 1;
	int val;
	fprintf(stderr, "android: set %s = %s\n", k, v);

	if (strcmp(k, "machine") == 0) {
		struct machine_config *mc = machine_config_by_name(v);
		if (mc) {
			ui_update_state(-1, ui_tag_machine, mc->id, NULL);
			xroar_hard_reset();
		}
	} else if (strcmp(k, "ram") == 0) {
		// XRoar rebuilds the whole machine from its config when asked to
		// select a machine, so a new RAM size takes effect at once.  (That
		// also resets the cartridge: MainActivity re-sends the cart setting.)
		struct machine_config *mc = xroar.machine_config;
		int ram = atoi(v);
		if (mc && ram > 0) {
			mc->ram = ram;
			mc->ram_org = ANY_AUTO;  // chip layout is derived from the size
			ui_update_state(-1, ui_tag_machine, mc->id, NULL);
		}
	} else if (strcmp(k, "cart") == 0) {
		snprintf(cur_cart, sizeof(cur_cart), "%s", v);
		apply_carts();
	} else if (strcmp(k, "sound") == 0) {
		snprintf(cur_sound, sizeof(cur_sound), "%s", v);
		apply_carts();
	} else if (strcmp(k, "tv_input") == 0) {
		if (enum_lookup(machine_tv_input_list, v, &val))
			ui_update_state(-1, ui_tag_tv_input, val, NULL);
	} else if (strcmp(k, "ccr") == 0) {
		if (enum_lookup(vo_cmp_ccr_list, v, &val))
			ui_update_state(-1, ui_tag_ccr, val, NULL);
	} else if (strcmp(k, "picture") == 0) {
		if (enum_lookup(vo_viewport_list, v, &val))
			ui_update_state(-1, ui_tag_picture, val, NULL);
	} else if (strcmp(k, "gl_filter") == 0) {
		if (enum_lookup(vo_gl_filter_list, v, &val))
			ui_update_state(-1, ui_tag_gl_filter, val, NULL);
	} else if (strcmp(k, "brightness") == 0) {
		ui_update_state(-1, ui_tag_brightness, atoi(v), NULL);
	} else if (strcmp(k, "contrast") == 0) {
		ui_update_state(-1, ui_tag_contrast, atoi(v), NULL);
	} else if (strcmp(k, "saturation") == 0) {
		ui_update_state(-1, ui_tag_saturation, atoi(v), NULL);
	} else if (strcmp(k, "hue") == 0) {
		ui_update_state(-1, ui_tag_hue, atoi(v), NULL);
	} else if (strcmp(k, "ntsc_scaling") == 0) {
		ui_update_state(-1, ui_tag_ntsc_scaling, atoi(v), NULL);
	} else if (strcmp(k, "cmp_colour_killer") == 0) {
		ui_update_state(-1, ui_tag_cmp_colour_killer, atoi(v), NULL);
	} else if (strcmp(k, "vdg_inverse") == 0) {
		ui_update_state(-1, ui_tag_vdg_inverse, atoi(v), NULL);
	} else if (strcmp(k, "gain") == 0) {
		float db = (float)atof(v);  // -50 or lower is silence
		ui_update_state(-1, ui_tag_gain, 0, &db);
	} else if (strcmp(k, "joy_right") == 0 || strcmp(k, "joy_left") == 0) {
		struct joystick_config *jc = strcmp(v, "none") ? joystick_config_by_name(v) : NULL;
		int port = (strcmp(k, "joy_right") == 0) ? 0 : 1;
		ui_update_state(-1, ui_tag_joystick_port, port, (void *)(intptr_t)(jc ? jc->id : 0));
	}
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

static void run_command(struct cmd *c) {
	switch (c->op) {
	case OP_KEY:           key_event(c->a, c->b); break;
	case OP_JOY:           joy_event(c->a, c->b); break;
	case OP_SOFT_RESET:    xroar_soft_reset(); break;
	case OP_HARD_RESET:    xroar_hard_reset(); break;
	case OP_DISK_INSERT:   if (c->s) insert_disk(c->a, c->s, 0); break;
	case OP_DISK_EJECT:    eject_disk(c->a); break;
	case OP_TAPE_INSERT:   if (c->s) xroar_insert_input_tape_file(c->s); break;
	case OP_TAPE_EJECT:    xroar_eject_input_tape(); break;
	case OP_TAPE_PLAYPAUSE: ui_update_state(-1, ui_tag_action, ui_action_tape_play_pause, NULL); break;
	case OP_TAPE_REWIND:   ui_update_state(-1, ui_tag_action, ui_action_tape_input_rewind, NULL); break;
	case OP_TYPE:          if (c->s) type_text(c->s); break;
	case OP_RUN:           if (c->s) open_file(c->s, 1); break;
	case OP_LOAD:          if (c->s) open_file(c->s, 0); break;
	case OP_SET:           if (c->s) apply_setting(c->s); break;
	default: break;
	}
}

/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
/* Keep the picture the right size.  XRoar learns the window size from SDL
 * events; on Android the Java layout can change the surface under it, so ask
 * the renderer directly (its output size is the coordinate system the picture
 * is drawn in) and log what we find, for bug reports. */

static int last_w, last_h;

static void check_display(void) {
	struct ui_sdl2_interface *u = global_uisdl2;
	if (!u || !u->vo_window) {
		return;
	}
	int ww = 0, wh = 0, w = 0, h = 0;
	SDL_GetWindowSize(u->vo_window, &ww, &wh);
	SDL_Renderer *r = SDL_GetRenderer(u->vo_window);
	if (r) {
		SDL_GetRendererOutputSize(r, &w, &h);
	}
	if (w <= 0 || h <= 0) {
		w = ww;
		h = wh;
	}
	if (w > 0 && h > 0 && (w != last_w || h != last_h)) {
		last_w = w;
		last_h = h;
		sdl_vo_notify_size_changed(u, w, h);
		struct vo_interface *vo = u->ui_interface.vo_interface;
		fprintf(stderr, "android: display: window %dx%d, renderer output %dx%d, picture area %d,%d %dx%d\n",
			ww, wh, w, h,
			vo->picture_area.x, vo->picture_area.y, vo->picture_area.w, vo->picture_area.h);
	}
}

/* Test aid: with XROAR_DUMP_FRAME=<file.ppm> in the environment, write what
 * the renderer shows (as the phone would) to a PPM a few seconds after the
 * first frame, then carry on.  Used by tools/hosttest to look at the picture
 * and measure it; it does nothing in the app. */
static void maybe_dump_frame(void) {
	static int done;
	static Uint32 start;
	const char *path = getenv("XROAR_DUMP_FRAME");
	if (!path || done || !global_uisdl2 || !global_uisdl2->vo_window) {
		return;
	}
	if (!start) {
		start = SDL_GetTicks();
		return;
	}
	const char *delay = getenv("XROAR_DUMP_DELAY_MS");
	if (SDL_GetTicks() - start < (Uint32)(delay ? atoi(delay) : 4000)) {
		return;
	}
	done = 1;
	SDL_Renderer *r = SDL_GetRenderer(global_uisdl2->vo_window);
	int w = 0, h = 0;
	if (!r || SDL_GetRendererOutputSize(r, &w, &h) != 0 || w <= 0 || h <= 0) {
		return;
	}
	uint8_t *px = malloc((size_t)w * h * 4);
	if (!px) {
		return;
	}
	if (SDL_RenderReadPixels(r, NULL, SDL_PIXELFORMAT_RGB24, px, w * 3) == 0) {
		FILE *f = fopen(path, "wb");
		if (f) {
			fprintf(f, "P6\n%d %d\n255\n", w, h);
			fwrite(px, 3, (size_t)w * h, f);
			fclose(f);
			fprintf(stderr, "android: frame %dx%d written to %s\n", w, h, path);
		}
	}
	free(px);
}

void android_xroar_run(int ncycles) {
	struct cmd *c;
	while ((c = dequeue()) != NULL) {
		run_command(c);
		free(c->s);
		free(c);
	}
	service_key_releases();
	check_display();
	maybe_dump_frame();
	xroar_run(ncycles);
}
