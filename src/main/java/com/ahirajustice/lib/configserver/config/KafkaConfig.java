package com.ahirajustice.lib.configserver.config;

import com.ahirajustice.lib.configserver.ConfigServer;
import com.ahirajustice.lib.configserver.conditions.ConfigServerEnabledCondition;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Conditional(ConfigServerEnabledCondition.class)
public class KafkaConfig {

    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaBootstrapServers()}")
    private String bootstrapAddress;
    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaSecurityProtocol() ?: ''}")
    private String securityProtocol;
    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaSaslJaasConfig() ?: ''}")
    private String saslJaasConfig;
    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaSaslMechanism() ?: ''}")
    private String saslMechanism;
    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaSessionTimeoutMs() ?: '45000'}")
    private String sessionTimeoutMs;
    @Value(value = "#{T(com.ahirajustice.lib.configserver.ConfigServer).getKafkaClientDnsLookup() ?: 'use_all_dns_ips'}")
    private String clientDnsLookup;

    // Topic Config
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);

        configureSasl(properties);

        return new KafkaAdmin(properties);
    }

    @Bean
    public NewTopic topic() {
        return TopicBuilder.name(ConfigServer.getTopic()).partitions(1).build();
    }

    // Producer Config
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put("client.dns.lookup", clientDnsLookup);
        properties.put("session.timeout.ms", sessionTimeoutMs);

        configureSasl(properties);

        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Consumer Config
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> properties = new HashMap<>();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, ConfigServer.getGroupId());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put("client.dns.lookup", clientDnsLookup);
        properties.put("session.timeout.ms", sessionTimeoutMs);

        configureSasl(properties);

        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        return factory;
    }

    private void configureSasl(Map<String, Object> properties) {
        if ("SASL_SSL".equals(securityProtocol)) {
            if (StringUtils.isEmpty(saslJaasConfig)) {
                throw new IllegalArgumentException("config.server.kafka.sasl.jaas.config is not set");
            }

            if (StringUtils.isEmpty(saslMechanism)) {
                throw new IllegalArgumentException("config.server.kafka.sasl.mechanism is not set");
            }

            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
            properties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
    }

}