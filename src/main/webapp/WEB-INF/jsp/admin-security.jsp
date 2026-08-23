<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Security Center · NullChats</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="csrf-token" content="${csrfToken}">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap.min.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/all.min.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/admin-security.css">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="page-admin">

<div class="container-fluid px-md-4 mt-4">
  <div class="row justify-content-center">

    <%-- COLUMNA CENTRAL: CONTENIDO PRINCIPAL --%>
    <main id="main-content-column" class="col-lg-8 col-md-12">

      <div class="d-flex justify-content-between align-items-center mb-4">
        <h3 class="m-0 fw-bold"><i class="fas fa-shield-alt text-primary me-2"></i>Security Center</h3>
      </div>

      <%-- Formulario de Filtros y Acciones --%>
      <div class="card p-3 mb-4 shadow-sm border-0">
        <form action="admin-security" method="GET" autocomplete="off">
          <div class="row g-3 align-items-end justify-content-center">
            <div class="col-md-3">
              <label class="small text-muted fw-bold mb-1">From</label>
              <input type="date" name="startDate" class="form-control" value="${startDate}" required>
            </div>
            <div class="col-md-3">
              <label class="small text-muted fw-bold mb-1">Until</label>
              <input type="date" name="endDate" class="form-control" value="${endDate}" required>
            </div>
            <div class="col-md-2">
              <button type="submit" class="btn btn-primary w-100 fw-bold"><i class="fas fa-sync-alt me-1"></i> Update Data</button>
            </div>
            <div class="col-md-2">
              <a href="${pageContext.request.contextPath}/admin-block-ip" class="btn btn-outline-danger w-100 fw-bold">
                <i class="fas fa-ban me-1"></i> Block IPs
              </a>
            </div>
            <div class="col-md-2">
              <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary w-100 fw-bold">
                <i class="fas fa-home me-1"></i> Dashboard
              </a>
            </div>
          </div>
        </form>
      </div>

      <%-- KPIs de Seguridad --%>
      <div class="row g-3 mb-4">
        <div class="col-xl-3 col-md-6">
          <div class="card h-100 shadow-sm">
            <div class="card-body d-flex justify-content-between align-items-center">
              <div>
                <h6 class="text-success text-uppercase text-xs fw-bold mb-1">Total Traffic</h6>
                <h3 class="mb-0 fw-bold">${stats.kpiTotalTraffic}</h3>
              </div>
              <div class="kpi-icon bg-gradient-success">
                <i class="fas fa-globe"></i>
              </div>
            </div>
          </div>
        </div>
        <div class="col-xl-3 col-md-6">
          <div class="card h-100 shadow-sm">
            <div class="card-body d-flex justify-content-between align-items-center">
              <div>
                <h6 class="text-danger text-uppercase text-xs fw-bold mb-1">Payloads Blocked</h6>
                <h3 class="mb-0 fw-bold">${stats.kpiSecurityBlocks}</h3>
              </div>
              <div class="kpi-icon bg-gradient-danger">
                <i class="fas fa-bug"></i>
              </div>
            </div>
          </div>
        </div>
        <div class="col-xl-3 col-md-6">
          <div class="card h-100 shadow-sm">
            <div class="card-body d-flex justify-content-between align-items-center">
              <div>
                <h6 class="text-warning text-uppercase text-xs fw-bold mb-1">Rate Limits Hits</h6>
                <h3 class="mb-0 fw-bold">${stats.kpiRateLimits}</h3>
              </div>
              <div class="kpi-icon bg-gradient-warning">
                <i class="fas fa-stopwatch"></i>
              </div>
            </div>
          </div>
        </div>
        <div class="col-xl-3 col-md-6">
          <div class="card h-100 shadow-sm">
            <div class="card-body d-flex justify-content-between align-items-center">
              <div>
                <h6 class="text-secondary text-uppercase text-xs fw-bold mb-1">Blacklist Rejections</h6>
                <h3 class="mb-0 fw-bold">${stats.kpiBlacklistHits}</h3>
              </div>
              <div class="kpi-icon bg-gradient-dark">
                <i class="fas fa-skull-crossbones"></i>
              </div>
            </div>
          </div>
        </div>
      </div>

      <%-- Gráficos del WAF --%>
      <div class="row g-4 mb-4">
        <div class="col-lg-8">
          <div class="card h-100 shadow-sm">
            <div class="card-header bg-transparent d-flex justify-content-between align-items-center">
              <h6 class="m-0 fw-bold text-primary"><i class="fas fa-chart-line me-2"></i>Traffic vs. Intercepted Attacks</h6>
            </div>
            <div class="card-body">
              <div style="height: 300px;"><canvas id="trafficLineChart"></canvas></div>
            </div>
          </div>
        </div>
        <div class="col-lg-4">
          <div class="card h-100 shadow-sm">
            <div class="card-header bg-transparent">
              <h6 class="m-0 fw-bold text-danger"><i class="fas fa-radiation-alt me-2"></i>Threat Distribution</h6>
            </div>
            <div class="card-body d-flex justify-content-center align-items-center">
              <c:choose>
                <c:when test="${empty stats.threatLabels}">
                  <div class="text-muted text-center p-4">
                    <i class="fas fa-shield-check fa-3x mb-3 text-success opacity-50"></i>
                    <p class="mb-0">No threats detected.</p>
                  </div>
                </c:when>
                <c:otherwise>
                  <div style="height: 250px; width: 100%;"><canvas id="threatDoughnutChart"></canvas></div>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </div>
      </div>

      <%-- Tablas de Análisis --%>
      <div class="row g-4 mb-4">

        <%-- Top Atacantes --%>
        <div class="col-lg-5">
          <div class="card h-100 shadow-sm">
            <div class="card-header py-3 bg-transparent d-flex justify-content-between align-items-center">
              <h6 class="m-0 fw-bold text-danger"><i class="fas fa-user-ninja me-2"></i>Top Attackers (IPs)</h6>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-light sticky-top">
                <tr><th>IP Address</th><th class="text-center">Blocks</th><th class="text-end">Ban</th></tr>
                </thead>
                <tbody>
                <c:forEach var="attacker" items="${stats.topAttackers}">
                  <tr>
                    <td class="font-monospace text-secondary fw-bold">
                      <c:out value="${attacker.ip_address}"/>
                    </td>
                    <td class="text-center">
                      <span class="badge bg-danger rounded-pill badge-threat">${attacker.block_count}</span>
                    </td>
                    <td class="text-end">
                      <c:choose>
                        <c:when test="${attacker.is_banned}">
                          <span class="badge bg-secondary bg-opacity-25 text-secondary px-2 py-1 border border-secondary border-opacity-25" title="Already Banned">
                            <i class="fas fa-ban me-1"></i>Banned
                          </span>
                        </c:when>
                        <c:otherwise>
                          <button type="button" class="btn btn-sm text-danger bg-danger bg-opacity-10 border-0 fw-bold px-2 py-1" onclick="showBanModal('${attacker.ip_address}')" title="Ban IP">
                            <i class="fas fa-gavel"></i>
                          </button>
                        </c:otherwise>
                      </c:choose>
                    </td>
                  </tr>
                </c:forEach>
                <c:if test="${empty stats.topAttackers}">
                  <tr><td colspan="3" class="text-center text-muted py-4">No attackers found.</td></tr>
                </c:if>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <%-- Últimas Amenazas --%>
        <div class="col-lg-7">
          <div class="card h-100 shadow-sm">
            <div class="card-header py-3 bg-transparent d-flex justify-content-between align-items-center">
              <h6 class="m-0 fw-bold text-warning"><i class="fas fa-exclamation-triangle me-2"></i>Recent Security Events</h6>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-light sticky-top">
                <tr>
                  <th>Time</th>
                  <th>IP</th>
                  <th>Type</th>
                  <th>Details</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="threat" items="${stats.recentThreats}">
                  <tr>
                    <td class="text-muted small"><c:out value="${fn:substring(threat.event_timestamp, 5, 16)}"/></td>
                    <td class="font-monospace small"><c:out value="${threat.ip_address}"/></td>
                    <td><span class="badge bg-dark border border-secondary text-uppercase" style="font-size: 0.7em;"><c:out value="${threat.event_type}"/></span></td>
                    <td class="small text-truncate" style="max-width: 150px;" title="${threat.details}"><c:out value="${threat.details}"/></td>
                  </tr>
                </c:forEach>
                <c:if test="${empty stats.recentThreats}">
                  <tr><td colspan="4" class="text-center text-muted py-4">No recent threats.</td></tr>
                </c:if>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>

  <%-- Modal de Confirmación para Banear IP --%>
  <div class="modal fade" id="banModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content shadow" style="background-color: var(--bs-body-bg); border: 1px solid var(--bs-border-color);">
        <div class="modal-header border-bottom-0">
          <h5 class="modal-title text-danger fw-bold"><i class="fas fa-exclamation-triangle me-2"></i>Confirm IP Ban</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body text-secondary">
          Are you sure you want to add the IP <strong id="modalIpDisplay" class="font-monospace text-primary"></strong> to the blocklist? This will immediately deny all access from this address.
        </div>
        <div class="modal-footer border-top-0">
          <button type="button" class="btn btn-secondary fw-bold border-0" data-bs-dismiss="modal">Cancel</button>
          <form action="${pageContext.request.contextPath}/admin-block-ip" method="POST" class="m-0">
            <input type="hidden" name="csrfToken" value="${csrfToken}">
            <input type="hidden" name="action" value="add">
            <input type="hidden" name="ip" id="modalIpInput" value="">
            <button type="submit" class="btn btn-danger fw-bold"><i class="fas fa-gavel me-2"></i>Ban IP</button>
          </form>
        </div>
      </div>
    </div>
  </div>

</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/js/chart.min.js"></script>

<script>
  // Pasamos los datos del servidor a JS de manera limpia
  const securityData = ${empty dashboardJson ? '{}' : dashboardJson};

  // Lógica del modal de baneo
  function showBanModal(ipAddress) {
    document.getElementById('modalIpDisplay').textContent = ipAddress;
    document.getElementById('modalIpInput').value = ipAddress;
    // 1: getOrCreateInstance evita crear modales duplicados en el DOM
    const banModal = bootstrap.Modal.getOrCreateInstance(document.getElementById('banModal'));
    banModal.show();
  }

  // 2: Interceptar bfcache para recargar la tabla y renovar CSRF tras volver atrás
  window.addEventListener('pageshow', function(event) {
    if (event.persisted) {
      const modalEl = document.getElementById('banModal');
      if (modalEl) {
        const banModal = bootstrap.Modal.getInstance(modalEl);
        if (banModal) {
          banModal.hide(); // Cerramos el modal atascado en memoria
        }
      }
      window.location.reload();
    }
  });
</script>

<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>
<%-- Lógica de Gráficos importada externamente --%>
<script src="${pageContext.request.contextPath}/scripts/admin/admin-security.js" defer></script>

<jsp:include page="messaging_widget.jsp" />
</body>
</html>