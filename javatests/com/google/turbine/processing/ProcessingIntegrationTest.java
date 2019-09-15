/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.turbine.processing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.turbine.binder.Binder;
import com.google.turbine.binder.ClassPathBinder;
import com.google.turbine.binder.Processing;
import com.google.turbine.diag.SourceFile;
import com.google.turbine.diag.TurbineError;
import com.google.turbine.lower.IntegrationTestSupport;
import com.google.turbine.parse.Parser;
import com.google.turbine.testing.TestClassPaths;
import com.google.turbine.tree.Tree;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProcessingIntegrationTest {

  @SupportedAnnotationTypes("*")
  public static class CrashingProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      throw new RuntimeException("crash!");
    }
  }

  private static final IntegrationTestSupport.TestInput SOURCES =
      IntegrationTestSupport.TestInput.parse(
          Joiner.on('\n')
              .join(
                  "=== Test.java ===", //
                  "@Deprecated",
                  "class Test extends NoSuch {",
                  "}"));

  @Test
  public void crash() throws IOException {
    ImmutableList<Tree.CompUnit> units =
        SOURCES.sources.entrySet().stream()
            .map(e -> new SourceFile(e.getKey(), e.getValue()))
            .map(Parser::parse)
            .collect(toImmutableList());
    try {
      Binder.bind(
          units,
          ClassPathBinder.bindClasspath(ImmutableList.of()),
          Processing.ProcessorInfo.create(
              ImmutableList.of(new CrashingProcessor()),
              getClass().getClassLoader(),
              ImmutableMap.of(),
              SourceVersion.latestSupported()),
          TestClassPaths.TURBINE_BOOTCLASSPATH,
          Optional.empty());
      fail();
    } catch (TurbineError e) {
      assertThat(e.diagnostics()).hasSize(2);
      assertThat(e.diagnostics().get(0).message()).contains("could not resolve NoSuch");
      assertThat(e.diagnostics().get(1).message()).contains("crash!");
    }
  }
}