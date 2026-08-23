/**
 * Un wrapper para la API fetch que comprueba si la sesión ha expirado (error 401)
 * y redirige al login si es necesario.
 */
export const fetchWithAuthCheck = async (url, options) => {
    const response = await fetch(url, options);

    if (response.status === 401) {
        // En lugar de intentar detener el polling aquí, el error que lanzamos
        // será capturado por el 'catch' de la función que llama,
        // y la redirección detendrá toda la ejecución de todos modos.

        // Evitar el doble slash "//" si la variable global
        // llegara a venir con una barra al final desde el servidor.
        const baseUrl = window.APP_BASE_URL || "";
        const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;

        // Construye la URL de login usando la ruta base limpia
        window.location.href = cleanBaseUrl + '/login';

        // Lanzamos un error para detener la ejecución.
        throw new Error('Session expired');
    }

    return response;
};