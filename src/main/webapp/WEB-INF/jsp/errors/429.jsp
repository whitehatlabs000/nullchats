<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>429 Too Many Requests - RandomPost</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/error-pages.css" rel="stylesheet">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="d-flex flex-column min-vh-100">

<main class="flex-grow-1">
  <div class="container error-page-container">

    <%-- Ícono de reloj de arena / espera --%>
    <div class="error-icon text-warning opacity-75">
      <i class="bi bi-hourglass-split"></i>
    </div>

    <h1 class="error-code">429</h1>

    <h2 class="error-title">Too Many Requests</h2>
    <p class="error-description">
      Whoa there, speedster! You've sent too many requests in a short amount of time. Please take a breather and try again in a few moments.
    </p>

    <div class="d-flex gap-3 justify-content-center flex-wrap">
      <a href="${pageContext.request.contextPath}/" class="btn btn-primary px-5 py-2 rounded-pill fw-bold shadow-sm">
        <i class="bi bi-house-door-fill me-2"></i> Return to Homepage
      </a>
    </div>

  </div>
</main>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>

</body>
</html>