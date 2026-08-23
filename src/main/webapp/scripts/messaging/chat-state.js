// scripts/chat-state.js

/**
 * Contiene el estado dinámico de la aplicación de mensajería.
 */
export const state = {
    currentUserId: null,
    currentPartnerId: null,
    messageOffset: 0,
    lastTimestamp: new Date().toISOString(),
    isLoading: false,
    blockStatus: 'NONE',
    pollingInterval: null


};

/**
 * Contiene las referencias a los elementos del DOM para evitar búsquedas repetidas.
 */
export const dom = {
    userSearchInput: document.getElementById('user-search-input'),
    searchResults: document.getElementById('search-results'),
    conversationsList: document.getElementById('conversations-list'),
    messageWindow: document.getElementById('message-window'),
    messageForm: document.getElementById('message-form'),
    messageInput: document.getElementById('message-input'),
    chatHeader: document.getElementById('chat-header'),

    chatPartnerDetails: document.getElementById('chat-partner-details'),
    refreshConversationsBtn: document.getElementById('refresh-conversations-btn'),
    showBlockedUsersBtn: document.getElementById('show-blocked-users-btn'),

    chatPartnerName: document.getElementById('chat-partner-name'),
    chatPartnerImg: document.getElementById('chat-partner-img'),
    messageFormContainer: document.getElementById('message-form-container'),
    welcomeMessage: document.getElementById('welcome-message'),
    blockUserBtn: document.getElementById('block-user-btn'),
    csrfTokenInput: document.getElementById('csrfToken'),
    attachFileBtn: document.getElementById('attach-file-btn'),
    fileInput: document.getElementById('file-input'),

    sidebar: document.getElementById('sidebar'),
    sidebarToggleBtn: document.getElementById('sidebar-toggle-btn'),
    sidebarOverlay: document.getElementById('sidebar-overlay')

};