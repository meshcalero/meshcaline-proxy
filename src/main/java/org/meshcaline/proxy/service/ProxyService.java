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

package org.meshcaline.proxy.service;

import org.meshcaline.proxy.configuration.service.ConfigurationService;
import org.meshcaline.proxy.multipart.DefaultPartEvents;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.multipart.MultipartWriterSupport;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

import jakarta.validation.constraints.NotNull;

@Service
public class ProxyService {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final ConfigurationService configurationService;
    private final WebClient webClient;
    private final ProxyQueryProcessor proxyQueryProcessor;
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    public ProxyService(
            ConfigurationService configurationService,
            WebClient.Builder webClientBuilder,
            ProxyQueryProcessor proxyQueryProcessor
    ) {
        this.configurationService = configurationService;
        //this.webClient = webClientBuilder.build();
        this.proxyQueryProcessor = proxyQueryProcessor;
        // Create ExchangeStrategies with default codecs
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> {
                ClientCodecConfigurer.ClientDefaultCodecs codecs = configurer.defaultCodecs();
                codecs.maxInMemorySize(16 * 1024 * 1024);  // Increase buffer size if needed
            })
            .build();

        this.webClient = webClientBuilder
            .exchangeStrategies(strategies)
            .build();
    }

    public Flux<DataBuffer> handleRequest( @NotNull ServerWebExchange exchange) {
        URI ingressURI = exchange.getRequest().getURI();
        URI egressURI = this.configurationService
            .findEgressURI(ingressURI)
            .orElseThrow( () -> new RuntimeException("No matching configuration found for: "+ingressURI) );

        // Set the Forwarded header for the downstream request
        // TODO : Doesn't work due to ForwardedFilter
        // HTTPUtils.setForwardedHeader(exchange, exchange.getRequest().getHeaders());

        // Try to extract a filter query from the request
        final QueryExtractor extractor = new QueryExtractor(exchange);
        final ServerHttpRequest proxiedRequest = exchange.getRequest();
        final ServerHttpResponse proxyResponse = exchange.getResponse();

        final WebClient.ResponseSpec proxiedResponse = proxyRequest(
            proxiedRequest.getMethod(),
            egressURI,
            prepareProxyRequestHeaders(proxiedRequest.getHeaders(),egressURI),
            Optional.empty()//.ofNullable(extractor.getBody())
        );
        return extractor.hasQuery()
            ? respondProcessedResponse(proxiedResponse, exchange, egressURI, extractor)
            : respondPlainResponse(proxiedResponse, proxyResponse);
    }

    private HttpHeaders prepareProxyRequestHeaders(HttpHeaders proxiedRequestHeaders, URI egressURI){
        final HttpHeaders response = new HttpHeaders(
            CollectionUtils.toMultiValueMap(new HashMap<>(proxiedRequestHeaders))
        );
        response.setHost(new InetSocketAddress(egressURI.getHost(),Math.max(0,egressURI.getPort())));
        return response;
    }

    private WebClient.ResponseSpec proxyRequest(
            @NotNull HttpMethod method,
            @NotNull URI targetUri,
            @NotNull HttpHeaders headers,
            @NotNull Optional<Flux<DataBuffer>> body
    ) {
        final WebClient.RequestBodySpec requestBodySpec = webClient.method(method)
            .uri(targetUri)
            .headers(h -> h.addAll(headers) );

        return (
                body.isPresent()
                    ? requestBodySpec.body(body.orElse(Flux.empty()), DataBuffer.class).retrieve()
                    : requestBodySpec.retrieve()
            )

            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse
                    .bodyToMono(String.class)
                    .flatMap(errorBody ->
                        Mono.error(new RuntimeException("Error from proxied service: " + errorBody))
                    )
            );
        //TODO: Add handling of redirect responses
    }

    private Flux<DataBuffer> respondPlainResponse(
            @NotNull WebClient.ResponseSpec proxiedResponse,
            @NotNull ServerHttpResponse proxyResponse
    ) {
         return proxiedResponse.toBodilessEntity()
            .flatMapMany(response -> {
                proxyResponse.setStatusCode(response.getStatusCode());
                proxyResponse.getHeaders().clear();
                proxyResponse.getHeaders().addAll(response.getHeaders());
                return proxiedResponse
                    .bodyToFlux(DataBuffer.class);
            });
    }

    private Flux<DataBuffer> respondProcessedResponse(
        @NotNull WebClient.ResponseSpec proxiedResponse,
        @NotNull ServerWebExchange exchange,
        @NotNull URI proxiedResponseUri,
        @NotNull QueryExtractor extractor
    ) {
        return proxiedResponse.toBodilessEntity()
            .flatMapMany( responseEntity -> {
                exchange.getResponse().setStatusCode(responseEntity.getStatusCode());
                return proxiedResponse.bodyToMono(String.class)
                    .flatMapMany( body ->{
                        final HttpHeaders proxiedResponseHeaders = responseEntity.getHeaders();
                        final ProxyQueryProcessor.Result processingResult = proxyQueryProcessor
                            .process(parseJson(body), extractor.getQuery());
                        return processingResult.getFollowUpTasks().isEmpty()
                            ? respondSingleResult(exchange.getResponse(),processingResult,proxiedResponseHeaders)
                            : respondResultAsMultipartDataBuffer(exchange, proxyQueryProcessor, processingResult, proxiedResponseHeaders, proxiedResponseUri);
                    });
            });
    }

    private JsonNode parseJson(final String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON response: \n"+responseBody, e);
        }
    }
    private Flux<DataBuffer> respondSingleResult(
            @NotNull ServerHttpResponse proxyResponse,
            @NotNull ProxyQueryProcessor.Result result,
            @NotNull HttpHeaders proxiedResponseHeaders

    ) {
        proxyResponse.getHeaders().clear();
        proxyResponse.getHeaders().addAll(proxiedResponseHeaders);
        return Flux.just(writeJsonTreeToDataBuffer(result.getTargetNode(), proxyResponse.bufferFactory()));
    }

    private Flux<DataBuffer> respondResultAsMultipartDataBuffer(
            @NotNull ServerWebExchange exchange,
            @NotNull ProxyQueryProcessor processor,
            @NotNull ProxyQueryProcessor.Result result,
            @NotNull HttpHeaders proxiedResponseHeaders,
            @NotNull URI proxiedResponseUri
    ) {
        final MultipartTransformer transformer = new MultipartTransformer();
        exchange.getResponse().getHeaders().setContentType(transformer.constructContentType());
        final Flux<PartEvent> events = respondResultAsMultipartEvents(exchange, processor, result, proxiedResponseHeaders, proxiedResponseUri);
        return transformer.transformToDataBuffer(exchange.getResponse().bufferFactory(), events)
    //        .doOnNext( buffer -> exchange.getResponse().writeWith(Mono.just(buffer)))
        ;
    }

    private Flux<PartEvent> respondResultAsMultipartEvents(
            @NotNull ServerWebExchange exchange,
            @NotNull ProxyQueryProcessor processor,
            @NotNull ProxyQueryProcessor.Result result,
            @NotNull HttpHeaders proxiedResponseHeaders,
            @NotNull URI proxiedResponseUri
    ) {
        return Flux.just(writeJsonTreeToDataBuffer(result.getTargetNode(), exchange.getResponse().bufferFactory()))
            .map( content -> createPartEvent(proxiedResponseHeaders,content) )
            .concatWith(
                Flux.fromIterable(result.getFollowUpTasks())
                    .flatMap( task -> createFollowUpTaskMultipartEvents(exchange, processor, task, proxiedResponseUri) )
//                    .delayElements(Duration.ofSeconds(5))

            )
            /*
            .doOnNext( part -> {
                System.out.print(new Date());
                System.out.println(part.headers());

            })
             */
            ;
    }

    private static class MultipartTransformer extends MultipartWriterSupport {
        private final byte[] boundary = MimeTypeUtils.generateMultipartBoundary();

        public MultipartTransformer() {
            super(Collections.singletonList(MediaType.MULTIPART_MIXED));
        }

        public MediaType constructContentType(){
            return getMultipartMediaType(MediaType.MULTIPART_MIXED, boundary);
        }

        public Flux<DataBuffer> transformToDataBuffer(DataBufferFactory bufferFactory, Flux<PartEvent> partEvents){
            return partEvents
                    .windowUntil(PartEvent::isLast)
                    .flatMap(partData ->
                            partData.switchOnFirst((signal, flux) -> {
                                if (signal.hasValue()) {
                                    PartEvent value = signal.get();
                                    Assert.state(value != null, "Null value");
                                    Flux<DataBuffer> dataBuffers = flux.map(PartEvent::content)
                                            .filter(buffer -> buffer.readableByteCount() > 0);
                                    return encodePartData(boundary, bufferFactory, value.headers(), dataBuffers);
                                }
                                else {
                                    return flux.cast(DataBuffer.class);
                                }
                            }))
                    .concatWith(generateLastLine(boundary, bufferFactory))
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        }

        private Flux<DataBuffer> encodePartData(byte[] boundary, DataBufferFactory bufferFactory, HttpHeaders headers, Flux<DataBuffer> body) {
            return Flux.concat(
                    generateBoundaryLine(boundary, bufferFactory),
                    generatePartHeaders(headers, bufferFactory),
                    body,
                    generateNewLine(bufferFactory));
        }
    }

    private Flux<PartEvent> createFollowUpTaskMultipartEvents(
            @NotNull ServerWebExchange exchange,
            @NotNull ProxyQueryProcessor processor,
            @NotNull ProxyQueryProcessor.FollowUpTask task,
            @NotNull URI proxiedResponseUri
    ) {
        final URI uri;
        try {
            uri = exchange.getRequest().getURI().resolve(task.url());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid relative URL "+task.url(), e);
        }
        final URI proxiedUri = this.configurationService
                .findEgressURI(uri)
                // if we don't have the URI configured, we try without transformation
                // TODO: Avoid risk for infinite loop due to misconfiguration
                .orElse(uri);
        final WebClient.ResponseSpec proxiedResponse = proxyRequest(
            HttpMethod.GET,
            proxiedUri,
            prepareProxyRequestHeaders(exchange.getRequest().getHeaders(),proxiedUri),
            Optional.empty()
        );
        return proxiedResponse
            .toBodilessEntity()
            .flatMapMany( responseEntity -> {
                return proxiedResponse.bodyToMono(String.class)
                    .flatMapMany( body ->{
                        final HttpHeaders proxiedResponseHeaders = responseEntity.getHeaders();
                        final ProxyQueryProcessor.Result processingResult = proxyQueryProcessor
                            .processFollowUpTask(parseJson(body), task);
                        return respondResultAsMultipartEvents(
                            exchange,
                            proxyQueryProcessor,
                            processingResult,
                            responseEntity.getHeaders(),
                            proxiedResponseUri
                        );
                    });
            })
            //.delayElements(Duration.ofSeconds(10))
            ;

    }

    private DataBuffer writeJsonTreeToDataBuffer(JsonNode rootNode, DataBufferFactory bufferFactory){
        final byte[] jsonInBytes;
        try {
            jsonInBytes = objectMapper.writeValueAsBytes(rootNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unexpected error while writing jsonTree to byte[]", e);
        }
        return bufferFactory.wrap(jsonInBytes);
    }

    private PartEvent createPartEvent(HttpHeaders headers, DataBuffer content) {
        return DefaultPartEvents.create(headers, content,true);
    }

    private static class QueryExtractor {
        private final Optional<String> query;
        private final Flux<DataBuffer> body;
        private final DataBufferFactory bufferFactory;
        private byte[] bodyInBytes;
        private JsonNode jsonBody;

        public QueryExtractor(ServerWebExchange exchange) {
            this.body = exchange.getRequest().getBody();
            this.bufferFactory = exchange.getResponse().bufferFactory();
            this.query = extractQuery(exchange);
        }

        private Optional<String> extractQuery(ServerWebExchange exchange) {
            return extractQueryFromParams(exchange)
                    .or( () -> extractQueryFromHeaders(exchange) )
                    .or( () -> extractQueryFromBody(exchange) );
        }

        private Optional<String> extractQueryFromParams(ServerWebExchange exchange) {
            return Optional.ofNullable(
                            exchange.getRequest().getQueryParams().getFirst("_meshcaline_query")
                    );
        }

        private Optional<String> extractQueryFromHeaders(ServerWebExchange exchange) {
            return Optional.ofNullable(
                            exchange.getRequest().getHeaders().getFirst("X-MESHCALINE-QUERY")
                    );
        }

        private Optional<String> extractQueryFromBody(ServerWebExchange exchange) {
            readBody();

            if( this.bodyInBytes == null ) return Optional.empty();

            try {
                this.jsonBody = ProxyService.objectMapper.readTree(this.bodyInBytes);
            } catch (IOException e) {
                // no valid json body; ignore
                return Optional.empty();
            }
            if( ! ObjectNode.class.isAssignableFrom( this.jsonBody.getClass() )) {
                // only ObjectNodes can contain _query property
                return Optional.empty();
            }
            ObjectNode rootObject = (ObjectNode) jsonBody;
            JsonNode queryNode = rootObject.remove("_meshcaline_query");
            Optional<String> result = Optional.ofNullable(queryNode).map(JsonNode::asText);
            // if we have changed the JSON object, we also
            // have to update the underlying raw body for forwarding the body
            // to the proxied service
            result.ifPresent(s -> {
                try {
                    ProxyService.objectMapper.writeValueAsBytes(rootObject);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Unexpected JSON processing error", e);
                }
            });
            return result;
        }

        private void readBody() {
            DataBufferUtils.join(this.body)
                .doOnSuccess( dataBuffer -> {
                    if (dataBuffer == null) {
                        this.bodyInBytes = null;
                    }
                    else {
                        final byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        this.bodyInBytes = bytes;
                    }
                })
                .subscribe();
        }

        public boolean hasQuery() { return this.query.isPresent(); }
        public boolean hasBodyInBytes() { return null != this.bodyInBytes; }
        public boolean hasJsonBody() { return null != this.jsonBody; }
        public JsonNode getJsonBody() { return this.jsonBody; }

        public String getQuery() { return this.query.orElse(null); }
        public Flux<DataBuffer> getBody() {
            return hasBodyInBytes()
                ? DataBufferUtils.read(
                        new ByteArrayResource(bodyInBytes),
                        bufferFactory,
                        bodyInBytes.length
                    )
                : this.body;
        }
    }

}