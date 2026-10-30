package com.exemplo.ajusteponto.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Representa as marcações e o balanço de horas de um dia de trabalho.
 */
public class DiaPonto {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private LocalDate data;

    // Registros originais informados pelo colaborador
    private String entrada1Original;
    private String saida1Original;
    private String entrada2Original;
    private String saida2Original;

    // Registros ajustados/sugeridos pelo agente para cumprir o regulamento
    private RegistroBatida entrada1;
    private RegistroBatida saida1;
    private RegistroBatida entrada2;
    private RegistroBatida saida2;
    private Duration totalTrabalhado;
    private Duration saldoDia;
    private String status;
    private String observacao;

    public DiaPonto() {}

    public DiaPonto(LocalDate data, RegistroBatida entrada1, RegistroBatida saida1,
                    RegistroBatida entrada2, RegistroBatida saida2) {
        this.data = data;
        this.entrada1 = entrada1;
        this.saida1 = saida1;
        this.entrada2 = entrada2;
        this.saida2 = saida2;

        this.entrada1Original = (entrada1 != null && entrada1.getHorario() != null) ? entrada1.formatado() : "--:--";
        this.saida1Original = (saida1 != null && saida1.getHorario() != null) ? saida1.formatado() : "--:--";
        this.entrada2Original = (entrada2 != null && entrada2.getHorario() != null) ? entrada2.formatado() : "--:--";
        this.saida2Original = (saida2 != null && saida2.getHorario() != null) ? saida2.formatado() : "--:--";

        calcularTotais();
    }

    public void calcularTotais() {
        long minutosTotal = 0;

        if (entrada1 != null && entrada1.getHorario() != null && saida1 != null && saida1.getHorario() != null) {
            minutosTotal += Duration.between(entrada1.getHorario(), saida1.getHorario()).toMinutes();
        }

        if (entrada2 != null && entrada2.getHorario() != null && saida2 != null && saida2.getHorario() != null) {
            minutosTotal += Duration.between(entrada2.getHorario(), saida2.getHorario()).toMinutes();
        }

        this.totalTrabalhado = Duration.ofMinutes(Math.max(0, minutosTotal));
        // Jornada padrão diária: 8 horas (480 minutos)
        long saldoMinutos = minutosTotal - 480;
        this.saldoDia = Duration.ofMinutes(saldoMinutos);

        boolean temAlteracao = (entrada1 != null && entrada1.isFoiAlterado())
                || (saida1 != null && saida1.isFoiAlterado())
                || (entrada2 != null && entrada2.isFoiAlterado())
                || (saida2 != null && saida2.isFoiAlterado());

        if (temAlteracao) {
            this.status = "AJUSTADO";
        } else if (minutosTotal == 480) {
            this.status = "REGULAR";
        } else if (minutosTotal == 0) {
            this.status = "SEM REGISTRO";
        } else {
            this.status = saldoMinutos > 0 ? "HORAS EXTRAS" : "DÉBITO";
        }
    }

    public boolean isFimDeSemana() {
        if (data == null) return false;
        DayOfWeek dow = data.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    public String getDataFormatada() {
        return data != null ? data.format(DATE_FMT) : "";
    }

    public String getDiaSemana() {
        if (data == null) return "";
        String nome = data.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        return nome.substring(0, 1).toUpperCase(PT_BR) + nome.substring(1);
    }

    public String formatarDuracao(Duration dur) {
        if (dur == null) return "00:00";
        long totalMin = dur.toMinutes();
        boolean negativo = totalMin < 0;
        long absMin = Math.abs(totalMin);
        long horas = absMin / 60;
        long minutos = absMin % 60;
        String prefixo = negativo ? "-" : (totalMin > 0 ? "+" : "");
        return String.format("%s%02d:%02d", prefixo, horas, minutos);
    }

    public String getTotalTrabalhadoFormatado() {
        if (totalTrabalhado == null) return "00:00";
        long totalMin = totalTrabalhado.toMinutes();
        long horas = totalMin / 60;
        long minutos = totalMin % 60;
        return String.format("%02d:%02d", horas, minutos);
    }

    public String getSaldoDiaFormatado() {
        return formatarDuracao(saldoDia);
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public RegistroBatida getEntrada1() {
        return entrada1;
    }

    public void setEntrada1(RegistroBatida entrada1) {
        this.entrada1 = entrada1;
    }

    public RegistroBatida getSaida1() {
        return saida1;
    }

    public void setSaida1(RegistroBatida saida1) {
        this.saida1 = saida1;
    }

    public RegistroBatida getEntrada2() {
        return entrada2;
    }

    public void setEntrada2(RegistroBatida entrada2) {
        this.entrada2 = entrada2;
    }

    public RegistroBatida getSaida2() {
        return saida2;
    }

    public void setSaida2(RegistroBatida saida2) {
        this.saida2 = saida2;
    }

    public Duration getTotalTrabalhado() {
        return totalTrabalhado;
    }

    public Duration getSaldoDia() {
        return saldoDia;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String getEntrada1Original() {
        return entrada1Original != null ? entrada1Original : "--:--";
    }

    public void setEntrada1Original(String entrada1Original) {
        this.entrada1Original = entrada1Original;
    }

    public String getSaida1Original() {
        return saida1Original != null ? saida1Original : "--:--";
    }

    public void setSaida1Original(String saida1Original) {
        this.saida1Original = saida1Original;
    }

    public String getEntrada2Original() {
        return entrada2Original != null ? entrada2Original : "--:--";
    }

    public void setEntrada2Original(String entrada2Original) {
        this.entrada2Original = entrada2Original;
    }

    public String getSaida2Original() {
        return saida2Original != null ? saida2Original : "--:--";
    }

    public void setSaida2Original(String saida2Original) {
        this.saida2Original = saida2Original;
    }

    public String getOriginalFormatado() {
        return String.format("%s  %s  %s  %s",
                getEntrada1Original(), getSaida1Original(), getEntrada2Original(), getSaida2Original());
    }

    public String getAjustadoFormatado() {
        return String.format("%s  %s  %s  %s",
                entrada1 != null ? entrada1.formatado() : "--:--",
                saida1 != null ? saida1.formatado() : "--:--",
                entrada2 != null ? entrada2.formatado() : "--:--",
                saida2 != null ? saida2.formatado() : "--:--");
    }

    public String getDiaSemanaCurto() {
        if (data == null) return "";
        return switch (data.getDayOfWeek()) {
            case MONDAY -> "Seg";
            case TUESDAY -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY -> "Qui";
            case FRIDAY -> "Sex";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
    }
}
