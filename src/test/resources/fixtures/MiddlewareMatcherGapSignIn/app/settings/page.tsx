'use client';
import { signIn } from 'next-auth/react';

export default function Settings() {
  return <button onClick={() => signIn()}>Sign in to change your settings</button>;
}
