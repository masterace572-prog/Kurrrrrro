package com.xlox;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.File;

import net_62v.external.IMundoProcessCallback;
import net_62v.external.MetaActivationManager;
import net_62v.external.MetaCore;
import net_62v.external.MetaStorageManager;

public class BoxApplication extends Application
        implements IMundoProcessCallback {

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static native String getSdkKey();

    private static final String TAG = "BoxApplication";

    public static BoxApplication gApp;


    public static boolean ENABLE_IMGUI = false;

    public static BoxApplication get() {
        return gApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MetaCore.setProcessLifecycleCallback(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        
        try {
            MetaActivationManager.activateSdk(getSdkKey());
        } catch (Exception e) {
            Log.e(TAG, "Activation Error", e);
        }
    }

    @Override
    public void onCreate(String s, String s1) {
        if (!s.equals("com.pubg.imobile")) {
            return;
        }

        try {

            ENABLE_IMGUI = false;

            // The lib is downloaded by the loader into its own files dir, but
            // this callback runs in the *game* process where getFilesDir() is
            // the game's internal storage. Check both locations: the game's
            // internal files dir (in case the lib was placed there) and the
            // game's external storage dir (where LibLoader copies it).
            File[] candidates = {
                    new File(new File(getFilesDir(), "loader"), "libbgmi.so"),
                    gameExternalLib()
            };

            File bgmiLib = null;
            for (File c : candidates) {
                if (c != null && c.exists() && c.length() > 0) {
                    bgmiLib = c;
                    break;
                }
            }

            if (bgmiLib != null) {
                System.load(bgmiLib.getAbsolutePath());
                Log.d("Load", "libbgmi.so LOADED from " + bgmiLib.getAbsolutePath());
            } else {
                Log.e("Load", "libbgmi.so NOT FOUND in any candidate location");
            }

        } catch (Exception e) {
            Log.e("Load", "Library load error", e);
        }
    }

    /** libbgmi.so location inside the game's own external storage dir. */
    private File gameExternalLib() {
        try {
            String root = MetaStorageManager.obtainAppExternalStorageDir("com.pubg.imobile");
            if (root == null) return null;
            return new File(new File(root, "loader"), "libbgmi.so");
        } catch (Exception e) {
            return null;
        }
    }
}
