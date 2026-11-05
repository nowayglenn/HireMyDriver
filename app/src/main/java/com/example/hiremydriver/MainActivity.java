package com.example.hiremydriver;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.Context;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends BaseActivity {
    private boolean openingRole = false;

    @Override protected void onResume() {
        super.onResume();
        openingRole = false;
        findViewById(R.id.btnGetStarted).setEnabled(true);
        findViewById(R.id.btnDriver).setEnabled(true);
    }

    private void openRole(Class<?> destination) {
        if (openingRole || isFinishing()) return;
        openingRole = true;
        findViewById(R.id.btnGetStarted).setEnabled(false);
        findViewById(R.id.btnDriver).setEnabled(false);
        startActivity(new Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
        overridePendingTransition(0, 0);
    }

    @Override
    protected boolean backgroundBehindSystemBars() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // I'm a Customer
        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);

        btnGetStarted.setOnClickListener(v -> {
            openRole(LoginActivity.class);
        });

        // I'm a Driver
        MaterialButton btnDriver= findViewById(R.id.btnDriver);

        btnDriver.setOnClickListener(v -> {

            openRole(DriverLoginActivity.class);
        });
    }
}
