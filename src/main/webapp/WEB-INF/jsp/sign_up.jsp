<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sign Up · NullChats</title>
  <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">

  <link href="${pageContext.request.contextPath}/css/auth.css" rel="stylesheet">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />

  <c:if test="${successRedirect}">
    <meta http-equiv="refresh" content="1;url=login" />
  </c:if>

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
      <i class="bi bi-person-plus-fill login-icon"></i>
      <h1 class="h3 mb-2 fw-bold">Create Account</h1>
      <p class="text-muted small">Join us by creating a new account</p>
    </div>

    <c:if test="${not empty error}">
      <div class="alert alert-danger d-flex align-items-center rounded-3 border-0 shadow-sm" role="alert">
        <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
        <div><c:out value="${error}" /></div>
      </div>
    </c:if>

    <c:if test="${not empty ok}">
      <div class="alert alert-success d-flex align-items-center rounded-3 border-0 shadow-sm" role="alert">
        <i class="bi bi-check-circle-fill me-2 fs-5"></i>
        <div>
          <c:out value="${ok}" />
          <p class="mb-0 small opacity-75">Redirecting to login...</p>
        </div>
      </div>
    </c:if>

    <form action="sign_up" method="post" autocomplete="off">

      <input type="hidden" name="csrfToken" value="${csrfToken}">

      <div class="input-group custom-input-group mb-3">
        <span class="input-group-text"><i class="bi bi-person-fill fs-5"></i></span>
        <div class="form-floating">
          <input type="text" class="form-control" id="username" name="username" placeholder="User"
                 maxlength="25" minlength="3" required autocomplete="off">
          <label for="username">Username</label>
        </div>
      </div>

      <div class="input-group custom-input-group mb-4">
        <span class="input-group-text"><i class="bi bi-lock-fill fs-5"></i></span>
        <div class="form-floating">
          <input type="password" class="form-control" id="password" name="password" placeholder="Password"
                 minlength="6" maxlength="100" required autocomplete="new-password">
          <label for="password">Password</label>
        </div>
      </div>

      <button class="btn btn-primary w-100 py-2 mb-3" type="submit">Sign Up</button>
    </form>

    <div class="text-center">
      <p class="text-muted small mb-2">Already have an account?</p>
      <a href="login" class="btn btn-outline-secondary btn-sm w-100">Log In</a>
    </div>

  </div>

  <footer class="mt-4 text-center">
    <p class="text-muted small opacity-75">&copy; NullChats</p>
  </footer>
</main>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>


</body>
</html>