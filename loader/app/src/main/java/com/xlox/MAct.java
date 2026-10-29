package com.xlox;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xlox.utils.AppManager;
import com.xlox.utils.Downtwo;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import net_62v.external.MetaActivationManager;
import net_62v.external.MetaActivityManager;
import net_62v.external.MetaStorageManager;

@Obfuscate
public class MAct extends AppCompatActivity {

    private static native String exdate();

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static final String PKG_BGMI = "com.pubg.imobile";
    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_VERSION_KEY = "version";

    // UI
    private ProgressBar downloadProgressBar;
    private TextView downloadStatusText;
    private TextView percentText;
    private View downloadBox;
    private View restartButton;
    private ProgressBar loadingCursor;
    private TextView appVersionText;
    
    private TextView startText;
    private ImageView actionIcon;
    private View startBgmi;
    private View startFB;
    private View fbCard;
    private View statusToggle;
    private View statusLoader;
    private ImageView statusErrorIcon;
    private TextView statusToggleText;

    private TextView gameName;
    private TextView packageName;
    private TextView gameStatus;
    private TextView subscriptionStatus;
    private TextView expiryValue;
    private TextView timeRemaining;
    private TextView deviceValue;
    private TextView deviceStatus;

    private TextView daysValue;
    private TextView hoursValue;
    private TextView minutesValue;
    private TextView secondsValue;

    private View obbButton;
    private boolean isObbCopied = false;
    private boolean isGameRunning = false;
    private boolean isGameInstalled = false;
    private boolean isUpdateAvailable = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        setContentView(R.layout.activity_main);

        checkPermissions();

        // Download UI
        downloadBox = findViewById(R.id.downloadBox);
        downloadProgressBar = findViewById(R.id.downloadProgressBar);
        downloadStatusText = findViewById(R.id.downloadStatusText);
        percentText = findViewById(R.id.percentText);
        restartButton = findViewById(R.id.restartButton);
        loadingCursor = findViewById(R.id.loadingCursor);
        appVersionText = findViewById(R.id.appVersionText);

        // Action UI
        startText = findViewById(R.id.startText);
        actionIcon = findViewById(R.id.actionIcon);
        startBgmi = findViewById(R.id.startBgmi);
        startFB = findViewById(R.id.startFB);
        fbCard = findViewById(R.id.fbCard);

        gameName = findViewById(R.id.gameName);
        packageName = findViewById(R.id.packageName);
        gameStatus = findViewById(R.id.gameStatus);
        statusToggle = findViewById(R.id.statusToggle);
        statusLoader = findViewById(R.id.statusLoader);
        statusErrorIcon = findViewById(R.id.statusErrorIcon);
        statusToggleText = findViewById(R.id.statusToggleText);
        subscriptionStatus = findViewById(R.id.subscriptionStatus);
        expiryValue = findViewById(R.id.expiryValue);
        timeRemaining = findViewById(R.id.timeRemaining);
        deviceValue = findViewById(R.id.deviceValue);
        deviceStatus = findViewById(R.id.deviceStatus);
        if (deviceValue != null) {
            deviceValue.setText(Build.MODEL == null ? "Android device" : Build.MODEL);
        }

        obbButton = findViewById(R.id.obbButton);

        daysValue = findViewById(R.id.daysBox).findViewById(R.id.timeValue);
        hoursValue = findViewById(R.id.hoursBox).findViewById(R.id.timeValue);
        minutesValue = findViewById(R.id.minutesBox).findViewById(R.id.timeValue);
        secondsValue = findViewById(R.id.secondsBox).findViewById(R.id.timeValue);
        ((TextView) findViewById(R.id.daysBox).findViewById(R.id.timeLabel)).setText("DAYS");
        ((TextView) findViewById(R.id.hoursBox).findViewById(R.id.timeLabel)).setText("HOURS");
        ((TextView) findViewById(R.id.minutesBox).findViewById(R.id.timeLabel)).setText("MINUTES");
        ((TextView) findViewById(R.id.secondsBox).findViewById(R.id.timeLabel)).setText("SECONDS");
        setKeyTimeUnavailable();
        startKeyCountdown();
        startPageAnimation();

        checkObbApplied();
        updateGameStatus();
        updateVersionDisplay();

        if (obbButton != null) {
            obbButton.setOnClickListener(v -> openExternalUrl("https://www.facebook.com/"));
        }

        if (restartButton != null) {
            restartButton.setOnClickListener(v -> restartApp());
        }

        if (startBgmi != null) {
            startBgmi.setOnClickListener(v -> {
                if (isUpdateAvailable) {
                    startDownload();
                } else if (!isGameInstalled) {
                    installGame();
                } else if (isGameRunning) {
                    stopGame();
                } else {
                    handleLaunchFlow();
                }
            });
        }

        if (startFB != null) {
            startFB.setOnClickListener(v -> openExternalUrl("https://twitter.com/"));
        }

        // Start Workflow: Update Check -> Download Files (if needed)
        startWorkflow();
    }

    private void startPageAnimation() {
        View logoGlow = findViewById(R.id.logoGlow);
        View mainLogo = findViewById(R.id.mainLogo);
        if (mainLogo != null) {
            ObjectAnimator logoFloat = ObjectAnimator.ofFloat(mainLogo, View.TRANSLATION_Y, 0f, -6f, 0f);
            logoFloat.setDuration(2600);
            logoFloat.setRepeatCount(ObjectAnimator.INFINITE);
            logoFloat.start();
        }
        if (logoGlow != null) {
            ObjectAnimator glowRotation = ObjectAnimator.ofFloat(logoGlow, View.ROTATION, 0f, 360f);
            glowRotation.setDuration(7000);
            glowRotation.setInterpolator(new LinearInterpolator());
            glowRotation.setRepeatCount(ObjectAnimator.INFINITE);
            glowRotation.start();
            logoGlow.animate().translationY(-5f).setDuration(1300)
                    .withEndAction(() -> logoGlow.animate().translationY(0f).setDuration(1300).start()).start();
        }
        View[] views = {findViewById(R.id.brandHeader), findViewById(R.id.mainCard),
                findViewById(R.id.statusToggle), findViewById(R.id.keyTimeRow), findViewById(R.id.fbCard)};
        long delay = 0;
        for (View view : views) {
            if (view != null) {
                view.setAlpha(0f);
                view.setTranslationY(34f);
                view.animate().alpha(1f).translationY(0f).setDuration(560).setStartDelay(delay).start();
                delay += 110;
            }
        }
    }

    private void setKeyTimeUnavailable() {
        if (daysValue != null) daysValue.setText("--");
        if (hoursValue != null) hoursValue.setText("--");
        if (minutesValue != null) minutesValue.setText("--");
        if (secondsValue != null) secondsValue.setText("--");
    }

    private void startKeyCountdown() {
        final String expiry = exdate();
        if (expiry == null || expiry.trim().isEmpty() || "NULL".equalsIgnoreCase(expiry.trim())) {
            if (expiryValue != null) expiryValue.setText("--");
            if (timeRemaining != null) timeRemaining.setText("--");
            return;
        }

        final long expiryMillis = parseExpiryMillis(expiry.trim());
        if (expiryValue != null) {
            if (expiryMillis > 0L) {
                expiryValue.setText(new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(new Date(expiryMillis)));
            } else {
                expiryValue.setText(expiry.trim());
            }
        }
        if (expiryMillis <= 0L) {
            if (timeRemaining != null) timeRemaining.setText("--");
            return;
        }

        final Handler timerHandler = new Handler(getMainLooper());
        final Runnable timer = new Runnable() {
            @Override public void run() {
                long remaining = expiryMillis - System.currentTimeMillis();
                if (remaining <= 0L) {
                    setTimeValues(0, 0, 0, 0);
                    if (subscriptionStatus != null) {
                        subscriptionStatus.setText("Expired");
                        subscriptionStatus.setTextColor(Color.parseColor("#FF5252"));
                    }
                    if (timeRemaining != null) timeRemaining.setText("Expired");
                    return;
                }
                if (subscriptionStatus != null) {
                    subscriptionStatus.setText("Active");
                    subscriptionStatus.setTextColor(Color.parseColor("#25C779"));
                }
                long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
                long days = totalSeconds / 86400L;
                long hours = (totalSeconds % 86400L) / 3600L;
                long minutes = (totalSeconds % 3600L) / 60L;
                long seconds = totalSeconds % 60L;
                setTimeValues(days, hours, minutes, seconds);
                timerHandler.postDelayed(this, 1000L);
            }
        };
        timer.run();
    }

    private long parseExpiryMillis(String value) {
        try {
            long numeric = Long.parseLong(value);
            return numeric < 100000000000L ? numeric * 1000L : numeric;
        } catch (NumberFormatException ignored) {}

        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "MMM dd, yyyy HH:mm:ss"};
        for (String format : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
                parser.setLenient(false);
                Date parsed = parser.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {}
        }
        return -1L;
    }

    @SuppressLint("SetTextI18n")
    private void setTimeValues(long days, long hours, long minutes, long seconds) {
        if (daysValue != null) daysValue.setText(String.format(Locale.US, "%02d", days));
        if (hoursValue != null) hoursValue.setText(String.format(Locale.US, "%02d", hours));
        if (minutesValue != null) minutesValue.setText(String.format(Locale.US, "%02d", minutes));
        if (secondsValue != null) secondsValue.setText(String.format(Locale.US, "%02d", seconds));
        if (timeRemaining != null) {
            timeRemaining.setText(String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes));
        }
    }

    private void updateVersionDisplay() {
        String localVersion = Downtwo.installedVersion(this);
        if (appVersionText != null) {
            boolean installed = !"0.0".equals(localVersion);
            appVersionText.setText(installed ? "Version " + localVersion : "Version —");
        }
    }

    private void startWorkflow() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.GONE);
            if (restartButton != null) restartButton.setVisibility(View.VISIBLE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
        });
        isUpdateAvailable = false;
        checkForUpdates(false);
    }

    /**
     * Asks the panel which lib version is live.
     * @param announceOnly show a toast when an update is found
     */
    private void checkForUpdates(final boolean announceOnly) {
        Downtwo checker = new Downtwo(this, result -> {
            switch (result.status) {
                case UPDATE_AVAILABLE:
                case ALREADY_NOTIFIED: { // still offer the button, just no second toast
                    final boolean fresh = result.status == Downtwo.Status.UPDATE_AVAILABLE;
                    isUpdateAvailable = true;
                    runOnUiThread(() -> {
                        updateButtonState(isGameRunning);
                        setLaunchButtonText("UPDATE AVAILABLE");
                        if (announceOnly && fresh && !isFinishing()) {
                            Toast.makeText(MAct.this,
                                    "New lib v" + result.version + " is available — tap UPDATE to install",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                }
                case ERROR:
                    // Unreachable update server must never block the loader.
                    Log.w("MAct", "update check failed: " + result.message);
                    isUpdateAvailable = false;
                    runOnUiThread(() -> {
                        updateButtonState(isGameRunning);
                        if (announceOnly && !isFinishing()) {
                            Toast.makeText(MAct.this, "Update check failed: " + result.message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                default:
                    isUpdateAvailable = false;
                    runOnUiThread(() -> updateButtonState(isGameRunning));
                    break;
            }
        });
        checker.execute(Downtwo.Link(), Downtwo.MODE_CHECK);
    }

    private void startDownload() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.VISIBLE);
            setLaunchButtonText("UPDATING...");
            if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
            if (percentText != null) percentText.setText("0%");
            if (downloadProgressBar != null) {
                downloadProgressBar.setVisibility(View.VISIBLE);
                downloadProgressBar.setIndeterminate(true);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(0);
            }
            if (restartButton != null) restartButton.setVisibility(View.GONE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.VISIBLE);
        });

        Downtwo task = new Downtwo(this, result -> {
            if (!result.isSuccess()) {
                // Surface the real reason instead of failing silently, and let the
                // user retry by tapping the button again.
                isUpdateAvailable = true;
                final String message = result.message.isEmpty() ? "Update failed" : result.message;
                Log.e("MAct", "update failed: " + message);
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText(message);
                    if (percentText != null) percentText.setText("FAILED");
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setIndeterminate(false);
                        downloadProgressBar.setProgress(0);
                    }
                    if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                    if (restartButton != null) restartButton.setVisibility(View.VISIBLE);
                    setLaunchButtonText("RETRY UPDATE");
                    if (startText != null) {
                        startText.setText("RETRY");
                        startText.setTextColor(Color.parseColor("#FF4444"));
                    }
                    if (!isFinishing()) {
                        Toast.makeText(MAct.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            runOnUiThread(() -> {
                isUpdateAvailable = false;
                if (downloadStatusText != null) downloadStatusText.setText("All process done");
                if (percentText != null) percentText.setText("100%");
                if (downloadProgressBar != null) {
                    downloadProgressBar.setIndeterminate(false);
                    downloadProgressBar.setProgress(100);
                }
                if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                updateVersionDisplay();
                setLaunchButtonText(isGameInstalled ? "LAUNCH GAME" : "INSTALL GAME");
                if (restartButton != null) {
                    restartButton.setVisibility(View.VISIBLE);
                    restartButton.setAlpha(0f);
                    restartButton.animate().alpha(1f).setDuration(500).start();
                }
            });
        });

        task.setProgressListener((percent, stage) -> runOnUiThread(() -> {
            if (downloadStatusText != null && stage != null && !stage.isEmpty()) {
                downloadStatusText.setText(stage);
            }
            if (downloadProgressBar != null) {
                if (percent < 0) {
                    downloadProgressBar.setIndeterminate(true);
                } else {
                    downloadProgressBar.setIndeterminate(false);
                    downloadProgressBar.setMax(100);
                    downloadProgressBar.setProgress(percent);
                }
            }
            if (percentText != null) {
                percentText.setText(percent < 0 ? "…" : percent + "%");
            }
        }));

        task.execute(Downtwo.Link(), Downtwo.MODE_DOWNLOAD);
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Runtime.getRuntime().exit(0);
        }
    }

    private void checkObbApplied() {
        new Thread(() -> {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(PKG_BGMI, 0);
                String gameObb = "main." + info.versionCode + "." + PKG_BGMI + ".obb";
                String destPath = MetaStorageManager.obtainAppExternalStorageDir(PKG_BGMI) + "/Android/obb/" + PKG_BGMI;
                File destFile = new File(destPath, gameObb);
                if (destFile.exists()) {
                    isObbCopied = true;
                    runOnUiThread(() -> { if (obbButton != null) obbButton.setVisibility(View.GONE); });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateGameStatus() {
        isGameInstalled = false;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(PKG_BGMI, 0);
            if (packageInfo != null) {
                isGameInstalled = true;
                if (gameName != null) gameName.setText("BGMI INDIA");
                if (packageName != null) packageName.setText(PKG_BGMI);
                if (gameStatus != null) gameStatus.setText("SAFE");
                if (deviceStatus != null) {
                    deviceStatus.setText("Safe");
                    deviceStatus.setTextColor(Color.parseColor("#315FE5"));
                }
                updateButtonState(false);
            }
        } catch (Exception ignored) {
            isGameInstalled = false;
            if (gameStatus != null) gameStatus.setText("Not Installed");
            if (deviceStatus != null) {
                deviceStatus.setText("Not installed");
                deviceStatus.setTextColor(Color.parseColor("#FF9800"));
            }
            setLaunchButtonText("INSTALL GAME");
            if (startText != null) {
                startText.setText("INSTALL GAME");
                startText.setTextColor(Color.parseColor("#FF8C00"));
            }
            if (actionIcon != null) {
                actionIcon.setImageResource(android.R.drawable.ic_menu_save);
                actionIcon.setColorFilter(Color.parseColor("#FF8C00"));
            }
        }
    }

    private void setLaunchButtonText(String label) {
        if (startText != null) startText.setText(label);
        if (startBgmi instanceof Button) ((Button) startBgmi).setText(label);
    }

    private void updateButtonState(boolean running) {
        isGameRunning = running;
        runOnUiThread(() -> {
            if (running) {
                setLaunchButtonText("STOP GAME");
                if (startText != null) {
                    startText.setText("STOP GAME");
                    startText.setTextColor(Color.parseColor("#FF4444"));
                }
                if (actionIcon != null) {
                    actionIcon.setImageResource(android.R.drawable.ic_delete);
                    actionIcon.setColorFilter(Color.parseColor("#FF4444"));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_ref_stop_button);
                }
                if (downloadBox != null) {
                    downloadBox.setVisibility(View.VISIBLE);
                    if (downloadStatusText != null) downloadStatusText.setText("Game Running...");
                    if (percentText != null) percentText.setText("ACTIVE");
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setIndeterminate(true);
                        downloadProgressBar.setProgress(100);
                    }
                    if (restartButton != null) restartButton.setVisibility(View.GONE);
                }
            } else {
                if (isUpdateAvailable) {
                    setLaunchButtonText("UPDATE AVAILABLE");
                    if (startText != null) {
                        startText.setText("UPDATE AVAILABLE");
                        startText.setTextColor(Color.parseColor("#FF8C00"));
                    }
                } else {
                    setLaunchButtonText("LAUNCH GAME");
                    if (startText != null) {
                        startText.setText("LAUNCH");
                        startText.setTextColor(Color.parseColor("#FF8C00"));
                    }
                }
                if (actionIcon != null) {
                    actionIcon.setImageResource(android.R.drawable.ic_media_play);
                    actionIcon.setColorFilter(Color.parseColor("#FF8C00"));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_orange_action);
                }
                if (downloadBox != null) {
                    new Handler().postDelayed(() -> {
                        if (!isGameRunning && !isUpdateAvailable) downloadBox.setVisibility(View.GONE);
                    }, 1000);
                }
            }
        });
    }

    private void handleLaunchFlow() {
        try {
            if (!MetaActivationManager.getActivationStatus()) {
                showStatusToggle("LAUNCHING FAILED", true);
                return;
            }
        } catch (Exception ignored) {
            showStatusToggle("LAUNCHING FAILED", true);
            return;
        }

        File libFile = new File(getFilesDir(), "loader/libbgmi.so");
        if (!libFile.exists()) {
            startDownload();
            return;
        }

        if (!isObbCopied) {
            copyObbFiles();
        } else {
            showStatusToggle("LAUNCHING BGMI", false);
            new Handler().postDelayed(() -> {
                hideStatusToggle();
                launchGame();
            }, 2500);
        }
    }

    private void showStatusToggle(String message, boolean isError) {
        if (statusToggle == null) return;
        
        runOnUiThread(() -> {
            statusToggle.setVisibility(View.VISIBLE);
            statusToggle.setAlpha(0f);
            statusToggle.animate().alpha(1f).setDuration(300).start();
            
            if (statusToggleText != null) {
                statusToggleText.setText(message);
                statusToggleText.setTextColor(isError ? Color.parseColor("#FF5252") : Color.WHITE);
            }
            
            if (statusLoader != null) {
                statusLoader.setVisibility(isError ? View.GONE : View.VISIBLE);
            }
            
            if (statusErrorIcon != null) {
                statusErrorIcon.setVisibility(isError ? View.VISIBLE : View.GONE);
            }
            
            if (isError) {
                new Handler().postDelayed(this::hideStatusToggle, 3000);
            }
        });
    }

    private void hideStatusToggle() {
        if (statusToggle == null) return;
        
        runOnUiThread(() -> {
            statusToggle.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                statusToggle.setVisibility(View.GONE);
            }).start();
        });
    }

    private void launchGame() {
        try {
            Intent intent = MetaActivityManager.obtainSplashLaunchIntent(PKG_BGMI, this);
            if (intent != null) {
                startActivity(intent);
                updateButtonState(true);
            }
        } catch (Exception e) {
            Log.e("MAct", "Launch error", e);
        }
    }

    private void stopGame() {
        try {
            MetaActivityManager.killAppByPkg(PKG_BGMI);
            updateButtonState(false);
        } catch (Exception e) {
            Toast.makeText(this, "Stop Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void installGame() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + PKG_BGMI));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + PKG_BGMI));
            startActivity(intent);
        }
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e("MAct", "Unable to open social link", e);
        }
    }

    private void copyObbFiles() {
        if (obbButton == null) return;
        
        Button btn = (Button) obbButton;
        btn.setEnabled(false);
        if (downloadBox != null) downloadBox.setVisibility(View.VISIBLE);
        if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
        if (percentText != null) percentText.setText("0%");
        if (downloadProgressBar != null) {
            downloadProgressBar.setIndeterminate(false);
            downloadProgressBar.setMax(100);
            downloadProgressBar.setProgress(0);
        }
        
        AppManager appManager = new AppManager(this);
        appManager.copyObbFolderAsync(PKG_BGMI, new AppManager.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean success) {
                runOnUiThread(() -> {
                    if (success) {
                        isObbCopied = true;
                        btn.setVisibility(View.GONE);
                        if (downloadStatusText != null) downloadStatusText.setText("Facebook access ready");
                        if (percentText != null) percentText.setText("100%");
                        if (downloadProgressBar != null) downloadProgressBar.setProgress(100);
                        new Handler().postDelayed(() -> {
                            if (downloadBox != null) downloadBox.setVisibility(View.GONE);
                        }, 900);
                        Toast.makeText(MAct.this, "OBB Copied Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        btn.setEnabled(true);
                        btn.setText("APPLY OBB");
                        if (downloadBox != null) downloadBox.setVisibility(View.GONE);
                        Toast.makeText(MAct.this, "OBB Copy Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onProgressUpdate(int progress) {
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
                    if (percentText != null) percentText.setText(progress + "%");
                    if (downloadProgressBar != null) downloadProgressBar.setProgress(progress);
                });
            }
        }, false); // false to hide the default dialog
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }
}
