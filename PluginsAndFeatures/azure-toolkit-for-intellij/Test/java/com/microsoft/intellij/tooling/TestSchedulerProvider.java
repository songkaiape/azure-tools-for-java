package com.microsoft.intellij.tooling;

import com.microsoft.azuretools.core.mvp.ui.base.SchedulerProvider;

import rx.Scheduler;
import rx.schedulers.TestScheduler;

public class TestSchedulerProvider implements SchedulerProvider {

    private TestScheduler testScheduler = new TestScheduler();

    @Override
    public Scheduler io() {
        return testScheduler;
    }

    @Override
    public Scheduler computation() {
        return testScheduler;
    }

    public void triggerActions() {
        testScheduler.triggerActions();
    }

}