const meta = { title: 'Guide' };

function buildMetadata() {
  return meta;
}

export { meta as metadata, buildMetadata as generateMetadata }; // Noncompliant

export default function Page() {
  return <article>Guide</article>;
}
