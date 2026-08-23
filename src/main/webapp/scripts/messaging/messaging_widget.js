import {fetchWithAuthCheck} from './messaging_401.js';
import {state} from './chat-state.js';
import { createVideoPreviewElement, startPollingForMessageStatus } from './messaging_polling_preview.js';

state.widget = {
    messageOffset: 0,
    isLoading: false,
    allMessagesLoaded: false
};

const widgetDom = {
    container: document.getElementById('chat-widget-container'),
    toggleBtn: document.getElementById('chat-toggle-btn'),
    unreadBadge: document.getElementById('widget-unread-badge'),
    window: document.getElementById('chat-window'),
    header: document.getElementById('widget-header'),
    headerTitle: document.getElementById('widget-header-title'),

    // referencias para la cabecera del chat
    headerInfo: document.getElementById('widget-header-info'),
    headerImg: document.getElementById('widget-header-img'),
    headerPartnerName: document.getElementById('widget-header-partner-name'),

    backBtn: document.getElementById('widget-back-btn'),
    closeBtn: document.getElementById('widget-close-btn'),
    body: document.getElementById('widget-body'),
    conversationsList: document.getElementById('widget-conversations-list'),
    messageWindow: document.getElementById('widget-message-window'),
    footer: document.getElementById('widget-footer'),
    messageForm: document.getElementById('widget-message-form'),
    messageInput: document.getElementById('widget-message-input'),

    attachFileBtn: document.getElementById('widget-attach-file-btn'),
    fileInput: document.getElementById('widget-file-input'),
    blockBtn: document.getElementById('widget-block-btn'),
    showBlockedBtn: document.getElementById('widget-show-blocked-btn'),
    maximizeBtn: document.getElementById('widget-maximize-btn'),
    searchArea: document.getElementById('widget-search-area'),
    searchInput: document.getElementById('widget-user-search'),
    searchResults: document.getElementById('widget-search-results'),

};


const blockedUsersModalElement = document.getElementById('blockedUsersModal');
const bsBlockedUsersModal = new bootstrap.Modal(blockedUsersModalElement);
const blockedUsersListContainer = document.getElementById('blocked-users-list-container');

let bsActionModal = null;
const actionModalElement = document.getElementById('actionModal');
const modalTitle = document.getElementById('modalTitle');
const modalBody = document.getElementById('modalBody');
const modalConfirmBtn = document.getElementById('modalConfirmBtn');
const modalCancelBtn = document.getElementById('modalCancelBtn');

function toggleChatWindow() {
    const isOpen = widgetDom.window.classList.toggle('open');
    widgetDom.toggleBtn.classList.toggle('open');

    if (isOpen) {

        if (state.currentPartnerId) {
            // Si reabrimos y ESTAMOS en un chat, reanudamos el polling
            if (state.pollingInterval) clearInterval(state.pollingInterval);

            // Hacemos un chequeo inmediato para traer lo perdido
            checkForNewEvents();

            // Reiniciamos el intervalo
            state.pollingInterval = setInterval(checkForNewEvents, 3000);

        } else {
            // Si estamos en la lista de chats, solo cargamos la lista si es necesario
            if (widgetDom.conversationsList.dataset.loaded !== 'true') {
                loadConversations();
            }
        }

    } else {

        if (state.pollingInterval) clearInterval(state.pollingInterval);
    }
}

function showConversationView(partnerId, partnerUsername, partnerProfileImage) {


    state.currentPartnerId = partnerId;

    // En lugar de usar la hora exacta "ya", usamos "hace 1 minuto".
    // El filtro de duplicados se encargará de que no se repitan.
    state.lastTimestamp = new Date(Date.now() - 60000).toISOString();

    //state.lastTimestamp = new Date().toISOString();
    state.widget.messageOffset = 0;
    state.widget.isLoading = false;
    state.widget.allMessagesLoaded = false;
    widgetDom.messageWindow.innerHTML = '';

    // Ocultar candado
    widgetDom.showBlockedBtn.classList.add('d-none');

    // Ocultar maximizar
    widgetDom.maximizeBtn.classList.add('d-none');

    // Ocultar lista y mostrar ventana de chat
    widgetDom.conversationsList.classList.add('d-none');
    widgetDom.searchArea.classList.add('d-none');
    widgetDom.messageWindow.classList.remove('d-none');
    widgetDom.footer.classList.remove('d-none');

    // Lógica de la cabecera
    widgetDom.headerTitle.classList.add('d-none'); // Oculta el título "Chats"
    widgetDom.headerInfo.classList.remove('d-none'); // Muestra el contenedor de la foto y nombre
    widgetDom.headerInfo.classList.add('d-flex');
    widgetDom.headerImg.src = `profile-img?file=${partnerProfileImage || 'default_profile.jpg'}`;
    widgetDom.headerPartnerName.textContent = partnerUsername;

    widgetDom.backBtn.classList.remove('d-none');

    loadMessages().then(() => {
        if (state.pollingInterval) clearInterval(state.pollingInterval);
        state.pollingInterval = setInterval(checkForNewEvents, 3000);
    });
    widgetDom.searchResults.innerHTML = '';
    widgetDom.searchInput.value = '';
}

function showListView() {
    if (state.pollingInterval) clearInterval(state.pollingInterval);
    state.currentPartnerId = null;

    // Ocultar candado
    widgetDom.showBlockedBtn.classList.remove('d-none');

    // maximizar
    widgetDom.maximizeBtn.classList.remove('d-none')

    // Ocultar ventana de chat y mostrar lista
    widgetDom.conversationsList.classList.remove('d-none');
    widgetDom.searchArea.classList.remove('d-none');
    widgetDom.messageWindow.classList.add('d-none');
    widgetDom.messageWindow.innerHTML = '';
    widgetDom.footer.classList.add('d-none');

    // Logica de la cabecera
    widgetDom.headerTitle.classList.remove('d-none'); // Muestra el título "Chats"
    widgetDom.headerInfo.classList.add('d-none'); // Oculta el contenedor de la foto y nombre
    widgetDom.headerInfo.classList.remove('d-flex');
    widgetDom.backBtn.classList.add('d-none');

    loadConversations();
}


function updateBlockUI() {
    // Reseteamos el estado visual y funcional por defecto
    widgetDom.messageInput.disabled = false;
    widgetDom.messageForm.querySelector('button[type="submit"]').disabled = false;
    if (widgetDom.attachFileBtn) widgetDom.attachFileBtn.disabled = false;
    widgetDom.messageInput.placeholder = "Write a message...";
    widgetDom.blockBtn.style.display = 'none'; // Ocultamos el botón hasta decidir si debe mostrarse

    // Eliminamos el mensaje de "estás bloqueado" si existe
    const blockedByPeerMessage = document.getElementById('widget-blocked-by-peer');
    if (blockedByPeerMessage) blockedByPeerMessage.remove();

    switch (state.blockStatus) {
        case 'I_BLOCKED':
            // Yo he bloqueado al usuario
            widgetDom.messageInput.disabled = true;
            widgetDom.messageForm.querySelector('button[type="submit"]').disabled = true;
            if (widgetDom.attachFileBtn) widgetDom.attachFileBtn.disabled = true;
            widgetDom.messageInput.placeholder = "Unlock to send messages.";

            widgetDom.blockBtn.innerHTML = '<i class="bi bi-lock-fill"></i>'; // Candado cerrado
            widgetDom.blockBtn.className = 'btn btn-sm btn-icon btn-danger ms-2'; // Clase para color rojo
            widgetDom.blockBtn.title = 'Unlock user';
            widgetDom.blockBtn.style.display = 'inline-block';
            break;

        case 'THEY_BLOCKED':
            // El otro usuario me ha bloqueado a mí
            widgetDom.messageInput.disabled = true;
            widgetDom.messageForm.querySelector('button[type="submit"]').disabled = true;
            if (widgetDom.attachFileBtn) widgetDom.attachFileBtn.disabled = true;
            widgetDom.messageInput.placeholder = "This user has blocked you.";

            // No mostramos el botón de bloquear, ya que no podemos hacer nada
            widgetDom.blockBtn.style.display = 'none';
            break;

        case 'NONE':
        default:
            // Nadie ha bloqueado a nadie
            widgetDom.blockBtn.innerHTML = '<i class="bi bi-unlock-fill"></i>'; // Candado abierto
            widgetDom.blockBtn.className = 'btn btn-sm btn-icon btn-outline-secondary ms-2'; // Clase para color gris
            widgetDom.blockBtn.title = 'Block user';
            widgetDom.blockBtn.style.display = 'inline-block';
            break;
    }
}

async function toggleBlock() {
    if (state.blockStatus === 'THEY_BLOCKED' || !state.currentPartnerId) {
        return;
    }

    const action = (state.blockStatus === 'I_BLOCKED') ? 'unblock_user' : 'block_user';
    try {
        const response = await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `action=${action}&partnerId=${state.currentPartnerId}&csrfToken=${document.querySelector('meta[name="csrf-token"]').getAttribute('content')}`
        });
        const result = await response.json();
        if (result.success) {
            state.blockStatus = (action === 'block_user') ? 'I_BLOCKED' : 'NONE';
            updateBlockUI();
            loadConversations();
        }
    } catch (error) {
        console.error(`Error attempting '${action}'.`);
    }
}

async function fetchAndShowBlockedUsers() {
    blockedUsersListContainer.innerHTML = `<div class="text-center"><div class="spinner-border spinner-border-sm" role="status"></div></div>`;
    bsBlockedUsersModal.show();
    try {
        const response = await fetchWithAuthCheck('blocked-users'); // Asume que tienes un servlet en esta URL
        if (!response.ok) throw new Error('Server error getting list.');
        const blockedUsers = await response.json();
        if (blockedUsers.length === 0) {
            blockedUsersListContainer.innerHTML = '<p class="text-muted text-center">You have no blocked users.</p>';
            return;
        }
        const fragment = document.createDocumentFragment();
        blockedUsers.forEach(user => {
            const userElement = document.createElement('div');
            userElement.className = 'd-flex align-items-center p-2 border-bottom';
            userElement.id = `blocked-user-${user.id}`;

            const img = document.createElement('img');
            img.src = `profile-img?file=${encodeURIComponent(user.profileImage || 'default_profile.jpg')}`;
            img.alt = `Foto de ${user.username}`;
            img.className = 'profile-img profile-img-sm rounded-circle me-3';

            const usernameDiv = document.createElement('div');
            usernameDiv.className = 'flex-grow-1';
            usernameDiv.textContent = user.username;

            const button = document.createElement('button');
            // Usamos btn-danger (rojo) y el icono de candado cerrado por defecto
            button.className = 'btn btn-sm btn-icon btn-danger unblock-user-btn';
            button.dataset.userId = user.id;
            button.innerHTML = '<i class="bi bi-lock-fill"></i>';
            button.title = 'Unlock user';

            userElement.appendChild(img);
            userElement.appendChild(usernameDiv);
            userElement.appendChild(button);

            fragment.appendChild(userElement);
        });
        blockedUsersListContainer.replaceChildren(fragment);
    } catch (error) {
        console.error('Failed to load blocked users.');
        blockedUsersListContainer.innerHTML = `<p class="text-danger text-center">Failed to load block list.</p>`;
    }
}

async function unblockUser(userIdToUnblock) {
    try {
        // Obtenemos el token del meta tag
        const csrfToken = document.querySelector('meta[name="csrf-token"]').getAttribute('content');

        const response = await fetchWithAuthCheck('blocked-users', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `userIdToUnblock=${userIdToUnblock}&csrfToken=${csrfToken}`
        });
        const result = await response.json();
        if (result.success) {
            const userElement = document.getElementById(`blocked-user-${userIdToUnblock}`);
            if (userElement) userElement.remove();
            if (blockedUsersListContainer.children.length === 0) {
                blockedUsersListContainer.innerHTML = '<p class="text-muted text-center">You have no blocked users.</p>';
            }
            loadConversations();
        } else {
            throw new Error(result.error || 'The user could not be unblocked.');
        }
    } catch (error) {
        alert(error.message);
        console.error('Error unlocking user.');
    }
}


/**
 * Busca usuarios
 */
async function searchUsers(query, selectConversationCallback) {
    if (query.length < 2) {
        widgetDom.searchResults.innerHTML = '';
        return;
    }
    try {
        const response = await fetchWithAuthCheck(`api/messaging?action=search_users&query=${encodeURIComponent(query)}`);
        if (!response.ok) throw new Error(`Search error: ${response.statusText}`);
        const users = await response.json();
        widgetDom.searchResults.innerHTML = '';

        if (users.length === 0) {
            widgetDom.searchResults.innerHTML = '<span class="list-group-item text-muted">No users found.</span>';
        } else {

            users.forEach(user => {
                const userElement = document.createElement('a');
                userElement.href = '#';
                userElement.className = 'list-group-item list-group-item-action d-flex align-items-center';

                const img = document.createElement('img');
                img.src = `profile-img?file=${user.profileImageFilename || 'default_profile.jpg'}`;
                img.alt = `Foto de ${user.username}`;
                img.className = 'profile-img profile-img-sm rounded-circle me-2';

                const infoDiv = document.createElement('div');
                infoDiv.className = 'flex-grow-1';

                const usernameH6 = document.createElement('h6');
                usernameH6.className = 'mb-0';
                usernameH6.textContent = decodeHtmlEntities(user.username);

                infoDiv.appendChild(usernameH6);
                userElement.appendChild(img);
                userElement.appendChild(infoDiv);

                userElement.addEventListener('click', (e) => {
                    e.preventDefault();
                    selectConversationCallback(user.id, user.username, user.profileImageFilename);
                });
                widgetDom.searchResults.appendChild(userElement);
            });

        }
    } catch (error) {
        console.error('User search error.');
        widgetDom.searchResults.innerHTML = '<span class="list-group-item text-danger">Search error.</span>';
    }
}

/**
 * Inicializa la funcionalidad de busqueda.
 */
function initializeUserSearch(selectConversationCallback) {
    let searchTimeout;
    widgetDom.searchInput.addEventListener('keyup', () => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            const query = widgetDom.searchInput.value.trim();
            searchUsers(query, selectConversationCallback);
        }, 300); // Espera 300ms antes de buscar para no saturar el servidor
    });
}

function showNotificationModal(title, message, isError = false) {
    if (!bsActionModal) bsActionModal = new bootstrap.Modal(actionModalElement);
    // Usamos .textContent y decodificamos ambos para seguridad y formato
    modalTitle.textContent = decodeHtmlEntities(title);
    modalBody.textContent = decodeHtmlEntities(message);
    modalCancelBtn.style.display = 'none';
    modalConfirmBtn.textContent = 'Accept';
    modalConfirmBtn.className = isError ? 'btn btn-danger' : 'btn btn-primary';
    modalConfirmBtn.onclick = () => bsActionModal.hide();
    bsActionModal.show();
}

/**
 * Inicia el proceso de eliminación mostrando un modal de confirmación.
 */
function initDeletionProcess(partnerId, partnerUsername, conversationElement) {
    if (!bsActionModal) bsActionModal = new bootstrap.Modal(actionModalElement);
    modalTitle.textContent = 'Confirm Deletion';

    // --- INICIO DE CONSTRUCCIÓN SEGURA ---
    modalBody.innerHTML = ''; // Limpiamos

    const text1 = document.createTextNode('Are you sure you want to delete the conversation with ');
    const strong = document.createElement('strong');
    strong.textContent = partnerUsername;
    const text2 = document.createTextNode('?');

    const br = document.createElement('br');
    const p = document.createElement('p');
    p.className = 'text-danger mt-2';
    p.textContent = 'This action is irreversible and will delete all messages for both of you.';

    modalBody.appendChild(text1);
    modalBody.appendChild(strong);
    modalBody.appendChild(text2);
    modalBody.appendChild(br);
    modalBody.appendChild(p);
    // --- FIN DE CONSTRUCCIÓN SEGURA ---

    modalCancelBtn.style.display = 'inline-block';
    modalConfirmBtn.textContent = 'Eliminate';
    modalConfirmBtn.className = 'btn btn-danger';
    modalConfirmBtn.onclick = async () => {
        bsActionModal.hide();
        try {
            const response = await fetchWithAuthCheck('delete-conversation', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: `partnerId=${partnerId}&csrfToken=${document.querySelector('meta[name="csrf-token"]').getAttribute('content')}`            });
            const result = await response.json();
            if (result.success) {
                if (state.currentPartnerId === partnerId) {
                    showListView(); // Vuelve a la lista si el chat borrado era el activo
                }
                conversationElement.remove();
                showNotificationModal('Success', 'The conversation has been deleted.');
            } else {
                throw new Error(result.error || 'An unknown error occurred.');
            }
        } catch (error) {
            console.error("Error deleting conversation.");
            showNotificationModal('Error', 'The conversation could not be deleted. Please try again.', true);
        }
    };
    bsActionModal.show();
}

function updateReadStatusInView() {
    widgetDom.messageWindow.querySelectorAll('.message-wrapper.sent .read-status').forEach(icon => {
        if (!icon.classList.contains('read')) {
            icon.classList.remove('bi-clock'); // Quita el reloj por si acaso
            icon.classList.add('bi-check2-all', 'read');
        }
    });
}


async function checkForNewEvents() {
    if (!state.currentPartnerId || document.hidden) return;

    let newPollTimestamp = null;
    let events;

    // --- 1. FASE DE OBTENCIÓN (FETCH) ---
    try {
        const url = `api/messaging?action=get_new_events&partnerId=${state.currentPartnerId}&since=${state.lastTimestamp}&_=${Date.now()}`;
        const response = await fetchWithAuthCheck(url);
        if (!response.ok) {
            // Si el servidor falla (500) o no autoriza (401), no actualiza el timestamp y reintenta.
            return;
        }

        events = await response.json();

        if (events.pollTimestamp) {
            newPollTimestamp = events.pollTimestamp;
        } else {
            // Si no hay timestamp, la respuesta está corrupta. No seguir.
            return;
        }

    } catch (fetchError) {
        // Error de red (ej. sin conexión) o JSON inválido.
        // No actualizamos el timestamp, se reintentará con el antiguo.
        console.warn("Widget polling network or JSON error.");
        return;
    }

    // --- 2. FASE DE PROCESAMIENTO (UI) Y COMMIT ---
    // Ponemos TODO el procesamiento en UN SOLO bloque try/catch.
    try {

        // Bloque A: Procesar nuevos mensajes (lo que YO recibo)
        if (events.newMessages && events.newMessages.length > 0) {
            events.newMessages.forEach(msg => {
                // Añadimos un try/catch interno por si UN solo mensaje viene corrupto
                try {
                    if (document.getElementById(`widget-message-${msg.id}`)) return;
                    const el = createMessageElement(msg);

                    // Usar inserción ordenada en lugar de appendChild ---
                    //widgetDom.messageWindow.appendChild(el);
                    appendMessageOrdered(el, msg.timestamp);
                } catch (msgError) {
                    console.warn(`Error rendering message ${msg.id}.`);
                }
            });

            // Hacemos scroll solo si estamos cerca del final o si es mensaje propio
            widgetDom.messageWindow.scrollTop = widgetDom.messageWindow.scrollHeight;

            // Esperamos a que el servidor confirme que marcamos como leídos.
            await markMessagesAsRead(state.currentPartnerId);

            // Actualizamos la lista de chats para limpiar el contador de "no leídos".
            loadConversations();
        }

        // Bloque B: Procesar eventos de lectura (lo que el OTRO hizo)
        if (events.partnerHasRead) {
            updateReadStatusInView();
        }

        // --- 3. FASE DE CONFIRMACIÓN (COMMIT) ---
        // SOLO si llegamos aquí (sin errores en el try), actualizamos el timestamp.
        state.lastTimestamp = newPollTimestamp;

    } catch (processingError) {
        // Si CUALQUIER COSA en el bloque 'try' falla (renderizar, markAsRead, loadConversations, updateReadStatus)
        // se captura el error aquí.
        console.warn("Error processing events. Retrying next cycle.");

        // ¡NO actualizamos el timestamp!
        // El próximo ciclo de polling (en 3s) usará el timestamp antiguo y reintentará
        // procesar estos mismos eventos.
    }
}

/**
 * Formats a timestamp into a language-neutral, relative time string.
 * e.g., "< 1m", "5m", "3h", or "DD/MM/YYYY".
 * @param {string} timestamp - The ISO string of the date to format.
 * @returns {string} The formatted relative time.
 */
const formatRelativeTime = (timestamp) => {
    const messageDate = new Date(timestamp);
    const now = new Date();
    const diffInSeconds = Math.floor((now - messageDate) / 1000);
    const diffInHours = diffInSeconds / 3600;

    if (diffInSeconds < 60) {
        return '< 1m';
    }

    // Menos de una hora
    const diffInMinutes = Math.floor(diffInSeconds / 60);
    if (diffInMinutes < 60) {
        return `${diffInMinutes}m`;
    }

    // Menos de 24 horas
    if (diffInHours < 24) {
        return `${Math.floor(diffInHours)}h`;
    }

    // Más de 24 horas: mostrar la fecha completa
    const day = String(messageDate.getDate()).padStart(2, '0');
    const month = String(messageDate.getMonth() + 1).padStart(2, '0'); // Los meses son base 0
    const year = messageDate.getFullYear();
    return `${day}/${month}/${year}`;
};

function createMessageElement(msg, imageLoadPromises = []) {
    const isSentByMe = msg.senderId === state.currentUserId;
    const wrapper = document.createElement('div');
    wrapper.className = `message-wrapper ${isSentByMe ? 'sent' : 'received'}`;
    wrapper.id = msg.is_optimistic ? msg.optimisticId : `widget-message-${msg.id}`;

    // Guardamos el timestamp en el DOM para poder ordenar
    wrapper.dataset.timestamp = msg.timestamp;

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    switch (msg.message_type) {
        case 'IMAGE':
            const image = document.createElement('img');
            const loadPromise = new Promise((resolve, reject) => {
                image.onload = resolve;
                image.onerror = reject;
            });
            imageLoadPromises.push(loadPromise);
            image.src = msg.is_optimistic ? msg.localUrl : `media/${msg.file_path}`;
            image.style.maxWidth = '100%';
            image.style.borderRadius = '8px';
            image.style.cursor = 'pointer';
            image.classList.add('widget-message-image');
            bubble.appendChild(image);
            break;

        case 'VIDEO':
            // Para mensajes optimistas (mientras sube), mostramos el video directamente.
            if (msg.is_optimistic) {
                const video = document.createElement('video');
                video.src = msg.localUrl;
                video.style.maxWidth = '100%';
                video.style.borderRadius = '8px';
                video.controls = true;
                video.preload = 'metadata';
                bubble.appendChild(video);
            } else {
                // --- ¡NUEVA LÓGICA DE ESTADO! ---
                if (msg.status === 'PROCESSING') {
                    const processingContainer = document.createElement('div');
                    processingContainer.className = 'video-preview-container processing'; // Fondo negro
                    processingContainer.innerHTML = '<div class="spinner-border spinner-border-sm text-light" role="status"></div>';
                    processingContainer.id = `msg-video-processing-${msg.id}`; // ID para el poller
                    bubble.appendChild(processingContainer);

                } else if (msg.status === 'FAILED') {
                    const errorContainer = document.createElement('div');
                    errorContainer.className = 'video-preview-container error';
                    errorContainer.innerHTML = '<i class="bi bi-exclamation-triangle-fill text-danger video-play-icon"></i>';
                    bubble.appendChild(errorContainer);

                } else { // Estado es COMPLETE
                    // Usamos la nueva función de ayuda
                    const previewContainer = createVideoPreviewElement(msg.preview_file, msg.file_path);
                    bubble.appendChild(previewContainer);
                }

                // Guardamos el file_path en el wrapper para que el poller lo use
                if (msg.file_path) {
                    wrapper.dataset.filePath = msg.file_path;
                }
            }
            break;


        default:
            bubble.textContent = decodeHtmlEntities(msg.content);
            break;

    }

    const time = formatRelativeTime(msg.timestamp);
    const metadata = document.createElement('span');
    metadata.className = 'message-metadata';
    metadata.innerHTML = `${time} `; // Dejar un espacio para el ícono

    // Lógica para crear y añadir el ícono de estado
    if (isSentByMe) {
        const readStatus = document.createElement('i');
        readStatus.classList.add('bi', 'read-status');
        if (msg.is_optimistic) {
            readStatus.classList.add('bi-clock'); // Enviando (reloj)
        } else {
            readStatus.classList.add('bi-check2-all'); // Entregado (doble tick gris)
            if (msg.is_read) {
                readStatus.classList.add('read'); // Leído (doble tick azul)
            }
        }
        metadata.appendChild(readStatus);
    }

    // Añadimos una clase especial a las burbujas con multimedia para que el CSS
    // pueda posicionar los metadatos correctamente como una superposición.
    if(msg.message_type === 'IMAGE' || msg.message_type === 'VIDEO') {
        bubble.classList.add('media-bubble');
    }

    bubble.appendChild(metadata);
    wrapper.appendChild(bubble);
    return wrapper;
}


async function loadConversations() {
    try {
        const response = await fetchWithAuthCheck(`api/messaging?action=get_conversations&_=${Date.now()}`);
        const conversations = await response.json();
        widgetDom.conversationsList.innerHTML = '';
        let totalUnread = 0;

        if (conversations.length === 0) {
            widgetDom.conversationsList.innerHTML = '<p class="text-center text-muted p-3">No active chats.</p>';
        } else {

            conversations.forEach(conv => {
                totalUnread += conv.unreadCount;
                const convElement = document.createElement('div');
                convElement.className = 'list-group-item list-group-item-action conversation-item';

                let lastMessageDisplay = conv.lastMessage;
                if (conv.message_type === 'IMAGE') lastMessageDisplay = '📷 Image';
                else if (conv.message_type === 'VIDEO') lastMessageDisplay = '📹 Video';

                // --- INICIO DE CONSTRUCCIÓN SEGURA ---
                const wrapper = document.createElement('div');
                wrapper.className = 'd-flex w-100';

                // 1. Enlace de la conversación
                const convLink = document.createElement('div');
                convLink.className = 'd-flex flex-grow-1 align-items-center conversation-link';
                convLink.style.minWidth = '0';
                convLink.style.cursor = 'pointer';

                const img = document.createElement('img');
                img.src = `profile-img?file=${conv.partnerProfileImage || 'default_profile.jpg'}`;
                img.alt = `Foto de ${conv.partnerUsername}`; // alt es seguro
                img.className = 'profile-img profile-img-sm rounded-circle me-3';

                const infoDiv = document.createElement('div');
                infoDiv.className = 'flex-grow-1';
                infoDiv.style.minWidth = '0';

                const infoHeader = document.createElement('div');
                infoHeader.className = 'd-flex w-100 justify-content-between';

                const usernameH6 = document.createElement('h6');
                usernameH6.className = 'mb-1 text-truncate';
                usernameH6.textContent = decodeHtmlEntities(conv.partnerUsername); // Seguro y bonito

                infoHeader.appendChild(usernameH6);

                if (conv.unreadCount > 0) {
                    const unreadBadge = document.createElement('span');
                    unreadBadge.className = 'badge bg-primary rounded-pill';
                    unreadBadge.textContent = conv.unreadCount; // ¡SEGURO!
                    infoHeader.appendChild(unreadBadge);
                }

                const lastMessageP = document.createElement('p');
                lastMessageP.className = 'mb-1 text-muted text-truncate';

                if (lastMessageDisplay) {
                    // Si es un tipo de archivo, el texto es estático y seguro (ej. "📷 Image")
                    if (conv.message_type === 'IMAGE' || conv.message_type === 'VIDEO') {
                        lastMessageP.textContent = lastMessageDisplay;
                    } else {
                        // Si es texto, lo decodificamos de forma segura
                        lastMessageP.textContent = decodeHtmlEntities(lastMessageDisplay);
                    }
                } else {
                    // "No messages" no necesita decodificación, pero le añadimos la cursiva
                    lastMessageP.textContent = 'No messages.';
                    lastMessageP.classList.add('fst-italic');
                }

                infoDiv.appendChild(infoHeader);
                infoDiv.appendChild(lastMessageP);
                convLink.appendChild(img);
                convLink.appendChild(infoDiv);

                // 2. Botón de Dropdown (HTML estático, por lo que innerHTML es seguro aquí)
                const dropdownDiv = document.createElement('div');
                dropdownDiv.className = 'dropdown ms-2';
                dropdownDiv.innerHTML = `
                <button class="btn btn-sm btn-icon" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="bi bi-three-dots-vertical"></i>
                </button>
                <ul class="dropdown-menu dropdown-menu-end">
                    <li>
                        <a class="dropdown-item text-danger widget-delete-btn" href="#">
                            <i class="bi bi-trash-fill me-2"></i>Eliminate
                        </a>
                    </li>
                </ul>
            `;

                wrapper.appendChild(convLink);
                wrapper.appendChild(dropdownDiv);
                convElement.appendChild(wrapper);
                // --- FIN DE CONSTRUCCIÓN SEGURA ---

                // Guardamos los datos en el elemento principal para que el listener los pueda usar
                convElement.dataset.partnerId = conv.partnerId;
                convElement.dataset.partnerUsername = conv.partnerUsername;
                convElement.dataset.partnerProfileImage = conv.partnerProfileImage;

                widgetDom.conversationsList.appendChild(convElement);
            });

        }
        if (totalUnread > 0) {
            widgetDom.unreadBadge.textContent = totalUnread > 9 ? '9+' : totalUnread;
            widgetDom.unreadBadge.classList.remove('d-none');
        } else {
            widgetDom.unreadBadge.classList.add('d-none');
        }
        widgetDom.conversationsList.dataset.loaded = 'true';
    } catch (error) {
        console.error('Widget: Could not load conversations.');
        widgetDom.conversationsList.innerHTML = '<p class="text-danger p-3">Error loading chats.</p>';
    }
}

async function loadMessages(prepend = false) {
    if (!state.currentPartnerId || state.widget.isLoading || state.widget.allMessagesLoaded) return;
    state.widget.isLoading = true;
    const spinner = document.createElement('div');
    if (prepend) {
        spinner.className = 'text-center p-2';
        spinner.innerHTML = '<div class="spinner-border spinner-border-sm"></div>';
        widgetDom.messageWindow.prepend(spinner);
    } else {
        widgetDom.messageWindow.innerHTML = '<div class="text-center p-4"><div class="spinner-border spinner-border-sm"></div></div>';
    }
    const oldScrollHeight = widgetDom.messageWindow.scrollHeight;
    try {
        const limit = 20;
        const url = `api/messaging?action=get_messages&partnerId=${state.currentPartnerId}&offset=${state.widget.messageOffset}&limit=${limit}`;
        const response = await fetchWithAuthCheck(url);
        const data = await response.json();
        state.blockStatus = data.blockStatus;
        updateBlockUI();
        const messages = data.messages || [];
        if (!prepend) widgetDom.messageWindow.innerHTML = '';
        const imageLoadPromises = [];
        if (messages.length < limit) state.widget.allMessagesLoaded = true;
        messages.reverse().forEach(msg => {
            const el = createMessageElement(msg, imageLoadPromises);
            if (prepend) widgetDom.messageWindow.prepend(el);
            else widgetDom.messageWindow.appendChild(el);

            // --- Iniciar poller para mensajes cargados ---
            if (msg.message_type === 'VIDEO' && msg.status === 'PROCESSING') {
                startPollingForMessageStatus(msg.id, el);
            }
        });
        // Función para realizar el scroll.
        const scrollToBottom = () => {
            widgetDom.messageWindow.scrollTop = widgetDom.messageWindow.scrollHeight;
        };

        if (prepend) {
            widgetDom.messageWindow.scrollTop = widgetDom.messageWindow.scrollHeight - oldScrollHeight;
        } else {

            try {
                if (imageLoadPromises.length > 0) {
                    // Si hay imágenes, esperamos a que todas carguen.
                    await Promise.all(imageLoadPromises);
                }
            } catch (err) {
                console.error("Error loading chat image.");
                // Hacemos scroll igualmente.
            }

            // Ya sea que las imágenes cargaron o fallaron, o no había,
            // esta parte se ejecuta *después*.
            scrollToBottom();

            // 1. ESPERAMOS a que el servidor confirme la lectura.
            await markMessagesAsRead(state.currentPartnerId);

            // 2. SOLO ENTONCES pedimos la lista de chats actualizada.
            loadConversations();
        }

        state.widget.messageOffset += messages.length;
    } catch (e) {
        console.error("Widget: Error loading messages.");
        widgetDom.messageWindow.innerHTML = '<p class="text-danger p-3">Error loading messages.</p>';
    } finally {
        state.widget.isLoading = false;
        if (prepend && spinner) spinner.remove();
    }
}

async function sendMessage(e) {
    e.preventDefault();
    const content = widgetDom.messageInput.value.trim();
    if (!content || !state.currentPartnerId) return;
    const originalValue = content;
    widgetDom.messageInput.value = '';

    const optimisticMsg = {
        optimisticId: `optimistic-${Date.now()}`,
        senderId: state.currentUserId,
        content: content,
        message_type: 'TEXT',
        timestamp: new Date().toISOString(),
        is_optimistic: true // Clave para mostrar el reloj
    };
    const sentMessageElement = createMessageElement(optimisticMsg);
    widgetDom.messageWindow.appendChild(sentMessageElement);
    widgetDom.messageWindow.scrollTop = widgetDom.messageWindow.scrollHeight;

    try {
        const response = await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `action=send_message&receiverId=${state.currentPartnerId}&content=${encodeURIComponent(content)}&csrfToken=${document.querySelector('meta[name="csrf-token"]').getAttribute('content')}`
        });

        const result = await response.json();
        const finalMessageElement = document.getElementById(optimisticMsg.optimisticId);

        if (result.success) {
            if (finalMessageElement) {
                const finalElement = createMessageElement(result.message);
                finalMessageElement.replaceWith(finalElement);
            }
        } else {
            // Si el error es por bloqueo, actualizamos la UI para mostrarlo.
            if (result.errorCode === 'USER_BLOCKED') {
                state.blockStatus = result.blockStatus;
                updateBlockUI();
            }
            // Lanzamos el error para que el bloque CATCH lo maneje y ponga el ícono de exclamación.
            throw new Error(result.error || 'The message could not be sent.');
        }
    } catch(err) {
        console.error("Widget: Error sending message.");
        // Si el error NO es por la sesión, solo actualiza el ícono, sin alertas.
        if (err.message !== 'Session expired') {

            const failedElement = document.getElementById(optimisticMsg.optimisticId);
            if (failedElement) {
                const errorIcon = failedElement.querySelector('.read-status');
                if (errorIcon) {
                    errorIcon.className = 'bi bi-exclamation-circle-fill text-danger read-status';
                }
            }
        }
    }
}

async function sendFile(file) {
    if (!file || !state.currentPartnerId) return;
    const optimisticId = `optimistic-${Date.now()}`;
    const localUrl = URL.createObjectURL(file);
    const optimisticMsg = {
        optimisticId: optimisticId,
        senderId: state.currentUserId,
        message_type: file.type.startsWith('image/') ? 'IMAGE' : 'VIDEO',
        localUrl: localUrl,
        timestamp: new Date().toISOString(),
        is_optimistic: true
    };
    const messageElement = createMessageElement(optimisticMsg);
    widgetDom.messageWindow.appendChild(messageElement);
    widgetDom.messageWindow.scrollTop = widgetDom.messageWindow.scrollHeight;
    const formData = new FormData();
    formData.append('file', file);
    formData.append('receiverId', state.currentPartnerId);
    formData.append('csrfToken', document.querySelector('meta[name="csrf-token"]').getAttribute('content'));
    try {
        const response = await fetchWithAuthCheck('upload-media', {method: 'POST', body: formData});
        URL.revokeObjectURL(localUrl);

        const result = await response.json();
        const sentMessageElement = document.getElementById(optimisticId);
        if (!result.success) {
            // Si el error es por bloqueo, actualizamos la UI para mostrarlo.
            if (result.errorCode === 'USER_BLOCKED') {
                state.blockStatus = result.blockStatus;
                updateBlockUI();
            }
            // Lanzamos el error para que el bloque CATCH lo maneje y ponga el ícono de exclamación.
            throw new Error(result.error || 'The file could not be sent.');
        }
        if (sentMessageElement) {
            const finalElement = createMessageElement(result.message);
            sentMessageElement.replaceWith(finalElement);

            // --- Iniciar el poller si es un video ---
            if (result.message.message_type === 'VIDEO' && result.message.status === 'PROCESSING') {
                startPollingForMessageStatus(result.message.id, finalElement);
            }
        }
        loadConversations();
    } catch (error) {
        // 1. Si es error de sesión, redirigir
        if (error.message.includes('The session may have expired')) {
            window.location.href = 'login';
            return;
        }

        console.error('Error sending file.');

        // 2. Actualizamos el ícono a exclamación roja (feedback visual en la burbuja)
        const failedElement = document.getElementById(optimisticId);
        if (failedElement) {
            const errorIcon = failedElement.querySelector('.read-status');
            if (errorIcon) {
                errorIcon.className = 'bi bi-exclamation-circle-fill text-danger read-status';
            }
        }

        // 3. Mostramos el modal con el mensaje exacto del servidor
        showNotificationModal('Upload Failed', error.message, true);
    }
}

async function markMessagesAsRead(partnerId) {

    if (!partnerId) return;
    try {
        await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `action=mark_as_read&partnerId=${partnerId}&csrfToken=${document.querySelector('meta[name="csrf-token"]').getAttribute('content')}`
        });
    } catch (error) {
        console.error("Widget: Error marking messages as read.");
    }
};

async function fetchCurrentUserId() {

    try {
        const response = await fetchWithAuthCheck('api/messaging?action=get_current_user_id');
        const data = await response.json();
        if (data.userId) {
            state.currentUserId = data.userId;
        }
    } catch (error) {
        console.error("Widget: Error fetching user ID.");
    }
};

async function initializeWidget() {
    if (!widgetDom.container) return;
    await fetchCurrentUserId();
    widgetDom.toggleBtn.addEventListener('click', toggleChatWindow);
    widgetDom.closeBtn.addEventListener('click', toggleChatWindow);
    widgetDom.backBtn.addEventListener('click', showListView);
    widgetDom.messageForm.addEventListener('submit', sendMessage);
    widgetDom.messageWindow.addEventListener('scroll', () => {
        if (widgetDom.messageWindow.scrollTop === 0) {
            loadMessages(true);
        }
    });
    if (widgetDom.attachFileBtn && widgetDom.fileInput) {
        widgetDom.attachFileBtn.addEventListener('click', () => {
            widgetDom.fileInput.click();
        });
        widgetDom.fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) {
                // 50 MB (Debe coincidir o ser menor nuestro @MultipartConfig en el servlet)
                const MAX_SIZE = 50 * 1024 * 1024;

                if (file.size > MAX_SIZE) {
                    showNotificationModal('File too large', 'The file exceeds the maximum allowed limit.', true);
                } else {
                    sendFile(file);
                }
            }
            e.target.value = null; // Limpiamos el input siempre
        });
    }
    widgetDom.maximizeBtn.addEventListener('click', () => {
        window.location.href = 'messaging';
    });

    widgetDom.blockBtn.addEventListener('click', toggleBlock);


    widgetDom.conversationsList.addEventListener('click', (e) => {
        const conversationLink = e.target.closest('.conversation-link');
        const deleteBtn = e.target.closest('.widget-delete-btn');
        const convItem = e.target.closest('.conversation-item');

        if (deleteBtn) {
            e.preventDefault();
            e.stopPropagation();
            const partnerId = parseInt(convItem.dataset.partnerId, 10);
            const partnerUsername = convItem.dataset.partnerUsername;
            initDeletionProcess(partnerId, partnerUsername, convItem);
        } else if (conversationLink) {
            e.preventDefault();
            const partnerId = parseInt(convItem.dataset.partnerId, 10);
            const partnerUsername = convItem.dataset.partnerUsername;
            const partnerProfileImage = convItem.dataset.partnerProfileImage;
            showConversationView(partnerId, partnerUsername, partnerProfileImage);
        }
    });

    // Listener para abrir imágenes en un modal
    widgetDom.messageWindow.addEventListener('click', (e) => {
        if (e.target.classList.contains('widget-message-image')) {
            const imageModalElement = document.getElementById('imageModal');
            if (!imageModalElement) return;

            const modalImage = imageModalElement.querySelector('#modalImage');
            modalImage.src = e.target.src;

            const bsModal = new bootstrap.Modal(imageModalElement);
            bsModal.show();
        }
    });

    widgetDom.showBlockedBtn.addEventListener('click', fetchAndShowBlockedUsers);

    blockedUsersListContainer.addEventListener('click', e => {
        // Usamos .closest() para detectar el clic aunque sea en el ícono <i>
        const btn = e.target.closest('.unblock-user-btn');

        if (btn) {
            btn.disabled = true;
            // 1. Animación visual inmediata: cambiar a candado abierto y color gris
            btn.innerHTML = '<i class="bi bi-unlock-fill"></i>';
            btn.className = 'btn btn-sm btn-icon btn-outline-secondary unblock-user-btn';

            const userId = parseInt(btn.dataset.userId, 10);

            // 2. Llamamos a la función. Si tiene éxito, la fila se elimina sola (en unblockUser).
            // Si falla, revertimos el icono al estado original.
            unblockUser(userId).catch(() => {
                btn.innerHTML = '<i class="bi bi-lock-fill"></i>';
                btn.className = 'btn btn-sm btn-icon btn-danger unblock-user-btn';
                btn.disabled = false;
            });
        }
    });

    initializeUserSearch(showConversationView);

    loadConversations();
    setInterval(loadConversations, 15000);

    // Solución para el 'lag' al cambiar de pestaña
    document.addEventListener('visibilitychange', () => {
        // Si la pestaña vuelve a estar visible Y estamos en un chat
        if (!document.hidden && state.currentPartnerId) {
            checkForNewEvents(); // Ejecuta un chequeo inmediato
        }
    });

}

document.addEventListener('DOMContentLoaded', initializeWidget);


/**
 * Función auxiliar para forzar la apertura del widget.
 * @param {boolean} forceOpen - Si es true, abre la ventana si está cerrada.
 */

function ensureWidgetIsOpen(forceOpen = false) {
    if (forceOpen && !widgetDom.window.classList.contains('open')) {
        toggleChatWindow();
    }
}

/**
 * Inserta un mensaje en la ventana de chat respetando el orden cronológico.
 * Soluciona el problema de mensajes desordenados (1,2,3,5,6,4).
 */

function appendMessageOrdered(newMessageElement, newMessageTimestamp) {
    const container = widgetDom.messageWindow;
    const messages = container.querySelectorAll('.message-wrapper');
    const newTime = new Date(newMessageTimestamp).getTime();

    // Si no hay mensajes, simplemente agregamos
    if (messages.length === 0) {
        container.appendChild(newMessageElement);
        return;
    }

    // Iteramos desde el final hacia arriba para encontrar dónde encajar
    let inserted = false;
    for (let i = messages.length - 1; i >= 0; i--) {
        const currentMsg = messages[i];
        const currentTime = new Date(currentMsg.dataset.timestamp).getTime();

        // Si el mensaje que estamos revisando es más VIEJO que el nuevo,
        // el nuevo va DESPUÉS de este.
        if (currentTime <= newTime) {
            // insertBefore con nextSibling inserta después del elemento actual
            container.insertBefore(newMessageElement, currentMsg.nextSibling);
            inserted = true;
            break;
        }
    }

    // Si llegamos al principio y no lo insertamos (es el más viejo de todos), va al inicio
    if (!inserted) {
        container.prepend(newMessageElement);
    }
}


// API global para controlar el widget desde otros scripts
window.chatWidgetApi = {
    openWithUser: (partnerId, partnerUsername, partnerProfileImage) => {
        ensureWidgetIsOpen(true); // Asegura que el widget esté abierto
        showConversationView(partnerId, partnerUsername, partnerProfileImage);
    }
};