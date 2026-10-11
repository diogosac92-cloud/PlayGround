package com.taxipracas.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
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
        web = WebView(this)
        setContentView(web)
        WebViewHolder.wv = web
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
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
        web.addJavascriptInterface(Bridge(this), "TaxiNative")
        web.loadUrl("file:///android_asset/taxi.html")
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

    @Deprecated("sem androidx de propósito")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAP) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, CaptureService::class.java)
                i.putExtra("code", resultCode)
                i.putExtra("data", data)
                startForegroundService(i)
            } else {
                fout("Partilha cancelada — escolha Ecrã inteiro.")
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
