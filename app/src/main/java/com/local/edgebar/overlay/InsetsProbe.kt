package com.local.edgebar.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.WindowInsets

/**
 * A 1x1, non-touchable, non-focusable view whose only job is to receive WindowInsets
 * dispatches. `isVisible()` reports whether the IME / system bars are on screen even
 * when they do not overlap this window. Purely event-driven: the system calls us when
 * insets change; nothing polls.
 */
@SuppressLint("ViewConstructor")
class InsetsProbe(
    context: Context,
    private val onChange: (keyboardVisible: Boolean, fullscreen: Boolean) -> Unit,
) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnApplyWindowInsetsListener { _, insets ->
            val ime = insets.isVisible(WindowInsets.Type.ime())
            val status = insets.isVisible(WindowInsets.Type.statusBars())
            val nav = insets.isVisible(WindowInsets.Type.navigationBars())
            // Immersive apps (video players, games) hide both bars.
            onChange(ime, !status && !nav)
            insets
        }
    }
}
