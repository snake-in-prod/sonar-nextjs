export default async function Dashboard() {
  const res = await fetch('https://api.example.com/stats');
  return <pre>{JSON.stringify(await res.json())}</pre>;
}
