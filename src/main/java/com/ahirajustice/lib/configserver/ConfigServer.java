package com.ahirajustice.lib.configserver;

import com.ahirajustice.lib.configserver.enums.ConfigEnvironment;
import com.ahirajustice.lib.configserver.exceptions.ConfigFetchException;
import com.ahirajustice.lib.configserver.exceptions.ConfigInitializationException;
import com.ahirajustice.lib.configserver.exceptions.ConfigServerConfigurationException;
import com.ahirajustice.lib.configserver.models.ConfigEntry;
import com.ahirajustice.lib.configserver.models.LoginResponse;
import com.ahirajustice.lib.configserver.requests.ClientLoginRequest;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
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

public class ConfigServer {

    public static void getConfig() {
        Map<String, String> envVars = System.getenv();

        String clientId = envVars.get("CONFIG_SERVER_CLIENT_ID");
        String clientSecret = envVars.get("CONFIG_SERVER_CLIENT_SECRET");
        String baseUrl = envVars.get("CONFIG_SERVER_BASE_URL");
        String environment = envVars.get("CONFIG_ENVIRONMENT");

        List<String> errors = new ArrayList<>();

        if (StringUtils.isBlank(clientId))
            errors.add("CONFIG_SERVER_CLIENT_ID");
        if (StringUtils.isBlank(clientSecret))
            errors.add("CONFIG_SERVER_CLIENT_SECRET");
        if (StringUtils.isBlank(baseUrl))
            errors.add("CONFIG_SERVER_BASE_URL");
        if (StringUtils.isBlank(environment) || !EnumUtils.isValidEnum(ConfigEnvironment.class, environment))
            errors.add("CONFIG_ENVIRONMENT");

        if (errors.size() == 4) {
            return;
        }

        if (!errors.isEmpty()) {
            String message = "Invalid configuration for";

            for (String error : errors) {
                message = String.format("%s %s, ", message, error);
            }

            throw new ConfigServerConfigurationException(message);
        }

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

        for (ConfigEntry configEntry: configEntries) {
            config.append(String.format("%s=%s", configEntry.getConfigKey(), configEntry.getConfigValue()));
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

}
