package com.ahirajustice.lib.configserver.listeners;

import com.ahirajustice.lib.configserver.ConfigServer;
import com.ahirajustice.lib.configserver.conditions.ConfigServerEnabledCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ConfigServerEnabledCondition.class)
public class ConfigRefreshListener {

    @KafkaListener(
            topics = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getTopic()}",
            groupId = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getGroupId()}"
    )
    public void listenForRestart(String message) {
        if (!ConfigServer.isBroadcaster()) {
            log.info("Service config refresh event received from {}", message);
            ConfigServer.pullConfig();
        }

        ConfigServer.setBroadcaster(false);
    }

}
