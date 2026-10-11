package com.taxipracas.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

// A MESMA app web (assets/taxi.html) a correr como aplicação.
// A vigia usa captura nativa (MediaProjection) porque o navegador não a dá no telemóvel.
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    companion object {
        const val REQ_CAP = 2001
        const val REQ_FILE = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        montar()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7) } catch (e: Exception) { }
        }
        CrashLog.mostrarSeHouver(this)
    }

    private fun montar() {
        web = WebView(this)
        configurar(web)
        WebViewHolder.wv = web
        setContentView(web)
        web.loadUrl("file:///android_asset/taxi.html")
    }

    // Se o motor da página morrer (página em branco), recria sozinho em vez de ficar morto.
    fun recarregar() {
        runOnUiThread {
            try { Toast.makeText(this, "O motor da página falhou — a recarregar.", Toast.LENGTH_LONG).show() }
            catch (e: Exception) { }
            try {
                val velha = try { web } catch (e: Exception) { null }
                web = WebView(this)
                configurar(web)
                WebViewHolder.wv = web
                setContentView(web)
                web.loadUrl("file:///android_asset/taxi.html")
                try { velha?.destroy() } catch (e: Exception) { }
            } catch (e: Exception) { }
        }
    }

    private fun configurar(w: WebView) {
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mediaPlaybackRequiresUserGesture = false
        w.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                try { recarregar() } catch (e: Exception) { }
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
            ): Boolean {
                return try {
                    fileCb?.onReceiveValue(null)
                    fileCb = cb
                    startActivityForResult(p.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) { fileCb = null; false }
            }
        }
        w.addJavascriptInterface(Bridge(this), "TaxiNative")
    }

    fun pedirCaptura() {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAP)
        } catch (e: Exception) {
            fout("Sem captura de ecrã neste aparelho.")
        }
    }

    fun fout(msg: String) {
        try {
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                try { web.evaluateJavascript("toast(${org.json.JSONObject.quote(msg)})", null) } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    fun vigiaNativa(ok: Boolean) {
        try {
            val w = WebViewHolder.wv ?: return
            w.post {
                try { w.evaluateJavascript("window.__vigiaNativa(${if (ok) "true" else "false"})", null) }
                catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    @Deprecated("sem androidx de propósito")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAP) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, CaptureService::class.java)
                i.putExtra("code", resultCode)
                i.putExtra("data", data)
                try {
                    startForegroundService(i)
                    vigiaNativa(true)
                } catch (e: Exception) {
                    vigiaNativa(false)
                    fout("O sistema bloqueou o arranque da vigia — com a app aberta, tente de novo.")
                }
            } else {
                vigiaNativa(false)
                fout("Partilha cancelada.")
            }
        } else if (requestCode == REQ_FILE) {
            try { fileCb?.onReceiveValue(FileChooserParams.parseResult(resultCode, data)) }
            catch (e: Exception) { try { fileCb?.onReceiveValue(null) } catch (e2: Exception) { } }
            fileCb = null
        }
    }

    override fun onDestroy() {
        if (WebViewHolder.wv === web) WebViewHolder.wv = null
        super.onDestroy()
    }
}

object WebViewHolder {
    @Volatile var wv: WebView? = null
}

// Apanhador de crashes: guarda o erro e mostra-o no próximo arranque.
object CrashLog {
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

    fun mostrarSeHouver(a: Activity) {
        try {
            val sp = a.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val t = sp.getString(KEY, null) ?: return
            sp.edit().remove(KEY).apply()
            a.runOnUiThread {
                try {
                    val tv = android.widget.TextView(a)
                    tv.text = t
                    tv.setTextIsSelectable(true)
                    tv.textSize = 11f
                    val sc = android.widget.ScrollView(a)
                    sc.addView(tv)
                    AlertDialog.Builder(a)
                        .setTitle("A app fechou com este erro — envie-me o texto")
                        .setView(sc)
                        .setPositiveButton("OK", null)
                        .show()
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }
}
