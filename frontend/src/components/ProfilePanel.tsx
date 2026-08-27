import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { getMyProfile, updateMyProfile } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useErrorToast } from './ErrorToast';
import { LanguageSwitch } from './LanguageSwitch';

export function ProfilePanel() {
  const { t } = useTranslation();
  const { logout } = useAuth();
  const { showError } = useErrorToast();
  const [email, setEmail] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    getMyProfile()
      .then((u) => {
        setEmail(u.email);
        setFirstName(u.firstName);
        setLastName(u.lastName);
        setJobTitle(u.jobTitle);
      })
      .catch(showError);
  }, [showError]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    try {
      await updateMyProfile({ firstName, lastName, jobTitle });
      setSaved(true);
      window.setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      showError(err);
    }
  }

  return (
    <div className="profile-panel">
      <h2>{t('profile.title')}</h2>
      <form onSubmit={onSave}>
        <label>
          {t('profile.email')}
          <input value={email} disabled />
        </label>
        <label>
          {t('profile.firstName')}
          <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
        </label>
        <label>
          {t('profile.lastName')}
          <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
        </label>
        <label>
          {t('profile.jobTitle')}
          <input value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} required />
        </label>
        <button type="submit">{t('profile.save')}</button>
        {saved && <span className="saved-note">{t('profile.saved')}</span>}
      </form>
      <LanguageSwitch />
      <button className="logout" onClick={logout}>
        {t('profile.logout')}
      </button>
    </div>
  );
}
