<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%-- Script de Tema (Dark/Light Mode) --%>
<script>

    <%-- Variable Global (Usada por csrf-refresher.js) --%>
    const contextPath = "${appBaseUrl}";

    (() => {
        <%-- // Función para establecer el tema en el HTML. --%>
        const setTheme = (theme) => {
            document.documentElement.setAttribute('data-bs-theme', theme);
        };

        <%-- // --- PRIORIDAD 1: Verificar si el usuario ya guardó un tema. --%>
        const storedTheme = localStorage.getItem('bs-theme');
        if (storedTheme) {
            setTheme(storedTheme);
            return;
        }

        <%-- // Si no hay tema guardado, el valor por defecto inicial es 'dark'. --%>
        let defaultTheme = 'dark';

        <%-- // --- PRIORIDAD 2 preferencia del Sistema Operativo. --%>


        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            defaultTheme = 'dark'; <%-- // Si el SO está en modo oscuro, se convierte en el nuevo defecto. --%>
        }

        setTheme(defaultTheme);

        <%-- //  Se guarda el tema por defecto para futuras páginas. --- --%>
        localStorage.setItem('bs-theme', defaultTheme);
    })();


    <%-- Eventos del DOM: Interactividad de los botones --%>

    <%-- Usamos una bandera para asegurarnos de que este script solo se configure una vez. --%>
    if (!window.themeSwitcherAttached) {
        document.addEventListener('DOMContentLoaded', function () {
            const mobileSwitch = document.getElementById('themeSwitch');
            const desktopSwitch = document.getElementById('themeSwitchDesktop');

            const handleThemeChange = (isChecked) => {
                const theme = isChecked ? 'dark' : 'light';
                document.documentElement.setAttribute('data-bs-theme', theme);
                try {
                    localStorage.setItem('bs-theme', theme);
                } catch (e) {
                    console.error("Could not save theme to localStorage.");
                }

                if (mobileSwitch && desktopSwitch) {
                    if (mobileSwitch.checked !== isChecked) mobileSwitch.checked = isChecked;
                    if (desktopSwitch.checked !== isChecked) desktopSwitch.checked = isChecked;
                }
            };

            const currentTheme = document.documentElement.getAttribute('data-bs-theme') || 'light';
            const isDark = currentTheme === 'dark';

            <%-- Sincronizar el estado de los switches con el tema actual al cargar --%>
            if (mobileSwitch) mobileSwitch.checked = isDark;
            if (desktopSwitch) desktopSwitch.checked = isDark;

            <%-- Escuchar cambios --%>
            if (mobileSwitch) {
                mobileSwitch.addEventListener('change', (event) => handleThemeChange(event.target.checked));
            }
            if (desktopSwitch) {
                desktopSwitch.addEventListener('change', (event) => handleThemeChange(event.target.checked));
            }
        });
        window.themeSwitcherAttached = true;
    }
</script>

<%-- INICIO MODAL IMAGEN (Simplificado para NullChats) --%>
<div class="modal fade" id="imageModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered modal-xl">
        <div class="modal-content bg-transparent border-0">
            <div class="modal-body text-center p-0">
                <img id="modalImage" src="" class="img-fluid" alt="Expanded image">
            </div>
            <div class="modal-footer border-0 justify-content-center">

                <button type="button" class="btn btn-light modal-nav-btn" data-bs-dismiss="modal" aria-label="Close">
                    <i class="bi bi-x-lg"></i>
                </button>

            </div>
        </div>
    </div>
</div>
<%-- FIN MODAL IMAGEN --%>

<%-- estilos generales --%>
<link href="${pageContext.request.contextPath}/css/root.css" rel="stylesheet">

<%-- Favicons y Manifest --%>
<link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/favicon-96x96.png" sizes="96x96" />
<link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/assets/favicon.svg" />

<link rel="shortcut icon" href="${pageContext.request.contextPath}/favicon.ico" />

<link rel="apple-touch-icon" sizes="180x180" href="${pageContext.request.contextPath}/assets/apple-touch-icon.png" />
<meta name="apple-mobile-web-app-title" content="NullChats" />

<meta name="description" content="NullChats is a secure, private, and anonymous messaging platform." />
<link rel="manifest" href="${pageContext.request.contextPath}/assets/site.webmanifest" />