package org.meshcaline.proxy.configuration.service;

import org.meshcaline.proxy.configuration.model.ProxyConfiguration;
import org.meshcaline.proxy.configuration.repository.ProxyConfigurationEntity;
import org.meshcaline.proxy.configuration.repository.ProxyConfigurationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ConfigurationService {

    private final ProxyConfigurationRepository proxyConfigurationRepository;
    private ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
/*
    private final ReactiveRedisMessageListenerContainer messageListenerContainer;

    public ConfigurationService(ProxyConfigurationRepository proxyConfigurationRepository,
                                ReactiveRedisMessageListenerContainer messageListenerContainer) {
        this.proxyConfigurationRepository = proxyConfigurationRepository;
        this.messageListenerContainer = messageListenerContainer;
    }
*/
    public ConfigurationService(ProxyConfigurationRepository proxyConfigurationRepository) {
        this.proxyConfigurationRepository = proxyConfigurationRepository;
    }

    @PostConstruct
    public void init() {
        loadConfigFromRepository();
        subscribeToConfigChanges();
    }

    public Optional<URI> findEgressURI(URI ingressURI) {
        return proxyConfiguration.findEgressURI(ingressURI.toString())
           .map( egressUri -> {
               try {
                   return new URI(egressUri);
               }
               catch (URISyntaxException e ) {
                   throw new RuntimeException("Invalid egressURI mapping: "+egressUri, e);
               }
           });
    }

    public Flux<ProxyConfiguration.Mapping> listMappings() {
        return Flux.fromIterable(this.proxyConfiguration.mappings());
    }

    public Mono<ProxyConfiguration.Mapping> addMapping(ProxyConfiguration.Mapping mapping) {
        return changeProxyConfiguration(config -> {
            if (config.findMapping(mapping.id()).isPresent()) {
                throw new IllegalArgumentException("Non-unique identifier in new mapping: "+mapping.id());
            }
            config.addMapping(mapping);
        }  )
        .map( config ->
            config
                .findMapping( mapping.id() )
                .orElseThrow( () -> new NoSuchElementException("Could not find proxy mapping with id "+mapping.id()) )
        );
    }

    public Mono<Void> deleteMapping(String id) {
        return changeProxyConfiguration(config -> {
                if (! config.removeMapping(id)) {
                    throw new NoSuchElementException("Could not find mapping with id: "+id);
                }
            } )
            .then();
    }

    public Mono<ProxyConfiguration.Mapping> updateMapping(ProxyConfiguration.Mapping mapping) {
        return changeProxyConfiguration(config -> {
                if (config.updateMapping(mapping).isEmpty()) {
                    throw new NoSuchElementException("Could not find mapping with id: "+mapping.id());
                }
            }  )
            .map( config ->
                config
                    .findMapping( mapping.id() )
                    .orElseThrow( () -> new NoSuchElementException("Could not find proxy mapping with id "+mapping.id()) )
            );
    }

    private Mono<ProxyConfigurationEntity> withLatestProxyConfiguration(Consumer<ProxyConfiguration> configConsumer) {
        return proxyConfigurationRepository.findFirstByOrderByCreatedAtDesc()
            .doOnSuccess( entity -> {
                this.proxyConfiguration = (entity == null)
                    ? new ProxyConfiguration()
                    : entity.configuration();
                configConsumer.accept(this.proxyConfiguration);
            });
    }

    private Mono<ProxyConfiguration> changeProxyConfiguration(Consumer<ProxyConfiguration> changingConsumer) {
        return withLatestProxyConfiguration( changingConsumer )
            .doOnSuccess( entity ->
                this.proxyConfigurationRepository
                    .save( new ProxyConfigurationEntity(this.proxyConfiguration) )
                    .subscribe()
            )
            .thenReturn ( this.proxyConfiguration );
    }

    private void loadConfigFromRepository() {
        withLatestProxyConfiguration(config -> {})
            .subscribe();
    }


    private void subscribeToConfigChanges() {
        /*
        messageListenerContainer.receive(ChannelTopic.of("config-changes"))
                .doOnNext(message -> loadConfigFromRepository())
                .subscribe();

         */
    }
}
