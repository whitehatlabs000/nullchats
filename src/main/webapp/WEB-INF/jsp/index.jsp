<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>NullChats · Secure Messaging</title>

    <meta name="description" content="NullChats is a secure, private, and anonymous messaging platform with zero tracking and uncompromising cryptographic security.">
    <meta name="author" content="whitehatlabs000">

    <meta property="og:type" content="website">
    <meta property="og:title" content="NullChats · Secure Messaging">
    <meta property="og:description" content="A manifesto for absolute privacy. No profiles, no tracking, and uncompromising cryptographic security.">
    <meta property="og:site_name" content="NullChats">
    <meta property="og:image" content="${pageContext.request.contextPath}/assets/web-app-manifest-512x512.png">

    <meta name="twitter:card" content="summary">
    <meta name="twitter:title" content="NullChats · Secure Messaging">
    <meta name="twitter:description" content="Secure, private, and anonymous messaging platform.">
    <meta name="twitter:image" content="${pageContext.request.contextPath}/assets/web-app-manifest-512x512.png">

    <script type="application/ld+json">
        {
            "@context": "https://schema.org",
            "@type": "SoftwareApplication",
            "name": "NullChats",
            "applicationCategory": "CommunicationApplication",
            "operatingSystem": "Web",
            "description": "Open-source secure messaging platform emphasizing absolute privacy, zero tracking, and AES-GCM encryption.",
            "author": { "@type": "Person", "name": "whitehatlabs000" },
            "offers": { "@type": "Offer", "price": "0", "priceCurrency": "USD" },
            "codeRepository": "https://github.com/whitehatlabs000/nullchats"
        }
    </script>


    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap-icons.css">
    <link href="${pageContext.request.contextPath}/css/index.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="index-body">

<div class="bg-animation">
    <div class="glow-orb"></div>
    <div class="glow-orb orb-2"></div>
</div>

<main class="d-flex flex-column justify-content-center align-items-center vh-100 position-relative z-1">

    <div class="splash-card text-center p-4 p-md-5 shadow-lg">
        <div class="brand-logo mb-4">
            <i class="bi bi-shield-lock-fill text-primary"></i>
        </div>

        <h1 class="brand-title fw-bold mb-3">NullChats</h1>

        <div class="tagline-container mb-5">
            <span id="typed-tagline" class="text-muted fs-5 font-monospace"></span><span class="cursor">_</span>
        </div>

        <a href="${pageContext.request.contextPath}/messaging" class="btn btn-primary btn-lg launch-btn px-5 py-3 rounded-pill fw-bold">
            Launch App <i class="bi bi-arrow-right ms-2"></i>
        </a>
    </div>

        <div class="position-absolute bottom-0 mb-4 text-center w-100 d-flex flex-column align-items-center gap-2">
            <span class="badge bg-dark bg-opacity-50 text-secondary border border-secondary border-opacity-25 rounded-pill px-3 py-2">
                <i class="bi bi-incognito me-2"></i>Zero Tracking. Total Privacy.
            </span>
            <a href="${pageContext.request.contextPath}/about" class="about-link text-muted text-decoration-none small">
                <i class="bi bi-info-circle me-1"></i>About the project
            </a>
        </div>

</main>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/index.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<jsp:include page="messaging_widget.jsp" />

</body>
</html>