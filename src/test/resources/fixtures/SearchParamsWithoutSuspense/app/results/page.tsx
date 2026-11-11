import ResultsTable from '@/components/ResultsTable';

// compliant: reading searchParams makes the page dynamic
export default async function Results({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const { q } = await searchParams;
  return <ResultsTable initial={q} />;
}
