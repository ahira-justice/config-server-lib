package com.ahirajustice.lib.configserver.autoconfig;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({
        "com.ahirajustice.lib.configserver",
})
public class ConfigServerAutoconfiguration {

}
