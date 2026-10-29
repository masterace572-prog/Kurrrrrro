package com.xlox.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The one place the app asks "is my library current?".
 *
 * Both screens need it: the login screen gates access on it (an update is
 * mandatory, there is no "later"), and the main screen shows the installed
 * version. The panel is asked first and the repo mirror second, exactly like
 * {@link Downtwo} does it.
 *
 * "Up to date" is the normal case, not an error: when nothing newer is on
 * offer the gate opens immediately and says so. It only blocks when a version
 * higher than the installed one exists — see {@link LibUpdate#isNewer}.
 */
public final class LibGate {

    public enum State {
        /** Asking the panel/mirror. */
        CHECKING,
        /** The installed lib is the newest one. */
        UP_TO_DATE,
        /** A newer lib exists (or none is installed) and must be downloaded. */
        UPDATE_AVAILABLE,
        /** Downloading; progress comes through {@link Listener#onProgress}. */
        DOWNLOADING,
        /** Downloaded and verified. */
        READY,
        /** Could not check or download; detail says why. */
        FAILED
    }

    public interface Listener {
        void onState(State state, String detail);

        default void onProgress(int percent) {}
    }

    private LibGate() {}

    public static File libFile(Context context) {
        return new File(new File(context.getFilesDir(), "loader"), "libbgmi.so");
    }

    public static boolean installed(Context context) {
        File file = libFile(context);
        return file.isFile() && file.length() > 0;
    }

    public static String installedVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("com.xlox.download", Context.MODE_PRIVATE);
        return prefs.getString("version", "1.0");
    }

    /** Asks what is being offered. Downloads nothing. */
    public static void check(Activity activity, Listener listener) {
        listener.onState(State.CHECKING, "");
        final Downtwo[] ref = new Downtwo[1];
        ref[0] = new Downtwo(activity, ok -> {
            Downtwo task = ref[0];
            String result = task == null || task.getResult() == null ? "" : task.getResult();
            String version = task == null ? "" : task.getFetchedServerVersion();

            if (LibUpdate.isUpdateResult(result)) {
                listener.onState(State.UPDATE_AVAILABLE, version == null ? "" : version);
            } else if (result.startsWith("No Update")) {
                listener.onState(State.UP_TO_DATE, version == null ? "" : version);
            } else if (installed(activity)) {
                // offline or the panel is down: the library on disk still works,
                // so this is not a reason to lock the user out
                listener.onState(State.UP_TO_DATE, installedVersion(activity));
            } else {
                listener.onState(State.FAILED,
                        result.isEmpty() ? "Could not reach the update server" : result);
            }
        });
        ref[0].execute(Downtwo.Link(), Downtwo.MODE_CHECK);
    }

    /** Downloads and verifies the library, reporting progress as it goes. */
    public static void download(Activity activity, Listener listener) {
        listener.onState(State.DOWNLOADING, "");
        final Downtwo[] ref = new Downtwo[1];
        ref[0] = new Downtwo(activity, ok -> {
            Downtwo task = ref[0];
            if (ok && installed(activity)) {
                listener.onState(State.READY, installedVersion(activity));
            } else {
                String why = task == null ? "" : task.getLastError();
                if (why == null || why.isEmpty()) why = "The download did not finish";
                listener.onState(State.FAILED, why);
            }
        });
        ref[0].setProgressListener(listener::onProgress);
        ref[0].execute(Downtwo.Link());
    }
}
