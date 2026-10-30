package com.xlox;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.File;

import net_62v.external.IMundoProcessCallback;
import net_62v.external.MetaActivationManager;
import net_62v.external.MetaCore;

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

            String loaderPath = new File(getFilesDir(), "loader").getAbsolutePath();
            File bgmiLib = new File(loaderPath, "libbgmi.so");

            if (bgmiLib.exists()) {
                System.load(bgmiLib.getAbsolutePath());
                Log.d("Load", "libbgmi.so LOADED");
            } else {
                Log.e("Load", "libbgmi.so NOT FOUND");
            }

        } catch (Exception e) {
            Log.e("Load", "Library load error", e);
        }
    }
}
