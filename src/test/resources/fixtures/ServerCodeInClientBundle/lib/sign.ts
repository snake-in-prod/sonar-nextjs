import crypto from 'node:crypto';

export function sign(value: string) {
  return crypto.createHmac('sha256', 'key').update(value).digest('hex');
}
