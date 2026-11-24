-- Topics for the course catalogue categories (concurrency, careers, backend messaging, more languages, AI design).
-- Additive and idempotent (slug is unique).
INSERT INTO catalog.topics (id, slug, name, position)
SELECT uuidv7(), t.slug, t.name, t.ord FROM (VALUES
  (65,'concurrency','Concurrency'),
  (66,'behavioral-interviews','Behavioral Interviews'),
  (67,'job-search','Job Search'),
  (68,'microservices','Microservices'),
  (69,'kafka','Apache Kafka'),
  (70,'cpp','C++'),
  (71,'csharp','C#'),
  (72,'go','Go'),
  (73,'agentic-coding','Agentic Coding'),
  (74,'ai-system-design','AI System Design')
) AS t(ord, slug, name)
ON CONFLICT (slug) DO NOTHING;
