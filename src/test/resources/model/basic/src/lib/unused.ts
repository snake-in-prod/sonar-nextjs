export function unused() {}

// the app checks a session somewhere: its unguarded actions count as unprotected
export async function viewer(getSession: () => Promise<unknown>) {
  return getSession();
}
