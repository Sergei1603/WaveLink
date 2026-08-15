import { useEffect, useState } from "react";

/**
 * Trails `value` by `ms`. Pass `ms = 0` to let a value through immediately — that is how
 * both track lists reload instantly when the search box is cleared.
 */
export function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);

  return debounced;
}
