import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { askCopilot, getCopilotUsage } from '../api/endpoints';
import type { CopilotAnswer, CopilotUsage } from '../api/types';
import { useErrorToast } from './ErrorToast';

export function CopilotPanel({ onCitationClick }: { onCitationClick: (messageId: number) => void }) {
  const { t, i18n } = useTranslation();
  const { showError } = useErrorToast();
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<CopilotAnswer | null>(null);
  const [usage, setUsage] = useState<CopilotUsage | null>(null);
  const [loading, setLoading] = useState(false);

  const refreshUsage = () => getCopilotUsage().then(setUsage).catch(showError);

  useEffect(() => {
    void refreshUsage();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onAsk(e: FormEvent) {
    e.preventDefault();
    if (!question.trim()) return;
    setLoading(true);
    try {
      setAnswer(await askCopilot(question.trim()));
      void refreshUsage();
    } catch (err) {
      showError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="copilot-panel">
      <h2>{t('copilot.title')}</h2>

      <form onSubmit={onAsk} className="copilot-form">
        <textarea
          value={question}
          placeholder={t('copilot.placeholder')}
          onChange={(e) => setQuestion(e.target.value)}
          rows={3}
        />
        <button type="submit" disabled={loading || !question.trim()}>
          {loading ? t('copilot.asking') : t('copilot.ask')}
        </button>
      </form>

      {answer ? (
        <div className="copilot-answer">
          {!answer.hadSufficientContext && (
            <p className="warning">{t('copilot.insufficientContext')}</p>
          )}
          <p>{answer.answer}</p>
          {answer.citations.length > 0 && (
            <>
              <h3>{t('copilot.citations')}</h3>
              <ul className="citations">
                {answer.citations.map((c) => (
                  <li key={c.messageId}>
                    <button className="link" onClick={() => onCitationClick(c.messageId)}>
                      [msg {c.messageId}]
                    </button>{' '}
                    <span className="muted">
                      {(c.similarityScore * 100).toFixed(0)}% {t('copilot.similarity')}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      ) : (
        <p className="muted">{t('copilot.empty')}</p>
      )}

      {usage && (
        <div className="copilot-usage">
          <h3>{t('copilot.usage')}</h3>
          <dl>
            <dt>{t('copilot.totalQueries')}</dt>
            <dd>{usage.totalQueries}</dd>
            <dt>{t('copilot.totalTokens')}</dt>
            <dd>{usage.totalTokensUsed}</dd>
            {usage.lastQueryAt && (
              <>
                <dt>{t('copilot.lastQuery')}</dt>
                <dd>{new Date(usage.lastQueryAt).toLocaleString(i18n.language)}</dd>
              </>
            )}
          </dl>
        </div>
      )}
    </div>
  );
}
