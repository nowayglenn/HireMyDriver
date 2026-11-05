package com.example.hiremydriver

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner

internal object ScreenOwner {
    fun from(context: Context): LifecycleOwner? {
        var current = context
        while (current !is LifecycleOwner && current is ContextWrapper && current.baseContext !== current) current = current.baseContext
        return current as? LifecycleOwner
    }
}
