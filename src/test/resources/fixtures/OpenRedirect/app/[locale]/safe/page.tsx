import { redirect } from 'next/navigation';

export default async function Page({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  // compliant: the value is a path segment after a fixed slash
  redirect(`/${locale}/login`);
}
