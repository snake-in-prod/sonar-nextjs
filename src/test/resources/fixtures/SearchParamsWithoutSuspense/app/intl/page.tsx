import { getTranslations } from 'next-intl/server';
import IntlFilter from '@/components/IntlFilter';

// dynamic: without setRequestLocale next-intl reads the locale from the request headers
export default async function IntlPage() {
  const t = await getTranslations('Filters');
  return (
    <main>
      <h1>{t('title')}</h1>
      <IntlFilter />
    </main>
  );
}
