import { state, dom } from './chat-state.js';
import { fetchWithAuthCheck } from './messaging_401.js';
import { initializeChatOptions } from './chats_blocked.js';
import { initUserSearch } from './messaging_search_users.js';
import { loadConversations } from './messaging_chat_list.js';
import { initDeletionProcess } from './messaging_options.js';
import { createVideoPreviewElement, startPollingForMessageStatus } from './messaging_polling_preview.js';

// --- FUNCIONES DE LÓGICA DE CHAT ---

const fetchCurrentUserId = async () => {
    try {
        const response = await fetchWithAuthCheck('api/messaging?action=get_current_user_id');
        const data = await response.json();
        if (data.userId) {
            state.currentUserId = data.userId;
        } else {
            console.error("Could not get the current user ID.");
        }
    } catch (error) {
        console.error("Error getting user ID.");
    }
};

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

    // Menos de un minuto
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


/**
 * Inserta un mensaje en la ventana de chat respetando el orden cronológico.
 */
function appendMessageOrdered(newMessageElement, newMessageTimestamp) {
    const container = dom.messageWindow;
    const messages = container.querySelectorAll('.message-wrapper');
    const newTime = new Date(newMessageTimestamp).getTime();

    if (messages.length === 0) {
        container.appendChild(newMessageElement);
        return;
    }

    let inserted = false;
    // Iteramos desde el final hacia arriba
    for (let i = messages.length - 1; i >= 0; i--) {
        const currentMsg = messages[i];
        const currentTime = new Date(currentMsg.dataset.timestamp).getTime();

        if (currentTime <= newTime) {
            container.insertBefore(newMessageElement, currentMsg.nextSibling);
            inserted = true;
            break;
        }
    }

    if (!inserted) {
        container.prepend(newMessageElement);
    }
}

const createMessageElement = (msg) => {
    const isSentByMe = msg.senderId === state.currentUserId;
    const wrapper = document.createElement('div');
    wrapper.classList.add('message-wrapper', isSentByMe ? 'sent' : 'received');
    wrapper.id = msg.is_optimistic ? msg.optimisticId : `message-${msg.id}`;

    // ---  Guardar timestamp ---
    wrapper.dataset.timestamp = msg.timestamp;

    const bubble = document.createElement('div');
    bubble.classList.add('message-bubble');

    switch (msg.message_type) {
        case 'IMAGE':
            const image = document.createElement('img');
            image.src = msg.is_optimistic ? msg.localUrl : `media/${encodeURIComponent(msg.file_path)}`;
            image.classList.add('message-media');
            if (msg.is_optimistic) {
                image.onload = () => bubble.classList.remove('loading');
                bubble.classList.add('loading');
            }
            bubble.appendChild(image);
            break;

        case 'VIDEO':
            // Para mensajes optimistas (mientras sube), mostramos el video directamente.
            if (msg.is_optimistic) {
                const video = document.createElement('video');
                video.src = msg.localUrl;
                video.controls = true;
                video.preload = 'metadata'; // 'metadata' es mejor para la preview local
                video.classList.add('message-media');
                video.oncanplay = () => bubble.classList.remove('loading');
                bubble.classList.add('loading');
                bubble.appendChild(video);
            } else {

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
                    const previewContainer = createVideoPreviewElement(msg.preview_file, msg.file_path);
                    bubble.appendChild(previewContainer);
                }

                // Guardamos el file_path en el wrapper para que el poller lo use
                if (msg.file_path) {
                    wrapper.dataset.filePath = msg.file_path;
                }
            }
            break;

        default: // 'TEXT' y cualquier otro caso
            const content = document.createElement('span');
            // Usamos .textContent y decodeHtmlEntities para mostrar el texto escapado de forma segura
            content.textContent = decodeHtmlEntities(msg.content);
            bubble.appendChild(content);
            break;
    }

    const metadata = document.createElement('span');
    metadata.classList.add('message-metadata');

    if (msg.message_type !== 'TEXT') {
        bubble.classList.add('media-bubble');
    }
    const time = formatRelativeTime(msg.timestamp);
    metadata.innerHTML = `${time} `;

    if (isSentByMe) {
        const readStatus = document.createElement('i');
        readStatus.classList.add('bi', 'read-status');
        if (msg.is_optimistic) {
            readStatus.classList.add('bi-clock'); // Enviando
        } else {
            readStatus.classList.add('bi-check2-all');
            if (msg.is_read) {
                readStatus.classList.add('read');
            }
        }
        metadata.appendChild(readStatus);
    }

    bubble.appendChild(metadata);
    wrapper.appendChild(bubble);
    return wrapper;
};

const markMessagesAsRead = async (partnerId) => {
    if (!partnerId) return;
    try {
        const csrfToken = dom.csrfTokenInput.value;
        await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `action=mark_as_read&partnerId=${partnerId}&csrfToken=${csrfToken}`
        });
    } catch (error) {
        console.error("Error marking messages as read.");
    }
};

const updateReadStatusInView = () => {
    document.querySelectorAll('.message-wrapper.sent .read-status').forEach(icon => {
        if (!icon.classList.contains('read')) {
            icon.classList.remove('bi-clock');
            icon.classList.add('bi-check2-all', 'read');
        }
    });
};

const loadMessages = async (prepend = false) => {
    if (state.isLoading || !state.currentPartnerId) return;
    state.isLoading = true;
    const oldHeight = dom.messageWindow.scrollHeight;

    //dd
    try {
        const url = `api/messaging?action=get_messages&partnerId=${state.currentPartnerId}&offset=${state.messageOffset}`;
        const response = await fetchWithAuthCheck(url);

        if (!response.ok) throw new Error('Error loading messages');

        const data = await response.json();
        const messages = data.messages;

        state.blockStatus = data.blockStatus;
        updateBlockUI();

        if (messages.length > 0) {
            messages.reverse();
            const imageLoadPromises = [];

            messages.forEach(msg => {
                const el = createMessageElement(msg);

                // Promesas de imágenes
                if (msg.message_type === 'IMAGE' && !msg.is_optimistic) {
                    const img = el.querySelector('img.message-media');
                    if (img && !img.complete) {
                        imageLoadPromises.push(new Promise((resolve) => {
                            img.onload = resolve;
                            img.onerror = resolve;
                        }));
                    }
                }

                if (prepend) {
                    dom.messageWindow.prepend(el);
                } else {
                    dom.messageWindow.append(el);
                }

                // Poller para video
                if (msg.message_type === 'VIDEO' && msg.status === 'PROCESSING') {
                    startPollingForMessageStatus(msg.id, el);
                }
            });

            if (prepend) {
                dom.messageWindow.scrollTop = dom.messageWindow.scrollHeight - oldHeight;
            } else {
                // --- Esperar carga de imágenes y LECTURA ---
                try {
                    if (imageLoadPromises.length > 0) {
                        await Promise.all(imageLoadPromises);
                    }
                } catch (err) { console.error("Error loading images."); }

                dom.messageWindow.scrollTop = dom.messageWindow.scrollHeight;

                // Esperamos a que el servidor confirme lectura ANTES de actualizar la lista
                if (state.currentPartnerId) {
                    await markMessagesAsRead(state.currentPartnerId);
                    loadConversations();
                }
            }
            state.messageOffset += messages.length;
        }
    } catch (error) {
        console.error("Error loading messages.");
    }
    finally {
        state.isLoading = false;
    }
};

const sendMessage = async (e) => {
    e.preventDefault();
    const content = dom.messageInput.value.trim();
    const csrfToken = dom.csrfTokenInput.value;
    if (!content || !state.currentPartnerId) return;

    const optimisticId = `optimistic-${Date.now()}`;
    const optimisticMsg = {
        optimisticId: optimisticId,
        senderId: state.currentUserId,
        content: content,
        message_type: 'TEXT',
        timestamp: new Date().toISOString(),
        is_read: false,
        is_optimistic: true
    };

    const messageElement = createMessageElement(optimisticMsg);
    dom.messageWindow.appendChild(messageElement);
    dom.messageWindow.scrollTop = dom.messageWindow.scrollHeight;

    const originalValue = dom.messageInput.value;
    dom.messageInput.value = '';
    dom.messageInput.focus();

    try {
        const response = await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `action=send_message&receiverId=${state.currentPartnerId}&content=${encodeURIComponent(content)}&csrfToken=${csrfToken}`
        });
        const result = await response.json();

        if (!result.success) {
            // Manejamos el error de bloqueo con la info del servidor.
            if (result.errorCode === 'USER_BLOCKED') {
                state.blockStatus = result.blockStatus;
                updateBlockUI();
                const optimisticElement = document.getElementById(optimisticId);
                if (optimisticElement) optimisticElement.remove();
            }
            throw new Error(result.error || 'The message could not be sent.');
        }

        const realMessage = result.message;
        const sentMessageElement = document.getElementById(optimisticId);
        if (sentMessageElement) {
            sentMessageElement.id = `message-${realMessage.id}`;
            const statusIcon = sentMessageElement.querySelector('.read-status');
            if (statusIcon) {
                statusIcon.classList.remove('bi-clock');
                statusIcon.classList.add('bi-check2-all'); // Entregado (✓✓ gris)
            }
        }
        loadConversations();
    } catch (error) {
        console.error('Error sending message.');
        const failedElement = document.getElementById(optimisticId);
        if (failedElement) {
            failedElement.classList.add('message-failed');
            const bubble = failedElement.querySelector('.message-bubble');
            if (bubble) {
                bubble.title = `Error: ${decodeHtmlEntities(error.message)}`;
                const errorIcon = bubble.querySelector('.read-status');
                if (errorIcon) {
                    errorIcon.classList.remove('bi-clock');
                    errorIcon.classList.add('bi-exclamation-circle-fill');
                }
            }
        }
        // Solo restauramos el texto en el input si el error NO FUE por un bloqueo.
        // Si el estado es 'THEY_BLOCKED', el campo debe permanecer vacío.
        if (state.blockStatus !== 'THEY_BLOCKED') {
            dom.messageInput.value = originalValue;
        }
    }
};


const sendFile = async (file) => {
    if (!file || !state.currentPartnerId) return;


    const optimisticId = `optimistic-${Date.now()}`;
    const localUrl = URL.createObjectURL(file); // URL local para la previsualización

    const optimisticMsg = {
        optimisticId: optimisticId,
        senderId: state.currentUserId,
        message_type: file.type.startsWith('image/') ? 'IMAGE' : 'VIDEO',
        localUrl: localUrl, // Pasamos la URL local para la vista previa
        timestamp: new Date().toISOString(),
        is_read: false,
        is_optimistic: true
    };

    const messageElement = createMessageElement(optimisticMsg);
    dom.messageWindow.appendChild(messageElement);
    dom.messageWindow.scrollTop = dom.messageWindow.scrollHeight;
    dom.messageInput.focus();

    const formData = new FormData();
    formData.append('file', file);
    formData.append('receiverId', state.currentPartnerId);
    formData.append('csrfToken', dom.csrfTokenInput.value);

    try {
        const response = await fetchWithAuthCheck('upload-media', {
            method: 'POST',
            body: formData
        });

        // Liberar la memoria usada por la URL del objeto local
        URL.revokeObjectURL(localUrl);

        const result = await response.json();

        if (!result.success) {
            if (result.errorCode === 'USER_BLOCKED') {
                state.blockStatus = result.blockStatus || 'THEY_BLOCKED';
                updateBlockUI();
            }
            throw new Error(result.error || 'The file could not be sent.');
        }

        const realMessage = result.message;
        const sentMessageElement = document.getElementById(optimisticId);
        if (sentMessageElement) {
            // Reemplaza el elemento optimista con el elemento final del servidor
            const finalElement = createMessageElement(realMessage);
            sentMessageElement.replaceWith(finalElement);

            // --- Iniciar el poller si es un video ---
            if (realMessage.message_type === 'VIDEO' && realMessage.status === 'PROCESSING') {
                startPollingForMessageStatus(realMessage.id, finalElement);
            }
        }
        // Actualiza la lista de conversaciones para mostrar el nuevo último mensaje
        loadConversations();

    } catch (error) {
        console.error('Error sending file.');
        const failedElement = document.getElementById(optimisticId);
        if (failedElement) {
            failedElement.classList.add('message-failed');
            const bubble = failedElement.querySelector('.message-bubble');
            if (bubble) bubble.title = `Error: ${decodeHtmlEntities(error.message)}`;
            const errorIcon = failedElement.querySelector('.read-status');
            if (errorIcon) {
                errorIcon.classList.remove('bi-clock');
                errorIcon.classList.add('bi-exclamation-circle-fill');
            }
        }
    }
};

const checkForNewEvents = async () => {
    if (!state.currentPartnerId || document.hidden) return;

    // --- FASE 1: FETCH ---
    let newPollTimestamp = null;
    let events;

    try {
        const url = `api/messaging?action=get_new_events&partnerId=${state.currentPartnerId}&since=${state.lastTimestamp}&_=${Date.now()}`;
        const response = await fetchWithAuthCheck(url);
        if (!response.ok) return;
        events = await response.json();

        if (events.pollTimestamp) {
            newPollTimestamp = events.pollTimestamp;
        } else {
            return; // Respuesta corrupta
        }
    } catch (fetchError) {
        //console.warn("Polling fetch error.");
        return;
    }

    // --- FASE 2: PROCESAMIENTO UI (Con Try/Catch de Seguridad) ---
    try {
        // 1. Procesar nuevos mensajes
        if (events.newMessages && events.newMessages.length > 0) {
            events.newMessages.forEach(msg => {
                try {
                    if (document.getElementById(`message-${msg.id}`)) return;

                    const el = createMessageElement(msg);
                    // --- Usar inserción ordenada para arreglar ---
                    appendMessageOrdered(el, msg.timestamp);

                } catch (msgError) {
                    console.warn("Error rendering message.");
                }
            });

            dom.messageWindow.scrollTop = dom.messageWindow.scrollHeight;

            // --- Esperar confirmación de lectura ---
            await markMessagesAsRead(state.currentPartnerId);
            loadConversations();
        }

        // 2. Procesar eventos de lectura
        if (events.partnerHasRead) {
            updateReadStatusInView();
        }

        // 3. --- FASE 3: COMMIT (Solo si todo salió bien) ---
        state.lastTimestamp = newPollTimestamp;

    } catch (processingError) {
        console.warn("Error processing events, retrying next cycle.");
        // NO actualizamos state.lastTimestamp, forzando reintento en el siguiente ciclo.
    }
};

/**
 * Actualiza la UI de bloqueo de forma asimétrica (sabe quién bloqueó).
 */
function updateBlockUI() {
    // Deshabilitamos todo por defecto y lo activamos según el caso
    dom.messageInput.disabled = true;
    dom.messageForm.querySelector('button[type="submit"]').disabled = true;

    if (dom.attachFileBtn) dom.attachFileBtn.disabled = true;

    dom.blockUserBtn.style.display = 'none';

    // Creamos o encontramos el elemento de texto para "Te han bloqueado"
    let blockedByPeerMessage = document.getElementById('blocked-by-peer-message');
    if (!blockedByPeerMessage) {
        blockedByPeerMessage = document.createElement('span');
        blockedByPeerMessage.id = 'blocked-by-peer-message';
        // Lo añadimos al header para que esté junto al nombre
        dom.chatHeader.appendChild(blockedByPeerMessage);
    }
    blockedByPeerMessage.style.display = 'none';

    switch (state.blockStatus) {
        case 'I_BLOCKED':
            dom.messageInput.placeholder = "Blocked user. Unblock to send messages.";

            // Candado cerrado, SIN texto, color ROJO (btn-danger)
            dom.blockUserBtn.innerHTML = '<i class="bi bi-lock-fill"></i>';
            dom.blockUserBtn.className = 'btn btn-sm btn-danger';
            dom.blockUserBtn.title = 'Unblock user';

            dom.blockUserBtn.style.display = 'inline-block';
            break;

        case 'THEY_BLOCKED':
            dom.messageInput.placeholder = "This user has blocked you.";
            blockedByPeerMessage.textContent = 'They have blocked you';
            blockedByPeerMessage.className = 'badge text-bg-secondary ms-2';
            blockedByPeerMessage.style.display = 'inline-block';
            break;

        case 'NONE':
        default:
            dom.messageInput.disabled = false;
            dom.messageForm.querySelector('button[type="submit"]').disabled = false;

            if (dom.attachFileBtn) dom.attachFileBtn.disabled = false;

            dom.messageInput.placeholder = "Write a message...";

            // Candado abierto, SIN texto, color VERDE (btn-outline-success)
            dom.blockUserBtn.innerHTML = '<i class="bi bi-unlock-fill"></i>';
            dom.blockUserBtn.className = 'btn btn-sm btn-outline-success';
            dom.blockUserBtn.title = 'Block user';

            dom.blockUserBtn.style.display = 'inline-block';
            break;
    }
}

/**
 * Maneja la acción de bloquear o desbloquear.
 */

async function toggleBlock() {
    // No puedes hacer nada si te han bloqueado a ti
    if (state.blockStatus === 'THEY_BLOCKED') {
        return;
    }

    const partnerId = state.currentPartnerId;
    if (!partnerId) return;

    // La acción depende de si TÚ has bloqueado al otro
    const action = (state.blockStatus === 'I_BLOCKED') ? 'unblock_user' : 'block_user';

    try {
        const response = await fetchWithAuthCheck('api/messaging', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `action=${action}&partnerId=${partnerId}&csrfToken=${dom.csrfTokenInput.value}`
        });
        const result = await response.json();

        if (result.success) {
            // Actualizamos el estado localmente al nuevo estado correcto
            state.blockStatus = (action === 'block_user') ? 'I_BLOCKED' : 'NONE';
            updateBlockUI();
            loadConversations();
        }
    } catch (error) {
        console.error(`Error when trying '${action}'.`);
    }
}

function openSidebar() {
    dom.sidebar.classList.add('open');
    dom.sidebarOverlay.classList.add('active');
}

function closeSidebar() {
    dom.sidebar.classList.remove('open');
    dom.sidebarOverlay.classList.remove('active');
}

function selectConversation(partnerId, partnerUsername, partnerProfileImage) {
    // Si se hace clic en el chat ya activo, solo cierra el menú en móvil y no hagas nada más.
    if (state.currentPartnerId === partnerId) {
        if (window.innerWidth < 768) {
            closeSidebar();
        }
        return;
    }

    state.currentPartnerId = partnerId;
    state.messageOffset = 0;
    dom.messageWindow.innerHTML = '';

    // --- Retroceder 60 segundos ---
    state.lastTimestamp = new Date(Date.now() - 60000).toISOString();
    //state.lastTimestamp = new Date().toISOString();

    document.querySelectorAll('.conversation-item').forEach(el => {
        el.classList.toggle('active', el.dataset.partnerId == partnerId);
    });

    dom.chatPartnerName.textContent = decodeHtmlEntities(partnerUsername);
    dom.chatPartnerImg.src = `profile-img?file=${encodeURIComponent(partnerProfileImage || 'default_profile.jpg')}`;
    dom.chatPartnerImg.alt = `Foto de ${partnerUsername}`;

    dom.chatPartnerName.style.cursor = 'default';
    dom.chatPartnerImg.style.cursor = 'default';

    // Nos aseguramos de borrar cualquier evento click previo
    dom.chatPartnerName.onclick = null;
    dom.chatPartnerImg.onclick = null;

    dom.chatPartnerDetails.classList.remove('d-none');

    dom.messageFormContainer.classList.remove('d-none');
    dom.welcomeMessage.classList.add('d-none');
    dom.messageInput.focus();

    if (state.pollingInterval) clearInterval(state.pollingInterval);

    // loadMessages ahora se encarga de marcar como leído internamente
    loadMessages().then(() => {
        // Eliminamos el badge visualmente inmediato para mejor UX
        const convItem = dom.conversationsList.querySelector(`[data-partner-id='${partnerId}']`);
        const badge = convItem?.querySelector('.badge');
        if (badge) badge.remove();

        state.pollingInterval = setInterval(checkForNewEvents, 3000);
    });

    // Cierra el sidebar después de seleccionar una conversación en vista móvil
    if (window.innerWidth < 768) {
        closeSidebar();
    }
}

async function initialize() {
    await fetchCurrentUserId();
    await loadConversations();
    initUserSearch(selectConversation);
    initializeChatOptions();

    // Esto crea un "detector" para el cambio de tamaño de la ventana.
    const mediaQuery = window.matchMedia('(min-width: 768px)');

    // Esta función se ejecutará CADA VEZ que la pantalla cruce el límite de 768px.
    function handleLayoutChange(e) {
        // La 'e.matches' será 'true' si la pantalla es ahora ANCHA (vista de escritorio).
        if (e.matches) {
            // Si entramos en la vista de escritorio, forzamos el cierre del menú.
            // Esto resetea su estado y evita bug.
            closeSidebar();
        }
    }

    mediaQuery.addEventListener('change', handleLayoutChange);

    dom.messageWindow.addEventListener('scroll', () => {
        if (dom.messageWindow.scrollTop === 0 && !state.isLoading) {
            loadMessages(true);
        }
    });

    // Listener para el botón de recarga ---
    dom.refreshConversationsBtn.addEventListener('click', async () => {
        // Deshabilitamos el botón y mostramos un spinner para dar feedback visual
        dom.refreshConversationsBtn.disabled = true;
        dom.refreshConversationsBtn.innerHTML = `<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>`;

        try {
            await loadConversations();
        } catch (error) {
            console.error("Error refreshing conversations manually.");
        } finally {
            // Volvemos a habilitar el botón y restauramos el ícono original
            dom.refreshConversationsBtn.disabled = false;
            dom.refreshConversationsBtn.innerHTML = `<i class="bi bi-arrow-clockwise fs-5"></i>`;
        }
    });

    // 1. Escuchar clics en toda la ventana de mensajes
    dom.messageWindow.addEventListener('click', (e) => {
        // 2. Comprobar si el clic fue sobre una imagen de un mensaje
        if (e.target.classList.contains('message-media') && e.target.tagName === 'IMG') {

            // 3. Obtener la imagen del modal y ponerle el 'src' de la imagen pequeña
            const modalImage = document.getElementById('modalImage');
            modalImage.src = e.target.src;

            // 4. Crear una instancia del modal de Bootstrap y mostrarlo
            const modalElement = document.getElementById('imageModal');
            const imageModal = new bootstrap.Modal(modalElement);
            imageModal.show();

        }
    });

    // El formulario solo envía mensajes de TEXTO
    dom.messageForm.addEventListener('submit', sendMessage);


    if (dom.attachFileBtn && dom.fileInput) {
        // Al hacer clic en el botón de clip, se activa el input de archivo
        dom.attachFileBtn.addEventListener('click', () => {
            dom.fileInput.click();
        });

        // Cuando el usuario selecciona un archivo, se llama a sendFile
        dom.fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) {
                // Validamos ANTES de enviar para evitar errores de red
                const MAX_SIZE = 20 * 1024 * 1024; // 20 MB

                if (file.size > MAX_SIZE) {
                    // Usamos el modal generico 'actionModal'
                    const actionModalEl = document.getElementById('actionModal');
                    if (actionModalEl) {
                        const modalTitle = document.getElementById('modalTitle');
                        const modalBody = document.getElementById('modalBody');
                        const modalConfirmBtn = document.getElementById('modalConfirmBtn');
                        const modalCancelBtn = document.getElementById('modalCancelBtn');

                        modalTitle.textContent = 'File too large';
                        modalBody.textContent = 'The file exceeds the maximum allowed limit of 20 MB.';

                        // Configuramos el botón para cerrar
                        modalConfirmBtn.textContent = 'Accept';
                        modalConfirmBtn.className = 'btn btn-danger';
                        modalConfirmBtn.onclick = () => bootstrap.Modal.getInstance(actionModalEl).hide();

                        modalCancelBtn.style.display = 'none'; // Ocultar cancelar

                        new bootstrap.Modal(actionModalEl).show();
                    } else {
                        // Fallback por si no existe el modal (raro)
                        alert("The file is too large. The maximum size is 20 MB.");
                    }
                } else {
                    sendFile(file);
                }
            }
            // Limpia el valor del input para permitir seleccionar el mismo archivo otra vez
            e.target.value = null;
        });
    }


    // Listener para el botón de bloquear/desbloquear.
    dom.blockUserBtn.addEventListener('click', toggleBlock);

    // Listener para abrir el sidebar con el botón hamburguesa
    dom.sidebarToggleBtn.addEventListener('click', (e) => {
        e.stopPropagation(); // Evita que otros listeners se activen
        openSidebar();
    });

    // Listener para cerrar el sidebar al hacer clic en el overlay
    dom.sidebarOverlay.addEventListener('click', closeSidebar);


    dom.conversationsList.addEventListener('click', (e) => {
        const deleteBtn = e.target.closest('.delete-conversation-btn');
        const convLink = e.target.closest('.conversation-details-link');
        const convItem = e.target.closest('.conversation-item');

        if (deleteBtn) {
            e.preventDefault();
            e.stopPropagation(); // Prevenir cualquier otra acción

            // Obtenemos los datos necesarios del elemento
            const partnerId = parseInt(deleteBtn.dataset.partnerId, 10);
            const partnerUsername = convItem.dataset.partnerUsername;

            // Llamamos a la nueva función que inicia el proceso con un modal
            initDeletionProcess(partnerId, partnerUsername, convItem);

        } else if (convLink) {
            e.preventDefault();
            const partnerId = parseInt(convItem.dataset.partnerId, 10);
            const partnerUsername = convItem.dataset.partnerUsername;
            const partnerProfileImage = convItem.dataset.partnerProfileImage;
            selectConversation(partnerId, partnerUsername, partnerProfileImage);
        }
    });
    //actualizar lista de chats cada 10 segundos
    setInterval(loadConversations, 10000);
}

document.addEventListener('DOMContentLoaded', initialize);