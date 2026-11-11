export default function Search({ searchParams }) {
  const { q } = searchParams; // Noncompliant {{"searchParams"}}
  return <p>{q}</p>;
}
