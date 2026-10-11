package com.taxipracas.app

// Apps-alvo lidas pela vigia (pacotes Android)
val TARGET_PKGS = setOf(
    "pt.geolink.taxilinkdriver", // Taxi-Link motorista
    "br.com.taxidigital",        // TaxiDigital motorista
    "br.com.taxidigitallite",    // TaxiDigital motorista lite
    "net.txidigital.izzymove"    // IzzyMove / CNTD
)

data class Praca(val id: String, val nome: String)

data class Leitura(
    val id: String,
    val pracaId: String,
    val fonte: String,
    val n: Int,
    val ts: Long,
    val metodo: String,
    val ecra: String?,
    val saidas15: Int?,
    val distM: Int?,
    val pos: Int?
)

data class Marcacao(
    val id: String,
    val key: String,
    val ts: Long,
    val pracaId: String?,
    val pracaNome: String?,
    val aviso: Int?,
    val fonte: String,
    val alerted: Boolean
)

fun uid(): String = System.currentTimeMillis().toString(36) + (Math.random() * 1e6).toInt().toString(36)

fun seedPracas(): List<Praca> {
    val nomes = listOf(
        "Aeroporto T1", "Av. Rio de Janeiro", "Santa Apolónia", "Oriente",
        "Cais do Sodré", "Jardim do Tabaco", "Pontinha", "Chiado - Igreja do Loreto",
        "Jardim da Estrela", "Largo do Chiado", "Largo de Camões", "Terminal de Cruzeiros",
        "Estrada dos Arneiros", "Praça do Comércio", "D. Carlos I", "Sul e Sueste", "Remolares"
    )
    return nomes.map { Praca(uid(), it) }
}
