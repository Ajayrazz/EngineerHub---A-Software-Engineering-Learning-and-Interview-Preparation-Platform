import { Briefcase, MessagesSquare } from "lucide-react";

/* Original course illustrations: inline SVG in the site palette, animated with SMIL/CSS (no scripts,
   CSP-safe). Each is decorative (aria-hidden); the course title carries the meaning. */

const B = "#3b82f6",
  S = "#38bdf8",
  L = "#bae6fd",
  D = "#0d1630",
  T = "#8fa0c4";

function Frame({ children }: { children: React.ReactNode }) {
  return (
    <svg viewBox="0 0 240 130" className="h-full w-full" aria-hidden role="presentation">
      <defs>
        <linearGradient id="g-art" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={B} stopOpacity=".9" />
          <stop offset="1" stopColor={S} stopOpacity=".9" />
        </linearGradient>
      </defs>
      {children}
    </svg>
  );
}

const Box = ({
  x,
  y,
  w = 44,
  h = 26,
  label,
  hot = false,
}: {
  x: number;
  y: number;
  w?: number;
  h?: number;
  label: string;
  hot?: boolean;
}) => (
  <g>
    <rect
      x={x}
      y={y}
      width={w}
      height={h}
      rx="6"
      fill={hot ? "url(#g-art)" : D}
      stroke={hot ? L : B}
      strokeWidth="1.2"
    />
    <text
      x={x + w / 2}
      y={y + h / 2 + 3.5}
      textAnchor="middle"
      fontSize="9"
      fontFamily="monospace"
      fill={hot ? "#fff" : L}
    >
      {label}
    </text>
  </g>
);

/** Boxes in a row with packets travelling along the connectors. */
export function FlowArt({ labels }: { labels: string[] }) {
  const n = labels.length,
    gap = (240 - 20 - n * 46) / (n - 1);
  return (
    <Frame>
      {labels.slice(0, -1).map((_, i) => {
        const x1 = 10 + i * (46 + gap) + 46,
          x2 = x1 + gap;
        return (
          <g key={i}>
            <line
              x1={x1}
              y1="65"
              x2={x2}
              y2="65"
              stroke={S}
              strokeWidth="1.5"
              className="flow-line"
            />
            <circle r="3" fill={L}>
              <animateMotion
                dur="2.2s"
                begin={`${i * 0.5}s`}
                repeatCount="indefinite"
                path={`M${x1} 65 L${x2} 65`}
              />
            </circle>
          </g>
        );
      })}
      {labels.map((l, i) => (
        <Box key={l} x={10 + i * (46 + gap)} y={52} w={46} label={l} hot={i === 1} />
      ))}
      <rect x="10" y="100" width="220" height="3" rx="1.5" fill={B} opacity=".25" />
    </Frame>
  );
}

export function ArrayArt() {
  const v = [3, 8, 1, 9, 4, 7];
  return (
    <Frame>
      {v.map((x, i) => (
        <g key={i}>
          <rect x={16 + i * 35} y="48" width="30" height="34" rx="5" fill={D} stroke={B} />
          <text
            x={31 + i * 35}
            y="70"
            textAnchor="middle"
            fontSize="13"
            fontFamily="monospace"
            fill={L}
          >
            {x}
          </text>
        </g>
      ))}
      <rect x="14" y="44" width="72" height="42" rx="7" fill="none" stroke={S} strokeWidth="2">
        <animate attributeName="x" values="14;104;14" dur="5s" repeatCount="indefinite" />
      </rect>
      <path d="M31 96 l-5 8 h10z" fill={S}>
        <animateTransform
          attributeName="transform"
          type="translate"
          values="0 0;175 0;0 0"
          dur="5s"
          repeatCount="indefinite"
        />
      </path>
      <text x="31" y="116" fontSize="8" fill={T} fontFamily="monospace">
        left
      </text>
    </Frame>
  );
}

/** Isometric stack of layers (protocol layers, OS layers). */
export function LayersArt({ labels }: { labels: string[] }) {
  return (
    <Frame>
      {labels.map((l, i) => {
        const y = 20 + i * 22;
        return (
          <g key={l}>
            <g transform={`translate(120 ${y}) scale(1 .5) rotate(45)`}>
              <rect
                x="-46"
                y="-46"
                width="92"
                height="92"
                rx="8"
                fill={i === 1 ? "url(#g-art)" : D}
                stroke={i === 1 ? L : B}
                strokeWidth="1.5"
                opacity=".95"
              >
                <animate
                  attributeName="opacity"
                  values=".7;1;.7"
                  dur={`${3 + i * 0.6}s`}
                  repeatCount="indefinite"
                />
              </rect>
            </g>
            <text
              x="120"
              y={y + 4}
              textAnchor="middle"
              fontSize="9.5"
              fontFamily="monospace"
              fill="#fff"
            >
              {l}
            </text>
          </g>
        );
      })}
    </Frame>
  );
}

export function CodeArt({ label }: { label: string }) {
  const lines = [
    [28, 90],
    [44, 120],
    [44, 70],
    [60, 100],
    [28, 40],
  ];
  return (
    <Frame>
      <rect x="26" y="14" width="188" height="102" rx="10" fill={D} stroke={B} />
      <circle cx="40" cy="26" r="3" fill="#1e3a8a" />
      <circle cx="51" cy="26" r="3" fill={B} />
      <circle cx="62" cy="26" r="3" fill={S} />
      {lines.map(([x, w], i) => (
        <rect
          key={i}
          x={x + 12}
          y={42 + i * 14}
          width={w}
          height="6"
          rx="3"
          fill={i % 2 ? S : B}
          opacity=".8"
        >
          <animate
            attributeName="width"
            values={`${w};${w + 24};${w}`}
            dur={`${2.5 + i * 0.4}s`}
            repeatCount="indefinite"
          />
        </rect>
      ))}
      <text
        x="204"
        y="108"
        textAnchor="end"
        fontSize="20"
        fontWeight="700"
        fontFamily="monospace"
        fill="url(#g-art)"
      >
        {label}
      </text>
    </Frame>
  );
}

export function GitArt() {
  const pts: [number, number][] = [
    [30, 80],
    [80, 80],
    [130, 80],
    [190, 80],
    [80, 40],
    [130, 40],
  ];
  return (
    <Frame>
      <path d="M30 80 H190" stroke={B} strokeWidth="3" />
      <path
        d="M80 80 Q80 40 110 40 H130 Q160 40 190 80"
        fill="none"
        stroke={S}
        strokeWidth="3"
        className="flow-line"
      />
      {pts.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="8" fill={D} stroke={i > 3 ? S : B} strokeWidth="3">
          <animate
            attributeName="r"
            values="8;10;8"
            dur="2.4s"
            begin={`${i * 0.3}s`}
            repeatCount="indefinite"
          />
        </circle>
      ))}
      <text x="30" y="108" fontSize="9" fontFamily="monospace" fill={T}>
        main
      </text>
      <text x="108" y="26" fontSize="9" fontFamily="monospace" fill={S}>
        feature
      </text>
    </Frame>
  );
}

export function DbArt() {
  return (
    <Frame>
      {[0, 1, 2].map((i) => (
        <ellipse
          key={i}
          cx="70"
          cy={40 + i * 18}
          rx="34"
          ry="10"
          fill={D}
          stroke={B}
          strokeWidth="1.5"
        />
      ))}
      <path d="M36 40 V76 M104 40 V76" stroke={B} strokeWidth="1.5" />
      <ellipse cx="70" cy="40" rx="34" ry="10" fill="url(#g-art)" opacity=".85" />
      <rect x="120" y="36" width="104" height="52" rx="8" fill={D} stroke={S} />
      <text x="130" y="55" fontSize="9" fontFamily="monospace" fill={L}>
        SELECT name
      </text>
      <text x="130" y="68" fontSize="9" fontFamily="monospace" fill={L}>
        FROM users
      </text>
      <text x="130" y="81" fontSize="9" fontFamily="monospace" fill={S}>
        WHERE id = 7;
      </text>
      <circle r="3.5" fill={L}>
        <animateMotion dur="2s" repeatCount="indefinite" path="M104 62 L120 62" />
      </circle>
    </Frame>
  );
}

export function ThreadsArt() {
  return (
    <Frame>
      {[0, 1, 2].map((i) => (
        <g key={i}>
          <text x="10" y={36 + i * 30} fontSize="8.5" fontFamily="monospace" fill={T}>
            T{i + 1}
          </text>
          <line x1="28" y1={32 + i * 30} x2="228" y2={32 + i * 30} stroke={B} opacity=".35" />
          {[0, 1, 2].map((j) => (
            <rect
              key={j}
              x={34 + ((i + j * 2) % 5) * 38}
              y={24 + i * 30}
              width="30"
              height="16"
              rx="4"
              fill={(i + j) % 2 ? "url(#g-art)" : D}
              stroke={S}
            >
              <animate
                attributeName="opacity"
                values=".4;1;.4"
                dur="3s"
                begin={`${(i + j) * 0.35}s`}
                repeatCount="indefinite"
              />
            </rect>
          ))}
        </g>
      ))}
      <rect
        x="108"
        y="14"
        width="30"
        height="86"
        rx="4"
        fill="none"
        stroke={L}
        strokeDasharray="4 3"
      />
      <text x="123" y="112" textAnchor="middle" fontSize="8" fontFamily="monospace" fill={L}>
        lock
      </text>
    </Frame>
  );
}

export function KafkaArt() {
  return (
    <Frame>
      {[0, 1, 2].map((p) => (
        <g key={p}>
          <text x="12" y={38 + p * 26} fontSize="8.5" fontFamily="monospace" fill={T}>
            P{p}
          </text>
          {[0, 1, 2, 3, 4].map((k) => (
            <rect
              key={k}
              x={30 + k * 28}
              y={28 + p * 26}
              width="22"
              height="16"
              rx="3"
              fill={D}
              stroke={k === 4 ? S : B}
            >
              {k === 4 && (
                <animate
                  attributeName="fill"
                  values={`${D};${B};${D}`}
                  dur="2s"
                  begin={`${p * 0.6}s`}
                  repeatCount="indefinite"
                />
              )}
            </rect>
          ))}
          <circle r="3" fill={L}>
            <animateMotion
              dur="2.4s"
              begin={`${p * 0.5}s`}
              repeatCount="indefinite"
              path={`M18 ${36 + p * 26} L${34 + 4 * 28} ${36 + p * 26}`}
            />
          </circle>
        </g>
      ))}
      <rect
        x="178"
        y="22"
        width="48"
        height="84"
        rx="8"
        fill="none"
        stroke={S}
        strokeDasharray="3 3"
      />
      <text x="202" y="116" textAnchor="middle" fontSize="8" fontFamily="monospace" fill={S}>
        consumers
      </text>
    </Frame>
  );
}

const Cls = ({ x, y, t }: { x: number; y: number; t: string }) => (
  <g>
    <rect x={x} y={y} width="64" height="40" rx="5" fill={D} stroke={B} />
    <line x1={x} y1={y + 14} x2={x + 64} y2={y + 14} stroke={B} />
    <text
      x={x + 32}
      y={y + 10.5}
      textAnchor="middle"
      fontSize="8.5"
      fontFamily="monospace"
      fill={L}
    >
      {t}
    </text>
    <rect x={x + 6} y={y + 21} width="36" height="3" rx="1.5" fill={S} opacity=".7" />
    <rect x={x + 6} y={y + 29} width="48" height="3" rx="1.5" fill={B} opacity=".7" />
  </g>
);

export function ClassArt() {
  return (
    <Frame>
      <path
        d="M120 54 V70 M66 70 H174 M66 70 V80 M174 70 V80"
        stroke={S}
        fill="none"
        strokeWidth="1.5"
        className="flow-line"
      />
      <Cls x={88} y={14} t="Shape" />
      <Cls x={34} y={80} t="Circle" />
      <Cls x={142} y={80} t="Rect" />
    </Frame>
  );
}

export function IconArt({ kind }: { kind: "chat" | "job" }) {
  return (
    <div className="relative grid h-full w-full place-items-center" aria-hidden>
      <svg viewBox="0 0 240 130" className="absolute inset-0 h-full w-full">
        <ellipse
          cx="120"
          cy="65"
          rx="70"
          ry="24"
          fill="none"
          stroke={B}
          strokeDasharray="4 4"
          className="flow-line"
        />
        <circle r="4" fill={S}>
          <animateMotion
            dur="6s"
            repeatCount="indefinite"
            path="M50 65 a70 24 0 1 0 140 0 a70 24 0 1 0 -140 0"
          />
        </circle>
      </svg>
      <span className="relative grid h-16 w-16 place-items-center rounded-2xl border bg-surface text-accent-2 shadow-[0_0_40px_-8px_#3b82f6]">
        {kind === "chat" ? <MessagesSquare size={32} /> : <Briefcase size={32} />}
      </span>
    </div>
  );
}

/** Picks an illustration for a course slug; falls back to a generic flow diagram. */
export function CourseArt({ slug }: { slug: string }) {
  switch (slug) {
    case "data-structures-and-algorithms":
      return <ArrayArt />;
    case "system-design-fundamentals":
      return <FlowArt labels={["Client", "LB", "App", "DB"]} />;
    case "system-design-interviews":
      return <FlowArt labels={["Users", "Cache", "Shards", "Queue"]} />;
    case "low-level-design":
      return <ClassArt />;
    case "concurrency-interview":
      return <ThreadsArt />;
    case "sql-interview":
      return <DbArt />;
    case "behavioral-interviews":
      return <IconArt kind="chat" />;
    case "job-search":
      return <IconArt kind="job" />;
    case "ai-engineering":
      return <FlowArt labels={["Docs", "Embed", "LLM", "Answer"]} />;
    case "genai-system-design":
      return <FlowArt labels={["User", "Guard", "LLM", "Tools"]} />;
    case "ml-system-design":
      return <FlowArt labels={["Data", "Train", "Model", "Serve"]} />;
    case "agentic-coding":
      return <FlowArt labels={["Dev", "Agent", "Code", "Tests"]} />;
    case "operating-systems":
      return <LayersArt labels={["Apps", "Kernel", "Drivers", "Hardware"]} />;
    case "computer-networks":
      return <LayersArt labels={["HTTP", "TCP", "IP", "Link"]} />;
    case "microservices":
      return <FlowArt labels={["Client", "Gateway", "Orders", "Pay"]} />;
    case "api-design":
      return <FlowArt labels={["GET", "API", "JSON", "200"]} />;
    case "apache-kafka":
      return <KafkaArt />;
    case "java":
      return <CodeArt label="Java" />;
    case "python":
      return <CodeArt label="Py" />;
    case "cpp":
      return <CodeArt label="C++" />;
    case "csharp":
      return <CodeArt label="C#" />;
    case "go":
      return <CodeArt label="Go" />;
    case "git":
      return <GitArt />;
    default:
      return <FlowArt labels={["In", "Work", "Out"]} />;
  }
}
