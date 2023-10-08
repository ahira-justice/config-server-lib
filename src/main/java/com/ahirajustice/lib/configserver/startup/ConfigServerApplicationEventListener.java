package com.ahirajustice.lib.configserver.startup;

import com.ahirajustice.lib.configserver.ConfigServer;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationListener;

import java.util.Set;
import java.util.stream.Collectors;

public class ConfigServerApplicationEventListener implements ApplicationListener<SpringApplicationEvent> {

    @Override
    public void onApplicationEvent(SpringApplicationEvent event) {
        if (event instanceof ApplicationStartingEvent) {
            ConfigServer.getConfig();
        }
        if (event instanceof ApplicationStartedEvent) {
            Set<Class<?>> primarySources = getPrimarySources(event.getSpringApplication().getAllSources());
            ConfigServer.configureRestart(((ApplicationStartedEvent)event).getApplicationContext(), primarySources);
        }
    }

    private Set<Class<?>> getPrimarySources(Set<Object> allSources) {
        return allSources.stream()
                .filter(source -> source instanceof Class<?>)
                .map(source -> (Class<?>)source)
                .collect(Collectors.toSet());
    }

}
