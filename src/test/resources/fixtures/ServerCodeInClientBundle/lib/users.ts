import { db } from './db';

export type Row = { id: string };

export function getUser() {
  return db.query('select 1');
}
