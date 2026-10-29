package com.xlox.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Just enough ELF reading to answer the two questions a loader actually needs:
 *
 *   is this a 64-bit ARM library?   (a 32-bit .so can never load into an
 *                                    arm64-v8a process)
 *   what else does it need?         (DT_NEEDED — if one of those is missing,
 *                                    System.load throws "library not found"
 *                                    and the mod does nothing)
 *
 * Plain Java, no Android: see loader/app/src/test/ElfTest.java.
 */
public final class Elf {

    private static final int PT_LOAD = 1;
    private static final int PT_DYNAMIC = 2;
    private static final long DT_NULL = 0;
    private static final long DT_NEEDED = 1;
    private static final long DT_STRTAB = 5;
    private static final long DT_STRSZ = 10;

    public final boolean elf;
    public final boolean is64Bit;
    public final int machine;          // 0xB7 = AArch64, 0x28 = ARM
    public final List<String> needed;  // as written in .dynstr

    private Elf(boolean elf, boolean is64Bit, int machine, List<String> needed) {
        this.elf = elf;
        this.is64Bit = is64Bit;
        this.machine = machine;
        this.needed = needed;
    }

    public String arch() {
        if (!elf) return "not an ELF";
        if (machine == 0xB7) return is64Bit ? "arm64-v8a" : "aarch64 (32-bit header?)";
        if (machine == 0x28) return "armeabi-v7a";
        if (machine == 0x3E) return "x86_64";
        if (machine == 0x03) return "x86";
        return "unknown (" + machine + ")";
    }

    public static Elf read(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return read(raf);
        } catch (IOException e) {
            return new Elf(false, false, -1, new ArrayList<>());
        }
    }

    private static Elf read(RandomAccessFile r) throws IOException {
        if (r.length() < 64) return new Elf(false, false, -1, new ArrayList<>());
        byte[] ident = new byte[16];
        r.readFully(ident);
        if (ident[0] != 0x7F || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') {
            return new Elf(false, false, -1, new ArrayList<>());
        }
        boolean is64 = ident[4] == 2;
        boolean little = ident[5] == 1;
        if (!little) return new Elf(true, is64, -1, new ArrayList<>());   // no need for BE support

        r.seek(18);
        int machine = read16(r);

        r.seek(is64 ? 0x20 : 0x1C);                      // e_phoff sits here
        long phoff = is64 ? read64(r) : read32(r);
        r.seek(is64 ? 0x36 : 0x2A);                      // e_phentsize / e_phnum
        int phentsize = read16(r);
        int phnum = read16(r);

        long dynOff = -1;
        long dynSize = 0;
        List<long[]> loads = new ArrayList<>();          // {vaddr, offset, filesz}

        for (int i = 0; i < phnum; i++) {
            r.seek(phoff + (long) i * phentsize);
            long type = read32(r);
            if (is64) {
                r.skipBytes(4);                          // flags
                long offset = read64(r);
                long vaddr = read64(r);
                read64(r);                               // paddr
                long filesz = read64(r);
                if (type == PT_LOAD) loads.add(new long[]{vaddr, offset, filesz});
                if (type == PT_DYNAMIC) {
                    dynOff = offset;
                    dynSize = filesz;
                }
            } else {
                long offset = read32(r);
                long vaddr = read32(r);
                read32(r);                               // paddr
                long filesz = read32(r);
                if (type == PT_LOAD) loads.add(new long[]{vaddr, offset, filesz});
                if (type == PT_DYNAMIC) {
                    dynOff = offset;
                    dynSize = filesz;
                }
            }
        }

        List<String> needed = new ArrayList<>();
        if (dynOff < 0 || dynSize <= 0) return new Elf(true, is64, machine, needed);

        int entrySize = is64 ? 16 : 8;
        int count = (int) Math.min(dynSize / entrySize, 4096);
        long strTabAddr = -1;
        long strTabSize = 0;
        long[] neededOffsets = new long[count];

        r.seek(dynOff);
        for (int i = 0; i < count; i++) {
            long tag = is64 ? read64(r) : read32(r);
            long val = is64 ? read64(r) : read32(r);
            if (tag == DT_NULL) break;
            if (tag == DT_NEEDED) neededOffsets[i] = val + 1;   // 0 means "empty"
            if (tag == DT_STRTAB) strTabAddr = val;
            if (tag == DT_STRSZ) strTabSize = val;
        }

        long strTabFile = -1;
        for (long[] seg : loads) {
            if (strTabAddr >= seg[0] && strTabAddr < seg[0] + seg[2]) {
                strTabFile = seg[1] + (strTabAddr - seg[0]);
                break;
            }
        }
        if (strTabFile < 0) return new Elf(true, is64, machine, needed);
        if (strTabSize <= 0) strTabSize = 4096;

        byte[] strTab = new byte[(int) Math.min(strTabSize, 1 << 20)];
        r.seek(strTabFile);
        r.readFully(strTab);

        for (long off : neededOffsets) {
            if (off <= 0) continue;
            int start = (int) (off - 1);
            if (start < 0 || start >= strTab.length) continue;
            int end = start;
            while (end < strTab.length && strTab[end] != 0) end++;
            String name = new String(strTab, start, end - start);
            if (!name.isEmpty() && !needed.contains(name)) needed.add(name);
        }

        return new Elf(true, is64, machine, needed);
    }

    private static int read16(RandomAccessFile r) throws IOException {
        int a = r.read();
        int b = r.read();
        return (b << 8) | (a & 0xFF);
    }

    private static long read32(RandomAccessFile r) throws IOException {
        long a = r.read() & 0xFFL;
        long b = r.read() & 0xFFL;
        long c = r.read() & 0xFFL;
        long d = r.read() & 0xFFL;
        return (d << 24) | (c << 16) | (b << 8) | a;
    }

    private static long read64(RandomAccessFile r) throws IOException {
        long low = read32(r);
        long high = read32(r);
        return (high << 32) | low;
    }
}
