function useState<T>(v: T): [T] {
  return [v];
}

export default function Local() {
  const [v] = useState(1);
  return <b>{v}</b>;
}
