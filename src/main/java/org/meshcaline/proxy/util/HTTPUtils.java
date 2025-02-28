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

package org.meshcaline.proxy.util;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import java.net.URI;

public class HTTPUtils {

    public static String constructIngressURI(ServerWebExchange exchange) {
        // Construct the original URI from the request
        String path = exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getQuery();
        return path + (query != null ? "?" + query : "");
    }

    public static void setForwardedHeader(ServerWebExchange exchange, HttpHeaders headers) {
        String forwarded = headers.getFirst("Forwarded");
        if (forwarded != null) {
            exchange.getResponse().getHeaders().set("Forwarded", forwarded);
        }
    }

    public static String constructForwardedHeader(URI ingressURI, URI egressURI) {
        StringBuilder forwardedHeader = new StringBuilder();

        // Add "by" (proxy server information, inferred from egress)
        forwardedHeader.append("by=").append(formatHostPort(egressURI));

        // Add "for" (original client IP, inferred from ingress)
        forwardedHeader.append(";for=").append(formatHostPort(ingressURI));

        // Add "proto" (original client protocol, inferred from ingress)
        forwardedHeader.append(";proto=").append(ingressURI.getScheme());

        // Add "host" (original request host, inferred from ingress)
        forwardedHeader.append(";host=").append(ingressURI.getHost());

        return forwardedHeader.toString();
    }

    private static String formatHostPort(URI uri) {
        if (uri.getPort() == -1) {  // Default ports are omitted
            return uri.getHost();
        }
        return uri.getHost() + ":" + uri.getPort();
    }
}
