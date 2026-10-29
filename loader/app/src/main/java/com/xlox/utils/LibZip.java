package com.xlox.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Choosing which `libbgmi.so` out of an archive is the one to keep.
 *
 * Archives built by Android tooling carry one copy per ABI
 * (`lib/arm64-v8a/libbgmi.so`, `lib/armeabi-v7a/libbgmi.so`, …) and a plain
 * recursive search returns whichever the filesystem lists first. Promoting the
 * 32-bit copy is a silent failure: it downloads fine, then cannot load into the
 * game's arm64 process.
 *
 * Plain Java, no Android: see loader/app/src/test/ElfTest.java.
 */
public final class LibZip {

    private LibZip() {}

    /** Every file with this name anywhere under {@code dir}. */
    public static List<File> findAll(File dir, String name) {
        List<File> out = new ArrayList<>();
        if (dir == null || name == null) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.getName().equals(name)) out.add(f);
        }
        for (File f : files) {
            if (f.isDirectory()) out.addAll(findAll(f, name));
        }
        return out;
    }

    /**
     * Picks the copy that can actually load: a path naming arm64/aarch64 first,
     * then a file whose header says 64-bit AArch64, then one already at the root
     * of the extraction directory, then whatever is left.
     */
    public static File pickArm64(List<File> hits, File root) {
        File best = null;
        int bestScore = -1;
        for (File hit : hits) {
            int score = score(hit, root);
            if (score > bestScore) {
                bestScore = score;
                best = hit;
            }
        }
        return best;
    }

    /** Higher is better; 0 means "probably the wrong ABI". */
    public static int score(File hit, File root) {
        String path = hit.getAbsolutePath().toLowerCase(Locale.US);
        if (path.contains("arm64") || path.contains("aarch64")) return 3;

        Elf elf = Elf.read(hit);
        if (elf.elf && elf.is64Bit && elf.machine == 0xB7) return 2;

        if (hit.getParentFile() != null && root != null
                && hit.getParentFile().getAbsolutePath().equals(root.getAbsolutePath())) {
            return 1;
        }
        return 0;
    }
}
