import { PrismaClient } from '@prisma/client';

export type User = { id: string };
export const db = new PrismaClient();
