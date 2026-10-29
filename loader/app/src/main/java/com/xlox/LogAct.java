package com.xlox;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import android.content.ClipboardManager;
import android.content.Context;
import android.app.Dialog;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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


    // UI
    private TextView premium;
    private TextView pasteKey;
    private LinearLayout loginCard;
    private ImageView logo;
    private ImageView eyeToggle;
    private boolean isPasswordVisible = false;

    // the "checking your key" dialog
    private Dialog verifyDialog;
    private TextView verifyStep;
    private ProgressBar verifySpinner;
    private ImageView verifyDone;
    private TextView loginError;

    private final Handler stepHandler = new Handler();
    private final Runnable[] stepAdvance = new Runnable[2];
    private boolean verifying = false;

    // Permissions

    private static final String PREFS_NAME = "com.xlox.prefs";
    private static final String PREF_PERMISSIONS_GRANTED = "permissions_granted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        prefs = new Prefs(this);

        textUsername = findViewById(R.id.userkey);
        btnLogin = findViewById(R.id.login);
        premium = findViewById(R.id.premium);
        loginCard = findViewById(R.id.loginCard);
        logo = findViewById(R.id.logo);
        eyeToggle = findViewById(R.id.eyeToggle);
        pasteKey = findViewById(R.id.pasteKey);
        loginError = findViewById(R.id.loginError);

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
            if (verifying) return;
            String userKey = textUsername.getText().toString().trim();
            if (userKey.isEmpty()) {
                showLoginError(getString(R.string.key_hint));
                return;
            }
            if (iskokofm()) {
                showLoginError("VPN detected — switch it off and try again");
                return;
            }
            if (!hasNetwork()) {
                showLoginError("No internet connection — connect and try again");
                return;
            }

            prefs.setSt(USER, userKey);
            hideKeyboard();
            Login(this, userKey);
        });

        textUsername.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick();
                return true;
            }
            return false;
        });

        if (textUsername != null) {
            textUsername.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) clearLoginError();
            });
        }
    }

    /** Gentle entrance: the mark settles, then the card rises into place. */
    private void startIntroAnimation() {
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setTranslationY(-140f);
            logo.animate().alpha(1f).translationY(0f).setDuration(900)
                    .setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
        if (premium != null) {
            premium.setAlpha(0f);
            premium.setTranslationY(90f);
            premium.animate().alpha(1f).translationY(0f).setDuration(900)
                    .setStartDelay(120).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
        if (loginCard != null) {
            loginCard.setAlpha(0f);
            loginCard.setTranslationY(120f);
            loginCard.animate().alpha(1f).translationY(0f).setDuration(1000)
                    .setStartDelay(200).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        }
    }

    /* ------------------------------------------------------ key verification */

    /**
     * The check runs inside a small dialog: a spinner ring and one line that
     * moves from "talking to the panel" to "binding this device" to "reading
     * your subscription", then a tick before the next screen opens.
     */
    private void showVerifying(String userKey) {
        verifying = true;
        clearLoginError();

        if (verifyDialog == null) {
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_verify);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setDimAmount(0.32f);
            }
            verifyDialog = dialog;
            verifyStep = dialog.findViewById(R.id.verifyStep);
            verifySpinner = dialog.findViewById(R.id.verifySpinner);
            verifyDone = dialog.findViewById(R.id.verifyDone);
        }

        TextView masked = verifyDialog.findViewById(R.id.verifyKey);
        if (masked != null) masked.setText(maskKey(userKey));
        if (verifyStep != null) verifyStep.setText(R.string.verify_step_panel);
        if (verifySpinner != null) verifySpinner.setVisibility(View.VISIBLE);
        if (verifyDone != null) verifyDone.setVisibility(View.GONE);

        if (!isFinishing() && !verifyDialog.isShowing()) {
            try {
                verifyDialog.show();
            } catch (Throwable t) {
                verifying = false;
                return;
            }
        }

        stepAdvance[0] = () -> setVerifyStep(R.string.verify_step_device);
        stepAdvance[1] = () -> setVerifyStep(R.string.verify_step_license);
        stepHandler.postDelayed(stepAdvance[0], 900);
        stepHandler.postDelayed(stepAdvance[1], 1800);
    }

    private void setVerifyStep(int resId) {
        if (verifyStep == null || !verifying) return;
        verifyStep.animate().alpha(0f).setDuration(110).withEndAction(() -> {
            if (verifyStep == null) return;
            verifyStep.setText(resId);
            verifyStep.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    /** Success beat: the tick lands, then the dialog closes itself. */
    private void showVerified() {
        if (verifyStep != null) verifyStep.setText(R.string.verify_step_ready);
        if (verifySpinner != null) verifySpinner.setVisibility(View.GONE);
        if (verifyDone != null) {
            verifyDone.setVisibility(View.VISIBLE);
            verifyDone.setScaleX(0.6f);
            verifyDone.setScaleY(0.6f);
            verifyDone.animate().scaleX(1f).scaleY(1f).setDuration(280).start();
        }
    }

    private void hideVerifying() {
        verifying = false;
        stepHandler.removeCallbacks(stepAdvance[0]);
        stepHandler.removeCallbacks(stepAdvance[1]);
        if (verifyDialog != null && verifyDialog.isShowing()) {
            try {
                verifyDialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Keys are quoted back masked; the full key is never re-printed. */
    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "";
        int show = Math.min(4, key.length() / 4);
        if (show == 0) return "••••";
        return key.substring(0, show) + " •••• " + key.substring(key.length() - show);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && textUsername != null) {
                imm.hideSoftInputFromWindow(textUsername.getWindowToken(), 0);
                textUsername.clearFocus();
            }
        } catch (Throwable ignored) {
        }
    }

    private void Login(final Context m_Context, final String userKey) {
        showVerifying(userKey);
        if (btnLogin != null) {
            btnLogin.setEnabled(false);
            btnLogin.setText(R.string.checking_key);
        }

        Handler loginHandler = new Handler(msg -> {
            if (btnLogin != null) {
                btnLogin.setEnabled(true);
                btnLogin.setText(R.string.continue_label);
                btnLogin.setTextColor(ContextCompat.getColor(this, R.color.on_action));
            }
            if (msg.what == 0) {
                showVerified();
                // let the tick settle, then hand over to the startup gate once
                // more (it is instant when the lib is already current)
                new Handler().postDelayed(() -> {
                    hideVerifying();
                    Intent i = new Intent(m_Context, SplashActivity.class);
                    i.putExtra(SplashActivity.EXTRA_NEXT, SplashActivity.NEXT_MAIN);
                    startActivity(i);
                    finish();
                }, 700);
            } else {
                hideVerifying();
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

    /** Inline, under the field: a rejected key stays visible until corrected. */
    private void showLoginError(String message) {
        if (isFinishing()) return;
        String text = message == null || message.trim().isEmpty()
                ? "The key could not be verified"
                : message.trim();
        if (loginError == null) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            return;
        }
        loginError.setText(text);
        loginError.setVisibility(View.VISIBLE);
        loginError.setAlpha(0f);
        loginError.setTranslationY(-4f);
        loginError.animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    private void clearLoginError() {
        if (loginError == null) return;
        loginError.setVisibility(View.GONE);
        loginError.setText("");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stepHandler.removeCallbacksAndMessages(null);
        if (verifyDialog != null && verifyDialog.isShowing()) {
            try {
                verifyDialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * The native panel call. Package-visible so the main screen can re-check a
     * licence before launching instead of trusting whatever is cached.
     */
    static native String Check(Context mContext, String userKey);
}
