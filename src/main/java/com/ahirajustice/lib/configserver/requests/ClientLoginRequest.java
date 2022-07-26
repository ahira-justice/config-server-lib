package com.ahirajustice.lib.configserver.requests;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ClientLoginRequest {

    private String clientId;
    private String clientSecret;

}
