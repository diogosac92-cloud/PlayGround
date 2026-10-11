package com.taxipracas.app

import java.text.Normalizer

// Resultado da leitura de um ecrã (mesmos campos da versão web)
data class LeituraInfo(
    val placas: List<String> = emptyList(),
    val licencas: List<String> = emptyList(),
    val posicao: Int? = null,
    val tempoPraca: String? = null,
    val fila: List<Pair<Int, String>> = emptyList(),
    val mesmaPrioridade: Int? = null,
    val distanciaM: Int? = null,
    val tdStatus: String? = null,
    val tdLocal: String? = null,
    val tdPos: Int? = null,
    val tempos: List<String> = emptyList(),
    val nums: List<Int> = emptyList(),
    val sugerido: Int? = null,
    val emEspera: Int? = null,
    val saidas15: Int? = null,
    val ecra: String? = null
)

data class ListaRow(val nome: String, val dist: Int?, val n: Int, val saidas15: Int)
data class MarcExtra(val key: String, val ts: Long, val pracaId: String?, val pracaNome: String?, val firme: Boolean, val aviso: Int?)

fun norm(s: String?): String {
    if (s == null) return ""
    return Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

fun detetarEcra(txt: String?): String? {
    val t = (txt ?: "").uppercase()
    fun has(vararg res: Regex) = res.any { it.containsMatchIn(t) }
    if (has(Regex("MARCA[CÇ][AÃÕ][OE]S?"), Regex("RESERVA"), Regex("AGENDAD")) && !has(Regex("OC\\s*/\\s*15"))) return "marc"
    if (has(Regex("ORDEM:"), Regex("FAVORITAS"), Regex("OC\\s*/\\s*15"))) return "lista"
    if (has(Regex("PESQUIS"), Regex("PROCUR"), Regex("OUTRA?\\s*PRA[CÇ]A"), Regex("LISTA\\s*DE\\s*PRA[CÇ]AS"), Regex("ESCOLHER\\s*PRA[CÇ]A"), Regex("VER\\s*PRA[CÇ]A"), Regex("SELECION"), Regex("LUGARES"), Regex("MESMA\\s*PRIORIDADE"), Regex("OCUPADOS"), Regex("QUINZE\\s*MINUTOS"), Regex("METROS"), Regex("T[AÁ]XIS\\s+NA\\s+PRA[CÇ]A"))) return "pesquisa"
    if (has(Regex("MINHA\\s*PRA[CÇ]A"), Regex("ESTOU\\s*NA"), Regex("\\bNA\\s*PRA[CÇ]A\\b"), Regex("\\bEM\\s*PRA[CÇ]A\\b"), Regex("MINHA\\s*FILA"), Regex("SA[IÍ]RAM"), Regex("POSI[CÇ][AÃ]O"), Regex("NA\\s*FILA"), Regex("CEDER\\s*VEZ"), Regex("SAIR\\s*PRA[CÇ]A"), Regex("CNTD"), Regex("PENDENTES/OFERTAS"), Regex("DEFINA STATUS"), Regex("COMUNICA[CÇ][AÃ]O COM A CENTRAL"))) return "praca"
    return null
}

private fun fixD(s: String) = s.replace("O", "0").replace("I", "1").replace("L", "1")

fun extrairInfo(txt: String?): LeituraInfo {
    val raw = txt ?: ""
    val t = raw.uppercase()
    // Matrículas PT (reserva)
    val placas = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    val re = Regex("\\b(?:(\\d{2})[\\s.\\-–]?([A-Z]{2})[\\s.\\-–]?(\\d{2})|([A-Z]{2})[\\s.\\-–]?(\\d{2})[\\s.\\-–]?([A-Z]{2}))\\b")
    for (m in re.findAll(t)) {
        val disp = if (m.groups[1] != null)
            "${fixD(m.groups[1]!!.value)}-${m.groups[2]!!.value}-${fixD(m.groups[3]!!.value)}"
        else
            "${m.groups[4]!!.value}-${fixD(m.groups[5]!!.value)}-${m.groups[6]!!.value}"
        if (seen.add(disp)) placas.add(disp)
    }
    // Nºs de licença etiquetados
    val licSet = mutableSetOf<String>()
    val licencas = mutableListOf<String>()
    val licRes = listOf(
        Regex("LICEN[CÇ]AS?\\s*(?:(?:NO\\.?|N[º°.]?)\\s*)?(\\d{1,5})"),
        Regex("\\bLIC\\.?\\s*(?:(?:NO\\.?|N[º°.]?)\\s*)?(\\d{1,5})"),
        Regex("T[AÁ]XIS?\\s*(?:NO\\.?|N[º°.]?)\\s*(\\d{1,5})"),
        Regex("ALVAR[AÁ]S?\\s*(?:(?:NO\\.?|N[º°.]?)\\s*)?(\\d{1,5})"),
        Regex("VIATURA\\s*(?:(?:NO\\.?|N[º°.]?)\\s*)?(\\d{1,5})"),
        Regex("\\bN[º°]\\s*(\\d{1,5})")
    )
    for (r in licRes) for (m in r.findAll(t)) {
        val v = m.groups[1]!!.value.trimStart('0').ifEmpty { "0" }
        if (licSet.add(v)) licencas.add(v)
    }
    // Posição: "Posição 5" ou "1º na fila"
    var posicao: Int? = null
    val pm = Regex("POSI[CÇ][AÃ]O\\s*(?:(?:NO\\.?|N[º°.]?)\\s*)?(\\d{1,3})").find(t)
        ?: Regex("(\\d{1,3})\\s*(?:[º°O]\\s*)?NA\\s*FILA").find(t)
    if (pm != null) {
        val v = pm.groups[1]!!.value.toIntOrNull()
        if (v != null && v in 1..500) posicao = v
    }
    // Tempo em praça "00:36 em praça"
    var tempoPraca: String? = null
    Regex("(\\d{1,2}:\\d{2})\\s*EM\\s*PRA[CÇ]A").find(t)?.let { tempoPraca = it.groups[1]!!.value }
    // Caixa da fila: linhas "posição licença"
    val fila = mutableListOf<Pair<Int, String>>()
    for (ln in raw.split("\n")) {
        if (":" in ln) continue
        val toks = Regex("\\d+").findAll(ln).map { it.value.toInt() }.toList()
        if (toks.size == 2 && toks[0] in 1..60 && toks[1] <= 99999) fila.add(toks[0] to toks[1].toString())
    }
    // Mesma prioridade / distância (ecrã pesquisa Taxi-Link)
    var mesmaPrioridade: Int? = null
    Regex("(\\d{1,3})\\s*COM\\s*A\\s*MESMA\\s*PRIORIDADE").find(t)?.let {
        val v = it.groups[1]!!.value.toIntOrNull(); if (v != null && v <= 500) mesmaPrioridade = v
    }
    var distanciaM: Int? = null
    Regex("\\bA\\s*([\\d.]+)\\s*METROS").find(t)?.let {
        distanciaM = it.groups[1]!!.value.replace(".", "").toIntOrNull()
    }
    if (distanciaM == null) Regex("\\bA\\s*([\\d.,]+)\\s*KM").find(t)?.let {
        distanciaM = ((it.groups[1]!!.value.replace(",", ".").toFloatOrNull() ?: return@let) * 1000).toInt()
    }
    // Cabeçalho TaxiDigital "(CNTD) Livre PT.RIO JANEIRO 4"
    var tdStatus: String? = null
    var tdLocal: String? = null
    var tdPos: Int? = null
    val th = t.replace(Regex("\\d{1,2}:\\d{2}"), " ").replace(Regex("\\d{1,3}\\s*%"), " ")
    Regex("\\(?CNTD\\)?\\s*(LIVRE|OCUPADO|EM\\s+SERVI[CÇ]O|AUSENTE|PAUSA)\\s+([A-ZÀ-Ú0-9 .\\-]{2,45})").find(th)?.let { h ->
        tdStatus = h.groups[1]!!.value.replace(Regex("\\s+"), " ")
        // o OCR às vezes junta linhas: corta em palavras de secção (nunca fazem parte do local)
        var loc = h.groups[2]!!.value.split(Regex("\\s+(POSI[CÇ][AÃ]O|HIST[OÓ]RICO|ZONAS|DEFINA|COMUNICA|PENDENTES|OFERTAS)\\b"))[0].trim()
        Regex("^(.*?)\\s+(\\d{1,3})$").find(loc)?.let { p ->
            val pv = p.groups[2]!!.value.toIntOrNull()
            if (pv != null && pv in 1..500) { loc = p.groups[1]!!.value.trim(); tdPos = pv }
        }
        if (loc.isNotEmpty()) tdLocal = loc
    }
    val tempos = (Regex("\\b\\d{1,3}\\s?min\\b", RegexOption.IGNORE_CASE).findAll(raw).map { it.value }
        + Regex("\\b\\d{1,2}:\\d{2}\\b").findAll(raw).map { it.value })
        .filter { !it.matches(Regex("(?i)^15\\s?min$")) }.take(8).toList()
    val nums = Regex("\\d{1,3}").findAll(raw).map { it.value.toInt() }.filter { it <= 500 }.toList()
    val sugerido = when {
        licencas.isNotEmpty() -> licencas.size
        placas.isNotEmpty() -> placas.size
        nums.isNotEmpty() -> nums[0]
        else -> null
    }
    return LeituraInfo(placas, licencas, posicao, tempoPraca, fila, mesmaPrioridade, distanciaM,
        tdStatus, tdLocal, tdPos, tempos, nums, sugerido)
}

fun extrairTaxilink(txt: String?): LeituraInfo {
    val raw = txt ?: ""
    val t = raw.replace(Regex("\\s+"), " ").uppercase().replace("QUINZE", "15")
    var saidas15: Int? = null
    val pats = listOf(
        Regex("(\\d{1,3})\\s*(?:CARROS?|TAXIS?|VIATURAS?|SERVI[CÇ]OS?|CHAMADAS?|SA[IÍ]DAS?)\\s*[^.]{0,40}15\\s*MIN", RegexOption.IGNORE_CASE),
        Regex("15\\s*MIN[^\\d.]{0,12}(\\d{1,3})", RegexOption.IGNORE_CASE),
        Regex("SA[IÍ]RAM?\\s*[^\\d.]{0,10}(\\d{1,3})", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,3})\\s*SA[IÍ]RAM", RegexOption.IGNORE_CASE),
        Regex("[UÚ]LTIMOS?\\s*15\\s*MIN[^\\d.]{0,12}(\\d{1,3})", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,3})\\s*[^\\d]{0,25}[UÚ]LTIMOS?\\s*15", RegexOption.IGNORE_CASE)
    )
    for (re in pats) {
        val v = re.find(t)?.groups?.get(1)?.value?.toIntOrNull()
        if (v != null && v <= 500) { saidas15 = v; break }
    }
    val base = extrairInfo(txt)
    var emEspera = base.licencas.size.takeIf { it > 0 }
        ?: base.fila.size.takeIf { it > 0 }
        ?: base.placas.size.takeIf { it > 0 }
        ?: base.posicao ?: base.tdPos
    if (emEspera == null) {
        val tnum = t.replace(Regex("\\bA\\s*[\\d.]+\\s*METROS"), " ")
            .replace(Regex("\\bA\\s*[\\d.,]+\\s*KM"), " ")
            .replace(Regex("\\d{1,2}:\\d{2}"), " ")
            .replace(Regex("\\d{1,3}\\s*%"), " ")
        val fb = Regex("\\d{1,3}").findAll(tnum).map { it.value.toInt() }
            .filter { it <= 500 && it != 15 && it != saidas15 }.toList()
        emEspera = fb.firstOrNull()
    }
    return base.copy(emEspera = emEspera, saidas15 = saidas15, ecra = detetarEcra(txt))
}

// Conjunto extra só usado no ecrã de lista (Taxi-Link)
fun extrairListaRows(txt: String?): List<ListaRow> = extrairLista(txt)

fun extrairLista(txt: String?): List<ListaRow> {
    val src = txt ?: ""
    val reC = Regex("(\\d{1,3})\\s+(\\d{1,3})\\s*t[aá]xis\\s*oc\\s*/\\s*15", RegexOption.IGNORE_CASE)
    val marks = reC.findAll(src).map {
        Triple(it.groups[1]!!.value.toInt(), it.groups[2]!!.value.toInt(), it.range)
    }.toList()
    fun limpa(s: String): Pair<String, Int?> {
        val d = Regex("a\\s+([\\d.]+)\\s*metros", RegexOption.IGNORE_CASE).find(s)
        var nome = ""
        for (ln in s.split("\n")) {
            if (Regex("ordem|favorita", RegexOption.IGNORE_CASE).containsMatchIn(ln)) continue
            var cand = ln.replace(Regex("[^A-Za-zÀ-Úà-ú .\\-']"), " ").replace(Regex("\\s{2,}"), " ").trim()
                .trim { !it.isLetter() }
            if (cand.length > nome.length) nome = cand
        }
        if (Regex("^(ordem|favoritas|inicio|extras|servicos|comunic|pracas|taxi)", RegexOption.IGNORE_CASE).containsMatchIn(nome)) nome = ""
        return nome to d?.groups?.get(1)?.value?.replace(".", "")?.toIntOrNull()
    }
    val rows = mutableListOf<ListaRow>()
    for (k in marks.indices) {
        val antes = if (k == 0) "" else src.substring(marks[k - 1].third.last + 1, marks[k].third.first)
        val depois = if (k + 1 < marks.size) src.substring(marks[k].third.last + 1, marks[k + 1].third.first) else src.substring(marks[k].third.last + 1)
        val a = limpa(antes); val d = limpa(depois)
        var sel: Pair<String, Int?>? = null
        if (d.first.length >= 3) { sel = d; if (sel.second == null && a.second != null) sel = sel.copy(second = a.second) }
        else if (a.first.length >= 3) sel = a
        if (sel == null || sel.first.length < 3) continue
        rows.add(ListaRow(sel.first, sel.second, marks[k].first, marks[k].second))
    }
    return rows
}

fun extrairMarcacoes(txt: String?): List<MarcExtra> {
    val out = mutableListOf<MarcExtra>()
    val t = txt ?: ""
    fun push(ts: Long, pracaId: String?, pracaNome: String?, firme: Boolean, aviso: Int?) {
        if (ts <= 0) return
        val key = "$ts|${pracaId ?: "-"}"
        if (out.any { it.key == key }) return
        out.add(MarcExtra(key, ts, pracaId, pracaNome, firme, aviso))
    }
    fun nomeEm(ctx: String, pracas: List<Praca>): Pair<String?, String?> {
        pracaPorTexto(ctx, pracas)?.let { return it.id to it.nome }
        matchPracaId(ctx, pracas)?.let { return it.id to it.nome }
        val reN = Regex("PRA[CÇ]A\\s+[A-ZÀ-Ú][A-ZÀ-Úa-zà-ú .\\-']{1,40}", RegexOption.IGNORE_CASE)
        var nm: String? = null
        for (m in reN.findAll(ctx)) nm = m.value
        return null to nm?.trim()?.replace(Regex("\\s{2,}"), " ")
    }
    // Formato real: "Praça Lumiar 11/10/2026, 15:40 (10m)"
    val reD = Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\s*,\\s*(\\d{1,2})[:hH](\\d{2})")
    var lastEnd = 0
    for (m in reD.findAll(t)) {
        val h = m.groups[4]!!.value.toInt(); val mi = m.groups[5]!!.value.toInt()
        if (h > 23 || mi > 59) { lastEnd = m.range.last + 1; continue }
        var yy = m.groups[3]?.value?.toInt() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (yy < 100) yy += 2000
        val cal = java.util.Calendar.getInstance()
        cal.set(yy, m.groups[2]!!.value.toInt() - 1, m.groups[1]!!.value.toInt(), h, mi, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val ts = cal.timeInMillis
        val ctx = t.substring(maxOf(lastEnd, m.range.first - 150), m.range.first)
        lastEnd = m.range.last + 1
        if (ts < System.currentTimeMillis() - 30 * 60000) continue
        val (pid, pnome) = nomeEm(ctx, emptyList())
        val av = Regex("\\(\\s*(\\d{1,3})\\s*m\\s*\\)").find(t.substring(m.range.first, minOf(t.length, m.range.first + 120)))?.groups?.get(1)?.value?.toIntOrNull()
        push(ts, pid, pnome, true, av)
    }
    if (Regex("MARCA[CÇ][AÃÕ][OE]S?|RESERVA|AGENDAD", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
        val reH = Regex("(\\d{1,2})[:hH](\\d{2})")
        for (g in reH.findAll(t)) {
            val ctx = t.substring(maxOf(0, g.range.first - 100), minOf(t.length, g.range.first + 100))
            if (!Regex("MARCA[CÇ][AÃÕ][OE]S?|RESERVA|AGENDAD", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) continue
            val h = g.groups[1]!!.value.toInt(); val mi = g.groups[2]!!.value.toInt()
            if (h > 23 || mi > 59) continue
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.set(java.util.Calendar.HOUR_OF_DAY, h); cal.set(java.util.Calendar.MINUTE, mi)
            if (cal.timeInMillis < System.currentTimeMillis() - 5 * 60000) cal.add(java.util.Calendar.DATE, 1)
            val (pid, pnome) = nomeEm(ctx, emptyList())
            push(cal.timeInMillis, pid, pnome, false, null)
        }
    }
    return out
}

// ---- matching de praças ----

private val TD_STOP = setOf("PRAÇA", "PRACA", "PRACAS", "AV", "AVENIDA", "RUA", "LARGO", "PT", "DE", "DA", "DO", "DAS", "DOS", "E", "S", "SAO", "SÃO", "SANTO", "SANTA", "CONCELHO", "ZONA", "ZONAS")

fun sigWords(s: String?): List<String> =
    norm(s).uppercase().split(Regex("[^A-Z]+")).filter { it.length >= 3 && it !in TD_STOP }

fun pracaPorTexto(txt: String?, pracas: List<Praca>): Praca? {
    val t = norm(txt)
    var best: Praca? = null; var bestLen = 0
    for (p in pracas) {
        val n = norm(p.nome)
        if (n.length >= 4 && t.contains(n) && n.length > bestLen) { best = p; bestLen = n.length }
    }
    return best
}

fun matchPracaId(nome: String?, pracas: List<Praca>): Praca? {
    val t = norm(nome)
    var best: Praca? = null; var bestLen = 0
    for (p in pracas) {
        val n = norm(p.nome)
        if (n.length >= 4 && (t.contains(n) || n.contains(t)) && n.length > bestLen) { best = p; bestLen = n.length }
    }
    return best
}

fun pracaPorToken(local: String?, pracas: List<Praca>): Praca? {
    val lw = sigWords(local); if (lw.isEmpty()) return null
    val need = if (lw.size == 1) 1 else 2
    var best: Praca? = null; var bestN = 0
    for (p in pracas) {
        val pw = sigWords(p.nome)
        val n = lw.count { it in pw }
        if (n >= need && n > bestN) { best = p; bestN = n }
    }
    return best
}

/** Casa texto+info com praça existente. Devolve Pair(id, nome) ou null. Criação feita pelo chamador. */
fun matchPracaAuto(txt: String?, info: LeituraInfo, pracas: List<Praca>): Pair<String, String>? {
    pracaPorTexto(txt, pracas)?.let { return it.id to it.nome }
    val loc = info.tdLocal
    if (loc != null) {
        if (Regex("MAFRA", RegexOption.IGNORE_CASE).containsMatchIn(loc)) return null
        pracaPorToken(loc, pracas)?.let { return it.id to it.nome }
    }
    return null
}

fun isNovaPracaTd(tdLocal: String?): Boolean {
    if (tdLocal == null) return false
    if (Regex("MAFRA", RegexOption.IGNORE_CASE).containsMatchIn(tdLocal)) return false
    return Regex("PT\\.|PRA[CÇ]A", RegexOption.IGNORE_CASE).containsMatchIn(tdLocal)
}
