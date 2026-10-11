package com.taxipracas.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer

// Serviço em 1º plano: fotografa o ecrã a cada 30 s, OCR no aparelho, guarda e avisa.
class ScreenCaptureService : Service() {

    companion object {
        const val CH_ID = "vigia"
        const val NOTIF_ID = 41
        const val INTERVALO_MS = 30000L
        var aCorrer = false
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var handler: Handler? = null
    private var aProcessar = false
    private var recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    private var sw = 0
    private var sh = 0
    private var dpi = 0

    private val tick = object : Runnable {
        override fun run() {
            try { lerEcra() } catch (e: Exception) { }
            handler?.postDelayed(this, INTERVALO_MS)
            verificarMarcacoesBg()
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        aCorrer = true
        criarCanal()
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val k = minOf(1f, 720f / metrics.widthPixels)
        sw = (metrics.widthPixels * k).toInt()
        sh = (metrics.heightPixels * k).toInt()
        dpi = metrics.densityDpi
        handler = Handler(Looper.getMainLooper())
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
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            reader = ImageReader.newInstance(sw, sh, ImageFormat.YUV_420_888, 2)
            display = projection!!.createVirtualDisplay(
                "taxi", sw, sh, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, null
            )
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        arrancarForeground("A iniciar vigia…")
        handler?.post(tick)
        BubbleManager.show(this)
        return START_STICKY
    }

    private fun arrancarForeground(txt: String) {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenCaptureService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_ID)
            .setContentTitle("Táxi Praças — vigia")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else
            startForeground(NOTIF_ID, n)
    }

    private fun criarCanal() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ID, "Vigia", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("alertas", "Alertas", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun ecraLigado(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun appAlvoVisivel(): Boolean {
        try {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val modo = if (Build.VERSION.SDK_INT >= 29)
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), packageName
                )
            else
                @Suppress("DEPRECATION") appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), packageName
                )
            if (modo != android.app.AppOpsManager.MODE_ALLOWED) return true // sem permissão: lê sempre
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val fim = System.currentTimeMillis()
            val ev = usm.queryEvents(fim - 15000, fim)
            var ultima: String? = null
            while (ev.hasNextEvent()) {
                val e = android.app.usage.UsageEvents.Event()
                ev.getNextEvent(e)
                if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) ultima = e.packageName
            }
            return ultima == null || ultima in TARGET_PKGS
        } catch (e: Exception) { return true }
    }

    private fun lerEcra() {
        if (aProcessar || !ecraLigado() || !appAlvoVisivel()) return
        val img = try { reader?.acquireLatestImage() } catch (e: Exception) { null } ?: return
        aProcessar = true
        try {
            val rot = when ((getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
            val bmp = yuvParaBitmap(img)
            val input = InputImage.fromBitmap(bmp, rot)
            recognizer.process(input)
                .addOnSuccessListener { t -> processarTexto(t.text) }
                .addOnCompleteListener { try { img.close() } catch (e: Exception) { }; aProcessar = false }
        } catch (e: Exception) {
            try { img.close() } catch (e2: Exception) { }
            aProcessar = false
        }
    }

    private fun yuvParaBitmap(img: Image): Bitmap {
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
        yuv.compressToJpeg(android.graphics.Rect(0, 0, w, h), 85, out)
        val bytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun fonteDe(texto: String): String {
        val appA = "Taxi-Link"; val appB = "TaxiDigital"
        return when {
            Regex("TAXI-LINK|OC\\s*/\\s*15|T[AÁ]XIS\\s+NA\\s+PRA[CÇ]A|LUGARES|\\bEM\\s*PRA[CÇ]A\\b|\\bNA\\s*FILA\\b", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> appA
            Regex("TAXI.?DIGITAL|IZZYMOVE|CNTD|PENDENTES/OFERTAS|COMUNICA[CÇ][AÃ]O COM A CENTRAL|\\bTX\\b|PT\\.", RegexOption.IGNORE_CASE).containsMatchIn(texto) -> appB
            else -> appA
        }
    }

    private fun processarTexto(texto: String) {
        if (texto.isBlank()) return
        val agora = System.currentTimeMillis()
        val fonte = fonteDe(texto)
        val pracas = Store.getPracas(this)
        // marcações primeiro
        val mcs = extrairMarcacoes(texto)
        if (mcs.isNotEmpty()) {
            val cur = Store.getMarcacoes(this).toMutableList()
            var nNovas = 0
            for (b in mcs) {
                if (cur.any { it.key == b.key }) continue
                var pid = b.pracaId
                var pnome = b.pracaNome
                if (pid == null && pnome != null && b.firme) {
                    val ex = pracas.find { norm(it.nome) == norm(pnome) }
                    if (ex != null) { pid = ex.id; pnome = ex.nome }
                    else {
                        val np = Praca(uid(), pnome.trim())
                        pracas.add(np); Store.savePracas(this, pracas)
                        pid = np.id
                    }
                } else if (pid == null && pnome != null) {
                    // tenta casar sem criar
                    val m2 = matchPracaId(pnome, pracas) ?: pracaPorToken(pnome, pracas)
                    if (m2 != null) { pid = m2.id; pnome = m2.nome }
                }
                cur.add(Marcacao(uid(), b.key, b.ts, pid, pnome, b.aviso, fonte, false))
                nNovas++
            }
            if (nNovas > 0) {
                Store.saveMarcacoes(this, cur)
                AlertScheduler.scheduleNext(this)
            }
        }
        val info = extrairTaxilink(texto)
        // Ocupado na TaxiDigital: ignorar contagem
        if (info.tdStatus != null && info.tdStatus != "LIVRE") { atualizarNotificacao(); return }
        // lista Taxi-Link: várias praças
        if (info.ecra == "lista") {
            val rows = extrairLista(texto)
            var nG = 0
            for (r in rows) {
                val mt = matchPracaId(r.nome, pracas) ?: continue
                Store.addLeitura(this, Leitura(uid(), mt.id, fonte, r.n, agora, "vigia-auto", "lista", r.saidas15, r.dist, null))
                nG++
            }
            if (nG > 0) atualizarNotificacao()
            return
        }
        // ecrã simples: uma praça
        if (info.emEspera != null || info.saidas15 != null) {
            var pid: String? = pracaPorTexto(texto, pracas)?.id
            var pnome: String? = pid?.let { id -> pracas.find { it.id == id }?.nome }
            if (pid == null && info.tdLocal != null) {
                val loc = info.tdLocal!!
                if (!Regex("MAFRA", RegexOption.IGNORE_CASE).containsMatchIn(loc)) {
                    pracaPorToken(loc, pracas)?.let { pid = it.id; pnome = it.nome }
                    if (pid == null && Regex("PT\\.|PRA[CÇ]A", RegexOption.IGNORE_CASE).containsMatchIn(loc)) {
                        val np = Praca(uid(), loc.trim())
                        pracas.add(np); Store.savePracas(this, pracas)
                        pid = np.id; pnome = np.nome
                    }
                }
            }
            var nV = info.emEspera
            val fPid: String? = pid
            if (nV == null && fPid != null) nV = ultimaContagem(fPid, Store.getLeituras(this))
            if (fPid != null && nV != null && (info.licencas.isNotEmpty() || info.placas.isNotEmpty() || info.saidas15 != null || nV <= 120)) {
                Store.addLeitura(this, Leitura(uid(), fPid, fonte, nV, agora, "vigia-auto",
                    info.ecra, info.saidas15, info.distanciaM, info.posicao ?: info.tdPos))
                atualizarNotificacao()
            }
        }
    }

    private fun atualizarNotificacao() {
        val pracas = Store.getPracas(this)
        val leituras = Store.getLeituras(this)
        val rows = calcRanking(pracas, leituras)
        val txt = if (rows.isEmpty()) "A vigiar — sem dados ainda"
        else {
            val b = rows[0]
            "👉 ${b.praca.nome} · ${b.s.atual} em espera · ${"%.1f".format(b.s.taxaH)}/h" +
                (if (b.distM != null) " · ${fmtDist(b.distM)}" else "")
        }
        arrancarForeground(txt)
        if (rows.isNotEmpty()) {
            val b = rows[0]
            BubbleManager.update(this, "👉 " + b.praca.nome,
                "${b.s.atual} esp · ${"%.1f".format(b.s.taxaH)}/h" + (if (b.distM != null) " · ${fmtDist(b.distM)}" else ""))
        }
    }

    private fun verificarMarcacoesBg() {
        try {
            val agora = System.currentTimeMillis()
            val cur = Store.getMarcacoes(this).toMutableList()
            var mudou = false
            val antes = cur.size
            cur.removeAll { agora - it.ts >= 30 * 60000 }
            if (cur.size != antes) mudou = true
            for (i in cur.indices) {
                val b = cur[i]
                val queda = if (b.aviso != null) b.ts - b.aviso * 60000 else b.ts - 20 * 60000
                val gatilho = if (b.aviso != null) queda - 20 * 60000 else b.ts - 20 * 60000
                if (!b.alerted && agora >= gatilho - 60000 && agora < b.ts + 5 * 60000) {
                    val n = ultimaContagem(b.pracaId, Store.getLeituras(this))
                    cur[i] = b.copy(alerted = true); mudou = true
                    AlertHelper.disparar(this, b, n)
                }
            }
            if (mudou) Store.saveMarcacoes(this, cur)
        } catch (e: Exception) { }
    }

    private fun pararTudo() {
        try { handler?.removeCallbacksAndMessages(null) } catch (e: Exception) { }
        try { display?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        try { projection?.stop() } catch (e: Exception) { }
        BubbleManager.hide(this)
        aCorrer = false
    }

    override fun onDestroy() {
        pararTudo()
        super.onDestroy()
    }
}
