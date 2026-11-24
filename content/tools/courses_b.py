"""Original EngineerHub course content, part B (AI, ML, data science, security, design)."""
from courses_a import Q, L, C

COURSES_B = [
C("ai-engineering", "AI Engineering",
  "Build with large language models: how they work, prompting and structured output, and retrieval-augmented generation.",
  "llm-engineering", "intermediate", "premium", 260, [
  ("Working with LLMs", [
    L("how-llms-work", "How large language models work", "free", 14,
      "## Predicting the next token\n\nA large language model (LLM) is a neural network trained on huge amounts of text to predict the **next token**. A *token* is a chunk of text (a word or part of one). Text goes in as tokens, passes through many layers using **attention** (which lets each token look at the others), and the model outputs a probability for every possible next token.\n\nGeneration repeats: pick a token, append it, predict again. **Temperature** controls randomness: low is focused and repeatable, high is varied.\n\n### Limits to remember\n- A fixed **context window**: only so many tokens fit.\n- Models can produce fluent but **wrong** statements (hallucination).\n- Their knowledge has a training cut-off.",
      quiz=[Q("What does an LLM fundamentally predict?", ["The next token", "The truth of a statement", "The user's intent only", "A database row"], 0, "Everything else emerges from next-token prediction at scale."),
            Q("Lowering the temperature makes output:", ["More random", "More focused and repeatable", "Longer", "Faster to train"], 1, "Low temperature concentrates probability on the likeliest tokens.")]),
    L("prompting-and-structured-output", "Prompting and structured output", "premium", 16,
      "## Clear instructions, checkable results\n\nGood prompts state the **role**, the **task**, the **context** and the **format** you want, and include a few **examples** when the pattern is subtle.\n\nFor software, ask for **structured output** (JSON matching a schema) and then **validate it** in code — never assume the model complied. Treat model output like any untrusted input.\n\n### Prompt injection\nIf your prompt includes user-supplied or web-sourced text, that text can contain instructions that try to override yours. Keep trusted instructions separate, limit what tools the model may call, and never give it secrets it does not need.",
      code=("python", "import json\n\ndef parse_ticket(model_reply: str):\n    data = json.loads(model_reply)          # may raise: handle it\n    allowed = {\"bug\", \"feature\", \"question\"}\n    if data.get(\"category\") not in allowed:\n        raise ValueError(\"unexpected category\")\n    return data", "Validate model output before trusting it"),
      quiz=[Q("Why validate JSON produced by a model?", ["It is always perfect", "It can be malformed or contain unexpected values", "JSON cannot be parsed otherwise", "To make it longer"], 1, "Models are probabilistic; code must enforce the contract."),
            Q("Prompt injection is when text from an untrusted source tries to override your instructions.", ["True", "False"], 0, "It is the LLM-era analogue of injection attacks.", "true_false")]),
    L("retrieval-augmented-generation", "Retrieval-augmented generation (RAG)", "premium", 18,
      "## Give the model the facts it needs\n\nA model does not know your private documents and may be out of date. **RAG** retrieves relevant passages at question time and places them in the prompt.\n\n1. **Chunk** documents into passages.\n2. **Embed** each chunk: turn it into a vector that captures meaning.\n3. Store vectors in a **vector index**.\n4. At query time, embed the question, find the nearest chunks, and add them to the prompt.\n5. Ask the model to answer **only from the provided context** and to cite it.\n\nQuality depends on retrieval: poor chunking or irrelevant matches give poor answers. Evaluate retrieval separately from generation.",
      quiz=[Q("What is an embedding?", ["A compressed file", "A vector that represents the meaning of text", "A database index only", "A prompt template"], 1, "Similar meanings map to nearby vectors."),
            Q("In RAG, which step most often limits answer quality?", ["Retrieving relevant context", "Choosing a font", "Counting tokens", "Using HTTPS"], 0, "If the right passage is never retrieved, the model cannot use it.")]),
  ]),
]),

C("low-level-design", "Low Level Design",
  "Write code that stays easy to change: SOLID principles, common design patterns and how to talk through a class design.",
  "low-level-design", "intermediate", "premium", 200, [
  ("Principles and patterns", [
    L("solid-principles", "The SOLID principles", "free", 16,
      "## Five habits for changeable code\n\n- **S — Single responsibility:** a class has one reason to change.\n- **O — Open/closed:** add behaviour by adding code, not by editing tested code.\n- **L — Liskov substitution:** a subtype must be usable wherever its parent is expected, without surprises.\n- **I — Interface segregation:** many small interfaces beat one fat one.\n- **D — Dependency inversion:** depend on abstractions, not concrete classes, so parts can be swapped and tested.\n\nThey are guidelines, not laws: apply them where change actually happens.",
      quiz=[Q("A class that formats reports *and* sends emails and saves to disk breaks which principle?", ["Single responsibility", "Liskov substitution", "Interface segregation", "None"], 0, "It has several reasons to change."),
            Q("Depending on an interface rather than a concrete class mainly helps:", ["Swapping implementations and testing", "Making code shorter", "Avoiding classes", "Saving memory"], 0, "That is dependency inversion.")]),
    L("strategy-and-observer", "Two patterns: Strategy and Observer", "premium", 16,
      "## Patterns as vocabulary\n\n**Strategy** lets you swap an algorithm at run time. A checkout takes a `PricingStrategy` (regular, festival sale, member discount) instead of a giant `if` chain.\n\n**Observer** lets objects subscribe to events. When an order is placed, the email sender, inventory and analytics each get notified without the order class knowing about them.\n\nBoth reduce coupling. Don't use a pattern until the problem is actually there.",
      code=("python", "class PricingStrategy:\n    def price(self, base: float) -> float: ...\n\nclass Festival(PricingStrategy):\n    def price(self, base): return base * 0.8\n\nclass Cart:\n    def __init__(self, strategy: PricingStrategy):\n        self.strategy = strategy\n    def total(self, base): return self.strategy.price(base)", "Strategy in a few lines"),
      quiz=[Q("Which pattern replaces a long if/else choosing between algorithms?", ["Strategy", "Observer", "Singleton", "Builder"], 0, "Each algorithm becomes an interchangeable object."),
            Q("An event publisher that notifies many unknown listeners is an example of:", ["Observer", "Adapter", "Factory", "Decorator"], 0, "Subscribers register; the publisher just broadcasts.")]),
  ]),
]),
]
