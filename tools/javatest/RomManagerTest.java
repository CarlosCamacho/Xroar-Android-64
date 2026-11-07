package io.github.carloscamacho.xroar;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

/* Desktop test of RomManager and the ROM catalog (no Android classes needed).
 * Usage: java -cp <classes> io.github.carloscamacho.xroar.RomManagerTest <rom-dir>
 * where <rom-dir> has the real bas13.rom, extbas11.rom and disk11.rom. */
public class RomManagerTest {
    static int failures;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }

    static byte[] read(File d, String n) throws Exception {
        return Files.readAllBytes(new File(d, n).toPath());
    }

    static void write(File f, byte[] b) throws Exception {
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(b);
        }
    }

    static String[] listRoms(File home) {
        String[] l = new File(home, "roms").list();
        java.util.Arrays.sort(l == null ? new String[0] : l);
        return l == null ? new String[0] : l;
    }

    public static void main(String[] a) throws Exception {
        File src = new File(a[0]);
        byte[] bas13 = read(src, "bas13.rom"), ext11 = read(src, "extbas11.rom"), disk11 = read(src, "disk11.rom");

        System.out.println("== identify");
        RomManager.Identity i1 = RomManager.identify(bas13);
        check(i1.entry != null && i1.entry.kind.equals("BASIC") && i1.entry.name.equals("bas13")
                && i1.entry.desc.equals("Colour BASIC 1.3 (Tandy)"), "bas13.rom is Colour BASIC 1.3: " + i1.describe());
        RomManager.Identity i2 = RomManager.identify(ext11);
        check(i2.entry != null && i2.entry.kind.equals("EXT") && i2.entry.name.equals("extbas11"), "extbas11.rom is Extended 1.1: " + i2.describe());
        RomManager.Identity i3 = RomManager.identify(disk11);
        check(i3.entry != null && i3.entry.kind.equals("DOS") && i3.entry.name.equals("disk11"), "disk11.rom is Disk BASIC 1.1: " + i3.describe());
        byte[] junk = new byte[8192];
        check(RomManager.identify(junk).entry == null, "a blank 8K file is not recognised");

        File home = Files.createTempDirectory("romtest").toFile();
        RomManager rm = new RomManager(home);

        System.out.println("== empty app folder");
        check(rm.active(RomManager.Slot.BASIC) == null && rm.label(RomManager.Slot.BASIC) == null, "no Color BASIC found");

        System.out.println("== install");
        check(rm.install(RomManager.Slot.BASIC, bas13, i1).equals("bas13.rom"), "Color BASIC installed as bas13.rom");
        check(rm.install(RomManager.Slot.EXT, ext11, i2).equals("extbas11.rom"), "Extended BASIC installed as extbas11.rom");
        check(rm.install(RomManager.Slot.DOS, disk11, i3).equals("disk11.rom"), "DOS installed as disk11.rom");
        check(String.join(",", listRoms(home)).equals("bas13.rom,disk11.rom,extbas11.rom"), "roms/ has the three files: " + String.join(",", listRoms(home)));
        check(rm.label(RomManager.Slot.BASIC).startsWith("Colour BASIC 1.3 (Tandy)"), "label: " + rm.label(RomManager.Slot.BASIC));

        System.out.println("== replace takes out other versions of the same ROM");
        write(new File(home, "roms/bas11.rom"), junk);      // a stale older copy
        write(new File(home, "bas12.ROM"), junk);           // another, in the app folder, upper-case extension
        check(rm.files(RomManager.Slot.BASIC).length == 3, "three BASIC files present before");
        rm.install(RomManager.Slot.BASIC, bas13, i1);
        check(rm.files(RomManager.Slot.BASIC).length == 1 && rm.active(RomManager.Slot.BASIC).getName().equals("bas13.rom"),
                "only bas13.rom is left after a replace");
        check(rm.files(RomManager.Slot.EXT).length == 1, "other slots are untouched");

        System.out.println("== a ROM XRoar does not know goes in under the slot's default name");
        check(rm.install(RomManager.Slot.SDC, junk, RomManager.identify(junk)).equals("sdcdos.rom"), "unknown file installed as sdcdos.rom");
        check(rm.install(RomManager.Slot.EXT, junk, RomManager.identify(junk)).equals("extbas11.rom"), "unknown file in the Extended slot is extbas11.rom");

        System.out.println("== a known ROM of the wrong kind keeps the slot's name (the app asks first)");
        check(rm.install(RomManager.Slot.BASIC, ext11, i2).equals("bas13.rom"), "Extended ROM forced into Color BASIC slot is named bas13.rom");
        rm.install(RomManager.Slot.BASIC, bas13, i1);

        System.out.println("== remove");
        check(rm.remove(RomManager.Slot.DOS) == 1 && rm.active(RomManager.Slot.DOS) == null, "DOS removed");
        check(rm.remove(RomManager.Slot.DOS) == 0, "removing again removes nothing");
        check(rm.active(RomManager.Slot.BASIC) != null, "Color BASIC still there");

        System.out.println("== catalog");
        RomCatalog.Entry dl = RomCatalog.byCrc(0xa923f5a2L);
        check(dl != null && dl.kind.equals("PAK") && dl.desc.startsWith("Downland"), "Downland pak known: " + (dl == null ? null : dl.desc));
        RomCatalog.Entry rc = RomCatalog.byCrc(0xdd94dd06L);
        check(rc != null && rc.kind.equals("GMC"), "RoboCop needs the Games Master Cartridge");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
