// scripts/messaging_search_users.js

import { dom } from './chat-state.js';

// Función para buscar usuarios en el servidor.
async function searchUsers(query, selectConversationCallback) {
    if (query.length < 2) {
        dom.searchResults.innerHTML = '';
        return;
    }
    try {
        const response = await fetch(`api/messaging?action=search_users&query=${encodeURIComponent(query)}`);
        if (!response.ok) throw new Error(`Search error: ${response.statusText}`);
        const users = await response.json();
        dom.searchResults.innerHTML = ''; // Limpia resultados anteriores

        if (users.length === 0) {
            dom.searchResults.innerHTML = '<span class="list-group-item text-muted">No users found.</span>';
        } else {
            users.forEach(user => {
                const userElement = document.createElement('a');
                userElement.href = '#';

                userElement.classList.add('list-group-item', 'list-group-item-action', 'd-flex', 'align-items-center');

                // --- INICIO DE CONSTRUCCIÓN SEGURA ---
                const img = document.createElement('img');
                img.src = `profile-img?file=${encodeURIComponent(user.profileImageFilename || 'default_profile.jpg')}`;
                img.alt = `Foto de ${user.username}`;
                img.className = 'profile-img profile-img-sm rounded-circle me-2';

                const infoDiv = document.createElement('div');
                infoDiv.className = 'flex-grow-1';

                const usernameH6 = document.createElement('h6');
                usernameH6.className = 'mb-0';
                usernameH6.textContent = decodeHtmlEntities(user.username); // Seguro y decodificado

                infoDiv.appendChild(usernameH6);
                userElement.appendChild(img);
                userElement.appendChild(infoDiv);
                // --- FIN DE CONSTRUCCIÓN SEGURA ---

                userElement.addEventListener('click', (e) => {
                    e.preventDefault();
                    // Llama al callback con los datos del usuario, incluyendo la imagen.
                    selectConversationCallback(user.id, user.username, user.profileImageFilename);

                    dom.searchResults.innerHTML = '';
                    dom.userSearchInput.value = '';
                });
                dom.searchResults.appendChild(userElement);
            });
        }
    } catch (error) {
        console.error('User search error.');
        dom.searchResults.innerHTML = '<span class="list-group-item text-danger">Search error.</span>';
    }
}

/**
 * Inicializa la funcionalidad de búsqueda de usuarios.
 * @param {Function} selectConversationCallback - La función a llamar cuando se selecciona un usuario.
 */
export function initUserSearch(selectConversationCallback) {
    let searchTimeout;
    dom.userSearchInput.addEventListener('keyup', () => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            const query = dom.userSearchInput.value.trim();
            searchUsers(query, selectConversationCallback);
        }, 300); // Debounce para no saturar con peticiones
    });
}