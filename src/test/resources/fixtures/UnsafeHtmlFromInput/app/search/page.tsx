import sanitizeHtml from 'sanitize-html';

export default async function SearchPage({ searchParams }: { searchParams: Promise<{ q?: string; note?: string }> }) {
  const { q, note } = await searchParams;
  const clean = sanitizeHtml(note ?? '');
  return (
    <main>
      <h1 dangerouslySetInnerHTML={{ __html: q ?? '' }} /> {/* Noncompliant {{Sanitize this HTML}} */}
      <p dangerouslySetInnerHTML={{ __html: '<b>Results</b> for ' + q }} /> {/* Noncompliant */}
      {/* compliant: sanitized first */}
      <p dangerouslySetInnerHTML={{ __html: clean }} />
      {/* compliant: a constant */}
      <p dangerouslySetInnerHTML={{ __html: '<b>Tip:</b> use quotes' }} />
    </main>
  );
}
