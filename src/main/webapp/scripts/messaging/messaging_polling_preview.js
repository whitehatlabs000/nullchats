// Este es el contenido completo de tu nuevo archivo: messaging_polling_preview.js

import {fetchWithAuthCheck} from './messaging_401.js'; // Importa la dependencia que necesita

/**
 * Función de ayuda para crear solo el contenedor de la vista previa del video.
 * @param {string} previewFile - El nombre del archivo de la miniatura.
 * @param {string} videoFile - El nombre del archivo del video completo.
 * @returns {HTMLElement} El elemento de la vista previa.
 */
export function createVideoPreviewElement(previewFile, videoFile) {
    const previewContainer = document.createElement('div');
    previewContainer.classList.add('video-preview-container');

    if (previewFile) {
        previewContainer.style.backgroundImage = `url('media/${encodeURIComponent(previewFile)}')`;
    } else {
        previewContainer.style.backgroundColor = '#000';
    }

    const playIcon = document.createElement('i');
    playIcon.className = 'bi bi-play-circle-fill video-play-icon';
    previewContainer.appendChild(playIcon);

    // Al hacer clic, reemplazamos la vista previa por el reproductor de video.
    previewContainer.addEventListener('click', () => {
        const videoPlayer = document.createElement('video');
        videoPlayer.src = `media/${encodeURIComponent(videoFile)}`;
        videoPlayer.controls = true;
        videoPlayer.autoplay = true;
        videoPlayer.style.maxWidth = '100%';
        videoPlayer.style.borderRadius = '8px';
        videoPlayer.style.display = 'block';

        previewContainer.innerHTML = ''; // Limpia el ícono de play
        previewContainer.appendChild(videoPlayer);
        previewContainer.style.backgroundImage = 'none';
        previewContainer.classList.remove('video-preview-container');
    }, { once: true }); // El evento solo se dispara una vez.

    return previewContainer;
}


/**
 * Inicia el sondeo (polling) para un ID de mensaje específico.
 * Se detendrá automáticamente cuando el estado sea COMPLETE o FAILED.
 * @param {number} messageId - El ID del mensaje a sondear.
 * @param {HTMLElement} messageElement - El elemento DOM (wrapper) del mensaje.
 */
export function startPollingForMessageStatus(messageId, messageElement) {
    if (!messageElement) return; // Salir si el elemento no existe

    const interval = setInterval(async () => {
        // Comprobar si el elemento sigue en el DOM
        if (!document.body.contains(messageElement)) {
            clearInterval(interval);
            return;
        }

        try {
            const response = await fetchWithAuthCheck(`get-message-status?id=${messageId}&_=${Date.now()}`);
            if (!response.ok) {
                clearInterval(interval); // Dejar de sondear si hay un error
                return;
            }

            const data = await response.json();

            if (data.status === 'PROCESSING') {
                // Continuar sondeando
                return;
            }

            // --- ¡Procesamiento terminado! ---
            clearInterval(interval);

            const bubble = messageElement.querySelector('.message-bubble');
            if (!bubble) return;

            // Primero, encontrar y salvar los metadatos
            const metadata = bubble.querySelector('.message-metadata');
            bubble.innerHTML = ''; // Limpiar (el spinner)

            if (data.status === 'COMPLETE') {
                // Crear y añadir el nuevo elemento de vista previa
                const filePath = messageElement.dataset.filePath;
                const previewContainer = createVideoPreviewElement(data.previewFile, filePath); // Usa la función importada
                bubble.appendChild(previewContainer);

            } else if (data.status === 'FAILED') {
                // Crear y añadir el icono de error
                const errorContainer = document.createElement('div');
                errorContainer.className = 'video-preview-container error';
                errorContainer.innerHTML = '<i class="bi bi-exclamation-triangle-fill text-danger video-play-icon"></i>';
                bubble.appendChild(errorContainer);
            }

            // Volver a añadir los metadatos al final
            if (metadata) {
                bubble.appendChild(metadata);
            }

        } catch (error) {
            console.error("Error polling message status.");
            clearInterval(interval); // Dejar de sondear si hay un error de red
        }
    }, 3000); // Sondear cada 3 segundos
}