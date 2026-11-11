'use client';

export default function Product({ params: p }) {
  return <h2>{p.id}</h2>; // Noncompliant
}
