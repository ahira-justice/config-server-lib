package com.ahirajustice.lib.configserver;

import com.ahirajustice.lib.configserver.enums.ConfigEnvironment;
import com.ahirajustice.lib.configserver.exceptions.ConfigFetchException;
import com.ahirajustice.lib.configserver.exceptions.ConfigInitializationException;
import com.ahirajustice.lib.configserver.exceptions.ConfigServerConfigurationException;
import com.ahirajustice.lib.configserver.models.ConfigEntry;
import com.ahirajustice.lib.configserver.models.LoginResponse;
import com.ahirajustice.lib.configserver.models.SimpleMessageResponse;
import com.ahirajustice.lib.configserver.requests.ClientLoginRequest;
import com.ahirajustice.lib.configserver.utils.CipherUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
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
    private static String clientId;
    private static String clientSecret;
    private static String baseUrl;
    private static String environment;
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

        clientId = envVars.get("CONFIG_SERVER_CLIENT_ID");
        clientSecret = envVars.get("CONFIG_SERVER_CLIENT_SECRET");
        baseUrl = envVars.get("CONFIG_SERVER_BASE_URL");
        environment = envVars.get("CONFIG_ENVIRONMENT");
        privateKey = envVars.get("CONFIG_SERVER_PRIVATE_KEY");
        kafkaBootstrapServers = envVars.get("CONFIG_SERVER_KAFKA_BOOTSTRAP_SERVERS");
        kafkaSecurityProtocol = envVars.get("CONFIG_SERVER_KAFKA_SECURITY_PROTOCOL");
        kafkaSaslMechanism = envVars.get("CONFIG_SERVER_KAFKA_SASL_MECHANISM");
        kafkaSaslJaasConfig = envVars.get("CONFIG_SERVER_KAFKA_SASL_JAAS_CONFIG");
        kafkaSessionTimeoutMs = envVars.get("CONFIG_SERVER_KAFKA_SESSION_TIMEOUT_MS");
        kafkaClientDnsLookup = envVars.get("CONFIG_SERVER_KAFKA_CLIENT_DNS_LOOKUP");
        podName = envVars.get("HOSTNAME");

        List<String> errors = new ArrayList<>();

        if (StringUtils.isBlank(clientId))
            errors.add("CONFIG_SERVER_CLIENT_ID");
        if (StringUtils.isBlank(clientSecret))
            errors.add("CONFIG_SERVER_CLIENT_SECRET");
        if (StringUtils.isBlank(baseUrl))
            errors.add("CONFIG_SERVER_BASE_URL");
        if (StringUtils.isBlank(environment) || !EnumUtils.isValidEnum(ConfigEnvironment.class, environment))
            errors.add("CONFIG_ENVIRONMENT");
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

        if (errors.size() == 10) {
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

        LoginResponse loginResponse = fetchAuthToken(baseUrl, clientId, clientSecret);
        List<ConfigEntry> configEntries = fetchConfig(baseUrl, loginResponse, ConfigEnvironment.valueOf(environment));
        persistConfig(configEntries);
    }

    private static LoginResponse fetchAuthToken(String baseUrl, String clientId, String clientSecret) {
        RestTemplate restTemplate = new RestTemplate();

        ClientLoginRequest request = ClientLoginRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        HttpEntity<?> requestEntity = new HttpEntity<>(request);

        try {
            var responseEntity = restTemplate.exchange(
                    String.format("%s/api/auth/client-login", baseUrl),
                    HttpMethod.POST,
                    requestEntity,
                    LoginResponse.class
            );

            return responseEntity.getBody();
        }
        catch (Exception ex) {
            throw new ConfigFetchException(ex.getMessage());
        }
    }

    private static List<ConfigEntry> fetchConfig(String baseUrl, LoginResponse loginResponse, ConfigEnvironment environment) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, loginResponse.getTokenType() + " " + loginResponse.getAccessToken());

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            var responseEntity = restTemplate.exchange(
                    String.format("%s/api/configs/%s", baseUrl, environment),
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
        LoginResponse loginResponse = fetchAuthToken(baseUrl, clientId, clientSecret);
        List<ConfigEntry> configEntries = fetchConfig(baseUrl, loginResponse, ConfigEnvironment.valueOf(environment));
        persistConfig(configEntries);
        restart();
    }

    public static String getTopic() {
        return clientId;
    }

    public static String getGroupId() {
        return podName;
    }

}
