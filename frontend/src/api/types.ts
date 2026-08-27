export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface UserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string;
  active: boolean;
  createdAt: string;
}

export interface ConversationSummary {
  channelId: string;
  name: string | null;
  channelType: 'direct' | 'group';
  lastMessageId: number | null;
  lastMessageContent: string | null;
  lastMessageSenderId: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface Message {
  id: number;
  channelId: string;
  senderId: string;
  content: string;
  status: 'sent' | 'edited' | 'deleted';
  editedAt: string | null;
  createdAt: string | null;
}

export interface KeysetPage<T> {
  items: T[];
  nextCursor: number | null;
}

export interface MessageSearchResult {
  id: number;
  channelId: string;
  highlightedContent: string;
  rank: number;
}

export interface Citation {
  messageId: number;
  similarityScore: number;
}

export interface CopilotAnswer {
  answer: string;
  hadSufficientContext: boolean;
  systemPromptVersion: string;
  tokensUsed: number | null;
  citations: Citation[];
}

export interface CopilotUsage {
  totalQueries: number;
  totalTokensUsed: number;
  lastQueryAt: string | null;
}

export interface MessageEvent {
  type: 'SENT' | 'EDITED' | 'DELETED';
  messageId: number;
  message: Message | null;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  correlationId: string | null;
  path: string;
}
