'use server';

import { redirect } from 'next/navigation';

const ALLOWED = ['/dashboard', '/settings'];

export async function login(formData: FormData) {
  // ...sign in
  redirect(formData.get('redirectTo') as string); // Noncompliant
}

export async function signup(data: FormData) {
  const target = String(data.get('target'));
  redirect(`${target}?welcome=1`); // Noncompliant
}

export async function logout(formData: FormData) {
  const target = formData.get('redirectTo') as string;
  // compliant: allow-list
  if (ALLOWED.includes(target)) {
    redirect(target);
  }
  redirect('/');
}

export async function onboard(formData: FormData) {
  let target = formData.get('next') as string;
  target = '/onboarding';
  // compliant: reassigned to a fixed path
  redirect(target);
}
