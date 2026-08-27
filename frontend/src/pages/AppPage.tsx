import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Client } from '@stomp/stompjs';
import { listConversations } from '../api/endpoints';
import type { ConversationSummary } from '../api/types';
import { createStompClient } from '../ws/stomp';
import { ChatPanel } from '../components/ChatPanel';
import { ConversationList } from '../components/ConversationList';
import { CopilotPanel } from '../components/CopilotPanel';
import { ProfilePanel } from '../components/ProfilePanel';
import { useErrorToast } from '../components/ErrorToast';

type MobileTab = 'chat' | 'copilot' | 'profile';

export function AppPage() {
  const { t } = useTranslation();
  const { showError } = useErrorToast();
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedChannel, setSelectedChannel] = useState<string | null>(null);
  const [tab, setTab] = useState<MobileTab>('chat');
  const [stompClient, setStompClient] = useState<Client | null>(null);
  // Se incrementa en cada CONNECT: tras una reconexión el cliente es el mismo objeto (setState
  // no re-renderiza) pero las suscripciones STOMP mueren — este contador fuerza resuscribir.
  const [wsEpoch, setWsEpoch] = useState(0);
  const clientRef = useRef<Client | null>(null);

  const refreshConversations = useCallback(() => {
    listConversations().then(setConversations).catch(showError);
  }, [showError]);

  useEffect(() => {
    refreshConversations();
  }, [refreshConversations]);

  useEffect(() => {
    // setState en onConnect: la suscripción del ChatPanel necesita el cliente ya conectado.
    const client = createStompClient((c) => {
      setStompClient(c);
      setWsEpoch((e) => e + 1);
    });
    clientRef.current = client;
    return () => {
      void clientRef.current?.deactivate();
    };
  }, []);

  const onCitationClick = useCallback((messageId: number) => {
    setTab('chat');
    document.getElementById(`msg-${messageId}`)?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="wordmark">
          riwi<span className="wordmark-dot">.</span>msg
        </span>
      </header>
      <div className="app-layout">
        <nav className="mobile-tabs">
          <button className={tab === 'chat' ? 'active' : ''} onClick={() => setTab('chat')}>
            {t('nav.chat')}
          </button>
          <button className={tab === 'copilot' ? 'active' : ''} onClick={() => setTab('copilot')}>
            {t('nav.copilot')}
          </button>
          <button className={tab === 'profile' ? 'active' : ''} onClick={() => setTab('profile')}>
            {t('nav.profile')}
          </button>
        </nav>

        <section className={`zone zone-chat ${tab === 'chat' ? 'visible' : ''}`}>
          <aside className="sidebar">
            <h2>{t('conversations.title')}</h2>
            <ConversationList
              conversations={conversations}
              selectedId={selectedChannel}
              onSelect={setSelectedChannel}
            />
          </aside>
          <ChatPanel
            channelId={selectedChannel}
            stompClient={stompClient}
            wsEpoch={wsEpoch}
            onActivity={refreshConversations}
          />
        </section>

        <section className={`zone zone-copilot ${tab === 'copilot' ? 'visible' : ''}`}>
          <CopilotPanel onCitationClick={onCitationClick} />
        </section>

        <section className={`zone zone-profile ${tab === 'profile' ? 'visible' : ''}`}>
          <ProfilePanel />
        </section>
      </div>
    </div>
  );
}
