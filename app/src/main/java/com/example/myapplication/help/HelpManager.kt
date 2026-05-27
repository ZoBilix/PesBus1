package com.example.myapplication.help

import android.content.Context
import android.view.LayoutInflater
import com.example.myapplication.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class HelpManager(private val context: Context) {
    /**
     * Показывает окно-инструкцию в виде BottomSheet
     */
    fun showInstruction() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_help, null)
        dialog.setContentView(view)
        dialog.show()
    }
}
