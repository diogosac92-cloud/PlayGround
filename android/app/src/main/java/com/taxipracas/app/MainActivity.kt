package com.taxipracas.app

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_CAP = 1001
    private lateinit var statusText: TextView
    private lateinit var rankingBox: LinearLayout
    private lateinit var marcBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogN.install(this)
        val scroll = ScrollView(this)
        val lay = LinearLayout(this)
        lay.orientation = LinearLayout.VERTICAL
        lay.setPadding(28, 28, 28, 60)
        scroll.addView(lay)
        setContentView(scroll)

        fun titulo(t: String): TextView {
            val v = TextView(this)
            v.text = t; v.textSize = 20f
            v.setPadding(0, 18, 0, 6)
            lay.addView(v)
            return v
        }
        fun botao(t: String, fn: (View) -> Unit): Button {
            val b = Button(this)
            b.text = t
            b.setOnClickListener(fn)
            lay.addView(b)
            return b
        }

        titulo("🚕 Táxi Praças Auto")
        statusText = TextView(this)
        lay.addView(statusText)
        CrashLogN.mostrarSeHouver(this, statusText)

        botao("▶️ Iniciar vigia") { pedirCaptura() }
        botao("⏹️ Parar vigia") {
            try { stopService(Intent(this, ScreenCaptureService::class.java)) } catch (e: Exception) { }
            atualizar()
        }
        botao("🪟 Mini on/off") {
            if (!BubbleManager.temPermissao(this)) BubbleManager.pedirPermissao(this)
            else {
                try {
                    val rows = calcRanking(Store.getPracas(this), Store.getLeituras(this))
                    if (rows.isEmpty()) Toast.makeText(this, "Sem dados ainda", Toast.LENGTH_SHORT).show()
                    else {
                        BubbleManager.show(this)
                        val b = rows[0]
                        BubbleManager.update(this, "👉 " + b.praca.nome, "${b.s.atual} esp · ${"%.1f".format(b.s.taxaH)}/h")
                    }
                } catch (e: Exception) { }
            }
        }

        titulo("🔑 Permissões (uma vez)")
        botao("1. Notificações") { pedirNotificacoes() }
        botao("2. Sobrepor a outras apps (mini)") { BubbleManager.pedirPermissao(this) }
        botao("3. Acesso a utilização (só ler com a app aberta)") { abrirUso() }
        botao("4. Ignorar otimização de bateria") { pedirBateria() }

        titulo("🏁 Para onde ir")
        rankingBox = LinearLayout(this)
        rankingBox.orientation = LinearLayout.VERTICAL
        lay.addView(rankingBox)

        titulo("📅 Marcações")
        marcBox = LinearLayout(this)
        marcBox.orientation = LinearLayout.VERTICAL
        lay.addView(marcBox)

        botao("↻ Atualizar") { atualizar() }
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun pedirCaptura() {
        try {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAP)
        } catch (e: Exception) {
            Toast.makeText(this, "Sem captura de ecrã neste aparelho.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("usado de propósito para não depender de androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAP) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, ScreenCaptureService::class.java)
                i.putExtra("code", resultCode)
                i.putExtra("data", data)
                try {
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                } catch (e: Exception) {
                    Toast.makeText(this, "O sistema bloqueou a vigia — com a app aberta, tente de novo.", Toast.LENGTH_LONG).show()
                    return
                }
                Toast.makeText(this, "Vigia ligada ✔ — abra a Taxi-Link/TaxiDigital", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Partilha cancelada.", Toast.LENGTH_SHORT).show()
            }
            atualizar()
        }
    }

    private fun pedirNotificacoes() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
            else Toast.makeText(this, "Já permitidas ✔", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Não precisa nesta versão ✔", Toast.LENGTH_SHORT).show()
    }

    private fun abrirUso() {
        try {
            val ok = try {
                val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
                @Suppress("DEPRECATION") appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
                ) == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) { false }
            if (ok) Toast.makeText(this, "Já permitido ✔", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) { }
    }

    private fun pedirBateria() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName))
                Toast.makeText(this, "Já ignorada ✔", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) { }
    }

    private fun txt(s: String, size: Float = 14f): TextView {
        val v = TextView(this)
        v.text = s; v.textSize = size
        v.setPadding(0, 4, 0, 4)
        return v
    }

    private fun atualizar() {
        try {
            statusText.text = if (ScreenCaptureService.aCorrer) "● A vigiar" else "○ Parada"
            rankingBox.removeAllViews()
            val rows = calcRanking(Store.getPracas(this), Store.getLeituras(this)).take(5)
            if (rows.isEmpty()) rankingBox.addView(txt("Sem dados ainda — ligue a vigia."))
            var i = 1
            for (r in rows) {
                rankingBox.addView(txt(
                    "${i++}. ${r.praca.nome} — ${r.s.atual} esp · ${"%.1f".format(r.s.taxaH)}/h · ${fmtEspera(r.s.esperaH)}" +
                        (if (r.distM != null) " · ${fmtDist(r.distM)}" else "")
                ))
            }
            marcBox.removeAllViews()
            val agora = System.currentTimeMillis()
            val prox = Store.getMarcacoes(this).filter { it.ts > agora - 30 * 60000 }.sortedBy { it.ts }.take(3)
            if (prox.isEmpty()) marcBox.addView(txt("Sem marcações.", 13f))
            for (b in prox) {
                val falta = ((b.ts - agora) / 60000).toInt()
                val linha = LinearLayout(this)
                linha.orientation = LinearLayout.HORIZONTAL
                val tv = TextView(this)
                val cai = if (b.aviso != null) " (cai " + AlertHelper.hora(
                    if (b.aviso != null) b.ts - b.aviso * 60000 else b.ts - 20 * 60000
                ) + ")" else ""
                tv.text = "📅 ${AlertHelper.hora(b.ts)}${if (b.pracaNome != null) " · ${b.pracaNome}" else ""}$cai — " +
                    (if (falta < 0) "a decorrer" else "em $falta min")
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val del = Button(this)
                del.text = "✕"
                del.setOnClickListener {
                    Store.saveMarcacoes(this, Store.getMarcacoes(this).filter { it.id != b.id })
                    AlertScheduler.scheduleNext(this)
                    atualizar()
                }
                linha.addView(tv); linha.addView(del)
                marcBox.addView(linha)
            }
            @Suppress("UNUSED_VARIABLE")
            val ctx: Context = this
        } catch (e: Exception) { }
    }
}

// Apanhador de crashes: guarda o erro e mostra-o no próximo arranque.
object CrashLogN {
    private const val PREF = "crashlog"
    private const val KEY = "trace"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(KEY, "$e\n$sw".take(4000)).apply()
            } catch (e2: Exception) { }
            try { prev?.uncaughtException(t, e) } catch (e3: Exception) { }
        }
    }

    fun mostrarSeHouver(a: Activity, status: TextView) {
        try {
            val sp = a.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val t = sp.getString(KEY, null) ?: return
            sp.edit().remove(KEY).apply()
            a.runOnUiThread {
                try {
                    status.text = "⚠️ A app fechou com erro. Toque para ver."
                    status.setOnClickListener {
                        val tv = TextView(a)
                        tv.text = t
                        tv.setTextIsSelectable(true)
                        tv.textSize = 11f
                        val sc = ScrollView(a)
                        sc.addView(tv)
                        android.app.AlertDialog.Builder(a)
                            .setTitle("Erro — envie-me este texto")
                            .setView(sc)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }
}
