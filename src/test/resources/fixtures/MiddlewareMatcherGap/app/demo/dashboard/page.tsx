// a demo with sample data, public by design
export default async function DemoDashboard() {
  const res = await fetch('https://example.com/sample.json');
  return <pre>{JSON.stringify(await res.json())}</pre>;
}
