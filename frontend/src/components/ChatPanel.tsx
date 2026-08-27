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

// Estado de envío optimista (solo cliente): un mensaje 'pending' nunca llega a persistirse en
// rw_messages si falla — no es un estado de negocio, por eso vive fuera del enum de la BD
// (sent|edited|deleted, ver docs/data-model.md §1) y se renderiza aparte de `messages`.
interface PendingSend {
  tempId: string;
  content: string;
  status: 'pending' | 'failed';
}

// crypto.randomUUID() solo existe en contextos seguros (HTTPS o localhost) — se rompería si
// la app se sirve por HTTP plano desde una IP de LAN. Solo necesitamos una clave local única
// para React, no un UUID real.
function genTempId() {
  return crypto.randomUUID ? crypto.randomUUID() : `tmp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
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
  const [pendingSends, setPendingSends] = useState<PendingSend[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);
  // El efecto de reset (abajo) limpia el estado al cambiar de canal, pero no cancela un envío
  // en curso: sin esta ref, attemptSend aplicaría su resultado tardío sobre el canal nuevo
  // (closure obsoleta sobre `channelId`, que sí necesitamos comparar en el momento en que la
  // promesa resuelve, no en el momento en que se creó).
  const channelIdRef = useRef(channelId);
  useEffect(() => {
    channelIdRef.current = channelId;
  }, [channelId]);

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
    setPendingSends([]);
    if (channelId) void loadPage(channelId, null);
  }, [channelId, loadPage]);

  useEffect(() => {
    if (!channelId || !stompClient || !stompClient.connected) return;
    const sub = subscribeToChannel(stompClient, channelId, (event) => {
      // El servidor publica por WS antes de responder el POST (MessageController): el eco
      // propio puede llegar antes que la promesa de sendMessage resuelva. Si es así, la
      // burbuja "Enviando…" seguiría visible junto a la ya confirmada hasta que attemptSend
      // la limpiara — se adelanta aquí para evitar el duplicado visual.
      if (event.type === 'SENT' && event.message?.senderId === userId) {
        setPendingSends((p) => p.filter((ps) => ps.status !== 'pending'));
      }
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
  }, [messages.length, pendingSends.length]);

  // Compartida por onSend (primer intento) y retrySend (reintento tras un fallo): mantiene un
  // único punto donde se resuelve pending -> enviado/fallido.
  async function attemptSend(tempId: string, content: string) {
    const targetChannel = channelId;
    if (!targetChannel) return;
    try {
      const sent = await sendMessage(targetChannel, content);
      // Si el usuario cambió de canal mientras la petición estaba en curso, el estado ya se
      // reseteó para el canal nuevo (efecto de arriba) — aplicar el resultado ahora lo
      // filtraría en el canal equivocado.
      if (channelIdRef.current !== targetChannel) return;
      // El eco por WS puede llegar antes o después; se dedup por id en ambos caminos.
      setMessages((prev) => (prev.some((m) => m.id === sent.id) ? prev : [...prev, sent]));
      setPendingSends((prev) => prev.filter((p) => p.tempId !== tempId));
      onActivity();
    } catch (err) {
      if (channelIdRef.current !== targetChannel) return;
      setPendingSends((prev) => prev.map((p) => (p.tempId === tempId ? { ...p, status: 'failed' } : p)));
      showError(err);
    }
  }

  async function onSend(e: FormEvent) {
    e.preventDefault();
    if (!channelId || !draft.trim()) return;
    const content = draft.trim();
    const tempId = genTempId();
    setPendingSends((prev) => [...prev, { tempId, content, status: 'pending' }]);
    setDraft('');
    void attemptSend(tempId, content);
  }

  function retrySend(tempId: string) {
    const pending = pendingSends.find((p) => p.tempId === tempId);
    if (!pending) return;
    setPendingSends((prev) => prev.map((p) => (p.tempId === tempId ? { ...p, status: 'pending' } : p)));
    void attemptSend(tempId, pending.content);
  }

  function dismissFailed(tempId: string) {
    setPendingSends((prev) => prev.filter((p) => p.tempId !== tempId));
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
          {pendingSends.map((p) => (
            <div key={p.tempId} className={`message own ${p.status}`}>
              <span className="message-content">{p.content}</span>
              <span className="message-meta">
                {p.status === 'pending' ? (
                  <em>{t('chat.sending')}</em>
                ) : (
                  <>
                    <em>{t('chat.sendFailed')}</em>
                    <button className="link" onClick={() => retrySend(p.tempId)}>
                      {t('chat.retry')}
                    </button>
                    <button className="link" onClick={() => dismissFailed(p.tempId)}>
                      {t('chat.dismiss')}
                    </button>
                  </>
                )}
              </span>
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
