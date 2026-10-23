package com.romanpanin.advancedalarm.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

/**
 * Работа с edge-to-edge и системными отступами.
 *
 * С Android 15 приложение обязано рисовать под системными панелями, поэтому
 * окно переводится в edge-to-edge, а контент получает отступы безопасной
 * области: системные панели, вырез экрана и клавиатура.
 */
object Insets {

    /** Переводит окно в режим рисования под системными панелями. */
    fun applyEdgeToEdge(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /**
     * Вешает на корневой [view] отступы по безопасной области экрана.
     *
     * @param ime учитывать ли клавиатуру — нужно на экранах с полями ввода.
     */
    fun applySafeArea(view: View, ime: Boolean = false) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        val safeArea = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(safeArea)
            val bottomInset = if (ime) {
                max(safe.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else {
                safe.bottom
            }
            target.updatePadding(
                left = baseLeft + safe.left,
                top = baseTop + safe.top,
                right = baseRight + safe.right,
                bottom = baseBottom + bottomInset
            )
            insets
        }
    }

    /**
     * Отступ только под вырез экрана — для полноэкранного окна блокировки,
     * где системные панели скрыты, но вырез остаётся.
     */
    fun applyCutout(view: View) {
        val baseTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            target.updatePadding(top = baseTop + cutout.top)
            insets
        }
    }
}
