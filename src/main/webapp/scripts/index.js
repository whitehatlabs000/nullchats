document.addEventListener("DOMContentLoaded", () => {
    const textElement = document.getElementById("typed-tagline");

    // Frases que rotarán en la pantalla de inicio
    const phrases = [
        "End-to-end privacy.",
        "No personal data, No tracking.",
        "Self-destructing data.",
        "Enter the void."
    ];

    let phraseIndex = 0;
    let charIndex = 0;
    let isDeleting = false;
    let typingSpeed = 80;

    function typeEffect() {
        const currentPhrase = phrases[phraseIndex];

        if (isDeleting) {
            // Borrando
            textElement.textContent = currentPhrase.substring(0, charIndex - 1);
            charIndex--;
            typingSpeed = 40; // Más rápido al borrar
        } else {
            // Escribiendo
            textElement.textContent = currentPhrase.substring(0, charIndex + 1);
            charIndex++;
            typingSpeed = 80; // Velocidad normal de tecleo
        }

        // Transiciones de estado
        if (!isDeleting && charIndex === currentPhrase.length) {
            // Pausa al terminar de escribir la frase
            isDeleting = true;
            typingSpeed = 2000;

            // Si es la última frase ("Enter the void."), podemos dejarla fija más tiempo
            if (phraseIndex === phrases.length - 1) {
                typingSpeed = 5000;
            }
        } else if (isDeleting && charIndex === 0) {
            // Pausa antes de empezar la siguiente frase
            isDeleting = false;
            phraseIndex = (phraseIndex + 1) % phrases.length;
            typingSpeed = 500;
        }

        setTimeout(typeEffect, typingSpeed);
    }

    // Iniciar el efecto tras un pequeño retraso para coincidir con la animación CSS
    setTimeout(typeEffect, 800);
});