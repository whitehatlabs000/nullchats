<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Maintenance Mode · NullChats</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${csrfToken}">
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/all.min.css" rel="stylesheet">

    <link href="${pageContext.request.contextPath}/css/admin-maintenance.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="page-view">

<div class="container-fluid px-md-4">
    <div class="row justify-content-center mt-4">

        <%-- COLUMNA CENTRAL: CONTENIDO PRINCIPAL --%>
        <main id="main-content-column" class="col-lg-8 col-md-12">

            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h2 class="mb-0"><i class="fas fa-tools"></i> Maintenance Management</h2>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="fas fa-home me-1"></i> Dashboard
                </a>
            </div>

            <c:if test="${not empty message}">
                <div class="alert alert-info alert-dismissible fade show">
                    <c:out value="${message}" />
                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                </div>
            </c:if>

            <div class="card shadow-sm">
                <div class="card-body text-center p-5">

                    <h3 class="mb-4">Server Status</h3>

                    <c:choose>
                        <c:when test="${isMaintenanceMode}">
                            <div class="mb-4">
                                <i class="fas fa-lock fa-4x text-danger mb-3"></i>
                                <h2 class="text-danger fw-bold">UNDER MAINTENANCE</h2>
                                <p class="text-muted">Public access is blocked. You can browse because you are an Administrator.</p>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="mb-4">
                                <i class="fas fa-globe fa-4x text-success mb-3"></i>
                                <h2 class="text-success fw-bold">ONLINE</h2>
                                <p class="text-muted">The site is accessible to all users.</p>
                            </div>
                        </c:otherwise>
                    </c:choose>

                    <hr>

                    <form action="admin-maintenance" method="POST">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="toggle">

                        <c:choose>
                            <c:when test="${isMaintenanceMode}">
                                <button type="submit" class="btn btn-success btn-lg px-5">
                                    <i class="fas fa-play"></i> Disable Maintenance (Open Site)
                                </button>
                            </c:when>
                            <c:otherwise>
                                <button type="submit" class="btn btn-danger btn-lg px-5">
                                    <i class="fas fa-stop-circle"></i> Enable Maintenance (Close Site)
                                </button>
                                <div class="mt-2 text-muted small">
                                    <i class="fas fa-exclamation-triangle"></i> Users will be redirected to the waiting page.
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </form>
                </div>
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