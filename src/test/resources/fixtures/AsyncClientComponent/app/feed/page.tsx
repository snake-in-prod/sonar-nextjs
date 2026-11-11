export default async function Page() {
  const posts = await fetch('https://example.com/posts').then((r) => r.json());
  return <ul>{posts.map((p: { id: string }) => <li key={p.id}>{p.id}</li>)}</ul>;
}
