package com.xlox.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * The two storage permissions this app needs, asked for separately and in order.
 *
 * They are not interchangeable, which is what broke the game data copy:
 *
 *  - <b>Files</b> (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`) is a
 *    runtime permission. This app targets Android 9 with
 *    `requestLegacyExternalStorage`, and in legacy mode that runtime grant is
 *    what allows reading `/sdcard/Android/obb/<game>`. Without it the folder is
 *    simply not listable — no error, just "no such directory".
 *  - <b>All files access</b> (`MANAGE_EXTERNAL_STORAGE`) is a special app-op,
 *    toggled in Settings. It covers shared storage (Download, the storage root)
 *    but on Android 11+ it never covers another app's `Android/obb`.
 *
 * Asking for only one of them is why the copy looked like "the file is not
 * there": Download was readable, `Android/obb` never was.
 */
public final class StorageAccess {

    private static final String TAG = "XloxStorage";
    private static final String PREFS = "com.xlox.download";
    private static final String KEY_ASKED = "storage_asked";

    /** Runtime READ/WRITE dialog. */
    public static final int REQUEST_RUNTIME = 4100;
    /** The system "All files access" screen. */
    public static final int REQUEST_ALL_FILES = 4101;

    private StorageAccess() {}

    /** READ + WRITE granted (always true below Android 6). */
    public static boolean runtimeGranted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** The "All files access" app-op (always true below Android 11). */
    public static boolean allFilesGranted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true;
        try {
            return Environment.isExternalStorageManager();
        } catch (Exception e) {
            Log.w(TAG, "could not read the all-files state: " + e.getMessage());
            return false;
        }
    }

    /** Both, i.e. the app can actually look inside Android/obb. */
    public static boolean ready(Context context) {
        return runtimeGranted(context) && allFilesGranted(context);
    }

    /**
     * Asks for whichever permission is still missing — exactly one screen per
     * call, because two system dialogs at once is how the second one gets lost.
     *
     * @return true when a system screen was opened, false when nothing is left
     *         to ask for.
     */
    public static boolean requestNext(AppCompatActivity activity) {
        if (!runtimeGranted(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_RUNTIME);
                return true;
            }
        }

        if (!allFilesGranted(activity)) {
            return openAllFilesScreen(activity);
        }

        return false;
    }

    /** Opens the app's own "All files access" toggle, not the general list. */
    public static boolean openAllFilesScreen(AppCompatActivity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
            activity.startActivityForResult(intent, REQUEST_ALL_FILES);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "per-app all-files screen unavailable: " + e.getMessage());
        }
        try {
            activity.startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_ALL_FILES);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "all-files screen unavailable: " + e.getMessage());
            return false;
        }
    }

    /** Remembered so the dialogs are shown once on their own, not on every launch. */
    public static boolean askedBefore(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ASKED, false);
    }

    public static void markAsked(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ASKED, true).apply();
    }

    /** One line for the UI that names what is still missing. */
    public static String describe(Context context) {
        boolean runtime = runtimeGranted(context);
        boolean allFiles = allFilesGranted(context);
        if (runtime && allFiles) return "File access granted";
        if (!runtime && !allFiles) return "Files and All files access are off";
        if (!runtime) return "Files access is off";
        return "All files access is off";
    }

    /** True when the result of a permission request belongs to this class. */
    public static boolean isOurs(int requestCode) {
        return requestCode == REQUEST_RUNTIME || requestCode == REQUEST_ALL_FILES;
    }
}
