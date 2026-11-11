export function useRouter() {
  return { push: (to: string | null) => to };
}
