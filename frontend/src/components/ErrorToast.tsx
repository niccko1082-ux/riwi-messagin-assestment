import { createContext, useCallback, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiRequestError } from '../api/client';

interface ToastState {
  message: string;
  correlationId: string | null;
}

const ToastContext = createContext<{ showError: (err: unknown) => void }>({
  showError: () => {},
});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const { t } = useTranslation();

  const showError = useCallback((err: unknown) => {
    if (err instanceof ApiRequestError) {
      setToast({ message: err.error.message, correlationId: err.error.correlationId });
    } else {
      setToast({ message: err instanceof Error ? err.message : String(err), correlationId: null });
    }
    window.setTimeout(() => setToast(null), 6000);
  }, []);

  return (
    <ToastContext.Provider value={{ showError }}>
      {children}
      {toast && (
        <div className="toast" role="alert" onClick={() => setToast(null)}>
          <strong>{t('errors.generic')}:</strong> {toast.message}
          {toast.correlationId && (
            <span className="toast-ref">
              {t('errors.correlation')}: {toast.correlationId}
            </span>
          )}
        </div>
      )}
    </ToastContext.Provider>
  );
}

export function useErrorToast() {
  return useContext(ToastContext);
}
