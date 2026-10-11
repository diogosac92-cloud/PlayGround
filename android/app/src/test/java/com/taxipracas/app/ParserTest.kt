package com.taxipracas.app

import org.junit.Assert.*
import org.junit.Test

// Paridade com os testes da versão web (t6/t10/t11b/t14/t16).
class ParserTest {

    private fun pracas() = listOf(
        Praca("rio", "Av. Rio de Janeiro"),
        Praca("pon", "Pontinha"),
        Praca("ent", "Entrecampos")
    )

    @Test
    fun ecraNaPraca_TaxiLink() {
        val f1 = "taxi-link 3381 Diogo Hilario Ouro Taxi-Link\n" +
            "Av. Rio de Janeiro\n00:36 em praca\n1o na fila\n1\n3381\n" +
            "Ocupado Extras Servicos Sair Praca Comunicacao Pracas Ceder Vez Inicio"
        val r = extrairTaxilink(f1)
        assertEquals("praca", r.ecra)
        assertEquals(1, r.posicao)
        assertEquals("00:36", r.tempoPraca)
        assertEquals(1, r.emEspera)
        assertEquals("rio", pracaPorTexto(f1, pracas())?.id)
    }

    @Test
    fun ecraPesquisa_TaxiLink() {
        val f2 = "taxi-link Pontinha\na 4991 metros\nLugares\n2 taxis\n" +
            "1 com a mesma prioridade\n0 ocupados nos ultimos quinze minutos\n" +
            "Taxis na praca\n1 6\n2 459"
        val r = extrairTaxilink(f2)
        assertEquals("pesquisa", r.ecra)
        assertEquals(2, r.emEspera)
        assertEquals(0, r.saidas15)
        assertEquals(1, r.mesmaPrioridade)
        assertEquals(4991, r.distanciaM)
        assertEquals(2, r.fila.size)
    }

    @Test
    fun ecraLista_TaxiLink() {
        val f3 = "Ordem: Distancia (asc.) Favoritas\n0 0 taxis oc/15\n" +
            "Chiado - Igreja do Loreto\na 4914 metros\n1 0 taxis oc/15\n" +
            "Jardim da Estrela\na 4932 metros\n0 1 taxis oc/15\n" +
            "Pontinha\na 4991 metros\n1 0 taxis oc/15\nSul e Sueste\na 5350 metros"
        assertEquals("lista", detetarEcra(f3))
        val l = extrairLista(f3)
        assertEquals(4, l.size)
        assertEquals("Pontinha", l[2].nome)
        assertEquals(4991, l[2].dist)
        assertEquals(0, l[2].n)
        assertEquals(1, l[2].saidas15)
    }

    @Test
    fun ecraPrincipal_TaxiDigital() {
        val td = "(CNTD) Livre PT.RIO JANEIRO 4\nPOSIÇÃO HISTÓRICO DEFINA STATUS/ESTADO\n" +
            "ZONAS COMUNICAÇÃO COM A CENTRAL PENDENTES/OFERTAS\n14:56 63%"
        val r = extrairTaxilink(td)
        assertEquals("praca", r.ecra)
        assertEquals("LIVRE", r.tdStatus)
        assertEquals("PT.RIO JANEIRO", r.tdLocal)
        assertEquals(4, r.tdPos)
        assertEquals(4, r.emEspera)
        assertEquals("rio", pracaPorToken("PT.RIO JANEIRO", pracas())?.id)
        val zo = extrairTaxilink("(CNTD) Livre MAFRA CONCELHO 1 POSIÇÃO")
        assertNull(pracaPorToken("MAFRA CONCELHO", pracas()))
        assertEquals("MAFRA CONCELHO", zo.tdLocal)
    }

    @Test
    fun marcacoes_TaxiLink() {
        val foto = "taxi-link Marcações\nPraça Lumiar\n11/10/2026, 15:40 (10m)\n" +
            "Praça Hospital de S. José\n11/10/2026, 16:00 (15m)\n" +
            "Praça Hotel Metropolitan\n11/10/2026, 16:15 (15m)\nOfertas"
        assertEquals("marc", detetarEcra(foto))
        // usa datas futuras relativas a hoje para não expirar
        val cal = java.util.Calendar.getInstance()
        val dma = "%02d/%02d/%04d".format(
            cal.get(java.util.Calendar.DATE),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.YEAR)
        )
        val t2 = "Marcações\nPraça Lumiar\n$dma, 23:40 (10m)\nOfertas"
        val ms = extrairMarcacoes(t2)
        assertEquals(1, ms.size)
        assertEquals(10, ms[0].aviso)
        assertTrue(ms[0].firme)
        val d = java.util.Calendar.getInstance().apply { timeInMillis = ms[0].ts }
        assertEquals(23, d.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals("Praça Lumiar", ms[0].pracaNome)
    }

    @Test
    fun ranking_fundeDuasApps() {
        val agora = System.currentTimeMillis()
        val l = listOf(
            Leitura("l1", "rio", "Taxi-Link", 1, agora - 20 * 60000, "x", null, null, null, null),
            Leitura("l2", "rio", "TaxiDigital", 4, agora - 5 * 60000, "x", null, null, null, null)
        )
        val s = statsPorPraca("rio", l)
        assertNotNull(s)
        assertEquals(2, s!!.n)
        assertEquals(4, s.atual)
        val rows = calcRanking(pracas(), l)
        assertEquals("rio", rows[0].praca.id)
    }

    @Test
    fun distFator_curva() {
        assertEquals(1.0, distFator(0), 0.001)
        assertEquals(0.5, distFator(3000), 0.01)
        assertEquals(0.67, distFator(1500), 0.03)
    }
}
