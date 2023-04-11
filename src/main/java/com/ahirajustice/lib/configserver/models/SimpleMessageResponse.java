package com.ahirajustice.lib.configserver.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleMessageResponse {

    private String message;
    private boolean success;

    public static SimpleMessageResponse success(String message) {
        return SimpleMessageResponse.builder()
                .message(message)
                .success(true)
                .build();
    }

    public static SimpleMessageResponse fail(String message) {
        return SimpleMessageResponse.builder()
                .message(message)
                .success(false)
                .build();
    }

}
