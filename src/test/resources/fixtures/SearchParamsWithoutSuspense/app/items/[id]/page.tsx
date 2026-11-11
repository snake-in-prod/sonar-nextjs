import Gallery from '@/components/Gallery';

// compliant: a dynamic segment without generateStaticParams is rendered on request
export default async function Item({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Gallery id={id} />;
}
