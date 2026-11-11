import Toc from '../../../components/Toc';

export function generateStaticParams() {
  return [{ slug: 'intro' }];
}

export default async function Doc({ params }) {
  const { slug } = await params;
  return <Toc slug={slug} />;
}
