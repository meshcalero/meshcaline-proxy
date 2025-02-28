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

package org.meshcaline.proxy.configuration.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.data.annotation.Transient;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyConfiguration {
    private final List<Mapping> mappings;

    public ProxyConfiguration( List<Mapping> mappings ){
        this.mappings = new CopyOnWriteArrayList<>(mappings);
    }

    public ProxyConfiguration() {
        this(new ArrayList<>());
    }

    public List<Mapping> mappings() {
        return mappings;
    }

    public Optional<Mapping> findMapping(String id) {
        return this.mappings.stream()
            .filter(m -> m.id().equals(id))
            .findFirst();
    }

    public boolean addMapping(Mapping mapping){
        return this.mappings.add(mapping);
    }

    public boolean removeMapping(String id){
        return this.mappings.removeIf( m -> m.id().equals(id) );
    }

    public Optional<Mapping> updateMapping(Mapping mapping){
        final ListIterator<Mapping> it = this.mappings.listIterator();
        while( it.hasNext() ){
            final Mapping oldMapping = it.next();
            if( oldMapping.id().equals(mapping.id()) ){
                it.set(mapping);
                return Optional.of(mapping);
            }
        }
        return Optional.empty();
    }

    public Optional<String> findEgressURI(String ingressURI) {
        // we want to process the entries strictly in order, hence don't use streams
        for( Mapping mapping: this.mappings ){
            final Mapping.UriMatcher matcher =  mapping.matcher(ingressURI);
            if (matcher.matches()) {
                return Optional.of(matcher.egressUri());
            }
        }
        return Optional.empty();
    }

    public static class Mapping {
        final String id;
        final String ingressURIRegEx;
        final String egressURIReplace;
        @Transient
        final Pattern ingressURIPattern;

        @JsonCreator
        public Mapping(
            String id,
            String ingressURIRegEx,
            String egressURIReplace
        ) {
            this.id = id;
            this.ingressURIRegEx = ingressURIRegEx;
            this.egressURIReplace = egressURIReplace;
            this.ingressURIPattern = Pattern.compile(ingressURIRegEx);
        }

        public Mapping(
            String ingressURIRegEx,
            String egressURIReplace
        ) {
            this(UUID.randomUUID().toString(), ingressURIRegEx, egressURIReplace);
        }

        public Mapping withId(String id){
            return new Mapping(id,this.ingressURIRegEx,this.egressURIReplace);
        }

        public String id() {
            return this.id;
        }

        public String ingressURIRegEx() {
            return this.ingressURIRegEx;
        }

        public String getEgressURIReplace() {
            return this.egressURIReplace;
        }

        public interface UriMatcher {
            boolean matches();
            String egressUri();
        }

        public UriMatcher matcher(final String ingressUri) {
            return new UriMatcher() {
                final Matcher matcher = ingressURIPattern.matcher(ingressUri);
                public boolean matches() { return matcher.matches(); }
                public String egressUri() { return matcher.replaceAll(egressURIReplace); }
            };
        }

    }

}
