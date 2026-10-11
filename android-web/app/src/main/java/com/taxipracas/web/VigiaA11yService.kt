package com.taxipracas.web

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.webkit.WebView
import android.widget.Toast

// Vigia por Acessibilidade: fotografia por takeScreenshot (sem diálogo de
// gravação, sem ecrã virtual). Para aparelhos onde a MediaProjection morre.
class VigiaA11yService : AccessibilityService() {

    companion object {
        @Volatile var inst: VigiaA11yService? = null
        @Volatile var vigiar = false

        fun ativo(ctx: Context): Boolean {
            return try {
                val s = Settings.Secure.getString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                s.contains(ctx.packageName + "/.VigiaA11yService", true)
            } catch (e: Exception) { false }
        }
    }

    private var handler: Handler? = null

    private val tick = object : Runnable {
        override fun run() {
            try { foto() } catch (t: Throwable) { }
            if (vigiar) handler?.postDelayed(this, CaptureService.INTERVALO_MS)
        }
    }

    override fun onServiceConnected() {
        inst = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        inst = null
        vigiar = false
        try { handler?.removeCallbacksAndMessages(null) } catch (e: Exception) { }
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(43) } catch (e: Exception) { }
        avisa(false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun comecar() {
        if (vigiar) return
        vigiar = true
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CaptureService.CH_ID, "Vigia", NotificationManager.IMPORTANCE_LOW))
            val open = android.app.PendingIntent.getActivity(
                this, 13, Intent(this, MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, CaptureService.CH_ID)
                .setContentTitle("Táxi Praças — vigia")
                .setContentText("A ler o ecrã sozinha")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            nm.notify(43, n)
        } catch (e: Exception) { }
        if (handler == null) handler = Handler(Looper.getMainLooper())
        handler?.post(tick)
        avisa(true)
    }

    fun parar() {
        vigiar = false
        try { handler?.removeCallbacksAndMessages(null) } catch (e: Exception) { }
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(43) } catch (e: Exception) { }
        avisa(false)
    }

    private fun avisa(ok: Boolean) {
        try {
            val wv: WebView? = WebViewHolder.wv
            wv?.post {
                try { wv.evaluateJavascript("window.__vigiaNativa(${if (ok) "true" else "false"})", null) }
                catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    private fun foto() {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(r: ScreenshotResult) {
                        try {
                            val hb = r.hardwareBuffer ?: return
                            try {
                                val cru = Bitmap.wrapHardwareBuffer(
                                    hb, r.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                                ) ?: return
                                var soft: Bitmap? = null
                                var copia: Bitmap? = null
                                try {
                                    val k = minOf(1f, 720f / cru.width)
                                    copia = cru.copy(Bitmap.Config.ARGB_8888, false)
                                    soft = Bitmap.createScaledBitmap(
                                        copia,
                                        (cru.width * k).toInt(), (cru.height * k).toInt(), true
                                    )
                                    val out = java.io.ByteArrayOutputStream()
                                    soft.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    if (b64.length > 700000) return
                                    val wv: WebView? = WebViewHolder.wv
                                    wv?.post {
                                        try { wv.evaluateJavascript("window.__onFrame('data:image/jpeg;base64,$b64')", null) }
                                        catch (e: Exception) { }
                                    }
                                } finally {
                                    try { copia?.recycle() } catch (e: Exception) { }
                                    try { cru.recycle() } catch (e: Exception) { }
                                    try { soft?.recycle() } catch (e: Exception) { }
                                }
                            } finally {
                                try { hb.close() } catch (e: Exception) { }
                            }
                        } catch (t: Throwable) { }
                    }

                    override fun onFailure(code: Int) { }
                })
        } catch (t: Throwable) { }
    }
}
