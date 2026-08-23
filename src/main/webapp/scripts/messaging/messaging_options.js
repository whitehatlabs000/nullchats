// scripts/messaging_options.js
import { state, dom } from './chat-state.js';

// Declaramos la variable para la instancia del modal, pero no la inicializamos aún.
let bsModal = null;

// Referencias a los componentes del modal
const actionModalElement = document.getElementById('actionModal');
const modalTitle = document.getElementById('modalTitle');
const modalBody = document.getElementById('modalBody');
const modalConfirmBtn = document.getElementById('modalConfirmBtn');
const modalCancelBtn = document.getElementById('modalCancelBtn');


/**
 * Muestra una notificación (éxito o error) usando el modal genérico.
 * @param {string} title - El título del modal.
 * @param {string} message - El mensaje a mostrar en el cuerpo.
 * @param {boolean} isError - Si es true, el botón de confirmación será rojo.
 */
function showNotificationModal(title, message, isError = false) {
    // Nos aseguramos de tener una instancia del modal
    if (!bsModal) {
        bsModal = new bootstrap.Modal(actionModalElement);
    }

    modalTitle.textContent = title;
    modalBody.textContent = message;

    modalCancelBtn.style.display = 'none';
    modalConfirmBtn.textContent = 'Accept';
    modalConfirmBtn.className = isError ? 'btn btn-danger' : 'btn btn-primary';
    modalConfirmBtn.onclick = () => bsModal.hide();

    bsModal.show();
}


/**
 * Inicia el proceso de eliminación de una conversación, mostrando un modal de confirmación.
 * @param {number} partnerId - El ID del otro usuario.
 * @param {string} partnerUsername - El nombre del otro usuario.
 * @param {HTMLElement} conversationElement - El elemento HTML de la conversación.
 */
export function initDeletionProcess(partnerId, partnerUsername, conversationElement) {
    // Nos aseguramos de tener una instancia del modal antes de usarlo.
    if (!bsModal) {
        bsModal = new bootstrap.Modal(actionModalElement);
    }

    modalTitle.textContent = 'Confirm Deletion';

    // --- INICIO DE CONSTRUCCIÓN SEGURA ---
    modalBody.innerHTML = ''; // Limpiamos

    const text1 = document.createTextNode('Are you sure you want to delete the conversation with ');
    const strong = document.createElement('strong');
    strong.textContent = partnerUsername;
    const text2 = document.createTextNode('?');

    const br1 = document.createElement('br');
    const br2 = document.createElement('br');

    const p = document.createElement('p');
    p.className = 'text-danger';
    p.textContent = 'This action is irreversible and will delete all messages for both parties.';

    modalBody.appendChild(text1);
    modalBody.appendChild(strong);
    modalBody.appendChild(text2);
    modalBody.appendChild(br1);
    modalBody.appendChild(br2);
    modalBody.appendChild(p);
    // --- FIN DE CONSTRUCCIÓN SEGURA ---

    modalCancelBtn.style.display = 'inline-block';
    modalConfirmBtn.textContent = 'Eliminate';
    modalConfirmBtn.className = 'btn btn-danger';

    modalConfirmBtn.onclick = async () => {
        bsModal.hide();

        try {
            const csrfToken = dom.csrfTokenInput.value;
            const response = await fetch('delete-conversation', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `partnerId=${partnerId}&csrfToken=${csrfToken}`
            });

            const result = await response.json();

            if (result.success) {
                if (state.currentPartnerId === partnerId) {
                    dom.messageWindow.innerHTML = '';
                    dom.welcomeMessage.classList.remove('d-none');
                    dom.chatPartnerDetails.classList.add('d-none');
                    dom.messageFormContainer.classList.add('d-none');
                    state.currentPartnerId = null;
                    if (state.pollingInterval) clearInterval(state.pollingInterval);
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

    bsModal.show();
}