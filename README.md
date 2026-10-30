# 🕒 Agente Ajuste de Ponto (Google ADK + Java 25 + Docker)

Agente inteligente especialista no **Regulamento de Horário Flexível** (Divisão de Recursos Humanos - DRH), construído com o **Google Agent Development Kit (ADK) para Java 25** e conteinerizado com **Docker**.

O agente audita os registros de ponto do colaborador (enviados via texto livre, chat ou upload de arquivos como `.csv`, `.txt` e `.pdf`), aplica as regras e heurísticas de ajuste automático para integralizar a meta de 8h diárias e gera um **Espelho de Ponto formatado em PDF**, com os registros alterados destacados **em negrito** e disponibilizados para download.

---

## 📋 Regras de Horário Flexível Implementadas

Conforme a regulamentação oficial de horário flexível:

1. **Compensação Diária**: A jornada padrão é de **8 horas diárias (40 horas semanais)**, compensadas no próprio dia.
2. **Horários Núcleo (Presença Obrigatória sem compensação)**:
   - **09:00 às 11:30** (turno da manhã)
   - **14:00 às 17:00** (turno da tarde)
3. **Faixas Flexíveis de Entrada e Saída**:
   - Entrada flexível: **07:00 às 09:00**
   - Saída flexível: **17:00 às 19:00**
4. **Intervalo de Almoço**:
   - Janela permitida: **11:30 às 14:00**
   - **Duração mínima**: **30 minutos**
   - **Duração máxima**: **02 horas e 30 minutos**
5. **Limites de Turno**:
   - Mínimo de **3 horas ininterruptas** por turno de trabalho.
   - Máximo de **5 horas ininterruptas** por turno de trabalho.

---

## 📁 Estrutura do Projeto

```text
ajuste-de-ponto/
├── Dockerfile                             # Build multi-stage otimizado (JDK 25 -> JRE 25)
├── docker-compose.yml                     # Orquestração em container
├── pom.xml                                # Java 25, Google ADK 1.13.0, OpenPDF e Spring Boot
├── .env.example                           # Modelo de configuração de chaves do Gemini
├── skills/
│   └── ajuste-de-ponto/
│       ├── SKILL.md                       # Especificação completa da skill de ajuste de ponto
│       └── regras_horarios_flexivel.jpeg  # Tabela com as faixas de horário do regulamento
└── src/
    ├── main/
    │   └── java/
    │       └── com/exemplo/ajusteponto/
    │           ├── Main.java              # Entrypoint com inicialização do Dev UI e REST APIs
    │           ├── AjusteDePontoAgent.java# Definição do LlmAgent com Google ADK
    │           ├── PontoTools.java        # Ferramentas (@Schema) acionadas pelo Gemini
    │           ├── controller/
    │           │   └── PontoApiController.java # Endpoints /api/ponto/upload e /download/{id}
    │           ├── service/
    │           │   ├── RegulamentoPontoEngine.java # Validador e ajustador automático de regras
    │           │   └── PdfEspelhoPontoGenerator.java # Gerador de PDF institucional (negrito nos ajustes)
    │           └── model/
    │               ├── RegistroBatida.java # Marcação individual com flag 'foiAlterado'
    │               ├── DiaPonto.java       # Dia de trabalho, totalizadores e status
    │               └── RelatorioPonto.java # Espelho consolidado do período
    └── test/
        └── java/
            └── com/exemplo/ajusteponto/
                └── RegulamentoPontoTest.java # Testes unitários com JUnit 5
```

---

## 🚀 Como Executar

### 1. Configurar Variáveis de Ambiente

Crie o arquivo `.env` na raiz do projeto:

```bash
cp .env.example .env
```

Defina sua chave de API do Gemini ([Google AI Studio](https://aistudio.google.com/app/apikey)):
```env
GEMINI_API_KEY=sua_chave_real_aqui
GOOGLE_API_KEY=sua_chave_real_aqui
MODEL_NAME=gemini-2.5-flash
PORT=8080
```

### 2. Execução com Docker (Recomendado)

Suba o container com o Docker Compose:

```bash
docker compose up -d --build
```

Ou diretamente pelo Docker CLI:
```bash
docker build -t ajuste-de-ponto:latest .
docker run -d -p 8080:8080 --env-file .env ajuste-de-ponto:latest
```

### 3. Execução Local com Java 25

```bash
export GEMINI_API_KEY="sua_chave_aqui"
./mvnw clean test package -DskipTests
java -jar target/ajuste-de-ponto-1.0.0-SNAPSHOT.jar
```

---

## 💬 Formas de Interação com o Agente

### Opção A: Interface Web Dev UI (Google ADK)

Acesse no navegador:
👉 **[http://localhost:8080/dev-ui/](http://localhost:8080/dev-ui/)**

Você pode colar ou digitar texto livremente, como:
```text
Olá, preciso ajustar meu ponto deste mês:
01/10/2026: 08:00 12:00 13:00 (esqueci de bater a saída)
02/10/2026: 08:00 17:00 (não registrei o almoço)
03/10/2026: 08:30 12:00 13:00 16:30
```

O agente:
1. Executará automaticamente o motor de regras de horário flexível.
2. Apresentará a tabela com os horários ajustados em **negrito**.
3. Fornecerá o link direto para download do PDF e a Data URI em Base64.

---

### Opção B: Endpoint REST de Upload de Arquivo

Para enviar arquivos `.csv`, `.txt` ou `.pdf` diretamente:

```bash
curl -X POST http://localhost:8080/api/ponto/upload \
  -F "file=@meu_espelho.csv" \
  -F "matricula=MAT-84920" \
  -F "nome=Carlos Eduardo Rocha"
```

A resposta JSON retorna o protocolo, saldo, totalizadores, o link de download direto e a codificação do PDF em Base64:
```json
{
  "sucesso": true,
  "protocolo": "AJUSTE-AJ-AB12CD34",
  "matricula": "MAT-84920",
  "nomeFuncionario": "Carlos Eduardo Rocha",
  "periodo": "Outubro/2026",
  "totalTrabalhado": "160:00",
  "totalEsperado": "160:00",
  "saldoPeriodo": "00:00",
  "totalAjustesRealizados": 3,
  "downloadUrl": "/api/ponto/download/AJUSTE-AJ-AB12CD34",
  "pdfDataUri": "data:application/pdf;base64,JVBERi0xLjQK..."
}
```

O PDF gerado também pode ser baixado diretamente pelo navegador em:
👉 **`http://localhost:8080/api/ponto/download/AJUSTE-AJ-AB12CD34`**

---

## 📄 Características do Relatório PDF Gerado

- **Identidade Institucional**: Cabeçalho oficial institucional / Divisão de Recursos Humanos (DRH).
- **Destaque Visual**: Horários inseridos ou ajustados pelo motor de regras são impressos **EM NEGRITO** e com fundo de destaque sutil.
- **Auditoria**: Número de protocolo único gerado para cada espelho.
- **Quadro de Resumo**: Horas trabalhadas, horas previstas e saldo de horas do período.
- **Formalização**: Termo de ciência da regulamentação com campos para **Assinatura do Colaborador** e **Assinatura do Gestor Imediato**.
