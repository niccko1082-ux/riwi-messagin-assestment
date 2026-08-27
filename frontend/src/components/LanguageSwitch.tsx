import { useTranslation } from 'react-i18next';

export function LanguageSwitch() {
  const { i18n, t } = useTranslation();
  return (
    <label className="lang-switch">
      {t('app.language')}
      <select value={i18n.language} onChange={(e) => i18n.changeLanguage(e.target.value)}>
        <option value="es">Español</option>
        <option value="en">English</option>
      </select>
    </label>
  );
}
