"use client"

export default async function ({ items }) { // Noncompliant
  return <ul>{items.map((i) => <li key={i}>{i}</li>)}</ul>;
}
