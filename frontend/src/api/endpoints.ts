import { apiFetch } from './client';
import type {
  AuthResponse,
  ConversationSummary,
  CopilotAnswer,
  CopilotUsage,
  KeysetPage,
  Message,
  MessageSearchResult,
  UserResponse,
} from './types';

export const login = (email: string, password: string) =>
  apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

export const listConversations = () =>
  apiFetch<ConversationSummary[]>('/api/conversations');

export const getHistory = (channelId: string, cursor: number | null, limit = 30) => {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor !== null) params.set('cursor', String(cursor));
  return apiFetch<KeysetPage<Message>>(`/api/channels/${channelId}/messages?${params}`);
};

export const sendMessage = (channelId: string, content: string) =>
  apiFetch<Message>(`/api/channels/${channelId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  });

export const editMessage = (messageId: number, content: string) =>
  apiFetch<void>(`/api/messages/${messageId}`, {
    method: 'PATCH',
    body: JSON.stringify({ content }),
  });

export const deleteMessage = (messageId: number) =>
  apiFetch<void>(`/api/messages/${messageId}`, { method: 'DELETE' });

export const searchMessages = (term: string, cursor: number | null, limit = 20) => {
  const params = new URLSearchParams({ term, limit: String(limit) });
  if (cursor !== null) params.set('cursor', String(cursor));
  return apiFetch<KeysetPage<MessageSearchResult>>(`/api/messages/search?${params}`);
};

export const getMyProfile = () => apiFetch<UserResponse>('/api/users/me');

export const updateMyProfile = (data: {
  firstName?: string;
  lastName?: string;
  jobTitle?: string;
}) =>
  apiFetch<void>('/api/users/me', { method: 'PATCH', body: JSON.stringify(data) });

export const askCopilot = (question: string) =>
  apiFetch<CopilotAnswer>('/api/copilot/ask', {
    method: 'POST',
    body: JSON.stringify({ question }),
  });

export const getCopilotUsage = () => apiFetch<CopilotUsage>('/api/copilot/usage');
