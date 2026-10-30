package com.exemplo.ajusteponto.service;

import com.exemplo.ajusteponto.model.DiaPonto;
import com.exemplo.ajusteponto.model.RegistroBatida;
import com.exemplo.ajusteponto.model.RelatorioPonto;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de regras do Regulamento de Horário Flexível e ajuste automático de inconsistências no espelho de ponto.
 */
public class RegulamentoPontoEngine {

    public static final LocalTime ENTRADA_FLEXIVEL_INICIO = LocalTime.of(7, 0);
    public static final LocalTime ENTRADA_FLEXIVEL_FIM = LocalTime.of(9, 0);

    public static final LocalTime HORARIO_NUCLEO_1_INICIO = LocalTime.of(9, 0);
    public static final LocalTime HORARIO_NUCLEO_1_FIM = LocalTime.of(11, 30);

    public static final LocalTime ALMOCO_FLEXIVEL_INICIO = LocalTime.of(11, 30);
    public static final LocalTime ALMOCO_FLEXIVEL_FIM = LocalTime.of(14, 0);

    public static final LocalTime HORARIO_NUCLEO_2_INICIO = LocalTime.of(14, 0);
    public static final LocalTime HORARIO_NUCLEO_2_FIM = LocalTime.of(17, 0);

    public static final LocalTime SAIDA_FLEXIVEL_INICIO = LocalTime.of(17, 0);
    public static final LocalTime SAIDA_FLEXIVEL_FIM = LocalTime.of(19, 0);

    public static final int JORNADA_PADRAO_MINUTOS = 480; // 8 horas
    public static final int TURNO_MINIMO_MINUTOS = 180;    // 3 horas
    public static final int TURNO_MAXIMO_MINUTOS = 300;    // 5 horas
    public static final int ALMOCO_MINIMO_MINUTOS = 30;    // 30 minutos
    public static final int ALMOCO_MAXIMO_MINUTOS = 150;   // 2 horas e 30 minutos

    private static final Pattern PATTERN_HORA = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern PATTERN_DATA = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}/\\d{2}/\\d{4})|(\\d{2}/\\d{2})");

    /**
     * Processa texto bruto ou conteúdo de arquivo e aplica o ajuste de acordo com as regras de horário flexível.
     */
    public RelatorioPonto processarEAjustar(String textoEntrada, String matricula, String nomeFuncionario) {
        String mat = (matricula != null && !matricula.isBlank()) ? matricula : "MAT-" + (10000 + new Random().nextInt(90000));
        String nome = (nomeFuncionario != null && !nomeFuncionario.isBlank()) ? nomeFuncionario : "Colaborador";
        String protocolo = "AJUSTE-AJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<DiaPonto> dias = extrairDias(textoEntrada);

        if (dias.isEmpty()) {
            dias = gerarPeriodoExemplo();
        }

        for (DiaPonto dia : dias) {
            ajustarDiaRegulamento(dia);
        }

        String periodo = identificarPeriodo(dias);
        RelatorioPonto relatorio = new RelatorioPonto(protocolo, mat, nome, periodo);
        relatorio.setDias(dias);

        return relatorio;
    }

    /**
     * Aplica o regulamento de horário flexível e ajusta automaticamente batidas faltantes ou jornada incompleta.
     */
    public void ajustarDiaRegulamento(DiaPonto dia) {
        if (dia.isFimDeSemana()) {
            dia.setStatus("RECESSO/FDS");
            return;
        }

        LocalTime e1 = dia.getEntrada1() != null ? dia.getEntrada1().getHorario() : null;
        LocalTime s1 = dia.getSaida1() != null ? dia.getSaida1().getHorario() : null;
        LocalTime e2 = dia.getEntrada2() != null ? dia.getEntrada2().getHorario() : null;
        LocalTime s2 = dia.getSaida2() != null ? dia.getSaida2().getHorario() : null;

        // Caso 1: Dia totalmente sem registros
        if (e1 == null && s1 == null && e2 == null && s2 == null) {
            dia.setEntrada1(RegistroBatida.ajustado(LocalTime.of(8, 0), "Inclusão automática 1º turno"));
            dia.setSaida1(RegistroBatida.ajustado(LocalTime.of(12, 0), "Inclusão automática almoço"));
            dia.setEntrada2(RegistroBatida.ajustado(LocalTime.of(13, 0), "Inclusão automática retorno almoço"));
            dia.setSaida2(RegistroBatida.ajustado(LocalTime.of(17, 0), "Inclusão automática término expediente"));
            dia.setObservacao("Todas as batidas foram inseridas automaticamente para cumprir a jornada regular de 8h.");
            dia.calcularTotais();
            return;
        }

        // Caso 2: Somente 2 batidas informadas (início e término geral, sem almoço)
        if (e1 != null && s1 == null && e2 == null && s2 != null) {
            dia.setSaida1(RegistroBatida.ajustado(LocalTime.of(12, 0), "Inclusão de saída para almoço (mínimo 30m / máx 2h30)"));
            dia.setEntrada2(RegistroBatida.ajustado(LocalTime.of(13, 0), "Inclusão de retorno de almoço"));
            s1 = dia.getSaida1().getHorario();
            e2 = dia.getEntrada2().getHorario();
        }

        // Caso 3: Falta batida de entrada 1
        if (e1 == null) {
            if (s1 != null) {
                LocalTime calcE1 = s1.minusHours(4);
                if (calcE1.isBefore(ENTRADA_FLEXIVEL_INICIO)) calcE1 = ENTRADA_FLEXIVEL_INICIO;
                dia.setEntrada1(RegistroBatida.ajustado(calcE1, "Inclusão de batida de início de turno"));
            } else {
                dia.setEntrada1(RegistroBatida.ajustado(LocalTime.of(8, 0), "Inclusão de batida de início de turno"));
            }
            e1 = dia.getEntrada1().getHorario();
        }

        // Caso 4: Falta intervalo de almoço (saída 1 ou entrada 2)
        if (s1 == null && e2 != null) {
            LocalTime calcAlmoco = e2.minusHours(1);
            if (calcAlmoco.isBefore(ALMOCO_FLEXIVEL_INICIO)) calcAlmoco = ALMOCO_FLEXIVEL_INICIO;
            dia.setSaida1(RegistroBatida.ajustado(calcAlmoco, "Inclusão de saída de almoço"));
            s1 = dia.getSaida1().getHorario();
        } else if (s1 != null && e2 == null) {
            LocalTime calcRetorno = s1.plusHours(1);
            if (calcRetorno.isAfter(ALMOCO_FLEXIVEL_FIM)) calcRetorno = ALMOCO_FLEXIVEL_FIM;
            dia.setEntrada2(RegistroBatida.ajustado(calcRetorno, "Inclusão de retorno de almoço"));
            e2 = dia.getEntrada2().getHorario();
        } else if (s1 == null && e2 == null) {
            dia.setSaida1(RegistroBatida.ajustado(LocalTime.of(12, 0), "Inclusão automática almoço"));
            dia.setEntrada2(RegistroBatida.ajustado(LocalTime.of(13, 0), "Inclusão automática retorno almoço"));
            s1 = dia.getSaida1().getHorario();
            e2 = dia.getEntrada2().getHorario();
        }

        // Valida limites do intervalo de almoço (mínimo 30m, máximo 2h30)
        long duracaoAlmoco = Duration.between(s1, e2).toMinutes();
        if (duracaoAlmoco < ALMOCO_MINIMO_MINUTOS) {
            dia.setEntrada2(RegistroBatida.ajustado(s1.plusMinutes(ALMOCO_MINIMO_MINUTOS),
                    "Ajuste para respeitar intervalo mínimo de almoço de 30 minutos"));
            e2 = dia.getEntrada2().getHorario();
        } else if (duracaoAlmoco > ALMOCO_MAXIMO_MINUTOS) {
            dia.setEntrada2(RegistroBatida.ajustado(s1.plusMinutes(ALMOCO_MAXIMO_MINUTOS),
                    "Ajuste para respeitar intervalo máximo de almoço de 2h30"));
            e2 = dia.getEntrada2().getHorario();
        }

        // Caso 5: Falta batida de saída final (saída 2) ou jornada diária incompleta
        long minutosTurno1 = Duration.between(e1, s1).toMinutes();

        if (s2 == null) {
            long minutosNecessariosTurno2 = JORNADA_PADRAO_MINUTOS - minutosTurno1;
            if (minutosNecessariosTurno2 < TURNO_MINIMO_MINUTOS) minutosNecessariosTurno2 = TURNO_MINIMO_MINUTOS;
            if (minutosNecessariosTurno2 > TURNO_MAXIMO_MINUTOS) minutosNecessariosTurno2 = TURNO_MAXIMO_MINUTOS;

            LocalTime calcSaida2 = e2.plusMinutes(minutosNecessariosTurno2);
            if (calcSaida2.isBefore(HORARIO_NUCLEO_2_FIM)) {
                calcSaida2 = HORARIO_NUCLEO_2_FIM;
            }
            if (calcSaida2.isAfter(SAIDA_FLEXIVEL_FIM)) {
                calcSaida2 = SAIDA_FLEXIVEL_FIM;
            }

            dia.setSaida2(RegistroBatida.ajustado(calcSaida2, "Inclusão automática para completar 8h diárias"));
            s2 = dia.getSaida2().getHorario();
        } else {
            long totalMinutosAtual = minutosTurno1 + Duration.between(e2, s2).toMinutes();
            long diferenca = JORNADA_PADRAO_MINUTOS - totalMinutosAtual;

            if (diferenca > 0 && diferenca <= 120) {
                LocalTime novaSaida2 = s2.plusMinutes(diferenca);
                if (!novaSaida2.isAfter(SAIDA_FLEXIVEL_FIM)) {
                    dia.setSaida2(RegistroBatida.ajustado(novaSaida2,
                            "Ajuste de saída no horário flexível para integralizar compensação de 8h diárias"));
                }
            }
        }

        // Validação final de horário núcleo obrigatório (09:00-11:30 e 14:00-17:00)
        List<String> avisos = new ArrayList<>();
        if (e1.isAfter(HORARIO_NUCLEO_1_INICIO)) {
            avisos.add("Entrada após 09:00 viola horário núcleo matutino.");
        }
        if (s1.isBefore(HORARIO_NUCLEO_1_FIM)) {
            avisos.add("Saída de almoço antes das 11:30 viola horário núcleo matutino.");
        }
        if (e2.isAfter(HORARIO_NUCLEO_2_INICIO)) {
            avisos.add("Retorno de almoço após as 14:00 viola horário núcleo vespertino.");
        }
        if (dia.getSaida2().getHorario().isBefore(HORARIO_NUCLEO_2_FIM)) {
            avisos.add("Saída antes das 17:00 viola horário núcleo vespertino.");
        }

        if (!avisos.isEmpty()) {
            dia.setObservacao(String.join(" ", avisos));
        }

        dia.calcularTotais();
    }

    public List<DiaPonto> extrairDias(String texto) {
        List<DiaPonto> resultado = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return resultado;
        }

        String[] linhas = texto.split("\\r?\\n");
        LocalDate dataAtual = LocalDate.now().withDayOfMonth(1);

        for (String linha : linhas) {
            String l = linha.trim();
            if (l.isBlank() || l.startsWith("#") || l.toLowerCase().startsWith("data")) {
                continue;
            }

            LocalDate data = extrairData(l);
            if (data == null) {
                data = dataAtual;
                dataAtual = dataAtual.plusDays(1);
            }

            List<LocalTime> horarios = extrairHorarios(l);
            if (horarios.isEmpty()) {
                continue;
            }

            RegistroBatida eb1 = null;
            RegistroBatida sb1 = null;
            RegistroBatida eb2 = null;
            RegistroBatida sb2 = null;

            if (horarios.size() == 1) {
                eb1 = RegistroBatida.original(horarios.get(0).toString());
            } else if (horarios.size() == 2) {
                eb1 = RegistroBatida.original(horarios.get(0).toString());
                sb2 = RegistroBatida.original(horarios.get(1).toString());
            } else if (horarios.size() == 3) {
                eb1 = RegistroBatida.original(horarios.get(0).toString());
                sb1 = RegistroBatida.original(horarios.get(1).toString());
                eb2 = RegistroBatida.original(horarios.get(2).toString());
            } else if (horarios.size() >= 4) {
                eb1 = RegistroBatida.original(horarios.get(0).toString());
                sb1 = RegistroBatida.original(horarios.get(1).toString());
                eb2 = RegistroBatida.original(horarios.get(2).toString());
                sb2 = RegistroBatida.original(horarios.get(3).toString());
            }

            DiaPonto dia = new DiaPonto(data, eb1, sb1, eb2, sb2);
            resultado.add(dia);
        }

        return resultado;
    }

    private LocalDate extrairData(String linha) {
        Matcher m = PATTERN_DATA.matcher(linha);
        if (m.find()) {
            String match = m.group();
            try {
                if (match.contains("-")) {
                    return LocalDate.parse(match, DateTimeFormatter.ISO_LOCAL_DATE);
                } else if (match.length() == 10) {
                    return LocalDate.parse(match, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } else if (match.length() == 5) {
                    int ano = LocalDate.now().getYear();
                    return LocalDate.parse(match + "/" + ano, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<LocalTime> extrairHorarios(String linha) {
        List<LocalTime> lista = new ArrayList<>();
        Matcher m = PATTERN_HORA.matcher(linha);
        while (m.find()) {
            try {
                String strHora = m.group();
                if (strHora.length() == 4) strHora = "0" + strHora;
                lista.add(LocalTime.parse(strHora, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception ignored) {}
        }
        return lista;
    }

    private String identificarPeriodo(List<DiaPonto> dias) {
        if (dias.isEmpty() || dias.get(0).getData() == null) {
            LocalDate now = LocalDate.now();
            return now.getMonth().name() + "/" + now.getYear();
        }
        LocalDate d = dias.get(0).getData();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM/yyyy", Locale.forLanguageTag("pt-BR"));
        String str = d.format(fmt);
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private List<DiaPonto> gerarPeriodoExemplo() {
        List<DiaPonto> lista = new ArrayList<>();
        LocalDate base = LocalDate.now().withDayOfMonth(1);
        int diasNoMes = Math.min(base.lengthOfMonth(), 15);

        for (int i = 0; i < diasNoMes; i++) {
            LocalDate dia = base.plusDays(i);
            if (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            if (i % 4 == 0) {
                lista.add(new DiaPonto(dia,
                        RegistroBatida.original("08:00"),
                        RegistroBatida.original("12:00"),
                        RegistroBatida.original("13:00"),
                        null));
            } else if (i % 4 == 1) {
                lista.add(new DiaPonto(dia,
                        RegistroBatida.original("08:00"),
                        RegistroBatida.original("12:00"),
                        RegistroBatida.original("13:00"),
                        RegistroBatida.original("16:30")));
            } else if (i % 4 == 2) {
                lista.add(new DiaPonto(dia,
                        RegistroBatida.original("08:30"),
                        null,
                        null,
                        RegistroBatida.original("17:30")));
            } else {
                lista.add(new DiaPonto(dia,
                        RegistroBatida.original("08:00"),
                        RegistroBatida.original("12:00"),
                        RegistroBatida.original("13:00"),
                        RegistroBatida.original("17:00")));
            }
        }

        return lista;
    }
}
