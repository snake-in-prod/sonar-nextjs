'use server';

import { redirect } from 'next/navigation';
import { z } from 'zod';
import { zfd } from 'zod-form-data';
import { parseWithZod } from '@conform-to/zod';
import { externalSchema } from 'some-schemas';
import { ChoiceSchema, LocalSchema, LoginSchema, PathSchema, SignupSchema } from '../../lib/validation';

const Schema = z.object({
  email: z.string().email(),
  redirectTo: z.string().optional(),
  next: z.string().trim().min(1).default('/'),
  url: z.string().url(),
  sized: z.string().refine((v) => v.length < 200),
  local: z.string().startsWith('/admin'),
  pattern: z.string().regex(/^\/[a-z]+$/),
  page: z.enum(['/a', '/b']),
  fixed: z.literal('/home'),
  either: z.union([z.literal('/a'), z.literal('/b')]),
  inside: z.string().refine((v) => v.startsWith('/') && !v.startsWith('//')),
  site: z.string().url().refine((v) => new URL(v).hostname === 'example.com'),
});

const FormSchema = zfd.formData({ next: zfd.text(z.string().optional()) });

export async function login(formData: FormData) {
  const parsed = Schema.safeParse({ email: formData.get('email'), redirectTo: formData.get('redirectTo') });
  if (!parsed.success) {
    return;
  }
  redirect(parsed.data.redirectTo ?? '/'); // Noncompliant
}

export async function loginParse(formData: FormData) {
  const data = Schema.parse({ next: formData.get('next') });
  redirect(data.next); // Noncompliant
}

export async function loginDestructured(formData: FormData): Promise<{ error?: string } | void> {
  const { success, data } = Schema.safeParse({ redirectTo: formData.get('redirectTo') });
  if (!success) {
    return { error: 'invalid' };
  }
  redirect(data.redirectTo || '/'); // Noncompliant
}

export async function loginFields(formData: FormData) {
  const p = Schema.safeParse({ redirectTo: formData.get('redirectTo') });
  const { redirectTo } = p.data;
  redirect(redirectTo); // Noncompliant
}

export async function loginEntries(formData: FormData) {
  const p = Schema.safeParse(Object.fromEntries(formData));
  redirect(p.data.url); // Noncompliant
}

export async function loginObject(values: { redirectTo?: string }) {
  const p = Schema.parse(values);
  redirect(p.redirectTo); // Noncompliant
}

export async function loginSized(formData: FormData) {
  const p = Schema.parse({ sized: formData.get('sized') });
  redirect(p.sized); // Noncompliant
}

export async function loginImported(formData: FormData) {
  const p = LoginSchema.safeParse({ callbackUrl: formData.get('callbackUrl') });
  redirect(p.data.callbackUrl); // Noncompliant
}

export async function loginExtended(formData: FormData) {
  const p = SignupSchema.parse({ next: formData.get('next') });
  redirect(p.next); // Noncompliant
}

export async function loginUnion(formData: FormData) {
  const p = ChoiceSchema.safeParse({ code: formData.get('code'), next: formData.get('next') });
  redirect(p.data.next); // Noncompliant
}

export async function loginForm(formData: FormData) {
  const data = FormSchema.parse(formData);
  redirect(data.next); // Noncompliant
}

export async function loginConform(formData: FormData) {
  const submission = parseWithZod(formData, { schema: Schema });
  if (submission.status !== 'success') {
    return;
  }
  redirect(submission.value.redirectTo); // Noncompliant
}

// compliant: the schema accepts only some paths
export async function restricted(formData: FormData) {
  const p = Schema.parse(Object.fromEntries(formData));
  redirect(p.local);
  redirect(p.pattern);
  redirect(p.page);
  redirect(p.fixed);
  redirect(p.either);
  redirect(p.inside);
  redirect(p.site);
}

// compliant: a field the schema does not declare is stripped
export async function undeclared(formData: FormData) {
  const p = Schema.parse({ other: formData.get('other') });
  redirect(p.other);
}

// compliant: imported schemas restricting the field, through a re-export
export async function importedLocal(formData: FormData) {
  const p = LocalSchema.safeParse({ redirectTo: formData.get('redirectTo') });
  redirect(p.data.redirectTo);
  const q = PathSchema.parse({ next: formData.get('next') });
  redirect(q.next);
}

// compliant: checked after parsing
export async function checkedAfter(formData: FormData) {
  const p = Schema.safeParse({ redirectTo: formData.get('redirectTo') });
  if (!p.data.redirectTo.startsWith('/') || p.data.redirectTo.startsWith('//')) {
    redirect('/');
  }
  redirect(p.data.redirectTo);
}

// compliant: the value parsed is not from the request
export async function fixedInput() {
  const p = Schema.parse({ redirectTo: '/home' });
  redirect(p.redirectTo);
}

// compliant: JSON.parse is no schema, and a schema from a package cannot be read
export async function unknownSchemas(formData: FormData) {
  const state = JSON.parse(String(formData.get('state')));
  redirect(state.next);
  const p = externalSchema.parse({ next: formData.get('next') });
  redirect(p.next);
}
