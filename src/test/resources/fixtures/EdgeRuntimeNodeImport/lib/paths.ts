import { Buffer } from 'node:buffer';
import type { Stats } from 'fs';

export function isPublic(path: string) {
  return path === '/' || Buffer.from(path).length < 2;
}
