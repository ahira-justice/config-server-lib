package com.ahirajustice.lib.configserver.conditions;

import com.ahirajustice.lib.configserver.ConfigServer;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class ConfigServerEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ConfigServer.isEnabled();
    }

}
