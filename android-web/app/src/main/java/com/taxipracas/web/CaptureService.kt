package com.taxipracas.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import android.webkit.WebView

// Captura o ecrã a cada 30 s e injeta os frames na página (window.__onFrame).
// É a peça que o navegador não dá no telemóvel; o resto é 100% a app web.
class CaptureService : Service() {

    companion object {
        const val CH_ID = "vigia"
        const val NOTIF_ID = 42
        const val INTERVALO_MS = 30000L
        @Volatile var aCorrer = false
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bgThread: HandlerThread? = null
    private var handler: Handler? = null
    private var aProcessar = false
    private var sw = 0
    private var sh = 0
    private var dpi = 0

    private val tick = object : Runnable {
        override fun run() {
            try { capturar() } catch (t: Throwable) { }
            handler?.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ID, "Vigia", NotificationManager.IMPORTANCE_LOW))
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val k = minOf(1f, 720f / metrics.widthPixels)
        sw = (metrics.widthPixels * k).toInt()
        sh = (metrics.heightPixels * k).toInt()
        dpi = metrics.densityDpi
        bgThread = HandlerThread("vigia").also { it.start() }
        handler = Handler(bgThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { pararTudo(); stopSelf(); return START_NOT_STICKY }
        }
        val code = intent?.getIntExtra("code", -999) ?: -999
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("data", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        if (code == -999 || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            reader = ImageReader.newInstance(sw, sh, ImageFormat.YUV_420_888, 2)
            display = projection!!.createVirtualDisplay(
                "taxiweb", sw, sh, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, null
            )
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(
            this, 11, Intent(this, CaptureService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 12, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_ID)
            .setContentTitle("Táxi Praças — vigia")
            .setContentText("A ler o ecrã sozinha")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Parar", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else
            startForeground(NOTIF_ID, n)
        aCorrer = true
        handler?.post(tick)
        toastJs("Vigia nativa ligada ✔")
        return START_STICKY
    }

    private fun toastJs(msg: String) {
        try {
            val wv: WebView? = WebViewHolder.wv
            wv?.post {
                try { wv.evaluateJavascript("toast(${org.json.JSONObject.quote(msg)})", null) } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    private fun capturar() {
        if (aProcessar) return
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return
        } catch (e: Exception) { }
        val img = try { reader?.acquireLatestImage() } catch (e: Exception) { null } ?: return
        aProcessar = true
        try {
            val jpeg = yuvParaJpeg(img)
            try { img.close() } catch (e: Exception) { }
            if (jpeg == null) { aProcessar = false; return }
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            if (b64.length > 700000) { aProcessar = false; return } // frame pesado: salta, protege a WebView
            val wv: WebView? = WebViewHolder.wv
            wv?.post {
                try { wv.evaluateJavascript("window.__onFrame('data:image/jpeg;base64,$b64')", null) }
                catch (e: Exception) { }
            }
        } catch (e: Exception) {
            try { img.close() } catch (e2: Exception) { }
        } finally {
            aProcessar = false
        }
    }

    private fun yuvParaJpeg(img: Image): ByteArray? {
        return try {
            val w = img.width; val h = img.height
            val yBuf = img.planes[0].buffer
            val uBuf = img.planes[1].buffer
            val vBuf = img.planes[2].buffer
            val yRow = img.planes[0].rowStride
            val uvRow = img.planes[1].rowStride
            val uvPix = img.planes[1].pixelStride
            val nv21 = ByteArray(w * h + w * h / 2)
            var p = 0
            for (r in 0 until h) {
                yBuf.position(r * yRow)
                for (c in 0 until w) nv21[p++] = yBuf.get()
            }
            var q = w * h
            for (r in 0 until h / 2) {
                for (c in 0 until w step 2) {
                    nv21[q++] = vBuf.get(r * uvRow + c * uvPix)
                    nv21[q++] = uBuf.get(r * uvRow + c * uvPix)
                }
            }
            val yuv = android.graphics.YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = java.io.ByteArrayOutputStream()
            yuv.compressToJpeg(android.graphics.Rect(0, 0, w, h), 70, out)
            out.toByteArray()
        } catch (e: Exception) { null }
    }

    private fun pararTudo() {
        aCorrer = false
        try { handler?.removeCallbacksAndMessages(null) } catch (e: Exception) { }
        try { bgThread?.quitSafely() } catch (e: Exception) { }
        bgThread = null
        try {
            val wv: WebView? = WebViewHolder.wv
            wv?.post {
                try { wv.evaluateJavascript("window.__vigiaNativa(false)", null) } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
        try { display?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        try { projection?.stop() } catch (e: Exception) { }
    }

    override fun onDestroy() {
        pararTudo()
        super.onDestroy()
    }
}
