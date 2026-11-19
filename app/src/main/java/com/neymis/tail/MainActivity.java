package com.neymis.tail;

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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CommandEngine.Host {

    private SharedPreferences prefs;
    private CommandEngine engine;
    private TextView output;
    private ScrollView scroll;
    private EditText input;
    private int historyIndex;
    private String historyDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("neymis_tail", MODE_PRIVATE);
        applyTheme(prefs.getInt("theme", 0));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        output = findViewById(R.id.output);
        scroll = findViewById(R.id.outputScroll);
        input = findViewById(R.id.input);

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

        requestStorageAccess();
    }

    private void submit() {
        String line = input.getText().toString();
        input.setText("");
        historyIndex = 0;
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

    @Override
    public void applyTheme(int theme) {
        prefs.edit().putInt("theme", theme).apply();
        recreate();
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
