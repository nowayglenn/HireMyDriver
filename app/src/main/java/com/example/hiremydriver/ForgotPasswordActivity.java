package com.example.hiremydriver;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ForgotPasswordActivity extends BaseActivity {

    public static final String EXTRA_DRIVER_FLOW = "recovery_driver_flow";
    public static final String EXTRA_EMAIL = "recovery_email";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_forgot_password);

        ImageButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);
        TextView txtLogin = findViewById(R.id.txtLogin);
        TextView txtAccountLabel = findViewById(R.id.txtAccountLabel);
        TextView txtSubtitle = findViewById(R.id.txtSubtitle);
        TextView txtResetStatus = findViewById(R.id.txtResetStatus);
        TextInputLayout emailLayout = findViewById(R.id.emailLayout);
        TextInputEditText edtEmail = findViewById(R.id.edtEmail);

        if (getIntent().getBooleanExtra(EXTRA_DRIVER_FLOW, false)) {
            txtAccountLabel.setText(R.string.recovery_driver_label);
            txtSubtitle.setText(R.string.recovery_driver_description);
        }

        if (savedInstanceState == null) {
            String email = getIntent().getStringExtra(EXTRA_EMAIL);
            if (email != null && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                edtEmail.setText(email.trim());
            }
        }

        // Finish returns to the customer or driver login that opened this screen.
        btnBack.setOnClickListener(v -> finish());
        txtLogin.setOnClickListener(v -> finish());

        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                emailLayout.setError(null);
                txtResetStatus.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });

        btnContinue.setOnClickListener(v -> requestPasswordReset(emailLayout, edtEmail, txtResetStatus));
        edtEmail.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnContinue.performClick();
                return true;
            }
            return false;
        });
    }

    private void requestPasswordReset(TextInputLayout emailLayout, TextInputEditText edtEmail,
                                      TextView txtResetStatus) {
        String email = edtEmail.getText() == null ? "" : edtEmail.getText().toString().trim();
        if (email.isEmpty()) {
            emailLayout.setError(getString(R.string.recovery_email_required));
            edtEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.recovery_email_invalid));
            edtEmail.requestFocus();
            return;
        }

        emailLayout.setError(null);
        androidx.core.view.WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(edtEmail);
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
        }
        edtEmail.clearFocus();

        if (getIntent().getBooleanExtra(EXTRA_DRIVER_FLOW, false)) {
            txtResetStatus.setText(R.string.recovery_unavailable);
            txtResetStatus.setVisibility(View.VISIBLE);
            return;
        }
        MaterialButton button = findViewById(R.id.btnContinue);
        button.setEnabled(false);
        button.setText(R.string.customer_reset_sending);
        edtEmail.setEnabled(false);
        CustomerAuth.resetPassword(this, email).addOnCompleteListener(task -> {
            if (isDestroyed() || isFinishing()) return;
            button.setEnabled(true);
            button.setText(R.string.recovery_send_link);
            edtEmail.setEnabled(true);
            Exception error = task.getException();
            boolean missingAccount = error instanceof com.google.firebase.auth.FirebaseAuthException &&
                    "ERROR_USER_NOT_FOUND".equals(((com.google.firebase.auth.FirebaseAuthException) error).getErrorCode());
            txtResetStatus.setText(task.isSuccessful() || missingAccount
                    ? getString(R.string.customer_reset_sent) : CustomerAuth.message(error));
            txtResetStatus.setVisibility(View.VISIBLE);
        });
    }
}
