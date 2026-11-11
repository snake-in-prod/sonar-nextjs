export function Content({ params }: { params: { slug: string } }) {
  return <p>{params.slug}</p>;
}
