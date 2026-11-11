import { User } from '@/lib/types';

// User is a type only: the import is erased, types.ts stays out of the client bundle
export const format = (n: number, u?: User) => `#${n}${u ? u.id : ''}`;
