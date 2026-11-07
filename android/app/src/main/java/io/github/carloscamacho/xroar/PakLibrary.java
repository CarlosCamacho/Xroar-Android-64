package io.github.carloscamacho.xroar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* The paks/ folder: cartridge images (.rom, .ccc) the user keeps there.
 * Each is identified by CRC-32 against XRoar's table, so a known Program Pak
 * shows its real title ("Downland v1.1 (1983) (Tandy) (26-3046)") and not just
 * a file name. */
final class PakLibrary {
    static final class Pak {
        final File file;
        final String title;          // catalog title, or the file name made readable
        final String detail;         // "8 KB  -  downland.rom" plus a note if it needs something else
        final boolean known;         // found in XRoar's table
        final boolean needsCoco3;    // a CoCo 3 title: won't run on this CoCo 1/2 build
        final boolean needsGmc;      // runs in a Games Master Cartridge

        Pak(File file, String title, String detail, boolean known, boolean needsCoco3, boolean needsGmc) {
            this.file = file;
            this.title = title;
            this.detail = detail;
            this.known = known;
            this.needsCoco3 = needsCoco3;
            this.needsGmc = needsGmc;
        }
    }

    private final File dir;
    // path + size + modified time -> identity, so a rescan doesn't re-read every file
    private final Map<String, RomManager.Identity> cache = new HashMap<>();

    PakLibrary(File home) {
        dir = new File(home, "paks");
    }

    File dir() {
        return dir;
    }

    static boolean isPakName(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".rom") || n.endsWith(".ccc");
    }

    /* All paks, sorted by title. */
    List<Pak> scan() {
        dir.mkdirs();
        List<Pak> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (!f.isFile() || !isPakName(f.getName())) {
                continue;
            }
            out.add(describe(f));
        }
        Collections.sort(out, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return out;
    }

    private Pak describe(File f) {
        String key = f.getPath() + "|" + f.length() + "|" + f.lastModified();
        RomManager.Identity id = cache.get(key);
        if (id == null) {
            try {
                id = RomManager.identify(RomManager.readAll(f));
            } catch (Exception e) {
                id = new RomManager.Identity(0, (int) f.length(), null);
            }
            cache.put(key, id);
        }
        String size = f.length() >= 1024 ? (f.length() / 1024) + " KB" : f.length() + " bytes";
        RomCatalog.Entry e = id.entry;
        boolean known = e != null && (e.kind.equals("PAK") || e.kind.equals("GMC"));
        boolean gmc = known && e.kind.equals("GMC");
        boolean coco3 = known && e.coco3;
        String title = known ? e.desc : readable(f.getName());
        String detail = size + "  —  " + f.getName();
        if (coco3) {
            detail += "  —  needs a CoCo 3 (won't run in this app)";
        } else if (gmc) {
            detail += "  —  Games Master Cartridge";
        } else if (e != null && !known) {
            detail += "  —  this is a system ROM (" + e.desc + "), not a Pak";
        }
        return new Pak(f, title, detail, known, coco3, gmc);
    }

    /* "super_pitfall.ROM" -> "super pitfall" */
    static String readable(String fileName) {
        String n = fileName;
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        return n.replace('_', ' ').trim();
    }

    /* Paks whose title or file name contains every word of the query. */
    static List<Pak> filter(List<Pak> all, String query) {
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        String[] words = query.toLowerCase(Locale.US).trim().split("\\s+");
        List<Pak> out = new ArrayList<>();
        for (Pak p : all) {
            String hay = (p.title + " " + p.file.getName()).toLowerCase(Locale.US);
            boolean ok = true;
            for (String w : words) {
                if (!hay.contains(w)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(p);
            }
        }
        return out;
    }

    /* Copy a stream into paks/ under name (made safe, and unique if taken). */
    File add(InputStream in, String name) throws Exception {
        dir.mkdirs();
        String safe = name.replaceAll("[/\\\\:]", "_");
        File out = new File(dir, safe);
        int n = 1;
        while (out.exists()) {
            int dot = safe.lastIndexOf('.');
            String base = dot > 0 ? safe.substring(0, dot) : safe;
            String ext = dot > 0 ? safe.substring(dot) : "";
            out = new File(dir, base + " (" + (++n) + ")" + ext);
        }
        try (FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int got;
            while ((got = in.read(buf)) > 0) {
                os.write(buf, 0, got);
            }
        }
        return out;
    }

    boolean delete(Pak p) {
        return p.file.delete();
    }
}
