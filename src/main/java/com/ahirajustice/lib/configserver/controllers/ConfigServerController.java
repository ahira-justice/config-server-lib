package com.ahirajustice.lib.configserver.controllers;

import com.ahirajustice.lib.configserver.ConfigServer;
import com.ahirajustice.lib.configserver.models.ConfigEntry;
import com.ahirajustice.lib.configserver.models.SimpleMessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
public class ConfigServerController {

    @RequestMapping(path = "/refresh", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public SimpleMessageResponse refreshConfig(@Valid @RequestBody List<ConfigEntry> request) {
        return ConfigServer.refreshConfig(request);
    }

}
