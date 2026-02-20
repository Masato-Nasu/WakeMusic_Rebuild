package jp.masatonasu.wakemusic

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton

/**
 * Optional floating STOP button shown over other apps while the alarm is ringing.
 *
 * - Requires user-granted overlay permission (SYSTEM_ALERT_WINDOW).
 * - If permission is not granted, it does nothing.
 */
object StopOverlay {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    fun canUse(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun show(context: Context) {
        val appCtx = context.applicationContext
        if (!canUse(appCtx)) return
        if (overlayView != null) return

        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = LayoutInflater.from(appCtx).inflate(R.layout.overlay_stop, null, false)

        v.findViewById<ImageButton>(R.id.btnOverlayStop).setOnClickListener {
            val i = Intent(appCtx, AlarmPlayerService::class.java).apply {
                action = AlarmPlayerService.ACTION_STOP
                putExtra(AlarmPlayerService.EXTRA_SOURCE, "overlay")
            }
            // startService is enough; service is already running as foreground in normal cases.
            runCatching { appCtx.startService(i) }
            hide(appCtx)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            // A small, safe default position. Users can still pull notification shade if needed.
            x = 24
            y = 160
        }

        runCatching {
            wm.addView(v, lp)
            overlayView = v
            windowManager = wm
        }
    }

    fun hide(context: Context) {
        val appCtx = context.applicationContext
        val v = overlayView ?: return
        runCatching {
            (windowManager ?: (appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager))
                .removeView(v)
        }
        overlayView = null
        windowManager = null
    }
}
