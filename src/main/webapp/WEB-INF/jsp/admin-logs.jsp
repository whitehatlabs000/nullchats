<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <title>Access Logs · NullChats</title>
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/all.min.css" rel="stylesheet" />

    <link href="${pageContext.request.contextPath}/css/admin-logs.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />

</head>
<body class="page-log">

<div class="container-fluid px-md-4">
    <div class="row justify-content-center mt-4">

        <%-- COLUMNA CENTRAL: CONTENIDO PRINCIPAL --%>
        <main id="main-content-column" class="col-lg-8 col-md-12">

            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h1 class="h2 mb-0 fw-bold">Access Logs</h1>
                    <p class="text-muted">Monitor login attempts and security events.</p>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="fas fa-home me-1"></i> Dashboard
                </a>
            </div>

            <div class="card mb-4 shadow-sm">
                <div class="card-header bg-transparent border-bottom-0 pt-3">
                    <h5 class="card-title mb-0 text-primary"><i class="fas fa-filter me-2"></i>Filter Logs</h5>
                </div>
                <div class="card-body">
                    <form id="filterForm">
                        <div class="row g-3 align-items-end">

                            <div class="col-md-3 col-lg-2">
                                <label for="filterDate" class="form-label fw-bold small text-uppercase">Date</label>
                                <input type="date" class="form-control" id="filterDate" name="filterDate" value="<c:out value="${filterDate}"/>">
                            </div>

                            <div class="col-md-3 col-lg-2">
                                <label for="filterUsername" class="form-label fw-bold small text-uppercase">Username</label>
                                <input type="text" class="form-control" id="filterUsername" name="filterUsername" value="<c:out value="${filterUsername}"/>" placeholder="username">
                            </div>

                            <div class="col-md-3 col-lg-2">
                                <label for="filterIp" class="form-label fw-bold small text-uppercase">IP Address</label>
                                <input type="text" class="form-control" id="filterIp" name="filterIp" value="<c:out value="${filterIp}"/>" placeholder="192.168...">
                            </div>

                            <div class="col-md-3 col-lg-2">
                                <label for="filterEvent" class="form-label fw-bold small text-uppercase">Event Type</label>
                                <select id="filterEvent" name="filterEvent" class="form-select">
                                    <option value="">All Events</option>
                                    <c:forEach var="eventType" items="${eventTypes}">
                                        <option value="${eventType}" <c:if test="${eventType == filterEvent}">selected</c:if>>
                                            <c:out value="${eventType}"/>
                                        </option>
                                    </c:forEach>
                                </select>
                            </div>

                            <div class="col-md-6 col-lg-2">
                                <label for="filterDetails" class="form-label fw-bold small text-uppercase">Details</label>
                                <input type="text" class="form-control" id="filterDetails" name="filterDetails" value="<c:out value="${filterDetails}"/>" placeholder="Keywords...">
                            </div>

                            <div class="col-md-6 col-lg-2 d-grid">
                                <button type="submit" class="btn btn-primary"><i class="fas fa-search me-1"></i> Search</button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>

            <div class="card shadow-sm position-relative" style="min-height: 200px;">

                <div id="loadingIndicator">
                    <div class="text-center">
                        <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;"></div>
                        <p class="mt-2 fw-bold text-muted">Loading Logs...</p>
                    </div>
                </div>

                <div class="table-responsive">

                    <table class="table table-striped table-hover align-middle mb-0 responsive-admin-logs-table">
                        <thead class="table-light">
                        <tr>
                            <th class="text-uppercase small fw-bold">Timestamp</th>
                            <th class="text-uppercase small fw-bold">IP Address</th>
                            <th class="text-uppercase small fw-bold">Username</th>
                            <th class="text-uppercase small fw-bold">Event</th>
                            <th class="text-uppercase small fw-bold">Details</th>
                        </tr>
                        </thead>

                        <tbody id="logsTableBody">
                        <c:choose>
                            <c:when test="${not empty logs}">
                                <c:forEach var="log" items="${logs}">
                                    <tr>

                                                <td class="text-muted small text-monospace" data-label="Timestamp"><c:out value="${log.timestamp}"/></td>
                                                <td class="text-monospace" data-label="IP Address"><c:out value="${log.ip}"/></td>
                                        <td class="fw-bold" data-label="Username"><c:out value="${log.username}"/></td>

                                        <td data-label="Event">
                                            <c:choose>
                                                <c:when test="${log.event == 'LOGIN_SUCCESS'}">
                                            <span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill">
                                                <i class="bi bi-check-circle-fill me-1"></i>SUCCESS
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'LOGIN_FAIL'}">
                                            <span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill">
                                                <i class="bi bi-x-circle-fill me-1"></i>FAIL
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'ACCOUNT_CREATED'}">
                                            <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill">
                                                <i class="bi bi-person-plus-fill me-1"></i>NEW USER
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'ACCOUNT_DISABLED'}">
                                            <span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle rounded-pill">
                                                <i class="bi bi-slash-circle-fill me-1"></i>DISABLED
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'ACCOUNT_ENABLED'}">
                                            <span class="badge bg-info-subtle text-info-emphasis border border-info-subtle rounded-pill">
                                                <i class="bi bi-check-circle-fill me-1"></i>ENABLED
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'PASSWORD_CHANGE'}">
                                            <span class="badge text-white rounded-pill" style="background-color: #6610f2;">
                                                <i class="bi bi-key-fill me-1"></i>PWD CHANGE
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'ACCOUNT_DELETED'}">
                                            <span class="badge bg-dark text-danger border border-danger rounded-pill">
                                                <i class="bi bi-trash-fill me-1"></i>DELETED
                                            </span>
                                                </c:when>
                                                <c:when test="${log.event == 'PAGE_VIEW'}">
                                            <span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill">
                                                VIEW
                                            </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge bg-light text-dark border rounded-pill"><c:out value="${log.event}"/></span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>


                                                <td class="small text-muted details-cell" data-label="Details"><c:out value="${log.details}"/></td>
                                    </tr>
                                </c:forEach>
                            </c:when>
                            <c:otherwise>
                                <tr>
                                    <td colspan="7" class="text-center py-5 text-muted">
                                        <i class="fas fa-search fa-3x mb-3 opacity-25"></i>
                                        <p class="mb-0">No logs found matching your criteria.</p>
                                    </td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                        </tbody>
                    </table>
                </div>
            </div>

            <nav id="paginationNav" aria-label="Page navigation" class="mt-4">
                <ul id="paginationContainer" class="pagination justify-content-center">
                    <c:if test="${totalPages > 1}">
                        <c:if test="${currentPage > 1}">
                            <li class="page-item">
                                <a class="page-link" href="#" data-page="${currentPage - 1}"><i class="fas fa-chevron-left"></i> Previous</a>
                            </li>
                        </c:if>

                        <%-- Paginación Simple --%>
                        <c:forEach var="i" begin="1" end="${totalPages}">
                            <li class="page-item ${i == currentPage ? 'active' : ''}">
                                <a class="page-link" href="#" data-page="${i}">${i}</a>
                            </li>
                        </c:forEach>

                        <c:if test="${currentPage < totalPages}">
                            <li class="page-item">
                                <a class="page-link" href="#" data-page="${currentPage + 1}">Next <i class="fas fa-chevron-right"></i></a>
                            </li>
                        </c:if>
                    </c:if>
                </ul>
            </nav>

        </main>

    </div>
</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/admin/admin-logs-scripts.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<jsp:include page="messaging_widget.jsp" />

</body>
</html>