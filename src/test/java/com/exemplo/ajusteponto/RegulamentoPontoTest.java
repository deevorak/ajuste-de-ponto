package com.exemplo.ajusteponto;

import com.exemplo.ajusteponto.model.DiaPonto;
import com.exemplo.ajusteponto.model.RelatorioPonto;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator.ResultadoPdf;
import com.exemplo.ajusteponto.service.RegulamentoPontoEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RegulamentoPontoTest {

    @Test
    public void testFechamentoEstritoDe8HorasSemHorasExtras() {
        RegulamentoPontoEngine engine = new RegulamentoPontoEngine();

        // Cenário:
        // Dia 1: 01/10/2026 (Quinta) 08:00 12:00 13:00 (faltou a saída 2) -> saída ajustada para 17:00 (8h00)
        // Dia 2: 02/10/2026 (Sexta) 08:00 12:00 13:00 18:30 (fez 9h30 -> sem hora extra, saída reduzida para 17:00, 8h00)
        // Dia 3: 05/10/2026 (Segunda) 08:30 12:00 13:00 16:30 (saiu cedo -> saída estendida para 17:30, 8h00)
        String dadosEntrada = """
            01/10/2026: 08:00 12:00 13:00
            02/10/2026: 08:00 12:00 13:00 18:30
            05/10/2026: 08:30 12:00 13:00 16:30
            """;

        RelatorioPonto relatorio = engine.processarEAjustar(dadosEntrada, "MAT-99887", "Carlos Silva");

        Assertions.assertNotNull(relatorio);
        Assertions.assertEquals("MAT-99887", relatorio.getMatricula());
        Assertions.assertEquals(3, relatorio.getDias().size());

        // Dia 1: Faltou saída 2 -> ajustada para 17:00 (4h + 4h = 8h00)
        DiaPonto d1 = relatorio.getDias().get(0);
        Assertions.assertEquals("08:00", d1.getTotalTrabalhadoFormatado(), "Dia 1 deve ter exatamente 8h00");
        Assertions.assertEquals("17:00", d1.getSaida2().formatado());
        Assertions.assertTrue(d1.getSaida2().isFoiAlterado(), "Saída do dia 1 deve estar em negrito");
        Assertions.assertEquals("08:00", d1.getEntrada1Original(), "Entrada 1 original deve ser 08:00");
        Assertions.assertEquals("--:--", d1.getSaida2Original(), "Saída 2 original ausente deve ser --:--");
        Assertions.assertEquals(Integer.valueOf(-240), d1.getDiferencaMinutosOriginal(), "Faltaram 240 minutos no dia 1");
        Assertions.assertEquals("-240 min", d1.getDiferencaMinutosOriginalFormatado());

        // Dia 2: Teve 1h30 a mais (18:30) -> Saída 2 DEVE ser alterada para 17:00 para fechar exatamente 8h00
        DiaPonto d2 = relatorio.getDias().get(1);
        Assertions.assertEquals("08:00", d2.getTotalTrabalhadoFormatado(), "Dia 2 NÃO pode ter horas extras; deve fechar exatamente 8h00");
        Assertions.assertEquals("17:00", d2.getSaida2().formatado(), "Saída 2 deve ter sido reduzida para 17:00");
        Assertions.assertTrue(d2.getSaida2().isFoiAlterado(), "Saída do dia 2 alterada deve estar marcada para negrito");
        Assertions.assertEquals("18:30", d2.getSaida2Original(), "Saída 2 original informada deve ser 18:30");
        Assertions.assertEquals(Integer.valueOf(90), d2.getDiferencaMinutosOriginal(), "Sobraram 90 minutos no dia 2");
        Assertions.assertEquals("+90 min", d2.getDiferencaMinutosOriginalFormatado());

        // Dia 3: Entrada 08:30 às 12:00 (3h30). Para fechar 8h00, o 2º turno deve ser de 4h30 (13:00 às 17:30)
        DiaPonto d3 = relatorio.getDias().get(2);
        Assertions.assertEquals("08:00", d3.getTotalTrabalhadoFormatado(), "Dia 3 deve fechar exatamente 8h00");
        Assertions.assertEquals("17:30", d3.getSaida2().formatado(), "Saída 2 deve ser estendida para 17:30 para fechar 8h00");
        Assertions.assertTrue(d3.getSaida2().isFoiAlterado(), "Saída do dia 3 alterada deve estar marcada para negrito");
        Assertions.assertEquals("16:30", d3.getSaida2Original(), "Saída 2 original informada deve ser 16:30");
        Assertions.assertEquals(Integer.valueOf(-60), d3.getDiferencaMinutosOriginal(), "Faltaram 60 minutos no dia 3");
        Assertions.assertEquals("-60 min", d3.getDiferencaMinutosOriginalFormatado());

        // Saldo total do período DEVE ser exatamente 00:00 (sem horas extras)
        Assertions.assertEquals("24:00", relatorio.getTotalTrabalhadoFormatado());
        Assertions.assertEquals("24:00", relatorio.getTotalEsperadoFormatado());
        Assertions.assertEquals("00:00", relatorio.getSaldoPeriodoFormatado());

        // Teste de geração do PDF
        PdfEspelhoPontoGenerator generator = new PdfEspelhoPontoGenerator();
        ResultadoPdf pdf = generator.gerarPdf(relatorio);
        Assertions.assertNotNull(pdf);
        Assertions.assertTrue(pdf.getBytes().length > 1000);
    }
}
