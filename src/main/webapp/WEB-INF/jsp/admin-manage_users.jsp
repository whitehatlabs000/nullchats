<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Manage Users · NullChats</title>
    <meta name="csrf-token" content="${csrfToken}">
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/all.min.css" rel="stylesheet" />

    <link href="${pageContext.request.contextPath}/css/admin-manage_users.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="page-manage">

<div class="container-fluid px-md-4">
    <div class="row justify-content-center">

        <main id="main-content-column" class="col-lg-8 col-md-12 mt-4">

            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h1 class="h2 mb-0 fw-bold">Manage Users</h1>
                    <p class="text-muted small">Search, edit, ban, or delete user accounts.</p>
                </div>
                <a href="${pageContext.request.contextPath}/admin-dashboard" class="btn btn-outline-secondary">
                    <i class="fas fa-home me-1"></i> Dashboard
                </a>
            </div>

            <div id="searchFormContainer" class="card mb-4 search-card">
                <div class="card-body">
                    <form id="searchForm" autocomplete="off">
                        <div class="input-group mb-3">
                            <span class="input-group-text bg-transparent border-end-0"><i class="bi bi-search"></i></span>
                            <input type="text" maxlength="100" class="form-control border-start-0 ps-0" name="q" value="<c:out value="${q}"/>" placeholder="Search user by name...">
                            <button class="btn btn-primary px-4" type="submit">Search</button>
                        </div>

                        <div class="row align-items-center g-3">
                            <div class="col-md-6">
                                <label class="form-label text-muted small fw-bold text-uppercase mb-1">Order By</label>
                                <select class="form-select form-select-sm" name="order">
                                    <option value="newest" <c:if test="${empty order or order == 'newest'}">selected</c:if>>Newest Joined</option>
                                    <option value="oldest" <c:if test="${order == 'oldest'}">selected</c:if>>Oldest Joined</option>
                                    <option value="username" <c:if test="${order == 'username'}">selected</c:if>>Alphabetical</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <label class="form-label text-muted small fw-bold text-uppercase mb-1">Filter</label>
                                <select class="form-select form-select-sm" name="filter" id="filter_select">
                                    <option value="all" <c:if test="${empty filter or filter == 'all'}">selected</c:if>>All Users</option>
                                    <option value="admins" <c:if test="${filter == 'admins'}">selected</c:if>>Show Admins Only</option>
                                    <option value="banned" <c:if test="${filter == 'banned'}">selected</c:if>>Show Banned Only</option>
                                </select>
                            </div>
                        </div>
                    </form>
                </div>
            </div>

            <div id="usersContainer">
            </div>

            <div id="loadingIndicator" class="text-center my-4" style="display: none;">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </div>
            <div id="noUsersMessage" class="alert alert-info text-center" style="display: none;">No users found.</div>

        </main>

    </div>
</div>

<%-- Modales --%>
<div class="modal fade" id="deleteUserConfirmModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Confirm Deletion</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p>Are you sure you want to permanently delete this user?</p>
                <p class="text-danger fw-bold mb-0">This action is irreversible.</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-danger" id="confirmDeleteUserBtn">Delete</button>
            </div>
        </div>
    </div>
</div>

<div class="modal fade" id="errorModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header bg-danger text-white">
                <h5 class="modal-title"><i class="bi bi-exclamation-triangle-fill me-2"></i>Error</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body" id="errorModalBody"></div>
            <div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button></div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/jquery-3.6.0.min.js"></script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/admin/admin-manage_users-scripts.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<jsp:include page="messaging_widget.jsp" />


</body>
</html>