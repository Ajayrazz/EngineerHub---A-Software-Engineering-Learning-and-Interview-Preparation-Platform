// Static class strings so Tailwind can see them (inline style attributes are blocked by our CSP).
const BARS = [
  ["h-[40%]", "[animation-delay:0s]"],
  ["h-[72%]", "[animation-delay:0.24s]"],
  ["h-[28%]", "[animation-delay:0.48s]"],
  ["h-[90%]", "[animation-delay:0.72s]"],
  ["h-[55%]", "[animation-delay:0.96s]"],
  ["h-[80%]", "[animation-delay:1.2s]"],
  ["h-[35%]", "[animation-delay:1.44s]"],
  ["h-[65%]", "[animation-delay:1.68s]"],
  ["h-[48%]", "[animation-delay:1.92s]"],
  ["h-[85%]", "[animation-delay:2.16s]"],
];

/** Decorative: a code window next to a looping "algorithm running" bar animation. */
export function HeroTerminal() {
  return (
    <div className="relative hidden lg:block" aria-hidden>
      <div className="conic">
        <div className="conic-in relative overflow-hidden">
          <div className="flex items-center gap-2 border-b px-4 py-3">
            <i className="h-3 w-3 rounded-full bg-[#1e3a8a]" />
            <i className="h-3 w-3 rounded-full bg-[#1d4ed8]" />
            <i className="h-3 w-3 rounded-full bg-[#38bdf8]" />
            <span className="ml-3 font-mono text-xs text-muted">two_sum.py</span>
            <span className="relative ml-auto flex h-2.5 w-2.5">
              <span className="ping absolute inline-flex h-full w-full rounded-full bg-accent-2 opacity-75" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-accent-2" />
            </span>
          </div>
          <pre className="px-5 py-4 font-mono text-[13px] leading-6">
            <span className="text-muted">def</span> <span className="text-accent-2">two_sum</span>
            (nums, target):{"\n"}
            {"    "}seen = {"{}"}
            {"\n"}
            {"    "}
            <span className="text-muted">for</span> i, x <span className="text-muted">in</span>{" "}
            enumerate(nums):{"\n"}
            {"        "}
            <span className="text-muted">if</span> target - x <span className="text-muted">in</span>{" "}
            seen:{"\n"}
            {"            "}
            <span className="text-muted">return</span> [seen[target - x], i]{"\n"}
            {"        "}seen[x] = i
          </pre>
          <div className="border-t px-5 pb-5 pt-4">
            <div className="mb-3 flex justify-between font-mono text-xs text-muted">
              <span>visualizing…</span>
              <span>O(n)</span>
            </div>
            <div className="bars flex h-24 items-end gap-2">
              {BARS.map(([h, d], i) => (
                <div key={i} className={`w-full ${h}`}>
                  <i className={d} />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
