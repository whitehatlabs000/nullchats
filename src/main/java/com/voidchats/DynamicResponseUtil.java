package com.voidchats;

import jakarta.servlet.http.HttpServletResponse;

public class DynamicResponseUtil {

    /**
     * Aplica las cabeceras HTTP más estrictas para evitar que el navegador,
     * los proxies (Nginx/Apache) o los CDNs (Cloudflare) guarden en caché
     * respuestas que contienen datos sensibles del usuario o tokens CSRF.
     */
    public static void disableCaching(HttpServletResponse response) {
        // HTTP 1.1: Obliga a revalidar y prohíbe almacenar en disco/RAM
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // HTTP 1.0: Para compatibilidad con clientes o proxies muy antiguos
        response.setHeader("Pragma", "no-cache");

        // Proxies: Indica que la respuesta expiró en el pasado (1 de enero de 1970)
        response.setDateHeader("Expires", 0);
    }
}