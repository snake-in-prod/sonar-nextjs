'use server';

// compliant: an error monitoring tunnel is public by design
export async function forwardEnvelope(body: string) {
  await fetch('https://errors.example.com/envelope', { method: 'POST', body });
}
