'use server';

import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

export async function login(formData: FormData) {
  const data = {
    email: formData.get('email') as string,
    redirectTo: (formData.get('redirectTo') as string) || '/',
  };
  redirect(data.redirectTo); // Noncompliant
}

export async function nested(formData: FormData) {
  const raw = { target: { path: formData.get('path') } };
  redirect(raw.target.path); // Noncompliant
}

export async function spread(formData: FormData) {
  const base = { next: formData.get('next') };
  const data = { ...base, mode: 'x' };
  redirect(data.next); // Noncompliant
}

export const destructured = async (formData: FormData): Promise<{ ok: boolean }> => {
  const data = { next: formData.get('next') };
  const { next } = data;
  redirect(next); // Noncompliant
};

// NODE_ENV is no secret: the comparison gates nothing
export async function withCookie(formData: FormData) {
  const data = { next: formData.get('next') as string };
  (await cookies()).set('seen', '1', { secure: process.env.NODE_ENV === 'production' });
  redirect(data.next); // Noncompliant
}

// compliant: a fixed field
export async function fixed(formData: FormData) {
  const data = { email: formData.get('email'), next: '/home' };
  redirect(data.next);
}

// compliant: checked
export async function checked(formData: FormData) {
  const data = { next: formData.get('next') as string };
  if (!data.next.startsWith('/') || data.next.startsWith('//')) {
    redirect('/');
  }
  redirect(data.next);
}

// compliant: assigned twice, not followed
export async function twice(formData: FormData) {
  let data = { next: formData.get('next') };
  data = { next: '/' };
  redirect(data.next);
}
