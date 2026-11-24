-- Topics beyond data structures and algorithms, so courses can be filed under software development,
-- AI/ML, security, data science and more. Additive and idempotent (slug is unique).
INSERT INTO catalog.topics (id, slug, name, position)
SELECT uuidv7(), t.slug, t.name, t.ord FROM (VALUES
  (40,'system-design','System Design'),
  (41,'low-level-design','Low-Level Design'),
  (42,'databases','Databases and SQL'),
  (43,'python','Python'),
  (44,'java','Java'),
  (45,'git','Git and Version Control'),
  (46,'web-apis','Web and API Development'),
  (47,'software-engineering','Software Engineering Practice'),
  (50,'machine-learning','Machine Learning'),
  (51,'deep-learning','Deep Learning'),
  (52,'llm-engineering','AI and LLM Engineering'),
  (53,'data-science','Data Science'),
  (54,'data-engineering','Data Engineering'),
  (60,'cybersecurity','Cybersecurity'),
  (61,'application-security','Application Security'),
  (62,'networking','Computer Networks'),
  (63,'operating-systems','Operating Systems'),
  (64,'cloud-devops','Cloud and DevOps')
) AS t(ord, slug, name)
ON CONFLICT (slug) DO NOTHING;
