package com.exemplo.ajusteponto;

import com.exemplo.ajusteponto.controller.PontoApiController;
import com.exemplo.ajusteponto.model.DiaPonto;
import com.exemplo.ajusteponto.model.RelatorioPonto;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator.ResultadoPdf;
import com.exemplo.ajusteponto.service.RegulamentoPontoEngine;
import com.google.adk.tools.Annotations.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferramentas (Tools) do Google ADK para o Agente de Ajuste de Ponto.
 */
public class PontoTools {

    private static final RegulamentoPontoEngine engine = new RegulamentoPontoEngine();
    private static final PdfEspelhoPontoGenerator pdfGenerator = new PdfEspelhoPontoGenerator();

    @Schema(description = "Processa os registros de ponto de um colaborador informados em texto ou arquivo, aplica as regras de horário flexível, ajusta automaticamente as inconsistências para atingir a meta de 8h diárias e gera o espelho de ponto em PDF com os horários alterados em negrito.")
    public static Map<String, Object> processarEAjustarPonto(
            @Schema(description = "Texto contendo as datas e horários das batidas (ex: '01/10/2026: 08:00 12:00 13:00' ou linhas em CSV/tabela)") String textoRegistros,
            @Schema(description = "Matrícula do funcionário (ex: MAT-12345)") String matricula,
            @Schema(description = "Nome completo do funcionário") String nomeFuncionario) {

        RelatorioPonto relatorio = engine.processarEAjustar(textoRegistros, matricula, nomeFuncionario);
        ResultadoPdf resultadoPdf = pdfGenerator.gerarPdf(relatorio);
        PontoApiController.registrarPdfNoCache(relatorio.getProtocolo(), resultadoPdf.getBytes());

        List<Map<String, Object>> diasDetalhados = new ArrayList<>();
        for (DiaPonto dia : relatorio.getDias()) {
            if (dia.isFimDeSemana()) continue;

            Map<String, Object> mDia = new LinkedHashMap<>();
            mDia.put("data", dia.getDataFormatada());
            mDia.put("diaSemana", dia.getDiaSemana());
            mDia.put("diaSemanaCurto", dia.getDiaSemanaCurto());

            // Batidas originais informadas pelo colaborador
            Map<String, String> orig = new LinkedHashMap<>();
            orig.put("entrada1", dia.getEntrada1Original());
            orig.put("saida1", dia.getSaida1Original());
            orig.put("entrada2", dia.getEntrada2Original());
            orig.put("saida2", dia.getSaida2Original());
            orig.put("formatado", dia.getOriginalFormatado());
            mDia.put("original", orig);

            // Sugestão de batidas ajustadas para 8h estritas
            Map<String, Object> sug = new LinkedHashMap<>();
            sug.put("entrada1", dia.getEntrada1() != null ? dia.getEntrada1().formatado() : "--:--");
            sug.put("entrada1Alterado", dia.getEntrada1() != null && dia.getEntrada1().isFoiAlterado());
            sug.put("saida1", dia.getSaida1() != null ? dia.getSaida1().formatado() : "--:--");
            sug.put("saida1Alterado", dia.getSaida1() != null && dia.getSaida1().isFoiAlterado());
            sug.put("entrada2", dia.getEntrada2() != null ? dia.getEntrada2().formatado() : "--:--");
            sug.put("entrada2Alterado", dia.getEntrada2() != null && dia.getEntrada2().isFoiAlterado());
            sug.put("saida2", dia.getSaida2() != null ? dia.getSaida2().formatado() : "--:--");
            sug.put("saida2Alterado", dia.getSaida2() != null && dia.getSaida2().isFoiAlterado());
            sug.put("formatado", dia.getAjustadoFormatado());
            mDia.put("sugerido", sug);

            // Manter compatibilidade com chaves diretas
            mDia.put("entrada1", dia.getEntrada1() != null ? dia.getEntrada1().formatado() : "--:--");
            mDia.put("saida1", dia.getSaida1() != null ? dia.getSaida1().formatado() : "--:--");
            mDia.put("entrada2", dia.getEntrada2() != null ? dia.getEntrada2().formatado() : "--:--");
            mDia.put("saida2", dia.getSaida2() != null ? dia.getSaida2().formatado() : "--:--");

            mDia.put("totalTrabalhado", dia.getTotalTrabalhadoFormatado());
            mDia.put("saldo", dia.getSaldoDiaFormatado());
            mDia.put("status", dia.getStatus());
            mDia.put("observacao", dia.getObservacao() != null ? dia.getObservacao() : "");
            diasDetalhados.add(mDia);
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("sucesso", true);
        resposta.put("protocolo", relatorio.getProtocolo());
        resposta.put("matricula", relatorio.getMatricula());
        resposta.put("nomeFuncionario", relatorio.getNomeFuncionario());
        resposta.put("periodo", relatorio.getPeriodo());
        resposta.put("totalTrabalhadoPeriodo", relatorio.getTotalTrabalhadoFormatado());
        resposta.put("totalEsperadoPeriodo", relatorio.getTotalEsperadoFormatado());
        resposta.put("saldoPeriodo", relatorio.getSaldoPeriodoFormatado());
        resposta.put("totalAjustesRealizados", relatorio.getTotalAjustesRealizados());
        resposta.put("dias", diasDetalhados);
        resposta.put("downloadUrl", "/api/ponto/download/" + relatorio.getProtocolo());
        resposta.put("caminhoArquivoPdf", resultadoPdf.getCaminhoArquivo());
        resposta.put("pdfBase64", resultadoPdf.getBase64());
        resposta.put("pdfDataUri", resultadoPdf.getDataUri());

        return resposta;
    }

    @Schema(description = "Consulta o regulamento oficial de horário flexível (Divisão de Recursos Humanos - DRH).")
    public static Map<String, Object> consultarRegrasHorarioFlexivel() {
        Map<String, Object> regras = new LinkedHashMap<>();
        regras.put("orgao", "Divisão de Recursos Humanos (DRH)");
        regras.put("regime", "Compensação diária das horas trabalhadas (8 horas diárias / 40 horas semanais)");
        regras.put("horarioFlexivelEntrada", "07:00 às 09:00");
        regras.put("horarioNucleoMatutino", "09:00 às 11:30 (Presença obrigatória - sem compensação)");
        regras.put("horarioFlexivelAlmoco", "11:30 às 14:00 (Mínimo de 30 minutos e máximo de 02 horas e 30 minutos)");
        regras.put("horarioNucleoVespertino", "14:00 às 17:00 (Presença obrigatória - sem compensação)");
        regras.put("horarioFlexivelSaida", "17:00 às 19:00");
        regras.put("jornadaMinimaTurno", "03 horas ininterruptas");
        regras.put("jornadaMaximaTurno", "05 horas");
        return regras;
    }
}
