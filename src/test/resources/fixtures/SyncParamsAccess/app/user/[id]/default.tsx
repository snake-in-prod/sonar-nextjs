export default function Fallback(props) {
  const { params } = props;
  return <span>{params.id}</span>; // Noncompliant
}
