/**
 * Decodifica entidades HTML (como &lt;) a sus caracteres reales (<)
 * de forma segura, sin renderizar HTML ni ejecutar scripts.
 * @param {string} text El texto que viene de la base de datos (ej. "esto es una prueba &lt;")
 * @returns {string} El texto decodificado (ej. "esto es una prueba <")
 */
function decodeHtmlEntities(text) {
    if (typeof text !== 'string') {
        return text;
    }

    // Creamos un elemento temporal en memoria. No es visible en la página.
    const tempElement = document.createElement('textarea');

    // Le asignamos el texto como .innerHTML.
    // El navegador analiza las entidades (ej. &lt;) y las convierte en caracteres (<).

    tempElement.innerHTML = text;

    // Leemos el .value (para textarea) o .textContent, que ahora es texto plano decodificado.
    return tempElement.value;
}