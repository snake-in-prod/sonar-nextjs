export async function getServerSideProps({ params }) {
  return { props: { id: params.id } };
}

export default function Old({ id }) {
  return <p>{id}</p>;
}
