package com.taxipracas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Armazenamento local em JSON (sem dependências). Mesmos dados da versão web.
object Store {
    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    private fun readArr(ctx: Context, name: String): JSONArray {
        return try {
            val f = file(ctx, name)
            if (!f.exists()) JSONArray() else JSONArray(f.readText())
        } catch (e: Exception) { JSONArray() }
    }

    private fun writeArr(ctx: Context, name: String, a: JSONArray) {
        try { file(ctx, name).writeText(a.toString()) } catch (e: Exception) { }
    }

    @Synchronized
    fun getPracas(ctx: Context): MutableList<Praca> {
        val a = readArr(ctx, "pracas.json")
        if (a.length() == 0) {
            val seeds = seedPracas()
            savePracas(ctx, seeds)
            return seeds.toMutableList()
        }
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Praca(o.getString("id"), o.getString("nome"))
        }
    }

    @Synchronized
    fun savePracas(ctx: Context, list: List<Praca>) {
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("id", p.id).put("nome", p.nome))
        writeArr(ctx, "pracas.json", a)
    }

    @Synchronized
    fun addPraca(ctx: Context, nome: String): Praca {
        val list = getPracas(ctx)
        list.find { norm(it.nome) == norm(nome) }?.let { return it }
        val p = Praca(uid(), nome.trim())
        list.add(p)
        savePracas(ctx, list)
        return p
    }

    @Synchronized
    fun getLeituras(ctx: Context): MutableList<Leitura> {
        val a = readArr(ctx, "leituras.json")
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Leitura(
                o.getString("id"), o.getString("pracaId"), o.optString("fonte", ""),
                o.getInt("n"), o.getLong("ts"), o.optString("metodo", ""),
                if (o.isNull("ecra")) null else o.optString("ecra"),
                if (o.isNull("saidas15")) null else o.optInt("saidas15"),
                if (o.isNull("distM")) null else o.optInt("distM"),
                if (o.isNull("pos")) null else o.optInt("pos")
            )
        }
    }

    @Synchronized
    fun addLeitura(ctx: Context, l: Leitura) {
        val a = readArr(ctx, "leituras.json")
        val o = JSONObject().put("id", l.id).put("pracaId", l.pracaId).put("fonte", l.fonte)
            .put("n", l.n).put("ts", l.ts).put("metodo", l.metodo)
        if (l.ecra != null) o.put("ecra", l.ecra)
        if (l.saidas15 != null) o.put("saidas15", l.saidas15)
        if (l.distM != null) o.put("distM", l.distM)
        if (l.pos != null) o.put("pos", l.pos)
        a.put(o)
        // cap a 2000 leituras
        while (a.length() > 2000) a.remove(0)
        writeArr(ctx, "leituras.json", a)
    }

    @Synchronized
    fun getMarcacoes(ctx: Context): MutableList<Marcacao> {
        val a = readArr(ctx, "marcacoes.json")
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Marcacao(
                o.getString("id"), o.getString("key"), o.getLong("ts"),
                if (o.isNull("pracaId")) null else o.optString("pracaId"),
                if (o.isNull("pracaNome")) null else o.optString("pracaNome"),
                if (o.isNull("aviso")) null else o.optInt("aviso"),
                o.optString("fonte", ""), o.optBoolean("alerted", false)
            )
        }
    }

    @Synchronized
    fun saveMarcacoes(ctx: Context, list: List<Marcacao>) {
        val a = JSONArray()
        for (b in list) {
            val o = JSONObject().put("id", b.id).put("key", b.key).put("ts", b.ts)
                .put("fonte", b.fonte).put("alerted", b.alerted)
            if (b.pracaId != null) o.put("pracaId", b.pracaId)
            if (b.pracaNome != null) o.put("pracaNome", b.pracaNome)
            if (b.aviso != null) o.put("aviso", b.aviso)
            a.put(o)
        }
        writeArr(ctx, "marcacoes.json", a)
    }
}
