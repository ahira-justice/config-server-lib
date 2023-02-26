# config-server-lib

## What is config-server-lib?

config-server-lib is a spring boot integration lib for [config-server](https://github.com/ahira-justice/config-server).

It bootstraps your application by fetching config from the config-server automatically on startup. It also provides your application with a _/refresh_ callback endpoint that _config-server_ can use to remotely trigger a config refresh and restart of your spring boot application.

## Usage
**Application.java** - _example_
```java
package com.ahirajustice.example;

import com.ahirajustice.lib.configserver.ConfigServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
        "com.ahirajustice.example",
        "com.ahirajustice.lib.configserver"
})
public class Application {

    public static void main(String[] args) {
        ConfigServer.getConfig();
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
        ConfigServer.configureRestart(Application.class, context);
    }

}
```

Integration with config server requires setting up the Spring _application.properties_ or _application.yml_ file to work with [spring-dotenv](https://github.com/paulschwarz/spring-dotenv).

**application.yml** - _Spring datasource config example_
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${env.DATABASE_URL}
    username: ${env.DATABASE_USER}
    password: ${env.DATABASE_PASSWORD}
```


There are four environment variables that _config-server-lib_ looks for on startup.

```shell
CONFIG_SERVER_CLIENT_ID=example_client
CONFIG_SERVER_CLIENT_SECRET=example_client_secret
CONFIG_SERVER_BASE_URL=https://configserver.example.com
CONFIG_ENVIRONMENT=DEVELOPMENT
```

**_CONFIG_SERVER_CLIENT_ID_** and **_CONFIG_SERVER_CLIENT_SECRET_** are credentials configured on _config-server_ on client registration.

**_CONFIG_SERVER_BASE_URL_** is the base url for the _config-server_ deployment.

**_CONFIG_ENVIRONMENT_** is the config scope for your application. **_CONFIG_ENVIRONMENT_** is one of [DEVELOPMENT, STAGING, PRODUCTION] and configs are fetched from _config-server_ under one of these environments.

To disable _config-server-lib_ from fetching configs on startup, simply omit setting these environment variables, or set them to empty/blank values.

## Installation
Add config-server-lib as a dependency

**pom.xml**
```xml
<dependency>
  <groupId>com.ahirajustice</groupId>
  <artifactId>config-server-lib</artifactId>
  <version>0.0.3</version>
</dependency>
```

**build.gradle**
```groovy
implementation 'com.ahirajustice:config-server-lib:0.0.3'
```

For more options with different build tools, check out [https://search.maven.org/artifact/com.ahirajustice/config-server-lib/0.0.3/jar](https://search.maven.org/artifact/com.ahirajustice/config-server-lib/0.0.3/jar)

## License

[The Apache License, Version 2.0](LICENSE)
