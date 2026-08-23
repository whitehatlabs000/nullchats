<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>About · NullChats</title>

    <meta name="description" content="Discover the technical specifications behind NullChats: open-source cryptographic design, AES-GCM secure messaging, and infrastructure protection via eWAF.">
    <meta name="author" content="whitehatlabs000">

    <meta property="og:type" content="website">
    <meta property="og:title" content="About NullChats · Technical Specifications">
    <meta property="og:description" content="A deep dive into our manifesto for absolute privacy. Learn about our encryption, open-source architecture, and anti-abuse systems.">
    <meta property="og:site_name" content="NullChats">
    <meta property="og:image" content="${pageContext.request.contextPath}/assets/web-app-manifest-512x512.png">

    <meta name="twitter:card" content="summary">
    <meta name="twitter:title" content="About NullChats · Technical Specifications">
    <meta name="twitter:description" content="Uncompromising cryptographic security and open-source infrastructure metrics.">
    <meta name="twitter:image" content="${pageContext.request.contextPath}/assets/web-app-manifest-512x512.png">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap-icons.css">
    <link href="${pageContext.request.contextPath}/css/about.css" rel="stylesheet">

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>
<body class="about-body">

<div class="bg-animation">
    <div class="glow-orb"></div>
    <div class="glow-orb orb-2"></div>
</div>

<div class="container py-5 position-relative z-1">

    <div class="mb-5">
        <a href="${pageContext.request.contextPath}/" class="btn btn-outline-light rounded-pill px-4 btn-glass">
            <i class="bi bi-arrow-left me-2"></i>Back to Home
        </a>
    </div>

    <div class="text-center mb-5">
        <i class="bi bi-shield-lock-fill display-1 text-primary mb-3 drop-glow"></i>
        <h1 class="display-4 fw-bold text-gradient">About NullChats</h1>
        <p class="lead text-muted mx-auto" style="max-width: 600px;">
            A manifesto for absolute privacy. No profiles, no tracking, and uncompromising cryptographic security.
        </p>
    </div>

        <div class="row g-4 mb-5">
            <div class="col-md-4">
                <div class="card glass-card h-100 p-4 border-0">
                    <i class="bi bi-file-lock2 fs-2 text-primary mb-3"></i>
                    <h4 class="fw-bold">AES-GCM Encryption</h4>
                    <p class="text-muted small mb-0">
                        Messages are encrypted using AES-256 in GCM mode (NoPadding) with a 12-byte IV and 128-bit authentication tag. NullChats uses AAD (Additional Authenticated Data) to cryptographically bind the sender and receiver IDs to the payload, ensuring messages cannot be tampered with or transplanted across chats.
                    </p>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card glass-card h-100 p-4 border-0">
                    <i class="bi bi-incognito fs-2 text-primary mb-3"></i>
                    <h4 class="fw-bold">Zero Profiles</h4>
                    <p class="text-muted small mb-0">
                        We stripped away social features. There are no public profiles and no followers. You interact using a simple username, transmitting data securely without tying it to a phone number, email, or real-world identity.
                    </p>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card glass-card h-100 p-4 border-0">
                    <i class="bi bi-shield-slash fs-2 text-primary mb-3"></i>
                    <h4 class="fw-bold">Anti-Abuse Systems</h4>
                    <p class="text-muted small mb-0">
                        The application includes a built-in Web Application Firewall (WAF) to manage rate limits, block malicious IPs, and handle spam protection directly from the core.
                    </p>
                </div>
            </div>
        </div>

    <div class="row justify-content-center">
        <div class="col-lg-8">
            <div class="card glass-card p-4 p-md-5 border-0 text-center">
                <h3 class="fw-bold mb-4">Support the Project</h3>
                <p class="text-muted mb-4">
                    NullChats is an independent project. Server costs, security audits, and continuous development are funded exclusively by the community. If you value true privacy, consider making a donation.
                </p>

                <div class="d-flex flex-column gap-3 mb-5 align-items-center">

                    <div class="crypto-box d-flex align-items-center justify-content-between p-3 rounded-3 w-100" style="max-width: 500px;">
                        <div class="d-flex align-items-center text-start overflow-hidden">
                            <i class="bi bi-currency-bitcoin fs-4 me-3 text-secondary"></i>
                            <div>
                                <div class="fw-bold small text-uppercase text-muted">Ethereum (ETH)</div>
                                <div class="text-truncate font-monospace small text-white" id="eth-address">0x22a1dd7cd753ba5c52db23d927fc6ded7025579d</div>
                            </div>
                        </div>
                        <button class="btn btn-sm btn-primary ms-3 flex-shrink-0" onclick="copyToClipboard('eth-address', this)">
                            <i class="bi bi-copy"></i>
                        </button>
                    </div>

                    <div class="crypto-box d-flex align-items-center justify-content-between p-3 rounded-3 w-100" style="max-width: 500px;">
                        <div class="d-flex align-items-center text-start overflow-hidden">
                            <i class="bi bi-currency-bitcoin fs-4 me-3 text-warning"></i>
                            <div>
                                <div class="fw-bold small text-uppercase text-muted">Bitcoin (BTC)</div>
                                <div class="text-truncate font-monospace small text-white" id="btc-address">1LUw27B6wPNibZKrPJGCigV642358rpABg</div>
                            </div>
                        </div>
                        <button class="btn btn-sm btn-primary ms-3 flex-shrink-0" onclick="copyToClipboard('btc-address', this)">
                            <i class="bi bi-copy"></i>
                        </button>
                    </div>
                </div>

                <hr class="border-secondary opacity-25 my-4">

                <h5 class="fw-bold">Open Source & Credits</h5>
                <p class="text-muted small mb-3">
                    NullChats is 100% open-source. Inspect the code, verify our claims, or contribute on
                    <a href="https://github.com/whitehatlabs000/nullchats" target="_blank" class="text-primary text-decoration-none fw-bold"><i class="bi bi-github"></i> GitHub</a>.
                </p>
                <p class="text-muted small mb-2">
                    For security reports or inquiries, reach out at:
                    <a href="mailto:ezequielmesa@proton.me" class="text-primary text-decoration-none fw-bold">ezequielmesa@proton.me</a>
                </p>
                <p class="text-muted small mb-0">
                    Engineered and developed by <span class="text-white fw-bold">whitehatlabs000</span>.
                </p>
            </div>
        </div>
    </div>

</div>

<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>
<jsp:include page="messaging_widget.jsp" />

<script>
    function copyToClipboard(elementId, btnElement) {
        const text = document.getElementById(elementId).innerText;
        navigator.clipboard.writeText(text).then(() => {
            const icon = btnElement.querySelector('i');
            icon.className = 'bi bi-check-lg';
            btnElement.classList.replace('btn-primary', 'btn-success');
            setTimeout(() => {
                icon.className = 'bi bi-copy';
                btnElement.classList.replace('btn-success', 'btn-primary');
            }, 2000);
        });
    }
</script>

</body>
</html>