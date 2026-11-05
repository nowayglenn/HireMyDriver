package com.example.hiremydriver;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class DriverRegistrationActivity extends RegistrationStepActivity {

    private TextInputLayout layoutFullName;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutMobile;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirmPassword;

    private TextInputEditText edtFullName;
    private TextInputEditText edtEmail;
    private TextInputEditText edtMobile;
    private TextInputEditText edtPassword;
    private TextInputEditText edtConfirmPassword;

    private CheckBox chkTerms;
    private TextView txtTermsLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_registration);

        // Find Layouts
        layoutFullName = findViewById(R.id.layoutFullName);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutMobile = findViewById(R.id.layoutMobile);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword);

        // Find Edit Texts
        edtFullName = findViewById(R.id.edtFullName);
        edtEmail = findViewById(R.id.edtEmail);
        edtMobile = findViewById(R.id.edtMobile);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);

        chkTerms = findViewById(R.id.chkTerms);
        txtTermsLink = findViewById(R.id.txtTermsLink);

        ImageButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnNext = findViewById(R.id.btnNext);
        TextView txtLogin = findViewById(R.id.txtLogin);

        setupClearError(edtFullName, layoutFullName);
        setupClearError(edtEmail, layoutEmail);
        setupClearError(edtMobile, layoutMobile);
        setupClearError(edtPassword, layoutPassword);
        setupClearError(edtConfirmPassword, layoutConfirmPassword);
        renderDraft();
        if (getEditing()) btnNext.setText("Save changes");
        chkTerms.setOnCheckedChangeListener((button, checked) -> {
            if (checked) findViewById(R.id.txtTermsError).setVisibility(View.GONE);
        });

        if (txtTermsLink != null) {
            txtTermsLink.setOnClickListener(v -> {
                Intent intent = new Intent(DriverRegistrationActivity.this, TermsConditionsActivity.class);
                startActivity(intent);
            });
        }

        btnBack.setOnClickListener(v -> backToPrevious());

        btnNext.setOnClickListener(v -> {
            boolean isValid = true;

            String name = edtFullName.getText() != null ? edtFullName.getText().toString().trim() : "";
            String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
            String mobile = edtMobile.getText() != null ? edtMobile.getText().toString().trim() : "";
            String password = edtPassword.getText() != null ? edtPassword.getText().toString() : "";
            String confirmPassword = edtConfirmPassword.getText() != null ? edtConfirmPassword.getText().toString() : "";

            if (name.isEmpty()) {
                layoutFullName.setError("Full name is required");
                isValid = false;
            } else {
                layoutFullName.setError(null);
            }

            if (email.isEmpty()) {
                layoutEmail.setError("Email is required");
                isValid = false;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layoutEmail.setError("Please enter a valid email address");
                isValid = false;
            } else {
                layoutEmail.setError(null);
            }

            if (mobile.isEmpty()) {
                layoutMobile.setError("Mobile number is required");
                isValid = false;
            } else {
                layoutMobile.setError(null);
            }

            if (password.isEmpty()) {
                layoutPassword.setError("Password is required");
                isValid = false;
            } else if (password.length() < 8) {
                layoutPassword.setError("Password must be at least 8 characters");
                isValid = false;
            } else {
                layoutPassword.setError(null);
            }

            if (confirmPassword.isEmpty()) {
                layoutConfirmPassword.setError("Please confirm your password");
                isValid = false;
            } else if (!password.equals(confirmPassword)) {
                layoutConfirmPassword.setError("Passwords do not match");
                isValid = false;
            } else {
                layoutConfirmPassword.setError(null);
            }

            if (chkTerms != null && !chkTerms.isChecked()) {
                findViewById(R.id.txtTermsError).setVisibility(View.VISIBLE);
                isValid = false;
            } else {
                findViewById(R.id.txtTermsError).setVisibility(View.GONE);
            }

            if (!isValid) {
                TextInputLayout[] fields = {layoutFullName, layoutEmail, layoutMobile, layoutPassword, layoutConfirmPassword};
                for (TextInputLayout field : fields) {
                    if (field.getError() != null && field.getEditText() != null) {
                        field.getEditText().requestFocus();
                        break;
                    }
                }
                Toast.makeText(this, "Please complete all fields correctly", Toast.LENGTH_SHORT).show();
                return;
            }

            continueTo(PersonalInfoActivity.class);
        });

        txtLogin.setOnClickListener(v -> {
            Intent intent = new Intent(DriverRegistrationActivity.this, DriverLoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void setupClearError(TextInputEditText editText, TextInputLayout layout) {
        if (editText == null || layout == null) return;
        android.content.res.ColorStateList red = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(198, 40, 40));
        layout.setErrorTextColor(red);
        layout.setBoxStrokeErrorColor(red);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    layout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override protected void captureDraft() {
        getDraft().putString("name", edtFullName.getText().toString().trim());
        getDraft().putString("email", edtEmail.getText().toString().trim());
        getDraft().putString("mobile", edtMobile.getText().toString().trim());
        getDraft().putString("password", edtPassword.getText().toString());
        getDraft().putString("confirmPassword", edtConfirmPassword.getText().toString());
        getDraft().putBoolean("terms", chkTerms.isChecked());
    }

    @Override protected void renderDraft() {
        edtFullName.setText(getDraft().getString("name", ""));
        edtEmail.setText(getDraft().getString("email", ""));
        edtMobile.setText(getDraft().getString("mobile", ""));
        edtPassword.setText(getDraft().getString("password", ""));
        edtConfirmPassword.setText(getDraft().getString("confirmPassword", ""));
        chkTerms.setChecked(getDraft().getBoolean("terms", false));
    }
}
