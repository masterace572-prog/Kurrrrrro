package com.xlox.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Minimal ELF reader used by the CI pre-build check to validate that a
 * bundled arm64 shared library is a real ELF before the APK is assembled.
 */
public final class Elf {

    public static final int ELFCLASSNONE = 0;
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;

    public static final int EM_NONE = 0;
    public static final int EM_AARCH64 = 183;

    private final byte[] ident = new byte[16];
    private final int machine;

    private Elf(File file, boolean readMachine) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            readFully(in, ident);
            if (readMachine) {
                // e_type(2 bytes) at offset 16, e_machine(2 bytes) at offset 18
                in.skip(2);
                byte[] machineBytes = new byte[2];
                readFully(in, machineBytes);
                machine = (machineBytes[0] & 0xff) | ((machineBytes[1] & 0xff) << 8);
            } else {
                machine = 0;
            }
        }
    }

    private static void readFully(FileInputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int read = in.read(buf, total, buf.length - total);
            if (read < 0) throw new IOException("unexpected end of stream");
            total += read;
        }
    }

    public static Elf open(File file) throws IOException {
        return new Elf(file, false);
    }

    public static Elf openWithMachine(File file) throws IOException {
        return new Elf(file, true);
    }

    public boolean isElf() {
        return ident[0] == 0x7f
                && ident[1] == 'E'
                && ident[2] == 'L'
                && ident[3] == 'F';
    }

    public int getClass_() {
        return ident[4] & 0xff;
    }

    public boolean is64Bit() {
        return getClass_() == ELFCLASS64;
    }

    public int getMachine() {
        return machine;
    }

    public boolean isAarch64() {
        return machine == EM_AARCH64;
    }
}
