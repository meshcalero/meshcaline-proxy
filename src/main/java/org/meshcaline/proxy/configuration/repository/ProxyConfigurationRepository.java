package org.meshcaline.proxy.configuration.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import org.springframework.lang.NonNull;

public interface ProxyConfigurationRepository extends ReactiveCrudRepository<ProxyConfigurationEntity, String> {
    @NonNull
    <S extends ProxyConfigurationEntity> Mono<S> findFirstByOrderByCreatedAtDesc();
}
