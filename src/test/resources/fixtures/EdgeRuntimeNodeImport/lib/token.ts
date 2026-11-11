import { createHmac } from 'crypto';

export function verify(token: string) {
  return createHmac('sha256', process.env.SECRET ?? '').update(token).digest('hex') === token;
}
