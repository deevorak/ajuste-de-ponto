package com.exemplo.ajusteponto.controller;

import com.exemplo.ajusteponto.model.RelatorioPonto;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator;
import com.exemplo.ajusteponto.service.PdfEspelhoPontoGenerator.ResultadoPdf;
import com.exemplo.ajusteponto.service.RegulamentoPontoEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoint REST para upload de arquivos de ponto e download de espelhos PDF ajustados.
 */
@RestController
@RequestMapping("/api/ponto")
@CrossOrigin(origins = "*")
public class PontoApiController {

    private final RegulamentoPontoEngine engine = new RegulamentoPontoEngine();
    private final PdfEspelhoPontoGenerator pdfGenerator = new PdfEspelhoPontoGenerator();
    private static final Map<String, byte[]> PDF_CACHE = new ConcurrentHashMap<>();

    public static void registrarPdfNoCache(String protocolo, byte[] pdfBytes) {
        if (protocolo != null && pdfBytes != null) {
            PDF_CACHE.put(protocolo, pdfBytes);
        }
    }

    /**
     * Upload de arquivo (.csv, .txt, .pdf) ou envio de texto direto para ajuste automático.
     */
    @PostMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> uploadArquivo(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "texto", required = false) String texto,
            @RequestParam(value = "matricula", required = false, defaultValue = "MAT-12345") String matricula,
            @RequestParam(value = "nome", required = false, defaultValue = "Colaborador") String nome) {

        try {
            String conteudo = "";
            if (file != null && !file.isEmpty()) {
                conteudo = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (texto != null && !texto.isBlank()) {
                conteudo = texto;
            } else {
                conteudo = "";
            }

            RelatorioPonto relatorio = engine.processarEAjustar(conteudo, matricula, nome);
            ResultadoPdf resultadoPdf = pdfGenerator.gerarPdf(relatorio);
            registrarPdfNoCache(relatorio.getProtocolo(), resultadoPdf.getBytes());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sucesso", true);
            resp.put("protocolo", relatorio.getProtocolo());
            resp.put("matricula", relatorio.getMatricula());
            resp.put("nomeFuncionario", relatorio.getNomeFuncionario());
            resp.put("periodo", relatorio.getPeriodo());
            resp.put("totalTrabalhado", relatorio.getTotalTrabalhadoFormatado());
            resp.put("totalEsperado", relatorio.getTotalEsperadoFormatado());
            resp.put("saldoPeriodo", relatorio.getSaldoPeriodoFormatado());
            resp.put("totalAjustesRealizados", relatorio.getTotalAjustesRealizados());
            resp.put("downloadUrl", "/api/ponto/download/" + relatorio.getProtocolo());
            resp.put("pdfDataUri", resultadoPdf.getDataUri());
            resp.put("pdfBase64", resultadoPdf.getBase64());

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("sucesso", false, "erro", e.getMessage()));
        }
    }

    /**
     * Endpoint direto para download do arquivo PDF pelo navegador.
     */
    @GetMapping("/download/{protocolo}")
    public ResponseEntity<byte[]> baixarPdf(@PathVariable("protocolo") String protocolo) {
        byte[] bytes = PDF_CACHE.get(protocolo);

        if (bytes == null) {
            try {
                Path arquivo = Paths.get("relatorios", "espelho_ponto_" + protocolo + ".pdf");
                if (Files.exists(arquivo)) {
                    bytes = Files.readAllBytes(arquivo);
                }
            } catch (Exception ignored) {}
        }

        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "espelho_ponto_" + protocolo + ".pdf");
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
