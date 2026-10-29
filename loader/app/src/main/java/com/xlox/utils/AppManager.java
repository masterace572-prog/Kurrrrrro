package com.xlox.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import net_62v.external.MetaPackageManager;
import net_62v.external.MetaStorageManager;

/**
 * Copies the game's OBB files into the sandbox.
 *
 * This used to be an AsyncTask living inside an AppCompatActivity subclass that
 * was created with {@code new AppManager(ctx)} — an Activity that never went
 * through onCreate. It also swallowed the reason a copy failed. Both are gone:
 * this is a plain object, the work runs on its own thread and every failure
 * carries a message that can be shown to the user.
 *
 * The game keeps its data at
 *   /storage/emulated/0/Android/obb/<pkg>/{main,patch}.<version>.<pkg>.obb
 * and the sandbox reads it from
 *   <sandbox external dir for <pkg>>/Android/obb/<pkg>/
 */
@Obfuscate
public class AppManager {

    private static final String TAG = "AppManager";

    public static final int INSTALL_APP = 1;
    public static final int COPY_OBB = 3;

    /** Where the game keeps its shared-storage data, newest-looking first. */
    private static final String[] SOURCE_ROOTS = {
            "/storage/emulated/0",
            "/sdcard",
            "/storage/self/primary",
    };

    public interface CopyCallback {
        void onCopyCompleted(boolean success);
        default void onProgressUpdate(int progress) {}
        /** Why the copy failed — shown to the user instead of a silent "failed". */
        default void onError(String message) {}
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /* ------------------------------------------------------------ entry points */

    public void copyObbFolderAsync(String pkg, final CopyCallback callback, boolean unusedDialogFlag) {
        new Thread(() -> copyObbFolder(pkg, callback), "obb-copy").start();
    }

    /**
     * True when every OBB the game has on shared storage is already in the
     * sandbox at the same size. Falls back to "a main OBB exists" when the
     * source cannot be listed (no storage access yet).
     */
    public boolean checkObbContainer(String packageName) {
        File destDir;
        try {
            destDir = getSandboxObbDir(packageName);
        } catch (Throwable t) {
            Log.w(TAG, "sandbox dir unavailable: " + t.getMessage());
            return false;
        }
        if (destDir == null || !destDir.isDirectory()) return false;

        File sourceDir = findSourceDir(packageName);
        if (sourceDir != null) {
            List<File> obbs = listObbFiles(sourceDir, packageName);
            if (!obbs.isEmpty()) {
                for (File obb : obbs) {
                    File copy = new File(destDir, obb.getName());
                    if (!copy.isFile() || copy.length() != obb.length()) return false;
                }
                return true;
            }
            return false;                    // source readable, but nothing to copy
        }

        // cannot see the source: trust a complete-looking destination
        Pattern main = mainPattern(packageName);
        File[] local = destDir.listFiles();
        if (local == null) return false;
        for (File file : local) {
            if (file.isFile() && main.matcher(file.getName()).matches() && file.length() > 0) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ the copy */

    private void copyObbFolder(String pkg, CopyCallback callback) {
        try {
            File sourceDir = findSourceDir(pkg);
            if (sourceDir == null) {
                fail(callback, "The game's OBB folder was not found.\n\n"
                        + "Open " + pkg + " once so it finishes downloading its data, "
                        + "and make sure All files access is allowed.");
                return;
            }

            File destDir;
            try {
                destDir = getSandboxObbDir(pkg);
            } catch (Throwable t) {
                fail(callback, "The sandbox storage is not available — is the game installed in the sandbox?"
                        + detail(t));
                return;
            }
            if (destDir == null) {
                fail(callback, "The sandbox storage is not available.");
                return;
            }
            if (sourceDir.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                ok(callback);
                return;
            }
            if (!destDir.isDirectory() && !destDir.mkdirs() && !destDir.isDirectory()) {
                fail(callback, "Cannot create the game data folder inside the sandbox.");
                return;
            }

            List<File> obbs = listObbFiles(sourceDir, pkg);
            List<File> pending = new ArrayList<>();
            long needed = 0L;
            for (File obb : obbs) {
                if (obb.length() <= 0) continue;
                File already = new File(destDir, obb.getName());
                if (already.isFile() && already.length() == obb.length()) continue;
                pending.add(obb);
                needed += obb.length();
            }

            if (obbs.isEmpty()) {
                fail(callback, "The game's OBB file is missing.\n\n"
                        + "Open the game once so it downloads its data, then try again.");
                return;
            }
            if (pending.isEmpty()) {
                ok(callback);
                return;
            }

            long free = freeBytes(destDir);
            if (free > 0 && free < needed + (8L * 1024L * 1024L)) {
                fail(callback, "Not enough free storage — "
                        + human(needed) + " of game data still has to be copied.");
                return;
            }

            long done = 0L;
            for (File obb : pending) {
                File target = new File(destDir, obb.getName());
                if (!copyFile(obb, target, done, needed, callback)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    return;                  // copyFile already reported
                }
                if (target.length() != obb.length()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    fail(callback, "The copy stopped early (" + human(target.length())
                            + " of " + human(obb.length()) + "). Free some space and retry.");
                    return;
                }
                done += obb.length();
            }

            ok(callback);

        } catch (Throwable t) {
            Log.e(TAG, "OBB copy failed", t);
            fail(callback, "Copying the game data failed" + detail(t));
        }
    }

    private boolean copyFile(File source, File target, long doneBefore, long total,
                             CopyCallback callback) {
        FileOutputStream output = null;
        try (FileInputStream input = new FileInputStream(source)) {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                fail(callback, "Cannot create the sandbox folder for the game data.");
                return false;
            }

            output = new FileOutputStream(target);
            byte[] buffer = new byte[1024 * 512];
            int length;
            long written = 0L;
            long lastPush = 0L;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
                written += length;
                long now = System.currentTimeMillis();
                if (callback != null && total > 0 && now - lastPush > 120L) {
                    lastPush = now;
                    final int percent = (int) Math.min(100L, ((doneBefore + written) * 100L) / total);
                    main.post(() -> callback.onProgressUpdate(percent));
                }
            }

            output.flush();
            output.getFD().sync();
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "writing " + target + " failed", t);
            fail(callback, "Cannot write the game data" + detail(t)
                    + "\n\nFree some space or allow All files access, then retry.");
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /* ----------------------------------------------------------------- helpers */

    /** The first readable OBB directory for this package, or null. */
    private File findSourceDir(String pkg) {
        for (String root : SOURCE_ROOTS) {
            File dir = new File(root + "/Android/obb/" + pkg);
            if (dir.isDirectory() && dir.canRead()) {
                String[] names = dir.list();
                if (names != null && names.length > 0) return dir;
                return dir;                  // readable but empty: still the right place
            }
        }
        // last resort: the API's own idea of external storage
        try {
            File fallback = new File(Environment.getExternalStorageDirectory(),
                    "Android/obb/" + pkg);
            if (fallback.isDirectory() && fallback.canRead()) return fallback;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private File getSandboxObbDir(String pkg) {
        return new File(MetaStorageManager.obtainAppExternalStorageDir(pkg) + "/Android/obb", pkg);
    }

    private Pattern mainPattern(String pkg) {
        return Pattern.compile("^main\\.[0-9]+\\." + Pattern.quote(pkg) + "\\.obb$");
    }

    /** main and patch OBBs, smallest first so the bar moves early. */
    private List<File> listObbFiles(File dir, String pkg) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        Pattern both = Pattern.compile(
                "^(main|patch)\\.[0-9]+\\." + Pattern.quote(pkg) + "\\.obb$");
        for (File file : files) {
            if (file.isFile() && both.matcher(file.getName()).matches() && file.length() > 0) {
                out.add(file);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.length(), b.length()));
        return out;
    }

    private long freeBytes(File dir) {
        try {
            android.os.StatFs stat = new android.os.StatFs(dir.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024L * 1024L) return (bytes / (1024 * 1024)) + " MB";
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String detail(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) return " (" + t.getClass().getSimpleName() + ")";
        return " (" + (m.length() > 90 ? m.substring(0, 90) + "…" : m) + ")";
    }

    private void ok(CopyCallback callback) {
        if (callback != null) main.post(() -> callback.onCopyCompleted(true));
    }

    private void fail(CopyCallback callback, String message) {
        Log.w(TAG, message);
        if (callback != null) main.post(() -> callback.onError(message));
    }

    /* ---------------------------------------------------------------- install */

    @SuppressLint("StaticFieldLeak")
    public void installIntoBox(String pkg, CopyCallback callback) {
        new Thread(() -> {
            try {
                PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
                String apkPath = info.applicationInfo.sourceDir;
                net_62v.external.MetaApplicationInstaller.installAppByPath(apkPath);
                boolean installed = MetaPackageManager.isInnerAppInstalled(pkg);
                if (callback != null) {
                    main.post(() -> callback.onCopyCompleted(installed));
                }
            } catch (PackageManager.NameNotFoundException e) {
                fail(callback, "The game is not installed on this device.");
            } catch (Throwable t) {
                fail(callback, "Could not install the game into the sandbox" + detail(t));
            }
        }, "sandbox-install").start();
    }

    /** Kept for callers that want the package's checker directly. */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Convenience for tests / logs: which roots look usable right now. */
    public static List<String> readableRoots() {
        List<String> out = new ArrayList<>();
        for (String root : Arrays.asList(SOURCE_ROOTS)) {
            File f = new File(root);
            if (f.isDirectory() && f.canRead()) out.add(root);
        }
        return out;
    }
}
