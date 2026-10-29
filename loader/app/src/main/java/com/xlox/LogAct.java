package com.xlox;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xlox.utils.LibGate;
import com.xlox.utils.Prefs;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Login, and the update gate that comes before it.
 *
 * The library is what makes the loader do anything, so it is checked as soon as
 * this screen appears: a missing or outdated library blocks everything behind an
 * overlay with a single "Update now" action — there is deliberately no way to
 * skip it, because a stale library is a launch that silently does nothing.
 */
@Obfuscate
public class LogAct extends AppCompatActivity {

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 200;

    private Prefs prefs;
    private final String USER = "USER";

    private EditText textUsername;
    private AppCompatButton btnLogin;
    private TextView pasteKey;
    private TextView statusLine;
    private LinearLayout loginCard;

    private View gate;
    private TextView gateTitle;
    private TextView gateDetail;
    private ProgressBar gateProgress;
    private AppCompatButton gateButton;

    private boolean isPasswordVisible = false;
    private boolean loginEnabled = false;
    private boolean downloading = false;
    private String pendingVersion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isVpnActive()) {
            new AlertDialog.Builder(this)
                    .setTitle("VPN detected")
                    .setMessage("Turn off the VPN to continue.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        setContentView(R.layout.activity_login);
        prefs = new Prefs(this);
        checkAndRequestPermissions();
        bindViews();
        wireInput();

        // what the update gate is allowed to do until the first answer arrives
        setLoginEnabled(false);
        startIntro();

        new Handler(getMainLooper()).postDelayed(this::runUpdateGate, 350);
    }

    private void bindViews() {
        textUsername = findViewById(R.id.userkey);
        btnLogin = findViewById(R.id.login);
        pasteKey = findViewById(R.id.pasteKey);
        statusLine = findViewById(R.id.statusLine);
        loginCard = findViewById(R.id.loginCard);

        gate = findViewById(R.id.gate);
        gateTitle = findViewById(R.id.gateTitle);
        gateDetail = findViewById(R.id.gateDetail);
        gateProgress = findViewById(R.id.gateProgress);
        gateButton = findViewById(R.id.gateButton);

        textUsername.setText(prefs.getSt(USER, ""));

        ImageView eyeToggle = findViewById(R.id.eyeToggle);
        if (eyeToggle != null) {
            eyeToggle.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                textUsername.setInputType(isPasswordVisible
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeToggle.setAlpha(isPasswordVisible ? 1f : 0.7f);
                textUsername.setSelection(textUsername.getText().length());
            });
        }
    }

    private void wireInput() {
        if (pasteKey != null) {
            pasteKey.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null
                        && clipboard.getPrimaryClip().getItemCount() > 0) {
                    CharSequence copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                    if (copied != null) {
                        textUsername.setText(copied.toString().trim());
                        textUsername.setSelection(textUsername.getText().length());
                    }
                }
            });
        }

        if (gateButton != null) {
            gateButton.setOnClickListener(v -> {
                if (downloading) return;
                // "Update now": the user pressed it, so the download is theirs
                startDownload();
            });
        }

        btnLogin.setOnClickListener(v -> {
            if (!loginEnabled) return;
            String userKey = textUsername.getText().toString().trim();
            if (userKey.isEmpty()) {
                textUsername.setError("Enter your key");
                return;
            }
            if (isVpnActive()) {
                toast("Turn off the VPN to continue");
                return;
            }
            prefs.setSt(USER, userKey);
            btnLogin.setEnabled(false);
            btnLogin.setText("Checking key…");
            login(userKey);
        });
    }

    private void startIntro() {
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setTranslationY(-24f);
            logo.animate().alpha(1f).translationY(0f).setDuration(700)
                    .setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
        if (loginCard != null) {
            loginCard.setAlpha(0f);
            loginCard.setTranslationY(28f);
            loginCard.animate().alpha(1f).translationY(0f).setDuration(600)
                    .setStartDelay(150).start();
        }
    }

    // ---------------------------------------------------------------- update gate

    private void runUpdateGate() {
        LibGate.check(this, new LibGate.Listener() {
            @Override
            public void onState(LibGate.State state, String detail) {
                switch (state) {
                    case UP_TO_DATE:
                        hideGate();
                        setLoginEnabled(true);
                        setStatus("Ready");
                        break;
                    case UPDATE_AVAILABLE:
                        pendingVersion = detail;
                        showGate(detail);
                        break;
                    case FAILED:
                        // nothing on disk and nothing reachable: there is nothing
                        // to log in to yet, so this stays on screen with a retry
                        pendingVersion = "";
                        showGateFailure(detail);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void showGate(String version) {
        if (gate == null) return;
        setLoginEnabled(false);
        downloading = false;
        gate.setVisibility(View.VISIBLE);
        gate.setAlpha(0f);
        gate.animate().alpha(1f).setDuration(220).start();

        boolean firstInstall = !LibGate.installed(this);
        gateTitle.setText(firstInstall ? "Setting up" : "Update available");
        gateDetail.setText(firstInstall
                ? "Downloading the game library. This happens once."
                : (version.isEmpty()
                    ? "A new library is required to continue."
                    : "Version " + version + " is required to continue."));
        gateProgress.setVisibility(firstInstall ? View.VISIBLE : View.GONE);
        gateProgress.setIndeterminate(firstInstall);
        gateProgress.setProgress(0);
        gateButton.setText(firstInstall ? "Downloading…" : "Update now");
        gateButton.setEnabled(!firstInstall);

        if (firstInstall) {
            // first run: no tapping, just download it
            startDownload();
        }
    }

    private void showGateFailure(String why) {
        if (gate == null) return;
        setLoginEnabled(false);
        gate.setVisibility(View.VISIBLE);
        gateTitle.setText("Update required");
        gateDetail.setText(why);
        gateProgress.setVisibility(View.GONE);
        gateButton.setText("Retry");
        gateButton.setEnabled(true);
        downloading = false;
    }

    private void startDownload() {
        downloading = true;
        gateButton.setEnabled(false);
        gateButton.setText("Downloading…");
        gateProgress.setVisibility(View.VISIBLE);
        gateProgress.setIndeterminate(false);
        gateProgress.setProgress(0);
        gateDetail.setText(pendingVersion.isEmpty()
                ? "Downloading the required files. Keep this screen open."
                : "Downloading version " + pendingVersion + ". Keep this screen open.");

        LibGate.download(this, new LibGate.Listener() {
            @Override
            public void onState(LibGate.State state, String detail) {
                downloading = false;
                if (state == LibGate.State.READY) {
                    hideGate();
                    setLoginEnabled(true);
                    setStatus("Library " + detail + " ready");
                } else if (state == LibGate.State.FAILED) {
                    showGateFailure(detail);
                }
            }

            @Override
            public void onProgress(int percent) {
                gateProgress.setIndeterminate(false);
                gateProgress.setProgress(percent);
                gateTitle.setText("Updating " + percent + "%");
            }
        });
    }

    private void hideGate() {
        if (gate == null || gate.getVisibility() != View.VISIBLE) return;
        gate.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            gate.setVisibility(View.GONE);
            // the progress report replaced the title; put it back for next time
            gateTitle.setText("Update available");
        }).start();
    }

    private void setLoginEnabled(boolean enabled) {
        loginEnabled = enabled;
        if (btnLogin == null) return;
        btnLogin.setEnabled(enabled);
        btnLogin.setAlpha(enabled ? 1f : 0.45f);
        if (enabled) btnLogin.setText("Continue");
    }

    private void setStatus(String text) {
        if (statusLine != null) statusLine.setText(text);
    }

    @Override
    public void onBackPressed() {
        // The gate cannot be skipped: while it is up, back does nothing.
        if (gate != null && gate.getVisibility() == View.VISIBLE) return;
        super.onBackPressed();
    }

    // -------------------------------------------------------------------- login

    private void login(final String userKey) {
        Handler loginHandler = new Handler(msg -> {
            if (msg.what == 0) {
                startActivity(new Intent(this, MAct.class));
                finish();
            } else {
                btnLogin.setEnabled(true);
                btnLogin.setText("Continue");
                toast(String.valueOf(msg.obj));
            }
            return true;
        });

        new Thread(() -> {
            String result = Check(this, userKey);
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

    // -------------------------------------------------------------- permissions

    private void checkAndRequestPermissions() {
        if (!isStoragePermissionGranted()) {
            requestStoragePermissionDirect();
        } else if (!canRequestPackageInstalls()) {
            requestUnknownAppPermissionsDirect();
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
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_MANAGE_STORAGE_PERMISSION);
        }
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private void requestUnknownAppPermissionsDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_UNKNOWN_APP_SOURCES);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION) {
            checkAndRequestPermissions();
        }
    }

    private boolean isVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                    return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static native String Check(Context mContext, String userKey);
}
