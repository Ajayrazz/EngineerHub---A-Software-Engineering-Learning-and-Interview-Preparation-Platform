#!/usr/bin/env bash
# Removes content created by the browser tests from the LOCAL dev database. The tests tag everything
# with a millisecond timestamp (10+ digits) in the slug or title; real content never has one.
# Dev only: refuses to run against anything but the docker compose postgres.
set -euo pipefail
cd "$(dirname "$0")/.."
SQL=$(cat <<'SQL'
begin;
create temp table tc as select id from catalog.courses where slug ~ '[0-9]{10,}' or title ~ '[0-9]{10,}';
create temp table tl as select id from catalog.lessons where course_id in (select id from tc);
create temp table tp as select id from problems.problems where slug ~ '[0-9]{10,}' or title ~ '[0-9]{10,}';
delete from analytics.lesson_completions where course_id in (select id from tc);
delete from analytics.completed_courses where course_id in (select id from tc);
delete from learning.quiz_attempts where course_id in (select id from tc);
delete from learning.lesson_progress where course_id in (select id from tc);
delete from learning.enrollments where course_id in (select id from tc);
delete from catalog.content_sources where entity_id in (select id from tl);
delete from catalog.content_blocks where lesson_id in (select id from tl);
delete from catalog.lessons where course_id in (select id from tc);
delete from catalog.sections where course_id in (select id from tc);
delete from catalog.courses where id in (select id from tc);
delete from problems.bookmarks where problem_id in (select id from tp);
delete from problems.user_problem_status where problem_id in (select id from tp);
delete from problems.validation_runs where problem_id in (select id from tp);
delete from problems.validation_programs where problem_id in (select id from tp);
delete from problems.problems where id in (select id from tp);
delete from problems.patterns where slug ~ '[0-9]{10,}' and id not in (select pattern_id from problems.problem_patterns);
create temp table tpl as select id from billing.plans p where name ~ '[0-9]{10,}'
  and not exists (select 1 from billing.purchase_intents x where x.plan_id = p.id)
  and not exists (select 1 from billing.subscriptions x where x.plan_id = p.id)
  and not exists (select 1 from billing.access_periods x where x.plan_id = p.id);
delete from billing.plan_entitlements where plan_id in (select id from tpl);
delete from billing.plan_prices where plan_id in (select id from tpl);
delete from billing.plans where id in (select id from tpl);
-- test plans that payments/subscriptions still reference cannot be deleted (immutable documents): hide them
update billing.plans set active = false where name ~ '[0-9]{10,}' and active;
select (select count(*) from catalog.courses) as courses_left, (select count(*) from problems.problems) as problems_left;
commit;
SQL
)
docker compose exec -T postgres psql -U engineerhub_app -d engineerhub -v ON_ERROR_STOP=1 -c "$SQL"
