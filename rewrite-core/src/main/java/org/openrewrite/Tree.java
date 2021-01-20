/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.style.Style;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@c")
public interface Tree {
    static UUID randomId() {
        return UUID.randomUUID();
    }

    Markers getMarkers();

    <T extends Tree> T withMarkers(Markers markers);

    default <T extends Tree> T mark(Marker... add) {
        Markers markers = getMarkers();
        for (Marker marker : add) {
            markers = markers.add(marker);
        }
        return withMarkers(markers);
    }

    @JsonIgnore
    @Nullable
    default <S extends Style> S getStyle(Class<S> style) {
        return NamedStyles.merge(style, getMarkers().findAll(NamedStyles.class));
    }

    /**
     * An id that can be used to identify a particular AST element, even after transformations have taken place on it
     *
     * @return A unique identifier
     */
    UUID getId();

    @Nullable
    default <R, P> R accept(TreeVisitor<R, P> v, P p) {
        return v.defaultValue(this, p);
    }

    <P> String print(TreePrinter<P> printer, P p);

    default <P> String print(P p) {
        return print(TreePrinter.identity(), p);
    }

    default String print() {
        return print(TreePrinter.identity(), new Object());
    }

    default <P> String printTrimmed(TreePrinter<P> printer, P p) {
        return StringUtils.trimIndent(print(printer, p).trim());
    }

    default <P> String printTrimmed(P p) {
        return printTrimmed(TreePrinter.identity(), p);
    }

    default String printTrimmed() {
        return printTrimmed(TreePrinter.identity(), new Object());
    }

    default boolean isScope(@Nullable Tree tree) {
        return tree != null && tree.getId().equals(getId());
    }
}
