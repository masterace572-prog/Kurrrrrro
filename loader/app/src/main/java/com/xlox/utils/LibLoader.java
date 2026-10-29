package com.xlox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads the downloaded mod library **inside the game's process**.
 *
 * That process is a sandbox process, and in it the sandbox SDK rewrites the
 * usual app paths to the guest's virtual storage — so `new File(getFilesDir(),
 * "loader")` can point at an empty directory that has nothing to do with where
 * the file was written. The absolute path is therefore resolved from the real
 * package directory first, and only then from the context.
 *
 * Failures are written to `loader/load.log`, because the only other place they
 * would show up is logcat on a device that is usually not plugged in.
 */
@Obfuscate
public final class LibLoader {

    private static final String TAG = "LibLoader";
    private static final String LIB_NAME = "libbgmi.so";
    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_LIB_PATH = "lib_path";

    /** Libraries the platform always provides; never preload these ourselves. */
    private static final List<String> SYSTEM_LIBS = Arrays.asList(
            "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libstdc++.so",
            "libandroid.so", "libjnigraphics.so", "libEGL.so", "libGLESv2.so",
            "libOpenSLES.so", "libOpenMAXAL.so", "libmediandk.so", "libnativewindow.so",
            "libvulkan.so", "libc++_shared.so", "libc++_static.so", "libdvm.so", "libart.so");

    private LibLoader() {}

    /** Every place the library could be, most trustworthy first. */
    public static List<File> candidates(Context ctx) {
        List<File> out = new ArrayList<>();
        String pkg = ctx.getPackageName();

        // 1. where the download actually put it (written by the main process)
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString(PREF_LIB_PATH, null);
            if (saved != null) out.add(new File(saved));
        } catch (Throwable ignored) {
        }

        // 2. the real package directory, spelled out: the sandbox redirects the
        //    context paths, but not a literal one
        out.add(new File("/data/user/0/" + pkg + "/files/loader/" + LIB_NAME));
        out.add(new File("/data/data/" + pkg + "/files/loader/" + LIB_NAME));

        // 3. whatever the context says (correct when nothing is redirected)
        try {
            out.add(new File(ctx.getFilesDir(), "loader/" + LIB_NAME));
        } catch (Throwable ignored) {
        }
        try {
            out.add(new File(ctx.getNoBackupFilesDir(), "loader/" + LIB_NAME));
        } catch (Throwable ignored) {
        }
        try {
            out.add(new File(ctx.getCodeCacheDir(), "loader/" + LIB_NAME));
        } catch (Throwable ignored) {
        }

        return out;
    }

    /** The first candidate that exists and is a 64-bit ARM library, else null. */
    public static File resolve(Context ctx) {
        for (File f : candidates(ctx)) {
            if (f.isFile() && f.length() > 0) {
                Elf elf = Elf.read(f);
                if (elf.elf && elf.is64Bit && elf.machine == 0xB7) return f;
            }
        }
        return null;
    }

    /**
     * Loads the library into the current process. Called from the sandbox's
     * process callback, i.e. in the game's own process, which is where a mod
     * library has to live.
     *
     * @return "ok: <path>" or "fail: <reason>"
     */
    public static String load(Context ctx) {
        File lib = resolve(ctx);
        if (lib == null) {
            String why = "fail: " + LIB_NAME + " not found or not arm64 — looked in "
                    + candidates(ctx);
            write(ctx, why);
            Log.e(TAG, why);
            return why;
        }

        // Dependencies first: a nested zip leaves them next to the library, and
        // System.load of the main one fails with "library not found" otherwise.
        preloadDependencies(lib);

        String result;
        try {
            System.load(lib.getAbsolutePath());
            result = "ok: " + lib.getAbsolutePath();
        } catch (Throwable first) {
            String msg = first.getMessage() == null ? first.toString() : first.getMessage();

            // Newer Android only dlopen()s libraries that cannot be modified, and
            // the download leaves them world-writable. Make them read-only and try
            // once more; Downtwo removes the old file before an update, so a
            // read-only copy never blocks the next download.
            if (msg != null && (msg.contains("writable") || msg.contains("read-only")
                    || msg.contains("readonly"))) {
                makeReadOnly(lib);
                try {
                    System.load(lib.getAbsolutePath());
                    result = "ok: " + lib.getAbsolutePath() + " (made read-only first)";
                } catch (Throwable second) {
                    String msg2 = second.getMessage() == null ? second.toString()
                            : second.getMessage();
                    result = "fail: System.load(" + lib.getAbsolutePath() + "): " + msg2;
                    Log.e(TAG, result, second);
                }
            } else {
                result = "fail: System.load(" + lib.getAbsolutePath() + "): " + msg;
                Log.e(TAG, result, first);
            }
        }
        if (result.startsWith("ok:")) Log.i(TAG, "loaded " + result);
        write(ctx, result);
        return result;
    }

    /** 0444 on the library and every sibling it depends on. */
    private static void makeReadOnly(File lib) {
        File dir = lib.getParentFile();
        if (dir != null) {
            String[] siblings = dir.list();
            if (siblings != null) {
                for (String name : siblings) {
                    if (name.endsWith(".so")) {
                        File f = new File(dir, name);
                        f.setWritable(false, false);
                        f.setWritable(false, true);
                        f.setExecutable(true, false);
                    }
                }
            }
        }
        lib.setWritable(false, false);
        lib.setWritable(false, true);
        lib.setExecutable(true, false);
        Log.i(TAG, "made " + lib.getName() + " read-only and executable");
    }

    /** Loads every same-directory library it lists as needed, ignoring failures. */
    private static void preloadDependencies(File lib) {
        Elf elf = Elf.read(lib);
        if (!elf.elf || elf.needed.isEmpty()) return;

        File dir = lib.getParentFile();
        List<String> pending = new ArrayList<>(elf.needed);
        pending.removeAll(SYSTEM_LIBS);

        // two passes so a dependency of a dependency also gets its turn
        for (int pass = 0; pass < 2 && !pending.isEmpty(); pass++) {
            for (int i = 0; i < pending.size(); i++) {
                String name = pending.get(i);
                File dep = dir == null ? null : new File(dir, name);
                if (dep == null || !dep.isFile()) continue;
                try {
                    System.load(dep.getAbsolutePath());
                    Log.i(TAG, "preloaded " + name);
                    pending.remove(i--);
                } catch (Throwable t) {
                    Log.w(TAG, "could not preload " + name + ": " + t.getMessage());
                }
            }
        }
        if (!pending.isEmpty()) Log.w(TAG, "still missing: " + pending);
    }

    /** One line for the UI: what the downloaded file actually is. */
    public static String describe(File lib) {
        if (lib == null || !lib.isFile()) return "no lib on disk";
        Elf elf = Elf.read(lib);
        if (!elf.elf) return "not an ELF file (" + lib.length() + " bytes)";
        StringBuilder out = new StringBuilder();
        out.append(elf.arch()).append(", ").append(lib.length() / 1024).append(" KB");
        if (!elf.needed.isEmpty()) {
            List<String> extra = new ArrayList<>(elf.needed);
            extra.removeAll(SYSTEM_LIBS);
            if (!extra.isEmpty()) out.append(", needs ").append(extra);
        }
        if (!elf.is64Bit || elf.machine != 0xB7) {
            out.append(" — cannot load into arm64-v8a");
        }
        return out.toString();
    }

    /** The directory the log lives in: the real one when it can be found. */
    private static File logDir(Context ctx) {
        for (File f : candidates(ctx)) {
            File dir = f.getParentFile();
            if (dir != null && dir.isDirectory()) return dir;
        }
        try {
            File dir = new File(ctx.getFilesDir(), "loader");
            if (dir.isDirectory() || dir.mkdirs()) return dir;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void write(Context ctx, String line) {
        File dir = logDir(ctx);
        if (dir == null) return;
        File log = new File(dir, "load.log");
        try (FileOutputStream out = new FileOutputStream(log, true)) {
            String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date());
            out.write((stamp + "  " + line + "\n").getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    /** Last line of the log, for the app to show after a failed launch. */
    public static String lastResult(Context ctx) {
        File dir = logDir(ctx);
        if (dir == null) return "";
        File log = new File(dir, "load.log");
        if (!log.isFile()) return "";
        String last = "";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(log), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) last = line.trim();
            }
        } catch (Throwable ignored) {
        }
        return last;
    }
}
