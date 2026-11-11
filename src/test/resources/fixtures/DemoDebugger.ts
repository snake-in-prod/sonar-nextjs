export function run(x: number) {
  // a debugger in a comment is not code
  debugger; // Noncompliant
  if (x) { debugger; debugger; } // Noncompliant 2
  debugger; // NOSONAR
  return `debugger`; // compliant: a string
}

function later() { debugger } // Noncompliant {{Remove this debugger}}
