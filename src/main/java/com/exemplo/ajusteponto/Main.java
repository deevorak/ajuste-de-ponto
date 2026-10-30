package com.exemplo.ajusteponto;

import com.google.adk.web.AdkWebServer;
import com.google.adk.web.AgentStaticLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Ponto de entrada da aplicação.
 * Inicializa o Spring Boot com o servidor Google ADK Dev UI e os endpoints REST de ponto e download.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.google.adk.web", "com.exemplo.ajusteponto"})
public class Main {

    public static void main(String[] args) {
        String port = System.getenv().getOrDefault("PORT", "8080");
        System.setProperty("server.port", port);
        System.setProperty("adk.agents.loader", "static");
        System.setProperty("org.apache.tomcat.websocket.DEFAULT_BUFFER_SIZE", "10485760");

        System.out.println("=".repeat(65));
        System.out.println("  Iniciando Agente de Ajuste de Ponto (Horário Flexível)");
        System.out.println("  Porta HTTP configurada: " + port);
        System.out.println("  Dev UI do Google ADK: http://localhost:" + port + "/dev-ui/");
        System.out.println("  Endpoint de Upload:   http://localhost:" + port + "/api/ponto/upload");
        System.out.println("=".repeat(65));

        SpringApplication app = new SpringApplication(AdkWebServer.class, Main.class);
        app.addInitializers(ctx -> {
            ctx.getBeanFactory().registerSingleton("agentLoader",
                    new AgentStaticLoader(AjusteDePontoAgent.ROOT_AGENT));
        });
        app.run(args);
    }
}
