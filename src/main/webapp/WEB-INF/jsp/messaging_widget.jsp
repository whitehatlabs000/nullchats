<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:if test="${not empty sessionScope.userId}">


    <link href="${pageContext.request.contextPath}/css/messaging_widget.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-icons.css" rel="stylesheet">

    <script>
        window.APP_BASE_URL = "${pageContext.request.contextPath}";
    </script>

    <%-- Modal para mostrar la lista de usuarios bloqueados --%>
    <div class="modal fade" id="blockedUsersModal" tabindex="-1" aria-labelledby="blockedUsersModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="blockedUsersModalLabel">Blocked Users</h5>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body" id="blocked-users-list-container">
                        <%-- El contenido se cargará aquí con JavaScript --%>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                </div>
            </div>
        </div>
    </div>

    <%-- Modal genérico para confirmaciones y notificaciones de delete conversation--%>
    <div class="modal fade" id="actionModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="modalTitle"></h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body" id="modalBody"></div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" id="modalCancelBtn" data-bs-dismiss="modal">Cancel</button>
                    <button type="button" class="btn btn-primary" id="modalConfirmBtn">Confirm</button>
                </div>
            </div>
        </div>
    </div>

    <div id="chat-widget-container">
        <button id="chat-toggle-btn" class="btn btn-primary shadow-lg position-relative" title="Abrir Chat">
            <i class="bi bi-chat-dots-fill"></i>
            <i class="bi bi-x-lg"></i>
            <span id="widget-unread-badge" class="badge rounded-pill bg-danger d-none"></span>
        </button>

        <div id="chat-window" class="shadow-lg">
            <div id="widget-header" class="p-2 border-bottom d-flex align-items-center">
                <button id="widget-back-btn" class="btn btn-sm btn-icon me-2 d-none"><i class="bi bi-arrow-left"></i></button>

                <h6 id="widget-header-title" class="mb-0 flex-grow-1">Chats</h6>

                <div id="widget-header-info" class="d-none align-items-center flex-grow-1" style="min-width: 0;">
                    <img id="widget-header-img" src="" alt="Foto de perfil" class="profile-img profile-img-sm rounded-circle me-2">
                    <div class="flex-grow-1" style="min-width: 0;">
                        <h6 id="widget-header-partner-name" class="mb-0 text-truncate"></h6>
                    </div>
                    <button id="widget-block-btn" class="btn btn-sm btn-icon ms-2" title="Block user"></button>
                </div>

                <button id="widget-maximize-btn" class="btn btn-sm btn-icon" title="Open in full page"><i class="bi bi-arrows-fullscreen"></i></button>

                <button id="widget-show-blocked-btn" class="btn btn-sm btn-icon" title="See blocked users"><i class="bi bi-lock-fill"></i></button>
                <button id="widget-close-btn" class="btn btn-sm btn-icon" title="Close"><i class="bi bi-x-lg"></i></button>
            </div>

            <div id="widget-search-area" class="p-2 border-bottom position-relative">
                <input type="text" id="widget-user-search" class="form-control form-control-sm" placeholder="Search or start a chat..." autocomplete="off">
                <div id="widget-search-results" class="list-group position-absolute w-100 start-0" style="z-index: 10;"></div>
            </div>

            <div id="widget-body">
                <div id="widget-conversations-list" class="list-group list-group-flush">
                    <div class="text-center p-4"><div class="spinner-border spinner-border-sm"></div></div>
                </div>
                <div id="widget-message-window" class="p-2 d-none"></div>
            </div>

            <div id="widget-footer" class="p-2 border-top d-none">
                <form id="widget-message-form" class="d-flex align-items-center">

                    <button class="btn btn-sm btn-secondary me-2" type="button" id="widget-attach-file-btn" title="Attach file">
                        <i class="bi bi-paperclip"></i>
                    </button>
                    <input type="file" id="widget-file-input" accept="image/*,video/*" style="display: none;">
                    <input type="text" id="widget-message-input" class="form-control form-control-sm" placeholder="Write a message..." autocomplete="off">
                    <button type="submit" class="btn btn-sm btn-primary ms-2" title="Send message"><i class="bi bi-send-fill"></i></button>
                </form>
            </div>
        </div>
    </div>


    <script type="module" src="${pageContext.request.contextPath}/scripts/messaging/messaging_widget.js"></script>
    <script src="${pageContext.request.contextPath}/scripts/decodeHtml.js"></script>
    <script src="${pageContext.request.contextPath}/scripts/csrf-refresher.js" defer></script>

</c:if>