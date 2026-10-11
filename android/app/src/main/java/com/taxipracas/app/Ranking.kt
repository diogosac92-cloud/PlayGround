package com.taxipracas.app

// Matemática igual à versão web: ritmo, espera, score, desconto de distância.
data class PracaStats(
    val atual: Int, val media: Double, val saidas: Int, val taxaH: Double,
    val esperaH: Double, val score: Double, val n: Int, val taxaTL: Double?
)

data class RankRow(
    val praca: Praca, val s: PracaStats, val distM: Int?,
    val scoreF: Double
)

fun statsPorPraca(pracaId: String, leituras: List<Leitura>): PracaStats? {
    val l = leituras.filter { it.pracaId == pracaId }.sortedBy { it.ts }
    if (l.isEmpty()) return null
    val vals = l.map { it.n }
    val media = vals.average()
    var saidas = 0
    for (i in 1 until l.size) { val d = l[i - 1].n - l[i].n; if (d > 0) saidas += d }
    val horas = if (l.size > 1) maxOf((l.last().ts - l.first().ts) / 3600000.0, 1.0 / 60) else 0.0
    var taxaH = if (horas > 0) saidas / horas else 0.0
    val agora = System.currentTimeMillis()
    val tl = l.filter { it.saidas15 != null && agora - it.ts < 90 * 60000 }.map { it.saidas15!! }
    var taxaTL: Double? = null
    if (tl.isNotEmpty()) { taxaTL = tl.average() * 4; taxaH = maxOf(taxaH, taxaTL) }
    val atual = l.last().n
    val esperaH = if (taxaH > 0) (atual + 1) / taxaH else Double.POSITIVE_INFINITY
    return PracaStats(atual, media, saidas, taxaH, esperaH, taxaH / (atual + 1), l.size, taxaTL)
}

fun ultimaDistancia(pracaId: String, leituras: List<Leitura>): Int? {
    val agora = System.currentTimeMillis()
    return leituras.filter { it.pracaId == pracaId && it.distM != null && agora - it.ts < 30 * 60000 }
        .maxByOrNull { it.ts }?.distM
}

fun distFator(m: Int, half: Int = 3000): Double = 1.0 / (1.0 + maxOf(0, m) / half.toDouble())

fun fmtEspera(h: Double): String {
    if (!h.isFinite()) return "s/ dados"
    val m = (h * 60).toInt()
    if (m < 1) return "<1 min"
    if (m < 60) return "~${m}min"
    return "~${m / 60}h${(m % 60).toString().padStart(2, '0')}"
}

fun fmtDist(m: Int): String = if (m < 1000) "$m m" else "${m / 1000},${(m % 1000) / 100} km"

fun calcRanking(pracas: List<Praca>, leituras: List<Leitura>): List<RankRow> {
    // janela fixa 4h como na web simplificada
    val cut = System.currentTimeMillis() - 4 * 3600000L
    val l = leituras.filter { it.ts >= cut }
    return pracas.mapNotNull { p ->
        val s = statsPorPraca(p.id, l) ?: return@mapNotNull null
        val dd = ultimaDistancia(p.id, leituras)
        val fd = if (dd != null) distFator(dd) else 1.0
        RankRow(p, s, dd, s.score * fd)
    }.sortedByDescending { it.scoreF }
}

fun ultimaContagem(pracaId: String?, leituras: List<Leitura>): Int? {
    if (pracaId == null) return null
    val l = leituras.filter { it.pracaId == pracaId }.maxByOrNull { it.ts }
    if (l == null || System.currentTimeMillis() - l.ts > 45 * 60000) return null
    return l.n
}
