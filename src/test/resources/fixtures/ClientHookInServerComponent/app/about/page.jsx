import * as React from "react";

export default function About() {
  const ref = React.useRef(null); // Noncompliant {{useRef}}
  return <div ref={ref} />;
}
