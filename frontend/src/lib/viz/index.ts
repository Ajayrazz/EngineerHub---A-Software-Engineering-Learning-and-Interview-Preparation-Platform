import catalogJson from "./catalog.json";
import {
  bfs,
  binarySearch,
  bubbleSort,
  fibonacciDp,
  insertionSort,
  mergeSort,
  selectionSort,
  slidingWindow,
  twoPointers,
  validParentheses,
} from "./algorithms";
import { VizInputError, type CatalogEntry, type Trace } from "./types";

export * from "./types";
export const CATALOG: readonly CatalogEntry[] = catalogJson as CatalogEntry[];

export function entry(id: string | undefined): CatalogEntry | undefined {
  return CATALOG.find((c) => c.id === id);
}

/** Shape check against the catalog's patterns: exactly what the server's BlockValidator accepts. */
export function shapeError(id: string, input: string, param: string): string | null {
  const e = entry(id);
  if (!e) return "Choose an algorithm.";
  if (!new RegExp(e.inputPattern).test(input.trim())) return `Check the input: ${e.inputLabel}.`;
  if (e.paramPattern) {
    if (!new RegExp(e.paramPattern).test(param.trim())) return `Check the value: ${e.paramLabel}.`;
  } else if (param.trim() !== "") return "This algorithm takes no extra value.";
  return null;
}

/** Builds the trace, or throws VizInputError with a message fit for the learner. */
export function generate(id: string, input: string, param: string): Trace {
  const bad = shapeError(id, input, param);
  if (bad) throw new VizInputError(bad);
  switch (id) {
    case "bubble-sort":
      return bubbleSort(input);
    case "selection-sort":
      return selectionSort(input);
    case "insertion-sort":
      return insertionSort(input);
    case "merge-sort":
      return mergeSort(input);
    case "binary-search":
      return binarySearch(input, param);
    case "two-pointers":
      return twoPointers(input, param);
    case "sliding-window":
      return slidingWindow(input, param);
    case "valid-parentheses":
      return validParentheses(input);
    case "bfs":
      return bfs(input, param);
    case "fibonacci-dp":
      return fibonacciDp(input);
    default:
      throw new VizInputError("Choose an algorithm.");
  }
}

/** null = fine; otherwise the message to show next to the inputs. */
export function inputProblem(id: string, input: string, param: string): string | null {
  try {
    generate(id, input, param);
    return null;
  } catch (e) {
    if (e instanceof VizInputError) return e.message;
    throw e;
  }
}
