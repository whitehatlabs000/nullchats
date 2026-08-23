// scripts/messaging_chat_list.js

import { dom, state } from './chat-state.js';
import { fetchWithAuthCheck } from './messaging_401.js';

/**
 * Carga las conversaciones desde el servidor y las muestra en la lista.
 */
export async function loadConversations() {
    try {
        const response = await fetchWithAuthCheck(`api/messaging?action=get_conversations&_=${Date.now()}`);
        if (!response.ok) throw new Error(`Error del servidor: ${response.statusText}`);

        const conversations = await response.json();
        const activePartnerId = state.currentPartnerId;

        // --- 1. Crear un contenedor en memoria ---
        const fragment = document.createDocumentFragment();

        if (conversations.length === 0) {
            const emptyMessage = document.createElement('p');
            emptyMessage.className = 'text-center text-muted p-3';
            emptyMessage.textContent = 'You have no active conversations.';
            // --- 2. Añadir el mensaje de "vacío" al contenedor ---
            fragment.appendChild(emptyMessage);
        } else {
            conversations.forEach(conv => {
                const convElement = document.createElement('div');
                convElement.classList.add('list-group-item', 'list-group-item-action', 'conversation-item', 'd-flex', 'align-items-center');

                if (conv.partnerId === activePartnerId) {
                    convElement.classList.add('active');
                }

                if (conv.blockStatus !== 'NONE') {
                    convElement.classList.add('blocked-conversation');
                }

                convElement.dataset.partnerId = conv.partnerId;
                convElement.dataset.partnerUsername = conv.partnerUsername;
                convElement.dataset.partnerProfileImage = conv.partnerProfileImage;

                // --- INICIO DE CONSTRUCCIÓN SEGURA ---

                // 1. Enlace de la conversación
                const convLink = document.createElement('div');
                convLink.className = 'conversation-details-link d-flex flex-grow-1 align-items-center';
                convLink.style.cursor = 'pointer';
                convLink.style.minWidth = '0';

                const img = document.createElement('img');
                img.src = `profile-img?file=${encodeURIComponent(conv.partnerProfileImage || 'default_profile.jpg')}`;
                img.alt = `Foto de ${conv.partnerUsername}`; // alt es seguro
                img.className = 'profile-img profile-img-sm rounded-circle me-3';

                const infoDiv = document.createElement('div');
                infoDiv.className = 'flex-grow-1';
                infoDiv.style.minWidth = '0';

                const infoHeader = document.createElement('div');
                infoHeader.className = 'd-flex w-100 justify-content-between';

                const usernameH6 = document.createElement('h6');
                usernameH6.className = 'mb-1 text-truncate';
                usernameH6.textContent = decodeHtmlEntities(conv.partnerUsername); // Seguro y decodificado

                infoHeader.appendChild(usernameH6);

                if (conv.unreadCount > 0) {
                    const unreadBadge = document.createElement('span');
                    unreadBadge.className = 'badge bg-primary rounded-pill';
                    unreadBadge.textContent = conv.unreadCount; // ¡SEGURO!
                    infoHeader.appendChild(unreadBadge);
                }

                const lastMessageP = document.createElement('p');
                lastMessageP.className = 'mb-1 text-muted text-truncate';

                if (conv.lastMessage) {
                    // Usamos .textContent y decodeHtmlEntities para mostrar el texto escapado de forma segura.
                    // Si el texto es "📷 Image", la función no le hará nada.
                    // Si es "&lt;b&gt;Hola&lt;/b&gt;", se convertirá en "Hola".
                    lastMessageP.textContent = decodeHtmlEntities(conv.lastMessage);
                } else {
                    // Usamos .textContent y añadimos la clase para la cursiva.
                    lastMessageP.textContent = 'There are no messages yet.';
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
                    <button class="btn btn-sm btn-icon" type="button" data-bs-toggle="dropdown" aria-expanded="false" onclick="event.stopPropagation();">
                        <i class="bi bi-three-dots-vertical"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end">
                        <li>
                            <a class="dropdown-item text-danger delete-conversation-btn" href="#" data-partner-id="${conv.partnerId}">
                                <i class="bi bi-trash-fill me-2"></i>Delete Conversation
                            </a>
                        </li>
                    </ul>
                `;

                convElement.appendChild(convLink);
                convElement.appendChild(dropdownDiv);
                // --- FIN DE CONSTRUCCIÓN SEGURA ---

                // --- 2. Añadir cada chat al contenedor en memoria ---
                fragment.appendChild(convElement);
            });
        }


        dom.conversationsList.replaceChildren(fragment);

    } catch (error) {
        console.error('The conversations could not be loaded.');
        dom.conversationsList.innerHTML = '<p class="text-danger p-3">Error loading chats.</p>';
    }
}