package com.exemplo.ajusteponto.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa uma marcação de ponto individual.
 */
public class RegistroBatida {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private LocalTime horario;
    private boolean foiAlterado;
    private String observacao;

    public RegistroBatida() {}

    public RegistroBatida(LocalTime horario, boolean foiAlterado, String observacao) {
        this.horario = horario;
        this.foiAlterado = foiAlterado;
        this.observacao = observacao;
    }

    public static RegistroBatida original(String horaStr) {
        if (horaStr == null || horaStr.isBlank() || horaStr.equals("--:--")) {
            return new RegistroBatida(null, false, "Batida não registrada originalmente");
        }
        return new RegistroBatida(LocalTime.parse(horaStr.trim(), TIME_FMT), false, null);
    }

    public static RegistroBatida ajustado(LocalTime horario, String motivo) {
        return new RegistroBatida(horario, true, motivo);
    }

    public LocalTime getHorario() {
        return horario;
    }

    public void setHorario(LocalTime horario) {
        this.horario = horario;
    }

    public boolean isFoiAlterado() {
        return foiAlterado;
    }

    public void setFoiAlterado(boolean foiAlterado) {
        this.foiAlterado = foiAlterado;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String formatado() {
        return horario != null ? horario.format(TIME_FMT) : "--:--";
    }

    @Override
    public String toString() {
        return formatado() + (foiAlterado ? " (Ajustado)" : "");
    }
}
