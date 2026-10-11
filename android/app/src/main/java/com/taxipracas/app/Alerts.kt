package com.taxipracas.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.text.SimpleDateFormat
import java.util.*

// Agenda o próximo alerta + dispara som/vibração/notificação.
object AlertScheduler {
    fun quedaDe(b: Marcacao): Long =
        if (b.aviso != null) b.ts - b.aviso * 60000 else b.ts - 20 * 60000

    fun gatilhoDe(b: Marcacao): Long {
        val q = quedaDe(b)
        return if (b.aviso != null) q - 20 * 60000 else b.ts - 20 * 60000
    }

    fun scheduleNext(ctx: Context) {
        try {
            val agora = System.currentTimeMillis()
            val next = Store.getMarcacoes(ctx)
                .filter { !it.alerted }
                .map { gatilhoDe(it) }
                .filter { it > agora - 60000 }
                .minOrNull() ?: return
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, 7, Intent(ctx, AlertReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= 23)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next, agora), pi)
            else
                @Suppress("DEPRECATION") am.setExact(AlarmManager.RTC_WAKEUP, maxOf(next, agora), pi)
        } catch (e: Exception) { }
    }
}

object AlertHelper {
    fun hora(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(ts))

    fun disparar(ctx: Context, b: Marcacao, n: Int?) {
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
        try {
            val vib = ctx.getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (e: Exception) { }
        val estado = when {
            n == null -> " · sem dados da praça"
            n == 0 -> " · praça VAZIA — vai!"
            else -> " · $n carro(s) na praça"
        }
        val cai = if (b.aviso != null)
            " · cai às " + hora(AlertScheduler.quedaDe(b)) else ""
        val msg = "📅 Marcação ${hora(b.ts)}${if (b.pracaNome != null) " — ${b.pracaNome}" else ""}$cai$estado"
        try {
            val open = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val not = Notification.Builder(ctx, "alertas")
                .setContentTitle("Táxi Praças 🔊")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(99, not)
        } catch (e: Exception) { }
    }
}

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        try {
            val agora = System.currentTimeMillis()
            val cur = Store.getMarcacoes(ctx).toMutableList()
            var mudou = false
            val antes = cur.size
            cur.removeAll { agora - it.ts >= 30 * 60000 }
            if (cur.size != antes) mudou = true
            for (i in cur.indices) {
                val b = cur[i]
                val g = AlertScheduler.gatilhoDe(b)
                if (!b.alerted && agora >= g - 60000 && agora < b.ts + 5 * 60000) {
                    val n = ultimaContagem(b.pracaId, Store.getLeituras(ctx))
                    cur[i] = b.copy(alerted = true); mudou = true
                    AlertHelper.disparar(ctx, b, n)
                }
            }
            if (mudou) Store.saveMarcacoes(ctx, cur)
            AlertScheduler.scheduleNext(ctx)
        } catch (e: Exception) { }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            try { AlertScheduler.scheduleNext(ctx.applicationContext) } catch (e: Exception) { }
        }
    }
}
