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
 * Motor de regras do Regulamento de Horário Flexível.
 * Garante rigorosamente que cada dia trabalhado feche em EXATAMENTE 8 horas (480 minutos),
 * sem horas extras e sem débitos, respeitando as faixas de horário núcleo e limites legais.
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

    public static final int JORNADA_PADRAO_MINUTOS = 480; // Rigorosamente 8 horas
    public static final int TURNO_MINIMO_MINUTOS = 180;    // 3 horas
    public static final int TURNO_MAXIMO_MINUTOS = 300;    // 5 horas
    public static final int ALMOCO_MINIMO_MINUTOS = 30;    // 30 minutos
    public static final int ALMOCO_MAXIMO_MINUTOS = 150;   // 2 horas e 30 minutos

    private static final Pattern PATTERN_HORA = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern PATTERN_DATA = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})|(\\d{2}/\\d{2}/\\d{4})|(\\d{2}/\\d{2})");

    /**
     * Processa texto bruto ou conteúdo de arquivo e aplica o ajuste estrito de 8h diárias.
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
     * Aplica o regulamento e ajusta os registros para fechar RIGOROSAMENTE 8h00 diárias,
     * eliminando qualquer possibilidade de hora extra ou déficit.
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

        // Se o dia está completamente vazio
        if (e1 == null && s1 == null && e2 == null && s2 == null) {
            dia.setEntrada1(RegistroBatida.ajustado(LocalTime.of(8, 0), "Inclusão automática 1º turno"));
            dia.setSaida1(RegistroBatida.ajustado(LocalTime.of(12, 0), "Inclusão automática almoço"));
            dia.setEntrada2(RegistroBatida.ajustado(LocalTime.of(13, 0), "Inclusão automática retorno almoço"));
            dia.setSaida2(RegistroBatida.ajustado(LocalTime.of(17, 0), "Inclusão automática término expediente"));
            dia.setObservacao("Batidas inseridas para cumprir exatamente 8h00 de jornada.");
            dia.calcularTotais();
            return;
        }

        // 1. Validação e ajuste de Entrada 1 (e1)
        if (e1 == null) {
            if (s1 != null) {
                LocalTime calcE1 = s1.minusHours(4);
                if (calcE1.isBefore(ENTRADA_FLEXIVEL_INICIO)) calcE1 = ENTRADA_FLEXIVEL_INICIO;
                dia.setEntrada1(RegistroBatida.ajustado(calcE1, "Inclusão de entrada"));
            } else {
                dia.setEntrada1(RegistroBatida.ajustado(LocalTime.of(8, 0), "Inclusão de entrada"));
            }
            e1 = dia.getEntrada1().getHorario();
        } else if (e1.isBefore(ENTRADA_FLEXIVEL_INICIO)) {
            dia.setEntrada1(RegistroBatida.ajustado(ENTRADA_FLEXIVEL_INICIO, "Entrada antecipada ajustada para o início da faixa flexível (07:00)"));
            e1 = dia.getEntrada1().getHorario();
        } else if (e1.isAfter(HORARIO_NUCLEO_1_INICIO)) {
            dia.setEntrada1(RegistroBatida.ajustado(HORARIO_NUCLEO_1_INICIO, "Entrada ajustada para o limite do horário núcleo (09:00)"));
            e1 = dia.getEntrada1().getHorario();
        }

        // 2. Validação e ajuste de Saída 1 (s1 - almoço início)
        if (s1 == null) {
            LocalTime calcS1 = e1.plusHours(4);
            if (calcS1.isBefore(ALMOCO_FLEXIVEL_INICIO)) calcS1 = ALMOCO_FLEXIVEL_INICIO;
            if (calcS1.isAfter(ALMOCO_FLEXIVEL_FIM)) calcS1 = ALMOCO_FLEXIVEL_FIM;
            dia.setSaida1(RegistroBatida.ajustado(calcS1, "Inclusão de saída de almoço"));
            s1 = dia.getSaida1().getHorario();
        } else if (s1.isBefore(HORARIO_NUCLEO_1_FIM)) {
            dia.setSaida1(RegistroBatida.ajustado(HORARIO_NUCLEO_1_FIM, "Saída ajustada para respeitar término do horário núcleo matutino (11:30)"));
            s1 = dia.getSaida1().getHorario();
        } else if (s1.isAfter(ALMOCO_FLEXIVEL_FIM)) {
            dia.setSaida1(RegistroBatida.ajustado(ALMOCO_FLEXIVEL_FIM, "Saída para almoço ajustada para o limite máximo (14:00)"));
            s1 = dia.getSaida1().getHorario();
        }

        // 3. Validação e ajuste de Entrada 2 (e2 - retorno almoço)
        if (e2 == null) {
            LocalTime calcE2 = s1.plusHours(1);
            if (calcE2.isAfter(HORARIO_NUCLEO_2_INICIO)) calcE2 = HORARIO_NUCLEO_2_INICIO;
            dia.setEntrada2(RegistroBatida.ajustado(calcE2, "Inclusão de retorno de almoço"));
            e2 = dia.getEntrada2().getHorario();
        } else if (e2.isAfter(HORARIO_NUCLEO_2_INICIO)) {
            dia.setEntrada2(RegistroBatida.ajustado(HORARIO_NUCLEO_2_INICIO, "Retorno de almoço ajustado para o início do horário núcleo vespertino (14:00)"));
            e2 = dia.getEntrada2().getHorario();
        }

        // Validação da duração do almoço (30min a 150min)
        long duracaoAlmoco = Duration.between(s1, e2).toMinutes();
        if (duracaoAlmoco < ALMOCO_MINIMO_MINUTOS) {
            dia.setEntrada2(RegistroBatida.ajustado(s1.plusMinutes(ALMOCO_MINIMO_MINUTOS),
                    "Retorno ajustado para cumprir intervalo mínimo de 30 minutos de almoço"));
            e2 = dia.getEntrada2().getHorario();
        } else if (duracaoAlmoco > ALMOCO_MAXIMO_MINUTOS) {
            dia.setEntrada2(RegistroBatida.ajustado(s1.plusMinutes(ALMOCO_MAXIMO_MINUTOS),
                    "Retorno ajustado para cumprir intervalo máximo de 2h30 de almoço"));
            e2 = dia.getEntrada2().getHorario();
        }

        // 4. Garantia estrita de 8h00 (480 minutos) diárias
        // Turno 2 obrigatório até às 17:00 (horário núcleo vespertino)
        long minAteFimNucleo2 = Duration.between(e2, HORARIO_NUCLEO_2_FIM).toMinutes();
        long turno2Minimo = Math.max(TURNO_MINIMO_MINUTOS, minAteFimNucleo2);

        // Se Turno 1 atual deixaria o Turno 2 abaixo do mínimo legal ou do horário núcleo:
        long minutosTurno1 = Duration.between(e1, s1).toMinutes();
        if (minutosTurno1 > JORNADA_PADRAO_MINUTOS - turno2Minimo) {
            long novoT1 = JORNADA_PADRAO_MINUTOS - turno2Minimo;
            LocalTime novoE1 = s1.minusMinutes(novoT1);
            if (novoE1.isBefore(ENTRADA_FLEXIVEL_INICIO)) {
                novoE1 = ENTRADA_FLEXIVEL_INICIO;
                s1 = novoE1.plusMinutes(novoT1);
                dia.setSaida1(RegistroBatida.ajustado(s1, "Ajuste de horário de almoço para fechar 8h00"));
            }
            dia.setEntrada1(RegistroBatida.ajustado(novoE1, "Entrada ajustada para viabilizar jornada exata de 8h00 sem horas extras"));
            e1 = dia.getEntrada1().getHorario();
            minutosTurno1 = Duration.between(e1, s1).toMinutes();
        } else if (minutosTurno1 < TURNO_MINIMO_MINUTOS) {
            dia.setEntrada1(RegistroBatida.ajustado(s1.minusMinutes(TURNO_MINIMO_MINUTOS),
                    "Entrada ajustada para respeitar o turno mínimo de 3 horas"));
            e1 = dia.getEntrada1().getHorario();
            minutosTurno1 = Duration.between(e1, s1).toMinutes();
        }

        // Calcula a Saída 2 EXATA para que a soma feche rigorosamente 480 minutos (8h00)
        long minutosNecessariosTurno2 = JORNADA_PADRAO_MINUTOS - minutosTurno1;
        LocalTime saida2Ideal = e2.plusMinutes(minutosNecessariosTurno2);

        // Se a saída 2 original não existia ou difere da saída ideal, altera para a ideal
        if (s2 == null || !s2.equals(saida2Ideal)) {
            String motivo = (s2 == null)
                    ? "Inclusão de saída calculada para fechar exatamente 8h00 de jornada"
                    : "Horário de saída ajustado de " + s2 + " para " + saida2Ideal + " para fechar rigorosamente 8h00 (sem horas extras)";

            dia.setSaida2(RegistroBatida.ajustado(saida2Ideal, motivo));
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
                        RegistroBatida.original("18:30"))); // Original com hora extra
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
