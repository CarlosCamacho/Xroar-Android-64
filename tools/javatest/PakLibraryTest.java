package io.github.carloscamacho.xroar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/* Desktop test of PakLibrary.  Usage: java -cp <classes> ...PakLibraryTest <rom-dir> */
public class PakLibraryTest {
    static int failures;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] a) throws Exception {
        byte[] bas13 = Files.readAllBytes(new File(a[0], "bas13.rom").toPath());
        File home = Files.createTempDirectory("pakstest").toFile();
        PakLibrary lib = new PakLibrary(home);

        System.out.println("== empty");
        check(lib.scan().isEmpty() && lib.dir().isDirectory(), "no paks, and the paks/ folder is created");

        System.out.println("== add and scan");
        byte[] blob = new byte[8192];
        blob[0] = 0x55;
        File f1 = lib.add(new ByteArrayInputStream(blob), "super_pitfall.ROM");
        File f2 = lib.add(new ByteArrayInputStream(new byte[2048]), "Zaxxon.ccc");
        File f3 = lib.add(new ByteArrayInputStream(new byte[4096]), "alpha beta.rom");
        lib.add(new ByteArrayInputStream(new byte[100]), "notes.txt");   // not a pak: ignored
        List<PakLibrary.Pak> all = lib.scan();
        check(all.size() == 3, "three paks (the .txt is ignored): " + all.size());
        check(all.get(0).title.equals("alpha beta") && all.get(1).title.equals("super pitfall") && all.get(2).title.equals("Zaxxon"),
                "sorted by readable title: " + all.get(0).title + ", " + all.get(1).title + ", " + all.get(2).title);
        check(all.get(1).detail.startsWith("8 KB") && all.get(2).detail.startsWith("2 KB"), "sizes shown: " + all.get(1).detail);
        check(!all.get(0).known && !all.get(0).needsCoco3 && !all.get(0).needsGmc, "an unknown file is just a pak");

        System.out.println("== same name twice");
        File f4 = lib.add(new ByteArrayInputStream(blob), "super_pitfall.ROM");
        check(!f4.equals(f1) && f4.getName().equals("super_pitfall (2).ROM"), "second copy is kept under a new name: " + f4.getName());
        File f5 = lib.add(new ByteArrayInputStream(blob), "../../evil:name.rom");
        check(f5.getParentFile().equals(lib.dir()), "path characters in a name stay inside paks/: " + f5.getName());

        System.out.println("== system ROM in paks/");
        lib.add(new ByteArrayInputStream(bas13), "mybasic.rom");
        PakLibrary.Pak sys = null;
        for (PakLibrary.Pak p : lib.scan()) if (p.file.getName().equals("mybasic.rom")) sys = p;
        check(sys != null && sys.detail.contains("system ROM") && sys.detail.contains("Colour BASIC 1.3"), "recognised as a system ROM, not a Pak: " + (sys == null ? null : sys.detail));

        System.out.println("== search");
        List<PakLibrary.Pak> now = lib.scan();
        check(PakLibrary.filter(now, "pit").size() == 2, "'pit' finds both pitfall copies");
        check(PakLibrary.filter(now, "super copy").size() == 0, "every word must match");
        check(PakLibrary.filter(now, "ZAX").size() == 1, "case does not matter");
        check(PakLibrary.filter(now, "  ").size() == now.size(), "a blank search shows everything");

        System.out.println("== delete");
        PakLibrary.Pak z = PakLibrary.filter(now, "zaxxon").get(0);
        check(lib.delete(z) && !f2.exists() && lib.scan().size() == now.size() - 1, "deleted from the folder and the list");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
