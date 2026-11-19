package com.neymis.tail;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CommandEngine.Host {

    private SharedPreferences prefs;
    private CommandEngine engine;
    private TextView output;
    private ScrollView scroll;
    private EditText input;
    private TextView hint;
    private int historyIndex;
    private String historyDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("neymis_tail", MODE_PRIVATE);
        applyThemeOnly(prefs.getInt("theme", 0));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        output = findViewById(R.id.output);
        scroll = findViewById(R.id.outputScroll);
        input = findViewById(R.id.input);
        applyColor(prefs.getString("color", "0f"));
        output.setTextSize(prefs.getInt("font_size", 13));

        engine = new CommandEngine(this, Environment.getExternalStorageDirectory());
        print("Neymis Tail 1.0 — консоль для Android");
        print("Введи `help`, чтобы увидеть команды.");
        print("");

        View clear = findViewById(R.id.clearButton);
        clear.setOnClickListener(v -> output.setText(""));

        View enter = findViewById(R.id.enterButton);
        enter.setOnClickListener(v -> submit());

        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit();
                return true;
            }
            return false;
        });

        input.setOnKeyListener((view, keyCode, event) -> {
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            List<String> history = engine.history();
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !history.isEmpty()) {
                if (historyIndex == 0) historyDraft = input.getText().toString();
                historyIndex = Math.min(historyIndex + 1, history.size());
                input.setText(history.get(history.size() - historyIndex));
                input.setSelection(input.length());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && historyIndex > 0) {
                historyIndex--;
                input.setText(historyIndex == 0 ? historyDraft
                        : history.get(history.size() - historyIndex));
                input.setSelection(input.length());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                List<String> variants = engine.completions(input.getText().toString());
                if (variants.size() == 1) {
                    input.setText(variants.get(0));
                    input.setSelection(input.length());
                } else if (!variants.isEmpty()) {
                    print("  " + String.join("   ", variants));
                }
                return true;
            }
            return false;
        });

        hint = findViewById(R.id.hint);
        if (!prefs.getBoolean("hint_shown", false)) {
            hint.setVisibility(View.VISIBLE);
            hint.setOnClickListener(v -> hideHint());
        }

        input.requestFocus();
        input.post(() -> {
            android.view.inputmethod.InputMethodManager manager =
                    (android.view.inputmethod.InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(input,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });

        requestStorageAccess();
        requestNotificationPermission();

        String scheduled = getIntent().getStringExtra("run_cmd");
        if (scheduled != null && !scheduled.isEmpty()) {
            print("$ " + scheduled);
            String result = engine.run(scheduled);
            if (!result.isEmpty()) print(result);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    private void hideHint() {
        if (hint == null) return;
        hint.setVisibility(View.GONE);
        prefs.edit().putBoolean("hint_shown", true).apply();
    }

    private void submit() {
        String line = input.getText().toString();
        input.setText("");
        historyIndex = 0;
        hideHint();
        if (line.trim().isEmpty()) return;
        print("$ " + line);
        String result = engine.run(line);
        if (!result.isEmpty()) print(result);
        print("");
    }

    private void print(String text) {
        output.append(text.replace("\t", "    "));
        output.append("\n");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public Context context() {
        return this;
    }

    @Override
    public void share(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Поделиться"));
    }

    @Override
    public void open(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Нет приложения для этого файла");
        }
    }

    @Override
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void clear() {
        runOnUiThread(() -> output.setText(""));
    }

    private void applyThemeOnly(int theme) {
        if (theme == 1) setTheme(R.style.AppTheme_Light);
        else if (theme == 2) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme);
    }

    @Override
    public void applyTheme(int theme) {
        prefs.edit().putInt("theme", theme).apply();
        recreate();
    }

    private void applyColor(String code) {
        int color;
        switch (code == null ? "" : code) {
            case "0a" -> color = 0xFF69F0AE;
            case "0c" -> color = 0xFFFF8A80;
            case "0e" -> color = 0xFFB0BEC5;
            case "0b" -> color = 0xFF80DEEA;
            default -> color = 0xFFE8EAF6;
        }
        output.setTextColor(color);
    }

    @Override
    public void setColor(String code) {
        prefs.edit().putString("color", code).apply();
        runOnUiThread(() -> applyColor(code));
    }

    @Override
    public void exit() {
        runOnUiThread(this::finish);
    }

    @Override
    public String pref(String key, String def) {
        return prefs.getString(key, def);
    }

    @Override
    public void setPref(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void stream(java.util.List<String> lines) {
        runOnUiThread(() -> new Thread(() -> {
            for (String line : lines) {
                runOnUiThread(() -> print(line));
                try {
                    Thread.sleep(18);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "neymis-stream").start());
    }

    @Override
    public void setFontSize(int size) {
        runOnUiThread(() -> output.setTextSize(size));
    }

    @Override
    public void showQr(android.graphics.Bitmap bitmap) {
        runOnUiThread(() -> {
            ImageView image = new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            new AlertDialog.Builder(this).setTitle("QR-код").setView(image)
                    .setPositiveButton("OK", null).show();
        });
    }

    @Override
    public void notify(String title, String text) {
        final String channelId = "neymis_tail";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(channelId) == null) {
                NotificationChannel channel = new NotificationChannel(channelId,
                        "Neymis Tail", NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void schedule(long delayMs, String command) {
        Intent intent = new Intent(this, TaskReceiver.class);
        intent.putExtra("command", command);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(this,
                (int) System.currentTimeMillis(), intent, flags);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            long trigger = System.currentTimeMillis() + delayMs;
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, trigger, pending);
            }
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }
}
