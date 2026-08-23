// Variables de paginación
let currentPage = 1;
let isLoading = false;
let hasMore = true;
const USERS_PER_PAGE = 20;

// Variables de Caché Globales
window.usersCache = [];
window.pageTag = 'admin_manage_users';

function escapeHtml(unsafe) {
    if (unsafe === null || unsafe === undefined) return "";
    return unsafe.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function showErrorModal(message) {
    $('#errorModalBody').text(message);
    const errorModal = new bootstrap.Modal(document.getElementById('errorModal'));
    errorModal.show();
}

function createUserCard(user) {
    const safeUsername = escapeHtml(user.username);
    const profileImg = user.profileImg || 'default_profile.jpg';

    const adminBadge = user.tipo === 'admin' ? '<span class="badge bg-primary ms-2" style="font-size: 0.7em;">Admin</span>' : '';

    const statusBadge = user.active
        ? '<span class="badge bg-success-subtle text-success border border-success-subtle status-badge"><i class="bi bi-check-circle-fill me-1"></i>Active</span>'
        : '<span class="badge bg-danger-subtle text-danger border border-danger-subtle status-badge"><i class="bi bi-slash-circle-fill me-1"></i>Banned</span>';

    const toggleIcon = user.active ? 'bi-lock-fill' : 'bi-unlock-fill';
    const toggleTitle = user.active ? 'Disable Access' : 'Enable Access';
    const toggleButton = `<button type="submit" class="btn-icon edit" title="${toggleTitle}"><i class="bi ${toggleIcon}"></i></button>`;

    // Formatear última conexión
    let lastConnText = 'Never';
    if (user.lastConnection) {
        const dateObj = new Date(user.lastConnection);
        lastConnText = dateObj.toLocaleDateString() + ' ' + dateObj.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
    }

    return `
        <div class="user-card shadow-sm" data-username="${safeUsername}">
            <div class="user-card-body">
                <img src="profile-img?file=${encodeURIComponent(profileImg)}" class="user-avatar" alt="${safeUsername}">
                
                <div class="user-info ms-3">
                    <h5 class="mb-0">
                        <span class="text-body fw-bold">${safeUsername}</span> 
                        ${adminBadge}
                    </h5>
                    <div class="stats mt-1 small text-muted">
                        <span><i class="bi bi-clock-history me-1"></i>Last Connection: ${lastConnText}</span>
                    </div>
                </div>

                <div class="user-status d-none d-sm-block ms-auto me-3">
                    ${statusBadge}
                </div>

                <div class="user-actions ms-auto ms-sm-0">
                    <a href="admin_change_password?u=${safeUsername}" class="btn-icon edit" title="Change Password">
                        <i class="bi bi-key-fill"></i>
                    </a>

                    <form class="d-inline toggle-account-form">
                        <input type="hidden" name="username" value="${safeUsername}">
                        <input type="hidden" name="action" value="${user.active ? 'disable' : 'enable'}">
                        ${toggleButton}
                    </form>

                    <form class="d-inline delete-user-form">
                        <input type="hidden" name="username" value="${safeUsername}">
                        <button type="submit" class="btn-icon delete" title="Delete User">
                            <i class="bi bi-trash-fill"></i>
                        </button>
                    </form>
                </div>
            </div>
        </div>
    `;
}


function loadUsers(page = 1, onComplete) {
    if (isLoading || (!hasMore && page > 1)) {
        if (typeof onComplete === 'function') onComplete();
        return;
    }
    isLoading = true;
    $('#loadingIndicator').show();

    // Extraemos variables para generar la firma de caché
    const q = $('#searchForm input[name="q"]').val().trim();
    const order = $('select[name="order"]').val() || 'newest';
    const filterValue = $('#filter_select').val() || 'all';

    const filterHash = `${q}_${order}_${filterValue}`;
    const cacheKey = `feedCache_${window.pageTag}_${filterHash}`;

    if (page === 1) {
        $('#usersContainer').empty();
        $('#noUsersMessage').hide();
        hasMore = true;
        window.usersCache = [];
    }

    const params = { action: 'load_users', q: q, order: order, page: page };
    if (filterValue !== 'all') params.filter = filterValue;

    $.ajax({
        url: 'admin-manage_users',
        type: 'GET',
        data: params,
        dataType: 'json',
        success: function(users) {
            if (users && users.length > 0) {
                users.forEach(user => {
                    $('#usersContainer').append(createUserCard(user));
                    window.usersCache.push(user); // Guardar en memoria JS
                });

                // Escribir Cache
                try {
                    sessionStorage.setItem(cacheKey, JSON.stringify(window.usersCache));
                } catch(e) {
                    console.warn("Storage quota exceeded. Clearing cache.");
                    sessionStorage.removeItem(cacheKey);
                }

                $('#usersContainer').css('opacity', 1);
                if (users.length < USERS_PER_PAGE) hasMore = false;

                // Fallback de Red
                if (window.restoreScrollTarget && window.restoreScrollTarget > 0) {
                    requestAnimationFrame(() => {
                        window.scrollTo(0, window.restoreScrollTarget);
                        window.restoreScrollTarget = null;
                    });
                }
            } else {
                hasMore = false;
                if (page === 1 && $('#usersContainer').children().length === 0) $('#noUsersMessage').show();
            }
        },
        error: () => {
            console.error("Error loading users.");
            showErrorModal('Could not load user list.');
        },
        complete: () => {
            isLoading = false;
            $('#loadingIndicator').hide();
            currentPage = page + 1;
            if (typeof onComplete === 'function') onComplete();
        }
    });
}

$(document).ready(function() {

    $.ajaxSetup({
        beforeSend: function(xhr) {
            const token = document.querySelector('meta[name="csrf-token"]').getAttribute('content');
            if (token) {
                xhr.setRequestHeader('X-CSRF-Token', token);
            }
        }
    });

    // =================================================================
    // GESTIÓN DE ESTADO Y RESTAURACIÓN
    // =================================================================
    const STATE_KEY = 'adminManageUsersState';
    let isRestoring = false;
    let isManualReload = false;
    let savedState = null;

    try {
        const navEntry = performance.getEntriesByType("navigation")[0];
        const navType = navEntry ? navEntry.type : '';

        if (navType === 'back_forward') {
            const rawState = sessionStorage.getItem(STATE_KEY);
            if (rawState) {
                savedState = JSON.parse(rawState);
                isRestoring = true;
            }
        } else {
            sessionStorage.removeItem(STATE_KEY);
            if (navType === 'reload') {
                isManualReload = true;
                // Higiene de memoria al hacer F5
                Object.keys(sessionStorage).forEach(key => {
                    if (key.startsWith(`feedCache_${window.pageTag}_`)) {
                        sessionStorage.removeItem(key);
                    }
                });
            }
        }
    } catch (e) { console.warn("Navigation state check failed."); }

    // Función auxiliar para limpiar la cache al buscar/filtrar
    function resetAndLoadUsers() {
        currentPage = 1;
        window.usersCache = []; // Limpia memoria
        loadUsers(currentPage);
    }

    $('#searchForm').on('submit', function(e) {
        e.preventDefault();
        resetAndLoadUsers();
    });
    $('select[name="order"], input[name="order"]').on('change', resetAndLoadUsers);
    $('#filter_select').on('change', resetAndLoadUsers);

    $(window).on('scroll', function() {
        if ($(window).scrollTop() + $(window).height() >= $(document).height() - 250 && !isLoading && hasMore) {
            loadUsers(currentPage);
        }
    });

    // --- TOGGLE ACCOUNT (Usando Data Attributes) ---
    $(document).on('submit', '.toggle-account-form', function(e) {
        e.preventDefault();
        const form = $(this);
        // Recuperamos el valor que enviaremos al backend
        const usernameVal = form.find('input[name="username"]').val();

        $.ajax({
            url: 'toggle_account',
            type: 'POST',
            data: form.serialize(),
            dataType: 'json'
        }).done(function(response) {
            if (response.success) {
                // Buscar el contenedor padre más cercano.
                // No necesitamos IDs ni data-attributes aquí, solo el contexto DOM.
                // Esto funciona con CUALQUIER nombre de usuario, por raro que sea.
                const userCard = form.closest('.user-card');
                const statusBadge = userCard.find('.status-badge'); // Buscamos por clase, no por ID

                // Actualizar Badge
                const newBadgeHtml = response.active
                    ? '<i class="bi bi-check-circle-fill me-1"></i>Active'
                    : '<i class="bi bi-slash-circle-fill me-1"></i>Banned';

                statusBadge.html(newBadgeHtml);

                if (response.active) {
                    statusBadge.removeClass('bg-danger-subtle text-danger border-danger-subtle')
                        .addClass('bg-success-subtle text-success border-success-subtle');
                } else {
                    statusBadge.removeClass('bg-success-subtle text-success border-success-subtle')
                        .addClass('bg-danger-subtle text-danger border-danger-subtle');
                }

                // Generar botón nuevo
                const newIcon = response.active ? 'bi-lock-fill' : 'bi-unlock-fill';
                const newTitle = response.active ? 'Disable Access' : 'Enable Access';
                // La variable usernameVal ya tiene el valor seguro (escapado) porque viene del input
                const newFormHtml = `
                    <input type="hidden" name="username" value="${usernameVal}">
                    <input type="hidden" name="action" value="${response.active ? 'disable' : 'enable'}">
                    <button type="submit" class="btn-icon edit" title="${newTitle}"><i class="bi ${newIcon}"></i></button>
                `;
                form.html(newFormHtml);

                // Actualizamos la caché en silencio comparando los valores crudos exactos
                const userInCache = window.usersCache.find(u => u.username === usernameVal);
                if (userInCache) {
                    userInCache.active = response.active;
                    const filterHash = `${$('#searchForm input[name="q"]').val().trim()}_${$('select[name="order"]').val() || 'newest'}_${$('#filter_select').val() || 'all'}`;
                    try { sessionStorage.setItem(`feedCache_${window.pageTag}_${filterHash}`, JSON.stringify(window.usersCache)); } catch(e){}
                }

            } else {
                showErrorModal(response.message || 'Failed to update user status.');
            }
        }).fail(function() {
            showErrorModal('An error occurred while updating user status.');
        });
    });

    // --- DELETE USER (Usando Data Attributes y Filter) ---
    let userToDeleteSafe = null; // Guardamos el username "seguro" (escapado)
    let userToDeleteRaw = null;  // Guardamos el "crudo" para el backend si es necesario

    $(document).on('submit', '.delete-user-form', function(e) {
        e.preventDefault();
        // El input tiene el valor escapado (safeUsername)
        userToDeleteSafe = $(this).find('input[name="username"]').val();

        const deleteModal = new bootstrap.Modal(document.getElementById('deleteUserConfirmModal'));
        deleteModal.show();
    });

    $('#confirmDeleteUserBtn').on('click', function() {
        if (!userToDeleteSafe) return;

        $.ajax({
            url: 'delete_user',
            type: 'POST',
            data: { username: userToDeleteSafe },
            dataType: 'json'
        }).done(function(response) {
            if (response.success) {
                const deleteModal = bootstrap.Modal.getInstance(document.getElementById('deleteUserConfirmModal'));
                deleteModal.hide();
                // En lugar de $('#user-card-' + algo), que puede fallar con '&',
                // usamos .filter().
                // Buscamos todas las cards y filtramos la que tenga el data-username exacto.
                $('.user-card').filter(function() {
                    // Usamos .data() porque decodifica automáticamente para hacer match perfecto
                    return $(this).data('username') === userToDeleteSafe;
                }).fadeOut(500, function() { $(this).remove(); });

                // Eliminamos permanentemente al usuario comparando los valores crudos
                window.usersCache = window.usersCache.filter(u => u.username !== userToDeleteSafe);
                const filterHash = `${$('#searchForm input[name="q"]').val().trim()}_${$('select[name="order"]').val() || 'newest'}_${$('#filter_select').val() || 'all'}`;
                try { sessionStorage.setItem(`feedCache_${window.pageTag}_${filterHash}`, JSON.stringify(window.usersCache)); } catch(e){}

            } else {
                showErrorModal('Failed to delete user.');
            }
        }).fail(function() {
            showErrorModal('An error occurred while deleting user.');
        }).always(function() {
            userToDeleteSafe = null;
        });
    });

    // =================================================================
    // EJECUCIÓN: RESTAURAR O CARGA NORMAL
    // =================================================================
    if (isRestoring && savedState) {
        console.log("[ManageUsers] Restoring state...");
        if ('scrollRestoration' in history) history.scrollRestoration = 'manual';

        // Restaurar inputs
        $('#searchForm input[name="q"]').val(savedState.q || '');
        $('select[name="order"]').val(savedState.order || 'newest');
        $('#filter_select').val(savedState.filter || 'all');

        const savedScrollTop = savedState.scrollTop || 0;
        const filterHash = `${savedState.q || ''}_${savedState.order || 'newest'}_${savedState.filter || 'all'}`;
        const cacheKey = `feedCache_${window.pageTag}_${filterHash}`;

        const cachedDataStr = sessionStorage.getItem(cacheKey);

        if (cachedDataStr) {
            try {
                isLoading = true;
                $("#usersContainer").hide();
                $('#loadingIndicator').show();
                $('html').css('scroll-behavior', 'auto');

                window.usersCache = JSON.parse(cachedDataStr);

                $("#usersContainer").empty();
                window.usersCache.forEach(user => {
                    $('#usersContainer').append(createUserCard(user));
                });

                currentPage = savedState.currentPage || 2;
                hasMore = savedState.hasMore !== undefined ? savedState.hasMore : true;

                requestAnimationFrame(() => {
                    $("#usersContainer").show();
                    requestAnimationFrame(() => {
                        window.scrollTo({ top: savedScrollTop, left: 0, behavior: 'auto' });
                        window.scrollTo(0, savedScrollTop);

                        $('#loadingIndicator').hide();
                        $("#usersContainer").css('opacity', 1);

                        if ('scrollRestoration' in history) history.scrollRestoration = 'auto';
                        $('html').css('scroll-behavior', '');

                        setTimeout(() => {
                            isRestoring = false;
                            isLoading = false;
                        }, 500);
                    });
                });
            } catch(e) {
                console.error("Cache parsing error. Falling back to network.");
                window.restoreScrollTarget = savedScrollTop;
                loadUsers(1, () => { if ('scrollRestoration' in history) history.scrollRestoration = 'auto'; });
            }
        } else {
            console.log("[ManageUsers] Cache missing. Falling back to network.");
            window.restoreScrollTarget = savedScrollTop;
            loadUsers(1, () => { if ('scrollRestoration' in history) history.scrollRestoration = 'auto'; });
        }
    } else {
        // Carga Normal o F5
        if (isManualReload) {
            if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
            window.scrollTo(0, 0);
        } else {
            if ('scrollRestoration' in history) history.scrollRestoration = 'auto';
        }
        loadUsers(1);
    }

    // --- GUARDADO DE ESTADO AL SALIR ---
    $(window).on('beforeunload', function() {
        const state = {
            scrollTop: $(window).scrollTop(),
            q: $('#searchForm input[name="q"]').val().trim(),
            order: $('select[name="order"]').val() || 'newest',
            filter: $('#filter_select').val() || 'all',
            currentPage: currentPage,
            hasMore: hasMore
        };
        sessionStorage.setItem(STATE_KEY, JSON.stringify(state));
    });

});