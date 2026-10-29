package com.xlox.utils;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xlox.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net_62v.external.MetaPackageManager;
import net_62v.external.MetaStorageManager;

@Obfuscate
public class AppManager extends AppCompatActivity {

    private final Context ctx;

    public static final int INSTALL_APP = 1;
    public static final int COPY_OBB = 3;

    /**
     * Where a successful copy is remembered.
     *
     * The sandbox hands out a virtual path for the game's obb folder and that
     * path is not always listable on the next launch, so "can I see the file?"
     * alone made the app ask for a copy every single time even though the data
     * was already in place. The receipt only ever gets written after a copy
     * that actually finished.
     */
    private static final String PREFS = "com.xlox.download";
    private static final String RECEIPT_NAME = "obb_name_";
    private static final String RECEIPT_SIZE = "obb_size_";
    private static final String RECEIPT_AT = "obb_at_";
    /** Where the obb that is actually in use was found (sandbox, Android/obb, …). */
    private static final String RECEIPT_SOURCE = "obb_source_";
    private static final String TAG = "XloxObb";

    public interface CopyCallback {
        void onCopyCompleted(boolean success);

        default void onProgressUpdate(int progress) {}

        /**
         * Why the copy failed, in words a user can act on ("the folder is
         * read-only", "no main.*.obb found in …"). The app used to show
         * "Copy failed. Try again." for every possible cause, which is exactly
         * as useful as no message at all.
         */
        default void onCopyFailed(String reason) {}
    }

    /** Where the game data is, or why it could not be found. */
    public static final class ObbStatus {
        public final boolean ready;
        /** The obb file found, or null. */
        public final File file;
        /** Human label of the place it was found, e.g. "Android/obb". */
        public final String where;
        /** Short explanation when it is not ready. */
        public final String detail;

        ObbStatus(boolean ready, File file, String where, String detail) {
            this.ready = ready;
            this.file = file;
            this.where = where;
            this.detail = detail;
        }
    }

    public AppManager(Context ctx) {
        this.ctx = ctx;
    }

    @SuppressLint("StaticFieldLeak")
    public void appManager(String pkg, int method, CopyCallback callback, boolean showDialog) {

        new AsyncTask<Void, Integer, Boolean>() {

            String errorMessage = "";

            Dialog progressDialog;
            ProgressBar progressBar;
            TextView progressText;

            long totalBytes = 0;
            long copiedBytes = 0;

            @Override
            protected void onPreExecute() {

                if (method == COPY_OBB && showDialog) {

                    progressDialog = new Dialog(ctx);
                    progressDialog.setContentView(R.layout.progress_dialog);
                    progressDialog.setCancelable(false);

                    progressBar = progressDialog.findViewById(R.id.progressBar);
                    progressText = progressDialog.findViewById(R.id.progressText);

                    if (progressBar != null) {
                        progressBar.setMax(100);
                        progressBar.setProgress(0);
                    }

                    if (progressText != null) {
                        progressText.setText("0%");
                    }

                    progressDialog.show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... voids) {

                if (method == COPY_OBB) {
                    return copyObbFolder();
                }

                if (method == INSTALL_APP) {
                    return installApp(pkg);
                }

                return false;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                if (callback != null) {
                    callback.onProgressUpdate(values[0]);
                }

                if (progressBar != null) {
                    progressBar.setProgress(values[0]);
                }

                if (progressText != null) {
                    progressText.setText(values[0] + "%");
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                boolean ok = Boolean.TRUE.equals(success);
                if (!ok && errorMessage != null && !errorMessage.isEmpty()) {
                    Log.w(TAG, "copy failed: " + errorMessage);
                }

                if (callback != null) {
                    callback.onCopyCompleted(ok);
                    if (!ok && errorMessage != null && !errorMessage.isEmpty()) {
                        callback.onCopyFailed(errorMessage);
                    }
                } else {
                    showResultDialog(ok, errorMessage);
                }
            }

            private boolean installApp(String pkg) {

                try {

                    PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(pkg, 0);

                    String apkPath = packageInfo.applicationInfo.sourceDir;

                    net_62v.external.MetaApplicationInstaller.installAppByPath(apkPath);

                    boolean result = MetaPackageManager.isInnerAppInstalled(pkg);

                    if (!result) {
                        errorMessage = "Sandbox install failed!";
                        return false;
                    }

                    return true;

                } catch (PackageManager.NameNotFoundException e) {
                    errorMessage = "Package not found!";
                    return false;

                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            private boolean copyObbFolder() {

                try {
                    // The game data can sit in several places on a real phone
                    // (the sandbox's own obb dir, the normal Android/obb, a
                    // Download folder after a manual copy). Look for it instead
                    // of insisting on the one path the app used to hard-code.
                    ObbStatus source = obbStatus(pkg);
                    if (!source.ready || source.file == null) {
                        errorMessage = source.detail;
                        return false;
                    }

                    File destDir;
                    try {
                        destDir = getMundoObbDir(pkg);
                    } catch (Exception e) {
                        // No sandbox path at all: the file is already somewhere
                        // the game can read, so there is nothing left to do.
                        if (isPublic(pkg, source.file)) {
                            rememberObb(pkg, source.file.getName(), source.file.length(),
                                    source.file.getAbsolutePath());
                            Log.i(TAG, "sandbox dir unavailable, the game reads " + source.file);
                            return true;
                        }
                        errorMessage = "The sandbox folder for the game is not available ("
                                + e.getMessage() + ").";
                        return false;
                    }

                    if (source.file.getAbsolutePath().startsWith(destDir.getAbsolutePath())) {
                        return true; // already in the sandbox
                    }

                    if (!destDir.exists() && !destDir.mkdirs()) {
                        // Cannot create the target. If the obb is in the phone's
                        // own Android/obb the sandbox already reads it there.
                        if (isPublic(pkg, source.file)) {
                            rememberObb(pkg, source.file.getName(), source.file.length(),
                                    source.file.getAbsolutePath());
                            Log.i(TAG, "cannot write " + destDir + ", using " + source.file);
                            return true;
                        }
                        errorMessage = "Could not create " + destDir.getAbsolutePath() + ".";
                        return false;
                    }

                    totalBytes = source.file.length();
                    copiedBytes = 0;

                    File target = new File(destDir, source.file.getName());
                    boolean copied = copyFile(source.file, target);

                    if (copied) {
                        rememberObb(pkg, source.file.getName(), source.file.length(),
                                target.getAbsolutePath());
                        return true;
                    }

                    // A failed copy of a file that already sits in the phone's
                    // Android/obb is not a failure the user needs to fix: the
                    // sandbox serves that exact path.
                    if (isPublic(pkg, source.file)) {
                        rememberObb(pkg, source.file.getName(), source.file.length(),
                                source.file.getAbsolutePath());
                        Log.w(TAG, "copy into the sandbox failed, falling back to " + source.file
                                + " (" + errorMessage + ")");
                        errorMessage = "";
                        return true;
                    }
                    return false;

                } catch (Exception e) {
                    errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
                    return false;
                }
            }

            private boolean copyFile(File source, File target) {
                try {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    FileInputStream input = new FileInputStream(source);
                    FileOutputStream output = new FileOutputStream(target);
                    byte[] buffer = new byte[1024 * 1024];
                    int length;

                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                        copiedBytes += length;
                        int progress = totalBytes > 0 ? (int) ((copiedBytes * 100) / totalBytes) : 100;
                        publishProgress(progress);
                    }

                    input.close();
                    output.flush();
                    output.close();
                    return true;
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            private long getFolderSize(File dir) {

                long size = 0;
                File[] files = dir.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            size += getFolderSize(file);
                        } else {
                            size += file.length();
                        }
                    }
                }

                return size;
            }

            private void showResultDialog(boolean success, String errorMessage) {

                new MaterialAlertDialogBuilder(ctx)
                        .setTitle(success ? "Success" : "Error")
                        .setMessage(success ? (method == INSTALL_APP ? "App installed successfully." : "OBB ready successfully.") : errorMessage)
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }

        }.execute();
    }

    private File getMundoObbDir(String pkg) {
        try {
            return new File(
                    MetaStorageManager.obtainAppExternalStorageDir(pkg) + "/Android/obb",
                    pkg
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // FIXED: Now accepts a String parameter for package name
    public boolean checkObbContainer(String packageName) {
        return obbStatus(packageName).ready;
    }

    /**
     * Looks for the game's obb everywhere it can legitimately be, and says which
     * copy it found.
     *
     * The old check asked one sandbox path whether it could list itself. On a
     * real phone that path is not listable until the sandbox has prepared the
     * game, so the answer was "no" until the game had been launched once — and
     * every manual copy into the phone's own Android/obb was invisible, even
     * though the game loads it.
     */
    public ObbStatus obbStatus(String packageName) {
        List<File> dirs = obbDirs(packageName);
        List<String> report = new ArrayList<>();
        boolean unreadable = false;
        boolean obbBlocked = false;

        for (File dir : dirs) {
            if (dir == null) continue;
            String where = label(dir, packageName);

            // 1. Ask for the one name the game will look for. A known path can
            //    be probed even when the folder cannot be listed — this is how
            //    the original loader checked its own copy, and it is the reason
            //    that check kept working where listFiles() returned null.
            File probed = probeKnownName(dir, packageName);
            if (probed != null) {
                rememberObb(packageName, probed.getName(), probed.length(), probed.getAbsolutePath());
                Log.i(TAG, "game data: " + probed + " (" + where + ", by name)");
                return new ObbStatus(true, probed, where, "");
            }

            // 2. Otherwise list the folder and take the newest obb in it.
            File[] files = dir.listFiles();
            if (files == null) {
                unreadable = true;                // missing or not listable
                boolean restricted = where.startsWith("Android/obb");
                if (restricted) obbBlocked = true;
                report.add(where + (restricted ? " — not readable (needs Files access)" : " — not readable"));
                continue;
            }
            File found = pickObb(files, packageName, isPackageDir(dir, packageName));
            if (found != null) {
                rememberObb(packageName, found.getName(), found.length(), found.getAbsolutePath());
                Log.i(TAG, "game data: " + found + " (" + where + ")");
                return new ObbStatus(true, found, where, "");
            }
            report.add(where + " — no .obb");
        }

        // Nothing listable. The recorded file may still be readable directly.
        String recorded = prefs().getString(RECEIPT_SOURCE + packageName, "");
        if (!recorded.isEmpty()) {
            File file = new File(recorded);
            if (file.isFile() && file.length() > 0) {
                return new ObbStatus(true, file, "recorded", "");
            }
        }

        // An earlier copy finished and nothing can be inspected: trust it rather
        // than asking for a 1 GB file again.
        String name = prefs().getString(RECEIPT_NAME + packageName, "");
        if (!name.isEmpty() && unreadable) {
            return new ObbStatus(true, null, "recorded", "");
        }

        String detail = "No game data found. " + join(report) + ".";
        if (obbBlocked) {
            detail += " Android/obb can only be read with the Files permission — allow it, then try again.";
        }
        return new ObbStatus(false, null, "", detail);
    }

    /** Every directory a main.<n>.<pkg>.obb can reasonably be in. */
    private List<File> obbDirs(String packageName) {
        List<File> dirs = new ArrayList<>();
        File sandbox = sandboxObbDir(packageName);
        if (sandbox != null) dirs.add(sandbox);

        File external = Environment.getExternalStorageDirectory();
        if (external != null) {
            dirs.add(new File(new File(new File(external, "Android"), "obb"), packageName));
            dirs.add(new File(new File(new File(external, "Android"), "media"), packageName));
            dirs.add(new File(external, "Download"));
            dirs.add(new File(external, "Downloads"));
            dirs.add(external);                              // a file dropped at the root
        }
        return dirs;
    }

    /** The version code of the installed game, or -1 when it cannot be read. */
    private long versionCode(String packageName) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(packageName, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            //noinspection deprecation
            return info.versionCode;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * The exact file name the game expects, probed by name.
     *
     * `listFiles()` on a protected folder answers null even when the file inside
     * is perfectly readable, so probing the known name is how "is my copy there?"
     * gets a truthful answer.
     */
    private File probeKnownName(File dir, String packageName) {
        long code = versionCode(packageName);
        if (code <= 0) return null;
        File file = new File(dir, "main." + code + "." + packageName + ".obb");
        return file.isFile() && file.length() > 0 ? file : null;
    }

    /** The sandbox's obb folder for the game, or null when it cannot be mapped. */
    private File sandboxObbDir(String packageName) {
        try {
            return new File(
                    MetaStorageManager.obtainAppExternalStorageDir(packageName) + "/Android/obb",
                    packageName
            );
        } catch (Exception e) {
            Log.w(TAG, "sandbox obb dir unavailable: " + e.getMessage());
            return null;
        }
    }

    /** The newest main.<n>.<pkg>.obb in a folder list. */
    private File pickObb(File[] files, String packageName, boolean loose) {
        String pattern = obbPattern(packageName);
        File selected = null;
        long selectedVersion = -1L;

        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String name = file.getName();
            boolean main = name.matches(pattern);
            if (!main) {
                // In a folder that belongs to this game (Android/obb/<pkg>,
                // Android/media/<pkg>, the sandbox folder) any .obb will do:
                // the file name is not something the user controls.
                if (!loose || !name.toLowerCase().endsWith(".obb")) continue;
            }
            long version = versionOf(name);
            if (selected == null || version > selectedVersion) {
                selected = file;
                selectedVersion = version;
            }
        }
        return selected;
    }

    /** "main.123.com.pubg.imobile.obb" -> 123, anything else -> -1. */
    private long versionOf(String name) {
        String[] parts = name.split("\\.");
        if (parts.length > 1) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1L;
    }

    /** A folder that exists for this game only, so any .obb in it counts. */
    private boolean isPackageDir(File dir, String packageName) {
        return dir != null && dir.getName().equals(packageName);
    }

    /** True for the phone's own storage, which the sandbox reads through. */
    private boolean isPublic(String packageName, File file) {
        if (file == null) return false;
        File external = Environment.getExternalStorageDirectory();
        if (external == null) return false;

        String path = file.getAbsolutePath();
        if (!path.startsWith(external.getAbsolutePath())) return false;

        File sandbox = sandboxObbDir(packageName);
        return sandbox == null || !path.startsWith(sandbox.getAbsolutePath());
    }

    /** Short, readable name for a folder: "Android/obb", "Download", "sandbox". */
    private String label(File dir, String packageName) {
        if (dir == null) return "?";
        File sandbox = sandboxObbDir(packageName);
        if (sandbox != null && dir.getAbsolutePath().equals(sandbox.getAbsolutePath())) {
            return "sandbox";
        }
        String path = dir.getAbsolutePath();
        int i = path.indexOf("Android/");
        if (i >= 0) return path.substring(i);

        File external = Environment.getExternalStorageDirectory();
        if (external != null && path.startsWith(external.getAbsolutePath())) {
            String rest = path.substring(external.getAbsolutePath().length());
            rest = rest.replaceFirst("^/", "");
            return rest.isEmpty() ? "storage root" : rest;
        }
        return path.length() > 28 ? "…" + path.substring(path.length() - 28) : path;
    }

    private String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append(", ");
            out.append(part);
        }
        return out.toString();
    }

    /** "main.<version>.<package>.obb", the file the game loads. */
    private String obbPattern(String packageName) {
        return "^main\\.[0-9]+\\." + Pattern.quote(packageName) + "\\.obb$";
    }

    /** True when a copy finished at least once, even if the folder is unreadable now. */
    public static boolean hasObbReceipt(Context context, String packageName) {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(RECEIPT_NAME + packageName, "").isEmpty();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void rememberObb(String packageName, String fileName, long size, String path) {
        prefs().edit()
                .putString(RECEIPT_NAME + packageName, fileName)
                .putLong(RECEIPT_SIZE + packageName, size)
                .putLong(RECEIPT_AT + packageName, System.currentTimeMillis())
                .putString(RECEIPT_SOURCE + packageName, path == null ? "" : path)
                .apply();
    }

    public void copyObbFolderAsync(String pkg, final CopyCallback callback) {
        copyObbFolderAsync(pkg, callback, true);
    }

    public void copyObbFolderAsync(String pkg, final CopyCallback callback, boolean showDialog) {
        ObbStatus status = obbStatus(pkg);
        if (status.ready) {
            if (callback != null) {
                callback.onCopyCompleted(true);
                callback.onProgressUpdate(100);
            }
            return;
        }

        appManager(pkg, COPY_OBB, callback, showDialog);
    }
}
