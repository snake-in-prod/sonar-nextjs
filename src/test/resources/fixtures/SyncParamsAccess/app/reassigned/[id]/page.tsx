export default async function Page({ params }) {
  params = await params;
  return <i>{params.id}</i>;
}
