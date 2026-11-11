import { useTranslations } from 'next-intl';

export default function Intl() {
  const t = useTranslations('home');
  return <h1>{t('title')}</h1>;
}
