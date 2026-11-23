package com.engineerhub.catalog.internal;

import com.engineerhub.catalog.CatalogApi;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogApiImpl implements CatalogApi {

  private static final String LESSON_SELECT =
      "select l.id, c.id, c.slug, l.slug, l.title, l.access_tier, l.free_preview"
          + " from catalog.lessons l join catalog.courses c on c.id = l.course_id join catalog.sections s on s.id = l.section_id"
          + " where c.status = 'published' and l.status = 'published'";

  private final JdbcClient jdbc;

  CatalogApiImpl(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static PublishedLesson lesson(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PublishedLesson(
        rs.getObject(1, UUID.class),
        rs.getObject(2, UUID.class),
        rs.getString(3),
        rs.getString(4),
        rs.getString(5),
        rs.getString(6),
        rs.getBoolean(7));
  }

  @Override
  public Map<UUID, Topic> topics(Collection<UUID> topicIds) {
    Map<UUID, Topic> out = new LinkedHashMap<>();
    if (topicIds.isEmpty()) {
      return out;
    }
    jdbc.sql("select id, slug, name from catalog.topics where id in (:ids)")
        .param("ids", List.copyOf(topicIds))
        .query((rs, i) -> new Topic(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .list()
        .forEach(t -> out.put(t.id(), t));
    return out;
  }

  @Override
  public Optional<PublishedCourse> publishedCourse(UUID courseId) {
    return jdbc.sql(
            "select id, slug, title from catalog.courses where id = :id and status = 'published'")
        .param("id", courseId)
        .query(
            (rs, i) ->
                new PublishedCourse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .optional();
  }

  @Override
  public Optional<PublishedLesson> publishedLesson(UUID lessonId) {
    return jdbc.sql(LESSON_SELECT + " and l.id = :id")
        .param("id", lessonId)
        .query((rs, i) -> lesson(rs))
        .optional();
  }

  @Override
  public List<PublishedLesson> publishedLessons(UUID courseId) {
    return jdbc.sql(LESSON_SELECT + " and c.id = :c order by s.position, l.position")
        .param("c", courseId)
        .query((rs, i) -> lesson(rs))
        .list();
  }

  @Override
  public Map<UUID, PublishedCourse> publishedCourses(Collection<UUID> courseIds) {
    Map<UUID, PublishedCourse> out = new LinkedHashMap<>();
    if (courseIds.isEmpty()) {
      return out;
    }
    jdbc.sql(
            "select id, slug, title from catalog.courses where status = 'published' and id in (:ids)")
        .param("ids", List.copyOf(courseIds))
        .query(
            (rs, i) ->
                new PublishedCourse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
        .list()
        .forEach(c -> out.put(c.id(), c));
    return out;
  }
}
