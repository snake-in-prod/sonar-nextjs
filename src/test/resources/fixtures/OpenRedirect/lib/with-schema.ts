export function withSchema(schema, action) {
  return async (raw) => action(schema.parse(raw));
}

export function service(options, handler) {
  return async (raw) => handler({ input: options.schema.parse(raw) });
}
