package com.engineerhub.problems.internal;

import com.engineerhub.problems.ValidationExecutor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Test double for the judge: unavailable unless a test switches it on; records the jobs it
 * receives.
 */
@Component
class FakeValidationExecutor implements ValidationExecutor {

  volatile boolean available = false;
  final List<Job> jobs = new CopyOnWriteArrayList<>();

  void reset() {
    available = false;
    jobs.clear();
  }

  @Override
  public String name() {
    return "fake-judge";
  }

  @Override
  public boolean available() {
    return available;
  }

  @Override
  public void submit(Job job) {
    jobs.add(job);
  }
}
