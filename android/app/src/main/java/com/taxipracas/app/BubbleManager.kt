package com.taxipracas.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Mini flutuante sobre as outras apps («para onde ir» sempre visível).
object BubbleManager {
    private var root: LinearLayout? = null
    private var t1: TextView? = null
    private var t2: TextView? = null

    fun temPermissao(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true

    fun pedirPermissao(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) {
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + ctx.packageName))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Ative 'Sobrepor a outras apps' nas definições.", Toast.LENGTH_LONG).show()
        }
    }

    fun show(ctx: Context) {
        try {
            val app = ctx.applicationContext
            if (!temPermissao(app) || root != null) return
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#CC111111"))
            bg.cornerRadius = 24f
            bg.setStroke(3, Color.parseColor("#FFD400"))
            val lay = LinearLayout(app)
            lay.orientation = LinearLayout.VERTICAL
            lay.background = bg
            lay.setPadding(20, 12, 20, 12)
            t1 = TextView(app).apply { setTextColor(Color.parseColor("#FFD400")); textSize = 15f }
            t2 = TextView(app).apply { setTextColor(Color.WHITE); textSize = 12f }
            lay.addView(t1); lay.addView(t2)
            lay.setOnClickListener {
                try {
                    val i = Intent(app, MainActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(i)
                } catch (e: Exception) { }
            }
            val flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            val tipo = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                tipo, flags, android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0; params.y = 160
            wm.addView(lay, params)
            root = lay
            atualizarTexto(app)
        } catch (e: Exception) { }
    }

    fun atualizarTexto(ctx: Context) {
        try {
            val rows = calcRanking(Store.getPracas(ctx), Store.getLeituras(ctx))
            if (rows.isEmpty()) {
                t1?.text = "Táxi Praças"
                t2?.text = "A ler…"
            } else {
                val b = rows[0]
                t1?.text = "👉 " + b.praca.nome
                t2?.text = "${b.s.atual} esp · ${"%.1f".format(b.s.taxaH)}/h" +
                    (if (b.distM != null) " · ${fmtDist(b.distM)}" else "")
            }
        } catch (e: Exception) { }
    }

    fun update(ctx: Context, l1: String, l2: String) {
        try {
            if (root == null) { show(ctx); if (root == null) return }
            t1?.text = l1; t2?.text = l2
        } catch (e: Exception) { }
    }

    fun hide(ctx: Context) {
        try {
            val app = ctx.applicationContext
            root?.let { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (e: Exception) { }
        root = null; t1 = null; t2 = null
    }
}
