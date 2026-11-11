export { generateMetadata } from '@/lib/seo'; // Noncompliant

export const metadata = { title: 'News' };

export default function Page() {
  return <p>News</p>;
}
