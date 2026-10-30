package com.exemplo.ajusteponto.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Contém o espelho de ponto consolidado do colaborador para um período.
 */
public class RelatorioPonto {
    private String protocolo;
    private String matricula;
    private String nomeFuncionario;
    private String periodo;
    private List<DiaPonto> dias = new ArrayList<>();
    private Duration totalTrabalhadoPeriodo = Duration.ZERO;
    private Duration totalEsperadoPeriodo = Duration.ZERO;
    private Duration saldoPeriodo = Duration.ZERO;
    private int totalAjustesRealizados = 0;

    public RelatorioPonto() {}

    public RelatorioPonto(String protocolo, String matricula, String nomeFuncionario, String periodo) {
        this.protocolo = protocolo;
        this.matricula = matricula;
        this.nomeFuncionario = nomeFuncionario;
        this.periodo = periodo;
    }

    public void recalcularTotais() {
        long minutosTrabalhados = 0;
        long minutosEsperados = 0;
        int ajustes = 0;

        for (DiaPonto dia : dias) {
            if (dia.isFimDeSemana()) continue;

            if (dia.getTotalTrabalhado() != null) {
                minutosTrabalhados += dia.getTotalTrabalhado().toMinutes();
            }
            // 8 horas por dia útil previsto
            minutosEsperados += 480;

            if ("AJUSTADO".equalsIgnoreCase(dia.getStatus())) {
                ajustes++;
            }
        }

        this.totalTrabalhadoPeriodo = Duration.ofMinutes(minutosTrabalhados);
        this.totalEsperadoPeriodo = Duration.ofMinutes(minutosEsperados);
        this.saldoPeriodo = Duration.ofMinutes(minutosTrabalhados - minutosEsperados);
        this.totalAjustesRealizados = ajustes;
    }

    public String formatarDuracao(Duration dur, boolean comSinal) {
        if (dur == null) return "00:00";
        long totalMin = dur.toMinutes();
        boolean negativo = totalMin < 0;
        long absMin = Math.abs(totalMin);
        long horas = absMin / 60;
        long minutos = absMin % 60;
        String prefixo = "";
        if (comSinal) {
            prefixo = negativo ? "-" : (totalMin > 0 ? "+" : "");
        }
        return String.format("%s%02d:%02d", prefixo, horas, minutos);
    }

    public String getTotalTrabalhadoFormatado() {
        return formatarDuracao(totalTrabalhadoPeriodo, false);
    }

    public String getTotalEsperadoFormatado() {
        return formatarDuracao(totalEsperadoPeriodo, false);
    }

    public String getSaldoPeriodoFormatado() {
        return formatarDuracao(saldoPeriodo, true);
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getNomeFuncionario() {
        return nomeFuncionario;
    }

    public void setNomeFuncionario(String nomeFuncionario) {
        this.nomeFuncionario = nomeFuncionario;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public List<DiaPonto> getDias() {
        return dias;
    }

    public void setDias(List<DiaPonto> dias) {
        this.dias = dias;
        recalcularTotais();
    }

    public Duration getTotalTrabalhadoPeriodo() {
        return totalTrabalhadoPeriodo;
    }

    public Duration getTotalEsperadoPeriodo() {
        return totalEsperadoPeriodo;
    }

    public Duration getSaldoPeriodo() {
        return saldoPeriodo;
    }

    public int getTotalAjustesRealizados() {
        return totalAjustesRealizados;
    }
}
