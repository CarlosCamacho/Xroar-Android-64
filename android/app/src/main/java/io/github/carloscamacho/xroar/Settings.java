package io.github.carloscamacho.xroar;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/* The app's settings: "key=value" lines in settings.conf in the app's folder.
 *
 * android_main.c reads the same file at start-up and turns the emulator keys
 * into XRoar command-line options; keys starting "ui_" belong to the Android
 * UI only.  A key that is absent means "leave XRoar's default". */
final class Settings {
    private static final String TAG = "XRoar";

    private final File file;
    private final Map<String, String> map = new LinkedHashMap<>();

    Settings(File home) {
        file = new File(home, "settings.conf");
        load();
    }

    private void load() {
        map.clear();
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    map.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't read " + file, e);
        }
    }

    private void save() {
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) {
            w.write("# Written by the app's settings screen.  Edit with care.\n");
            for (Map.Entry<String, String> e : map.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "can't write " + tmp, e);
            return;
        }
        if (!tmp.renameTo(file)) {
            Log.e(TAG, "can't replace " + file);
        }
    }

    boolean has(String key) {
        return map.containsKey(key);
    }

    String get(String key, String def) {
        String v = map.get(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    boolean getBool(String key, boolean def) {
        String v = get(key, "");
        if (v.isEmpty()) {
            return def;
        }
        return !v.equals("0");
    }

    void set(String key, String value) {
        map.put(key, value);
        save();
    }

    void setInt(String key, int value) {
        set(key, Integer.toString(value));
    }

    void setBool(String key, boolean value) {
        set(key, value ? "1" : "0");
    }

    void remove(String key) {
        if (map.remove(key) != null) {
            save();
        }
    }
}
