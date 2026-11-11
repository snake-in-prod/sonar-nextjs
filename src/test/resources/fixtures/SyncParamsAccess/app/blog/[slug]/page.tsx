export async function generateMetadata({ params }: { params: { slug: string } }) {
  return { title: `Post ${params.slug}` }; // Noncompliant {{Await "params" or unwrap it with React.use}}
}

export default function Page({ params }: { params: { slug: string } }) {
  const title = params.slug.toUpperCase(); // Noncompliant
  return <h1>{title}{params.slug}</h1>;
}
