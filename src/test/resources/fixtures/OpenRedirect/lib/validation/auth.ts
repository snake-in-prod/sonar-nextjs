import { z } from 'zod';

const Base = z.object({ email: z.string().email() });
const LocalPath = z.string().startsWith('/');

export const LoginSchema = z.object({ email: z.string().email(), callbackUrl: z.string().optional() });
export const LocalSchema = Base.extend({ redirectTo: z.string().startsWith('/') });
export const SignupSchema = Base.extend({ next: z.string() });
export const PathSchema = z.object({ next: LocalPath.optional() });
export const ChoiceSchema = z.union([
  z.object({ code: z.string(), next: z.string().default('/') }),
  z.object({ backup: z.string(), next: z.string().default('/') }),
]);
