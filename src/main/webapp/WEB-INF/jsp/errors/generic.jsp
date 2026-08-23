<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Unexpected Error - RandomPost</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">
  <link href="${pageContext.request.contextPath}/css/error-pages.css" rel="stylesheet">

  <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="d-flex flex-column min-vh-100">

<main class="flex-grow-1">
  <div class="container error-page-container">

    <%-- Ícono de alerta roja --%>
    <div class="error-icon text-danger opacity-75">
      <i class="bi bi-exclamation-octagon-fill"></i>
    </div>

    <%-- En lugar de un número, ponemos texto, reduciendo ligeramente el tamaño para que encaje bien --%>
    <h1 class="error-code" style="font-size: 6rem;">Error</h1>

    <h2 class="error-title">An Unexpected Error Occurred</h2>
    <p class="error-description">
      Something went wrong that we didn't anticipate. Don't worry, it's not your fault. Try navigating back to the homepage.
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