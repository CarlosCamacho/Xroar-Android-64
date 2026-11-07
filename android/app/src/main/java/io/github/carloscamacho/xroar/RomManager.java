package io.github.carloscamacho.xroar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.CRC32;

/* Looks after the system ROM files in roms/: which ones are there, what they
 * are (by CRC-32, using XRoar's own table), and replacing or removing them.
 *
 * XRoar finds ROMs by name: Color BASIC as bas13.rom (or bas12, bas11,
 * bas10), Extended BASIC as extbas11.rom (or extbas10), the disk controller
 * as disk11.rom (or disk10), SDC-DOS as sdcdos.rom.  Installing a ROM names
 * it for what it is, and takes out any other version of the same ROM first so
 * the one just installed is the one XRoar picks. */
final class RomManager {
    /* The ROM slots the machine has. */
    enum Slot {
        BASIC("Color BASIC", "BASIC", new String[] { "bas13", "bas12", "bas11", "bas10" }, "bas13", true),
        EXT("Extended BASIC", "EXT", new String[] { "extbas11", "extbas10" }, "extbas11", true),
        DOS("Disk controller (DOS)", "DOS", new String[] { "disk11", "disk10" }, "disk11", true),
        SDC("SDC-DOS (CoCoSDC)", "SDC", new String[] { "sdcdos", "sdc-dos", "sdc_dos" }, "sdcdos", false),
        EMES("emesDOS09 cartridge ROM (16K)", "EMES", new String[] { "emesdos09" }, "emesdos09", false);

        final String title;
        final String kind;           // matches RomCatalog.Entry.kind (SDC has no catalog entry)
        final String[] names;        // every file name XRoar accepts, best first
        final String defaultName;
        final boolean required;

        Slot(String title, String kind, String[] names, String defaultName, boolean required) {
            this.title = title;
            this.kind = kind;
            this.names = names;
            this.defaultName = defaultName;
            this.required = required;
        }
    }

    /* What a file turned out to be. */
    static final class Identity {
        final long crc;
        final int size;
        final RomCatalog.Entry entry;   // null if XRoar's table doesn't know it

        Identity(long crc, int size, RomCatalog.Entry entry) {
            this.crc = crc;
            this.size = size;
            this.entry = entry;
        }

        String describe() {
            if (entry != null) {
                return entry.desc;
            }
            return String.format("unknown ROM (CRC %08x, %d bytes)", crc, size);
        }
    }

    private static final String[] EXTS = { ".rom", ".ROM", "" };

    private final File home;

    RomManager(File home) {
        this.home = home;
    }

    private File[] dirs() {
        return new File[] { new File(home, "roms"), home };
    }

    static Identity identify(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data, 0, data.length);
        return new Identity(c.getValue(), data.length, RomCatalog.byCrc(c.getValue()));
    }

    /* Every file in the slot's names, in search order. */
    File[] files(Slot slot) {
        java.util.List<File> out = new java.util.ArrayList<>();
        for (String n : slot.names) {
            for (File d : dirs()) {
                for (String e : EXTS) {
                    File f = new File(d, n + e);
                    if (f.isFile()) {
                        out.add(f);
                    }
                }
            }
        }
        return out.toArray(new File[0]);
    }

    /* The file XRoar will use for this slot, or null. */
    File active(Slot slot) {
        File[] f = files(slot);
        return f.length > 0 ? f[0] : null;
    }

    /* "Colour BASIC 1.3 (Tandy)  -  bas13.rom", or null if there is none. */
    String label(Slot slot) {
        File f = active(slot);
        if (f == null) {
            return null;
        }
        String what = null;
        try {
            Identity id = identify(readAll(f));
            what = id.entry != null ? id.entry.desc : String.format("unrecognised (CRC %08x)", id.crc);
        } catch (Exception e) {
            what = "unreadable";
        }
        return (slot == Slot.SDC || slot == Slot.EMES) && what.startsWith("unrecognised") ? f.getName() : what + "  —  " + f.getName();
    }

    /* Delete every file for this slot.  Returns how many were removed. */
    int remove(Slot slot) {
        int n = 0;
        for (File f : files(slot)) {
            if (f.delete()) {
                n++;
            }
        }
        return n;
    }

    /* Install data as this slot's ROM, named by what it is (or the slot's
     * default name if XRoar's table doesn't know it).  Other versions for the
     * slot are removed first.  Returns the file name installed. */
    String install(Slot slot, byte[] data, Identity id) throws Exception {
        String name = slot.defaultName;
        if (id != null && id.entry != null && slot.kind.equals(id.entry.kind)) {
            name = id.entry.name;
        }
        File roms = new File(home, "roms");
        roms.mkdirs();
        remove(slot);
        File out = new File(roms, name + ".rom");
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
        return out.getName();
    }

    static byte[] readAll(File f) throws Exception {
        if (f.length() > 1024 * 1024) {
            throw new Exception("too big");
        }
        byte[] buf = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) {
                    break;
                }
                got += n;
            }
        }
        return buf;
    }
}
