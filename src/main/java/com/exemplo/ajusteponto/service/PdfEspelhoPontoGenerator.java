package com.exemplo.ajusteponto.service;

import com.exemplo.ajusteponto.model.DiaPonto;
import com.exemplo.ajusteponto.model.RegistroBatida;
import com.exemplo.ajusteponto.model.RelatorioPonto;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Gerador de espelho de ponto em formato PDF institucional.
 * Destaca visualmente as batidas alteradas ou incluídas em NEGRITO.
 */
public class PdfEspelhoPontoGenerator {

    // Paleta de cores corporativa sóbria
    private static final Color AZUL_INSTITUCIONAL_ESCURO = new Color(26, 54, 93);   // #1A365D (Header principal)
    private static final Color AZUL_INSTITUCIONAL_MEDIO = new Color(30, 64, 175);  // #1E40AF (Header sugestão)
    private static final Color CINZA_SLATE_ORIGINAL = new Color(71, 85, 105);       // #475569 (Header original)
    private static final Color CINZA_SLATE_SUB_ORIGINAL = new Color(100, 116, 139); // #64748B (Sub-header original)
    private static final Color AZUL_SUB_SUGESTAO = new Color(37, 99, 235);          // #2563EB (Sub-header sugestão)
    private static final Color CINZA_CABECALHO = new Color(237, 242, 247);          // #EDF2F7
    private static final Color CINZA_LINHA_PAR = new Color(248, 250, 252);          // #F8FAFC
    private static final Color FUNDO_ORIGINAL_PAR = new Color(241, 245, 249);       // #F1F5F9
    private static final Color DESTAQUE_AJUSTE_BG = new Color(224, 242, 254);       // #E0F2FE (Azul claro destaque)
    private static final Color DESTAQUE_AJUSTE_TEXTO = new Color(15, 23, 42);       // #0F172A

    // Fontes
    private static final Font FONT_TITULO = new Font(Font.HELVETICA, 12, Font.BOLD, AZUL_INSTITUCIONAL_ESCURO);
    private static final Font FONT_SUBTITULO = new Font(Font.HELVETICA, 8.5f, Font.BOLD, AZUL_INSTITUCIONAL_MEDIO);
    private static final Font FONT_METADADOS_LABEL = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.BLACK);
    private static final Font FONT_METADADOS_VAL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.DARK_GRAY);
    private static final Font FONT_TH = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);
    private static final Font FONT_SUB_TH = new Font(Font.HELVETICA, 6.5f, Font.BOLD, Color.WHITE);
    private static final Font FONT_TD_NORMAL = new Font(Font.HELVETICA, 7f, Font.NORMAL, Color.DARK_GRAY);
    private static final Font FONT_TD_ORIGINAL = new Font(Font.HELVETICA, 7f, Font.NORMAL, new Color(51, 65, 85));
    private static final Font FONT_TD_ORIGINAL_VAZIO = new Font(Font.HELVETICA, 7f, Font.NORMAL, new Color(148, 163, 184));
    private static final Font FONT_TD_ALTERADO = new Font(Font.HELVETICA, 7.5f, Font.BOLD, DESTAQUE_AJUSTE_TEXTO);
    private static final Font FONT_RODAPE = new Font(Font.HELVETICA, 6.5f, Font.ITALIC, Color.GRAY);

    public static class ResultadoPdf {
        private final byte[] bytes;
        private final String base64;
        private final String caminhoArquivo;
        private final String dataUri;

        public ResultadoPdf(byte[] bytes, String base64, String caminhoArquivo) {
            this.bytes = bytes;
            this.base64 = base64;
            this.caminhoArquivo = caminhoArquivo;
            this.dataUri = "data:application/pdf;base64," + base64;
        }

        public byte[] getBytes() { return bytes; }
        public String getBase64() { return base64; }
        public String getCaminhoArquivo() { return caminhoArquivo; }
        public String getDataUri() { return dataUri; }
    }

    /**
     * Gera o documento PDF completo do espelho de ponto ajustado.
     */
    public ResultadoPdf gerarPdf(RelatorioPonto relatorio) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // 1. Cabeçalho Institucional
            adicionarCabecalho(doc, relatorio);

            // 2. Tabela de Registros com destaques em negrito
            adicionarTabelaBatidas(doc, relatorio);

            // 3. Quadro Resumo e Totalizadores
            adicionarQuadroResumo(doc, relatorio);

            // 4. Termo de Ciência e Assinaturas
            adicionarBlocoAssinaturas(doc);

            doc.close();

            byte[] pdfBytes = baos.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(pdfBytes);

            Path pastaRelatorios = Paths.get("relatorios");
            if (!Files.exists(pastaRelatorios)) {
                Files.createDirectories(pastaRelatorios);
            }
            String nomeArquivo = "espelho_ponto_" + relatorio.getProtocolo() + ".pdf";
            Path arquivoPath = pastaRelatorios.resolve(nomeArquivo);
            Files.write(arquivoPath, pdfBytes);

            return new ResultadoPdf(pdfBytes, base64, arquivoPath.toAbsolutePath().toString());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar o PDF do espelho de ponto: " + e.getMessage(), e);
        }
    }

    private void adicionarCabecalho(Document doc, RelatorioPonto relatorio) throws DocumentException {
        Paragraph pTitulo = new Paragraph("EMPRESA PÚBLICA DE TECNOLOGIA DA INFORMAÇÃO", FONT_TITULO);
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(pTitulo);

        Paragraph pSub = new Paragraph("Divisão de Recursos Humanos - DRH | Espelho de Ponto Ajustado (Horário Flexível)", FONT_SUBTITULO);
        pSub.setAlignment(Element.ALIGN_CENTER);
        pSub.setSpacingAfter(8);
        doc.add(pSub);

        PdfPTable metaTable = new PdfPTable(4);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{18, 32, 20, 30});
        metaTable.setSpacingAfter(10);

        adicionarCelulaMeta(metaTable, "Colaborador:", relatorio.getNomeFuncionario());
        adicionarCelulaMeta(metaTable, "Matrícula:", relatorio.getMatricula());
        adicionarCelulaMeta(metaTable, "Período:", relatorio.getPeriodo());
        adicionarCelulaMeta(metaTable, "Carga Horária:", "8h diárias / 40h semanais");
        adicionarCelulaMeta(metaTable, "Protocolo:", relatorio.getProtocolo());
        adicionarCelulaMeta(metaTable, "Regime:", "Horário Flexível com Compensação Diária");

        doc.add(metaTable);
    }

    private void adicionarCelulaMeta(PdfPTable table, String rotulo, String valor) {
        PdfPCell cRotulo = new PdfPCell(new Phrase(rotulo, FONT_METADADOS_LABEL));
        cRotulo.setBackgroundColor(CINZA_CABECALHO);
        cRotulo.setBorderColor(Color.LIGHT_GRAY);
        cRotulo.setPadding(4);
        table.addCell(cRotulo);

        PdfPCell cValor = new PdfPCell(new Phrase(valor, FONT_METADADOS_VAL));
        cValor.setBorderColor(Color.LIGHT_GRAY);
        cValor.setPadding(4);
        table.addCell(cValor);
    }

    private void adicionarTabelaBatidas(Document doc, RelatorioPonto relatorio) throws DocumentException {
        PdfPTable table = new PdfPTable(13);
        table.setWidthPercentage(100);
        // Colunas: Data (10%), Dia (8.5%), Originais (4 x 6.5% = 26%), Sugestão (4 x 6.5% = 26%), Total (8%), Saldo (8%), Status (13.5%)
        table.setWidths(new float[]{10.0f, 8.5f, 6.5f, 6.5f, 6.5f, 6.5f, 6.5f, 6.5f, 6.5f, 6.5f, 8.0f, 8.0f, 13.5f});

        // LINHA 1 DE CABEÇALHO (Agrupamento de Colunas)
        PdfPCell cData = new PdfPCell(new Phrase("Data", FONT_TH));
        cData.setRowspan(2);
        cData.setBackgroundColor(AZUL_INSTITUCIONAL_ESCURO);
        cData.setHorizontalAlignment(Element.ALIGN_CENTER);
        cData.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cData.setPadding(3);
        table.addCell(cData);

        PdfPCell cDia = new PdfPCell(new Phrase("Dia", FONT_TH));
        cDia.setRowspan(2);
        cDia.setBackgroundColor(AZUL_INSTITUCIONAL_ESCURO);
        cDia.setHorizontalAlignment(Element.ALIGN_CENTER);
        cDia.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cDia.setPadding(3);
        table.addCell(cDia);

        PdfPCell cGrupoOrig = new PdfPCell(new Phrase("REGISTRO INFORMADO (ORIGINAL)", FONT_TH));
        cGrupoOrig.setColspan(4);
        cGrupoOrig.setBackgroundColor(CINZA_SLATE_ORIGINAL);
        cGrupoOrig.setHorizontalAlignment(Element.ALIGN_CENTER);
        cGrupoOrig.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cGrupoOrig.setPadding(3);
        table.addCell(cGrupoOrig);

        PdfPCell cGrupoAjust = new PdfPCell(new Phrase("SUGESTÃO DE REGULARIZAÇÃO (8H)", FONT_TH));
        cGrupoAjust.setColspan(4);
        cGrupoAjust.setBackgroundColor(AZUL_INSTITUCIONAL_MEDIO);
        cGrupoAjust.setHorizontalAlignment(Element.ALIGN_CENTER);
        cGrupoAjust.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cGrupoAjust.setPadding(3);
        table.addCell(cGrupoAjust);

        PdfPCell cTotal = new PdfPCell(new Phrase("Total", FONT_TH));
        cTotal.setRowspan(2);
        cTotal.setBackgroundColor(AZUL_INSTITUCIONAL_ESCURO);
        cTotal.setHorizontalAlignment(Element.ALIGN_CENTER);
        cTotal.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cTotal.setPadding(3);
        table.addCell(cTotal);

        PdfPCell cSaldo = new PdfPCell(new Phrase("Saldo", FONT_TH));
        cSaldo.setRowspan(2);
        cSaldo.setBackgroundColor(AZUL_INSTITUCIONAL_ESCURO);
        cSaldo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cSaldo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cSaldo.setPadding(3);
        table.addCell(cSaldo);

        PdfPCell cStatus = new PdfPCell(new Phrase("Status", FONT_TH));
        cStatus.setRowspan(2);
        cStatus.setBackgroundColor(AZUL_INSTITUCIONAL_ESCURO);
        cStatus.setHorizontalAlignment(Element.ALIGN_CENTER);
        cStatus.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cStatus.setPadding(3);
        table.addCell(cStatus);

        // LINHA 2 DE CABEÇALHO (Sub-colunas de Horários)
        String[] subOrig = {"Ent 1", "Saí 1", "Ent 2", "Saí 2"};
        for (String sub : subOrig) {
            PdfPCell c = new PdfPCell(new Phrase(sub, FONT_SUB_TH));
            c.setBackgroundColor(CINZA_SLATE_SUB_ORIGINAL);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPadding(2.5f);
            table.addCell(c);
        }

        String[] subAjust = {"Ent 1", "Saí 1", "Ent 2", "Saí 2"};
        for (String sub : subAjust) {
            PdfPCell c = new PdfPCell(new Phrase(sub, FONT_SUB_TH));
            c.setBackgroundColor(AZUL_SUB_SUGESTAO);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPadding(2.5f);
            table.addCell(c);
        }

        // LINHAS DE DADOS
        boolean linhaPar = false;
        for (DiaPonto dia : relatorio.getDias()) {
            Color corFundo = dia.isFimDeSemana() ? new Color(241, 245, 249) : (linhaPar ? CINZA_LINHA_PAR : Color.WHITE);
            Color corFundoOrig = dia.isFimDeSemana() ? new Color(241, 245, 249) : (linhaPar ? FUNDO_ORIGINAL_PAR : new Color(248, 250, 252));

            adicionarCelulaDado(table, dia.getDataFormatada(), FONT_TD_NORMAL, corFundo, Element.ALIGN_CENTER);
            adicionarCelulaDado(table, dia.getDiaSemanaCurto(), FONT_TD_NORMAL, corFundo, Element.ALIGN_CENTER);

            // 4 Colunas dos registros originais
            adicionarCelulaOriginal(table, dia.getEntrada1Original(), corFundoOrig);
            adicionarCelulaOriginal(table, dia.getSaida1Original(), corFundoOrig);
            adicionarCelulaOriginal(table, dia.getEntrada2Original(), corFundoOrig);
            adicionarCelulaOriginal(table, dia.getSaida2Original(), corFundoOrig);

            // 4 Colunas da sugestão ajustada
            adicionarCelulaBatida(table, dia.getEntrada1(), corFundo);
            adicionarCelulaBatida(table, dia.getSaida1(), corFundo);
            adicionarCelulaBatida(table, dia.getEntrada2(), corFundo);
            adicionarCelulaBatida(table, dia.getSaida2(), corFundo);

            // Total, Saldo e Status
            adicionarCelulaDado(table, dia.getTotalTrabalhadoFormatado(), FONT_TD_NORMAL, corFundo, Element.ALIGN_CENTER);
            adicionarCelulaDado(table, dia.getSaldoDiaFormatado(), FONT_TD_NORMAL, corFundo, Element.ALIGN_CENTER);

            Font fontStatus = "AJUSTADO".equalsIgnoreCase(dia.getStatus()) ? FONT_TD_ALTERADO : FONT_TD_NORMAL;
            adicionarCelulaDado(table, dia.getStatus(), fontStatus, corFundo, Element.ALIGN_CENTER);

            linhaPar = !linhaPar;
        }

        doc.add(table);

        Paragraph legenda = new Paragraph(
                "* REGISTRO INFORMADO (ORIGINAL): Batidas originais submetidas pelo colaborador (--:-- indica ausência de marcação).\n" +
                "* SUGESTÃO DE REGULARIZAÇÃO (8H): Horários ajustados para cumprimento estrito de 8h diárias (sem horas extras). " +
                "Batidas alteradas/incluídas destacadas em NEGRITO e fundo azul.",
                FONT_RODAPE);
        legenda.setSpacingBefore(4);
        legenda.setSpacingAfter(8);
        doc.add(legenda);
    }

    private void adicionarCelulaOriginal(PdfPTable table, String hora, Color fundo) {
        boolean vazio = hora == null || hora.isBlank() || hora.equals("--:--");
        Font font = vazio ? FONT_TD_ORIGINAL_VAZIO : FONT_TD_ORIGINAL;
        adicionarCelulaDado(table, hora, font, fundo, Element.ALIGN_CENTER);
    }

    private void adicionarCelulaBatida(PdfPTable table, RegistroBatida batida, Color corPadrao) {
        if (batida == null || batida.getHorario() == null) {
            adicionarCelulaDado(table, "--:--", FONT_TD_NORMAL, corPadrao, Element.ALIGN_CENTER);
            return;
        }

        boolean alterado = batida.isFoiAlterado();
        Font font = alterado ? FONT_TD_ALTERADO : FONT_TD_NORMAL;
        Color bg = alterado ? DESTAQUE_AJUSTE_BG : corPadrao;
        String texto = batida.formatado();

        adicionarCelulaDado(table, texto, font, bg, Element.ALIGN_CENTER);
    }

    private void adicionarCelulaDado(PdfPTable table, String texto, Font font, Color fundo, int alinhamento) {
        PdfPCell c = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        c.setBackgroundColor(fundo);
        c.setBorderColor(new Color(226, 232, 240));
        c.setHorizontalAlignment(alinhamento);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        table.addCell(c);
    }

    private void adicionarQuadroResumo(Document doc, RelatorioPonto relatorio) throws DocumentException {
        PdfPTable resumo = new PdfPTable(4);
        resumo.setWidthPercentage(100);
        resumo.setWidths(new float[]{25, 25, 25, 25});
        resumo.setSpacingBefore(6);
        resumo.setSpacingAfter(10);

        adicionarCardResumo(resumo, "HORAS TRABALHADAS", relatorio.getTotalTrabalhadoFormatado(), AZUL_INSTITUCIONAL_MEDIO);
        adicionarCardResumo(resumo, "HORAS PREVISTAS", relatorio.getTotalEsperadoFormatado(), Color.DARK_GRAY);
        adicionarCardResumo(resumo, "SALDO DO PERÍODO", relatorio.getSaldoPeriodoFormatado(), AZUL_INSTITUCIONAL_ESCURO);
        adicionarCardResumo(resumo, "AJUSTES REALIZADOS", String.valueOf(relatorio.getTotalAjustesRealizados()) + " dia(s)", new Color(2, 132, 199));

        doc.add(resumo);
    }

    private void adicionarCardResumo(PdfPTable table, String rotulo, String valor, Color corDestaque) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(241, 245, 249));
        c.setBorderColor(new Color(203, 213, 225));
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pRotulo = new Paragraph(rotulo, new Font(Font.HELVETICA, 7, Font.BOLD, Color.GRAY));
        pRotulo.setAlignment(Element.ALIGN_CENTER);
        c.addElement(pRotulo);

        Paragraph pValor = new Paragraph(valor, new Font(Font.HELVETICA, 10, Font.BOLD, corDestaque));
        pValor.setAlignment(Element.ALIGN_CENTER);
        c.addElement(pValor);

        table.addCell(c);
    }

    private void adicionarBlocoAssinaturas(Document doc) throws DocumentException {
        Paragraph termo = new Paragraph(
                "Declaro para os devidos fins que conferi os registros originais informados e estou ciente da proposta de regularização " +
                        "de jornada acima descrita, confirmando a adequação aos limites do Regulamento de Horário Flexível vigente (DRH).",
                new Font(Font.HELVETICA, 7.5f, Font.ITALIC, Color.DARK_GRAY));
        termo.setAlignment(Element.ALIGN_JUSTIFIED);
        termo.setSpacingAfter(18);
        doc.add(termo);

        PdfPTable assinaturas = new PdfPTable(2);
        assinaturas.setWidthPercentage(100);
        assinaturas.setWidths(new float[]{50, 50});

        PdfPCell cColab = new PdfPCell();
        cColab.setBorder(Rectangle.NO_BORDER);
        cColab.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph p1 = new Paragraph("_________________________________________\nAssinatura do Colaborador",
                new Font(Font.HELVETICA, 8, Font.BOLD, Color.DARK_GRAY));
        p1.setAlignment(Element.ALIGN_CENTER);
        cColab.addElement(p1);
        assinaturas.addCell(cColab);

        PdfPCell cGestor = new PdfPCell();
        cGestor.setBorder(Rectangle.NO_BORDER);
        cGestor.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph p2 = new Paragraph("_________________________________________\nGestor Imediato (Chefia / DRH)",
                new Font(Font.HELVETICA, 8, Font.BOLD, Color.DARK_GRAY));
        p2.setAlignment(Element.ALIGN_CENTER);
        cGestor.addElement(p2);
        assinaturas.addCell(cGestor);

        doc.add(assinaturas);
    }
}
