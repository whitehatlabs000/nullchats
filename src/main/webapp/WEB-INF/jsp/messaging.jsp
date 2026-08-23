<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Messages · NullChats</title>
    <meta name="csrf-token" content="${csrfToken}">

    <link href="${pageContext.request.contextPath}/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/bootstrap-icons.css">
    <link href="${pageContext.request.contextPath}/css/messaging.css" rel="stylesheet">

    <script>
        window.APP_BASE_URL = "${pageContext.request.contextPath}";
    </script>

    <jsp:include page="/WEB-INF/jsp/common/head_setup.jsp" />
</head>

<body class="messaging-full-screen">

<div class="d-flex main-container">
    <div id="sidebar" class="d-flex flex-column p-3">

        <%-- PANEL DEL USUARIO ACTUAL --%>
        <div class="d-flex justify-content-between align-items-center mb-4 pb-3 border-bottom">
            <div class="d-flex align-items-center flex-grow-1" style="min-width: 0;">
                <img src="profile-img?file=${sessionScope.profileImg != null ? sessionScope.profileImg : 'default_profile.jpg'}" alt="My Profile" class="profile-img profile-img-md rounded-circle me-2 border">
                <h6 class="mb-0 text-truncate fw-bold">${sessionScope.user}</h6>
            </div>

            <div class="dropdown">
                <button class="btn btn-sm btn-icon" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Options">
                    <i class="bi bi-three-dots-vertical fs-5"></i>
                </button>
                <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                    <li>
                        <div class="dropdown-item d-flex justify-content-between align-items-center pe-3" style="cursor: pointer;" onclick="document.getElementById('themeSwitchDesktop').click();">
                            <span><i class="bi bi-moon-stars-fill me-2"></i>Dark Mode</span>
                            <div class="form-check form-switch mb-0 ms-4">
                                <input class="form-check-input" type="checkbox" role="switch" id="themeSwitchDesktop" style="cursor: pointer;" onclick="event.stopPropagation();">
                            </div>
                        </div>
                    </li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/change_password"><i class="bi bi-key-fill me-2"></i>Change Password</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/logout"><i class="bi bi-box-arrow-right me-2"></i>Logout</a></li>
                </ul>
            </div>
        </div>

        <div class="d-flex justify-content-between align-items-center mb-3">
            <h5 class="mb-0 fw-bold">Chats</h5>
            <div>
                <button class="btn btn-sm btn-icon" id="refresh-conversations-btn" title="Refresh chats">
                    <i class="bi bi-arrow-clockwise fs-5"></i>
                </button>
                <button class="btn btn-sm btn-icon" id="show-blocked-users-btn" title="View blocked users">
                    <i class="bi bi-lock-fill fs-5"></i>
                </button>
            </div>
        </div>
        <div class="mb-3">
            <input type="text" id="user-search-input" class="form-control" placeholder="Search or start a chat..." autocomplete="off">
            <div id="search-results" class="list-group mt-1"></div>
        </div>
        <div id="conversations-list" class="list-group">
            <div class="text-center p-5">
                <div class="spinner-border spinner-border-sm" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </div>
        </div>
    </div>

    <div id="sidebar-overlay"></div>

    <div id="chat-container" class="d-flex flex-column flex-grow-1">
        <div id="chat-header" class="p-3 border-bottom d-flex align-items-center"> <button id="sidebar-toggle-btn" class="btn btn-dark d-md-none me-3">
            <i class="bi bi-list fs-5"></i>
        </button>

            <div id="chat-partner-details" class="d-flex align-items-center flex-grow-1 d-none"> <img id="chat-partner-img" src="" alt="Foto de perfil" class="profile-img profile-img-md rounded-circle me-3">
                <div class="flex-grow-1">
                    <h5 id="chat-partner-name" class="mb-0"></h5>
                </div>
                <button id="block-user-btn" class="btn btn-sm btn-outline-danger ms-3" title="Block user">
                    <i class="bi bi-person-slash-fill"></i>
                </button>
            </div>
        </div>

        <div id="message-window" class="flex-grow-1 p-3">
            <div id="welcome-message" class="d-flex align-items-center justify-content-center h-100">
                <p class="text-muted text-center px-4">Select a chat to view messages or search for a user to start a new conversation.</p>
            </div>
        </div>

        <div id="message-form-container" class="p-3 border-top d-none">
            <form id="message-form" class="d-flex align-items-center">
                <input type="hidden" id="csrfToken" value="${csrfToken}" />
                <button class="btn btn-secondary me-2" type="button" id="attach-file-btn" title="Attach file">
                    <i class="bi bi-paperclip"></i>
                </button>
                <input type="file" id="file-input" accept="image/*,video/*" style="display: none;">
                <input type="text" id="message-input" class="form-control me-2" placeholder="Write a message..." autocomplete="off">
                <button type="submit" class="btn btn-primary" title="Send message">
                    <i class="bi bi-send-fill"></i>
                </button>
            </form>
        </div>
    </div>
</div>


<script src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
<script type="module" src="${pageContext.request.contextPath}/scripts/messaging/messaging.js"></script>
<script src="${pageContext.request.contextPath}/scripts/decodeHtml.js"></script>
<script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

<div class="modal fade" id="actionModal" tabindex="-1" aria-labelledby="modalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="modalTitle"></h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>

            </div>
            <div class="modal-body" id="modalBody">
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" id="modalCancelBtn" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary" id="modalConfirmBtn">Confirm</button>
            </div>
        </div>
    </div>
</div>

<div class="modal fade" id="blockedUsersModal" tabindex="-1" aria-labelledby="blockedUsersModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="blockedUsersModalLabel">Blocked Users</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body" id="blocked-users-list-container">
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            </div>
        </div>
    </div>
</div>

</body>
</html>