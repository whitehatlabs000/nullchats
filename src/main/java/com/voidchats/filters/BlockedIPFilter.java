package com.voidchats.filters;

import com.voidchats.IPUtils;
import com.voidchats.admin.IPBlockManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BlockedIPFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(BlockedIPFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // El IPBlockManager ya tiene la lista en RAM lista para usarse.
        log.info("BlockedIPFilter initialized | Checking against RAM-based IPBlockManager");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String clientIp = IPUtils.getClientIp(httpRequest);

        if (clientIp != null && clientIp.length() > 50) {
            log.warn("DoS SHIELD: BlockedIPFilter intercepted fake/massive IP header (length: {})", clientIp.length());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid IP address format.");
            return; // Detiene la petición y protege el servidor
        }

        if (IPBlockManager.getBlockedIPs().contains(clientIp)) {
            // Usamos DEBUG para no inundar el log de producción si un bot baneado ataca 100 veces por segundo.
            log.debug("BLOCKED IP ATTEMPT: Rejected request from already banned IP: {}", clientIp);

            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Your IP address has been blocked.");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}