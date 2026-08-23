import { dom } from './chat-state.js';
import { loadConversations } from './messaging_chat_list.js';

// Obtenemos la instancia del modal de Bootstrap
const blockedUsersModalElement = document.getElementById('blockedUsersModal');
const bsBlockedUsersModal = new bootstrap.Modal(blockedUsersModalElement);
const blockedUsersListContainer = document.getElementById('blocked-users-list-container');

/**
 * Busca y muestra la lista de usuarios bloqueados en el modal.
 */
async function fetchAndShowBlockedUsers() {
    blockedUsersListContainer.innerHTML = `<div class="text-center"><div class="spinner-border spinner-border-sm" role="status"></div></div>`;
    bsBlockedUsersModal.show();

    try {
        const response = await fetch('blocked-users');
        if (!response.ok) throw new Error('Error del servidor al obtener la lista.');

        const blockedUsers = await response.json();

        if (blockedUsers.length === 0) {
            blockedUsersListContainer.innerHTML = '<p class="text-muted text-center">You don\'t have any users blocked.</p>';
            return;
        }

        // Construimos la lista en memoria para evitar repintados
        const fragment = document.createDocumentFragment();
        blockedUsers.forEach(user => {
            const userElement = document.createElement('div');
            userElement.className = 'd-flex align-items-center p-2 border-bottom';
            userElement.id = `blocked-user-${user.id}`;

            // --- INICIO DE CONSTRUCCIÓN SEGURA ---
            const img = document.createElement('img');
            img.src = `profile-img?file=${encodeURIComponent(user.profileImage || 'default_profile.jpg')}`;
            img.alt = `Foto de ${user.username}`;
            img.className = 'profile-img profile-img-sm rounded-circle me-3';

            const usernameDiv = document.createElement('div');
            usernameDiv.className = 'flex-grow-1';
            usernameDiv.textContent = decodeHtmlEntities(user.username); // Seguro y decodificado

            const button = document.createElement('button');
            // rojo y candado cerrado por defecto
            button.className = 'btn btn-sm btn-icon btn-danger unblock-user-btn';
            button.dataset.userId = user.id;
            button.innerHTML = '<i class="bi bi-lock-fill"></i>';
            button.title = 'Unlock user';

            userElement.appendChild(img);
            userElement.appendChild(usernameDiv);
            userElement.appendChild(button);
            // --- FIN DE CONSTRUCCIÓN SEGURA ---

            fragment.appendChild(userElement);
        });
        blockedUsersListContainer.replaceChildren(fragment);

    } catch (error) {
        console.error('Error fetching blocked users.');
        blockedUsersListContainer.innerHTML = `<p class="text-danger text-center">The list of blocked users could not be loaded.</p>`;
    }
}

/**
 * Maneja la solicitud para desbloquear a un usuario.
 * @param {number} userIdToUnblock
 */
async function unblockUser(userIdToUnblock) {
    try {
        const csrfToken = dom.csrfTokenInput.value;
        const response = await fetch('blocked-users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `userIdToUnblock=${userIdToUnblock}&csrfToken=${csrfToken}`
        });

        const result = await response.json();
        if (result.success) {
            // Eliminar al usuario de la lista del modal
            const userElement = document.getElementById(`blocked-user-${userIdToUnblock}`);
            if (userElement) {
                userElement.remove();
            }
            // Comprobar si la lista está vacía ahora
            if (blockedUsersListContainer.children.length === 0) {
                blockedUsersListContainer.innerHTML = '<p class="text-muted text-center">You don\'t have any users blocked.</p>';
            }
            // Recargar la lista principal de chats por si el usuario desbloqueado está allí
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
 * Inicializa todos los listeners relacionados con las opciones de chats.
 */
export function initializeChatOptions() {
    // Listener para el botón de mostrar bloqueados
    dom.showBlockedUsersBtn.addEventListener('click', fetchAndShowBlockedUsers);

    // Listener delegado para los botones de desbloquear dentro del modal
    blockedUsersListContainer.addEventListener('click', e => {
        // Usamos .closest para detectar el clic en el icono o el botón
        const btn = e.target.closest('.unblock-user-btn');

        if (btn) {
            btn.disabled = true;

            // 1. Animación visual inmediata: cambiar a candado abierto y color gris
            btn.innerHTML = '<i class="bi bi-unlock-fill"></i>';
            btn.className = 'btn btn-sm btn-icon btn-outline-secondary unblock-user-btn';

            const userId = parseInt(btn.dataset.userId, 10);

            // 2. Llamada a la API.
            // Si falla, revertimos el estado visual en el catch.
            unblockUser(userId).catch(() => {
                btn.innerHTML = '<i class="bi bi-lock-fill"></i>';
                btn.className = 'btn btn-sm btn-icon btn-danger unblock-user-btn';
                btn.disabled = false;
            });
        }
    });
}