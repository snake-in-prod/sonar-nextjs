// compliant: what export * brings is not visible here
export * from '@/lib/seo';

export const metadata = { title: 'Press' };

export default function Page() {
  return <p>Press</p>;
}
