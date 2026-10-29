package com.xlox;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xlox.utils.Downtwo;
import com.xlox.utils.Prefs;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class LogAct extends AppCompatActivity {

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private Prefs prefs;
    private final String USER = "USER";

    private EditText textUsername;
    private AppCompatButton btnLogin;

    private Dialog loadingDialog;

    // UI
    private TextView premium;
    private TextView pasteKey;
    private LinearLayout loginCard;
    private LinearLayout mainContainer;
    private ImageView logo;
    private ImageView eyeToggle;
    private boolean isPasswordVisible = false;

    // Permissions
    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 100;

    private static final String PREFS_NAME = "com.xlox.prefs";
    private static final String PREF_PERMISSIONS_GRANTED = "permissions_granted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (iskokofm()) {
            new AlertDialog.Builder(LogAct.this).setTitle("VPN Detected").setMessage("Please disable VPN to continue").setPositiveButton("OK", null).show();
            return;
        }

        setContentView(R.layout.activity_login);
        prefs = new Prefs(this);
        checkAndRequestPermissions();

        textUsername = findViewById(R.id.userkey);
        btnLogin = findViewById(R.id.login);
        premium = findViewById(R.id.premium);
        loginCard = findViewById(R.id.loginCard);
        mainContainer = findViewById(R.id.mainContainer);
        logo = findViewById(R.id.logo);
        eyeToggle = findViewById(R.id.eyeToggle);
        pasteKey = findViewById(R.id.pasteKey);

        textUsername.setText(prefs.getSt(USER, ""));

        if (eyeToggle != null) {
            eyeToggle.setOnClickListener(v -> {
                if (isPasswordVisible) {
                    textUsername.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    eyeToggle.setImageResource(R.drawable.ic_eye);
                } else {
                    textUsername.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    eyeToggle.setImageResource(R.drawable.ic_eye_off);
                }
                isPasswordVisible = !isPasswordVisible;
                textUsername.setSelection(textUsername.getText().length());
            });
        }

        if (pasteKey != null) {
            pasteKey.setOnClickListener(v -> pasteFromClipboard());
        }

        startIntroAnimation();

        btnLogin.setOnClickListener(v -> {
            String userKey = textUsername.getText().toString().trim();
            if (userKey.isEmpty()) {
                textUsername.setError("Please enter key");
                return;
            }
            if (iskokofm()) {
                new AlertDialog.Builder(LogAct.this).setTitle("VPN Detected").setMessage("Please disable VPN to continue").setPositiveButton("OK", null).show();
                return;
            }

            if (!hasNetwork()) {
                showLoginError("No internet connection — connect and try again");
                return;
            }

            prefs.setSt(USER, userKey);

            btnLogin.setEnabled(false);
            btnLogin.setText(R.string.checking_key);
            btnLogin.setTextColor(Color.WHITE);

            Login(this, userKey);
        });
    }

    private void startIntroAnimation() {
        if (mainContainer != null) {
            mainContainer.setVisibility(View.VISIBLE);
            mainContainer.setAlpha(1f);
        }
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setTranslationY(-180f);
            logo.animate().alpha(1f).translationY(0f).setDuration(1200).setInterpolator(new DecelerateInterpolator(1.4f)).start();
            ObjectAnimator logoFloat = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -6f, 0f);
            logoFloat.setStartDelay(1300);
            logoFloat.setDuration(2600);
            logoFloat.setRepeatCount(ValueAnimator.INFINITE);
            logoFloat.start();
        }
        if (premium != null) {
            premium.setAlpha(0f);
            premium.setTranslationY(120f);
            premium.animate().alpha(1f).translationY(0f).setDuration(1200).setStartDelay(450).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
        if (loginCard != null) {
            loginCard.setAlpha(0f);
            loginCard.setTranslationY(260f);
            loginCard.animate().alpha(1f).translationY(0f).setDuration(1500).setStartDelay(650).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
    }

    private void Login(final Context m_Context, final String userKey) {
        final AlertDialog[] verifying = new AlertDialog[1];
        try {
            verifying[0] = new AlertDialog.Builder(this)
                    .setTitle("Verifying key")
                    .setMessage("Checking your license with the panel…")
                    .setCancelable(false)
                    .create();
            verifying[0].show();
        } catch (Throwable ignored) {
        }

        Handler loginHandler = new Handler(msg -> {
            if (verifying[0] != null && verifying[0].isShowing()) {
                verifying[0].dismiss();
            }
            if (msg.what == 0) {
                // key is valid — the startup gate runs once more (instantly when
                // the lib is current) before the main screen opens
                Intent i = new Intent(m_Context, SplashActivity.class);
                i.putExtra(SplashActivity.EXTRA_NEXT, SplashActivity.NEXT_MAIN);
                startActivity(i);
                finish();
            } else if (msg.what == 1) {
                resetLoginButton();
                showLoginError((String) msg.obj);
            }
            return true;
        });

        new Thread(() -> {
            String result;
            try {
                result = Check(m_Context, userKey);
            } catch (Throwable t) {
                String m = t.getMessage();
                result = "Verification failed" + (m == null || m.trim().isEmpty() ? "" : ": " + m);
            }
            if (result == null || result.trim().isEmpty()) {
                result = "The server rejected this key (empty answer)";
            }

            if ("OK".equals(result)) {
                loginHandler.sendEmptyMessage(0);
            } else {
                Message msg = Message.obtain();
                msg.what = 1;
                msg.obj = result;
                loginHandler.sendMessage(msg);
            }
        }).start();
    }

    private void resetLoginButton() {
        runOnUiThread(() -> {
            if (btnLogin == null) return;
            btnLogin.setEnabled(true);
            btnLogin.setText(R.string.login_button);
            btnLogin.setTextColor(Color.parseColor("#0D0F12"));
        });
    }

    /** A dialog, not a toast: a rejected key must stay on screen until read. */
    private void showLoginError(String message) {
        if (isFinishing()) return;
        String text = message == null || message.trim().isEmpty()
                ? "The key could not be verified"
                : message.trim();
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Key not accepted")
                    .setMessage(text)
                    .setPositiveButton("Try again", (d, w) -> d.dismiss())
                    .setNegativeButton("Paste key", (d, w) -> {
                        d.dismiss();
                        pasteFromClipboard();
                    })
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (copied != null) {
                    textUsername.setText(copied.toString().trim());
                    textUsername.setSelection(textUsername.getText().length());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean hasNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                if (active == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    private void checkAndRequestPermissions() {
        if (!isStoragePermissionGranted()) {
            requestStoragePermissionDirect();
        }
    }

    private boolean isStoragePermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void requestStoragePermissionDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_MANAGE_STORAGE_PERMISSION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION) {
            checkAndRequestPermissions();
        }
    }

    private boolean iskokofm() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static native String Check(Context mContext, String userKey);
}
