<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Change Password - ${targetUser} · NullChats</title>
    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/auth.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>

<body class="d-flex flex-column justify-content-center align-items-center min-vh-100 py-5">

<div class="position-fixed top-0 end-0 p-3" style="z-index: 1060;">
    <div class="form-check form-switch">
        <input class="form-check-input" type="checkbox" id="themeSwitchDesktop" title="Change theme">
        <label class="form-check-label" for="themeSwitchDesktop"><i class="bi bi-moon-stars-fill"></i></label>
    </div>
</div>

<main class="form-signin w-100 m-auto p-3" style="max-width: 440px;">

    <div class="card login-card p-4 p-md-5 border-0">
        <div class="text-center mb-4">

            <i class="bi bi-shield-lock-fill login-icon"></i>
            <h1 class="h3 mb-2 fw-bold">Admin Reset</h1>
            <p class="text-muted small">Target user: <strong class="text-body"><c:out value="${targetUser}"/></strong></p>
        </div>

        <c:if test="${not empty error}">
            <div class="alert alert-danger d-flex align-items-center rounded-3 border-0 shadow-sm">
                <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
                <div><c:out value="${error}" /></div>
            </div>
        </c:if>

        <c:if test="${not empty ok}">
            <div class="alert alert-success d-flex align-items-center rounded-3 border-0 shadow-sm">
                <i class="bi bi-check-circle-fill me-2 fs-5"></i>
                <div><c:out value="${ok}" /></div>
            </div>
        </c:if>

        <div id="jsError" class="alert alert-danger d-flex align-items-center rounded-3 border-0 shadow-sm d-none">
            <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
            <div id="jsErrorText"></div>
        </div>

        <form action="admin_change_password" method="post" onsubmit="return validarFormulario();">
            <input type="hidden" name="target_user" value="${targetUser}" />
            <input type="hidden" name="csrfToken" value="${csrfToken}" />

            <div class="input-group custom-input-group mb-3">
                <span class="input-group-text"><i class="bi bi-lock-fill fs-5"></i></span>
                <div class="form-floating">
                    <input type="password" class="form-control" name="new_password" id="new_password" placeholder="New password" required minlength="6" maxlength="100">
                    <label for="new_password">New password</label>
                </div>
            </div>

            <div class="input-group custom-input-group mb-4">
                <span class="input-group-text"><i class="bi bi-check-circle-fill fs-5"></i></span>
                <div class="form-floating">
                    <input type="password" class="form-control" name="confirm_password" id="confirm_password" placeholder="Repeat new password" required minlength="6" maxlength="100">
                    <label for="confirm_password">Repeat new password</label>
                </div>
            </div>

            <button class="btn btn-primary w-100 py-2 mb-3" type="submit">Update Password</button>

            <div class="text-center">
                <a href="admin-manage_users" class="btn btn-outline-secondary btn-sm w-100">← Return to user list</a>
            </div>
        </form>
    </div>

    <footer class="mt-4 text-center">
        <p class="text-muted small opacity-75">&copy; 2026 RandomPost</p>
    </footer>
</main>

<script>
    function validarFormulario() {
        const nueva = document.getElementById('new_password').value;
        const repetir = document.getElementById('confirm_password').value;
        const errorDiv = document.getElementById('jsError');
        const errorText = document.getElementById('jsErrorText');

        if (nueva !== repetir) {
            errorDiv.classList.remove('d-none');
            errorText.textContent = 'Passwords do not match.';
            return false;
        } else {
            errorDiv.classList.add('d-none');
            errorText.textContent = '';
        }

        return true;
    }
</script>
<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>

</body>
</html>