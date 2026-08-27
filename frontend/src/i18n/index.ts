import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

const resources = {
  es: {
    translation: {
      app: { title: 'Mensajería Riwi', language: 'Idioma' },
      login: {
        title: 'Iniciar sesión',
        email: 'Correo electrónico',
        password: 'Contraseña',
        submit: 'Entrar',
        loading: 'Entrando…',
      },
      nav: { chat: 'Chat', copilot: 'Copiloto', profile: 'Perfil' },
      conversations: {
        title: 'Conversaciones',
        empty: 'Sin conversaciones',
        direct: 'Directo',
        unread: '{{count}} sin leer',
      },
      chat: {
        placeholder: 'Escribe un mensaje…',
        send: 'Enviar',
        edit: 'Editar',
        delete: 'Eliminar',
        save: 'Guardar',
        cancel: 'Cancelar',
        loadMore: 'Cargar mensajes anteriores',
        edited: 'editado',
        deleted: 'Mensaje eliminado',
        selectConversation: 'Selecciona una conversación',
        search: 'Buscar mensajes…',
        searchResults: 'Resultados de búsqueda',
        noResults: 'Sin resultados',
        closeSearch: 'Cerrar búsqueda',
        confirmDelete: '¿Eliminar este mensaje?',
      },
      copilot: {
        title: 'Copiloto',
        placeholder: 'Pregunta sobre tus conversaciones…',
        ask: 'Preguntar',
        asking: 'Consultando…',
        insufficientContext:
          'El copiloto no encontró contexto suficiente en tus canales para esta pregunta.',
        citations: 'Citas',
        similarity: 'similitud',
        usage: 'Uso acumulado',
        totalQueries: 'Consultas',
        totalTokens: 'Tokens',
        lastQuery: 'Última consulta',
        empty: 'Haz una pregunta sobre el contenido de tus canales.',
      },
      profile: {
        title: 'Mi perfil',
        firstName: 'Nombre',
        lastName: 'Apellido',
        jobTitle: 'Cargo',
        email: 'Correo',
        save: 'Guardar cambios',
        saved: 'Perfil actualizado',
        logout: 'Cerrar sesión',
      },
      errors: { generic: 'Error', correlation: 'Ref' },
    },
  },
  en: {
    translation: {
      app: { title: 'Riwi Messaging', language: 'Language' },
      login: {
        title: 'Sign in',
        email: 'Email',
        password: 'Password',
        submit: 'Sign in',
        loading: 'Signing in…',
      },
      nav: { chat: 'Chat', copilot: 'Copilot', profile: 'Profile' },
      conversations: {
        title: 'Conversations',
        empty: 'No conversations',
        direct: 'Direct',
        unread: '{{count}} unread',
      },
      chat: {
        placeholder: 'Type a message…',
        send: 'Send',
        edit: 'Edit',
        delete: 'Delete',
        save: 'Save',
        cancel: 'Cancel',
        loadMore: 'Load earlier messages',
        edited: 'edited',
        deleted: 'Message deleted',
        selectConversation: 'Select a conversation',
        search: 'Search messages…',
        searchResults: 'Search results',
        noResults: 'No results',
        closeSearch: 'Close search',
        confirmDelete: 'Delete this message?',
      },
      copilot: {
        title: 'Copilot',
        placeholder: 'Ask about your conversations…',
        ask: 'Ask',
        asking: 'Asking…',
        insufficientContext:
          'The copilot did not find enough context in your channels for this question.',
        citations: 'Citations',
        similarity: 'similarity',
        usage: 'Accumulated usage',
        totalQueries: 'Queries',
        totalTokens: 'Tokens',
        lastQuery: 'Last query',
        empty: 'Ask a question about the content of your channels.',
      },
      profile: {
        title: 'My profile',
        firstName: 'First name',
        lastName: 'Last name',
        jobTitle: 'Job title',
        email: 'Email',
        save: 'Save changes',
        saved: 'Profile updated',
        logout: 'Sign out',
      },
      errors: { generic: 'Error', correlation: 'Ref' },
    },
  },
};

i18n.use(initReactI18next).init({
  resources,
  lng: localStorage.getItem('riwi.lang') ?? 'es',
  fallbackLng: 'es',
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', (lng) => localStorage.setItem('riwi.lang', lng));

export default i18n;
