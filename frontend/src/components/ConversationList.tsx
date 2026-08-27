import { useTranslation } from 'react-i18next';
import type { ConversationSummary } from '../api/types';

interface Props {
  conversations: ConversationSummary[];
  selectedId: string | null;
  onSelect: (channelId: string) => void;
}

export function ConversationList({ conversations, selectedId, onSelect }: Props) {
  const { t } = useTranslation();

  if (conversations.length === 0) {
    return <p className="muted">{t('conversations.empty')}</p>;
  }

  return (
    <ul className="conversation-list">
      {conversations.map((c) => (
        <li key={c.channelId}>
          <button
            className={c.channelId === selectedId ? 'conversation selected' : 'conversation'}
            onClick={() => onSelect(c.channelId)}
          >
            <span className="conversation-name">
              <span className="channel-glyph" aria-hidden="true">
                {c.channelType === 'group' ? '#' : '@'}
              </span>
              {c.name ?? t('conversations.direct')}
            </span>
            {c.lastMessageContent && (
              <span className="conversation-preview">{c.lastMessageContent}</span>
            )}
            {c.unreadCount > 0 && (
              <span className="badge">{t('conversations.unread', { count: c.unreadCount })}</span>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
}
