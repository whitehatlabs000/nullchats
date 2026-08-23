<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <title>Dashboard · NullChats</title>
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/admin-dashboard.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body>

<div class="container py-5">

    <div class="text-center mb-5">
        <h1 class="display-5 fw-bold">Administration Dashboard</h1>
        <p class="text-muted lead">Central control panel for NullChats management.</p>
    </div>

    <%-- WIDGET DE MONITOREO DE SALUD DE LA BASE DE DATOS --%>
    <div class="card mb-5 shadow-sm border-0" style="border-radius: 0.75rem;">
        <div class="card-body p-4 d-flex flex-column flex-xl-row justify-content-between align-items-center rounded" style="background: var(--bs-body-bg); border: 1px solid var(--bs-border-color);">
            <div class="d-flex align-items-center mb-4 mb-xl-0">
                <div class="bg-primary bg-opacity-10 text-primary rounded-circle p-3 me-3 d-flex align-items-center justify-content-center" style="width: 60px; height: 60px;">
                    <i class="bi bi-database-fill-gear fs-3"></i>
                </div>
                <div>
                    <h5 class="fw-bold mb-1">Database Health Monitor</h5>
                    <p class="text-muted small mb-0">Automated Background Maintenance Tasks</p>
                </div>
            </div>
            <div class="d-flex flex-wrap gap-2 gap-md-3 justify-content-center">
                <%-- Global Scheduler Status --%>
                <div class="border rounded px-3 py-2 text-center bg-body-tertiary">
                    <div class="small text-muted fw-bold text-uppercase mb-2" style="font-size: 0.7rem; letter-spacing: 0.5px;">Global Engine</div>
                    <c:choose>
                        <c:when test="${globalSchedulerStatus == 'ON'}">
                            <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill"><i class="bi bi-check-circle-fill me-1"></i>ON</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill"><i class="bi bi-x-circle-fill me-1"></i>${globalSchedulerStatus}</span>
                        </c:otherwise>
                    </c:choose>
                </div>

                <%-- Rate Limits Cleanup --%>
                <div class="border rounded px-3 py-2 text-center bg-body-tertiary">
                    <div class="small text-muted fw-bold text-uppercase mb-2" style="font-size: 0.7rem; letter-spacing: 0.5px;">Rate Limits</div>
                    <c:choose>
                        <c:when test="${eventStatuses['clean_rate_limit_logs'] == 'ENABLED'}">
                            <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill"><i class="bi bi-check-circle-fill me-1"></i>ENABLED</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill"><i class="bi bi-exclamation-triangle-fill me-1"></i>OFF</span>
                        </c:otherwise>
                    </c:choose>
                </div>

                <%-- Audit Logs Cleanup --%>
                <div class="border rounded px-3 py-2 text-center bg-body-tertiary">
                    <div class="small text-muted fw-bold text-uppercase mb-2" style="font-size: 0.7rem; letter-spacing: 0.5px;">Audit Logs</div>
                    <c:choose>
                        <c:when test="${eventStatuses['clean_audit_logs'] == 'ENABLED'}">
                            <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill"><i class="bi bi-check-circle-fill me-1"></i>ENABLED</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill"><i class="bi bi-exclamation-triangle-fill me-1"></i>OFF</span>
                        </c:otherwise>
                    </c:choose>
                </div>

            </div>
        </div>
    </div>

    <h6 class="text-uppercase text-muted fw-bold mb-3 ms-1"><i class="bi bi-sliders me-2"></i>System Modules</h6>
    <%-- Reorganización de las 5 herramientas principales en una grilla simétrica --%>
    <div class="row g-4 mb-5 justify-content-center">

        <%-- 1. Control de Usuarios --%>
        <div class="col-sm-6 col-md-4 col-xl-2-5">
            <a href="${pageContext.request.contextPath}/admin-manage_users" class="card dashboard-card card-manage">
                <div class="card-body text-center p-4">
                    <i class="bi bi-person-gear card-icon"></i>
                    <h5 class="card-title">Manage Users</h5>
                    <p class="card-text text-secondary small">Edit, ban, or delete user accounts.</p>
                </div>
            </a>
        </div>

        <%-- 2. Centro de Seguridad (WAF) --%>
        <div class="col-sm-6 col-md-4 col-xl-2-5">
            <a href="${pageContext.request.contextPath}/admin-security" class="card dashboard-card card-security">
                <div class="card-body text-center p-4">
                    <i class="bi bi-shield-check card-icon"></i>
                    <h5 class="card-title">Security Center</h5>
                    <p class="card-text text-secondary small">WAF threats, statistics, and attacks.</p>
                </div>
            </a>
        </div>

        <%-- 3. Logs de Auditoría --%>
        <div class="col-sm-6 col-md-4 col-xl-2-5">
            <a href="${pageContext.request.contextPath}/admin-logs" class="card dashboard-card card-analytics">
                <div class="card-body text-center p-4">
                    <i class="bi bi-journal-text card-icon"></i>
                    <h5 class="card-title">Access Logs</h5>
                    <p class="card-text text-secondary small">Audit login attempts and system events.</p>
                </div>
            </a>
        </div>

        <%-- 4. Firewall IP --%>
        <div class="col-sm-6 col-md-4 col-xl-2-5">
            <a href="${pageContext.request.contextPath}/admin-block-ip" class="card dashboard-card card-security">
                <div class="card-body text-center p-4">
                    <i class="bi bi-shield-slash-fill card-icon"></i>
                    <h5 class="card-title">Blocked IPs</h5>
                    <p class="card-text text-secondary small">Manage firewall and IP restrictions.</p>
                </div>
            </a>
        </div>

        <%-- 5. Modo Mantenimiento --%>
        <div class="col-sm-6 col-md-4 col-xl-2-5">
            <a href="${pageContext.request.contextPath}/admin-maintenance" class="card dashboard-card card-security">
                <div class="card-body text-center p-4">
                    <i class="bi bi-cone-striped card-icon"></i>
                    <h5 class="card-title">Maintenance Mode</h5>
                    <p class="card-text text-secondary small">Toggle server availability settings.</p>
                </div>
            </a>
        </div>

    </div>

</div>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<jsp:include page="messaging_widget.jsp" />


</body>
</html>