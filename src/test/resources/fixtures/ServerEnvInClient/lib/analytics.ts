export function track() {
  return fetch('/t?id=' + process.env.ANALYTICS_ID); // Noncompliant {{ANALYTICS_ID}}
}
