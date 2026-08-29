package com.sunrisedental.event;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClinicEventPublisher {

    private static final Logger LOGGER = Logger.getLogger(
            ClinicEventPublisher.class.getName()
    );

    private static final ClinicEventPublisher INSTANCE =
            new ClinicEventPublisher();

    private final CopyOnWriteArrayList<ClinicEventListener> listeners =
            new CopyOnWriteArrayList<>();

    private ClinicEventPublisher() {
    }

    public static ClinicEventPublisher getInstance() {
        return INSTANCE;
    }

    public void register(ClinicEventListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void unregister(ClinicEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(ClinicEvent event) {
        if (event == null) {
            return;
        }

        for (ClinicEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "Clinic event listener failed for " +
                                event.type(),
                        exception
                );
            }
        }
    }
}
