'use client';
import { redirect, usePathname } from 'next/navigation';

export default function CoursePage() {
  const params = usePathname();
  // compliant: a local named params holding the current path, not the route params
  redirect(`${params}/introduction`);
}

export async function Lesson(props: { params: Promise<{ to: string }> }) {
  const params = await props.params;
  redirect(params.to); // Noncompliant
}
