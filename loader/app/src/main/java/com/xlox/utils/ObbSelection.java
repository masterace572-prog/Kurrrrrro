package com.xlox.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The file logic of the OBB copy, with no Android dependencies so it can be
 * exercised on a plain JVM (see loader/app/src/test/ObbSelectionTest).
 *
 * Keeping this separate is deliberate: "it says ready but the game has no data"
 * bugs live in exactly these three decisions —
 *   which files count, which still need copying, and whether the destination
 *   really matches the source.
 */
public final class ObbSelection {

    private ObbSelection() {}

    /** `main.<version>.<pkg>.obb` or `patch.<version>.<pkg>.obb`. */
    public static Pattern obbPattern(String pkg) {
        return Pattern.compile("^(main|patch)\\.[0-9]+\\." + Pattern.quote(pkg) + "\\.obb$");
    }

    public static Pattern mainPattern(String pkg) {
        return Pattern.compile("^main\\.[0-9]+\\." + Pattern.quote(pkg) + "\\.obb$");
    }

    /** Every OBB in `dir` that belongs to `pkg`, smallest first. */
    public static List<File> list(File dir, String pkg) {
        List<File> out = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return out;
        Pattern p = obbPattern(pkg);
        for (File f : files) {
            if (f.isFile() && p.matcher(f.getName()).matches() && f.length() > 0) out.add(f);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.length(), b.length()));
        return out;
    }

    /** The ones whose sandbox copy is missing or the wrong size. */
    public static List<File> pending(List<File> source, File destDir, String pkg) {
        List<File> out = new ArrayList<>();
        for (File src : source) {
            File copy = new File(destDir, src.getName());
            if (!copy.isFile() || copy.length() != src.length()) out.add(src);
        }
        return out;
    }

    /** True when every source OBB is present in the destination at the same size. */
    public static boolean matches(List<File> source, File destDir, String pkg) {
        if (source == null || source.isEmpty()) return false;
        return pending(source, destDir, pkg).isEmpty();
    }

    /** Fallback when the source cannot be listed (no storage access). */
    public static boolean hasMainObb(File destDir, String pkg) {
        File[] files = destDir == null ? null : destDir.listFiles();
        if (files == null) return false;
        Pattern main = mainPattern(pkg);
        for (File f : files) {
            if (f.isFile() && main.matcher(f.getName()).matches() && f.length() > 0) return true;
        }
        return false;
    }
}
