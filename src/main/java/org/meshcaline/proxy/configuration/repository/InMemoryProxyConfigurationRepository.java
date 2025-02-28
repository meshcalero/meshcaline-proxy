/*
 * This file is part of the *meshcaline proxy* project.
 *
 * Copyright (C) 2025, Andreas Schmidt
 *
 * *meshcaline proxy* is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshcaline.proxy.configuration.repository;

import org.meshcaline.proxy.configuration.model.ProxyConfiguration;
import io.vavr.NotImplementedError;
import jakarta.annotation.PostConstruct;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryProxyConfigurationRepository implements ProxyConfigurationRepository {

    final private LinkedHashMap<String, ProxyConfigurationEntity> configMap = new LinkedHashMap<>();

    @Component
    @ConfigurationProperties("proxy.config")
    private static class DefaultConfiguration {
        private Map<String,String> mappings;

        public Map<String, String> mappings() {
            return mappings;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }
    }

    @Autowired
    private DefaultConfiguration defaultConfiguration;

    @PostConstruct
    private void init(){
        if (defaultConfiguration.mappings == null) return;
        final List<ProxyConfiguration.Mapping> mappings = defaultConfiguration.mappings
            .entrySet().stream()
            .map( entry -> new ProxyConfiguration.Mapping(entry.getKey(),entry.getValue()))
            .toList();
        final ProxyConfigurationEntity configEntity = new ProxyConfigurationEntity(new ProxyConfiguration(mappings));
        configMap.put(configEntity.id(),configEntity);
    }

    @SuppressWarnings("unchecked")
    private <S extends ProxyConfigurationEntity> S asS(ProxyConfigurationEntity entity) {
        return (S) entity;
    }

    @Override
    public <S extends ProxyConfigurationEntity> Mono<S> findFirstByOrderByCreatedAtDesc() {
        return Mono.justOrEmpty(
            Optional.ofNullable(configMap.lastEntry())
                .map( e -> asS(e.getValue()))
        );
    }

    @Override
    public <S extends ProxyConfigurationEntity> Mono<S> save(S entity) {
        configMap.put(entity.id(),entity);
        return Mono.just( asS(entity) );
    }

    @Override
    public <S extends ProxyConfigurationEntity> Flux<S> saveAll(Iterable<S> entities) {
        for( ProxyConfigurationEntity entity : entities ){
            configMap.put(entity.id(),entity);
        }
        return Flux.fromIterable(entities);
    }

    @Override
    public <S extends ProxyConfigurationEntity> Flux<S> saveAll(Publisher<S> entityStream) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<ProxyConfigurationEntity> findById(String s) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<ProxyConfigurationEntity> findById(Publisher<String> id) {
        return null;
    }

    @Override
    public Mono<Boolean> existsById(String s) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Boolean> existsById(Publisher<String> id) {
        throw new NotImplementedError();
    }

    @Override
    public Flux<ProxyConfigurationEntity> findAll() {
        throw new NotImplementedError();
    }

    @Override
    public Flux<ProxyConfigurationEntity> findAllById(Iterable<String> strings) {
        throw new NotImplementedError();
    }

    @Override
    public Flux<ProxyConfigurationEntity> findAllById(Publisher<String> idStream) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Long> count() {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteById(String s) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteById(Publisher<String> id) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> delete(ProxyConfigurationEntity entity) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends ProxyConfigurationEntity> entities) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends ProxyConfigurationEntity> entityStream) {
        throw new NotImplementedError();
    }

    @Override
    public Mono<Void> deleteAll() {
        throw new NotImplementedError();
    }

}
