import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { Client } from '@stomp/stompjs';
import {
  deleteMessage,
  editMessage,
  getHistory,
  searchMessages,
  sendMessage,
} from '../api/endpoints';
import type { Message, MessageSearchResult } from '../api/types';
import { subscribeToChannel } from '../ws/stomp';
import { useAuth } from '../auth/AuthContext';
import { useErrorToast } from './ErrorToast';

interface Props {
  channelId: string | null;
  stompClient: Client | null;
  wsEpoch: number;
  onActivity: () => void;
}

// ts_headline marca coincidencias con <mark>…</mark> pero NO escapa el HTML del contenido
// del mensaje: inyectarlo con dangerouslySetInnerHTML sería XSS almacenado. Se parsean solo
// los marcadores y el resto se renderiza como texto.
function renderHighlighted(highlighted: string) {
  return highlighted.split(/<\/?mark>/).map((part, i) =>
    i % 2 === 1 ? <mark key={i}>{part}</mark> : <span key={i}>{part}</span>,
  );
}

export function ChatPanel({ channelId, stompClient, wsEpoch, onActivity }: Props) {
  const { t } = useTranslation();
  const { userId } = useAuth();
  const { showError } = useErrorToast();

  // La API devuelve el historial DESC (keyset por message_id); se guarda ASC para render.
  const [messages, setMessages] = useState<Message[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [draft, setDraft] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<MessageSearchResult[] | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const loadPage = useCallback(
    async (channel: string, cursor: number | null) => {
      try {
        const page = await getHistory(channel, cursor);
        const asc = [...page.items].reverse();
        setMessages((prev) => (cursor === null ? asc : [...asc, ...prev]));
        setNextCursor(page.nextCursor);
      } catch (err) {
        showError(err);
      }
    },
    [showError],
  );

  useEffect(() => {
    setMessages([]);
    setNextCursor(null);
    setSearchResults(null);
    setEditingId(null);
    if (channelId) void loadPage(channelId, null);
  }, [channelId, loadPage]);

  useEffect(() => {
    if (!channelId || !stompClient || !stompClient.connected) return;
    const sub = subscribeToChannel(stompClient, channelId, (event) => {
      setMessages((prev) => {
        switch (event.type) {
          case 'SENT':
            if (!event.message || prev.some((m) => m.id === event.messageId)) return prev;
            return [...prev, event.message];
          case 'EDITED':
            return prev.map((m) =>
              m.id === event.messageId && event.message
                ? { ...m, content: event.message.content, status: 'edited' }
                : m,
            );
          case 'DELETED':
            return prev.map((m) =>
              m.id === event.messageId ? { ...m, status: 'deleted' } : m,
            );
        }
      });
      onActivity();
    });
    return () => sub.unsubscribe();
  }, [channelId, stompClient, wsEpoch, onActivity]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  async function onSend(e: FormEvent) {
    e.preventDefault();
    if (!channelId || !draft.trim()) return;
    try {
      const sent = await sendMessage(channelId, draft.trim());
      // El eco por WS puede llegar antes o después; se dedup por id en ambos caminos.
      setMessages((prev) => (prev.some((m) => m.id === sent.id) ? prev : [...prev, sent]));
      setDraft('');
      onActivity();
    } catch (err) {
      showError(err);
    }
  }

  async function onSaveEdit(messageId: number) {
    try {
      await editMessage(messageId, editDraft.trim());
      setMessages((prev) =>
        prev.map((m) =>
          m.id === messageId ? { ...m, content: editDraft.trim(), status: 'edited' } : m,
        ),
      );
      setEditingId(null);
    } catch (err) {
      showError(err);
    }
  }

  async function onDelete(messageId: number) {
    if (!window.confirm(t('chat.confirmDelete'))) return;
    try {
      await deleteMessage(messageId);
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, status: 'deleted' } : m)),
      );
    } catch (err) {
      showError(err);
    }
  }

  async function onSearch(e: FormEvent) {
    e.preventDefault();
    if (!searchTerm.trim()) return;
    try {
      const page = await searchMessages(searchTerm.trim(), null);
      setSearchResults(page.items);
    } catch (err) {
      showError(err);
    }
  }

  if (!channelId) {
    return <div className="chat-panel empty-state">{t('chat.selectConversation')}</div>;
  }

  return (
    <div className="chat-panel">
      <form className="search-bar" onSubmit={onSearch}>
        <input
          type="search"
          placeholder={t('chat.search')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        {searchResults !== null && (
          <button
            type="button"
            onClick={() => {
              setSearchResults(null);
              setSearchTerm('');
            }}
          >
            {t('chat.closeSearch')}
          </button>
        )}
      </form>

      {searchResults !== null ? (
        <div className="messages search-results">
          <h3>{t('chat.searchResults')}</h3>
          {searchResults.length === 0 && <p className="muted">{t('chat.noResults')}</p>}
          {searchResults.map((r) => (
            <div key={r.id} className="message">
              <span>{renderHighlighted(r.highlightedContent)}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="messages">
          {nextCursor !== null && (
            <button className="load-more" onClick={() => void loadPage(channelId, nextCursor)}>
              {t('chat.loadMore')}
            </button>
          )}
          {messages.map((m) => (
            <div
              key={m.id}
              id={`msg-${m.id}`}
              className={m.senderId === userId ? 'message own' : 'message'}
            >
              {m.status === 'deleted' ? (
                <em className="muted">{t('chat.deleted')}</em>
              ) : editingId === m.id ? (
                <span className="edit-row">
                  <input value={editDraft} onChange={(e) => setEditDraft(e.target.value)} />
                  <button onClick={() => void onSaveEdit(m.id)}>{t('chat.save')}</button>
                  <button onClick={() => setEditingId(null)}>{t('chat.cancel')}</button>
                </span>
              ) : (
                <>
                  <span className="message-content">{m.content}</span>
                  <span className="message-meta">
                    {m.status === 'edited' && <em> ({t('chat.edited')})</em>}
                    {m.senderId === userId && (
                      <>
                        <button
                          className="link"
                          onClick={() => {
                            setEditingId(m.id);
                            setEditDraft(m.content);
                          }}
                        >
                          {t('chat.edit')}
                        </button>
                        <button className="link" onClick={() => void onDelete(m.id)}>
                          {t('chat.delete')}
                        </button>
                      </>
                    )}
                  </span>
                </>
              )}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      )}

      <form className="composer" onSubmit={onSend}>
        <input
          value={draft}
          placeholder={t('chat.placeholder')}
          onChange={(e) => setDraft(e.target.value)}
        />
        <button type="submit" disabled={!draft.trim()}>
          {t('chat.send')}
        </button>
      </form>
    </div>
  );
}
