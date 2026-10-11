package com.taxipracas.web

import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

// Ponte JS <-> Android. No JS: window.TaxiNative.startWatch()/stopWatch()/setBookings()/notify()
class Bridge(activity: MainActivity) {
    private val ref = WeakReference(activity)

    private fun act(): MainActivity? = ref.get()

    @JavascriptInterface
    fun startWatch() {
        val a = act() ?: return
        a.runOnUiThread { a.pedirCaptura() }
    }

    @JavascriptInterface
    fun stopWatch() {
        try { act()?.stopService(Intent(act(), CaptureService::class.java)) } catch (e: Exception) { }
        try { VigiaA11yService.inst?.parar() } catch (e: Exception) { }
    }

    @JavascriptInterface
    fun setBookings(json: String) {
        try {
            val app = act()?.applicationContext ?: return
            app.getSharedPreferences("marc", Context.MODE_PRIVATE).edit().putString("lista", json).apply()
            AlertCore.scheduleNext(app)
            AlertCore.pedirAlarmesExatos(app)
        } catch (e: Exception) { }
    }

    @JavascriptInterface
    fun notify(titulo: String, corpo: String) {
        try {
            val app = act()?.applicationContext ?: return
            AlertCore.notificar(app, titulo, corpo)
        } catch (e: Exception) { }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        try {
            val a = act() ?: return
            a.runOnUiThread { Toast.makeText(a, msg, Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { }
    }

    // A vigia nativa está mesmo a correr (qualquer dos dois modos)?
    @JavascriptInterface
    fun isWatching(): Boolean {
        return try { CaptureService.aCorrer || VigiaA11yService.vigiar } catch (e: Exception) { false }
    }

    // Diagnóstico do aparelho (a app mostra; o utilizador fotografa e envia).
    @JavascriptInterface
    fun diag(): String {
        return try {
            val app = act()?.applicationContext ?: return "{}"
            val o = JSONObject()
            o.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            o.put("webview", chromeVersao(app))
            o.put("overlay", Settings.canDrawOverlays(app))
            o.put("uso", usoOk(app))
            o.put("bateria", bateriaOk(app))
            o.put("notif", notifOk(app))
            o.put("vigia", try { CaptureService.aCorrer || VigiaA11yService.vigiar } catch (e: Exception) { false })
            o.put("acess", try { VigiaA11yService.ativo(app) } catch (e: Exception) { false })
            o.put("crash", app.getSharedPreferences("crashlog", Context.MODE_PRIVATE).getString("trace", null))
            o.toString()
        } catch (e: Exception) { "{}" }
    }

    private fun chromeVersao(app: Context): String {
        return try {
            val ua = android.webkit.WebSettings.getDefaultUserAgent(app) ?: ""
            Regex("Chrome/([\\d.]+)").find(ua)?.groupValues?.get(1) ?: ua.take(60)
        } catch (e: Exception) { "?" }
    }

    private fun usoOk(app: Context): Boolean {
        return try {
            val ao = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun bateriaOk(app: Context): Boolean {
        return try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(app.packageName)
        } catch (e: Exception) { false }
    }

    private fun notifOk(app: Context): Boolean {
        return try {
            app.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
        } catch (e: Exception) { false }
    }

    // Mini nativo (bolha sobre as outras apps). Sem permissão, abre as definições.
    @JavascriptInterface
    fun mini(l1: String, l2: String) {
        val a = act() ?: return
        a.runOnUiThread {
            try {
                if (BubbleManager.visivel()) BubbleManager.hide(a)
                else BubbleManager.show(a, l1, l2)
            } catch (e: Exception) { }
        }
    }

    // Vigia por Acessibilidade (sem diálogo de gravação). Se o serviço não
    // estiver ativo nas Definições, abre-as e devolve false para o JS guiar.
    @JavascriptInterface
    fun vigiaA11y(): Boolean {
        return try {
            val a = act() ?: return false
            if (!VigiaA11yService.ativo(a)) {
                a.runOnUiThread {
                    try {
                        Toast.makeText(a, "Ative 'Táxi Praças' na Acessibilidade. Se 'restrita': info da app → ⋮ → permitir.", Toast.LENGTH_LONG).show()
                        a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) { }
                }
                false
            } else {
                VigiaA11yService.inst?.comecar()
                true
            }
        } catch (e: Exception) { false }
    }

    // HTTP sem CORS (a WebView bloqueia cp.pt/adsb por falta de cabeçalhos).
    // O JS chama httpGet(id,url,headers) e recebe window.__httpRes(id,ok,corpo).
    @JavascriptInterface
    fun httpGet(reqId: String, url: String, headersJson: String) {
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TaxiPracas")
                conn.setRequestProperty("Accept", "application/json")
                try {
                    val h = JSONObject(headersJson)
                    val it = h.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        conn.setRequestProperty(k, h.optString(k))
                    }
                } catch (e: Exception) { }
                val code = try { conn.responseCode } catch (e: Exception) { -1 }
                val stream = try {
                    if (code in 200..299) conn.inputStream else conn.errorStream
                } catch (e: Exception) { null }
                val body = try { stream?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
                responder(reqId, code in 200..299, if (code in 200..299) body else "HTTP $code")
            } catch (e: Exception) {
                responder(reqId, false, e.message ?: "falha de rede")
            }
        }.start()
    }

    private fun responder(reqId: String, ok: Boolean, corpo: String) {
        try {
            val wv: WebView? = WebViewHolder.wv
            val payload = JSONObject.quote(corpo)
            val rid = JSONObject.quote(reqId)
            wv?.post {
                try { wv.evaluateJavascript("window.__httpRes($rid,${if (ok) 1 else 0},$payload)", null) }
                catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }
}

// Alarmes exatos das marcações (funcionam com a app fechada / ecrã desligado).
object AlertCore {
    private const val CH = "alertas"

    fun hora(ts: Long): String = SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(ts))

    fun quedaDe(o: JSONObject): Long {
        val aviso = if (o.isNull("aviso")) null else o.optInt("aviso")
        return if (aviso != null) o.getLong("ts") - aviso * 60000 else o.getLong("ts") - 20 * 60000
    }

    fun gatilhoDe(o: JSONObject): Long {
        val aviso = if (o.isNull("aviso")) null else o.optInt("aviso")
        return if (aviso != null) quedaDe(o) - 20 * 60000 else o.getLong("ts") - 20 * 60000
    }

    fun lista(app: Context): JSONArray {
        return try {
            JSONArray(app.getSharedPreferences("marc", Context.MODE_PRIVATE).getString("lista", "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }
    }

    fun canal(app: Context) {
        try {
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CH, "Alertas", NotificationManager.IMPORTANCE_HIGH)
            )
        } catch (e: Exception) { }
    }

    fun pedirAlarmesExatos(app: Context) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val am = app.getSystemService(AlarmManager::class.java) ?: return
                if (!am.canScheduleExactAlarms()) {
                    val done = app.getSharedPreferences("marc", Context.MODE_PRIVATE).getBoolean("pediuAlarm", false)
                    if (!done) {
                        app.getSharedPreferences("marc", Context.MODE_PRIVATE).edit().putBoolean("pediuAlarm", true).apply()
                        app.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
            }
        } catch (e: Exception) { }
    }

    fun scheduleNext(app: Context) {
        try {
            val agora = System.currentTimeMillis()
            var next: Long? = null
            val arr = lista(app)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("alerted", false)) continue
                val g = gatilhoDe(o)
                if (g > agora - 60000 && (next == null || g < next)) next = g
            }
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                app, 17, Intent(app, AlertReceiverN::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            if (next == null) return
            if (Build.VERSION.SDK_INT >= 23)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next, agora), pi)
            else
                @Suppress("DEPRECATION") am.setExact(AlarmManager.RTC_WAKEUP, maxOf(next, agora), pi)
        } catch (e: Exception) { }
    }

    fun marcarAlerted(app: Context, key: String) {
        try {
            val arr = lista(app)
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("key") == key) arr.getJSONObject(i).put("alerted", true)
            }
            app.getSharedPreferences("marc", Context.MODE_PRIVATE).edit().putString("lista", arr.toString()).apply()
        } catch (e: Exception) { }
    }

    fun apitar() {
        try {
            Thread {
                try {
                    val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    repeat(3) {
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                        Thread.sleep(350)
                    }
                    tone.release()
                } catch (e: Exception) { }
            }.start()
        } catch (e: Exception) { }
    }

    fun vibrar(app: Context) {
        try {
            val vib = app.getSystemService(Vibrator::class.java) ?: return
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (e: Exception) { }
    }

    fun notificar(app: Context, titulo: String, corpo: String) {
        try {
            canal(app)
            val open = PendingIntent.getActivity(
                app, 18, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(app, CH)
                .setContentTitle(titulo).setContentText(corpo)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(open).setAutoCancel(true).build()
            app.getSystemService(NotificationManager::class.java)?.notify(98, n)
        } catch (e: Exception) { }
    }
}

class AlertReceiverN : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        try {
            val app = ctx.applicationContext
            val agora = System.currentTimeMillis()
            val arr = AlertCore.lista(app)
            var achou: JSONObject? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("alerted", false)) continue
                val g = AlertCore.gatilhoDe(o)
                if (agora >= g - 60000 && agora < o.getLong("ts") + 5 * 60000) { achou = o; break }
            }
            if (achou == null) { AlertCore.scheduleNext(app); return }
            val b = achou
            AlertCore.marcarAlerted(app, b.optString("key"))
            AlertCore.scheduleNext(app)
            // conta recente via página (se viva); senão dispara como "sem dados"
            val wv: WebView? = WebViewHolder.wv
            val pid = if (b.isNull("pracaId")) null else b.optString("pracaId")
            var decidido = false
            fun decidir(n: Int?) {
                if (decidido) return
                decidido = true
                val estado = when {
                    n == null -> " · sem dados da praça"
                    n == 0 -> " · praça VAZIA — vai!"
                    else -> " · $n carro(s) na praça"
                }
                val cai = if (!b.isNull("aviso")) " · cai às " + AlertCore.hora(AlertCore.quedaDe(b)) else ""
                val msg = "📅 Marcação " + AlertCore.hora(b.getLong("ts")) +
                    (if (!b.isNull("pracaNome")) " — " + b.optString("pracaNome") else "") + cai + estado
                AlertCore.apitar()
                AlertCore.vibrar(app)
                AlertCore.notificar(app, "Táxi Praças 🔊", msg)
            }
            if (wv == null || pid == null) {
                decidir(null)
            } else {
                try {
                    wv.post {
                        try {
                            wv.evaluateJavascript("window.__contagem(" + JSONObject.quote(pid) + ")") { v ->
                                val n = v?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
                                decidir(n)
                            }
                        } catch (e: Exception) { decidir(null) }
                    }
                    Handler(Looper.getMainLooper()).postDelayed({ decidir(null) }, 4000)
                } catch (e: Exception) { decidir(null) }
            }
        } catch (e: Exception) { }
    }
}

class BootReceiverN : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            try { AlertCore.scheduleNext(ctx.applicationContext) } catch (e: Exception) { }
        }
    }
}
