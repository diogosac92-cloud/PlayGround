package com.taxipracas.web

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Mini flutuante sobre as outras apps («para onde ir» sempre visível).
// Versão da híbrida: só texto (o ranking vive no JavaScript).
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
            Toast.makeText(ctx, "Sobrepor: ative nas definições. Se 'restrita': info da app → ⋮ → permitir.", Toast.LENGTH_LONG).show()
        }
    }

    fun visivel(): Boolean = root != null

    fun show(ctx: Context, l1: String, l2: String) {
        try {
            val app = ctx.applicationContext
            if (!temPermissao(app)) { pedirPermissao(ctx); return }
            if (root != null) { update(l1, l2); return }
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
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
            update(l1, l2)
        } catch (e: Exception) {
            try { Toast.makeText(ctx, "Mini bloqueado pelo sistema.", Toast.LENGTH_LONG).show() } catch (e2: Exception) { }
        }
    }

    fun update(l1: String, l2: String) {
        try { t1?.text = l1; t2?.text = l2 } catch (e: Exception) { }
    }

    fun hide(ctx: Context) {
        try {
            val app = ctx.applicationContext
            root?.let { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (e: Exception) { }
        root = null; t1 = null; t2 = null
    }
}
