import { redirect } from 'next/navigation';
import { getServerSession } from 'next-auth';
import { z } from 'zod';
import { loadContext } from './session';

// not a "use server" module: wrappers that the actions are built with

// checks the session, then calls the action with the user and the caller's arguments
export function withAuth<T>(action: (user: { id: string }, ...args: any[]) => Promise<T>): (...args: any[]) => Promise<T> {
  return async (...args: any[]): Promise<T> => {
    const session = await getServerSession();
    if (!session) {
      redirect('/login');
    }
    return action(session.user, ...args);
  };
}

// checks the session through a project helper
export const authenticated = <A extends unknown[], R>(fn: (...args: A) => Promise<R>) => async (...args: A) => {
  await loadContext();
  return fn(...args);
};

// validates the input, no session
export function validated<S extends z.ZodTypeAny, T>(schema: S, action: (data: z.infer<S>) => Promise<T>) {
  return async (input: unknown) => {
    const data = schema.parse(input);
    return action(data);
  };
}

// logs only
export function withLogging(fn) {
  return async (...args) => {
    console.log('call');
    return fn(...args);
  };
}

// not a wrapper: it calls the function right away
export function runNow(fn) {
  return fn();
}
