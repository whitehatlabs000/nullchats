<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Blocked IPs · NullChats</title>
    <meta name="csrf-token" content="${csrfToken}">
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/all.min.css" rel="stylesheet" />

    <link href="${pageContext.request.contextPath}/css/admin-block-ip.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />

</head>
<body class="page-view">

<div class="container-fluid px-md-4">
    <div class="row justify-content-center mt-4">

        <main id="main-content-column" class="col-lg-8 col-md-12">

            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h1 class="h2 mb-0 fw-bold">Blocked IP Management</h1>
                    <p class="text-muted small mb-0">Deny access to specific IP addresses.</p>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="fas fa-home me-1"></i> Dashboard
                </a>
            </div>

            <%-- Mensajes Flash --%>
            <c:if test="${not empty message}">
                <div class="alert alert-info alert-dismissible fade show shadow-sm" role="alert">
                    <i class="bi bi-info-circle-fill me-2"></i> ${message}
                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                </div>
            </c:if>

            <div class="row g-4 mb-4">

                <div class="col-md-6">
                    <div class="card shadow-sm h-100">
                        <div class="card-header bg-transparent border-0 pt-3 pb-0">
                            <h6 class="fw-bold text-danger mb-0"><i class="bi bi-shield-slash-fill me-2"></i>Block New IP</h6>
                        </div>
                        <div class="card-body">
                            <form action="admin-block-ip" method="post" class="mt-2">
                                <input type="hidden" name="action" value="add">
                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                <div class="input-group">
                                    <input type="text" name="ip" class="form-control" placeholder="192.168.x.x" required>
                                    <button type="submit" class="btn btn-danger">Block</button>
                                </div>
                                <div class="form-text small">Use standard IPv4 or IPv6 format.</div>
                            </form>
                        </div>
                    </div>
                </div>

                <div class="col-md-6">
                    <div class="card shadow-sm h-100">
                        <div class="card-header bg-transparent border-0 pt-3 pb-0">
                            <h6 class="fw-bold text-primary mb-0"><i class="bi bi-search me-2"></i>Search Blocklist</h6>
                        </div>
                        <div class="card-body">
                            <form action="admin-block-ip" method="get" class="mt-2">
                                <div class="input-group">
                                    <input type="text" name="q" class="form-control" placeholder="Search IP..." value="<c:out value="${searchQuery}"/>">
                                    <button type="submit" class="btn btn-secondary">Search</button>
                                </div>
                                <div class="form-text small">Filter the list below.</div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card shadow-sm">
                <div class="card-header bg-transparent py-3">
                    <div class="d-flex justify-content-between align-items-center">
                        <h6 class="mb-0 fw-bold"><i class="bi bi-list-ul me-2"></i>Blocked IP Addresses</h6>
                        <span class="badge bg-secondary rounded-pill">${blockedIPs != null ? blockedIPs.size() : 0} Entries</span>
                    </div>
                </div>

                <ul class="list-group list-group-flush ip-list-group">
                    <c:choose>
                        <c:when test="${not empty blockedIPs}">
                            <c:forEach var="ip" items="${blockedIPs}">
                                <li class="list-group-item">
                                    <div class="d-flex align-items-center">
                                        <div class="bg-danger-subtle text-danger rounded p-2 me-3">
                                            <i class="bi bi-ban"></i>
                                        </div>
                                        <div>
                                            <div class="ip-address text-body-emphasis"><c:out value="${ip}"/></div>
                                            <small class="text-muted">Access Denied</small>
                                        </div>
                                    </div>

                                    <form action="admin-block-ip" method="post" class="m-0">
                                        <input type="hidden" name="action" value="remove">
                                        <input type="hidden" name="ip" value="<c:out value="${ip}"/>">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <button type="submit" class="btn btn-sm btn-outline-success border-0 bg-success-subtle text-success" title="Unblock IP">
                                            <i class="bi bi-unlock-fill me-1"></i> Unblock
                                        </button>
                                    </form>
                                </li>
                            </c:forEach>
                        </c:when>
                        <c:otherwise>
                            <li class="list-group-item text-center py-5">
                                <div class="text-muted opacity-50 mb-2"><i class="bi bi-shield-check display-4"></i></div>
                                <p class="mb-0 fw-bold text-muted">No IPs are currently blocked.</p>
                            </li>
                        </c:otherwise>
                    </c:choose>
                </ul>
            </div>

        </main>

    </div>
</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<jsp:include page="messaging_widget.jsp" />

</body>
</html>