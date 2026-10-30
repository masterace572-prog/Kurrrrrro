package com.xlox.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net_62v.external.MetaStorageManager;

/**
 * Downloads the loader library zip from the panel endpoint, extracts it into
 * the loader's own files dir, and copies {@code libbgmi.so} into a location
 * that {@link BoxApplication} can reach from the game process
 * ({@code com.pubg.imobile}).
 *
 * The game process runs {@link BoxApplication#onCreate(String, String)} with
 * {@code getFilesDir()} pointing at the *game's* internal storage, so a file
 * sitting in the loader's files dir is invisible to it. The mundo sandbox
 * exposes the game's external storage dir through
 * {@link MetaStorageManager#obtainAppExternalStorageDir(String)}; copying the
 * .so there makes it loadable by the game.
 */
public final class LibLoader {

    private static final String TAG = "LibLoader";
    private static final String LIB_NAME = "libbgmi.so";
    private static final String LOADER_DIR = "loader";

    private LibLoader() {}

    /**
     * Starts the download + extract + copy flow. The callback runs on the UI
     * thread when everything is done (or when it fails).
     */
    public static void loadIfMissing(final Context ctx, final Callback callback) {
        final File loaderLib = new File(new File(ctx.getFilesDir(), LOADER_DIR), LIB_NAME);
        if (loaderLib.exists() && loaderLib.length() > 0) {
            // Already downloaded — still make sure the game process can see it.
            copyToGameStorage(ctx, loaderLib, callback);
            return;
        }

        new Downtwo(ctx, success -> {
            if (!success) {
                if (callback != null) callback.onResult(false, "Download failed");
                return;
            }
            File extracted = new File(new File(ctx.getFilesDir(), LOADER_DIR), LIB_NAME);
            if (!extracted.exists()) {
                if (callback != null) callback.onResult(false, "Extracted lib not found");
                return;
            }
            copyToGameStorage(ctx, extracted, callback);
        }).execute(Downtwo.Link());
    }

    private static void copyToGameStorage(final Context ctx, final File source,
                                          final Callback callback) {
        new AsyncTask<Void, Void, Boolean>() {
            String error = "";

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    String gameRoot = MetaStorageManager.obtainAppExternalStorageDir("com.pubg.imobile");
                    if (gameRoot == null) {
                        error = "Game external storage dir unavailable";
                        return false;
                    }

                    File gameLoaderDir = new File(gameRoot, LOADER_DIR);
                    if (!gameLoaderDir.exists() && !gameLoaderDir.mkdirs()) {
                        error = "Could not create " + gameLoaderDir.getAbsolutePath();
                        return false;
                    }

                    File target = new File(gameLoaderDir, LIB_NAME);
                    copyFile(source, target);
                    Log.d(TAG, "libbgmi.so copied to " + target.getAbsolutePath());
                    return true;
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (callback != null) callback.onResult(success, error);
            }
        }.execute();
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            FileChannel inChannel = in.getChannel();
            FileChannel outChannel = out.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
        target.setReadable(true, false);
    }

    public interface Callback {
        void onResult(boolean success, String error);
    }
}
