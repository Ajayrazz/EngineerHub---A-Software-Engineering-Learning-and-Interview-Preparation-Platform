package com.engineerhub.analytics.internal;

import com.engineerhub.identity.UserDirectory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own activity dashboard. No endpoint takes a user id: ownership is the token. */
@RestController
@RequestMapping("/api/v1/me")
class DashboardController {

  record StreakView(int current, int longest, LocalDate lastActiveDay, boolean activeToday) {}

  record DayView(LocalDate day, int lessonsCompleted) {}

  record DashboardResponse(
      StreakView streak,
      String timezone,
      LocalDate today,
      long lessonsCompleted,
      long coursesCompleted,
      List<DayView> activity) {}

  private static final int WINDOW_DAYS = 120;

  private final AnalyticsService service;
  private final UserDirectory directory;

  DashboardController(AnalyticsService service, UserDirectory directory) {
    this.service = service;
    this.directory = directory;
  }

  @GetMapping("/dashboard")
  @PreAuthorize("isAuthenticated()")
  ResponseEntity<DashboardResponse> dashboard(Authentication authentication) {
    var d = service.dashboard(directory.current(authentication).id(), WINDOW_DAYS);
    var body =
        new DashboardResponse(
            new StreakView(
                d.currentStreak(), d.longestStreak(), d.lastActiveDay(), d.activeToday()),
            d.timezone(),
            d.today(),
            d.lessonsCompleted(),
            d.coursesCompleted(),
            d.activity().stream().map(x -> new DayView(x.day(), x.lessonsCompleted())).toList());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
