package io.github.carloscamacho.xroar;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

/* XRoar on SDL2.  libmain.so holds XRoar plus a small SDL_main() wrapper
 * (android_main.c) that sets HOME to this app's external files directory.
 *
 * If the activity was started by "Open with XRoar", the file is copied into
 * opened/ (content:// URIs have no usable path for native code) and
 * passed to XRoar as "-run <path>", which attaches it and tries to autorun.
 */
public class MainActivity extends SDLActivity {
    private static final String TAG = "XRoar";

    /* The app's folder for ROMs, xroar.conf, the SD card and the log:
     * Android/media/<package>/.  File managers can write there on Android 11+,
     * unlike Android/data/<package>/.  Falls back to the files directory. */
    private File homeDir() {
        File[] media = getExternalMediaDirs();
        if (media != null) {
            for (File d : media) {
                if (d != null && (d.isDirectory() || d.mkdirs())) {
                    return d;
                }
            }
        }
        File d = getExternalFilesDir(null);
        return d != null ? d : getFilesDir();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before super: SDLActivity starts the native thread later, and
        // android_main.c reads XROAR_HOME when it starts.
        try {
            Os.setenv("XROAR_HOME", homeDir().getAbsolutePath(), true);
        } catch (Exception e) {
            Log.e(TAG, "can't set XROAR_HOME", e);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return new String[0];
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return new String[0];
        }
        try {
            String path = copyToFiles(uri);
            if (path != null) {
                return new String[] { "-run", path };
            }
        } catch (Exception e) {
            Log.e(TAG, "can't open " + uri, e);
        }
        return new String[0];
    }

    private String copyToFiles(Uri uri) throws Exception {
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        String name = null;
        try (Cursor c = getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
            }
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "opened.bin";
        }
        name = name.replaceAll("[/\\\\:]", "_");
        File dir = new File(homeDir(), "opened");
        dir.mkdirs();
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        return out.getAbsolutePath();
    }

    /* Implemented in android_main.c: queue a file for the running emulator. */
    private static native void nativeOpenFile(String path);

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // XRoar is already running: copy the file and let the emulator
        // thread load and autorun it.
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) {
            return;
        }
        setIntent(intent);
        try {
            String path = copyToFiles(intent.getData());
            if (path != null) {
                nativeOpenFile(path);
            }
        } catch (Exception e) {
            Log.e(TAG, "can't open " + intent.getData(), e);
        }
    }
}
