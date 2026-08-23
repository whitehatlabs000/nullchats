<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:if test="${not empty sessionScope.user}">
  <c:redirect url="home"/>
</c:if>

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="${csrfToken}">
  <title>Login · NullChats</title>
  <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/auth.css" rel="stylesheet">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>

<body class="d-flex flex-column justify-content-center align-items-center min-vh-100 py-5">

<div class="position-fixed top-0 end-0 p-3" style="z-index: 1060;">
  <div class="form-check form-switch">
    <input class="form-check-input" type="checkbox" id="themeSwitch" title="Change theme">
    <label class="form-check-label" for="themeSwitch"><i class="bi bi-moon-stars-fill"></i></label>
  </div>
</div>

<main class="form-signin w-100 m-auto p-3" style="max-width: 440px;">

  <div class="card login-card p-4 p-md-5 border-0">

    <div class="text-center mb-4">
      <i class="bi bi-box-arrow-in-right login-icon"></i>
      <h1 class="h3 mb-2 fw-bold">Welcome Back</h1>
      <p class="text-muted small">Enter your credentials to access your account</p>
    </div>

    <c:if test="${not empty error}">
      <div class="alert alert-danger d-flex align-items-center rounded-3 border-0 shadow-sm" role="alert">
        <i class="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
        <div><c:out value="${error}" /></div>
      </div>
    </c:if>

    <form action="login" method="post" autocomplete="off">
      <input type="hidden" name="csrfToken" value="${csrfToken}" />

      <div class="input-group custom-input-group mb-3">
        <span class="input-group-text"><i class="bi bi-person-fill fs-5"></i></span>
        <div class="form-floating">
          <input type="text" class="form-control" id="username" name="username" placeholder="User" required autofocus autocomplete="off">
          <label for="username">Username</label>
        </div>
      </div>

      <div class="input-group custom-input-group mb-4">
        <span class="input-group-text"><i class="bi bi-lock-fill fs-5"></i></span>
        <div class="form-floating">
          <input type="password" class="form-control" id="password" name="password" placeholder="Password" required autocomplete="new-password">
          <label for="password">Password</label>
        </div>
      </div>

      <button class="btn btn-primary w-100 py-2 mb-3" type="submit">Sign In</button>
    </form>

    <div class="text-center">
      <p class="text-muted small mb-2">Don't have an account?</p>
      <a href="sign_up" class="btn btn-outline-secondary btn-sm w-100">Create New Account</a>
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