package com.example.hiremydriver

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PendingApprovalDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = layoutInflater.inflate(R.layout.dialog_pending_approval, null)
        content.findViewById<View>(R.id.btnVerificationContinue).setOnClickListener { dismiss() }
        return MaterialAlertDialogBuilder(requireContext())
            .setView(content)
            .setBackground(ColorDrawable(Color.TRANSPARENT))
            .create()
            .apply { setCanceledOnTouchOutside(false) }
    }

    companion object {
        const val TAG = "pending_approval"
    }
}
