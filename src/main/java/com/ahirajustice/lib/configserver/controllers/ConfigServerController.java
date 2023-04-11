package com.ahirajustice.lib.configserver.controllers;

import com.ahirajustice.lib.configserver.ConfigServer;
import com.ahirajustice.lib.configserver.exceptions.ConfigRefreshBroadcastException;
import com.ahirajustice.lib.configserver.models.ConfigEntry;
import com.ahirajustice.lib.configserver.models.SimpleMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SuccessCallback;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ConfigServerController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @RequestMapping(path = "/refresh", method = RequestMethod.POST)
    public ResponseEntity<SimpleMessageResponse> refreshConfig(@Valid @RequestBody List<ConfigEntry> request) {
        if (!ConfigServer.isEnabled()) {
            SimpleMessageResponse response = SimpleMessageResponse.fail("Config Server is not enabled for this service");
            return ResponseEntity.badRequest().body(response);
        }

        broadcastConfigRefresh(ConfigServer.getTopic(), ConfigServer.getGroupId());
        SimpleMessageResponse response = ConfigServer.refreshConfig(request);

        return ResponseEntity.ok().body(response);
    }

    public void broadcastConfigRefresh(String topicName, String message) {
        try {
            ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topicName, message);
            future.addCallback(onSuccess(message), onFailure(message));
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new ConfigRefreshBroadcastException(ex.getMessage());
        }
    }

    private SuccessCallback<SendResult<String, String>> onSuccess(String message) {
        return (success) -> {
            if (success != null) {
                ConfigServer.setBroadcaster(true);
                log.info("Sent message=[{}] with offset=[{}]", message, success.getRecordMetadata().offset());
            }
        };
    }

    private FailureCallback onFailure(String message) {
        return (failure) -> log.error("Unable to send message=[{}] due to : {}", message, failure.getMessage());
    }

}
