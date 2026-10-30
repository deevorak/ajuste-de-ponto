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
    public void testAjusteAutomaticoRegrasHorarioFlexivel() {
        RegulamentoPontoEngine engine = new RegulamentoPontoEngine();

        // Cenário:
        // Dia 1: 08:00 12:00 13:00 (faltou a batida de saída do 2º turno)
        // Dia 2: 08:00 17:00 (faltou marcar almoço)
        // Dia 3: 08:30 12:00 13:00 16:30 (saiu antes, precisa compensar para 8h)
        String dadosEntrada = """
            01/10/2026: 08:00 12:00 13:00
            02/10/2026: 08:00 17:00
            03/10/2026: 08:30 12:00 13:00 16:30
            """;

        RelatorioPonto relatorio = engine.processarEAjustar(dadosEntrada, "MAT-99887", "Carlos Silva");

        Assertions.assertNotNull(relatorio);
        Assertions.assertEquals("MAT-99887", relatorio.getMatricula());
        Assertions.assertEquals(3, relatorio.getDias().size());

        // Dia 1: Saída 2 deve ser inserida como 17:00 e marcada como alterada
        DiaPonto d1 = relatorio.getDias().get(0);
        Assertions.assertNotNull(d1.getSaida2());
        Assertions.assertEquals("17:00", d1.getSaida2().formatado());
        Assertions.assertTrue(d1.getSaida2().isFoiAlterado(), "A saída 2 do dia 1 deve estar marcada como alterada para ficar em negrito no PDF");
        Assertions.assertEquals("08:00", d1.getTotalTrabalhadoFormatado());

        // Dia 2: Almoço deve ser inserido (12:00 às 13:00)
        DiaPonto d2 = relatorio.getDias().get(1);
        Assertions.assertNotNull(d2.getSaida1());
        Assertions.assertNotNull(d2.getEntrada2());
        Assertions.assertTrue(d2.getSaida1().isFoiAlterado());
        Assertions.assertTrue(d2.getEntrada2().isFoiAlterado());
        Assertions.assertEquals("08:00", d2.getTotalTrabalhadoFormatado());

        // Geração do PDF
        PdfEspelhoPontoGenerator generator = new PdfEspelhoPontoGenerator();
        ResultadoPdf pdf = generator.gerarPdf(relatorio);

        Assertions.assertNotNull(pdf);
        Assertions.assertTrue(pdf.getBytes().length > 1000, "O PDF gerado deve conter conteúdo válido");
        Assertions.assertNotNull(pdf.getBase64());
        Assertions.assertTrue(pdf.getDataUri().startsWith("data:application/pdf;base64,"));
    }
}
