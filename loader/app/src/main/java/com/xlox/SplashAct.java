package com.xlox;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xlox.utils.LibGate;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * The first thing shown: just the mark and the name, long enough for the process
 * to warm up. Everything that talks to the network happens on the next screen,
 * so this one can never hold the user up.
 */
@Obfuscate
public class SplashAct extends AppCompatActivity {

    private static final long SPLASH_MILLIS = 1100L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView version = findViewById(R.id.splashVersion);
        if (version != null) {
            version.setText("v" + LibGate.installedVersion(this));
        }

        View logo = findViewById(R.id.splashLogo);
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setScaleX(0.92f);
            logo.setScaleY(0.92f);
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).start();
        }
        View name = findViewById(R.id.splashName);
        if (name != null) {
            name.setAlpha(0f);
            name.animate().alpha(1f).setDuration(520).setStartDelay(180).start();
        }

        new Handler(getMainLooper()).postDelayed(this::goToLogin, SPLASH_MILLIS);
    }

    private void goToLogin() {
        startActivity(new Intent(this, LogAct.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        // nothing to go back to
    }
}
