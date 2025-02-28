/*
 * This file is part of the *meshcaline proxy* project.
 *
 * The file contains a reduced version of the
 * org.springframework.http.codec.multipart.DefaultPartEvents class of the spring framework
 *
 * Copying the file was necessary as a workaround to the visibility restrictions of the implementation
 * within the springframework project.
 *
 * The original copyright notices remains valid for this single file, but not for any other
 * file of the *meshcaline proxy* project.
 */


/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.meshcaline.proxy.multipart;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.http.codec.multipart.FormPartEvent;
import org.springframework.util.Assert;

/**
 * Default implementations of {@link PartEvent} and subtypes.
 *
 * @author Arjen Poutsma
 * @since 6.0
 */
public abstract class DefaultPartEvents {

    public static PartEvent create(HttpHeaders headers, DataBuffer dataBuffer, boolean isLast) {
        Assert.notNull(headers, "Headers must not be null");
        Assert.notNull(dataBuffer, "DataBuffer must not be null");
        return new DefaultPartEvent(headers, dataBuffer, isLast);
    }

    public static PartEvent create(HttpHeaders headers) {
        Assert.notNull(headers, "Headers must not be null");
        return new DefaultPartEvent(headers);
    }

    private static abstract class AbstractPartEvent implements PartEvent {

        private final HttpHeaders headers;


        protected AbstractPartEvent(HttpHeaders headers) {
            this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
        }

        @Override
        public HttpHeaders headers() {
            return this.headers;
        }
    }


    /**
     * Default implementation of {@link PartEvent}.
     */
    private static class DefaultPartEvent extends AbstractPartEvent {

        private static final DataBuffer EMPTY = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);

        private final DataBuffer content;

        private final boolean last;


        public DefaultPartEvent(HttpHeaders headers) {
            this(headers, EMPTY, true);
        }

        public DefaultPartEvent(HttpHeaders headers, DataBuffer content, boolean last) {
            super(headers);
            this.content = content;
            this.last = last;
        }

        @Override
        public DataBuffer content() {
            return this.content;
        }

        @Override
        public boolean isLast() {
            return this.last;
        }

    }

}
