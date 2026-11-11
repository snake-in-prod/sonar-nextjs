// compliant: the Pages Router has no metadata exports
export const metadata = { title: 'Contact' };

export function generateMetadata() {
  return metadata;
}

export default function Contact() {
  return <p>Contact</p>;
}
