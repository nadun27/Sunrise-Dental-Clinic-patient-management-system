package com.sunrisedental.event;

@FunctionalInterface
public interface ClinicEventListener {
    void onEvent(ClinicEvent event);
}
