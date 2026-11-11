import NextAuth from 'next-auth';
import { PrismaAdapter } from '@auth/prisma-adapter';
import { db } from './db';

const { auth, signIn, signOut, unstable_update } = NextAuth({ adapter: PrismaAdapter(db) });

export { auth, signIn, signOut, unstable_update };
