package io.github.carloscamacho.xroar;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

/* XRoar on SDL2.  libmain.so holds XRoar plus a small SDL_main() wrapper
 * (android_main.c) that sets HOME to this app's external files directory.
 *
 * If the activity was started by "Open with XRoar", the file is copied into
 * files/opened/ (content:// URIs have no usable path for native code) and
 * passed to XRoar as "-run <path>", which attaches it and tries to autorun.
 */
public class MainActivity extends SDLActivity {
    private static final String TAG = "XRoar";

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
        File base = getExternalFilesDir(null);
        if (base == null) {
            base = getFilesDir();
        }
        File dir = new File(base, "opened");
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
