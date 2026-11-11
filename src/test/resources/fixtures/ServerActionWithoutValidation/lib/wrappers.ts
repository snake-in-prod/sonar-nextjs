import { auth } from '@/auth';

// not a "use server" module: wrappers that the actions are built with

// parses the form with the schema before the action runs
export function validatedAction(schema, action) {
  return async (prevState, formData: FormData) => {
    const result = schema.safeParse(Object.fromEntries(formData));
    if (!result.success) {
      return { error: result.error.message };
    }
    return action(result.data, formData);
  };
}

// adds the user before the caller's arguments, validates nothing
export function withUser(action) {
  return async (...args) => {
    const user = await auth();
    return action(user, ...args);
  };
}
