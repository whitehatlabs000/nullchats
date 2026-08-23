/**
 * csrf-refresher.js
 * - Obtiene la ruta base (contextPath) de una variable global provista por el JSP.
 * - Añade una función `syncTokenOnLoad` que se ejecuta en cada carga para manejar
 * cargas normales y pestañas duplicadas de forma robusta.
 * - Incluye un mecanismo anti-caché en todas las llamadas fetch.
 */

if (typeof window.isCsrfRefresherLoaded === 'undefined') {

    window.isCsrfRefresherLoaded = true;

    const csrfChannel = new BroadcastChannel('csrf-channel');

    function updateTokenOnPage(newToken) {
        if (!newToken) return;
        let updatesPerformed = 0;
        const metaTag = document.querySelector('meta[name="csrf-token"]');
        if (metaTag) {
            metaTag.setAttribute('content', newToken);
            updatesPerformed++;
        }
        if (typeof window.csrfToken !== 'undefined') {
            window.csrfToken = newToken;
        }
        const hiddenInputs = document.querySelectorAll('input[name="csrfToken"]');
        if (hiddenInputs.length > 0) {
            hiddenInputs.forEach(input => {
                input.value = newToken;
            });
            updatesPerformed++;
        }

    }

    csrfChannel.onmessage = (event) => {
        if (event.data && event.data.type === 'CSRF_TOKEN_UPDATE' && event.data.token) {
            updateTokenOnPage(event.data.token);
        }
    };

    window.broadcastCsrfToken = (newToken) => {
        csrfChannel.postMessage({
            type: 'CSRF_TOKEN_UPDATE',
            token: newToken
        });
        updateTokenOnPage(newToken);
    };

    /**
     * Obtiene la ruta base desde la variable global 'contextPath' que es
     * inyectada por el JSP (la obtiene del Listener con config.properties).
     */

    function getBaseUrl() {
        if (typeof contextPath !== 'undefined') {
            return contextPath;
        }
        console.error('Global variable "contextPath" is undefined.');
        return ''; // Devuelve vacío como último recurso para evitar errores.
    }

    /**
     * Esta función se ejecuta en CADA carga de página normal.
     * Es la que soluciona el problema de las PESTAÑAS DUPLICADAS.
     */

    function syncTokenOnLoad() {
        const baseUrl = getBaseUrl();
        // FIX: Evitar el doble slash "//" cuando baseUrl es la raíz "/"
        const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
        const cacheBuster = `?t=${new Date().getTime()}`;
        const fetchUrl = `${cleanBaseUrl}/api/csrf-token${cacheBuster}`;

        fetch(fetchUrl, {
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        })
            .then(response => response.ok ? response.json() : Promise.reject('Network error'))
            .then(data => {
                if (data && data.csrfToken) {
                    const domToken = document.querySelector('meta[name="csrf-token"]').getAttribute('content');
                    if (data.csrfToken !== domToken) {
                        broadcastCsrfToken(data.csrfToken);
                    } else {
                        // Notify other tabs in case they are out of sync.
                        csrfChannel.postMessage({
                            type: 'CSRF_TOKEN_UPDATE',
                            token: data.csrfToken
                        });
                    }
                }
            })
            .catch(() => {
                console.error('CSRF token sync failed.');
            });
    }

    window.addEventListener('pageshow', function(event) {
        if (event.persisted) {

            // Reutilizamos nuestra función para obtener la ruta y añadimos anti-caché.
            const baseUrl = getBaseUrl();
            // FIX: Evitar el doble slash "//" cuando baseUrl es la raíz "/"
            const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
            const cacheBuster = `?t=${new Date().getTime()}`;
            const fetchUrl = `${cleanBaseUrl}/api/csrf-token${cacheBuster}`;

            fetch(fetchUrl, {
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
                .then(response => response.ok ? response.json() : Promise.reject('Network error'))
                .then(data => {
                    if (data && data.csrfToken) {
                        updateTokenOnPage(data.csrfToken);
                    } else {
                        console.error('Invalid CSRF token data.');
                    }
                })
                .catch(() => {
                    console.error('Failed to refresh CSRF token.');
                });
        }
    });


    syncTokenOnLoad();
}