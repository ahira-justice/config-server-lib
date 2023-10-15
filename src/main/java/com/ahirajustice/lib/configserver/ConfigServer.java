package com.ahirajustice.lib.configserver;

import com.ahirajustice.lib.configserver.constants.SecurityConstants;
import com.ahirajustice.lib.configserver.exceptions.ConfigFetchException;
import com.ahirajustice.lib.configserver.exceptions.ConfigInitializationException;
import com.ahirajustice.lib.configserver.exceptions.ConfigServerConfigurationException;
import com.ahirajustice.lib.configserver.models.ConfigEntry;
import com.ahirajustice.lib.configserver.models.SimpleMessageResponse;
import com.ahirajustice.lib.configserver.utils.CipherUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ConfigServer {

    @Getter
    @Setter
    private static boolean broadcaster;
    @Getter
    private static boolean enabled;
    private static Class<?>[] sources;
    private static ConfigurableApplicationContext context;
    private static ApplicationArguments args;
    private static String serviceId;
    private static String secretKey;
    private static String baseUrl;
    private static String privateKey;
    @Getter
    private static String kafkaBootstrapServers;
    @Getter
    private static String kafkaSecurityProtocol;
    @Getter
    private static String kafkaSaslMechanism;
    @Getter
    private static String kafkaSaslJaasConfig;
    @Getter
    private static String kafkaSessionTimeoutMs;
    @Getter
    private static String kafkaClientDnsLookup;
    private static String podName;

    public static void getConfig() {
        Map<String, String> envVars = System.getenv();

        secretKey = envVars.get("CONFIG_SERVER_SECRET_KEY");
        baseUrl = envVars.get("CONFIG_SERVER_BASE_URL");
        privateKey = envVars.get("CONFIG_SERVER_PRIVATE_KEY");
        kafkaBootstrapServers = envVars.get("CONFIG_SERVER_KAFKA_BOOTSTRAP_SERVERS");
        kafkaSecurityProtocol = envVars.get("CONFIG_SERVER_KAFKA_SECURITY_PROTOCOL");
        kafkaSaslMechanism = envVars.get("CONFIG_SERVER_KAFKA_SASL_MECHANISM");
        kafkaSaslJaasConfig = envVars.get("CONFIG_SERVER_KAFKA_SASL_JAAS_CONFIG");
        kafkaSessionTimeoutMs = envVars.get("CONFIG_SERVER_KAFKA_SESSION_TIMEOUT_MS");
        kafkaClientDnsLookup = envVars.get("CONFIG_SERVER_KAFKA_CLIENT_DNS_LOOKUP");
        podName = envVars.get("HOSTNAME");

        List<String> errors = new ArrayList<>();

        if (StringUtils.isBlank(secretKey))
            errors.add("CONFIG_SERVER_CLIENT_SECRET");
        if (StringUtils.isBlank(baseUrl))
            errors.add("CONFIG_SERVER_BASE_URL");
        if (StringUtils.isBlank(privateKey))
            errors.add("CONFIG_SERVER_PRIVATE_KEY");
        if (StringUtils.isBlank(kafkaBootstrapServers))
            errors.add("CONFIG_SERVER_KAFKA_BOOTSTRAP_SERVERS");
        if (StringUtils.isBlank(kafkaSecurityProtocol))
            errors.add("CONFIG_SERVER_KAFKA_SECURITY_PROTOCOL");
        if (StringUtils.isBlank(kafkaSaslMechanism))
            errors.add("CONFIG_SERVER_KAFKA_SASL_MECHANISM");
        if (StringUtils.isBlank(kafkaSaslJaasConfig))
            errors.add("CONFIG_SERVER_KAFKA_SASL_JAAS_CONFIG");
        if (StringUtils.isBlank(podName))
            errors.add("HOSTNAME");

        if (errors.size() == 8) {
            return;
        }

        if (!errors.isEmpty()) {
            String message = "Invalid configuration for";

            for (String error : errors) {
                message = String.format("%s %s, ", message, error);
            }

            throw new ConfigServerConfigurationException(message);
        }

        enabled = true;
        serviceId = envVars.get("SERVICE_NAME");

        List<ConfigEntry> configEntries = fetchConfig(baseUrl);
        persistConfig(configEntries);
    }

    private static List<ConfigEntry> fetchConfig(String baseUrl) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, SecurityConstants.TOKEN_PREFIX + " " + secretKey);

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            var responseEntity = restTemplate.exchange(
                    String.format("%s/api/configs/fetch", baseUrl),
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<List<ConfigEntry>>() {}
            );

            return responseEntity.getBody();
        }
        catch (Exception ex) {
            throw new ConfigFetchException(ex.getMessage());
        }

    }

    private static void persistConfig(List<ConfigEntry> configEntries) {
        StringBuilder config = new StringBuilder();

        for (ConfigEntry configEntry : configEntries) {
            String configValue = configEntry.getConfigValue();

            if (configEntry.getEncrypted()) {
                configValue = decrypt(configValue);
            }
            
            config.append(String.format("%s=%s", configEntry.getConfigKey(), configValue));
            config.append("\n");
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(".env"));
            writer.write(config.toString());
            writer.close();
        }
        catch (IOException ex) {
            throw new ConfigInitializationException(ex.getMessage());
        }
    }

    private static String decrypt(String configValue) {
        return CipherUtils.decryptString(configValue, privateKey);
    }

    public static SimpleMessageResponse refreshConfig(List<ConfigEntry> configEntries) {
        try {
            persistConfig(configEntries);
            restart();

            return SimpleMessageResponse.success("Successfully refreshed application config");
        }
        catch (ConfigInitializationException ex) {
            return SimpleMessageResponse.fail("Error occurred while persisting application config");
        }
        catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return SimpleMessageResponse.fail("An error occurred while refreshing application config. Check application logs.");
        }
    }

    public static void configureRestart(ConfigurableApplicationContext applicationContext, Set<Class<?>> primarySources) {
        sources = primarySources.toArray(Class[]::new);
        context = applicationContext;
        args = context.getBean(ApplicationArguments.class);
    }

    private static void restart() {
        Thread thread = new Thread(() -> {
            context.close();
            context = SpringApplication.run(sources, args.getSourceArgs());
        });

        thread.setDaemon(false);
        thread.start();
    }

    public static void pullConfig() {
        List<ConfigEntry> configEntries = fetchConfig(baseUrl);
        persistConfig(configEntries);
        restart();
    }

    public static String getTopic() {
        return StringUtils.isNotBlank(serviceId) ? serviceId : secretKey;
    }

    public static String getGroupId() {
        return podName;
    }

}
