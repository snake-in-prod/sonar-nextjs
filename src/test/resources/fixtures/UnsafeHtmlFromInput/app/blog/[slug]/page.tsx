import DOMPurify from 'isomorphic-dompurify';
import { marked } from 'marked';

const API = 'https://cms.example.com';

export default async function PostPage(props: { params: Promise<{ slug: string }> }) {
  const params = await props.params;
  const res = await fetch(`${API}/posts/${params.slug}`);
  const post = await res.json();
  const intro = await fetch(`${API}/intro`).then((r) => r.text());
  const safe = await res.json();
  safe.body = DOMPurify.sanitize(safe.body);
  const extra = await (await fetch(`${API}/extra`)).json();
  const jsonLd = { '@type': 'BlogPosting', headline: post.title };
  return (
    <article>
      <h1 dangerouslySetInnerHTML={{ __html: `<span>${params.slug}</span>` }} /> {/* Noncompliant */}
      <div dangerouslySetInnerHTML={{ __html: post.content }} /> {/* Noncompliant */}
      <div dangerouslySetInnerHTML={{ __html: intro }} /> {/* Noncompliant */}
      <aside dangerouslySetInnerHTML={{ __html: extra.html }} /> {/* Noncompliant */}
      {/* compliant: sanitized where it is rendered */}
      <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(post.content) }} />
      {/* compliant: sanitized in place before */}
      <div dangerouslySetInnerHTML={{ __html: safe.body }} />
      {/* compliant: structured data, serialized */}
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      {/* compliant: a renderer the rule does not follow */}
      <div dangerouslySetInnerHTML={{ __html: marked(post.summary) }} />
    </article>
  );
}

export function Html({ html }: { html: string }) {
  // compliant: a prop, its origin is not visible here
  return <div dangerouslySetInnerHTML={{ __html: html }} />;
}
