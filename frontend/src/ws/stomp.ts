import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getAccessToken } from '../api/client';
import type { MessageEvent as ChannelEvent } from '../api/types';

// SockJS exige http(s); .env suele traer ws:// por costumbre.
const WS_URL = (import.meta.env.VITE_WS_URL ?? 'http://localhost:8080/ws')
  .replace(/^ws:/, 'http:')
  .replace(/^wss:/, 'https:');

export function createStompClient(onConnect: (client: Client) => void): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
    // El interceptor del backend exige el JWT en el frame CONNECT, no en el handshake.
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` };
    },
    reconnectDelay: 5000,
    onConnect: () => onConnect(client),
  });
  client.activate();
  return client;
}

export function subscribeToChannel(
  client: Client,
  channelId: string,
  handler: (event: ChannelEvent) => void,
) {
  return client.subscribe(`/topic/channels/${channelId}`, (frame) => {
    handler(JSON.parse(frame.body) as ChannelEvent);
  });
}
