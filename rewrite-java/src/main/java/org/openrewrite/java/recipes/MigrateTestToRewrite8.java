/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.recipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MigrateTestToRewrite8 extends Recipe {
    private static final String REWRITE_TEST_FQN = "org.openrewrite.test.RewriteTest";
    private static final MethodMatcher RECIPE_METHOD_MATCHER = new MethodMatcher("org.openrewrite.test.RecipeSpec recipe(org.openrewrite.Recipe)");
    private static final MethodMatcher DO_NEXT_METHOD_MATCHER = new MethodMatcher("org.openrewrite.Recipe doNext(org.openrewrite.Recipe)");

    @Override
    public String getDisplayName() {
        return "Migrate rewrite unit test from version 7 to 8";
    }

    @Override
    public String getDescription() {
        return "Since the method `Recipe::doNext(..)` is deprecated, For unit test, change usage like `RecipeSpec.recipe(X.doNext(Y))` to `RecipeSpec.recipes(X, Y)`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("Rewrite8 migration");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(REWRITE_TEST_FQN, false),
            new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                ExecutionContext ctx) {
                    method = super.visitMethodInvocation(method, ctx);
                    boolean isRecipeSpecRecipeMethod = "recipe".equals(method.getSimpleName()) &&
                                                       method.getSelect() != null &&
                                                       TypeUtils.isOfClassType(method.getSelect().getType(), "org.openrewrite.test.RecipeSpec");
                    if (isRecipeSpecRecipeMethod) {
                        method.getArguments();
                        if (method.getArguments().isEmpty()) {
                            return method;
                        }
                        List<Expression> recipes = flatDoNext(method.getArguments().get(0));
                        if (recipes.size() > 1) {
                            String argsPlaceHolders = String.join(",", Collections.nCopies(recipes.size(), "#{any()}"));
                            JavaTemplate recipesTemplate = JavaTemplate.builder(
                                    "#{any()}.recipes(" + argsPlaceHolders + ")")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion()
                                    .classpath(JavaParser.runtimeClasspath()))
                                .imports("org.openrewrite.test.RecipeSpec", "org.openrewrite.test.RewriteTest")
                                // .imports("org.openrewrite.Preconditions")
                                .build();

                            Object[] parameters = new Object[recipes.size() + 1];
                            parameters[0] = method.getSelect();
                            for (int i = 0; i < recipes.size(); i++) {
                                parameters[i + 1] = recipes.get(i);
                            }

                            return recipesTemplate.apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                parameters
                            );
                        }
                    }
                    return method;
                }
            });
    }

    private static List<Expression> flatDoNext(Expression expression) {
        List<Expression> recipes = new ArrayList<>();
        if (!(expression instanceof J.MethodInvocation)) {
            recipes.add(expression);
            return recipes;
        }

        J.MethodInvocation method = (J.MethodInvocation) expression;
        boolean isDoNextMethod = "doNext".equals(method.getSimpleName()) &&
                                 method.getSelect() != null;
        if (isDoNextMethod) {
            recipes.addAll(flatDoNext(method.getSelect()));
            recipes.addAll(flatDoNext(method.getArguments().get(0)));
        }
        return recipes;
    }
}
