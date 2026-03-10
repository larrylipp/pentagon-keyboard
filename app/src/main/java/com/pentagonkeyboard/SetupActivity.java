package com.pentagonkeyboard;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SetupActivity extends AppCompatActivity {

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        tvStatus = findViewById(R.id.tv_status);
        Button btnEnable   = findViewById(R.id.btn_enable);
        Button btnSelect   = findViewById(R.id.btn_select);
        Button btnSettings = findViewById(R.id.btn_settings);

        btnEnable.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        btnSelect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        btnSettings.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvStatus.setVisibility(isKeyboardEnabled() ? View.VISIBLE : View.GONE);
    }

    private boolean isKeyboardEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        return enabled != null && enabled.contains(getPackageName());
    }
}
